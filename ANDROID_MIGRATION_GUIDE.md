# Sort Pulse - Android & Kotlin Migration Guide

This document provides a comprehensive blueprint for relocating the **Sort Pulse** desktop puzzle game (currently written in Java 21 and JavaFX) to **Android using Kotlin and Jetpack Compose**. 

---

## 1. Target Stack Architecture

To transition from a desktop application to a modern Android mobile application, we will adopt the following stack:

| Component | Desktop Implementation (JavaFX) | Target Mobile Implementation (Kotlin/Android) |
|---|---|---|
| **Language** | Java 21+ | **Kotlin 1.9+ / 2.0+** |
| **UI Framework** | JavaFX layouts (VBox, StackPane) | **Jetpack Compose** (declarative Composables) |
| **Rendering Canvas** | JavaFX `Canvas` with `GraphicsContext` | **Compose `Canvas` Component** with `DrawScope` |
| **Game Loop** | JavaFX `AnimationTimer` (60 FPS) | **`LaunchedEffect`** + **`withFrameMillis`** loop |
| **Concurrency** | Manual daemon threads (`Thread`) | **Kotlin Coroutines** (`Dispatchers.Default` & `Dispatchers.Main`) |
| **Audio Synthesizer** | `javax.sound.sampled.SourceDataLine` | **`android.media.AudioTrack`** in Streaming Mode |
| **Data Persistence** | Flat text file (`sort_pulse_scores.txt`) | **Preferences DataStore** (JSON Serialization or proto) |
| **Controls** | Keyboard Events (`A`, `D`, `ENTER`, `ESC`) | **Touch Screen Tap Gestures** & **On-Screen Controller Layout** |

---

## 2. Key Architectural Migrations

### 2.1 UI & Screen Flow (State-Driven Navigation)
In JavaFX, the application switches views by mutating the style and layout children of a root `StackPane`. On Android, we will leverage **State-Driven Rendering** inside a single `MainActivity`. A state variable (e.g., `ScreenState` enum: `Menu`, `Tutorial`, `Game`, `Leaderboard`) will control which Composable is placed in the composition.

### 2.2 Graphics & Interpolation Game Loop
The desktop game utilizes linear interpolation (LERP) inside a 60 FPS animation timer to smoothly slide sorting blocks. 
- **The Issue:** Android devices have variable refresh rates (60Hz, 90Hz, 120Hz). A fixed frame increment could make the game speed inconsistent.
- **The Solution:** Use Compose's `withFrameMillis { frameTime -> ... }` inside a coroutine loop. It suspends until the next frame draw and provides the system time. Calculate the delta time to scale LERP transitions and particle velocities.

### 2.3 Audio Synthesis (`AudioTrack` in Kotlin)
The desktop project writes raw PCM bytes (synthesized on-the-fly) to a `SourceDataLine`. 
- **The Issue:** Standard Java Sound APIs are completely absent in Android.
- **The Solution:** Use Android's low-latency `AudioTrack` API. 
  - Construct an `AudioTrack` with `AudioAttributes.USAGE_GAME` and `AudioFormat.ENCODING_PCM_16BIT`.
  - Use Kotlin Coroutines on `Dispatchers.Default` to run the mixing loop.
  - **Optimization:** Unlike Java, which requires decomposing 16-bit short values into raw bytes (`byte[]`), Android `AudioTrack` accepts short arrays (`ShortArray`). We can write synthesized PCM floats directly to `ShortArray` and feed it to `AudioTrack.write()`, saving CPU cycles and reducing garbage collector overhead!

### 2.4 Mobile Controls Adaptation
Keyboard shortcuts (`A`/`D` to move cursor, `ENTER` to swap, `ESC` to go back) must be adapted for touchscreens. Two layouts are recommended for Android:
1. **Direct Touch (Puzzle-Style - Recommended Default):**
   - Tap directly on any sorting block to place the cursor.
   - Tap a prominent floating "EXECUTE" or "SWAP" button (or double-tap the block) to execute the action.
   - This feels much faster and more natural on vertical mobile screens.
2. **Virtual Retro D-Pad Overlay (Arcade Theme):**
   - A virtual retro controller layout (Left/Right arrow pads, green "ENTER" button, and small "ESC" / "RESET" buttons) placed at the bottom of the screen.
   - This fits perfectly with the **GameBoy Retro** theme.

---

## 3. Persistent Storage (DataStore)
Instead of parsing a delimited comma-separated text file, we will serialize high score entries to local application storage. Using **Jetpack Preferences DataStore** with Kotlin Serialization (converting the score list to a JSON string) provides thread-safe, non-blocking I/O out-of-the-box.

---

## 4. Kotlin Code Translations & Templates

Below are code conversions of the core sub-systems from Java to Kotlin.

### 4.1 Domain Model: Polymorphic Block System
Kotlin allows us to simplify the Java hierarchy using primary constructors, custom properties, and abstract sealed classes.

```kotlin
package com.sortpulse.game.domain

import androidx.compose.ui.graphics.Color

sealed class BlockSegment(
    var rawValue: Int,
    var positionIndex: Int
) {
    abstract fun calculateSortWeight(): Double
    
    // Theme-compatible color rendering mapping
    abstract fun getBlockColor(theme: GameTheme): Color
}

class OddSegment(rawValue: Int, positionIndex: Int) : BlockSegment(rawValue, positionIndex) {
    override fun calculateSortWeight(): Double = rawValue * 1.25
    override fun getBlockColor(theme: GameTheme): Color = theme.unsortedColor
}

class EvenSegment(rawValue: Int, positionIndex: Int) : BlockSegment(rawValue, positionIndex) {
    override fun calculateSortWeight(): Double = rawValue + 5.5
    override fun getBlockColor(theme: GameTheme): Color = theme.accentColor
}
```

---

### 4.2 SoundManager (Android AudioTrack & Coroutines)
This replaces the heavy Java Mixer threads. It synthesizes sine-wave frequencies on-the-fly and writes them to a streaming `AudioTrack` using a thread-safe `CopyOnWriteArrayList` for active sound events.

```kotlin
package com.sortpulse.game.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin

object SoundManager {
    class ActiveTone(
        val hz: Double,
        val msecs: Int,
        val volume: Double,
        val pan: Double = 0.0 // -1.0 (left) to 1.0 (right)
    ) {
        val phaseStep = 2.0 * PI * hz / 44100.0
        var phase = 0.0
        val totalSamples = (44100.0 * (msecs / 1000.0)).toInt()
        var remainingSamples = totalSamples
        val fadeSamples = totalSamples / 10
    }

    private val activeTones = CopyOnWriteArrayList<ActiveTone>()
    private var mixerJob: Job? = null
    private var musicJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var musicRunning = false

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        startMixer()
    }

    fun stop() {
        stopMusic()
        mixerJob?.cancel()
        mixerJob = null
        audioTrack?.apply {
            try {
                stop()
                release()
            } catch (e: Exception) {}
        }
        audioTrack = null
    }

    private fun startMixer() {
        if (mixerJob != null) return
        mixerJob = scope.launch {
            val sampleRate = 44100
            val bufferSizeFrames = 1024 // ~23.2ms latency buffer
            
            val minBufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSizeInBytes = maxOf(minBufSize, bufferSizeFrames * 4)

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also { it.play() }

            // Allocate a ShortArray directly (stereo holds Left + Right interleaved)
            val shortBuffer = ShortArray(bufferSizeFrames * 2)

            while (isActive) {
                val currentTones = ArrayList(activeTones)
                
                for (f in 0 until bufferSizeFrames) {
                    var leftSum = 0.0
                    var rightSum = 0.0

                    for (tone in currentTones) {
                        if (tone.remainingSamples > 0) {
                            var envelope = 1.0
                            val currentSampleIdx = tone.totalSamples - tone.remainingSamples
                            
                            // 10% Fade envelope to prevent speaker popping/clicking
                            if (tone.fadeSamples > 0) {
                                if (currentSampleIdx < tone.fadeSamples) {
                                    envelope = currentSampleIdx.toDouble() / tone.fadeSamples
                                } else if (tone.remainingSamples < tone.fadeSamples) {
                                    envelope = tone.remainingSamples.toDouble() / tone.fadeSamples
                                }
                            }
                            
                            val sampleVal = sin(tone.phase) * tone.volume * envelope
                            tone.phase += tone.phaseStep
                            if (tone.phase > 2.0 * PI) {
                                tone.phase -= 2.0 * PI
                            }

                            val leftFactor = (1.0 - tone.pan).coerceIn(0.0, 1.0)
                            val rightFactor = (1.0 + tone.pan).coerceIn(0.0, 1.0)

                            leftSum += sampleVal * leftFactor
                            rightSum += sampleVal * rightFactor
                            tone.remainingSamples--
                        }
                    }

                    // Clamp values to prevent digital clipping distortion
                    leftSum = leftSum.coerceIn(-1.0, 1.0)
                    rightSum = rightSum.coerceIn(-1.0, 1.0)

                    // Convert to 16-bit Signed Short PCM values
                    shortBuffer[f * 2] = (leftSum * 32767.0).toInt().toShort()
                    shortBuffer[f * 2 + 1] = (rightSum * 32767.0).toInt().toShort()
                }

                // Clean up finished frequencies
                activeTones.removeAll { it.remainingSamples <= 0 }

                // Non-blocking streaming write
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            }
        }
    }

    fun playTone(hz: Double, msecs: Int, volume: Double, pan: Double = 0.0) {
        activeTones.add(ActiveTone(hz, msecs, volume, pan))
    }

    fun playSuccess(pan: Double = 0.0) {
        scope.launch {
            playTone(988.0, 40, 0.15, pan) // B5
            delay(40)
            playTone(1318.0, 80, 0.15, pan) // E6
        }
    }

    fun playFailure(pan: Double = 0.0) {
        scope.launch {
            for (hz in 350 downTo 100 step 25) {
                playTone(hz.toDouble(), 15, 0.15, pan)
                delay(15)
            }
        }
    }

    fun startMusic(isPlayingGetter: () -> Boolean, getTimerRatio: () -> Double) {
        if (musicRunning) return
        musicRunning = true
        musicJob = scope.launch {
            var chordIdx = 0
            val progressions = arrayOf(
                doubleArrayOf(220.0, 261.6, 329.6, 261.6), // Am
                doubleArrayOf(196.0, 246.9, 293.7, 246.9), // G
                doubleArrayOf(174.6, 220.0, 261.6, 220.0), // F
                doubleArrayOf(164.8, 207.7, 246.9, 207.7)  // E
            )

            while (musicRunning && isActive) {
                if (!isPlayingGetter()) {
                    delay(100)
                    continue
                }

                val chord = progressions[chordIdx]
                for (note in chord) {
                    if (!musicRunning || !isActive) break
                    
                    // Dynamically speed up arpeggios as time winds down
                    val timeRatio = getTimerRatio().coerceIn(0.0, 1.0)
                    val noteMsecs = 80
                    val totalMsecs = (200.0 + 250.0 * timeRatio).toInt()

                    playTone(note, noteMsecs, 0.02)
                    delay(totalMsecs.toLong())
                }
                chordIdx = (chordIdx + 1) % progressions.size
            }
        }
    }

    fun stopMusic() {
        musicRunning = false
        musicJob?.cancel()
        musicJob = null
    }
}
```

---

### 4.3 Jetpack Compose Game Screen skeleton (Visual Rendering Canvas)
This skeleton establishes the game canvas, rendering state, particle triggers, and the linear interpolation game loop synchronized with the screen refresh cycle.

```kotlin
package com.sortpulse.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sortpulse.game.domain.BlockSegment
import kotlinx.coroutines.isActive

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val theme = uiState.theme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.bg)
            .padding(16.dp)
    ) {
        // --- 1. HUD Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Timer: ${uiState.timer}s", color = theme.accent)
            Text("Score: ${uiState.score}", color = theme.text)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Interactive Canvas ---
        GameCanvas(
            blocks = uiState.blocks,
            cursorIndex = uiState.cursorIndex,
            particles = viewModel.particles,
            theme = theme,
            onBlockTapped = { index -> viewModel.onBlockSelected(index) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 3. Bottom Controls Area ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { viewModel.executeAction() },
                colors = ButtonDefaults.buttonColors(containerColor = theme.sorted)
            ) {
                Text("EXECUTE", color = Color.White)
            }
        }
    }
}

@Composable
fun GameCanvas(
    blocks: List<BlockSegment>,
    cursorIndex: Int,
    particles: List<ParticleState>,
    theme: GameTheme,
    onBlockTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep track of visual positions for LERP animations
    val visualXMap = remember { mutableStateMapOf<BlockSegment, Float>() }
    val visualYMap = remember { mutableStateMapOf<BlockSegment, Float>() }

    // Coroutine-based frame-rendering loop
    LaunchedEffect(blocks) {
        while (isActive) {
            withFrameMillis { frameTime ->
                // Interpolate segment positions towards target indices
                val boxWidth = 120f // base coordinate box sizing
                blocks.forEachIndexed { index, segment ->
                    val targetX = index * boxWidth
                    val currentX = visualXMap[segment] ?: targetX
                    visualXMap[segment] = currentX + (targetX - currentX) * 0.22f

                    val targetY = 0f
                    val currentY = visualYMap[segment] ?: targetY
                    visualYMap[segment] = currentY + (targetY - currentY) * 0.22f
                }
                
                // Update physics particle positions inside viewModel
                // viewModel.updateParticles()
            }
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(blocks) {
                detectTapGestures { offset ->
                    // Determine which block slot was tapped based on touch X coordinate
                    val tappedIndex = (offset.x / 120f).toInt()
                    if (tappedIndex in blocks.indices) {
                        onBlockTapped(tappedIndex)
                    }
                }
            }
    ) {
        val boxWidth = 100f
        val boxHeight = 180f

        // Draw active sorting blocks
        blocks.forEach { segment ->
            val drawX = visualXMap[segment] ?: 0f
            val drawY = visualYMap[segment] ?: 0f

            drawRect(
                color = segment.getBlockColor(theme),
                topLeft = Offset(drawX, drawY),
                size = Size(boxWidth, boxHeight)
            )
        }

        // Draw cursor indicator box
        val cursorX = cursorIndex * 120f
        drawRect(
            color = theme.accent,
            topLeft = Offset(cursorX - 5f, -5f),
            size = Size(boxWidth + 10f, boxHeight + 10f),
            style = Stroke(width = 4f)
        )

        // Draw dynamic vector particles
        particles.forEach { particle ->
            drawCircle(
                color = particle.color.copy(alpha = particle.alpha),
                center = Offset(particle.x, particle.y),
                radius = particle.size
            )
        }
    }
}
```

---

## 5. Migration Execution Strategy

We recommend executing the relocation in the following logical sequence:

1. **Setup Project:** Create a new Android Studio project templates utilizing **Jetpack Compose** (Activity/Gradle configuration). Include Kotlin Serialization.
2. **Translate Domain:** Port `BlockSegment` hierarchy, `PuzzleRow` container, and `SortingStep` data model to Kotlin classes.
3. **Core Algorithmic Logic:** Port `GridSorter` class. Verify its sorting step tracers through JUnit unit tests on JVM.
4. **Implement Synth Audio:** Implement the `SoundManager` class using `AudioTrack` and Kotlin Coroutines. Test sound sweeps and arpeggio tempos using a minimal test activity.
5. **View Model & Game Engine State:** Build the `GameViewModel`. Expose game states (`timer`, `score`, `grid`) using Kotlin StateFlows (`MutableStateFlow`). Integrate game rules, penalty checks, and high score checks.
6. **Compose UI Layers:** Build screen composables: Menu, Visual Guides (animated slider overlays), gameplay canvas, and leaderboards. Integrate local Preference DataStore file writing.
7. **Refine & Polish:** Calibrate LERP values, test touchscreen cursor drag & tap gestures, verify audio thread interrupts under lifecycle changes (`onPause`, `onResume`), and override display options to lock orientation if needed.
