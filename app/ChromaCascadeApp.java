package app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.ArcType;


import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Sort Pulse: Timed Puzzle Blitz Engine
 * A frantic time-attack 2D arcade puzzle engine written from scratch.
 * Dekeying falling matrix grids in favor of a variable-length horizontal block sorting loop
 * evaluated against a continuous countdown clock.
 */
public class ChromaCascadeApp extends Application {

    private static ChromaCascadeController controllerInstance;
    private static VBox activePauseOverlay = null;

    // --- Step Domain Class ---
    public static class SortingStep {
        public int correctIndex;
        public int targetIndex;
        public int nextCursor;
        public int startIndex;
        public int wrapIndex;
        public boolean[] greenBlocks;
        public String description;

        // Tracking fields for partition and active pointers
        public int activeLeft = -1;
        public int activeRight = -1;
        public int pivotIndex = -1;
        public int headA = -1;
        public int headB = -1;
        public int mergeTarget = -1;
        public int mid = -1;
    }

    // --- Theme Config System ---
    public static class Theme {
        public final String name;
        public final Color bg;
        public final Color panelBg;
        public final Color border;
        public final Color sorted;
        public final Color unsorted;
        public final Color accent;
        public final Color text;
        public final Color textMuted;

        // CSS Hex string representations
        public final String bgHex;
        public final String panelBgHex;
        public final String borderHex;
        public final String sortedHex;
        public final String unsortedHex;
        public final String accentHex;
        public final String textHex;
        public final String textMutedHex;

        public Theme(String name, String bg, String panelBg, String border, String sorted, String unsorted, String accent, String text, String textMuted) {
            this.name = name;
            this.bg = Color.web(bg);
            this.panelBg = Color.web(panelBg);
            this.border = Color.web(border);
            this.sorted = Color.web(sorted);
            this.unsorted = Color.web(unsorted);
            this.accent = Color.web(accent);
            this.text = Color.web(text);
            this.textMuted = Color.web(textMuted);

            this.bgHex = bg;
            this.panelBgHex = panelBg;
            this.borderHex = border;
            this.sortedHex = sorted;
            this.unsortedHex = unsorted;
            this.accentHex = accent;
            this.textHex = text;
            this.textMutedHex = textMuted;
        }
    }

    public static final java.util.Map<String, Theme> THEMES = new java.util.LinkedHashMap<>();
    public static boolean isCustomFontLoaded = false;
    public static long lastGameStartTime = 0;
    static {
        THEMES.put("Classic Neon", new Theme("Classic Neon", "#0b0f19", "#0f172a", "#1e293b", "#10b981", "#334155", "#f59e0b", "#f8fafc", "#64748b"));
        THEMES.put("GameBoy Retro", new Theme("GameBoy Retro", "#cadc9f", "#8b9c6a", "#475230", "#475230", "#8b9c6a", "#1e2412", "#1e2412", "#475230"));
        
        try (java.io.InputStream is = ChromaCascadeApp.class.getResourceAsStream("/app/PressStart2P-Regular.ttf")) {
            if (is != null) {
                if (Font.loadFont(is, 12) != null) {
                    isCustomFontLoaded = true;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private static final java.util.Map<Double, Font> fontCache = new java.util.HashMap<>();

    public static Font getThemeFont(String family, FontWeight weight, double size, boolean isGB) {
        if (isGB) {
            double scaledSize = Math.round(size * 0.72);
            if (scaledSize < 6) scaledSize = 6;
            
            Font cached = fontCache.get(scaledSize);
            if (cached != null) {
                return cached;
            }
            
            if (isCustomFontLoaded) {
                try (java.io.InputStream is = ChromaCascadeApp.class.getResourceAsStream("/app/PressStart2P-Regular.ttf")) {
                    if (is != null) {
                        Font font = Font.loadFont(is, scaledSize);
                        if (font != null) {
                            fontCache.put(scaledSize, font);
                            return font;
                        }
                    }
                } catch (Exception e) {
                    // Fallback
                }
            }
            Font fallback = Font.font("Courier New", FontWeight.BOLD, Math.round(size * 1.1));
            fontCache.put(scaledSize, fallback);
            return fallback;
        }
        return Font.font(family, weight, size);
    }

    private static void styleMenuButton(Button btn, Theme theme, String hoverHex) {
        boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
        String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");
        String fontSize = isGB ? "10px" : "14px";
        String hoverBg = isGB ? theme.textHex : hoverHex;
        String hoverText = isGB ? theme.bgHex : theme.bgHex;
        
        btn.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + fontSize + "; -fx-padding: 12px 30px; -fx-background-radius: 4px; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px; -fx-min-width: 280; -fx-cursor: hand;");
        
        btn.setOnMouseEntered(e -> {
            SoundManager.playHover();
            String effect = isGB ? "" : " -fx-effect: dropshadow(three-pass-box, " + hoverHex + "4D, 8, 0, 0, 0);";
            btn.setStyle("-fx-background-color: " + hoverBg + "; -fx-text-fill: " + hoverText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + fontSize + "; -fx-padding: 12px 30px; -fx-background-radius: 4px; -fx-border-color: " + hoverBg + "; -fx-border-width: 1.5px; -fx-min-width: 280; -fx-cursor: hand;" + effect);
        });
        
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + fontSize + "; -fx-padding: 12px 30px; -fx-background-radius: 4px; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px; -fx-min-width: 280; -fx-cursor: hand;");
        });
    }

    private static void applyTheme(String themeName, StackPane rootContainer, VBox menuLayout, VBox leaderboardLayout, VBox gameLayout, 
            Label menuTitle, Label menuSubtitle, Label themeLabel, ComboBox<String> themeCb, CheckBox practiceModeCb, 
            Label lbTitle, Button backBtn, ListView<String> logView, Label timerVal, Label scoreVal, Label modeLabel, Text controlGuide, 
            Button selectionBtn, Button quickBtn, Button mergeBtn, Button bubbleBtn, Button insertionBtn, Button leaderboardBtn) {
        
        Theme theme = THEMES.getOrDefault(themeName, THEMES.get("Classic Neon"));
        boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
        String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");
        String fontMono = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Consolas', monospace");
        
        // Root backgrounds
        String smoothStyle = isGB ? "; -fx-font-smoothing-type: gray;" : "";
        rootContainer.setStyle("-fx-background-color: " + theme.bgHex + smoothStyle);
        menuLayout.setStyle("-fx-background-color: " + theme.bgHex + smoothStyle);
        leaderboardLayout.setStyle("-fx-background-color: " + theme.bgHex + smoothStyle);
        gameLayout.setStyle("-fx-background-color: " + theme.bgHex + smoothStyle);
        
        // Titles and Text headers
        if (isGB) {
            menuTitle.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + ";");
            menuSubtitle.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 8px; -fx-text-fill: " + theme.textMutedHex + "; -fx-padding: -10px 0 10px 0; -fx-font-weight: bold;");
            themeLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: " + theme.textMutedHex + ";");
            practiceModeCb.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 9px; -fx-text-fill: " + theme.textHex + "; -fx-font-weight: bold;");
            lbTitle.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + ";");
        } else {
            menuTitle.setStyle("-fx-font-family: 'Segoe UI', 'Outfit', sans-serif; -fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + "; -fx-effect: dropshadow(three-pass-box, " + theme.accentHex + "66, 12, 0, 0, 0);");
            menuSubtitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: " + theme.textMutedHex + "; -fx-padding: -15px 0 10px 0;");
            themeLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + theme.textMutedHex + ";");
            practiceModeCb.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: " + theme.textHex + ";");
            lbTitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + ";");
        }
        
        // Dropdown selection style
        themeCb.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px; -fx-background-radius: 4px; -fx-border-radius: 4px; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + (isGB ? "9px" : "12px") + ";");
        
        // Back Button
        backBtn.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-padding: 10px 24px; -fx-background-radius: 4px; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px; -fx-cursor: hand;");
        backBtn.setOnMouseEntered(e -> {
            SoundManager.playHover();
            backBtn.setStyle("-fx-background-color: " + theme.textHex + "; -fx-text-fill: " + theme.bgHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-padding: 10px 24px; -fx-background-radius: 4px; -fx-border-color: " + theme.textHex + "; -fx-border-width: 1.5px; -fx-cursor: hand;");
        });
        backBtn.setOnMouseExited(e -> backBtn.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-padding: 10px 24px; -fx-background-radius: 4px; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px; -fx-cursor: hand;"));

        // HUD panel components
        timerVal.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: " + (isGB ? "24px" : "44px") + "; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + ";");
        scoreVal.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: " + (isGB ? "10px" : "16px") + "; -fx-font-weight: bold; -fx-text-fill: " + theme.textHex + ";");
        modeLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-font-weight: bold; -fx-text-fill: " + theme.sortedHex + ";");
        controlGuide.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "8px" : "11px") + "; -fx-fill: " + theme.textMutedHex + "; -fx-font-weight: bold;");

        // Log View list
        logView.setStyle("-fx-background-color: " + theme.panelBgHex + "; -fx-control-inner-background: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textHex + "; -fx-font-family: " + fontMono + "; -fx-font-size: " + (isGB ? "8px" : "11px") + "; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1.5px;");

        // Menu buttons (styled according to algorithm accent colors)
        styleMenuButton(selectionBtn, theme, "#10b981"); // Emerald Green
        styleMenuButton(quickBtn, theme, "#f59e0b");     // Amber Gold
        styleMenuButton(mergeBtn, theme, "#3b82f6");     // Dodge Blue
        styleMenuButton(bubbleBtn, theme, "#ec4899");    // Hot Pink
        styleMenuButton(insertionBtn, theme, "#8b5cf6"); // Purple
        styleMenuButton(leaderboardBtn, theme, "#a855f7");// Violet
    }

    // --- Retro Sound Synthesizer Engine ---
    public static class SoundManager {
        private static class ActiveTone {
            final double hz;
            final double phaseStep;
            double phase = 0.0;
            final double volume;
            final double pan; // -1.0 (left) to 1.0 (right)
            int remainingSamples;
            final int totalSamples;
            final int fadeSamples;

            ActiveTone(double hz, int msecs, double volume, double pan) {
                this.hz = hz;
                this.phaseStep = 2.0 * Math.PI * hz / 44100.0;
                this.volume = volume;
                this.pan = pan;
                this.totalSamples = (int) (44100.0 * (msecs / 1000.0));
                this.remainingSamples = this.totalSamples;
                this.fadeSamples = this.totalSamples / 10;
            }
        }

        private static final java.util.List<ActiveTone> activeTones = new java.util.ArrayList<>();
        private static Thread mixerThread;
        private static boolean mixerRunning = false;
        private static SourceDataLine mixerLine;

        static {
            startMixer();
        }

        private static synchronized void startMixer() {
            if (mixerRunning) return;
            mixerRunning = true;
            mixerThread = new Thread(() -> {
                float sampleRate = 44100f;
                AudioFormat af = new AudioFormat(sampleRate, 16, 2, true, false);
                int bufferSizeFrames = 1024; // ~23.2ms latency (prevents buffer underruns)
                byte[] buf = new byte[bufferSizeFrames * 4];

                while (mixerRunning) {
                    if (mixerLine == null || !mixerLine.isOpen()) {
                        try {
                            mixerLine = AudioSystem.getSourceDataLine(af);
                            mixerLine.open(af, 16384);
                            mixerLine.start();
                        } catch (Exception e) {
                            try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
                            continue;
                        }
                    }

                    try {
                        // Mix active tones
                        java.util.List<ActiveTone> localTones;
                        synchronized (activeTones) {
                            localTones = new java.util.ArrayList<>(activeTones);
                        }

                        for (int f = 0; f < bufferSizeFrames; f++) {
                            double leftSum = 0.0;
                            double rightSum = 0.0;

                            for (ActiveTone tone : localTones) {
                                if (tone.remainingSamples > 0) {
                                    double envelope = 1.0;
                                    int currentSampleIdx = tone.totalSamples - tone.remainingSamples;
                                    if (tone.fadeSamples > 0) {
                                        if (currentSampleIdx < tone.fadeSamples) {
                                            envelope = (double) currentSampleIdx / tone.fadeSamples;
                                        } else if (tone.remainingSamples < tone.fadeSamples) {
                                            envelope = (double) tone.remainingSamples / tone.fadeSamples;
                                        }
                                    }
                                    double sampleVal = Math.sin(tone.phase) * tone.volume * envelope;
                                    tone.phase += tone.phaseStep;
                                    if (tone.phase > 2.0 * Math.PI) {
                                        tone.phase -= 2.0 * Math.PI;
                                    }
                                    
                                    // Linear panning
                                    double leftFactor = Math.max(0.0, Math.min(1.0, 1.0 - tone.pan));
                                    double rightFactor = Math.max(0.0, Math.min(1.0, 1.0 + tone.pan));

                                    leftSum += sampleVal * leftFactor;
                                    rightSum += sampleVal * rightFactor;
                                    tone.remainingSamples--;
                                }
                            }

                            // Clamp to prevent digital clipping
                            if (leftSum > 1.0) leftSum = 1.0;
                            else if (leftSum < -1.0) leftSum = -1.0;
                            if (rightSum > 1.0) rightSum = 1.0;
                            else if (rightSum < -1.0) rightSum = -1.0;

                            short leftShort = (short) (leftSum * 32767.0);
                            short rightShort = (short) (rightSum * 32767.0);

                            int byteIdx = f * 4;
                            buf[byteIdx] = (byte) (leftShort & 0xFF);
                            buf[byteIdx + 1] = (byte) ((leftShort >> 8) & 0xFF);
                            buf[byteIdx + 2] = (byte) (rightShort & 0xFF);
                            buf[byteIdx + 3] = (byte) ((rightShort >> 8) & 0xFF);
                        }

                        // Remove finished tones
                        synchronized (activeTones) {
                            activeTones.removeIf(t -> t.remainingSamples <= 0);
                        }

                        mixerLine.write(buf, 0, buf.length);
                    } catch (Exception ex) {
                        try {
                            if (mixerLine != null) {
                                try { mixerLine.close(); } catch (Exception e) {}
                                mixerLine = null;
                            }
                            Thread.sleep(100);
                        } catch (InterruptedException ie) {
                            break;
                        }
                    }
                }

                if (mixerLine != null) {
                    try {
                        mixerLine.stop();
                        mixerLine.close();
                    } catch (Exception e) {}
                }
            });
            mixerThread.setDaemon(true);
            mixerThread.setName("ChromaCascade-AudioMixer");
            mixerThread.start();
        }

        private static void playTone(int hz, int msecs, double volume) {
            playTone(hz, msecs, volume, 0.0);
        }

        private static void playTone(int hz, int msecs, double volume, double pan) {
            synchronized (activeTones) {
                activeTones.add(new ActiveTone(hz, msecs, volume, pan));
            }
        }

        public static void playSuccess() {
            playSuccess(0.0);
        }

        public static void playSuccess(double pan) {
            new Thread(() -> {
                try {
                    playTone(988, 40, 0.15, pan);  // B5 (bright start)
                    Thread.sleep(40);
                    playTone(1318, 80, 0.15, pan); // E6 (chime finish)
                } catch (InterruptedException e) {}
            }).start();
        }

        public static void playFailure() {
            playFailure(0.0);
        }

        public static void playFailure(double pan) {
            new Thread(() -> {
                try {
                    // Rapid descending slide sweep
                    for (int hz = 350; hz >= 100; hz -= 25) {
                        playTone(hz, 15, 0.15, pan);
                        Thread.sleep(15);
                    }
                } catch (InterruptedException e) {}
            }).start();
        }

        public static void playWaveClear() {
            new Thread(() -> {
                try {
                    int[] notes = {523, 784, 523, 659, 784, 1046}; // C5, G5, C5, E5, G5, C6
                    int[] durations = {80, 80, 80, 80, 80, 250};
                    for (int i = 0; i < notes.length; i++) {
                        playTone(notes[i], durations[i], 0.15);
                        Thread.sleep(durations[i] - 10);
                    }
                } catch (InterruptedException e) {}
            }).start();
        }

        public static void playGameOver() {
            new Thread(() -> {
                try {
                    playTone(392, 150, 0.18); // G4
                    Thread.sleep(150);
                    playTone(349, 150, 0.18); // F4
                    Thread.sleep(150);
                    playTone(311, 150, 0.18); // Eb4
                    Thread.sleep(150);
                    playTone(261, 300, 0.22); // C4
                } catch (InterruptedException e) {}
            }).start();
        }

        public static void playClick() {
            playClick(0.0);
        }

        public static void playClick(double pan) {
            playTone(1200, 10, 0.08, pan); // Tiny high pitch click
        }

        public static void playHover() {
            playTone(1000, 8, 0.03); // Soft high pitch click for hovers
        }

        public static void playMenuSelect() {
            new Thread(() -> {
                try {
                    playTone(600, 30, 0.12);
                    Thread.sleep(30);
                    playTone(900, 40, 0.12);
                } catch (InterruptedException e) {}
            }).start();
        }

        private static Thread musicThread;
        private static boolean musicRunning = false;

        public static synchronized void startMusic(ChromaCascadeModel model) {
            if (musicRunning) return;
            musicRunning = true;
            musicThread = new Thread(() -> {
                int chordIdx = 0;

                try {
                    while (musicRunning) {
                        if (!model.getGameState().equalsIgnoreCase("PLAYING") || model.isGameOver()) {
                            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                            continue;
                        }

                        // Dynamically choose progression based on active algorithm
                        String algo = model.getTargetAlgorithm();
                        int[][] progressions;
                        if (algo.equalsIgnoreCase("Bubble Sort")) {
                            progressions = new int[][] {
                                {261, 329, 392, 329}, // C
                                {349, 440, 523, 440}, // F
                                {392, 494, 587, 494}, // G
                                {349, 440, 523, 440}  // F
                            };
                        } else if (algo.equalsIgnoreCase("Insertion Sort")) {
                            progressions = new int[][] {
                                {220, 220, 293, 293}, // Am - Dm shuffle
                                {196, 196, 246, 246}, // G - B shuffle
                                {220, 220, 293, 293},
                                {329, 329, 329, 329}  // E transition
                            };
                        } else if (algo.equalsIgnoreCase("Quick Sort")) {
                            progressions = new int[][] {
                                {220, 261, 329, 392}, // Am7
                                {293, 349, 440, 523}, // Dm7
                                {329, 392, 494, 587}, // Em7
                                {220, 261, 329, 392}
                            };
                        } else if (algo.equalsIgnoreCase("Merge Sort")) {
                            progressions = new int[][] {
                                {220, 329, 261, 392}, // Alternating low-high split
                                {174, 261, 220, 329},
                                {196, 293, 246, 369},
                                {164, 246, 220, 329}
                            };
                        } else {
                            // Selection Sort (Default)
                            progressions = new int[][] {
                                {220, 261, 329, 261}, // Am
                                {196, 246, 293, 246}, // G
                                {174, 220, 261, 220}, // F
                                {164, 207, 246, 207}  // E
                            };
                        }

                        if (chordIdx >= progressions.length) {
                            chordIdx = 0;
                        }

                        int[] chord = progressions[chordIdx];
                        for (int note : chord) {
                            if (!musicRunning || !model.getGameState().equalsIgnoreCase("PLAYING") || model.isGameOver()) break;
                            
                            double timeRatio = model.isPracticeMode() ? 1.0 : (double) model.getCountdownTimer() / 20.0;
                            if (timeRatio > 1.0) timeRatio = 1.0;
                            
                            int noteMsecs = 80;
                            int totalMsecs = (int) (200.0 + 250.0 * timeRatio);

                            playTone(note, noteMsecs, 0.02);

                            try {
                                Thread.sleep(totalMsecs);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        chordIdx = (chordIdx + 1) % progressions.length;
                    }
                } catch (Exception e) {
                    // Fail silently
                }
            });
            musicThread.setDaemon(true);
            musicThread.setName("ChromaCascade-MusicSequencer");
            musicThread.start();
        }

        public static synchronized void stopMusic() {
            musicRunning = false;
            if (musicThread != null) {
                musicThread.interrupt();
                musicThread = null;
            }
        }
    }

    public static void logStatus(String message) {
        if (controllerInstance != null) {
            Platform.runLater(() -> controllerInstance.addLogMessage(message));
        } else {
            System.out.println(message);
        }
    }

    // --- Domain Hierarchy ---

    public static abstract class BlockSegment {
        private int rawValue;
        private int positionIndex;

        public BlockSegment(int rawValue, int positionIndex) {
            this.rawValue = rawValue;
            this.positionIndex = positionIndex;
        }

        public int getRawValue() {
            return rawValue;
        }

        public void setRawValue(int rawValue) {
            this.rawValue = rawValue;
        }

        public int getPositionIndex() {
            return positionIndex;
        }

        public void setPositionIndex(int positionIndex) {
            this.positionIndex = positionIndex;
        }

        public abstract double calculateSortWeight();
    }

    public static class OddSegment extends BlockSegment {
        public OddSegment(int rawValue, int positionIndex) {
            super(rawValue, positionIndex);
        }

        @Override
        public double calculateSortWeight() {
            return getRawValue() * 1.25;
        }
    }

    public static class EvenSegment extends BlockSegment {
        public EvenSegment(int rawValue, int positionIndex) {
            super(rawValue, positionIndex);
        }

        @Override
        public double calculateSortWeight() {
            return getRawValue() + 5.5;
        }
    }

    // Rigid primitive container class locked to active random length
    public static class PuzzleRow {
        private BlockSegment[] currentSet;
        private BlockSegment[] overflowStack = new BlockSegment[5];
        private int overflowCount = 0;

        public PuzzleRow(int size) {
            this.currentSet = new BlockSegment[size];
        }

        public BlockSegment[] getCurrentSet() {
            return currentSet;
        }

        public void setCurrentSet(BlockSegment[] currentSet) {
            this.currentSet = currentSet;
        }

        public BlockSegment[] getOverflowStack() {
            return overflowStack;
        }

        public int getOverflowCount() {
            return overflowCount;
        }

        public void setSegment(int index, BlockSegment segment) {
            try {
                if (index < 0 || index >= currentSet.length) {
                    throw new ArrayIndexOutOfBoundsException("Index " + index + " out of bounds for PuzzleRow segments array of size " + currentSet.length);
                }
                currentSet[index] = segment;
                if (segment != null) {
                    segment.setPositionIndex(index);
                }
            } catch (ArrayIndexOutOfBoundsException e) {
                handleOverflowGracefully(segment, e.getMessage());
            }
        }

        // Method simulating external injection / updates when row is active
        public void simulateExternalUpdate(BlockSegment segment, int index) {
            try {
                if (index < 0 || index >= currentSet.length) {
                    throw new ArrayIndexOutOfBoundsException("Simulation update index " + index + " exceeds capacity " + currentSet.length);
                }
                currentSet[index] = segment;
                if (segment != null) {
                    segment.setPositionIndex(index);
                }
            } catch (Exception e) {
                handleOverflowGracefully(segment, e.getMessage());
            }
        }

        private void handleOverflowGracefully(BlockSegment segment, String errorDetail) {
            if (overflowCount < 5) {
                overflowStack[overflowCount] = segment;
                overflowCount++;
                ChromaCascadeApp.logStatus("OVERFLOW WARNING: Catch-safe stack captured segment (Val: " 
                        + (segment != null ? segment.getRawValue() : "null") + ") due to exception: [" + errorDetail + "]. Slot: " + overflowCount + "/5");
            } else {
                ChromaCascadeApp.logStatus("OVERFLOW CRITICAL: Safety Stack maximum limit (5) reached! Injected block dropped.");
            }
        }

        public void clearOverflow() {
            for (int i = 0; i < overflowStack.length; i++) {
                overflowStack[i] = null;
            }
            overflowCount = 0;
        }
    }

    // --- Model ---

    public static class ChromaCascadeModel {
        private String gameState = "MENU"; // "MENU", "PLAYING", "GAME_OVER"
        private java.util.List<SortingStep> steps = new java.util.ArrayList<>();
        private int currentStep = 0;
        private int completedWavesCount = 0;
        private int waveErrors = 0;
        private int errorFlashFrames = 0;
        private boolean practiceMode = false;
        private int comboCount = 0;
        private boolean enteringInitials = false;
        private String playerInitials = "";

        private PuzzleRow puzzleRow;
        private int activeSegmentCursor = 0;
        private int score = 0;
        private int countdownTimer = 60; // 60 seconds starting clock
        private long lastSortDurationNs = 0;
        private String lastSortAlgorithm = "Selection Sort";
        private String lastCompletionMethod = "Selection Sort";
        private String targetAlgorithm = "Selection Sort";
        private int sortedCount = 0;
        private java.util.List<String> moveHistory = new java.util.ArrayList<>();
        private boolean gameOver = false;

        // Visual freeze indicator forCompleted banner pop-up
        private int freezeFrames = 0;
        private int startCountdown = -1; // -1 means inactive, 3, 2, 1, 0 are countdown states

        private String activeThemeName = "Classic Neon";

        public String getActiveThemeName() {
            return activeThemeName;
        }

        public void setActiveThemeName(String activeThemeName) {
            this.activeThemeName = activeThemeName;
        }

        public Theme getTheme() {
            return THEMES.getOrDefault(activeThemeName, THEMES.get("Classic Neon"));
        }

        private ObservableList<String> memoryRegisters = FXCollections.observableArrayList();
        private ObservableList<String> systemStatusLog = FXCollections.observableArrayList();

        public PuzzleRow getPuzzleRow() {
            return puzzleRow;
        }

        public void setPuzzleRow(PuzzleRow puzzleRow) {
            this.puzzleRow = puzzleRow;
        }

        public int getActiveSegmentCursor() {
            return activeSegmentCursor;
        }

        public void setActiveSegmentCursor(int activeSegmentCursor) {
            this.activeSegmentCursor = activeSegmentCursor;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public int getCountdownTimer() {
            return countdownTimer;
        }

        public void setCountdownTimer(int countdownTimer) {
            this.countdownTimer = countdownTimer;
        }

        public long getLastSortDurationNs() {
            return lastSortDurationNs;
        }

        public void setLastSortDurationNs(long lastSortDurationNs) {
            this.lastSortDurationNs = lastSortDurationNs;
        }

        public String getLastSortAlgorithm() {
            return lastSortAlgorithm;
        }

        public void setLastSortAlgorithm(String lastSortAlgorithm) {
            this.lastSortAlgorithm = lastSortAlgorithm;
        }

        public String getLastCompletionMethod() {
            return lastCompletionMethod;
        }

        public void setLastCompletionMethod(String lastCompletionMethod) {
            this.lastCompletionMethod = lastCompletionMethod;
        }

        public String getTargetAlgorithm() {
            return targetAlgorithm;
        }

        public void setTargetAlgorithm(String targetAlgorithm) {
            this.targetAlgorithm = targetAlgorithm;
        }

        public int getSortedCount() {
            return sortedCount;
        }

        public void setSortedCount(int sortedCount) {
            this.sortedCount = sortedCount;
        }

        public java.util.List<String> getMoveHistory() {
            return moveHistory;
        }

        public void setMoveHistory(java.util.List<String> moveHistory) {
            this.moveHistory = moveHistory;
        }

        public boolean isGameOver() {
            return gameOver;
        }

        public void setGameOver(boolean gameOver) {
            this.gameOver = gameOver;
        }

        public int getFreezeFrames() {
            return freezeFrames;
        }

        public void setFreezeFrames(int freezeFrames) {
            this.freezeFrames = freezeFrames;
        }

        public int getStartCountdown() {
            return startCountdown;
        }

        public void setStartCountdown(int startCountdown) {
            this.startCountdown = startCountdown;
        }

        public ObservableList<String> getMemoryRegisters() {
            return memoryRegisters;
        }

        public ObservableList<String> getSystemStatusLog() {
            return systemStatusLog;
        }

        public String getGameState() { return gameState; }
        public void setGameState(String gameState) { this.gameState = gameState; }
        public java.util.List<SortingStep> getSteps() { return steps; }
        public void setSteps(java.util.List<SortingStep> steps) { this.steps = steps; }
        public int getCurrentStep() { return currentStep; }
        public void setCurrentStep(int currentStep) { this.currentStep = currentStep; }
        public int getCompletedWavesCount() { return completedWavesCount; }
        public void setCompletedWavesCount(int completedWavesCount) { this.completedWavesCount = completedWavesCount; }
        public int getWaveErrors() { return waveErrors; }
        public void setWaveErrors(int waveErrors) { this.waveErrors = waveErrors; }
        public int getErrorFlashFrames() { return errorFlashFrames; }
        public void setErrorFlashFrames(int errorFlashFrames) { this.errorFlashFrames = errorFlashFrames; }
        public boolean isPracticeMode() { return practiceMode; }
        public void setPracticeMode(boolean practiceMode) { this.practiceMode = practiceMode; }
        public int getComboCount() { return comboCount; }
        public void setComboCount(int comboCount) { this.comboCount = comboCount; }
        public boolean isEnteringInitials() { return enteringInitials; }
        public void setEnteringInitials(boolean enteringInitials) { this.enteringInitials = enteringInitials; }
        public String getPlayerInitials() { return playerInitials; }
        public void setPlayerInitials(String playerInitials) { this.playerInitials = playerInitials; }
    }

    // --- Custom Sorter Engine ---

    public static class GridSorter {

        public static void sortRow(BlockSegment[] array, String algorithmName) {
            if (algorithmName.equalsIgnoreCase("Quick Sort")) {
                quickSort(array, 0, array.length - 1);
            } else if (algorithmName.equalsIgnoreCase("Merge Sort")) {
                mergeSort(array, 0, array.length - 1);
            } else if (algorithmName.equalsIgnoreCase("Bubble Sort")) {
                bubbleSort(array);
            } else if (algorithmName.equalsIgnoreCase("Insertion Sort")) {
                insertionSort(array);
            } else {
                selectionSort(array);
            }
        }

        // Selection Sort (Ascending)
        public static void selectionSort(BlockSegment[] array) {
            int n = array.length;
            for (int i = 0; i < n - 1; i++) {
                int minIdx = i;
                for (int j = i + 1; j < n; j++) {
                    if (compare(array[j], array[minIdx]) < 0) {
                        minIdx = j;
                    }
                }
                BlockSegment temp = array[i];
                array[i] = array[minIdx];
                array[minIdx] = temp;
                if (array[i] != null) array[i].setPositionIndex(i);
                if (array[minIdx] != null) array[minIdx].setPositionIndex(minIdx);
            }
        }

        // Bubble Sort (Ascending)
        public static void bubbleSort(BlockSegment[] array) {
            int n = array.length;
            boolean swapped;
            for (int i = 0; i < n - 1; i++) {
                swapped = false;
                for (int j = 0; j < n - i - 1; j++) {
                    if (compare(array[j], array[j + 1]) > 0) {
                        BlockSegment temp = array[j];
                        array[j] = array[j + 1];
                        array[j + 1] = temp;
                        if (array[j] != null) array[j].setPositionIndex(j);
                        if (array[j + 1] != null) array[j + 1].setPositionIndex(j + 1);
                        swapped = true;
                    }
                }
                if (!swapped) break;
            }
        }

        // Insertion Sort (Ascending)
        public static void insertionSort(BlockSegment[] array) {
            int n = array.length;
            for (int i = 1; i < n; i++) {
                BlockSegment key = array[i];
                int j = i - 1;
                while (j >= 0 && compare(array[j], key) > 0) {
                    array[j + 1] = array[j];
                    if (array[j + 1] != null) array[j + 1].setPositionIndex(j + 1);
                    j = j - 1;
                }
                array[j + 1] = key;
                if (array[j + 1] != null) array[j + 1].setPositionIndex(j + 1);
            }
        }

        // Quick Sort (Ascending)
        public static void quickSort(BlockSegment[] array, int low, int high) {
            if (low < high) {
                int pi = partition(array, low, high);
                quickSort(array, low, pi - 1);
                quickSort(array, pi + 1, high);
            }
        }

        private static int partition(BlockSegment[] array, int low, int high) {
            BlockSegment pivot = array[high];
            int i = low - 1;
            for (int j = low; j < high; j++) {
                if (compare(array[j], pivot) <= 0) {
                    i++;
                    BlockSegment temp = array[i];
                    array[i] = array[j];
                    array[j] = temp;
                    if (array[i] != null) array[i].setPositionIndex(i);
                    if (array[j] != null) array[j].setPositionIndex(j);
                }
            }
            BlockSegment temp = array[i + 1];
            array[i + 1] = array[high];
            array[high] = temp;
            if (array[i + 1] != null) array[i + 1].setPositionIndex(i + 1);
            if (array[high] != null) array[high].setPositionIndex(high);
            return i + 1;
        }

        // Merge Sort (Ascending)
        public static void mergeSort(BlockSegment[] array, int l, int r) {
            if (l < r) {
                int m = l + (r - l) / 2;
                mergeSort(array, l, m);
                mergeSort(array, m + 1, r);
                merge(array, l, m, r);
            }
        }

        private static void merge(BlockSegment[] array, int l, int m, int r) {
            int n1 = m - l + 1;
            int n2 = r - m;

            BlockSegment[] L = new BlockSegment[n1];
            BlockSegment[] R = new BlockSegment[n2];

            for (int i = 0; i < n1; ++i)
                L[i] = array[l + i];
            for (int j = 0; j < n2; ++j)
                R[j] = array[m + 1 + j];

            int i = 0, j = 0;
            int k = l;
            while (i < n1 && j < n2) {
                if (compare(L[i], R[j]) <= 0) {
                    array[k] = L[i];
                    i++;
                } else {
                    array[k] = R[j];
                    j++;
                }
                if (array[k] != null) array[k].setPositionIndex(k);
                k++;
            }

            while (i < n1) {
                array[k] = L[i];
                if (array[k] != null) array[k].setPositionIndex(k);
                i++;
                k++;
            }

            while (j < n2) {
                array[k] = R[j];
                if (array[k] != null) array[k].setPositionIndex(k);
                j++;
                k++;
            }
        }

        private static int compare(BlockSegment a, BlockSegment b) {
            if (a == null && b == null) return 0;
            if (a == null) return 1;  // null goes to the end
            if (b == null) return -1;
            return Integer.compare(a.getRawValue(), b.getRawValue());
        }

        public static int countInversions(BlockSegment[] row) {
            int inversions = 0;
            for (int i = 0; i < row.length; i++) {
                if (row[i] == null) continue;
                int valI = row[i].getRawValue();
                for (int j = i + 1; j < row.length; j++) {
                    if (row[j] == null) continue;
                    int valJ = row[j].getRawValue();
                    if (valI > valJ) {
                        inversions++;
                    }
                }
            }
            return inversions;
        }

        public static boolean isSegmentSorted(BlockSegment[] row, int index) {
            if (row[index] == null) return true;
            int val = row[index].getRawValue();

            // Compare to left neighbor
            if (index > 0 && row[index - 1] != null) {
                if (row[index - 1].getRawValue() > val) {
                    return false;
                }
            }

            // Compare to right neighbor
            if (index < row.length - 1 && row[index + 1] != null) {
                if (val > row[index + 1].getRawValue()) {
                    return false;
                }
            }

            return true;
        }

        public static void shiftElement(BlockSegment[] array, int from, int to) {
            if (from == to) return;
            BlockSegment target = array[from];
            if (from > to) {
                for (int i = from; i > to; i--) {
                    array[i] = array[i - 1];
                }
                array[to] = target;
            } else {
                for (int i = from; i < to; i++) {
                    array[i] = array[i + 1];
                }
                array[to] = target;
            }
            for (int i = 0; i < array.length; i++) {
                if (array[i] != null) {
                    array[i].setPositionIndex(i);
                }
            }
        }

        // --- Step Generators for User Gameplay ---

        private static int[] getSortedValues(BlockSegment[] array) {
            BlockSegment[] sorted = array.clone();
            selectionSort(sorted);
            int[] vals = new int[sorted.length];
            for (int i = 0; i < sorted.length; i++) {
                vals[i] = sorted[i].getRawValue();
            }
            return vals;
        }

        public static void shiftBooleanElement(boolean[] array, int from, int to) {
            if (from == to) return;
            boolean target = array[from];
            if (from > to) {
                for (int i = from; i > to; i--) {
                    array[i] = array[i - 1];
                }
                array[to] = target;
            } else {
                for (int i = from; i < to; i++) {
                    array[i] = array[i + 1];
                }
                array[to] = target;
            }
        }

        public static java.util.List<SortingStep> generateBubbleSortSteps(BlockSegment[] initial) {
            BlockSegment[] array = initial.clone();
            int[] sortedVals = getSortedValues(initial);
            java.util.List<SortingStep> steps = new java.util.ArrayList<>();
            int n = array.length;
            boolean swapped;
            for (int i = 0; i < n - 1; i++) {
                swapped = false;
                for (int j = 0; j < n - i - 1; j++) {
                    if (compare(array[j], array[j + 1]) > 0) {
                        SortingStep step = new SortingStep();
                        step.correctIndex = j;
                        step.targetIndex = j + 1;
                        step.startIndex = j;
                        step.nextCursor = j;
                        step.activeLeft = 0;
                        step.activeRight = n - i - 1;
                        step.description = String.format("Swap adjacent elements (value %d and %d) at indices %d and %d.",
                                                         array[j].getRawValue(), array[j + 1].getRawValue(), j, j + 1);

                        shiftElement(array, j, j + 1);
                        swapped = true;

                        step.greenBlocks = new boolean[n];
                        for (int k = 0; k < n; k++) {
                            step.greenBlocks[k] = (array[k].getRawValue() == sortedVals[k]);
                        }
                        steps.add(step);
                    }
                }
                if (!swapped) break;
            }
            return steps;
        }

        public static java.util.List<SortingStep> generateInsertionSortSteps(BlockSegment[] initial) {
            BlockSegment[] array = initial.clone();
            int[] sortedVals = getSortedValues(initial);
            java.util.List<SortingStep> steps = new java.util.ArrayList<>();
            int n = array.length;
            for (int i = 1; i < n; i++) {
                int j = i;
                while (j > 0 && compare(array[j], array[j - 1]) < 0) {
                    SortingStep step = new SortingStep();
                    step.correctIndex = j;
                    step.targetIndex = j - 1;
                    step.startIndex = j;
                    step.nextCursor = j - 1;
                    step.activeLeft = 0;
                    step.activeRight = i;
                    step.description = String.format("Insert element (value %d) leftwards to index %d.",
                                                     array[j].getRawValue(), j - 1);

                    shiftElement(array, j, j - 1);
                    j--;

                    step.greenBlocks = new boolean[n];
                    for (int k = 0; k < n; k++) {
                        step.greenBlocks[k] = (array[k].getRawValue() == sortedVals[k]);
                    }
                    steps.add(step);
                }
            }
            return steps;
        }

        public static java.util.List<SortingStep> generateSelectionSortSteps(BlockSegment[] initial) {
            BlockSegment[] array = initial.clone();
            int[] sortedVals = getSortedValues(initial);
            java.util.List<SortingStep> steps = new java.util.ArrayList<>();
            int n = array.length;
            for (int i = 0; i < n - 1; i++) {
                int minIdx = i;
                for (int j = i + 1; j < n; j++) {
                    if (compare(array[j], array[minIdx]) < 0) {
                        minIdx = j;
                    }
                }
                SortingStep step = new SortingStep();
                step.correctIndex = minIdx;
                step.targetIndex = i;
                step.nextCursor = (i + 1 < n) ? i + 1 : i;
                step.startIndex = i;
                step.wrapIndex = i;
                step.description = String.format("Select minimum element (value %d) at index %d and shift to %d.", array[minIdx].getRawValue(), minIdx, i);
                step.activeLeft = i;
                step.activeRight = n - 1;
                step.mergeTarget = i;
                
                shiftElement(array, minIdx, i);
                
                step.greenBlocks = new boolean[n];
                for (int k = 0; k < n; k++) {
                    step.greenBlocks[k] = (k <= i && array[k].getRawValue() == sortedVals[k]);
                }
                steps.add(step);
            }
            return steps;
        }

        public static java.util.List<SortingStep> generateQuickSortSteps(BlockSegment[] initial) {
            BlockSegment[] array = initial.clone();
            int[] sortedVals = getSortedValues(initial);
            java.util.List<SortingStep> steps = new java.util.ArrayList<>();
            boolean[] finalized = new boolean[array.length];
            quickSortTraceSteps(array, 0, array.length - 1, steps, finalized, sortedVals);
            return steps;
        }

        private static void quickSortTraceSteps(BlockSegment[] array, int low, int high, java.util.List<SortingStep> steps, boolean[] finalized, int[] sortedVals) {
            if (low >= high) {
                if (low >= 0 && low < array.length) {
                    finalized[low] = true;
                }
                return;
            }
            int pivotIdx = high;
            BlockSegment pivot = array[pivotIdx];
            int i = low - 1;
            for (int j = low; j < high; j++) {
                if (compare(array[j], pivot) <= 0) {
                    i++;
                    if (j != i) {
                        SortingStep step = new SortingStep();
                        step.correctIndex = j;
                        step.targetIndex = i;
                        step.nextCursor = (j + 1 < high) ? j + 1 : high;
                        step.startIndex = low;
                        step.wrapIndex = low;
                        step.description = String.format("Select element %d at index %d and shift to index %d.", array[j].getRawValue(), j, i);
                        step.activeLeft = low;
                        step.activeRight = high;
                        step.pivotIndex = pivotIdx;
                        step.mergeTarget = i;
                        
                        shiftElement(array, j, i);
                        shiftBooleanElement(finalized, j, i);
                        if (pivotIdx >= i && pivotIdx < j) {
                            pivotIdx++;
                        }
                        
                        step.greenBlocks = new boolean[array.length];
                        for (int k = 0; k < array.length; k++) {
                            step.greenBlocks[k] = finalized[k] && (array[k].getRawValue() == sortedVals[k]);
                        }
                        steps.add(step);
                    }
                }
            }
            int targetPivotIdx = i + 1;
            SortingStep step = new SortingStep();
            step.correctIndex = pivotIdx;
            step.targetIndex = targetPivotIdx;
            step.nextCursor = targetPivotIdx;
            step.startIndex = low;
            step.wrapIndex = low;
            step.description = String.format("Select pivot %d at index %d and shift to final index %d.", array[pivotIdx].getRawValue(), pivotIdx, targetPivotIdx);
            step.activeLeft = low;
            step.activeRight = high;
            step.pivotIndex = pivotIdx;
            step.mergeTarget = targetPivotIdx;
            
            shiftElement(array, pivotIdx, targetPivotIdx);
            shiftBooleanElement(finalized, pivotIdx, targetPivotIdx);
            finalized[targetPivotIdx] = true;
            
            step.greenBlocks = new boolean[array.length];
            for (int k = 0; k < array.length; k++) {
                step.greenBlocks[k] = finalized[k] && (array[k].getRawValue() == sortedVals[k]);
            }
            steps.add(step);

            quickSortTraceSteps(array, low, targetPivotIdx - 1, steps, finalized, sortedVals);
            quickSortTraceSteps(array, targetPivotIdx + 1, high, steps, finalized, sortedVals);
        }

        public static java.util.List<SortingStep> generateMergeSortSteps(BlockSegment[] initial) {
            BlockSegment[] array = initial.clone();
            int[] sortedVals = getSortedValues(initial);
            java.util.List<SortingStep> steps = new java.util.ArrayList<>();
            boolean[] finalized = new boolean[array.length];
            mergeSortTraceSteps(array, 0, array.length - 1, steps, finalized, sortedVals);
            return steps;
        }

        private static void mergeSortTraceSteps(BlockSegment[] array, int l, int r, java.util.List<SortingStep> steps, boolean[] finalized, int[] sortedVals) {
            if (l < r) {
                int m = l + (r - l) / 2;
                mergeSortTraceSteps(array, l, m, steps, finalized, sortedVals);
                mergeSortTraceSteps(array, m + 1, r, steps, finalized, sortedVals);
                mergeTraceSteps(array, l, m, r, steps, finalized, sortedVals);
            }
        }

        private static void mergeTraceSteps(BlockSegment[] array, int l, int m, int r, java.util.List<SortingStep> steps, boolean[] finalized, int[] sortedVals) {
            int p1 = l;
            int p2 = m + 1;
            for (int k = l; k <= r; k++) {
                if (p1 > m) {
                    break;
                } else if (p2 > r) {
                    break;
                } else {
                    if (compare(array[p1], array[p2]) <= 0) {
                        SortingStep step = new SortingStep();
                        step.correctIndex = p1;
                        step.targetIndex = k;
                        step.nextCursor = (p1 + 1 <= m) ? p1 + 1 : p2;
                        step.startIndex = l;
                        step.wrapIndex = l;
                        step.description = String.format("Select %d at index %d to merge at index %d.", array[p1].getRawValue(), p1, k);
                        step.activeLeft = l;
                        step.activeRight = r;
                        step.headA = p1;
                        step.headB = p2;
                        step.mergeTarget = k;
                        step.mid = m;
                        
                        step.greenBlocks = new boolean[array.length];
                        for (int x = 0; x < array.length; x++) {
                            step.greenBlocks[x] = finalized[x] && (array[x].getRawValue() == sortedVals[x]);
                        }
                        steps.add(step);
                        p1++;
                    } else {
                        SortingStep step = new SortingStep();
                        step.correctIndex = p2;
                        step.targetIndex = k;
                        step.nextCursor = (p2 + 1 <= r) ? p2 + 1 : p1;
                        step.startIndex = l;
                        step.wrapIndex = l;
                        step.description = String.format("Select %d at index %d and shift to merge index %d.", array[p2].getRawValue(), p2, k);
                        step.activeLeft = l;
                        step.activeRight = r;
                        step.headA = p1;
                        step.headB = p2;
                        step.mergeTarget = k;
                        step.mid = m;
                        
                        shiftElement(array, p2, k);
                        shiftBooleanElement(finalized, p2, k);
                        
                        step.greenBlocks = new boolean[array.length];
                        for (int x = 0; x < array.length; x++) {
                            step.greenBlocks[x] = finalized[x] && (array[x].getRawValue() == sortedVals[x]);
                        }
                        steps.add(step);
                        p1++;
                        m++;
                        p2++;
                    }
                }
            }
            for (int k = l; k <= r; k++) {
                finalized[k] = true;
            }
            if (!steps.isEmpty()) {
                boolean[] finalGreen = new boolean[array.length];
                for (int x = 0; x < array.length; x++) {
                    finalGreen[x] = finalized[x] && (array[x].getRawValue() == sortedVals[x]);
                }
                steps.get(steps.size() - 1).greenBlocks = finalGreen;
            }
        }
    }

    // --- View ---

    public static class ChromaCascadeView {
        private ChromaCascadeModel model;
        private Canvas canvas;
        private GraphicsContext gc;

        // HUD elements
        private Label scoreValLabel;
        private Label timerValLabel;
        private Label metricsValLabel;
        private Label targetValLabel;
        private ListView<String> logListView;
        
        private java.util.Map<BlockSegment, Double> visualXMap = new java.util.HashMap<>();
        private java.util.Map<BlockSegment, Double> visualYMap = new java.util.HashMap<>();
        private java.util.List<Particle> particles = new java.util.ArrayList<>();
        private java.util.List<FloatingText> floatingTexts = new java.util.ArrayList<>();

        private Canvas offscreenCanvas;
        private GraphicsContext offscreenGc;
        private Canvas pixelateCanvas;
        private GraphicsContext pixelateGc;

        public ChromaCascadeView(ChromaCascadeModel model) {
            this.model = model;
            this.canvas = new Canvas(800, 400);
            this.gc = canvas.getGraphicsContext2D();
        }

        public void clearVisuals() {
            visualXMap.clear();
            visualYMap.clear();
            floatingTexts.clear();
            particles.clear();
        }

        public void spawnFloatingText(double x, double y, String text, Color color) {
            floatingTexts.add(new FloatingText(x, y, text, color));
        }

        public void spawnParticles(double x, double y, Color color, int count) {
            Random r = new Random();
            for (int i = 0; i < count; i++) {
                double angle = r.nextDouble() * 2 * Math.PI;
                double speed = 0.5 + r.nextDouble() * 3.5;
                double vx = Math.cos(angle) * speed;
                double vy = Math.sin(angle) * speed - 1.5;
                double size = 3 + r.nextDouble() * 4;
                int maxLife = 20 + r.nextInt(25);
                particles.add(new Particle(x, y, vx, vy, color, size, maxLife));
            }
        }

        public Canvas getCanvas() {
            return canvas;
        }

        public void setScoreValLabel(Label scoreValLabel) {
            this.scoreValLabel = scoreValLabel;
        }

        public void setTimerValLabel(Label timerValLabel) {
            this.timerValLabel = timerValLabel;
        }

        public void setMetricsValLabel(Label metricsValLabel) {
            this.metricsValLabel = metricsValLabel;
        }

        public void setTargetValLabel(Label targetValLabel) {
            this.targetValLabel = targetValLabel;
        }

        public void setLogListView(ListView<String> logListView) {
            this.logListView = logListView;
        }

        public void draw() {
            Theme theme = model.getTheme();
            boolean isGameBoy = theme.name.equalsIgnoreCase("GameBoy Retro");

            // Draw background
            gc.setFill(theme.bg);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

            // Draw Canvas structural outline
            gc.setStroke(theme.border);
            gc.setLineWidth(2.0);
            gc.strokeRoundRect(10, 10, canvas.getWidth() - 20, canvas.getHeight() - 20, 8, 8);

            // Draw horizontal row segments
            PuzzleRow row = model.getPuzzleRow();
            if (row != null && row.getCurrentSet() != null) {
                BlockSegment[] set = row.getCurrentSet();
                int size = set.length;

                double totalWidth = 700.0;
                double boxHeight = 85.0;
                double boxWidth = totalWidth / size;
                double startX = (canvas.getWidth() - totalWidth) / 2.0;
                double startY = (canvas.getHeight() - boxHeight) / 2.0;

                // Extract step information if available
                int activeLeft = -1;
                int activeRight = -1;
                int pivotIndex = -1;
                int headA = -1;
                int headB = -1;
                int mergeTarget = -1;
                int mid = -1;

                if (model.getSteps() != null && !model.getSteps().isEmpty()) {
                    int stepIdx = model.getCurrentStep();
                    if (stepIdx < model.getSteps().size()) {
                        SortingStep step = model.getSteps().get(stepIdx);
                        activeLeft = step.activeLeft;
                        activeRight = step.activeRight;
                        pivotIndex = step.pivotIndex;
                        headA = step.headA;
                        headB = step.headB;
                        mergeTarget = step.mergeTarget;
                        mid = step.mid;
                    }
                }

                // Mode accent color helper
                String targetAlgo = model.getTargetAlgorithm();
                Color modeAccent = theme.accent;

                // 1. Draw dashed active sub-array boundary
                if (activeLeft != -1 && activeRight != -1 && model.getFreezeFrames() <= 0) {
                    if (targetAlgo.equalsIgnoreCase("Merge Sort")) {
                        // Draw Subarray A frame (upper level: startY - 60)
                        if (mid != -1 && mid >= activeLeft) {
                            double ax1 = startX + activeLeft * boxWidth + 2;
                            double ax2 = startX + (mid + 1) * boxWidth - 2;
                            double ay1 = startY - 60 - 10;
                            double ay2 = startY - 60 + boxHeight + 10;

                            gc.setStroke(theme.accent.deriveColor(0, 1, 1, 0.6));
                            gc.setLineWidth(1.2);
                            gc.setLineDashes(new double[]{4.0, 3.0});
                            gc.strokeRoundRect(ax1, ay1, ax2 - ax1, ay2 - ay1, 6, 6);
                            gc.setLineDashes(null);

                            gc.setFill(theme.accent.deriveColor(0, 1, 1, 0.7));
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                            gc.fillText("SUBARRAY A (SORTED)", ax1 + 6, ay1 - 4);
                        }

                        // Draw Subarray B frame (upper level: startY - 60)
                        if (mid != -1 && activeRight >= mid + 1) {
                            double bx1 = startX + (mid + 1) * boxWidth + 2;
                            double bx2 = startX + (activeRight + 1) * boxWidth - 2;
                            double by1 = startY - 60 - 10;
                            double by2 = startY - 60 + boxHeight + 10;

                            gc.setStroke(theme.accent.deriveColor(0, 1, 1, 0.6));
                            gc.setLineWidth(1.2);
                            gc.setLineDashes(new double[]{4.0, 3.0});
                            gc.strokeRoundRect(bx1, by1, bx2 - bx1, by2 - by1, 6, 6);
                            gc.setLineDashes(null);

                            gc.setFill(theme.accent.deriveColor(0, 1, 1, 0.7));
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                            gc.fillText("SUBARRAY B (SORTED)", bx1 + 6, by1 - 4);
                        }

                        // Draw Merged Output frame (lower level: startY + 60)
                        double ox1 = startX + activeLeft * boxWidth + 2;
                        double ox2 = startX + (activeRight + 1) * boxWidth - 2;
                        double oy1 = startY + 60 - 10;
                        double oy2 = startY + 60 + boxHeight + 10;

                        gc.setStroke(theme.sorted.deriveColor(0, 1, 1, 0.6));
                        gc.setLineWidth(1.2);
                        gc.setLineDashes(new double[]{4.0, 3.0});
                        gc.strokeRoundRect(ox1, oy1, ox2 - ox1, oy2 - oy1, 6, 6);
                        gc.setLineDashes(null);

                        gc.setFill(theme.sorted.deriveColor(0, 1, 1, 0.7));
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                        gc.fillText("MERGED OUTPUT SLOT", ox1 + 6, oy1 - 4);
                    } else {
                        double rx1 = startX + activeLeft * boxWidth + 2;
                        double rx2 = startX + (activeRight + 1) * boxWidth - 2;
                        double ry1 = startY - 15;
                        double ry2 = startY + boxHeight + 15;

                        gc.setStroke(theme.border.deriveColor(0, 1, 1, 0.6));
                        gc.setLineWidth(1.5);
                        gc.setLineDashes(new double[]{6.0, 4.0});
                        gc.strokeRoundRect(rx1, ry1, rx2 - rx1, ry2 - ry1, 6, 6);
                        gc.setLineDashes(null);

                        gc.setFill(theme.textMuted.deriveColor(0, 1, 1, 0.7));
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 9, isGameBoy));
                        gc.fillText("ACTIVE SUBARRAY", rx1 + 6, ry1 - 4);
                    }
                }

                for (int i = 0; i < size; i++) {
                    BlockSegment segment = set[i];
                    if (segment == null) continue;

                    double targetX = startX + i * boxWidth + 4;
                    double targetY = startY;
                    if (targetAlgo.equalsIgnoreCase("Merge Sort") && activeLeft != -1 && activeRight != -1 && model.getFreezeFrames() <= 0) {
                        if (i >= activeLeft && i < mergeTarget) {
                            targetY = startY + 60;
                        } else if (i >= mergeTarget && i <= activeRight) {
                            targetY = startY - 60;
                        }
                    }

                    final double finalX = targetX;
                    final double finalY = targetY;

                    double curX = visualXMap.computeIfAbsent(segment, k -> finalX);
                    double curY = visualYMap.computeIfAbsent(segment, k -> finalY);

                    curX += (targetX - curX) * 0.22;
                    curY += (targetY - curY) * 0.22;

                    visualXMap.put(segment, curX);
                    visualYMap.put(segment, curY);

                    double x = curX;
                    double y = curY;
                    double w = boxWidth - 8;
                    double h = boxHeight;

                    // Color based on pre-computed step greenBlocks
                    boolean isSorted = false;
                    if (model.getSteps() != null && !model.getSteps().isEmpty()) {
                        int stepIdx = model.getCurrentStep();
                        if (stepIdx == 0) {
                            isSorted = false;
                        } else if (stepIdx >= model.getSteps().size()) {
                            isSorted = true;
                        } else {
                            isSorted = model.getSteps().get(stepIdx - 1).greenBlocks[i];
                        }
                    } else {
                        isSorted = (i < model.getSortedCount());
                    }
                    
                    // Highlight configurations
                    boolean isPivot = (i == pivotIndex && model.getFreezeFrames() <= 0);
                    boolean isHeadA = (i == headA && model.getFreezeFrames() <= 0);
                    boolean isHeadB = (i == headB && model.getFreezeFrames() <= 0);

                    Color segmentColor = isSorted ? theme.sorted : theme.unsorted;

                    // Glow background shadow for sorted elements
                    if (isSorted) {
                        gc.setFill(segmentColor.deriveColor(0, 1, 1, 0.15));
                        gc.fillRoundRect(x - 2, y - 2, w + 4, h + 4, 6, 6);
                    }

                    // Block gradient
                    LinearGradient gradient = new LinearGradient(
                            0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                            new Stop(0, segmentColor.deriveColor(0, 1, 1.1, 0.95)),
                            new Stop(1, segmentColor.deriveColor(0, 1, 0.85, 0.95))
                    );
                    gc.setFill(gradient);
                    gc.fillRoundRect(x, y, w, h, 4, 4);

                    // Border (colored if active pivot or heads)
                    Color borderCol = segmentColor.deriveColor(0, 1, 1.2, 1.0);
                    if (isPivot) {
                        borderCol = Color.web("#ea580c"); // Orange
                    } else if (isHeadA || isHeadB) {
                        borderCol = theme.accent;
                    }
                    gc.setStroke(borderCol);
                    gc.setLineWidth(isPivot || isHeadA || isHeadB ? 2.0 : 1.0);
                    gc.strokeRoundRect(x, y, w, h, 4, 4);

                    // Selection cursor highlight
                    if (i == model.getActiveSegmentCursor() && model.getFreezeFrames() <= 0) {
                        gc.setStroke(theme.accent);
                        gc.setLineWidth(2.5);
                        gc.strokeRoundRect(x - 1, y - 1, w + 2, h + 2, 4, 4);
                    }

                    // Draw Badges above blocks
                    if (isPivot) {
                        double badgeW = Math.min(w, 42.0);
                        double badgeX = x + (w - badgeW) / 2.0;
                        gc.setFill(Color.web("#ea580c"));
                        gc.fillRoundRect(badgeX, y - 17, badgeW, 13, 3, 3);
                        gc.setFill(Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                        gc.fillText("PIVOT", badgeX + (badgeW - 25) / 2.0, y - 8);
                    } else if (isHeadA) {
                        double badgeW = Math.min(w, 42.0);
                        double badgeX = x + (w - badgeW) / 2.0;
                        gc.setFill(theme.accent);
                        gc.fillRoundRect(badgeX, y - 17, badgeW, 13, 3, 3);
                        gc.setFill(Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                        gc.fillText("HEAD A", badgeX + (badgeW - 30) / 2.0, y - 8);
                    } else if (isHeadB) {
                        double badgeW = Math.min(w, 42.0);
                        double badgeX = x + (w - badgeW) / 2.0;
                        gc.setFill(theme.accent);
                        gc.fillRoundRect(badgeX, y - 17, badgeW, 13, 3, 3);
                        gc.setFill(Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGameBoy));
                        gc.fillText("HEAD B", badgeX + (badgeW - 30) / 2.0, y - 8);
                    }

                    // Centered raw integer value
                    gc.setFill(theme.text);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 20, isGameBoy));
                    String valStr = String.valueOf(segment.getRawValue());
                    double charWidth = isGameBoy ? 12.0 : 10.0;
                    double textWidth = charWidth * valStr.length();
                    gc.fillText(valStr, x + (w - textWidth) / 2.0, y + 46);

                    // Weight detail
                    String weightStr = String.format("%.1f", segment.calculateSortWeight());
                    gc.setFont(getThemeFont("Consolas", FontWeight.NORMAL, 9.0, isGameBoy));
                    gc.setFill(theme.textMuted.deriveColor(0, 1, 1, 0.8));
                    double wStrWidth = 5.5 * weightStr.length();
                    gc.fillText(weightStr, x + (w - wStrWidth) / 2.0, y + h - 6);
                }

                // Draw greater than / less than comparison badge between Quick Sort cursor and pivot
                if (targetAlgo.equalsIgnoreCase("Quick Sort") && pivotIndex != -1 && model.getFreezeFrames() <= 0) {
                    int cursorIdx = model.getActiveSegmentCursor();
                    if (cursorIdx != -1 && cursorIdx != pivotIndex && cursorIdx < set.length && !model.getSteps().get(model.getCurrentStep()).greenBlocks[cursorIdx]) {
                        double cx = startX + cursorIdx * boxWidth + boxWidth / 2.0;
                        double px = startX + pivotIndex * boxWidth + boxWidth / 2.0;
                        double midX = (cx + px) / 2.0;
                        double midY = startY - 35;

                        int valCursor = set[cursorIdx].getRawValue();
                        int valPivot = set[pivotIndex].getRawValue();
                        String op = "=";
                        if (valCursor < valPivot) {
                            op = "<";
                        } else if (valCursor > valPivot) {
                            op = ">";
                        }

                        // Draw operator circle background
                        gc.setFill(theme.bg);
                        gc.setStroke(theme.accent);
                        gc.setLineWidth(1.5);
                        gc.fillOval(midX - 15, midY - 15, 30, 30);
                        gc.strokeOval(midX - 15, midY - 15, 30, 30);

                        // Draw operator text
                        gc.setFill(theme.accent);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 16, isGameBoy));
                        double opOffsetX = isGameBoy ? -4.0 : -5.5;
                        double opOffsetY = isGameBoy ? 4.0 : 5.5;
                        gc.fillText(op, midX + opOffsetX, midY + opOffsetY);
                    }
                }

                // 2. Draw target slot pointer arrow
                if (mergeTarget != -1 && model.getFreezeFrames() <= 0) {
                    double arrowX = startX + mergeTarget * boxWidth + boxWidth / 2.0;
                    double arrowYHead = startY - 8;
                    double arrowYTail = startY - 26;
                    if (targetAlgo.equalsIgnoreCase("Merge Sort")) {
                        arrowYHead = startY + 60 - 8;
                        arrowYTail = startY + 60 - 26;
                    }

                    gc.setStroke(modeAccent);
                    gc.setLineWidth(2.5);
                    gc.strokeLine(arrowX, arrowYTail, arrowX, arrowYHead);

                    gc.strokeLine(arrowX - 4, arrowYHead - 4, arrowX, arrowYHead);
                    gc.strokeLine(arrowX + 4, arrowYHead - 4, arrowX, arrowYHead);

                    gc.setFill(modeAccent);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 9, isGameBoy));
                    String targetLabel = "TARGET SLOT";
                    double labelOffset = isGameBoy ? -36 : -28;
                    gc.fillText(targetLabel, arrowX + labelOffset, arrowYTail - 4);
                }
            }

            // Draw completed wave banner overlay
            if (model.getFreezeFrames() > 0) {
                double overlayY = (canvas.getHeight() - 100.0) / 2.0;
                gc.setFill(theme.bg.deriveColor(0, 1, 1, 0.9));
                gc.fillRect(10, overlayY, canvas.getWidth() - 20, 100);

                gc.setStroke(theme.sorted.deriveColor(0, 1, 1, 0.8));
                gc.setLineWidth(1.5);
                gc.strokeRect(10, overlayY, canvas.getWidth() - 20, 100);

                gc.setFill(theme.sorted);
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 22, isGameBoy));
                String bannerMsg = "WAVE COMPLETED!";
                double msgCharW = isGameBoy ? 16.0 : 14.0;
                gc.fillText(bannerMsg, (canvas.getWidth() - msgCharW * bannerMsg.length()) / 2.0, overlayY + 44);

                gc.setFill(theme.text);
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGameBoy));
                double promptOffset = isGameBoy ? -120 : -80;
                gc.fillText("Generating next scrambled set...", canvas.getWidth() / 2.0 + promptOffset, overlayY + 74);
            }

            // Draw Game Over Screen Mask
            if (model.isGameOver()) {
                gc.setFill(theme.bg.deriveColor(0, 1, 1, 0.96));
                gc.fillRect(10, 10, canvas.getWidth() - 20, canvas.getHeight() - 20);

                if (model.isEnteringInitials()) {
                    // Draw Initials Entry Box
                    gc.setStroke(theme.accent.deriveColor(0, 1, 1, 0.8));
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(200, 70, 400, 260, 8, 8);
                    gc.setFill(theme.panelBg.deriveColor(0, 1, 1, 0.98));
                    gc.fillRoundRect(200, 70, 400, 260, 8, 8);

                    // Title
                    gc.setFill(theme.accent);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 22, isGameBoy));
                    String title = "NEW HIGH SCORE!";
                    double tCharW = isGameBoy ? 8.0 : 6.5;
                    gc.fillText(title, 400 - (title.length() * tCharW), 115);

                    // Subtitle
                    gc.setFill(theme.text);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 14, isGameBoy));
                    String sub = "Score: " + model.getScore();
                    double sCharW = isGameBoy ? 5.0 : 4.0;
                    gc.fillText(sub, 400 - (sub.length() * sCharW), 145);

                    gc.setFill(theme.textMuted);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGameBoy));
                    double initLabelX = isGameBoy ? 310 : 335;
                    gc.fillText("ENTER YOUR INITIALS", initLabelX, 180);

                    // Draw Initials Input Boxes
                    String initials = model.getPlayerInitials();
                    for (int k = 0; k < 3; k++) {
                        double bx = 330 + k * 55;
                        double by = 200;
                        gc.setFill(theme.border);
                        gc.fillRoundRect(bx, by, 40, 50, 4, 4);
                        gc.setStroke(theme.textMuted);
                        gc.strokeRoundRect(bx, by, 40, 50, 4, 4);

                        if (k < initials.length()) {
                            gc.setFill(theme.text);
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 26, isGameBoy));
                            double keyOffX = isGameBoy ? 8 : 11;
                            gc.fillText(String.valueOf(initials.charAt(k)), bx + keyOffX, by + 36);
                        } else if (k == initials.length()) {
                            // Flash cursor
                            if ((System.currentTimeMillis() / 400) % 2 == 0) {
                                gc.setFill(theme.accent);
                                gc.fillRect(bx + 10, by + 40, 20, 4);
                            }
                        }
                    }

                    gc.setFill(theme.textMuted);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 10, isGameBoy));
                    double helperX = isGameBoy ? 250 : 295;
                    gc.fillText("TYPE A-Z AND PRESS ENTER TO REGISTER", helperX, 290);
                } else {
                    // Draw Leaderboard Table Box
                    gc.setStroke(isGameBoy ? theme.border : Color.web("#ef4444", 0.7));
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(160, 50, 480, 300, 8, 8);
                    gc.setFill(theme.panelBg.deriveColor(0, 1, 1, 0.98));
                    gc.fillRoundRect(160, 50, 480, 300, 8, 8);

                    // Title
                    gc.setFill(isGameBoy ? theme.accent : Color.web("#ef4444"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 22, isGameBoy));
                    String title = "TIME EXPIRED";
                    double expiredCharW = isGameBoy ? 8.0 : 6.5;
                    gc.fillText(title, 400 - (title.length() * expiredCharW), 90);

                    // Subtitle
                    gc.setFill(theme.text);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGameBoy));
                    String sub = "Final Score: " + model.getScore() + " | Completed Waves: " + model.getCompletedWavesCount();
                    double expiredSubW = isGameBoy ? 5.0 : 4.0;
                    gc.fillText(sub, 400 - (sub.length() * expiredSubW), 115);

                    // Headers
                    gc.setFill(theme.textMuted);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGameBoy));
                    gc.fillText("RANK", 190, 145);
                    gc.fillText("NAME", 260, 145);
                    gc.fillText("SCORE", 380, 145);
                    gc.fillText("DATE", 490, 145);

                    gc.setStroke(theme.border);
                    gc.setLineWidth(1.0);
                    gc.strokeLine(180, 153, 620, 153);

                    // Render Top 5 Scores
                    java.util.List<LeaderboardManager.Entry> top = LeaderboardManager.getTopScores(model.getTargetAlgorithm(), 5);
                    for (int idx = 0; idx < top.size(); idx++) {
                        LeaderboardManager.Entry ent = top.get(idx);
                        double ry = 175 + idx * 26;
                        
                        boolean highlight = false;
                        // Match if the entry is the player's new score
                        if (ent.score == model.getScore() && (ent.name.equalsIgnoreCase(model.getPlayerInitials()) || ent.name.equalsIgnoreCase("YOU"))) {
                            highlight = true;
                        }

                        Color rowColor = highlight ? theme.accent : theme.text;
                        gc.setFill(rowColor);
                        gc.setFont(getThemeFont("Consolas", highlight ? FontWeight.BOLD : FontWeight.NORMAL, 12, isGameBoy));
                        
                        gc.fillText(String.format("%02d", idx + 1), 195, ry);
                        gc.fillText(ent.name, 265, ry);
                        gc.fillText(String.format("%05d", ent.score), 380, ry);
                        gc.fillText(ent.date, 490, ry);
                    }

                    gc.setStroke(theme.border);
                    gc.strokeLine(180, 305, 620, 305);

                    gc.setFill(theme.textMuted);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGameBoy));
                    double restartHelpX = isGameBoy ? 245 : 295;
                    gc.fillText("Press R to Restart | ESC to Main Menu", restartHelpX, 328);
                }
            }

            // Visual Effect: Error Red Border Flash
            if (model.getErrorFlashFrames() > 0) {
                gc.setStroke(Color.web("#f43f5e", 0.6 * (model.getErrorFlashFrames() / 15.0)));
                gc.setLineWidth(16.0);
                gc.strokeRect(10, 10, canvas.getWidth() - 20, canvas.getHeight() - 20);
                model.setErrorFlashFrames(model.getErrorFlashFrames() - 1);
            }

            // Visual Effect: Low Time Pulse (Red pulsing overlay when time <= 5s)
            if (model.getCountdownTimer() <= 5 && !model.isGameOver() && model.getFreezeFrames() <= 0 && model.getCountdownTimer() > 0 && !model.isPracticeMode()) {
                double pulse = 0.12 + 0.12 * Math.sin(System.currentTimeMillis() / 120.0);
                gc.setFill(Color.web("#ef4444", pulse));
                gc.fillRect(10, 10, canvas.getWidth() - 20, canvas.getHeight() - 20);
            }

            // Draw combo count if comboCount >= 2
            if (model.getComboCount() >= 2) {
                gc.setFill(theme.accent);
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 15, isGameBoy));
                String comboText = "COMBO x" + model.getComboCount() + " (" + model.getComboCount() + "x Multiplier!)";
                double pulse = 1.0 + 0.04 * Math.sin(System.currentTimeMillis() / 100.0);
                gc.save();
                gc.translate(25, 45);
                gc.scale(pulse, pulse);
                
                // Add retro shadow
                gc.setFill(theme.bg);
                gc.fillText(comboText, 1, 1);
                gc.setFill(theme.accent);
                gc.fillText(comboText, 0, 0);
                gc.restore();
            }

            // Update and draw particles
            java.util.Iterator<Particle> it = particles.iterator();
            while (it.hasNext()) {
                Particle p = it.next();
                if (p.update()) {
                    p.draw(gc);
                } else {
                    it.remove();
                }
            }

            // Update and draw floating texts
            java.util.Iterator<FloatingText> fit = floatingTexts.iterator();
            while (fit.hasNext()) {
                FloatingText ft = fit.next();
                if (ft.update()) {
                    ft.draw(gc);
                } else {
                    fit.remove();
                }
            }

            // Draw start countdown overlay
            if (model.getStartCountdown() >= 0) {
                // Semi-transparent overlay
                gc.setFill(isGameBoy ? theme.panelBg.deriveColor(0, 1, 1, 0.4) : Color.web("#0b0f19", 0.65));
                gc.fillRect(10, 10, canvas.getWidth() - 20, canvas.getHeight() - 20);

                // Huge countdown text
                gc.setFont(getThemeFont(isGameBoy ? "Courier New" : "Consolas", FontWeight.BOLD, isGameBoy ? 54 : 96, isGameBoy));
                gc.setFill(theme.accent);
                
                String countdownText = model.getStartCountdown() == 0 ? "GO!" : String.valueOf(model.getStartCountdown());
                
                // Centering the text on the canvas
                double fontSize = isGameBoy ? 54 : 96;
                double charW = fontSize * 0.6;
                double textW = charW * countdownText.length();
                double textX = (canvas.getWidth() - textW) / 2.0;
                double textY = (canvas.getHeight() + fontSize * 0.75) / 2.0;
                
                gc.fillText(countdownText, textX, textY);
                
                // Draw a smaller "GET READY" text above it
                if (model.getStartCountdown() > 0) {
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGameBoy ? 10 : 20, isGameBoy));
                    gc.setFill(theme.text);
                    String msg = "GET READY";
                    double msgW = msg.length() * (isGameBoy ? 7 : 12);
                    gc.fillText(msg, (canvas.getWidth() - msgW) / 2.0, textY - (isGameBoy ? 45 : 85));
                }
            }

        }

        public void updateHUD() {
            Theme theme = model.getTheme();
            boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
            String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");
            String fontMono = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Consolas', monospace");

            if (scoreValLabel != null) {
                if (model.isPracticeMode()) {
                    scoreValLabel.setText("PRACTICE MODE (UNTIMED)");
                } else {
                    int topScore = 0;
                    java.util.List<LeaderboardManager.Entry> top = LeaderboardManager.getTopScores(model.getTargetAlgorithm(), 1);
                    if (!top.isEmpty()) {
                        topScore = top.get(0).score;
                    }
                    if (model.getScore() > topScore) {
                        topScore = model.getScore();
                    }
                    scoreValLabel.setText(String.format("SCORE: %05d    HI-SCORE: %05d", model.getScore(), topScore));
                }
                scoreValLabel.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: " + (isGB ? "10px" : "16px") + "; -fx-text-fill: " + theme.textHex + "; -fx-font-weight: bold;");
            }
            if (timerValLabel != null) {
                if (model.isPracticeMode()) {
                    timerValLabel.setText("PRACTICE");
                    timerValLabel.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: " + (isGB ? "16px" : "32px") + "; -fx-text-fill: " + theme.sortedHex + "; -fx-font-weight: bold;");
                } else {
                    int rem = model.getCountdownTimer();
                    timerValLabel.setText(rem + "s");
                    if (isGB) {
                        timerValLabel.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: 24px; -fx-text-fill: " + theme.accentHex + "; -fx-font-weight: bold;");
                    } else {
                        if (rem <= 5) {
                            timerValLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 44px; -fx-text-fill: #f43f5e; -fx-font-weight: bold; -fx-effect: dropshadow(three-pass-box, rgba(244,63,94,0.6), 10, 0, 0, 0);");
                        } else {
                            timerValLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 44px; -fx-text-fill: " + theme.accentHex + "; -fx-font-weight: bold;");
                        }
                    }
                }
            }
            if (targetValLabel != null) {
                String modeLabelStr = "MODE: " + model.getTargetAlgorithm().toUpperCase();
                if (model.isPracticeMode()) {
                    targetValLabel.setText(modeLabelStr + " [PRACTICE]");
                } else {
                    targetValLabel.setText(modeLabelStr + " | WAVE: " + (model.getCompletedWavesCount() + 1));
                }
                targetValLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-text-fill: " + theme.sortedHex + "; -fx-font-weight: bold;");
            }
        }
    }

    // --- Controller ---

    public static class ChromaCascadeController {
        private ChromaCascadeModel model;
        private ChromaCascadeView view;
        private Random random = new Random();
        private AnimationTimer animationTimer;
        public ListView<String> logListView;

        public ChromaCascadeController(ChromaCascadeModel model, ChromaCascadeView view) {
            this.model = model;
            this.view = view;
            ChromaCascadeApp.controllerInstance = this;
        }

        public void setAnimationTimer(AnimationTimer animationTimer) {
            this.animationTimer = animationTimer;
        }

        public void initializeGame() {
            model.setScore(0);
            model.setCompletedWavesCount(0);
            model.setStartCountdown(3);
            
            int startingTime = 20;
            String targetAlgo = model.getTargetAlgorithm();
            if (targetAlgo.equalsIgnoreCase("Quick Sort") || targetAlgo.equalsIgnoreCase("Merge Sort")) {
                startingTime = 40;
            } else if (targetAlgo.equalsIgnoreCase("Bubble Sort") || targetAlgo.equalsIgnoreCase("Insertion Sort")) {
                startingTime = 30;
            }
            model.setCountdownTimer(startingTime);
            
            model.setLastSortDurationNs(0);
            model.setGameOver(false);
            model.setFreezeFrames(0);
            model.getSystemStatusLog().clear();
            model.getMemoryRegisters().clear();

            spawnNewPuzzleSet();

            addLogMessage("PUZZLE ENGINE: System ready. Countdown ticker initialized.");
            addLogMessage("STATUS: Solve waves using " + model.getTargetAlgorithm() + "!");
            SoundManager.startMusic(model);
        }

        public void spawnNewPuzzleSet() {
            int completed = model.getCompletedWavesCount();
            int length = Math.min(12, 8 + completed / 4);
            model.setWaveErrors(0);

            PuzzleRow row = null;
            BlockSegment[] set = null;
            while (true) {
                row = new PuzzleRow(length);
                set = row.getCurrentSet();
                for (int i = 0; i < length; i++) {
                    int val = random.nextInt(50) + 1;
                    BlockSegment segment = (val % 2 != 0) ? new OddSegment(val, i) : new EvenSegment(val, i);
                    row.setSegment(i, segment);
                }
                if (GridSorter.countInversions(set) > 0) {
                    break;
                }
            }
            model.setPuzzleRow(row);
            model.setSortedCount(0);
            model.getMoveHistory().clear();
            view.clearVisuals();

            java.util.List<SortingStep> stepsList;
            String mode = model.getTargetAlgorithm();
            if (mode.equalsIgnoreCase("Quick Sort")) {
                stepsList = GridSorter.generateQuickSortSteps(set);
            } else if (mode.equalsIgnoreCase("Merge Sort")) {
                stepsList = GridSorter.generateMergeSortSteps(set);
            } else if (mode.equalsIgnoreCase("Bubble Sort")) {
                stepsList = GridSorter.generateBubbleSortSteps(set);
            } else if (mode.equalsIgnoreCase("Insertion Sort")) {
                stepsList = GridSorter.generateInsertionSortSteps(set);
            } else {
                stepsList = GridSorter.generateSelectionSortSteps(set);
            }
            model.setSteps(stepsList);
            model.setCurrentStep(0);

            int startCursor = (stepsList != null && !stepsList.isEmpty()) ? stepsList.get(0).startIndex : 0;
            model.setActiveSegmentCursor(startCursor);

            addLogMessage("SPAWN: Generated wave of size " + length);
            addLogMessage("STATUS: Move using [A]/[D], shift with [ENTER]!");
        }

        public void handleKeyPress(KeyCode code) {
            if (model.isGameOver()) {
                if (model.isEnteringInitials()) {
                    if (code == KeyCode.BACK_SPACE) {
                        String initials = model.getPlayerInitials();
                        if (initials.length() > 0) {
                            model.setPlayerInitials(initials.substring(0, initials.length() - 1));
                            SoundManager.playClick();
                        }
                    } else if (code == KeyCode.ENTER) {
                        String initials = model.getPlayerInitials().trim();
                        if (initials.length() > 0) {
                            LeaderboardManager.addScore(model.getTargetAlgorithm(), model.getScore(), initials);
                            model.setEnteringInitials(false);
                            SoundManager.playSuccess();
                        }
                    } else {
                        String s = code.toString();
                        if (s.length() == 1 && Character.isLetter(s.charAt(0))) {
                            String initials = model.getPlayerInitials();
                            if (initials.length() < 3) {
                                model.setPlayerInitials(initials + s.toUpperCase());
                                SoundManager.playClick();
                            }
                        }
                    }
                    return;
                }

                if (code == KeyCode.R) {
                    initializeGame();
                    if (animationTimer != null) {
                        animationTimer.start();
                    }
                }
                return;
            }

            if (model.getFreezeFrames() > 0) {
                return;
            }

            PuzzleRow row = model.getPuzzleRow();
            if (row == null || row.getCurrentSet() == null) {
                return;
            }
            BlockSegment[] set = row.getCurrentSet();
            int length = set.length;

            switch (code) {
                case A:
                    int cursorA = model.getActiveSegmentCursor();
                    int prevCursor = cursorA - 1;
                    if (prevCursor < 0) {
                        prevCursor = length - 1;
                    }
                    model.setActiveSegmentCursor(prevCursor);
                    SoundManager.playClick(-0.35);
                    break;

                case D:
                    int cursor = model.getActiveSegmentCursor();
                    int nextCursor = cursor + 1;
                    if (nextCursor >= length) {
                        nextCursor = 0;
                    }
                    model.setActiveSegmentCursor(nextCursor);
                    SoundManager.playClick(0.35);
                    break;

                case ENTER:
                    executeShiftAction();
                    break;

                default:
                    break;
            }
        }

        public void triggerGameOver() {
            model.setGameOver(true);
            SoundManager.stopMusic();
            SoundManager.playGameOver();
            if (!model.isPracticeMode() && model.getScore() > 0 && LeaderboardManager.qualifiesForTopFive(model.getTargetAlgorithm(), model.getScore())) {
                model.setEnteringInitials(true);
                model.setPlayerInitials("");
            } else {
                model.setEnteringInitials(false);
            }
        }

        private void executeShiftAction() {
            PuzzleRow row = model.getPuzzleRow();
            if (row == null || row.getCurrentSet() == null) return;
            BlockSegment[] set = row.getCurrentSet();
            int cursor = model.getActiveSegmentCursor();
            
            int currentStepIdx = model.getCurrentStep();
            java.util.List<SortingStep> steps = model.getSteps();
            
            if (steps == null || currentStepIdx >= steps.size()) {
                return;
            }
            
            SortingStep step = steps.get(currentStepIdx);
            
            // Calculate coordinates for particle burst
            double totalWidth = 700.0;
            double boxWidth = totalWidth / set.length;
            double startX = (view.getCanvas().getWidth() - totalWidth) / 2.0;
            double px = startX + cursor * boxWidth + boxWidth / 2.0;
            double py = view.getCanvas().getHeight() / 2.0;
            
            // Calculate spatial stereo pan based on cursor position
            double pan = 0.0;
            if (set.length > 1) {
                pan = ((double) cursor / (set.length - 1)) * 2.0 - 1.0;
            }
            
            if (cursor == step.correctIndex) {
                int val = set[cursor].getRawValue();
                GridSorter.shiftElement(set, step.correctIndex, step.targetIndex);
                
                SoundManager.playSuccess(pan);
                
                model.setCurrentStep(currentStepIdx + 1);
                model.setComboCount(model.getComboCount() + 1);
                
                // Spawn green particles
                view.spawnParticles(px, py, model.getTheme().sorted, 25);
                
                if (currentStepIdx + 1 < steps.size()) {
                    String algo = model.getTargetAlgorithm();
                    if (algo.equalsIgnoreCase("Selection Sort") || algo.equalsIgnoreCase("Quick Sort") || algo.equalsIgnoreCase("Merge Sort")) {
                        model.setActiveSegmentCursor(steps.get(currentStepIdx + 1).startIndex);
                    }
                }
                
                // Award points for step and spawn floating texts
                if (!model.isPracticeMode()) {
                    int stepPts = 10 * model.getComboCount();
                    model.setScore(model.getScore() + stepPts);
                    addLogMessage(String.format("CORRECT: Value %d shifted. (+%d PTS, Combo x%d!)", val, stepPts, model.getComboCount()));
                    view.spawnFloatingText(px, py - 20, "+" + stepPts + " PTS", model.getTheme().sorted);
                    if (model.getComboCount() >= 2) {
                        view.spawnFloatingText(px, py - 40, "COMBO x" + model.getComboCount() + "!", model.getTheme().accent);
                    }
                } else {
                    addLogMessage(String.format("CORRECT: Value %d shifted.", val));
                    view.spawnFloatingText(px, py - 20, "CORRECT!", model.getTheme().sorted);
                }
                
                if (model.getCurrentStep() >= steps.size()) {
                    checkWinCondition();
                }
            } else {
                SoundManager.playFailure(pan);
                model.setErrorFlashFrames(15);
                model.setWaveErrors(model.getWaveErrors() + 1);
                model.setComboCount(0); // Reset combo
                
                // Spawn red particles
                view.spawnParticles(px, py, Color.web("#f43f5e"), 20);
                
                if (!model.isPracticeMode()) {
                    int currentTimer = model.getCountdownTimer();
                    int newTimer = Math.max(0, currentTimer - 3);
                    model.setCountdownTimer(newTimer);
                    if (newTimer == 0) {
                        triggerGameOver();
                    }
                    model.setScore(Math.max(0, model.getScore() - 25));
                    addLogMessage(String.format("INCORRECT: Penalty applied. (-25 PTS, Timer -3s)"));
                    
                    view.spawnFloatingText(px, py - 20, "-25 PTS", Color.web("#ef4444"));
                    view.spawnFloatingText(px, py - 40, "-3s TIMER", Color.web("#f43f5e"));
                } else {
                    addLogMessage("INCORRECT: Selected block is invalid!");
                    view.spawnFloatingText(px, py - 20, "TRY AGAIN!", Color.web("#ef4444"));
                }
                
                String algo = model.getTargetAlgorithm();
                if (algo.equalsIgnoreCase("Selection Sort") || algo.equalsIgnoreCase("Quick Sort") || algo.equalsIgnoreCase("Merge Sort")) {
                    model.setActiveSegmentCursor(step.startIndex);
                }
            }
        }

        private void checkWinCondition() {
            SoundManager.playWaveClear();
            
            // Spawn confetti particles from the top of the canvas
            for (int i = 0; i < 60; i++) {
                double rx = new Random().nextDouble() * view.getCanvas().getWidth();
                double ry = 20;
                view.spawnParticles(rx, ry, Color.color(new Random().nextDouble(), new Random().nextDouble(), new Random().nextDouble()), 1);
            }
            
            int base = 100;
            int speedBonus = Math.max(0, model.getCountdownTimer() * 10 - model.getWaveErrors() * 30);
            int waveScore = base + speedBonus;
            
            if (!model.isPracticeMode()) {
                model.setScore(model.getScore() + waveScore);
            }
            
            int additionalTime = 10;
            String targetAlgo = model.getTargetAlgorithm();
            if (targetAlgo.equalsIgnoreCase("Merge Sort")) {
                additionalTime = 30;
            } else if (targetAlgo.equalsIgnoreCase("Bubble Sort") || targetAlgo.equalsIgnoreCase("Insertion Sort") || targetAlgo.equalsIgnoreCase("Quick Sort")) {
                additionalTime = 15;
            }
            if (!model.isPracticeMode()) {
                model.setCountdownTimer(model.getCountdownTimer() + additionalTime);
            }
            model.setCompletedWavesCount(model.getCompletedWavesCount() + 1);
            model.setFreezeFrames(30);
            model.setComboCount(0); // reset combo for next wave
            
            if (!model.isPracticeMode()) {
                addLogMessage(String.format("VERIFIED: Clear via %s (+%d PTS, +%ds)!", model.getTargetAlgorithm(), waveScore, additionalTime));
            } else {
                addLogMessage(String.format("VERIFIED: Clear via %s!", model.getTargetAlgorithm()));
            }
        }

        public void addLogMessage(String msg) {
            ObservableList<String> logs = model.getSystemStatusLog();
            logs.add(msg);
            if (logs.size() > 20) {
                logs.remove(0);
            }
            if (logListView != null) {
                logListView.scrollTo(logs.size() - 1);
            }
        }

        // Simulates external update events triggering safety stack overflow check
        public void triggerSimulatedUpdate() {
            PuzzleRow row = model.getPuzzleRow();
            if (row == null || row.getCurrentSet() == null) return;

            int val = random.nextInt(50) + 1;
            BlockSegment injected = (val % 2 != 0) ? new OddSegment(val, -1) : new EvenSegment(val, -1);
            // Index L is out of bounds for the current set, triggering exception
            int size = row.getCurrentSet().length;
            row.simulateExternalUpdate(injected, size);
        }
    }

    // --- Leaderboard Manager ---
    public static class LeaderboardManager {
        private static final String FILE_NAME;
        static {
            String path = "sort_pulse_scores.txt";
            try {
                String userHome = System.getProperty("user.home");
                java.io.File dir = new java.io.File(userHome, ".sortpulse");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                path = new java.io.File(dir, "sort_pulse_scores.txt").getAbsolutePath();
            } catch (Exception e) {
                // Fallback to current working directory
            }
            FILE_NAME = path;
        }

        public static class Entry {
            public String mode;
            public String name;
            public int score;
            public String date;

            public Entry(String mode, String name, int score, String date) {
                this.mode = mode;
                this.name = name;
                this.score = score;
                this.date = date;
            }
        }

        public static java.util.List<Entry> loadEntries() {
            java.util.List<Entry> entries = new java.util.ArrayList<>();
            java.io.File file = new java.io.File(FILE_NAME);
            if (!file.exists()) {
                // Backward compatibility: Import existing local score file if present
                java.io.File localFile = new java.io.File("sort_pulse_scores.txt");
                if (localFile.exists()) {
                    try {
                        java.nio.file.Files.copy(localFile.toPath(), file.toPath());
                    } catch (Exception e) {
                        file = localFile;
                    }
                }
            }
            if (!file.exists()) {
                // Selection Sort
                entries.add(new Entry("Selection Sort", "MIC", 500, "2026-06-11"));
                entries.add(new Entry("Selection Sort", "RON", 400, "2026-06-11"));
                entries.add(new Entry("Selection Sort", "SHL", 300, "2026-06-11"));
                // Bubble Sort
                entries.add(new Entry("Bubble Sort", "MIC", 500, "2026-06-11"));
                entries.add(new Entry("Bubble Sort", "RON", 400, "2026-06-11"));
                entries.add(new Entry("Bubble Sort", "SHL", 300, "2026-06-11"));
                // Insertion Sort
                entries.add(new Entry("Insertion Sort", "MIC", 500, "2026-06-11"));
                entries.add(new Entry("Insertion Sort", "RON", 400, "2026-06-11"));
                entries.add(new Entry("Insertion Sort", "SHL", 300, "2026-06-11"));
                // Quick Sort
                entries.add(new Entry("Quick Sort", "MIC", 800, "2026-06-11"));
                entries.add(new Entry("Quick Sort", "RON", 600, "2026-06-11"));
                entries.add(new Entry("Quick Sort", "SHL", 400, "2026-06-11"));
                // Merge Sort
                entries.add(new Entry("Merge Sort", "MIC", 1000, "2026-06-11"));
                entries.add(new Entry("Merge Sort", "RON", 700, "2026-06-11"));
                entries.add(new Entry("Merge Sort", "SHL", 500, "2026-06-11"));
                saveEntries(entries);
                return entries;
            }
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] parts = line.split(":", 4);
                    if (parts.length >= 3) {
                        String mode = parts[0].trim();
                        String name = parts[1].trim();
                        int score = 0;
                        try {
                            score = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {}
                        String date = parts.length == 4 ? parts[3].trim() : "2026-06-06";
                        entries.add(new Entry(mode, name, score, date));
                    }
                }
            } catch (Exception e) {}
            return entries;
        }

        public static void saveEntries(java.util.List<Entry> entries) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(FILE_NAME))) {
                for (Entry e : entries) {
                    pw.println(e.mode + ":" + e.name + ":" + e.score + ":" + e.date);
                }
            } catch (Exception e) {}
        }

        public static void addScore(String mode, int score, String name) {
            java.util.List<Entry> entries = loadEntries();
            String date = java.time.LocalDate.now().toString();
            entries.add(new Entry(mode, name, score, date));
            saveEntries(entries);
        }

        public static boolean qualifiesForTopFive(String mode, int score) {
            if (score <= 0) return false;
            java.util.List<Entry> top = getTopScores(mode, 5);
            if (top.size() < 5) return true;
            return score > top.get(top.size() - 1).score;
        }

        public static java.util.List<Entry> getTopScores(String mode, int limit) {
            java.util.List<Entry> entries = loadEntries();
            java.util.List<Entry> filtered = new java.util.ArrayList<>();
            for (Entry e : entries) {
                if (e.mode.equalsIgnoreCase(mode)) {
                    filtered.add(e);
                }
            }
            filtered.sort((a, b) -> Integer.compare(b.score, a.score));
            if (filtered.size() > limit) {
                return filtered.subList(0, limit);
            }
            return filtered;
        }
    }

    // --- Floating Text System ---
    public static class FloatingText {
        double x, y;
        double vy;
        double alpha;
        String text;
        Color color;
        int maxLife;
        int life;
        Font font;

        public FloatingText(double x, double y, String text, Color color) {
            this.x = x;
            this.y = y;
            this.vy = -1.2;
            this.text = text;
            this.color = color;
            this.maxLife = 45;
            this.life = maxLife;
            this.alpha = 1.0;
            this.font = Font.font("Segoe UI", FontWeight.BOLD, 14);
        }

        public boolean update() {
            y += vy;
            vy *= 0.97;
            life--;
            alpha = (double) life / maxLife;
            return life > 0;
        }

        public void draw(GraphicsContext gc) {
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            gc.setFont(font);
            gc.fillText(text, x - (text.length() * 3.5), y);
        }
    }

    // --- Particle System ---
    public static class Particle {
        double x, y;
        double vx, vy;
        double alpha;
        double size;
        Color color;
        int maxLife;
        int life;

        public Particle(double x, double y, double vx, double vy, Color color, double size, int maxLife) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.size = size;
            this.maxLife = maxLife;
            this.life = maxLife;
            this.alpha = 1.0;
        }

        public boolean update() {
            x += vx;
            y += vy;
            vy += 0.08; 
            vx *= 0.98;
            life--;
            alpha = (double) life / maxLife;
            return life > 0;
        }

        public void draw(GraphicsContext gc) {
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            gc.fillOval(x - size / 2.0, y - size / 2.0, size, size);
        }
    }

    // --- Main Entry Point ---

    @Override
    public void start(Stage primaryStage) {
        // MVC Integration
        ChromaCascadeModel model = new ChromaCascadeModel();
        ChromaCascadeView view = new ChromaCascadeView(model);
        ChromaCascadeController controller = new ChromaCascadeController(model, view);

        // Root container to swap layouts (Menu vs Game)
        StackPane rootContainer = new StackPane();
        rootContainer.setStyle("-fx-background-color: #0b0f19;");

        // 1. Build SLEEK MAIN MENU LAYOUT
        VBox menuLayout = new VBox(20);
        menuLayout.setAlignment(Pos.CENTER);
        menuLayout.setPadding(new Insets(40));
        menuLayout.setStyle("-fx-background-color: #0b0f19;");

        Label menuTitle = new Label("SORT PULSE");
        menuTitle.setStyle("-fx-font-family: 'Segoe UI', 'Outfit', sans-serif; -fx-font-size: 44px; -fx-font-weight: bold; -fx-text-fill: #10b981; -fx-effect: dropshadow(three-pass-box, rgba(16,185,129,0.4), 12, 0, 0, 0);");
        
        Label menuSubtitle = new Label("TIMED PUZZLE BLITZ ENGINE");
        menuSubtitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: #64748b; -fx-padding: -15px 0 10px 0;");

        Button selectionBtn = new Button("SELECTION SORT");
        Button quickBtn = new Button("QUICK SORT");
        Button mergeBtn = new Button("MERGE SORT");
        
        Button bubbleBtn = new Button("BUBBLE SORT");
        bubbleBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;");
        bubbleBtn.setOnMouseEntered(e -> bubbleBtn.setStyle("-fx-background-color: #ec4899; -fx-text-fill: #ffffff; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #ec4899; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(236,72,153,0.3), 8, 0, 0, 0);"));
        bubbleBtn.setOnMouseExited(e -> bubbleBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;"));

        Button insertionBtn = new Button("INSERTION SORT");
        insertionBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;");
        insertionBtn.setOnMouseEntered(e -> insertionBtn.setStyle("-fx-background-color: #a855f7; -fx-text-fill: #ffffff; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #a855f7; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(168,85,247,0.3), 8, 0, 0, 0);"));
        insertionBtn.setOnMouseExited(e -> insertionBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;"));

        CheckBox practiceModeCb = new CheckBox("PRACTICE MODE (NO TIMER)");
        practiceModeCb.setStyle("-fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 13px; -fx-cursor: hand;");
        practiceModeCb.selectedProperty().addListener((obs, oldVal, newVal) -> {
            model.setPracticeMode(newVal);
        });

        // Theme ComboBox Selector
        HBox themeBox = new HBox(15);
        themeBox.setAlignment(Pos.CENTER);
        Label themeLabel = new Label("UI COLOR THEME:");
        themeLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 13px;");
        ComboBox<String> themeCb = new ComboBox<>();
        themeCb.getItems().addAll("Classic Neon", "GameBoy Retro");
        themeCb.setValue("Classic Neon");
        themeBox.getChildren().addAll(themeLabel, themeCb);

        Button leaderboardBtn = new Button("HIGH SCORES");
        leaderboardBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;");
        leaderboardBtn.setOnMouseEntered(e -> leaderboardBtn.setStyle("-fx-background-color: #a855f7; -fx-text-fill: #ffffff; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #a855f7; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(168,85,247,0.3), 8, 0, 0, 0);"));
        leaderboardBtn.setOnMouseExited(e -> leaderboardBtn.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12px 30px; -fx-background-radius: 6px; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-min-width: 280; -fx-cursor: hand;"));

        Label menuGuide = new Label("Press ESC to Quit Game");
        menuGuide.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-text-fill: #475569; -fx-padding: 10px 0 0 0;");

        menuLayout.getChildren().addAll(menuTitle, menuSubtitle, selectionBtn, quickBtn, mergeBtn, bubbleBtn, insertionBtn, practiceModeCb, themeBox, leaderboardBtn, menuGuide);

        // Leaderboards Layout
        VBox leaderboardLayout = new VBox(20);
        leaderboardLayout.setAlignment(Pos.CENTER);
        leaderboardLayout.setPadding(new Insets(40));
        leaderboardLayout.setStyle("-fx-background-color: #0b0f19;");

        Label lbTitle = new Label("HIGH SCORES");
        lbTitle.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #a855f7;");

        HBox columns = new HBox(30); // slightly tighter column spacing to fit all 5 modes
        columns.setAlignment(Pos.CENTER);

        Runnable refreshLeaderboard = () -> {
            columns.getChildren().clear();
            Theme theme = model.getTheme();
            boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
            String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");
            String fontMono = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Consolas', monospace");

            String[] modes = {"Selection Sort", "Bubble Sort", "Insertion Sort", "Quick Sort", "Merge Sort"};
            for (String modeName : modes) {
                VBox col = new VBox(10);
                col.setAlignment(Pos.TOP_CENTER);
                
                String colBg = theme.panelBgHex;
                String colBorder = theme.borderHex;
                double colMinW = isGB ? 150 : 170;
                col.setStyle("-fx-background-color: " + colBg + "; -fx-padding: 12px; -fx-background-radius: 4px; -fx-border-color: " + colBorder + "; -fx-border-width: 1.5px; -fx-min-width: " + colMinW + ";");

                Label modeHeader = new Label(modeName.toUpperCase());
                double headerSize = isGB ? 7 : 10;
                modeHeader.setStyle("-fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + headerSize + "px; -fx-text-fill: " + theme.textHex + ";");
                col.getChildren().add(modeHeader);

                java.util.List<LeaderboardManager.Entry> top = LeaderboardManager.getTopScores(modeName, 5);
                if (top.isEmpty()) {
                    Label noScores = new Label("NO SCORES YET");
                    double noScoresSize = isGB ? 7 : 10;
                    noScores.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + noScoresSize + "px; -fx-text-fill: " + theme.textMutedHex + ";");
                    col.getChildren().add(noScores);
                } else {
                    for (int i = 0; i < top.size(); i++) {
                        LeaderboardManager.Entry ent = top.get(i);
                        Label entryLbl = new Label((i + 1) + ". " + ent.name + " - " + ent.score);
                        double entrySize = isGB ? 7 : 11;
                        entryLbl.setStyle("-fx-font-family: " + fontMono + "; -fx-font-size: " + entrySize + "px; -fx-text-fill: " + theme.textHex + ";");
                        col.getChildren().add(entryLbl);
                    }
                }
                columns.getChildren().add(col);
            }
        };

        Button backBtn = new Button("BACK TO MENU");
        backBtn.setOnAction(e -> {
            SoundManager.playMenuSelect();
            rootContainer.getChildren().setAll(menuLayout);
        });

        leaderboardLayout.getChildren().addAll(lbTitle, columns, backBtn);

        leaderboardBtn.setOnAction(e -> {
            SoundManager.playMenuSelect();
            refreshLeaderboard.run();
            rootContainer.getChildren().setAll(leaderboardLayout);
        });

        // 2. Build SLEEK MINIMAL GAME LAYOUT
        VBox gameLayout = new VBox(0);
        gameLayout.setAlignment(Pos.CENTER);
        gameLayout.setStyle("-fx-background-color: #0b0f19;");

        // Top HUD Header with spacing above blocks
        VBox topHud = new VBox(8);
        topHud.setPadding(new Insets(30, 0, 30, 0)); // 30px spacing above blocks
        topHud.setAlignment(Pos.CENTER);

        Label modeLabel = new Label("MODE: SELECTION SORT | WAVE: 1");
        modeLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 12px; -fx-text-fill: #10b981; -fx-font-weight: bold; -fx-letter-spacing: 1.5;");
        view.setTargetValLabel(modeLabel);

        Label timerVal = new Label("20s");
        timerVal.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 44px; -fx-text-fill: #f59e0b; -fx-font-weight: bold;");
        view.setTimerValLabel(timerVal);

        Label scoreVal = new Label("SCORE: 00000");
        scoreVal.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 16px; -fx-text-fill: #ffffff; -fx-font-weight: bold;");
        view.setScoreValLabel(scoreVal);

        topHud.getChildren().addAll(modeLabel, timerVal, scoreVal);

        // Center Canvas Wrapper
        StackPane canvasWrapper = new StackPane();
        canvasWrapper.setPadding(new Insets(10, 50, 10, 50));
        canvasWrapper.getChildren().add(view.getCanvas());

        // Minimal HUD Log list
        HBox bottomPanel = new HBox();
        bottomPanel.setPadding(new Insets(20, 50, 20, 50));
        bottomPanel.setAlignment(Pos.CENTER);

        ListView<String> logView = new ListView<>(model.getSystemStatusLog());
        logView.setPrefHeight(90);
        logView.setPrefWidth(700); // Centered and expanded to match block area width
        logView.setStyle("-fx-background-color: #0f172a; -fx-control-inner-background: #0f172a; -fx-text-fill: #e2e8f0; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-border-color: #1e293b; -fx-border-radius: 4px;");
        logView.setFocusTraversable(false);
        view.setLogListView(logView);
        controller.logListView = logView;

        bottomPanel.getChildren().add(logView);

        // Bottom Controls Layout Guide
        HBox controlsBar = new HBox();
        controlsBar.setPadding(new Insets(10));
        controlsBar.setAlignment(Pos.CENTER);
        Text controlGuide = new Text("CONTROLS: [A] Move Left | [D] Move Right | [ENTER] Select/Shift | [R] Restart | [ESC] Pause");
        controlGuide.setFill(Color.web("#64748b"));
        controlGuide.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-style: italic; -fx-font-size: 11px;");
        controlsBar.getChildren().add(controlGuide);

        gameLayout.getChildren().addAll(topHud, canvasWrapper, bottomPanel, controlsBar);

        // Add Listener to Theme ComboBox to apply selected styles
        themeCb.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                model.setActiveThemeName(newVal);
                applyTheme(newVal, rootContainer, menuLayout, leaderboardLayout, gameLayout, 
                    menuTitle, menuSubtitle, themeLabel, themeCb, practiceModeCb, 
                    lbTitle, backBtn, logView, timerVal, scoreVal, modeLabel, controlGuide, 
                    selectionBtn, quickBtn, mergeBtn, bubbleBtn, insertionBtn, leaderboardBtn);
            }
        });

        // Initialize with default Theme
        applyTheme("Classic Neon", rootContainer, menuLayout, leaderboardLayout, gameLayout, 
            menuTitle, menuSubtitle, themeLabel, themeCb, practiceModeCb, 
            lbTitle, backBtn, logView, timerVal, scoreVal, modeLabel, controlGuide, 
            selectionBtn, quickBtn, mergeBtn, bubbleBtn, insertionBtn, leaderboardBtn);

        // Add Menu Initially
        rootContainer.getChildren().add(menuLayout);

        Scene scene = new Scene(rootContainer);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Sort Pulse: Timed Puzzle Blitz");

        // Borderless & Fullscreen Settings
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setFullScreen(true);
        primaryStage.setFullScreenExitHint("");
        primaryStage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);

        // Setup Mode Button actions to enter Play state
        selectionBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            showTutorialOverlay(rootContainer, "Selection Sort", model, () -> {
                model.setTargetAlgorithm("Selection Sort");
                model.setGameState("PLAYING");
                controller.initializeGame();
                rootContainer.getChildren().setAll(gameLayout);
                gameLayout.requestFocus();
            });
        });

        quickBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            showTutorialOverlay(rootContainer, "Quick Sort", model, () -> {
                model.setTargetAlgorithm("Quick Sort");
                model.setGameState("PLAYING");
                controller.initializeGame();
                rootContainer.getChildren().setAll(gameLayout);
                gameLayout.requestFocus();
            });
        });

        mergeBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            showTutorialOverlay(rootContainer, "Merge Sort", model, () -> {
                model.setTargetAlgorithm("Merge Sort");
                model.setGameState("PLAYING");
                controller.initializeGame();
                rootContainer.getChildren().setAll(gameLayout);
                gameLayout.requestFocus();
            });
        });

        bubbleBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            showTutorialOverlay(rootContainer, "Bubble Sort", model, () -> {
                model.setTargetAlgorithm("Bubble Sort");
                model.setGameState("PLAYING");
                controller.initializeGame();
                rootContainer.getChildren().setAll(gameLayout);
                gameLayout.requestFocus();
            });
        });

        insertionBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            showTutorialOverlay(rootContainer, "Insertion Sort", model, () -> {
                model.setTargetAlgorithm("Insertion Sort");
                model.setGameState("PLAYING");
                controller.initializeGame();
                rootContainer.getChildren().setAll(gameLayout);
                gameLayout.requestFocus();
            });
        });

        // Key Listeners
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.ESCAPE) {
                if (model.getGameState().equalsIgnoreCase("PLAYING")) {
                    showPauseOverlay(rootContainer, model, controller, menuLayout, gameLayout);
                } else if (model.getGameState().equalsIgnoreCase("PAUSED")) {
                    hidePauseOverlay(rootContainer, model, gameLayout);
                } else if (model.getGameState().equalsIgnoreCase("GAME_OVER")) {
                    model.setGameState("MENU");
                    SoundManager.stopMusic();
                    rootContainer.getChildren().setAll(menuLayout);
                } else {
                    Platform.exit();
                    System.exit(0);
                }
            } else {
                if (model.getGameState().equalsIgnoreCase("PLAYING") || model.getGameState().equalsIgnoreCase("GAME_OVER")) {
                    if (model.getStartCountdown() >= 0) {
                        event.consume();
                        return;
                    }
                    if (System.currentTimeMillis() - lastGameStartTime < 200) {
                        event.consume();
                        return;
                    }
                    controller.handleKeyPress(code);
                }
            }
        });

        // Animation Timer Heartbeat Loop
        AnimationTimer loop = new AnimationTimer() {
            private long lastTimerTickTime = 0;
            private long lastCountdownTickTime = 0;

            @Override
            public void handle(long now) {
                if (model.getGameState().equalsIgnoreCase("MENU")) {
                    return; // Skip loops while in menu
                }

                if (model.getGameState().equalsIgnoreCase("PAUSED")) {
                    lastTimerTickTime = 0;
                    lastCountdownTickTime = 0;
                    return; // Freeze and skip ticks while paused
                }

                if (model.isGameOver()) {
                    view.draw();
                    view.updateHUD();
                    return;
                }

                // Handle the 3-2-1 countdown before game starts
                if (model.getStartCountdown() >= 0) {
                    if (lastCountdownTickTime == 0) {
                        lastCountdownTickTime = now;
                    }
                    long elapsedCountdown = now - lastCountdownTickTime;
                    long limit = model.getStartCountdown() == 0 ? 500_000_000L : 1_000_000_000L; // 0.5s for GO!, 1.0s for numbers
                    
                    if (elapsedCountdown >= limit) {
                        lastCountdownTickTime = now;
                        model.setStartCountdown(model.getStartCountdown() - 1);
                        if (model.getStartCountdown() == 0) {
                            // play GO sound!
                            SoundManager.playSuccess();
                        } else if (model.getStartCountdown() > 0) {
                            // play tick beep sound!
                            SoundManager.playClick();
                        }
                    }
                    view.draw();
                    view.updateHUD();
                    return; // block other game updates during countdown
                } else {
                    lastCountdownTickTime = 0;
                }

                // Core 1-second countdown clock decay
                if (lastTimerTickTime == 0) {
                    lastTimerTickTime = now;
                }
                long elapsed = now - lastTimerTickTime;
                if (elapsed >= 1_000_000_000L) {
                    lastTimerTickTime = now;
                    if (model.getFreezeFrames() <= 0 && !model.isPracticeMode()) {
                        model.setCountdownTimer(model.getCountdownTimer() - 1);
                        if (model.getCountdownTimer() <= 0) {
                            model.setCountdownTimer(0);
                            controller.triggerGameOver();
                        }
                    }
                }

                // Visual freeze frame tick decrement for popping animation
                if (model.getFreezeFrames() > 0) {
                    model.setFreezeFrames(model.getFreezeFrames() - 1);
                    if (model.getFreezeFrames() == 0) {
                        controller.spawnNewPuzzleSet();
                    }
                }

                view.draw();
                view.updateHUD();
            }
        };

        controller.setAnimationTimer(loop);
        loop.start();

        primaryStage.show();
        rootContainer.requestFocus();
    }

    private void showTutorialOverlay(StackPane root, String algorithm, ChromaCascadeModel model, Runnable onStartGame) {
        Theme theme = model.getTheme();
        boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
        String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");

        VBox overlay = new VBox(15);
        overlay.setAlignment(Pos.CENTER);
        overlay.setPadding(new Insets(30));
        overlay.setStyle("-fx-background-color: " + (isGB ? theme.bgHex : "rgba(11, 15, 25, 0.98)") + ";");

        String accentColor;
        String descText = "";
        String proTip;

        if (isGB) {
            accentColor = theme.accentHex;
        } else if (algorithm.equalsIgnoreCase("Selection Sort")) {
            accentColor = "#10b981";
        } else if (algorithm.equalsIgnoreCase("Quick Sort")) {
            accentColor = "#f59e0b";
        } else if (algorithm.equalsIgnoreCase("Bubble Sort")) {
            accentColor = "#ec4899";
        } else if (algorithm.equalsIgnoreCase("Insertion Sort")) {
            accentColor = "#8b5cf6";
        } else {
            accentColor = "#3b82f6";
        }

        if (algorithm.equalsIgnoreCase("Selection Sort")) {
            descText = "VISUAL PLAY GUIDE:\n1. PRESS [A]/[D] to move selection cursor left/right.\n2. SELECT the absolute minimum value from remaining grey blocks.\n3. PRESS [ENTER] on the minimum. It shifts left, turns green (sorted).";
            proTip = "Pro Tip: Use the decimal weights rendered beneath each block to quickly compare values!";
        } else if (algorithm.equalsIgnoreCase("Quick Sort")) {
            descText = "VISUAL PLAY GUIDE:\n1. PIVOT is the rightmost block of active range.\n2. PRESS [A]/[D] to move selection cursor.\n3. PRESS [ENTER] on blocks smaller than/equal to pivot to shift them left. Finally shift pivot.";
            proTip = "Pro Tip: Look at the orange PIVOT badge and follow the TARGET SLOT arrow to partition elements!";
        } else if (algorithm.equalsIgnoreCase("Bubble Sort")) {
            descText = "VISUAL PLAY GUIDE:\n1. COMPARE adjacent elements under the flashing cursor frame.\n2. DECIDE if they are in the correct order (left <= right).\n3. IF OUT OF ORDER, PRESS [ENTER] to swap them. Otherwise, PRESS [D] to step forward.";
            proTip = "Pro Tip: Bubble Sort repeatedly swaps adjacent out-of-order pairs from left to right until the largest element bubbles to the end!";
        } else if (algorithm.equalsIgnoreCase("Insertion Sort")) {
            descText = "VISUAL PLAY GUIDE:\n1. THE CURSOR highlights the element to be inserted into the sorted sub-list on its left.\n2. PRESS [ENTER] to swap the cursor element leftward if it is smaller than its left neighbor.\n3. REPEAT until the element is in its correct sorted position, then press [D] to move to the next item.";
            proTip = "Pro Tip: Shift the element leftward step-by-step using [ENTER] as long as the left element is larger than it!";
        } else {
            descText = "VISUAL PLAY GUIDE:\n1. COMPARE the two subarray head blocks currently being merged.\n2. SELECT the smaller value of the two using the cursor.\n3. PRESS [ENTER] to shift it down into the output area.";
            proTip = "Pro Tip: Focus on the cyan HEAD A and HEAD B badges; they highlight the two elements to compare!";
        }

        Label titleLabel = new Label(algorithm.toUpperCase() + " VISUAL PREVIEW");
        if (isGB) {
            titleLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");
        } else {
            titleLabel.setStyle("-fx-font-family: 'Segoe UI', 'Outfit', sans-serif; -fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + "; -fx-effect: dropshadow(three-pass-box, " + accentColor + "66, 10, 0, 0, 0);");
        }

        Label descLabel = new Label(descText);
        if (isGB) {
            descLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 7px; -fx-text-fill: " + theme.textHex + "; -fx-line-spacing: 6px;");
        } else {
            descLabel.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px; -fx-text-fill: #e2e8f0; -fx-line-spacing: 4px;");
        }
        descLabel.setWrapText(true);
        descLabel.setMaxWidth(700);

        final int[] currentStepHolder = {0};
        final int[] detailedPageHolder = {0};
        final int maxSteps;
        if (algorithm.equalsIgnoreCase("Selection Sort")) {
            maxSteps = 11;
        } else if (algorithm.equalsIgnoreCase("Quick Sort")) {
            maxSteps = 9;
        } else if (algorithm.equalsIgnoreCase("Bubble Sort")) {
            maxSteps = 6;
        } else if (algorithm.equalsIgnoreCase("Insertion Sort")) {
            maxSteps = 6;
        } else {
            maxSteps = 5;
        }

        final int maxDetailedPages;
        if (algorithm.equalsIgnoreCase("Selection Sort") || algorithm.equalsIgnoreCase("Merge Sort")) {
            maxDetailedPages = 6;
        } else {
            maxDetailedPages = 5;
        }
        final String[] detailedPages = new String[maxDetailedPages];
        final String[] detailedPageTitles = new String[maxDetailedPages];

        if (algorithm.equalsIgnoreCase("Selection Sort")) {
            detailedPageTitles[0] = "Core Rules";
            detailedPageTitles[1] = "Process - Pass 1";
            detailedPageTitles[2] = "Process - Pass 2";
            detailedPageTitles[3] = "Process - Pass 3";
            detailedPageTitles[4] = "Shortcuts";
            detailedPageTitles[5] = "Complexity & Logic";

            detailedPages[0] = "OBJECTIVE & CORE RULES:\n- Goal: Find the smallest element in the unsorted suffix and shift it to the sorted prefix.\n- Concept: The array is split into a Green SORTED prefix and a Grey UNSORTED suffix.\n- The green TARGET arrow marks where the next minimum element should go.";
            detailedPages[1] = "SORTING PROCESS - PASS 1:\n1. Scan the entire grey region to find the absolute minimum value (5).\n2. Move selection cursor to 5 and press [ENTER] to swap with target slot (index 0, containing 15).\n3. Result: [5 | 15, 8, 22, 12]";
            detailedPages[2] = "SORTING PROCESS - PASS 2:\n1. Unsorted region is now [15, 8, 22, 12]. Scan to find next minimum (8).\n2. Move cursor to 8 and press [ENTER] to swap with target slot (index 1, containing 15).\n3. Result: [5, 8 | 15, 22, 12]";
            detailedPages[3] = "SORTING PROCESS - PASS 3:\n1. Unsorted region is now [15, 22, 12]. Scan to find next minimum (12).\n2. Move cursor to 12 and press [ENTER] to swap with target slot (index 2, containing 15).\n3. Result: [5, 8, 12 | 22, 15]";
            detailedPages[4] = "KEYBOARD CONTROLS & SHORTCUTS:\n- [A]/[D]: Move Cursor Left/Right | [ENTER]: Select & Shift Element\n- [R]: Restart current puzzle wave immediately if you make a mistake\n- [ESC]: Pause the match | [LEFT]/[RIGHT] or [A]/[D]: Navigate tutorial slides";
            detailedPages[5] = "COMPLEXITY & CODE LOGIC:\n- Time Complexity: Best: O(N^2) | Avg: O(N^2) | Worst: O(N^2) (Always scans entire unsorted suffix)\n- Space Complexity: O(1) auxiliary (In-place sorting)\n- Stability: Unstable (Swapping elements can disrupt relative order of identical keys)\n- Core Logic: Linear search for min, swap to front, advance sorted boundary.";
        } else if (algorithm.equalsIgnoreCase("Quick Sort")) {
            detailedPageTitles[0] = "Core Rules";
            detailedPageTitles[1] = "Process - Scan";
            detailedPageTitles[2] = "Process - Pivot Place";
            detailedPageTitles[3] = "Shortcuts";
            detailedPageTitles[4] = "Complexity & Logic";

            detailedPages[0] = "OBJECTIVE & CORE RULES:\n- Goal: Partition the active subarray around a pivot so that smaller/equal elements go left, and larger elements go right.\n- Concept: The rightmost element is the orange PIVOT. The orange TARGET arrow marks the boundary for elements <= pivot.";
            detailedPages[1] = "SORTING PROCESS - PARTITION SCAN:\n1. Scan elements from left to right, comparing each with Pivot (10).\n2. If element <= Pivot: Press [ENTER] to swap to the TARGET slot (indicated by orange arrow).\n3. Scan 12 > 10 (skip), 18 > 10 (skip), scan 5 <= 10 -> swap 5 with index 0.";
            detailedPages[2] = "SORTING PROCESS - PIVOT SWAP:\n1. Continue scan: 15 > 10 (skip). Scan complete.\n2. Finally, swap the Pivot (10) into the final target slot boundary (index 1, containing 18).\n3. Result: [5, 10 | 12, 15, 18]. Pivot 10 is now locked in green.";
            detailedPages[3] = "KEYBOARD CONTROLS & SHORTCUTS:\n- [A]/[D]: Move Cursor Left/Right | [ENTER]: Swap element <= pivot, or swap pivot\n- [R]: Restart current puzzle wave immediately if you make a mistake\n- [ESC]: Pause the match | [LEFT]/[RIGHT] or [A]/[D]: Navigate tutorial slides";
            detailedPages[4] = "COMPLEXITY & CODE LOGIC:\n- Time Complexity: Best: O(N log N) | Avg: O(N log N) | Worst: O(N^2) (Poor pivot selection, e.g., already sorted)\n- Space Complexity: O(log N) stack depth (Recursive calls)\n- Stability: Unstable (Swapping pivot can change relative order of identical elements)\n- Core Logic: Divide & conquer. Partition elements around pivot, recursively sort partitions.";
        } else if (algorithm.equalsIgnoreCase("Bubble Sort")) {
            detailedPageTitles[0] = "Core Rules";
            detailedPageTitles[1] = "Process - Skip";
            detailedPageTitles[2] = "Process - Bubble Swap";
            detailedPageTitles[3] = "Shortcuts";
            detailedPageTitles[4] = "Complexity & Logic";

            detailedPages[0] = "OBJECTIVE & CORE RULES:\n- Goal: Compare adjacent elements and swap if they are out of order. Repeat until sorted.\n- Concept: A flashing cursor frame spans two adjacent elements. It bubbles the largest elements to the end of the unsorted segment.";
            detailedPages[1] = "SORTING PROCESS - CASE A (SKIP):\n1. Compare adjacent elements under the flashing frame: 8 vs 15.\n2. Since 8 <= 15, they are in correct relative order.\n3. Press [D] to skip swapping and advance frame to next pair.";
            detailedPages[2] = "SORTING PROCESS - CASE B (SWAP):\n1. Frame is at 15 vs 5. Since 15 > 5, they are out of order.\n2. Press [ENTER] to swap them -> array becomes [8, 5, 15, 12, 10].\n3. Continue bubbling 15 to the end. It will lock green.";
            detailedPages[3] = "KEYBOARD CONTROLS & SHORTCUTS:\n- [D]: Skip swap & advance frame | [ENTER]: Swap the two adjacent elements\n- [R]: Restart current puzzle wave immediately if you make a mistake\n- [ESC]: Pause the match | [LEFT]/[RIGHT] or [A]/[D]: Navigate tutorial slides";
            detailedPages[4] = "COMPLEXITY & CODE LOGIC:\n- Time Complexity: Best: O(N) (Optimized with early exit flag) | Avg: O(N^2) | Worst: O(N^2)\n- Space Complexity: O(1) auxiliary (In-place)\n- Stability: Stable (Does not swap equal elements, preserving original order)\n- Core Logic: Repeated adjacent comparison and swap passes, bubbling largest value to end.";
        } else if (algorithm.equalsIgnoreCase("Insertion Sort")) {
            detailedPageTitles[0] = "Core Rules";
            detailedPageTitles[1] = "Process - Shift";
            detailedPageTitles[2] = "Process - Lock Position";
            detailedPageTitles[3] = "Shortcuts";
            detailedPageTitles[4] = "Complexity & Logic";

            detailedPages[0] = "OBJECTIVE & CORE RULES:\n- Goal: Insert each new element into its proper position relative to the sorted prefix on the left.\n- Concept: The sorted prefix on the left is outlined by a dashed box. The cursor highlights the active element to insert.";
            detailedPages[1] = "SORTING PROCESS - SHIFT LEFT:\n1. Active element is 8 at index 2. Left neighbor is 12.\n2. Compare active (8) < left neighbor (12).\n3. Since 8 < 12, press [ENTER] to swap active element leftward. Array becomes: [5, 8, 12, 15, 10]";
            detailedPages[2] = "SORTING PROCESS - POSITION FOUND:\n1. Active element 8 is now at index 1. Left neighbor is 5.\n2. Compare active (8) >= left neighbor (5). Insertion position found.\n3. Press [D] to lock prefix [5, 8, 12] in green and advance.";
            detailedPages[3] = "KEYBOARD CONTROLS & SHORTCUTS:\n- [ENTER]: Swap active element leftward | [D]: Finalize position & move to next item\n- [R]: Restart current puzzle wave immediately if you make a mistake\n- [ESC]: Pause the match | [LEFT]/[RIGHT] or [A]/[D]: Navigate tutorial slides";
            detailedPages[4] = "COMPLEXITY & CODE LOGIC:\n- Time Complexity: Best: O(N) (Already sorted) | Avg: O(N^2) | Worst: O(N^2) (Reversed array)\n- Space Complexity: O(1) auxiliary (In-place)\n- Stability: Stable (Does not swap past equal elements, preserving original order)\n- Core Logic: Take active element and shift leftward until its correct position is found.";
        } else {
            detailedPageTitles[0] = "Core Rules";
            detailedPageTitles[1] = "Process - Compare";
            detailedPageTitles[2] = "Process - Merge Head";
            detailedPageTitles[3] = "Process - Finalize";
            detailedPageTitles[4] = "Shortcuts";
            detailedPageTitles[5] = "Complexity & Logic";

            detailedPages[0] = "OBJECTIVE & CORE RULES:\n- Goal: Merge two sorted sub-lists (Subarray A and Subarray B) into a single sorted output list.\n- Concept: Current head elements of both subarrays are marked with HEAD A and HEAD B badges. Compare them and select the smaller.";
            detailedPages[1] = "SORTING PROCESS - COMPARE HEADS:\n1. Compare HEAD A (5) vs HEAD B (3).\n2. Since 3 < 5, select HEAD B (3) using [A]/[D] and press [ENTER] to merge it down.\n3. Output array first slot becomes 3.";
            detailedPages[2] = "SORTING PROCESS - MERGE HEAD:\n1. New heads: HEAD A (5) vs HEAD B (10).\n2. Since 5 < 10, select HEAD A (5) and press [ENTER] to merge it down.\n3. Output array becomes [3, 5].";
            detailedPages[3] = "SORTING PROCESS - FINALIZE:\n1. New heads: HEAD A (15) vs HEAD B (10). Merge HEAD B (10) -> Output: [3, 5, 10].\n2. Subarray B is empty. Select remaining HEAD A (15) and merge it.\n3. Result: [3, 5, 10, 15] is fully merged.";
            detailedPages[4] = "KEYBOARD CONTROLS & SHORTCUTS:\n- [A]/[D]: Move cursor between HEAD A and HEAD B | [ENTER]: Shift selected head to output\n- [R]: Restart current puzzle wave immediately if you make a mistake\n- [ESC]: Pause the match | [LEFT]/[RIGHT] or [A]/[D]: Navigate tutorial slides";
            detailedPages[5] = "COMPLEXITY & CODE LOGIC:\n- Time Complexity: Best: O(N log N) | Avg: O(N log N) | Worst: O(N log N) (Guaranteed performance)\n- Space Complexity: O(N) auxiliary space (Requires buffer for merging subarrays)\n- Stability: Stable (Maintains relative order of equal elements during merge)\n- Core Logic: Divide array in half recursively, merge sorted halves back together.";
        }

        // Tab Selection Controls
        final boolean[] showDetailedHolder = {false};
        Button btnSimplified = new Button("SIMPLIFIED TUTORIAL");
        Button btnDetailed = new Button("DETAILED TUTORIAL");

        String activeTabStyle;
        String inactiveTabStyle;
        if (isGB) {
            activeTabStyle = "-fx-background-color: " + theme.accentHex + "; -fx-text-fill: " + theme.bgHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: 8px; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: hand; -fx-border-color: " + theme.accentHex + "; -fx-border-width: 1px; -fx-border-radius: 4px;";
            inactiveTabStyle = "-fx-background-color: " + theme.panelBgHex + "; -fx-text-fill: " + theme.textMutedHex + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: 8px; -fx-padding: 6px 12px; -fx-background-radius: 4px; -fx-cursor: hand; -fx-border-color: " + theme.borderHex + "; -fx-border-width: 1px; -fx-border-radius: 4px;";
        } else {
            activeTabStyle = "-fx-background-color: " + accentColor + "; -fx-text-fill: #ffffff; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-border-color: " + accentColor + "; -fx-border-width: 1px; -fx-border-radius: 6px;";
            inactiveTabStyle = "-fx-background-color: #1e293b; -fx-text-fill: #94a3b8; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-border-color: #334155; -fx-border-width: 1px; -fx-border-radius: 6px;";
        }

        HBox navBox = new HBox(20);
        navBox.setAlignment(Pos.CENTER);
        navBox.setPadding(new Insets(5, 0, 5, 0));

        String navBtnBg = isGB ? theme.panelBgHex : "#1e293b";
        String navBtnText = isGB ? theme.textHex : "#f8fafc";
        String navBtnBorder = isGB ? theme.borderHex : "#334155";
        String navFontSize = isGB ? "8px" : "11px";

        Button prevBtn = new Button("PREV STEP");
        prevBtn.setStyle("-fx-background-color: " + navBtnBg + "; -fx-text-fill: " + navBtnText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + navFontSize + "; -fx-padding: 8px 20px; -fx-background-radius: 5px; -fx-cursor: hand; -fx-border-color: " + navBtnBorder + "; -fx-border-width: 1px; -fx-border-radius: 5px;");
        prevBtn.setOnMouseEntered(e -> SoundManager.playHover());
        
        Label stepLabel = new Label();
        stepLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "9px" : "13px") + "; -fx-text-fill: " + navBtnText + "; -fx-font-weight: bold; -fx-min-width: 100; -fx-alignment: center;");
        stepLabel.setAlignment(Pos.CENTER);

        Button nextBtn = new Button("NEXT STEP");
        nextBtn.setStyle("-fx-background-color: " + navBtnBg + "; -fx-text-fill: " + navBtnText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + navFontSize + "; -fx-padding: 8px 20px; -fx-background-radius: 5px; -fx-cursor: hand; -fx-border-color: " + navBtnBorder + "; -fx-border-width: 1px; -fx-border-radius: 5px;");
        nextBtn.setOnMouseEntered(e -> SoundManager.playHover());

        navBox.getChildren().addAll(prevBtn, stepLabel, nextBtn);

        final String fDescText = descText;

        Runnable refreshTabContent = () -> {
            boolean showDetailed = showDetailedHolder[0];
            if (showDetailed) {
                descLabel.setText(detailedPages[detailedPageHolder[0]]);
                stepLabel.setText(String.format("Page %d of %d: %s", detailedPageHolder[0] + 1, maxDetailedPages, detailedPageTitles[detailedPageHolder[0]]));
                prevBtn.setText("PREV PAGE");
                nextBtn.setText("NEXT PAGE");
                btnDetailed.setStyle(activeTabStyle);
                btnSimplified.setStyle(inactiveTabStyle);
            } else {
                descLabel.setText(fDescText);
                stepLabel.setText(String.format("Step %d of %d", currentStepHolder[0] + 1, maxSteps));
                prevBtn.setText("PREV STEP");
                nextBtn.setText("NEXT STEP");
                btnDetailed.setStyle(inactiveTabStyle);
                btnSimplified.setStyle(activeTabStyle);
            }
        };

        // Initialize Tab Styles
        refreshTabContent.run();

        btnSimplified.setOnAction(e -> {
            if (showDetailedHolder[0]) {
                showDetailedHolder[0] = false;
                currentStepHolder[0] = 0;
                SoundManager.playClick();
                refreshTabContent.run();
            }
        });

        btnDetailed.setOnAction(e -> {
            if (!showDetailedHolder[0]) {
                showDetailedHolder[0] = true;
                detailedPageHolder[0] = 0;
                SoundManager.playClick();
                refreshTabContent.run();
            }
        });

        if (!isGB) {
            btnSimplified.setOnMouseEntered(e -> {
                if (showDetailedHolder[0]) {
                    btnSimplified.setStyle("-fx-background-color: #334155; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-border-color: #475569; -fx-border-width: 1px; -fx-border-radius: 6px;");
                }
            });
            btnSimplified.setOnMouseExited(e -> {
                if (showDetailedHolder[0]) {
                    btnSimplified.setStyle(inactiveTabStyle);
                }
            });
            btnDetailed.setOnMouseEntered(e -> {
                if (!showDetailedHolder[0]) {
                    btnDetailed.setStyle("-fx-background-color: #334155; -fx-text-fill: #f8fafc; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 8px 16px; -fx-background-radius: 6px; -fx-cursor: hand; -fx-border-color: #475569; -fx-border-width: 1px; -fx-border-radius: 6px;");
                }
            });
            btnDetailed.setOnMouseExited(e -> {
                if (!showDetailedHolder[0]) {
                    btnDetailed.setStyle(inactiveTabStyle);
                }
            });
        }

        HBox tabHeader = new HBox(15);
        tabHeader.setAlignment(Pos.CENTER);
        tabHeader.getChildren().addAll(btnSimplified, btnDetailed);

        VBox descContainer = new VBox();
        descContainer.setAlignment(Pos.CENTER);
        descContainer.setPadding(new Insets(10, 15, 10, 15));
        descContainer.setMaxWidth(700);
        if (isGB) {
            descContainer.setPrefHeight(90);
            descContainer.setMinHeight(90);
            descContainer.setMaxHeight(90);
        } else {
            descContainer.setPrefHeight(130);
            descContainer.setMinHeight(130);
            descContainer.setMaxHeight(130);
        }
        descContainer.setStyle("-fx-background-color: " + (isGB ? theme.panelBgHex : "#0f172a") + "; -fx-border-color: " + (isGB ? theme.borderHex : "#1e293b") + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        descContainer.getChildren().add(descLabel);

        // Canvas for animated sorting preview (expanded height for legend and keys)
        Canvas canvas = new Canvas(700, 320);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Pixelation canvas for tutorial (GameBoy mode)
        final Canvas tutPixCanvas = isGB ? new Canvas(350, 160) : null;
        final GraphicsContext tutPixGc = isGB ? tutPixCanvas.getGraphicsContext2D() : null;

        prevBtn.setOnAction(e -> {
            boolean showDetailed = showDetailedHolder[0];
            if (showDetailed) {
                if (detailedPageHolder[0] > 0) {
                    detailedPageHolder[0]--;
                    SoundManager.playClick();
                    refreshTabContent.run();
                }
            } else {
                if (currentStepHolder[0] > 0) {
                    currentStepHolder[0]--;
                    SoundManager.playClick();
                    refreshTabContent.run();
                }
            }
        });

        nextBtn.setOnAction(e -> {
            boolean showDetailed = showDetailedHolder[0];
            if (showDetailed) {
                if (detailedPageHolder[0] < maxDetailedPages - 1) {
                    detailedPageHolder[0]++;
                    SoundManager.playClick();
                    refreshTabContent.run();
                }
            } else {
                if (currentStepHolder[0] < maxSteps - 1) {
                    currentStepHolder[0]++;
                    SoundManager.playClick();
                    refreshTabContent.run();
                }
            }
        });

        VBox tipBox = new VBox(8);
        tipBox.setPadding(new Insets(12));
        tipBox.setMaxWidth(700);
        tipBox.setStyle("-fx-background-color: " + (isGB ? theme.panelBgHex : "#1e293b") + "; -fx-border-color: " + accentColor + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px;");
        Label tipLabel = new Label(proTip);
        tipLabel.setWrapText(true);
        tipLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "7px" : "13px") + "; -fx-text-fill: " + (isGB ? theme.textMutedHex : "#94a3b8") + "; -fx-font-style: italic;");
        tipBox.getChildren().add(tipLabel);

        HBox btnBox = new HBox(20);
        btnBox.setAlignment(Pos.CENTER);
        btnBox.setPadding(new Insets(10, 0, 0, 0));

        String startFontSize = isGB ? "9px" : "14px";
        String startTextCol = isGB ? theme.bgHex : "#ffffff";
        Button startBtn = new Button("START GAME");
        startBtn.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: " + startTextCol + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-cursor: hand;");
        startBtn.setOnMouseEntered(e -> {
            SoundManager.playHover();
            startBtn.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: " + startTextCol + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-cursor: hand;" + (isGB ? "" : " -fx-effect: dropshadow(three-pass-box, " + accentColor + "66, 8, 0, 0, 0);"));
        });
        startBtn.setOnMouseExited(e -> startBtn.setStyle("-fx-background-color: " + accentColor + "; -fx-text-fill: " + startTextCol + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-cursor: hand;"));

        String backBg = isGB ? theme.panelBgHex : "#1e293b";
        String backText = isGB ? theme.textHex : "#f8fafc";
        String backBorder = isGB ? theme.borderHex : "#334155";
        String backHoverBg = isGB ? theme.textHex : "#ef4444";
        String backHoverText = isGB ? theme.bgHex : "#ffffff";
        Button backBtn = new Button("BACK");
        backBtn.setStyle("-fx-background-color: " + backBg + "; -fx-text-fill: " + backText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + backBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;");
        backBtn.setOnMouseEntered(e -> {
            SoundManager.playHover();
            backBtn.setStyle("-fx-background-color: " + backHoverBg + "; -fx-text-fill: " + backHoverText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + backHoverBg + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;");
        });
        backBtn.setOnMouseExited(e -> backBtn.setStyle("-fx-background-color: " + backBg + "; -fx-text-fill: " + backText + "; -fx-font-family: " + fontFam + "; -fx-font-weight: bold; -fx-font-size: " + startFontSize + "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + backBorder + "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;"));

        btnBox.getChildren().addAll(startBtn, backBtn);
        overlay.getChildren().addAll(titleLabel, tabHeader, descContainer, canvas, navBox, tipBox, btnBox);

        root.getChildren().add(overlay);
        overlay.requestFocus();

        AnimationTimer tutorialTimer = new AnimationTimer() {
            private double[] selectionVals = {15, 8, 22, 5, 12};
            private double[] selectionX = {100, 180, 260, 340, 420};
            private double[] selectionTargetX = {100, 180, 260, 340, 420};
            private boolean[] selectionGreen = {false, false, false, false, false};
            private int selectionCursor = 0;
            
            private double[] quickVals = {12, 18, 5, 15, 10};
            private double[] quickX = {100, 180, 260, 340, 420};
            private double[] quickTargetX = {100, 180, 260, 340, 420};
            private boolean[] quickGreen = {false, false, false, false, false};
            private int quickCursor = 0;
            private boolean pivotMode = false;
            
            private double[] mergeSub1 = {5, 15};
            private double[] mergeSub2 = {3, 10};
            private double[] mergeOut = {0, 0, 0, 0};
            private double[] mergeSub1X = {120, 200};
            private double[] mergeSub2X = {340, 420};
            
            private double[] mergeSub1Y = {50, 50};
            private double[] mergeSub2Y = {50, 50};
            
            private boolean[] mergeSub1Merged = {false, false};
            private boolean[] mergeSub2Merged = {false, false};
            private boolean[] mergeOutGreen = {false, false, false, false};

            private double[] bubbleVals = {8, 15, 5, 12, 10};
            private double[] bubbleX = {100, 180, 260, 340, 420};
            private double[] bubbleTargetX = {100, 180, 260, 340, 420};
            private boolean[] bubbleGreen = {false, false, false, false, false};
            private int bubbleCursor = 0;

            private double[] insertionVals = {5, 12, 8, 15, 10};
            private double[] insertionX = {100, 180, 260, 340, 420};
            private double[] insertionTargetX = {100, 180, 260, 340, 420};
            private boolean[] insertionGreen = {false, false, false, false, false};
            private int insertionCursor = 0;
            
            @Override
            public void handle(long now) {
                gc.setFill(isGB ? theme.panelBg : Color.web("#0f172a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setStroke(isGB ? theme.border : Color.web("#1e293b"));
                gc.setLineWidth(1.0);
                gc.strokeRect(0, 0, canvas.getWidth(), canvas.getHeight());
                
                if (showDetailedHolder[0]) {
                    drawDetailedVisualGuide(gc, algorithm, detailedPageHolder[0]);
                } else {
                    int step = currentStepHolder[0];
                    if (algorithm.equalsIgnoreCase("Selection Sort")) {
                        updateAndDrawSelection(gc, step);
                    } else if (algorithm.equalsIgnoreCase("Quick Sort")) {
                        updateAndDrawQuick(gc, step);
                    } else if (algorithm.equalsIgnoreCase("Bubble Sort")) {
                        updateAndDrawBubble(gc, step);
                    } else if (algorithm.equalsIgnoreCase("Insertion Sort")) {
                        updateAndDrawInsertion(gc, step);
                    } else {
                        updateAndDrawMerge(gc, step);
                    }
                }
            }

            private void drawKeyCap(GraphicsContext gc, double x, double y, String label, boolean isPressed) {
                double w = label.length() * 10 + 20;
                double h = 24;
                
                // Key shadow
                gc.setFill(isGB ? theme.bg.darker() : Color.web("#020617"));
                gc.fillRoundRect(x + 1, y + 2, w, h, 4, 4);
                
                // Key base
                gc.setFill(isPressed ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.border : Color.web("#1e293b")));
                gc.setStroke(isPressed ? (isGB ? theme.sorted.brighter() : Color.web("#34d399")) : (isGB ? theme.textMuted : Color.web("#475569")));
                gc.setLineWidth(1.5);
                gc.fillRoundRect(x, y, w, h, 4, 4);
                gc.strokeRoundRect(x, y, w, h, 4, 4);
                
                // Key text
                gc.setFill(isPressed ? (isGB ? theme.bg : Color.BLACK) : (isGB ? theme.text : Color.web("#f8fafc")));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 10, isGB));
                gc.fillText(label, x + (w - (label.length() * 6)) / 2.0 - 2, y + 15);
            }

            private void drawLegend(GraphicsContext gc, String algo, double x, double y) {
                gc.setFill(isGB ? theme.bg.deriveColor(0,1,1,0.6) : Color.web("#1e293b", 0.6));
                gc.setStroke(isGB ? theme.border.deriveColor(0,1,1,0.8) : Color.web("#334155", 0.8));
                gc.setLineWidth(1.0);
                gc.fillRoundRect(x, y, 680, 75, 6, 6);
                gc.strokeRoundRect(x, y, 680, 75, 6, 6);
                
                // Draw Legend Title
                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 9, isGB));
                gc.fillText(isGB ? "LEGEND" : "VISUAL UI INDICATORS LEGEND", x + 12, y + 15);
                
                // Draw Legend elements
                double startX = x + 12;
                double startY = y + 24;
                
                // 1. Target Arrow
                gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                gc.setLineWidth(2.0);
                gc.strokeLine(startX, startY + 15, startX, startY + 5);
                gc.strokeLine(startX - 3, startY + 8, startX, startY + 5);
                gc.strokeLine(startX + 3, startY + 8, startX, startY + 5);
                gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                gc.fillText(isGB ? "Target Arrow (Destination index)" : "Target Slot Arrow (Destination index for shifts)", startX + 12, startY + 13);
                
                // 2. Active range
                double activeX = x + (isGB ? 280 : 250);
                gc.setStroke(isGB ? theme.textMuted : Color.web("#64748b"));
                gc.setLineDashes(new double[]{4.0, 3.0});
                gc.strokeRoundRect(activeX, startY + 3, 30, 12, 2, 2);
                gc.setLineDashes(null);
                gc.fillText(isGB ? "Dashed Frame (Active segment)" : "Dashed Frame (Active Subarray Segment)", activeX + 36, startY + 13);
                
                // 3. Algorithm specific legends
                double specificX = x + (isGB ? 495 : 480);
                if (algo.equalsIgnoreCase("Quick Sort")) {
                    gc.setFill(isGB ? theme.accent : Color.web("#ea580c"));
                    gc.fillRoundRect(specificX, startY + 2, 32, 13, 2, 2);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 7, isGB));
                    gc.fillText("PIVOT", specificX + (isGB ? 2 : 5), startY + 11);
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                    gc.fillText(isGB ? "Pivot Badge" : "Pivot Block Badge", specificX + 38, startY + 13);
                } else if (algo.equalsIgnoreCase("Merge Sort")) {
                    gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                    gc.fillRoundRect(specificX, startY + 2, 32, 13, 2, 2);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 7, isGB));
                    gc.fillText("HEAD", specificX + (isGB ? 4 : 6), startY + 11);
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                    gc.fillText(isGB ? "Subarray Heads" : "Active Subarray Heads", specificX + 38, startY + 13);
                } else {
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillRoundRect(specificX, startY + 2, 32, 13, 2, 2);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 7, isGB));
                    gc.fillText("SORT", specificX + (isGB ? 4 : 6), startY + 11);
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                    gc.fillText(isGB ? "Sorted Block" : "Green block (Sorted & Finalized)", specificX + 38, startY + 13);
                }
            }
            
            private void updateAndDrawSelection(GraphicsContext gc, int step) {
                // Initial values: selectionVals = {15, 8, 22, 5, 12};
                // index mapping: 0:15, 1:8, 2:22, 3:5, 4:12
                if (step == 0) {
                    selectionTargetX[0] = 100; selectionTargetX[1] = 180; selectionTargetX[2] = 260; selectionTargetX[3] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 2; // scanning
                    selectionGreen[0] = false; selectionGreen[1] = false; selectionGreen[2] = false; selectionGreen[3] = false; selectionGreen[4] = false;
                } else if (step == 1) {
                    selectionTargetX[0] = 100; selectionTargetX[1] = 180; selectionTargetX[2] = 260; selectionTargetX[3] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 3; // select 5
                    selectionGreen[0] = false; selectionGreen[1] = false; selectionGreen[2] = false; selectionGreen[3] = false; selectionGreen[4] = false;
                } else if (step == 2) {
                    // 5 shifted to index 0. Order: 5, 15, 8, 22, 12
                    selectionTargetX[3] = 100; selectionTargetX[0] = 180; selectionTargetX[1] = 260; selectionTargetX[2] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 2; // scanning
                    selectionGreen[3] = true; selectionGreen[0] = false; selectionGreen[1] = false; selectionGreen[2] = false; selectionGreen[4] = false;
                } else if (step == 3) {
                    selectionTargetX[3] = 100; selectionTargetX[0] = 180; selectionTargetX[1] = 260; selectionTargetX[2] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 1; // select 8
                    selectionGreen[3] = true; selectionGreen[0] = false; selectionGreen[1] = false; selectionGreen[2] = false; selectionGreen[4] = false;
                } else if (step == 4) {
                    // 8 shifted to index 1. Order: 5, 8, 15, 22, 12
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[0] = 260; selectionTargetX[2] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 4; // scanning
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[0] = false; selectionGreen[2] = false; selectionGreen[4] = false;
                } else if (step == 5) {
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[0] = 260; selectionTargetX[2] = 340; selectionTargetX[4] = 420;
                    selectionCursor = 4; // select 12
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[0] = false; selectionGreen[2] = false; selectionGreen[4] = false;
                } else if (step == 6) {
                    // 12 shifted to index 2. Order: 5, 8, 12, 15, 22
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[4] = 260; selectionTargetX[0] = 340; selectionTargetX[2] = 420;
                    selectionCursor = 0; // scanning
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[4] = true; selectionGreen[0] = false; selectionGreen[2] = false;
                } else if (step == 7) {
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[4] = 260; selectionTargetX[0] = 340; selectionTargetX[2] = 420;
                    selectionCursor = 0; // select 15
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[4] = true; selectionGreen[0] = false; selectionGreen[2] = false;
                } else if (step == 8) {
                    // 15 shifted to index 3. Order: 5, 8, 12, 15, 22
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[4] = 260; selectionTargetX[0] = 340; selectionTargetX[2] = 420;
                    selectionCursor = 2; // scanning
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[4] = true; selectionGreen[0] = true; selectionGreen[2] = false;
                } else if (step == 9) {
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[4] = 260; selectionTargetX[0] = 340; selectionTargetX[2] = 420;
                    selectionCursor = 2; // select 22
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[4] = true; selectionGreen[0] = true; selectionGreen[2] = false;
                } else {
                    // All sorted and green
                    selectionTargetX[3] = 100; selectionTargetX[1] = 180; selectionTargetX[4] = 260; selectionTargetX[0] = 340; selectionTargetX[2] = 420;
                    selectionCursor = -1; // hide cursor
                    selectionGreen[3] = true; selectionGreen[1] = true; selectionGreen[4] = true; selectionGreen[0] = true; selectionGreen[2] = true;
                }
                
                for (int i = 0; i < 5; i++) {
                    selectionX[i] += (selectionTargetX[i] - selectionX[i]) * 0.15;
                }
                
                // Draw title of the current phase
                gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGB));
                if (step == 0) {
                    gc.fillText("PHASE 1: SCAN FOR THE MINIMUM ELEMENT IN UNSORTED RANGE [0, 4]", 15, 25);
                } else if (step == 1) {
                    gc.fillText("PHASE 2: NAVIGATE TO MINIMUM AND PRESS ENTER TO SHIFT TO TARGET SLOT 0", 15, 25);
                } else if (step == 2) {
                    gc.fillText("PHASE 3: SCAN UNSORTED RANGE [1, 4] TO LOCATE NEXT MINIMUM", 15, 25);
                } else if (step == 3) {
                    gc.fillText("PHASE 4: SHIFT NEXT MINIMUM TO NEXT TARGET SLOT 1", 15, 25);
                } else if (step == 4) {
                    gc.fillText("PHASE 5: SCAN UNSORTED RANGE [2, 4] TO LOCATE NEXT MINIMUM", 15, 25);
                } else if (step == 5) {
                    gc.fillText("PHASE 6: SHIFT NEXT MINIMUM TO NEXT TARGET SLOT 2", 15, 25);
                } else if (step == 6) {
                    gc.fillText("PHASE 7: SCAN UNSORTED RANGE [3, 4] TO LOCATE NEXT MINIMUM", 15, 25);
                } else if (step == 7) {
                    gc.fillText("PHASE 8: SHIFT NEXT MINIMUM TO NEXT TARGET SLOT 3", 15, 25);
                } else if (step == 8) {
                    gc.fillText("PHASE 9: SCAN UNSORTED RANGE [4, 4] TO LOCATE NEXT MINIMUM", 15, 25);
                } else if (step == 9) {
                    gc.fillText("PHASE 10: SHIFT FINAL ELEMENT TO NEXT TARGET SLOT 4", 15, 25);
                } else {
                    gc.fillText("PHASE 11: SELECTED MINIMUMS ARE FINALIZED AND LOCKED", 15, 25);
                }

                // Draw active subarray dashed frame for selection sort
                double startX = 100;
                double boxWidth = 80;
                double boxHeight = 65;
                double startY = 60;
                
                int targetIndex = step / 2;
                if (step < 10) {
                    double rx1 = startX + targetIndex * boxWidth + 2;
                    double rx2 = startX + 5 * boxWidth - 2;
                    gc.setStroke(isGB ? theme.textMuted.deriveColor(0,1,1,0.6) : Color.web("#475569", 0.6));
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(rx1, startY - 10, rx2 - rx1, boxHeight + 20, 6, 6);
                    gc.setLineDashes(null);
                    
                    gc.setFill(isGB ? theme.textMuted.deriveColor(0,1,1,0.7) : Color.web("#94a3b8", 0.7));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("UNSORTED RANGE", rx1 + 6, startY - 14);

                    // Draw Target Slot pointer
                    double arrowX = startX + targetIndex * boxWidth + boxWidth / 2.0;
                    double arrowYHead = startY - 4;
                    double arrowYTail = startY - 18;
                    gc.setStroke(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(arrowX, arrowYTail, arrowX, arrowYHead);
                    gc.strokeLine(arrowX - 3, arrowYHead - 3, arrowX, arrowYHead);
                    gc.strokeLine(arrowX + 3, arrowYHead - 3, arrowX, arrowYHead);
                    
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("TARGET", arrowX - 16, arrowYTail - 4);
                }
                
                for (int i = 0; i < 5; i++) {
                    double x = selectionX[i];
                    double y = 60;
                    double w = 65;
                    double h = 65;
                    
                    boolean isSorted = selectionGreen[i];
                    Color col = isSorted ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.unsorted : Color.web("#334155"));
                    
                    gc.setFill(col);
                    gc.fillRoundRect(x, y, w, h, 6, 6);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, w, h, 6, 6);
                    
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                    gc.fillText(String.valueOf((int)selectionVals[i]), x + 22, y + 38);
                }
                
                if (selectionCursor != -1) {
                    double cursorX = selectionX[selectionCursor];
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(cursorX - 2, 60 - 2, 69, 69, 6, 6);
                }
                
                // Draw keyboard simulation (y = 145)
                boolean dPressed = (step < 10 && step % 2 == 0);
                boolean enterPressed = (step < 10 && step % 2 == 1);
                
                drawKeyCap(gc, 200, 145, "A", false);
                drawKeyCap(gc, 250, 145, "D", dPressed);
                drawKeyCap(gc, 320, 145, "ENTER", enterPressed);

                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGB));
                if (step == 0) {
                    gc.fillText("Virtual Player pressing [D] to scan unsorted blocks...", 170, 195);
                } else if (step == 1) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Found Minimum 5! Pressing [ENTER] to shift to target index 0...", 130, 195);
                } else if (step == 2) {
                    gc.fillText("Scanning unsorted section to find next minimum...", 160, 195);
                } else if (step == 3) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Found Minimum 8! Pressing [ENTER] to shift to target index 1...", 130, 195);
                } else if (step == 4) {
                    gc.fillText("Scanning unsorted section to find next minimum...", 160, 195);
                } else if (step == 5) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Found Minimum 12! Pressing [ENTER] to shift to target index 2...", 130, 195);
                } else if (step == 6) {
                    gc.fillText("Scanning unsorted section to find next minimum...", 160, 195);
                } else if (step == 7) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Found Minimum 15! Pressing [ENTER] to shift to target index 3...", 130, 195);
                } else if (step == 8) {
                    gc.fillText("Only one block left in unsorted range...", 210, 195);
                } else if (step == 9) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Pressing [ENTER] to finalize last element 22...", 190, 195);
                } else {
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillText("Sorting complete! All blocks are finalized and locked in green.", 150, 195);
                }
                
                drawLegend(gc, "Selection Sort", 10, 235);
            }
            
            private void updateAndDrawQuick(GraphicsContext gc, int step) {
                // Initial values: quickVals = {12, 18, 5, 15, 10}
                // index mapping: 0:12, 1:18, 2:5, 3:15, 4:10
                if (step == 0) {
                    quickTargetX[0] = 100; quickTargetX[1] = 180; quickTargetX[2] = 260; quickTargetX[3] = 340; quickTargetX[4] = 420;
                    quickCursor = 0; // scanning 12
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = false;
                    pivotMode = false;
                } else if (step == 1) {
                    quickTargetX[0] = 100; quickTargetX[1] = 180; quickTargetX[2] = 260; quickTargetX[3] = 340; quickTargetX[4] = 420;
                    quickCursor = 1; // scanning 18
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = false;
                    pivotMode = false;
                } else if (step == 2) {
                    quickTargetX[0] = 100; quickTargetX[1] = 180; quickTargetX[2] = 260; quickTargetX[3] = 340; quickTargetX[4] = 420;
                    quickCursor = 2; // select 5
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = false;
                    pivotMode = false;
                } else if (step == 3) {
                    // 5 shifted to index 0. Order: 5, 12, 18, 15, 10
                    quickTargetX[2] = 100; quickTargetX[0] = 180; quickTargetX[1] = 260; quickTargetX[3] = 340; quickTargetX[4] = 420;
                    quickCursor = 3; // scanning 15
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = false;
                    pivotMode = false;
                } else if (step == 4) {
                    quickTargetX[2] = 100; quickTargetX[0] = 180; quickTargetX[1] = 260; quickTargetX[3] = 340; quickTargetX[4] = 420;
                    quickCursor = 4; // select Pivot 10
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = false;
                    pivotMode = true;
                } else if (step == 5) {
                    // Pivot 10 shifted to index 1 (turns green).
                    // Sub-partition [2, 4] (values 12, 18, 15) with Pivot 15.
                    // Scan 12 (select 12) at index 2 (element 0).
                    quickTargetX[2] = 100; quickTargetX[4] = 180; quickTargetX[0] = 260; quickTargetX[1] = 340; quickTargetX[3] = 420;
                    quickCursor = 0; // select 12
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = true;
                    pivotMode = false;
                } else if (step == 6) {
                    // 12 shifted to index 2 (no actual movement visual).
                    // Scan 18 (element 1) at index 3.
                    quickTargetX[2] = 100; quickTargetX[4] = 180; quickTargetX[0] = 260; quickTargetX[1] = 340; quickTargetX[3] = 420;
                    quickCursor = 1; // scanning 18
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = true;
                    pivotMode = false;
                } else if (step == 7) {
                    // Select Pivot 15 (element 3) at index 4.
                    quickTargetX[2] = 100; quickTargetX[4] = 180; quickTargetX[0] = 260; quickTargetX[1] = 340; quickTargetX[3] = 420;
                    quickCursor = 3; // select Pivot 15
                    quickGreen[0] = false; quickGreen[1] = false; quickGreen[2] = false; quickGreen[3] = false; quickGreen[4] = true;
                    pivotMode = true;
                } else {
                    // Pivot 15 shifted to index 3 (turns green).
                    // Remaining partitions [2, 2] and [4, 4] are size 1 and finalized (green).
                    // Final order: 5, 10, 12, 15, 18
                    // Elements: 5 (idx 2), 10 (idx 4), 12 (idx 0), 15 (idx 3), 18 (idx 1)
                    quickTargetX[2] = 100; quickTargetX[4] = 180; quickTargetX[0] = 260; quickTargetX[3] = 340; quickTargetX[1] = 420;
                    quickCursor = -1; // hide cursor
                    quickGreen[2] = true; quickGreen[4] = true; quickGreen[0] = true; quickGreen[3] = true; quickGreen[1] = true;
                    pivotMode = false;
                }
                
                for (int i = 0; i < 5; i++) {
                    quickX[i] += (quickTargetX[i] - quickX[i]) * 0.15;
                }
                
                // Draw phase title
                gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGB));
                if (step == 0) {
                    gc.fillText("PHASE 1: COMPARE SCANNING ELEMENTS WITH PIVOT (10)", 15, 25);
                } else if (step == 1) {
                    gc.fillText("PHASE 2: COMPARE SCANNING ELEMENTS WITH PIVOT (10)", 15, 25);
                } else if (step == 2) {
                    gc.fillText("PHASE 3: ELEMENT 5 <= PIVOT! PRESS ENTER TO SHIFT TO TARGET SLOT 0", 15, 25);
                } else if (step == 3) {
                    gc.fillText("PHASE 4: COMPARE SCANNING ELEMENTS WITH PIVOT (10)", 15, 25);
                } else if (step == 4) {
                    gc.fillText("PHASE 5: SHIFT PIVOT TO FINAL PARTITION TARGET SLOT 1", 15, 25);
                } else if (step == 5) {
                    gc.fillText("PHASE 6: SUB-PARTITION RANGE [2, 4] WITH PIVOT (15) | SCAN ELEMENT 12 <= 15", 15, 25);
                } else if (step == 6) {
                    gc.fillText("PHASE 7: COMPARE SCANNING ELEMENTS WITH PIVOT (15)", 15, 25);
                } else if (step == 7) {
                    gc.fillText("PHASE 8: SHIFT PIVOT TO FINAL PARTITION TARGET SLOT 3", 15, 25);
                } else {
                    gc.fillText("PHASE 9: SORTING COMPLETE! ALL BLOCK PARTITIONS FINALIZED", 15, 25);
                }

                // Draw active subarray dashed frame
                double startX = 100;
                double boxWidth = 80;
                double boxHeight = 65;
                double startY = 60;
                
                int pLeft = (step < 5) ? 0 : 2;
                int pRight = 4;
                int targetIndex = -1;
                if (step < 5) {
                    targetIndex = (step < 3) ? 0 : 1;
                } else if (step < 8) {
                    targetIndex = (step < 7) ? 2 : 3;
                }
                
                if (step < 8) {
                    // Draw dashed range
                    double rx1 = startX + pLeft * boxWidth + 2;
                    double rx2 = startX + (pRight + 1) * boxWidth - 2;
                    gc.setStroke(isGB ? theme.textMuted.deriveColor(0,1,1,0.6) : Color.web("#475569", 0.6));
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(rx1, startY - 10, rx2 - rx1, boxHeight + 20, 6, 6);
                    gc.setLineDashes(null);
                    
                    gc.setFill(isGB ? theme.textMuted.deriveColor(0,1,1,0.7) : Color.web("#94a3b8", 0.7));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText(step < 5 ? "ACTIVE PARTITION RANGE [0, 4]" : "SUB-PARTITION RANGE [2, 4]", rx1 + 8, startY - 14);

                    // Draw Target Slot Arrow
                    double arrowX = startX + targetIndex * boxWidth + boxWidth / 2.0;
                    double arrowYHead = startY - 4;
                    double arrowYTail = startY - 18;
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(arrowX, arrowYTail, arrowX, arrowYHead);
                    gc.strokeLine(arrowX - 3, arrowYHead - 3, arrowX, arrowYHead);
                    gc.strokeLine(arrowX + 3, arrowYHead - 3, arrowX, arrowYHead);
                    
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("TARGET SLOT", arrowX - 22, arrowYTail - 4);
                }
                
                for (int i = 0; i < 5; i++) {
                    double x = quickX[i];
                    double y = 60;
                    double w = 65;
                    double h = 65;
                    
                    boolean isSorted = quickGreen[i];
                    boolean isPivot = (step < 5 && i == 4) || (step >= 5 && step < 8 && i == 3);
                    
                    Color col = isSorted ? (isGB ? theme.sorted : Color.web("#10b981")) : (isPivot ? (isGB ? theme.accent : Color.web("#f59e0b")) : (isGB ? theme.unsorted : Color.web("#334155")));
                    
                    gc.setFill(col);
                    gc.fillRoundRect(x, y, w, h, 6, 6);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, w, h, 6, 6);
                    
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                    gc.fillText(String.valueOf((int)quickVals[i]), x + 22, y + 38);
                    
                    if (isPivot && !isSorted) {
                        double badgeW = 38;
                        double badgeX = x + (w - badgeW) / 2.0;
                        gc.setFill(isGB ? theme.accent : Color.web("#ea580c"));
                        gc.fillRoundRect(badgeX, y - 14, badgeW, 11, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 7, isGB));
                        gc.fillText("PIVOT", badgeX + 6, y - 6);
                    }
                }
                
                if (quickCursor != -1) {
                    double cursorX = quickX[quickCursor];
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(cursorX - 2, 60 - 2, 69, 69, 6, 6);
                    
                    // Draw comparison operator between cursor and pivot
                    int pivotTutIdx = (step < 5) ? 4 : ((step < 8) ? 3 : -1);
                    if (pivotTutIdx != -1 && quickCursor != pivotTutIdx && !quickGreen[quickCursor]) {
                        double cxMid = quickX[quickCursor] + 32.5;
                        double pxMid = quickX[pivotTutIdx] + 32.5;
                        double opMidX = (cxMid + pxMid) / 2.0;
                        double opMidY = 40;
                        
                        int valC = (int) quickVals[quickCursor];
                        int valP = (int) quickVals[pivotTutIdx];
                        String op = valC < valP ? "<" : (valC > valP ? ">" : "=");
                        
                        gc.setFill(isGB ? theme.bg : Color.web("#0b0f19"));
                        gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                        gc.setLineWidth(1.5);
                        gc.fillOval(opMidX - 12, opMidY - 12, 24, 24);
                        gc.strokeOval(opMidX - 12, opMidY - 12, 24, 24);
                        
                        gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                        gc.fillText(op, opMidX - 4.5, opMidY + 5);
                    }
                }
                
                // Draw keyboard simulation (y = 145)
                boolean dPressed = (step == 0 || step == 1 || step == 3 || step == 6);
                boolean enterPressed = (step == 2 || step == 4 || step == 5 || step == 7);
                
                drawKeyCap(gc, 200, 145, "A", false);
                drawKeyCap(gc, 250, 145, "D", dPressed);
                drawKeyCap(gc, 320, 145, "ENTER", enterPressed);

                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGB));
                if (step == 0) {
                    gc.fillText("Scanning elements: 12 > 10. No shift. Pressing [D]...", 170, 195);
                } else if (step == 1) {
                    gc.fillText("Scanning elements: 18 > 10. No shift. Pressing [D]...", 170, 195);
                } else if (step == 2) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("5 <= Pivot (10)! Pressing [ENTER] to shift to target index 0...", 130, 195);
                } else if (step == 3) {
                    gc.fillText("Scanning elements: 15 > 10. No shift. Pressing [D]...", 170, 195);
                } else if (step == 4) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Partition scan complete. Pressing [ENTER] to shift Pivot to index 1...", 120, 195);
                } else if (step == 5) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("New partition [2, 4] with Pivot 15. 12 <= 15! Pressing [ENTER]...", 125, 195);
                } else if (step == 6) {
                    gc.fillText("Scanning elements: 18 > 15. No shift. Pressing [D]...", 170, 195);
                } else if (step == 7) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Partition scan complete. Pressing [ENTER] to shift Pivot to index 3...", 120, 195);
                } else {
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillText("Sorting complete! All partitions sorted and finalized.", 170, 195);
                }
                
                drawLegend(gc, "Quick Sort", 10, 235);
            }
            
            private void updateAndDrawMerge(GraphicsContext gc, int step) {
                if (step == 0) {
                    mergeSub2Merged[0] = false; mergeSub1Merged[0] = false; mergeSub2Merged[1] = false; mergeSub1Merged[1] = false;
                    mergeOutGreen[0] = false; mergeOutGreen[1] = false; mergeOutGreen[2] = false; mergeOutGreen[3] = false;
                } else if (step == 1) {
                    mergeSub2Merged[0] = true; mergeSub1Merged[0] = false; mergeSub2Merged[1] = false; mergeSub1Merged[1] = false;
                    mergeOutGreen[0] = false; mergeOutGreen[1] = false; mergeOutGreen[2] = false; mergeOutGreen[3] = false;
                } else if (step == 2) {
                    mergeSub2Merged[0] = true; mergeSub1Merged[0] = true; mergeSub2Merged[1] = false; mergeSub1Merged[1] = false;
                    mergeOutGreen[0] = false; mergeOutGreen[1] = false; mergeOutGreen[2] = false; mergeOutGreen[3] = false;
                } else if (step == 3) {
                    mergeSub2Merged[0] = true; mergeSub1Merged[0] = true; mergeSub2Merged[1] = true; mergeSub1Merged[1] = false;
                    mergeOutGreen[0] = false; mergeOutGreen[1] = false; mergeOutGreen[2] = false; mergeOutGreen[3] = false;
                } else {
                    mergeSub2Merged[0] = true; mergeSub1Merged[0] = true; mergeSub2Merged[1] = true; mergeSub1Merged[1] = true;
                    mergeOutGreen[0] = true; mergeOutGreen[1] = true; mergeOutGreen[2] = true; mergeOutGreen[3] = true;
                }
                
                for (int i = 0; i < 2; i++) {
                    mergeSub1X[i] += ( (mergeSub1Merged[i] ? (180 + (i==0?80:240)) : (120 + i*80)) - mergeSub1X[i] ) * 0.15;
                    mergeSub1Y[i] += ( (mergeSub1Merged[i] ? 130 : 50) - mergeSub1Y[i] ) * 0.15;
                    
                    mergeSub2X[i] += ( (mergeSub2Merged[i] ? (180 + (i==0?0:160)) : (340 + i*80)) - mergeSub2X[i] ) * 0.15;
                    mergeSub2Y[i] += ( (mergeSub2Merged[i] ? 130 : 50) - mergeSub2Y[i] ) * 0.15;
                }
                
                // Draw phase title
                gc.setFill(isGB ? theme.accent : Color.web("#3b82f6"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGB));
                if (step == 0) {
                    gc.fillText("PHASE 1: COMPARE HEADS A (5) & B (3) -> CHOOSE SMALLER (3)", 15, 25);
                } else if (step == 1) {
                    gc.fillText("PHASE 2: COMPARE HEADS A (5) & B (10) -> CHOOSE SMALLER (5)", 15, 25);
                } else if (step == 2) {
                    gc.fillText("PHASE 3: COMPARE HEADS A (15) & B (10) -> CHOOSE SMALLER (10)", 15, 25);
                } else if (step == 3) {
                    gc.fillText("PHASE 4: SUBARRAY B EMPTY -> CHOOSE REMAINING HEAD A (15)", 15, 25);
                } else {
                    gc.fillText("PHASE 5: SUBARRAYS SUCCESSFULLY MERGED AND LOCKED (GREEN)", 15, 25);
                }

                // Draw active subarray dashed frames
                gc.setStroke(isGB ? theme.textMuted.deriveColor(0,1,1,0.6) : Color.web("#475569", 0.6));
                gc.setLineWidth(1.2);
                gc.setLineDashes(new double[]{4.0, 3.0});
                
                // Subarray A
                gc.strokeRoundRect(110, 42, 150, 60, 4, 4);
                // Subarray B
                gc.strokeRoundRect(330, 42, 150, 60, 4, 4);
                // Output Area
                gc.strokeRoundRect(170, 122, 250, 60, 4, 4);
                
                gc.setLineDashes(null);
                
                gc.setFill(isGB ? theme.textMuted.deriveColor(0,1,1,0.7) : Color.web("#94a3b8", 0.7));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                gc.fillText("SUBARRAY A (SORTED)", 115, 36);
                gc.fillText("SUBARRAY B (SORTED)", 335, 36);
                gc.fillText("MERGED OUTPUT SLOT", 175, 116);

                // Draw Target Slot Arrow
                int targetIndex = step;
                if (step == 4) targetIndex = -1;
                
                if (targetIndex != -1) {
                    double arrowX = 180 + targetIndex * 80 + 25;
                    double arrowYHead = 120;
                    double arrowYTail = 106;
                    gc.setStroke(isGB ? theme.sorted : Color.web("#06b6d4"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(arrowX, arrowYTail, arrowX, arrowYHead);
                    gc.strokeLine(arrowX - 3, arrowYHead - 3, arrowX, arrowYHead);
                    gc.strokeLine(arrowX + 3, arrowYHead - 3, arrowX, arrowYHead);
                }

                // Draw elements of Subarray A
                for (int i = 0; i < 2; i++) {
                    double x = mergeSub1X[i];
                    double y = mergeSub1Y[i];
                    boolean merged = mergeSub1Merged[i];
                    Color col = (merged && mergeOutGreen[i==0?1:3]) ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.unsorted : Color.web("#334155"));
                    
                    gc.setFill(col);
                    gc.fillRoundRect(x, y, 50, 45, 4, 4);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, 50, 45, 4, 4);
                    
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText(String.valueOf((int)mergeSub1[i]), x + 16, y + 26);

                    // HEAD A Badge
                    if (i == 0 && !mergeSub1Merged[0] && step < 2) {
                        gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                        gc.fillRoundRect(x + 5, y - 12, 40, 10, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 6, isGB));
                        gc.fillText("HEAD A", x + 9, y - 5);
                    } else if (i == 1 && !mergeSub1Merged[1] && mergeSub1Merged[0] && step >= 2 && step < 4) {
                        gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                        gc.fillRoundRect(x + 5, y - 12, 40, 10, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 6, isGB));
                        gc.fillText("HEAD A", x + 9, y - 5);
                    }
                }
                
                // Draw elements of Subarray B
                for (int i = 0; i < 2; i++) {
                    double x = mergeSub2X[i];
                    double y = mergeSub2Y[i];
                    boolean merged = mergeSub2Merged[i];
                    Color col = (merged && mergeOutGreen[i==0?0:2]) ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.unsorted : Color.web("#334155"));
                    
                    gc.setFill(col);
                    gc.fillRoundRect(x, y, 50, 45, 4, 4);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, 50, 45, 4, 4);
                    
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText(String.valueOf((int)mergeSub2[i]), x + 16, y + 26);

                    // HEAD B Badge
                    if (i == 0 && !mergeSub2Merged[0] && step < 1) {
                        gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                        gc.fillRoundRect(x + 5, y - 12, 40, 10, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 6, isGB));
                        gc.fillText("HEAD B", x + 9, y - 5);
                    } else if (i == 1 && !mergeSub2Merged[1] && mergeSub2Merged[0] && step >= 1 && step < 3) {
                        gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                        gc.fillRoundRect(x + 5, y - 12, 40, 10, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 6, isGB));
                        gc.fillText("HEAD B", x + 9, y - 5);
                    }
                }
                
                
                double cursorX = 0;
                double cursorY = 50;
                boolean cursorVisible = true;
                
                if (step == 0) {
                    cursorX = mergeSub2X[0];
                } else if (step == 1) {
                    cursorX = mergeSub1X[0];
                } else if (step == 2) {
                    cursorX = mergeSub2X[1];
                } else if (step == 3) {
                    cursorX = mergeSub1X[1];
                } else {
                    cursorVisible = false;
                }
                
                if (cursorVisible) {
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(cursorX - 1, cursorY - 1, 52, 47, 4, 4);
                }
                
                // Draw keyboard simulation (y = 200)
                boolean enterPressed = (step < 4);
                
                drawKeyCap(gc, 200, 200, "A", false);
                drawKeyCap(gc, 250, 200, "D", false);
                drawKeyCap(gc, 320, 200, "ENTER", enterPressed);

                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGB));
                if (step == 0) {
                    gc.fillText("Comparing subarray heads: 5 vs 3. Select smaller head (3)...", 130, 190);
                } else if (step == 1) {
                    gc.fillText("Comparing subarray heads: 5 vs 10. Select smaller head (5)...", 130, 190);
                } else if (step == 2) {
                    gc.fillText("Comparing subarray heads: 15 vs 10. Select smaller head (10)...", 125, 190);
                } else if (step == 3) {
                    gc.fillText("Subarray B is empty. Select remaining head (15)...", 150, 190);
                } else {
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillText("Sub-arrays successfully merged! Output finalizes green.", 160, 190);
                }
                
                drawLegend(gc, "Merge Sort", 10, 235);
            }

            private void updateAndDrawBubble(GraphicsContext gc, int step) {
                if (step == 0) {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 180; bubbleTargetX[2] = 260; bubbleTargetX[3] = 340; bubbleTargetX[4] = 420;
                    bubbleCursor = 0;
                    bubbleGreen[0] = false; bubbleGreen[1] = false; bubbleGreen[2] = false; bubbleGreen[3] = false; bubbleGreen[4] = false;
                } else if (step == 1) {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 180; bubbleTargetX[2] = 260; bubbleTargetX[3] = 340; bubbleTargetX[4] = 420;
                    bubbleCursor = 1;
                    bubbleGreen[0] = false; bubbleGreen[1] = false; bubbleGreen[2] = false; bubbleGreen[3] = false; bubbleGreen[4] = false;
                } else if (step == 2) {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 260; bubbleTargetX[2] = 180; bubbleTargetX[3] = 340; bubbleTargetX[4] = 420;
                    bubbleCursor = 2;
                    bubbleGreen[0] = false; bubbleGreen[1] = false; bubbleGreen[2] = false; bubbleGreen[3] = false; bubbleGreen[4] = false;
                } else if (step == 3) {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 340; bubbleTargetX[2] = 180; bubbleTargetX[3] = 260; bubbleTargetX[4] = 420;
                    bubbleCursor = 3;
                    bubbleGreen[0] = false; bubbleGreen[1] = false; bubbleGreen[2] = false; bubbleGreen[3] = false; bubbleGreen[4] = false;
                } else if (step == 4) {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 420; bubbleTargetX[2] = 180; bubbleTargetX[3] = 260; bubbleTargetX[4] = 340;
                    bubbleCursor = 4;
                    bubbleGreen[1] = true; // element 15 is sorted
                } else {
                    bubbleTargetX[0] = 100; bubbleTargetX[1] = 420; bubbleTargetX[2] = 180; bubbleTargetX[3] = 260; bubbleTargetX[4] = 340;
                    bubbleCursor = -1;
                    bubbleGreen[1] = true;
                }

                for (int i = 0; i < 5; i++) {
                    bubbleX[i] += (bubbleTargetX[i] - bubbleX[i]) * 0.15;
                }

                gc.setFill(isGB ? theme.accent : Color.web("#ec4899"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGB));
                if (step == 0) {
                    gc.fillText("STEP 1: COMPARE 8 AND 15. SINCE 8 <= 15, THEY ARE IN ORDER. PRESS [D] TO STEP FORWARD.", 15, 25);
                } else if (step == 1) {
                    gc.fillText("STEP 2: COMPARE 15 AND 5. SINCE 15 > 5, THEY ARE OUT OF ORDER. PRESS [ENTER] TO SWAP.", 15, 25);
                } else if (step == 2) {
                    gc.fillText("STEP 3: COMPARE 15 AND 12. SINCE 15 > 12, THEY ARE OUT OF ORDER. PRESS [ENTER] TO SWAP.", 15, 25);
                } else if (step == 3) {
                    gc.fillText("STEP 4: COMPARE 15 AND 10. SINCE 15 > 10, THEY ARE OUT OF ORDER. PRESS [ENTER] TO SWAP.", 15, 25);
                } else if (step == 4) {
                    gc.fillText("STEP 5: ELEMENT 15 REACHED THE END AND IS LOCKED IN GREEN (SORTED).", 15, 25);
                } else {
                    gc.fillText("STEP 6: FIRST PASS COMPLETE. REPEAT UNTIL ALL BLOCKS ARE SORTED.", 15, 25);
                }

                double startX = 100;
                double boxWidth = 80;
                double boxHeight = 65;
                double startY = 60;

                if (bubbleCursor >= 0 && bubbleCursor < 4) {
                    double rx1 = startX + bubbleCursor * boxWidth - 2;
                    double rx2 = rx1 + boxWidth * 2 - 12;
                    gc.setStroke(isGB ? theme.accent.deriveColor(0,1,1,0.8) : Color.web("#ec4899", 0.8));
                    gc.setLineWidth(2.0);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(rx1, startY - 8, rx2 - rx1, boxHeight + 16, 6, 6);
                    gc.setLineDashes(null);
                    
                    gc.setFill(isGB ? theme.accent : Color.web("#ec4899"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("COMPARE PAIR", rx1 + 6, startY - 12);
                }

                for (int i = 0; i < 5; i++) {
                    double x = bubbleX[i];
                    double y = 60;
                    double w = 65;
                    double h = 65;

                    boolean isSorted = bubbleGreen[i];
                    Color col = isSorted ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.unsorted : Color.web("#334155"));

                    gc.setFill(col);
                    gc.fillRoundRect(x, y, w, h, 6, 6);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, w, h, 6, 6);

                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                    gc.fillText(String.valueOf((int)bubbleVals[i]), x + 22, y + 38);
                }

                if (bubbleCursor >= 0 && bubbleCursor < 4) {
                    double cursorX = startX + bubbleCursor * boxWidth;
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(cursorX - 2, 60 - 2, boxWidth * 2 - 11, 69, 6, 6);
                }

                boolean dPressed = (step == 0);
                boolean enterPressed = (step == 1 || step == 2 || step == 3);

                drawKeyCap(gc, 200, 145, "A", false);
                drawKeyCap(gc, 250, 145, "D", dPressed);
                drawKeyCap(gc, 320, 145, "ENTER", enterPressed);

                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGB));
                if (step == 0) {
                    gc.fillText("Adjacent values 8 and 15 are sorted. Press [D] to advance cursor.", 150, 195);
                } else if (step == 1) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("15 > 5 is out of order. Press [ENTER] to swap elements.", 175, 195);
                } else if (step == 2) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("15 > 12 is out of order. Press [ENTER] to swap elements.", 175, 195);
                } else if (step == 3) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("15 > 10 is out of order. Press [ENTER] to swap elements.", 175, 195);
                } else if (step == 4) {
                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillText("15 bubbled to its final position! Locking element in green.", 160, 195);
                } else {
                    gc.fillText("First pass complete. Press [START GAME] to play!", 195, 195);
                }

                drawLegend(gc, "Bubble Sort", 10, 235);
            }

            private void updateAndDrawInsertion(GraphicsContext gc, int step) {
                if (step == 0) {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 180; insertionTargetX[2] = 260; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = 2; // highlight 8
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = false; insertionGreen[3] = false; insertionGreen[4] = false;
                } else if (step == 1) {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 180; insertionTargetX[2] = 260; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = 2;
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = false; insertionGreen[3] = false; insertionGreen[4] = false;
                } else if (step == 2) {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 260; insertionTargetX[2] = 180; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = 1; // 8 is now at index 1
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = false; insertionGreen[3] = false; insertionGreen[4] = false;
                } else if (step == 3) {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 260; insertionTargetX[2] = 180; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = 1;
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = true; insertionGreen[3] = false; insertionGreen[4] = false;
                } else if (step == 4) {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 260; insertionTargetX[2] = 180; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = 3; // active is now 15
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = true; insertionGreen[3] = true; insertionGreen[4] = false;
                } else {
                    insertionTargetX[0] = 100; insertionTargetX[1] = 260; insertionTargetX[2] = 180; insertionTargetX[3] = 340; insertionTargetX[4] = 420;
                    insertionCursor = -1;
                    insertionGreen[0] = true; insertionGreen[1] = true; insertionGreen[2] = true; insertionGreen[3] = true; insertionGreen[4] = true;
                }

                for (int i = 0; i < 5; i++) {
                    insertionX[i] += (insertionTargetX[i] - insertionX[i]) * 0.15;
                }

                gc.setFill(isGB ? theme.accent : Color.web("#8b5cf6"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 12, isGB));
                if (step == 0) {
                    gc.fillText("STEP 1: SORTED SUB-LIST IS [5, 12]. ACTIVE ELEMENT TO INSERT IS 8.", 15, 25);
                } else if (step == 1) {
                    gc.fillText("STEP 2: COMPARE 8 WITH LEFT NEIGHBOR 12. SINCE 8 < 12, PRESS [ENTER] TO SWAP LEFT.", 15, 25);
                } else if (step == 2) {
                    gc.fillText("STEP 3: COMPARE 8 WITH NEW LEFT NEIGHBOR 5. SINCE 8 >= 5, IT IS IN POSITION.", 15, 25);
                } else if (step == 3) {
                    gc.fillText("STEP 4: INSERTION POSITION FOUND. PRESS [D] TO FINALIZE AND ADVANCE CURSOR.", 15, 25);
                } else if (step == 4) {
                    gc.fillText("STEP 5: PREFIX [5, 8, 12] IS NOW SORTED. ACTIVE ELEMENT IS NOW 15.", 15, 25);
                } else {
                    gc.fillText("STEP 6: REPEAT FOR ALL ELEMENTS UNTIL THE ENTIRE LIST IS INSERTED.", 15, 25);
                }

                double startX = 100;
                double boxWidth = 80;
                double boxHeight = 65;
                double startY = 60;

                int sortedCount = 2;
                if (step == 3 || step == 4) sortedCount = 3;
                if (step >= 5) sortedCount = 5;

                double rx1 = startX + 2;
                double rx2 = startX + sortedCount * boxWidth - 12;
                gc.setStroke(isGB ? theme.accent.deriveColor(0,1,1,0.6) : Color.web("#8b5cf6", 0.6));
                gc.setLineWidth(1.5);
                gc.setLineDashes(new double[]{4.0, 3.0});
                gc.strokeRoundRect(rx1, startY - 10, rx2 - rx1, boxHeight + 20, 6, 6);
                gc.setLineDashes(null);
                
                gc.setFill(isGB ? theme.accent : Color.web("#8b5cf6"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                gc.fillText("SORTED SUB-LIST", rx1 + 6, startY - 14);

                if (step == 1) {
                    double arrowX = startX + boxWidth + boxWidth / 2.0;
                    double arrowYHead = startY - 4;
                    double arrowYTail = startY - 18;
                    gc.setStroke(isGB ? theme.accent : Color.web("#8b5cf6"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(arrowX, arrowYTail, arrowX, arrowYHead);
                    gc.strokeLine(arrowX - 3, arrowYHead - 3, arrowX, arrowYHead);
                    gc.strokeLine(arrowX + 3, arrowYHead - 3, arrowX, arrowYHead);
                    
                    gc.fillText("INSERT HERE", arrowX - 24, arrowYTail - 4);
                }

                for (int i = 0; i < 5; i++) {
                    double x = insertionX[i];
                    double y = 60;
                    double w = 65;
                    double h = 65;

                    boolean isSorted = insertionGreen[i];
                    Color col = isSorted ? (isGB ? theme.sorted : Color.web("#10b981")) : (isGB ? theme.unsorted : Color.web("#334155"));

                    if (i == 2 && !isSorted) {
                        col = isGB ? theme.accent : Color.web("#8b5cf6");
                    }

                    gc.setFill(col);
                    gc.fillRoundRect(x, y, w, h, 6, 6);
                    gc.setStroke(col.deriveColor(0, 1, 1.2, 1));
                    gc.strokeRoundRect(x, y, w, h, 6, 6);

                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                    gc.fillText(String.valueOf((int)insertionVals[i]), x + 22, y + 38);
                }

                if (insertionCursor != -1) {
                    double cursorX = insertionX[insertionCursor];
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(cursorX - 2, 60 - 2, 69, 69, 6, 6);
                }

                boolean dPressed = (step == 0 || step == 3);
                boolean enterPressed = (step == 1);

                drawKeyCap(gc, 200, 145, "A", false);
                drawKeyCap(gc, 250, 145, "D", dPressed);
                drawKeyCap(gc, 320, 145, "ENTER", enterPressed);

                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 13, isGB));
                if (step == 0) {
                    gc.fillText("Press [D] to examine the active element 8 to insert.", 175, 195);
                } else if (step == 1) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("8 < 12! Neighbor is larger. Press [ENTER] to shift 8 left.", 155, 195);
                } else if (step == 2) {
                    gc.fillText("8 >= 5. Left neighbor is smaller or equal. Insertion position found.", 130, 195);
                } else if (step == 3) {
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.fillText("Press [D] to lock element 8 into sorted sub-list.", 190, 195);
                } else if (step == 4) {
                    gc.fillText("Active element is now 15. It is larger than 12, no shift needed.", 145, 195);
                } else {
                    gc.fillText("Insertion sort preview complete! Press [START GAME] to play.", 160, 195);
                }

                drawLegend(gc, "Insertion Sort", 10, 235);
            }

            private void drawDetailedShortcutsPage(GraphicsContext gc, String algo) {
                gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                gc.fillText("KEYBOARD CONTROLS REFERENCE MAP", 15, 30);

                double y = 80;
                if (algo.equalsIgnoreCase("Selection Sort") || algo.equalsIgnoreCase("Quick Sort") || algo.equalsIgnoreCase("Merge Sort")) {
                    drawKeyCap(gc, 80, y, "A", false);
                    drawKeyCap(gc, 140, y, "D", false);
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                    gc.fillText("A / D: Move Selection Cursor Left/Right", 220, y + 16);
                } else if (algo.equalsIgnoreCase("Bubble Sort")) {
                    drawKeyCap(gc, 110, y, "D", false);
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                    gc.fillText("D: Step Forward / Skip Swap (elements in order)", 180, y + 16);
                } else { // Insertion Sort
                    drawKeyCap(gc, 110, y, "D", false);
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                    gc.fillText("D: Finalize Position & Advance (element is in position)", 180, y + 16);
                }

                drawKeyCap(gc, 80, y + 40, "ENTER", false);
                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                if (algo.equalsIgnoreCase("Selection Sort")) {
                    gc.fillText("ENTER: Confirm & Shift Minimum element to Target Slot", 180, y + 56);
                } else if (algo.equalsIgnoreCase("Quick Sort")) {
                    gc.fillText("ENTER: Shift element <= Pivot to Target, or Shift Pivot", 180, y + 56);
                } else if (algo.equalsIgnoreCase("Bubble Sort")) {
                    gc.fillText("ENTER: Swap out-of-order adjacent elements (Left > Right)", 180, y + 56);
                } else if (algo.equalsIgnoreCase("Insertion Sort")) {
                    gc.fillText("ENTER: Swap active element leftward (if smaller than left)", 180, y + 56);
                } else { // Merge
                    gc.fillText("ENTER: Shift selected Subarray Head down to Output Slot", 180, y + 56);
                }

                drawKeyCap(gc, 80, y + 80, "R", false);
                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                gc.fillText("R: Restart Current Puzzle Wave immediately (Resets progress)", 140, y + 96);

                drawKeyCap(gc, 80, y + 120, "ESC", false);
                gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 12, isGB));
                gc.fillText("ESC: Pause / Return to Main Menu", 150, y + 136);

                drawLegend(gc, algo, 10, 235);
            }

            private void drawSelectionDetailed(GraphicsContext gc, int page) {
                if (page == 0) { // Page 1: Core Logic
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("SELECTION SORT: SPLIT-ARRAY LOGIC", 15, 30);

                    double y = 80;
                    double w = 65;
                    double h = 65;
                    
                    double[] vals = {3, 5, 15, 8, 22};
                    for (int i = 0; i < 2; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }
                    
                    for (int i = 2; i < 5; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }

                    gc.setStroke(isGB ? theme.textMuted : Color.web("#475569"));
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(255, y - 10, 245, h + 20, 6, 6);
                    gc.setLineDashes(null);

                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("SORTED REGION (LOCKED)", 105, y - 15);
                    gc.fillText("UNSORTED REGION (SEARCH)", 265, y - 15);

                    double arrowX = 100 + 2 * 80 + w / 2.0;
                    gc.setStroke(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(arrowX, y - 8, arrowX, y - 4);
                    gc.strokeLine(arrowX - 3, y - 4, arrowX, y - 4);
                    gc.strokeLine(arrowX + 3, y - 4, arrowX, y - 4);

                    gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                    gc.fillText("TARGET SLOT", arrowX - 25, y - 28);

                    double minX = 100 + 3 * 80;
                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.5);
                    gc.strokeRoundRect(minX - 2, y - 2, w + 4, h + 4, 6, 6);
                    
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("MINIMUM VALUE FOUND", minX - 15, y + 80);

                    drawLegend(gc, "Selection Sort", 10, 235);
                } else if (page == 1) { // Page 2: Sorting Process - Pass 1
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("SELECTION SORT: PROCESS (PASS 1)", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color minCol = isGB ? theme.accent : Color.web("#f59e0b");
                    Color targetCol = isGB ? theme.textMuted : Color.web("#06b6d4");

                    double[] vals1 = {15, 8, 22, 5, 12};
                    Color[] cols1 = {unsortedCol, unsortedCol, unsortedCol, minCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, 0, targetCol, "1. Locate Absolute Minimum (5) & Swap with Target (15)");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 3 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 0 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, isGB ? theme.accent : Color.web("#f59e0b"));

                    double[] vals2 = {5, 8, 22, 15, 12};
                    Color[] cols2 = {sortedCol, unsortedCol, unsortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, 1, targetCol, "Result: 5 is Locked. Target Slot Advances to Index 1");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("PASS 1 DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Range scanned: index 0 to 4.", 420, textY);
                    gc.fillText("- Min found: 5 at index 3.", 420, textY + spacingY);
                    gc.fillText("- Swap with target index 0 (15).", 420, textY + 2 * spacingY);
                    gc.fillText("- Index 0 is now sorted (Green).", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Selection Sort", 10, 235);
                } else if (page == 2) { // Page 3: Sorting Process - Pass 2
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("SELECTION SORT: PROCESS (PASS 2)", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color minCol = isGB ? theme.accent : Color.web("#f59e0b");
                    Color targetCol = isGB ? theme.textMuted : Color.web("#06b6d4");

                    double[] vals1 = {5, 15, 8, 22, 12};
                    Color[] cols1 = {sortedCol, unsortedCol, minCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, 1, targetCol, "2. Scan Unsorted Suffix: Find Minimum (8) & Swap with Target (15)");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 2 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 1 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, isGB ? theme.accent : Color.web("#f59e0b"));

                    double[] vals2 = {5, 8, 15, 22, 12};
                    Color[] cols2 = {sortedCol, sortedCol, unsortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, 2, targetCol, "Result: 8 is Locked. Target Slot Advances to Index 2");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("PASS 2 DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Range scanned: index 1 to 4.", 420, textY);
                    gc.fillText("- Min found: 8 at index 2.", 420, textY + spacingY);
                    gc.fillText("- Swap with target index 1 (15).", 420, textY + 2 * spacingY);
                    gc.fillText("- Prefix [5, 8] is sorted.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Selection Sort", 10, 235);
                } else if (page == 3) { // Page 4: Sorting Process - Pass 3
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("SELECTION SORT: PROCESS (PASS 3)", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color minCol = isGB ? theme.accent : Color.web("#f59e0b");
                    Color targetCol = isGB ? theme.textMuted : Color.web("#06b6d4");

                    double[] vals1 = {5, 8, 15, 22, 12};
                    Color[] cols1 = {sortedCol, sortedCol, unsortedCol, unsortedCol, minCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, 2, targetCol, "3. Scan Unsorted Suffix: Find Minimum (12) & Swap with Target (15)");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 4 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 2 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, isGB ? theme.accent : Color.web("#f59e0b"));

                    double[] vals2 = {5, 8, 12, 22, 15};
                    Color[] cols2 = {sortedCol, sortedCol, sortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, 3, targetCol, "Result: 12 is Locked. Target Slot Advances to Index 3");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("PASS 3 DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Range scanned: index 2 to 4.", 420, textY);
                    gc.fillText("- Min found: 12 at index 4.", 420, textY + spacingY);
                    gc.fillText("- Swap with target index 2 (15).", 420, textY + 2 * spacingY);
                    gc.fillText("- Prefix [5, 8, 12] is sorted.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Selection Sort", 10, 235);
                } else if (page == 4) {
                    drawDetailedShortcutsPage(gc, "Selection Sort");
                } else {
                    String[][] selectionTable = {
                        {"Best-Case Time", "O(N^2)"},
                        {"Average-Case Time", "O(N^2)"},
                        {"Worst-Case Time", "O(N^2)"},
                        {"Space Complexity", "O(1) Auxiliary"},
                        {"Stability", "Unstable"}
                    };
                    String[] selectionCode = {
                        "for i = 0 to n-1:",
                        "  min_idx = i",
                        "  for j = i+1 to n:",
                        "    if arr[j] < arr[min_idx]:",
                        "      min_idx = j",
                        "  swap(arr[i], arr[min_idx])"
                    };
                    drawComplexityPage(gc, "SELECTION SORT: COMPLEXITY & CODE LOGIC", selectionTable, selectionCode, "Selection Sort");
                }
            }

            private void drawQuickDetailed(GraphicsContext gc, int page) {
                if (page == 0) { // Page 1: Core Logic
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("QUICK SORT: PARTITIONING & PIVOT LOGIC", 15, 30);

                    double y = 80;
                    double w = 65;
                    double h = 65;
                    
                    double[] vals = {5, 8, 10, 18, 15};
                    for (int i = 0; i < 2; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }
                    
                    {
                        double x = 100 + 2 * 80;
                        gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[2]), x + 22, y + 38);

                        double badgeW = 38;
                        double badgeX = x + (w - badgeW) / 2.0;
                        gc.setFill(isGB ? theme.accent : Color.web("#ea580c"));
                        gc.fillRoundRect(badgeX, y - 14, badgeW, 11, 2, 2);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 7, isGB));
                        gc.fillText("PIVOT", badgeX + 6, y - 6);
                    }

                    for (int i = 3; i < 5; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }

                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("ELEMENTS <= PIVOT", 100, y + 80);
                    gc.fillText("PIVOT ELEMENT", 260, y + 80);
                    gc.fillText("ELEMENTS > PIVOT", 420, y + 80);

                    gc.setStroke(isGB ? theme.textMuted : Color.web("#475569"));
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(90, y - 20, 410, h + 110, 6, 6);
                    gc.setLineDashes(null);

                    drawLegend(gc, "Quick Sort", 10, 235);
                } else if (page == 1) { // Page 2: Process - Scan
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("QUICK SORT: PARTITION SCAN PROCESS", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color pivotCol = isGB ? theme.accent : Color.web("#ea580c");
                    Color activeCol = isGB ? theme.accent : Color.web("#3b82f6");
                    Color targetCol = isGB ? theme.textMuted : Color.web("#f59e0b");

                    double[] vals1 = {12, 18, 5, 15, 10};
                    Color[] cols1 = {unsortedCol, unsortedCol, activeCol, unsortedCol, pivotCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, 0, targetCol, "1. Scan & Compare: 5 <= 10 (Pivot) -> Swap to Target Slot (12)");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 2 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 0 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, isGB ? theme.accent : Color.web("#3b82f6"));

                    double[] vals2 = {5, 18, 12, 15, 10};
                    Color[] cols2 = {unsortedCol, unsortedCol, unsortedCol, unsortedCol, pivotCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, 1, targetCol, "Result: 5 is Swapped. Target Slot Boundary Shifts to Index 1");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("SCAN DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Pivot is 10 (Orange).", 420, textY);
                    gc.fillText("- Active cursor scans index 2 (5).", 420, textY + spacingY);
                    gc.fillText("- Since 5 <= 10, swap to target index 0.", 420, textY + 2 * spacingY);
                    gc.fillText("- Target slot advances to index 1.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Quick Sort", 10, 235);
                } else if (page == 2) { // Page 3: Process - Pivot Place
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("QUICK SORT: PIVOT PLACEMENT PROCESS", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color pivotCol = isGB ? theme.accent : Color.web("#ea580c");
                    Color targetCol = isGB ? theme.textMuted : Color.web("#f59e0b");

                    double[] vals1 = {5, 18, 12, 15, 10};
                    Color[] cols1 = {unsortedCol, unsortedCol, unsortedCol, unsortedCol, pivotCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, 1, targetCol, "2. Scan Finished. Swap Pivot (10) to target slot (18)");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 4 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 1 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, isGB ? theme.accent : Color.web("#ea580c"));

                    double[] vals2 = {5, 10, 12, 15, 18};
                    Color[] cols2 = {unsortedCol, sortedCol, unsortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, 2, targetCol, "Result: Pivot (10) is in final position & turns Green");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("PIVOT DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- All elements compared.", 420, textY);
                    gc.fillText("- Swap pivot with target index 1.", 420, textY + spacingY);
                    gc.fillText("- Pivot 10 is now in final position.", 420, textY + 2 * spacingY);
                    gc.fillText("- Subarrays are recursively sorted.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Quick Sort", 10, 235);
                } else if (page == 3) {
                    drawDetailedShortcutsPage(gc, "Quick Sort");
                } else {
                    String[][] quickTable = {
                        {"Best-Case Time", "O(N log N)"},
                        {"Average-Case Time", "O(N log N)"},
                        {"Worst-Case Time", "O(N^2)"},
                        {"Space Complexity", "O(log N) Stack"},
                        {"Stability", "Unstable"}
                    };
                    String[] quickCode = {
                        "function quickSort(arr, low, high):",
                        "  if low < high:",
                        "    pi = partition(arr, low, high)",
                        "    quickSort(arr, low, pi-1)",
                        "    quickSort(arr, pi+1, high)"
                    };
                    drawComplexityPage(gc, "QUICK SORT: COMPLEXITY & CODE LOGIC", quickTable, quickCode, "Quick Sort");
                }
            }

            private void drawBubbleDetailed(GraphicsContext gc, int page) {
                if (page == 0) { // Page 1: Core Logic
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("BUBBLE SORT: THE BUBBLING CONCEPT", 15, 30);

                    double y = 80;
                    double w = 65;
                    double h = 65;
                    
                    double[] vals = {5, 8, 12, 10, 15};
                    for (int i = 0; i < 4; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }
                    
                    {
                        double x = 100 + 4 * 80;
                        gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[4]), x + 22, y + 38);
                    }

                    gc.setStroke(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setLineWidth(2.0);
                    gc.strokeArc(100 + w/2.0, y + h/2.0 - 10, 160, 40, 0, 180, ArcType.OPEN);
                    
                    gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("LARGER VALUE BUBBLES RIGHT", 180, y - 20);

                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.fillText("UNSORTED ELEMENTS", 100, y + 85);
                    gc.fillText("BUBBLED & LOCKED", 420, y + 85);

                    drawLegend(gc, "Bubble Sort", 10, 235);
                } else if (page == 1) { // Page 2: Process - Case A: Skip
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("BUBBLE SORT: PROCESS - CASE A (SKIP)", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color activeBorderCol = isGB ? theme.accent : Color.web("#3b82f6");

                    double[] vals1 = {8, 15, 5, 12, 10};
                    Color[] cols1 = {unsortedCol, unsortedCol, unsortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, -1, null, "1. Compare: 8 <= 15 -> Elements in correct relative order");
                    
                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    gc.setStroke(activeBorderCol);
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(50 - 2, 75 - 2, 2 * blockW + gap + 4, blockW + 4, isGB ? 2 : 6, isGB ? 2 : 6);

                    drawMiniArray(gc, 50, 175, vals1, cols1, -1, null, "Result: Skip Swap by pressing [D]. Frame advances to index 1 and 2");
                    gc.setStroke(activeBorderCol);
                    gc.strokeRoundRect(50 + (blockW + gap) - 2, 175 - 2, 2 * blockW + gap + 4, blockW + 4, isGB ? 2 : 6, isGB ? 2 : 6);

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("CASE A DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Compare elements 8 and 15.", 420, textY);
                    gc.fillText("- Left value <= Right value.", 420, textY + spacingY);
                    gc.fillText("- Correct order. No swap needed.", 420, textY + 2 * spacingY);
                    gc.fillText("- Press [D] to advance cursor frame.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Bubble Sort", 10, 235);
                } else if (page == 2) { // Page 3: Process - Case B: Swap
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("BUBBLE SORT: PROCESS - CASE B (SWAP)", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color activeBorderCol = isGB ? theme.accent : Color.web("#3b82f6");
                    Color swapBorderCol = isGB ? theme.accent : Color.web("#ef4444");

                    double[] vals1 = {8, 15, 5, 12, 10};
                    Color[] cols1 = {unsortedCol, unsortedCol, unsortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, -1, null, "2. Compare: 15 > 5 -> Elements out of order!");
                    
                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    gc.setStroke(swapBorderCol);
                    gc.setLineWidth(2.0);
                    gc.strokeRoundRect(50 + (blockW + gap) - 2, 75 - 2, 2 * blockW + gap + 4, blockW + 4, isGB ? 2 : 6, isGB ? 2 : 6);

                    double startX = 50 + 1 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 2 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, swapBorderCol);

                    double[] vals2 = {8, 5, 15, 12, 10};
                    drawMiniArray(gc, 50, 175, vals2, cols1, -1, null, "Result: Swap elements with [ENTER]. Frame advances to index 2 and 3");
                    gc.setStroke(activeBorderCol);
                    gc.strokeRoundRect(50 + 2 * (blockW + gap) - 2, 175 - 2, 2 * blockW + gap + 4, blockW + 4, isGB ? 2 : 6, isGB ? 2 : 6);

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("CASE B DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Compare elements 15 and 5.", 420, textY);
                    gc.fillText("- Left value > Right value.", 420, textY + spacingY);
                    gc.fillText("- Out of order! Swap required.", 420, textY + 2 * spacingY);
                    gc.fillText("- Press [ENTER] to execute swap.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Bubble Sort", 10, 235);
                } else if (page == 3) {
                    drawDetailedShortcutsPage(gc, "Bubble Sort");
                } else {
                    String[][] bubbleTable = {
                        {"Best-Case Time", "O(N) (Optimized)"},
                        {"Average-Case Time", "O(N^2)"},
                        {"Worst-Case Time", "O(N^2)"},
                        {"Space Complexity", "O(1) Auxiliary"},
                        {"Stability", "Stable"}
                    };
                    String[] bubbleCode = {
                        "for i = 0 to n-1:",
                        "  for j = 0 to n-i-2:",
                        "    if arr[j] > arr[j+1]:",
                        "      swap(arr[j], arr[j+1])"
                    };
                    drawComplexityPage(gc, "BUBBLE SORT: COMPLEXITY & CODE LOGIC", bubbleTable, bubbleCode, "Bubble Sort");
                }
            }

            private void drawInsertionDetailed(GraphicsContext gc, int page) {
                if (page == 0) { // Page 1: Core Logic
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("INSERTION SORT: SORTED SUB-LIST INSERTION", 15, 30);

                    double y = 80;
                    double w = 65;
                    double h = 65;
                    
                    double[] vals = {5, 12, 8, 15, 10};
                    for (int i = 0; i < 2; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.sorted : Color.web("#10b981"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }
                    
                    {
                        double x = 100 + 2 * 80;
                        gc.setFill(isGB ? theme.accent : Color.web("#8b5cf6"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[2]), x + 22, y + 38);
                    }

                    for (int i = 3; i < 5; i++) {
                        double x = 100 + i * 80;
                        gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                        gc.fillRoundRect(x, y, w, h, 6, 6);
                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 18, isGB));
                        gc.fillText(String.valueOf((int)vals[i]), x + 22, y + 38);
                    }

                    gc.setStroke(isGB ? theme.accent : Color.web("#8b5cf6"));
                    gc.setLineWidth(1.5);
                    gc.setLineDashes(new double[]{4.0, 3.0});
                    gc.strokeRoundRect(90, y - 10, 155, h + 20, 6, 6);
                    gc.setLineDashes(null);

                    gc.setFill(isGB ? theme.accent : Color.web("#8b5cf6"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("SORTED SUB-LIST", 100, y - 15);
                    
                    gc.setStroke(isGB ? theme.accent : Color.web("#8b5cf6"));
                    gc.setLineWidth(2.0);
                    gc.strokeLine(260, y + 30, 210, y + 30);
                    gc.strokeLine(210, y + 30, 215, y + 26);
                    gc.strokeLine(210, y + 30, 215, y + 34);

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.fillText("INSERT ACTIVE ELEMENT", 255, y - 15);

                    drawLegend(gc, "Insertion Sort", 10, 235);
                } else if (page == 1) { // Page 2: Process - Shift Left
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("INSERTION SORT: PROCESS - SHIFT LEFT", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color activeCol = isGB ? theme.accent : Color.web("#8b5cf6");

                    double[] vals1 = {5, 12, 8, 15, 10};
                    Color[] cols1 = {sortedCol, sortedCol, activeCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, -1, null, "1. Active (8) < Left (12) -> Swap Leftward with [ENTER]");

                    double blockW = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;
                    double startX = 50 + 2 * (blockW + gap) + blockW/2.0;
                    double endX = 50 + 1 * (blockW + gap) + blockW/2.0;
                    drawSwapArrow(gc, startX, 75, endX, 75, activeCol);

                    double[] vals2 = {5, 8, 12, 15, 10};
                    Color[] cols2 = {sortedCol, activeCol, sortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, -1, null, "Result: Swapped. Active Element is now at index 1");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("SHIFT DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Prefix [5, 12] is sorted.", 420, textY);
                    gc.fillText("- Compare active 8 with left 12.", 420, textY + spacingY);
                    gc.fillText("- Since 8 < 12, press [ENTER] to shift.", 420, textY + 2 * spacingY);
                    gc.fillText("- Array is now [5, 8, 12, 15, 10].", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Insertion Sort", 10, 235);
                } else if (page == 2) { // Page 3: Process - Position Found
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("INSERTION SORT: PROCESS - POSITION FOUND", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color activeCol = isGB ? theme.accent : Color.web("#8b5cf6");

                    double[] vals1 = {5, 8, 12, 15, 10};
                    Color[] cols1 = {sortedCol, activeCol, sortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, vals1, cols1, -1, null, "2. Compare Active (8) with Left Neighbor (5)");

                    double[] vals2 = {5, 8, 12, 15, 10};
                    Color[] cols2 = {sortedCol, sortedCol, sortedCol, unsortedCol, unsortedCol};
                    drawMiniArray(gc, 50, 175, vals2, cols2, -1, null, "Result: 8 >= 5. Insertion slot found. Lock prefix with [D]");

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("LOCKING DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Compare active 8 with left 5.", 420, textY);
                    gc.fillText("- Since 8 >= 5, no more shifts.", 420, textY + spacingY);
                    gc.fillText("- Press [D] to lock prefix green.", 420, textY + 2 * spacingY);
                    gc.fillText("- Prefix [5, 8, 12] becomes green.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Insertion Sort", 10, 235);
                } else if (page == 3) {
                    drawDetailedShortcutsPage(gc, "Insertion Sort");
                } else {
                    String[][] insertionTable = {
                        {"Best-Case Time", "O(N)"},
                        {"Average-Case Time", "O(N^2)"},
                        {"Worst-Case Time", "O(N^2)"},
                        {"Space Complexity", "O(1) Auxiliary"},
                        {"Stability", "Stable"}
                    };
                    String[] insertionCode = {
                        "for i = 1 to n:",
                        "  key = arr[i]",
                        "  j = i - 1",
                        "  while j >= 0 and arr[j] > key:",
                        "    arr[j+1] = arr[j]",
                        "    j--",
                        "  arr[j+1] = key"
                    };
                    drawComplexityPage(gc, "INSERTION SORT: COMPLEXITY & CODE LOGIC", insertionTable, insertionCode, "Insertion Sort");
                }
            }

            private void drawMergeDetailed(GraphicsContext gc, int page) {
                if (page == 0) { // Page 1: Core Logic
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("MERGE SORT: SUBARRAY MERGING LOGIC", 15, 30);

                    double y = 80;
                    double w = 50;
                    double h = 45;

                    gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                    gc.fillRoundRect(120, y, w, h, 4, 4);
                    gc.fillRoundRect(180, y, w, h, 4, 4);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("5", 140, y + 26);
                    gc.fillText("15", 196, y + 26);

                    gc.setFill(isGB ? theme.unsorted : Color.web("#334155"));
                    gc.fillRoundRect(340, y, w, h, 4, 4);
                    gc.fillRoundRect(400, y, w, h, 4, 4);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.fillText("3", 360, y + 26);
                    gc.fillText("10", 416, y + 26);

                    gc.setFill(isGB ? theme.bg : Color.web("#0f172a"));
                    gc.setStroke(isGB ? theme.border : Color.web("#1e293b"));
                    gc.setLineWidth(1.0);
                    gc.strokeRoundRect(170, y + 70, 250, h + 10, 4, 4);

                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 8, isGB));
                    gc.fillText("SUBARRAY A (SORTED)", 115, y - 10);
                    gc.fillText("SUBARRAY B (SORTED)", 335, y - 10);
                    gc.fillText("MERGED OUTPUT SLOT", 175, y + 65);

                    gc.setFill(isGB ? theme.sorted : Color.web("#06b6d4"));
                    gc.fillRoundRect(125, y - 24, 40, 10, 2, 2);
                    gc.fillRoundRect(345, y - 24, 40, 10, 2, 2);
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 6, isGB));
                    gc.fillText("HEAD A", 129, y - 17);
                    gc.fillText("HEAD B", 349, y - 17);

                    drawLegend(gc, "Merge Sort", 10, 235);
                } else if (page == 1) { // Page 2: Process - Compare Heads
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("MERGE SORT: PROCESS - COMPARE HEADS", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color headCol = isGB ? theme.accent : Color.web("#06b6d4");

                    double[] subA = {5, 15};
                    Color[] colsA = {headCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, subA, colsA, 0, headCol, "Subarray A");

                    double[] subB = {3, 10};
                    Color[] colsB = {headCol, unsortedCol};
                    drawMiniArray(gc, 230, 75, subB, colsB, 0, headCol, "Subarray B");

                    double blockW = isGB ? 40 : 48;
                    double blockH = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;

                    gc.setFill(isGB ? theme.textMuted : Color.web("#cbd5e1"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText("Merged Output Array", 100, 165);

                    for (int i = 0; i < 4; i++) {
                        double bx = 100 + i * (blockW + gap);
                        if (i == 0) {
                            gc.setFill(sortedCol);
                            gc.fillRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.border : sortedCol.deriveColor(0, 1, 1.2, 1));
                            gc.strokeRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);

                            gc.setFill(isGB ? theme.bg : Color.WHITE);
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 14, isGB));
                            gc.fillText("3", bx + (isGB ? 14 : 20), 175 + (isGB ? 25 : 30));
                        } else {
                            gc.setFill(isGB ? theme.panelBg : Color.web("#1e293b"));
                            gc.fillRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.border : Color.web("#334155"));
                            gc.setLineWidth(1.0);
                            gc.setLineDashes(new double[]{4.0, 3.0});
                            gc.strokeRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setLineDashes(null);
                        }
                    }

                    double startX = 230 + 0 * (blockW + gap) + blockW/2.0;
                    double endX = 100 + 0 * (blockW + gap) + blockW/2.0;
                    drawArrow(gc, startX, 75 + blockH, endX, 175, isGB ? theme.accent : Color.web("#06b6d4"));

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("STEP 1 DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Compare HEAD A (5) vs HEAD B (3).", 420, textY);
                    gc.fillText("- Since 3 < 5, select HEAD B.", 420, textY + spacingY);
                    gc.fillText("- Press [ENTER] to merge it down.", 420, textY + 2 * spacingY);
                    gc.fillText("- First output slot gets 3.", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Merge Sort", 10, 235);
                } else if (page == 2) { // Page 3: Process - Merge Head
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("MERGE SORT: PROCESS - MERGE HEAD", 15, 30);

                    Color unsortedCol = isGB ? theme.unsorted : Color.web("#334155");
                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");
                    Color headCol = isGB ? theme.accent : Color.web("#06b6d4");

                    double[] subA = {5, 15};
                    Color[] colsA = {headCol, unsortedCol};
                    drawMiniArray(gc, 50, 75, subA, colsA, 0, headCol, "Subarray A");

                    double[] subB = {10, 0};
                    Color[] colsB = {headCol, unsortedCol};
                    // Manually draw Subarray B to show it has only 1 element left
                    double blockW = isGB ? 40 : 48;
                    double blockH = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;

                    gc.setFill(isGB ? theme.text : Color.web("#cbd5e1"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText("Subarray B", 230, 75 - 8);

                    for (int i = 0; i < 2; i++) {
                        double bx = 230 + i * (blockW + gap);
                        if (i == 0) {
                            gc.setFill(headCol);
                            gc.fillRoundRect(bx, 75, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.accent : Color.web("#06b6d4"));
                            gc.strokeRoundRect(bx - 1.5, 75 - 1.5, blockW + 3, blockH + 3, isGB ? 2 : 6, isGB ? 2 : 6);

                            gc.setFill(isGB ? theme.bg : Color.WHITE);
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 14, isGB));
                            gc.fillText("10", bx + (isGB ? 8 : 14), 75 + (isGB ? 25 : 30));
                        } else {
                            gc.setFill(isGB ? theme.panelBg : Color.web("#1e293b"));
                            gc.fillRoundRect(bx, 75, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.border : Color.web("#334155"));
                            gc.setLineWidth(1.0);
                            gc.setLineDashes(new double[]{4.0, 3.0});
                            gc.strokeRoundRect(bx, 75, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setLineDashes(null);
                        }
                    }

                    gc.setFill(isGB ? theme.textMuted : Color.web("#cbd5e1"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText("Merged Output Array", 100, 165);

                    for (int i = 0; i < 4; i++) {
                        double bx = 100 + i * (blockW + gap);
                        if (i < 2) {
                            gc.setFill(sortedCol);
                            gc.fillRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.border : sortedCol.deriveColor(0, 1, 1.2, 1));
                            gc.strokeRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);

                            gc.setFill(isGB ? theme.bg : Color.WHITE);
                            gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 14, isGB));
                            gc.fillText(i == 0 ? "3" : "5", bx + (isGB ? 14 : 20), 175 + (isGB ? 25 : 30));
                        } else {
                            gc.setFill(isGB ? theme.panelBg : Color.web("#1e293b"));
                            gc.fillRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setStroke(isGB ? theme.border : Color.web("#334155"));
                            gc.setLineWidth(1.0);
                            gc.setLineDashes(new double[]{4.0, 3.0});
                            gc.strokeRoundRect(bx, 175, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                            gc.setLineDashes(null);
                        }
                    }

                    double startX = 50 + 0 * (blockW + gap) + blockW/2.0;
                    double endX = 100 + 1 * (blockW + gap) + blockW/2.0;
                    drawArrow(gc, startX, 75 + blockH, endX, 175, isGB ? theme.accent : Color.web("#06b6d4"));

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("STEP 2 DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Compare HEAD A (5) vs HEAD B (10).", 420, textY);
                    gc.fillText("- Since 5 < 10, select HEAD A.", 420, textY + spacingY);
                    gc.fillText("- Press [ENTER] to merge it down.", 420, textY + 2 * spacingY);
                    gc.fillText("- Output array is now [3, 5].", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Merge Sort", 10, 235);
                } else if (page == 3) { // Page 4: Process - Finalize
                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                    gc.fillText("MERGE SORT: PROCESS - FINAL MERGING", 15, 30);

                    Color sortedCol = isGB ? theme.sorted : Color.web("#10b981");

                    double blockW = isGB ? 40 : 48;
                    double blockH = isGB ? 40 : 48;
                    double gap = isGB ? 8 : 12;

                    // Draw completed merged output array
                    gc.setFill(isGB ? theme.textMuted : Color.web("#cbd5e1"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText("Merged Output Array", 100, 125);

                    double[] finalVals = {3, 5, 10, 15};
                    for (int i = 0; i < 4; i++) {
                        double bx = 100 + i * (blockW + gap);
                        gc.setFill(sortedCol);
                        gc.fillRoundRect(bx, 140, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                        gc.setStroke(isGB ? theme.border : sortedCol.deriveColor(0, 1, 1.2, 1));
                        gc.strokeRoundRect(bx, 140, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);

                        gc.setFill(isGB ? theme.bg : Color.WHITE);
                        gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 14, isGB));
                        String valStr = String.valueOf((int)finalVals[i]);
                        double textOffset = isGB ? (valStr.length() > 1 ? 8 : 14) : (valStr.length() > 1 ? 14 : 20);
                        gc.fillText(valStr, bx + textOffset, 140 + (isGB ? 25 : 30));
                    }

                    gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 11, isGB));
                    gc.fillText("FINAL STEP DETAILS:", 420, 80);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, 10, isGB));
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    double textY = 100;
                    double spacingY = isGB ? 15 : 20;
                    gc.fillText("- Merge HEAD B (10) -> Output [3, 5, 10].", 420, textY);
                    gc.fillText("- Subarray B is now empty.", 420, textY + spacingY);
                    gc.fillText("- Select remaining HEAD A (15) & merge.", 420, textY + 2 * spacingY);
                    gc.fillText("- Final sorted array: [3, 5, 10, 15].", 420, textY + 3 * spacingY);

                    drawLegend(gc, "Merge Sort", 10, 235);
                } else if (page == 4) {
                    drawDetailedShortcutsPage(gc, "Merge Sort");
                } else {
                    String[][] mergeTable = {
                        {"Best-Case Time", "O(N log N)"},
                        {"Average-Case Time", "O(N log N)"},
                        {"Worst-Case Time", "O(N log N)"},
                        {"Space Complexity", "O(N)"},
                        {"Stability", "Stable"}
                    };
                    String[] mergeCode = {
                        "function mergeSort(arr, l, r):",
                        "  if l < r:",
                        "    mid = (l + r) / 2",
                        "    mergeSort(arr, l, mid)",
                        "    mergeSort(arr, mid+1, r)",
                        "    merge(arr, l, mid, r)"
                    };
                    drawComplexityPage(gc, "MERGE SORT: COMPLEXITY & CODE LOGIC", mergeTable, mergeCode, "Merge Sort");
                }
            }

            private void drawMiniArray(GraphicsContext gc, double x, double y, double[] values, Color[] colors, int highlightIdx, Color highlightBorderCol, String label) {
                double blockW = isGB ? 40 : 48;
                double blockH = isGB ? 40 : 48;
                double gap = isGB ? 8 : 12;
                
                if (label != null && !label.isEmpty()) {
                    gc.setFill(isGB ? theme.text : Color.web("#cbd5e1"));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText(label, x, y - 8);
                }
                
                for (int i = 0; i < values.length; i++) {
                    double bx = x + i * (blockW + gap);
                    Color col = colors[i];
                    
                    gc.setFill(col);
                    gc.fillRoundRect(bx, y, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                    
                    if (i == highlightIdx && highlightBorderCol != null) {
                        gc.setStroke(highlightBorderCol);
                        gc.setLineWidth(isGB ? 2.0 : 3.0);
                        gc.strokeRoundRect(bx - 1.5, y - 1.5, blockW + 3, blockH + 3, isGB ? 2 : 6, isGB ? 2 : 6);
                    } else {
                        gc.setStroke(isGB ? theme.border : col.deriveColor(0, 1, 1.2, 1));
                        gc.setLineWidth(1.0);
                        gc.strokeRoundRect(bx, y, blockW, blockH, isGB ? 2 : 6, isGB ? 2 : 6);
                    }
                    
                    gc.setFill(isGB ? theme.bg : Color.WHITE);
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 14, isGB));
                    String valStr = String.valueOf((int)values[i]);
                    double textOffset = isGB ? (valStr.length() > 1 ? 8 : 14) : (valStr.length() > 1 ? 14 : 20);
                    gc.fillText(valStr, bx + textOffset, y + (isGB ? 25 : 30));
                }
            }

            private void drawArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
                gc.setStroke(color);
                gc.setLineWidth(2.0);
                gc.strokeLine(x1, y1, x2, y2);
                
                double angle = Math.atan2(y2 - y1, x2 - x1);
                double arrowHeadSize = 6;
                double dx1 = arrowHeadSize * Math.cos(angle - Math.PI / 6);
                double dy1 = arrowHeadSize * Math.sin(angle - Math.PI / 6);
                double dx2 = arrowHeadSize * Math.cos(angle + Math.PI / 6);
                double dy2 = arrowHeadSize * Math.sin(angle + Math.PI / 6);
                
                gc.strokeLine(x2, y2, x2 - dx1, y2 - dy1);
                gc.strokeLine(x2, y2, x2 - dx2, y2 - dy2);
            }

            private void drawSwapArrow(GraphicsContext gc, double x1, double y1, double x2, double y2, Color color) {
                gc.setStroke(color);
                gc.setLineWidth(2.0);
                
                double ctrlX = (x1 + x2) / 2.0;
                double ctrlY = y1 - 25; // curve upwards
                
                gc.beginPath();
                gc.moveTo(x1, y1);
                gc.quadraticCurveTo(ctrlX, ctrlY, x2, y2);
                gc.stroke();
                
                double angle = Math.atan2(y2 - ctrlY, x2 - ctrlX);
                double arrowHeadSize = 6;
                double dx1 = arrowHeadSize * Math.cos(angle - Math.PI / 6);
                double dy1 = arrowHeadSize * Math.sin(angle - Math.PI / 6);
                double dx2 = arrowHeadSize * Math.cos(angle + Math.PI / 6);
                double dy2 = arrowHeadSize * Math.sin(angle + Math.PI / 6);
                
                gc.strokeLine(x2, y2, x2 - dx1, y2 - dy1);
                gc.strokeLine(x2, y2, x2 - dx2, y2 - dy2);
            }

            private void drawComplexityPage(GraphicsContext gc, String title, String[][] complexityTable, String[] pseudocodeLines, String algo) {
                gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, 14, isGB));
                gc.fillText(title, 15, 30);

                double cardW = 310;
                double cardH = isGB ? 175 : 180;
                gc.setFill(isGB ? theme.panelBg : Color.web("#1e293b"));
                gc.fillRoundRect(30, 60, cardW, cardH, isGB ? 2 : 6, isGB ? 2 : 6);
                gc.setStroke(isGB ? theme.border : Color.web("#334155"));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(30, 60, cardW, cardH, isGB ? 2 : 6, isGB ? 2 : 6);

                gc.setFill(isGB ? theme.text : Color.web("#cbd5e1"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 12, isGB));
                gc.fillText("ALGORITHMIC COMPLEXITY", 45, 82);

                gc.setFont(getThemeFont("Segoe UI", FontWeight.NORMAL, isGB ? 8 : 11, isGB));
                for (int i = 0; i < complexityTable.length; i++) {
                    double rowY = 110 + i * (isGB ? 20 : 24);
                    gc.setFill(isGB ? theme.textMuted : Color.web("#94a3b8"));
                    gc.fillText(complexityTable[i][0], 45, rowY);
                    
                    String val = complexityTable[i][1];
                    boolean isHighlight = val.contains("O(") || val.equals("Stable");
                    gc.setFill(isHighlight ? (isGB ? theme.accent : Color.web("#f59e0b")) : (isGB ? theme.text : Color.web("#f8fafc")));
                    gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 8 : 11, isGB));
                    gc.fillText(val, 210, rowY);
                }

                gc.setFill(isGB ? theme.panelBg : Color.web("#1e293b"));
                gc.fillRoundRect(360, 60, cardW, cardH, isGB ? 2 : 6, isGB ? 2 : 6);
                gc.setStroke(isGB ? theme.border : Color.web("#334155"));
                gc.strokeRoundRect(360, 60, cardW, cardH, isGB ? 2 : 6, isGB ? 2 : 6);

                gc.setFill(isGB ? theme.text : Color.web("#cbd5e1"));
                gc.setFont(getThemeFont("Segoe UI", FontWeight.BOLD, isGB ? 9 : 12, isGB));
                gc.fillText("CORE LOGIC (PSEUDOCODE)", 375, 82);

                double charW = isGB ? 5.2 : 6.0;
                for (int i = 0; i < pseudocodeLines.length; i++) {
                    double lineY = 110 + i * (isGB ? 15 : 18);
                    String line = pseudocodeLines[i];
                    
                    double drawX = 375;
                    String[] tokens = line.split("(?<=\\s)|(?=\\s)|(?=\\()|(?<=\\) )|(?=\\))|(?<=\\))");
                    for (String token : tokens) {
                        String trimToken = token.trim();
                        if (trimToken.equals("for") || trimToken.equals("while") || trimToken.equals("if") || trimToken.equals("return") || trimToken.equals("function") || trimToken.equals("else")) {
                            gc.setFill(isGB ? theme.accent : Color.web("#f59e0b"));
                            gc.setFont(getThemeFont(isGB ? "Courier New" : "Consolas", FontWeight.BOLD, isGB ? 8 : 10, isGB));
                        } else if (trimToken.startsWith("//")) {
                            gc.setFill(isGB ? theme.textMuted : Color.web("#64748b"));
                            gc.setFont(getThemeFont(isGB ? "Courier New" : "Consolas", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                        } else {
                            gc.setFill(isGB ? theme.text : Color.web("#f8fafc"));
                            gc.setFont(getThemeFont(isGB ? "Courier New" : "Consolas", FontWeight.NORMAL, isGB ? 8 : 10, isGB));
                        }
                        gc.fillText(token, drawX, lineY);
                        drawX += token.length() * charW;
                    }
                }
                drawLegend(gc, algo, 10, 235);
            }

            private void drawDetailedVisualGuide(GraphicsContext gc, String algo, int page) {
                if (algo.equalsIgnoreCase("Selection Sort")) {
                    drawSelectionDetailed(gc, page);
                } else if (algo.equalsIgnoreCase("Quick Sort")) {
                    drawQuickDetailed(gc, page);
                } else if (algo.equalsIgnoreCase("Bubble Sort")) {
                    drawBubbleDetailed(gc, page);
                } else if (algo.equalsIgnoreCase("Insertion Sort")) {
                    drawInsertionDetailed(gc, page);
                } else {
                    drawMergeDetailed(gc, page);
                }
            }
        };
        
        startBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            tutorialTimer.stop();
            root.getChildren().remove(overlay);
            lastGameStartTime = System.currentTimeMillis();
            onStartGame.run();
        });

        backBtn.setOnAction(event -> {
            SoundManager.playMenuSelect();
            tutorialTimer.stop();
            root.getChildren().remove(overlay);
        });

        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                event.consume();
                tutorialTimer.stop();
                root.getChildren().remove(overlay);
                lastGameStartTime = System.currentTimeMillis();
                onStartGame.run();
            } else if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                event.consume();
                tutorialTimer.stop();
                root.getChildren().remove(overlay);
            } else if (event.getCode() == javafx.scene.input.KeyCode.RIGHT || event.getCode() == javafx.scene.input.KeyCode.D) {
                event.consume();
                boolean showDetailed = showDetailedHolder[0];
                if (showDetailed) {
                    if (detailedPageHolder[0] < maxDetailedPages - 1) {
                        detailedPageHolder[0]++;
                        SoundManager.playClick();
                        refreshTabContent.run();
                    }
                } else {
                    if (currentStepHolder[0] < maxSteps - 1) {
                        currentStepHolder[0]++;
                        SoundManager.playClick();
                        refreshTabContent.run();
                    }
                }
            } else if (event.getCode() == javafx.scene.input.KeyCode.LEFT || event.getCode() == javafx.scene.input.KeyCode.A) {
                event.consume();
                boolean showDetailed = showDetailedHolder[0];
                if (showDetailed) {
                    if (detailedPageHolder[0] > 0) {
                        detailedPageHolder[0]--;
                        SoundManager.playClick();
                        refreshTabContent.run();
                    }
                } else {
                    if (currentStepHolder[0] > 0) {
                        currentStepHolder[0]--;
                        SoundManager.playClick();
                        refreshTabContent.run();
                    }
                }
            }
        });
        
        tutorialTimer.start();
    }

    private static void setupButtonHover(Button btn, String normalBg, String normalText, String normalBorder,
                                         String hoverBg, String hoverText, String hoverBorder,
                                         boolean isGB, String fontFam, String fontSize, boolean hasGlow, String accentColor) {
        btn.setStyle("-fx-background-color: " + normalBg + 
                     "; -fx-text-fill: " + normalText + 
                     "; -fx-font-family: " + fontFam + 
                     "; -fx-font-weight: bold; -fx-font-size: " + fontSize + 
                     "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + normalBorder + 
                     "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> {
            SoundManager.playHover();
            String glowStyle = (isGB || !hasGlow) ? "" : " -fx-effect: dropshadow(three-pass-box, " + accentColor + "66, 8, 0, 0, 0);";
            btn.setStyle("-fx-background-color: " + hoverBg + 
                         "; -fx-text-fill: " + hoverText + 
                         "; -fx-font-family: " + fontFam + 
                         "; -fx-font-weight: bold; -fx-font-size: " + fontSize + 
                         "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + hoverBorder + 
                         "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;" + glowStyle);
        });
        btn.setOnMouseExited(e -> {
            btn.setStyle("-fx-background-color: " + normalBg + 
                         "; -fx-text-fill: " + normalText + 
                         "; -fx-font-family: " + fontFam + 
                         "; -fx-font-weight: bold; -fx-font-size: " + fontSize + 
                         "; -fx-padding: 12px 35px; -fx-background-radius: 6px; -fx-border-color: " + normalBorder + 
                         "; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-cursor: hand;");
        });
    }

    private static void showPauseOverlay(StackPane root, ChromaCascadeModel model, ChromaCascadeController controller, VBox menuLayout, VBox gameLayout) {
        if (activePauseOverlay != null) {
            return;
        }

        model.setGameState("PAUSED");

        Theme theme = model.getTheme();
        boolean isGB = theme.name.equalsIgnoreCase("GameBoy Retro");
        String fontFam = isGB && isCustomFontLoaded ? "'Press Start 2P'" : (isGB ? "'Courier New'" : "'Segoe UI', sans-serif");

        VBox overlay = new VBox(20);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: " + (isGB ? "rgba(202, 220, 159, 0.95)" : "rgba(11, 15, 25, 0.85)") + ";");

        // The card panel
        VBox card = new VBox(25);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(30, 40, 30, 40));
        card.setMaxWidth(450);
        card.setStyle("-fx-background-color: " + theme.panelBgHex + 
                      "; -fx-border-color: " + theme.borderHex + 
                      "; -fx-border-width: 2px; -fx-background-radius: 8px; -fx-border-radius: 8px;");

        Label pauseTitle = new Label("GAME PAUSED");
        if (isGB) {
            pauseTitle.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + ";");
        } else {
            pauseTitle.setStyle("-fx-font-family: 'Segoe UI', 'Outfit', sans-serif; -fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + theme.accentHex + 
                                "; -fx-effect: dropshadow(three-pass-box, " + theme.accentHex + "66, 12, 0, 0, 0);");
        }

        VBox buttonContainer = new VBox(15);
        buttonContainer.setAlignment(Pos.CENTER);

        Button resumeBtn = new Button("RESUME");
        Button restartBtn = new Button("RESTART");
        Button leaveBtn = new Button("LEAVE");

        String normalBg = isGB ? theme.panelBgHex : "#1e293b";
        String normalText = isGB ? theme.textHex : "#f8fafc";
        String normalBorder = isGB ? theme.borderHex : "#334155";
        
        String hoverBg = isGB ? theme.textHex : theme.accentHex;
        String hoverText = isGB ? theme.bgHex : "#ffffff";
        String hoverBorder = isGB ? theme.textHex : theme.accentHex;

        setupButtonHover(resumeBtn, normalBg, normalText, normalBorder, hoverBg, hoverText, hoverBorder, isGB, fontFam, isGB ? "9px" : "14px", true, theme.accentHex);
        setupButtonHover(restartBtn, normalBg, normalText, normalBorder, isGB ? theme.textHex : "#3b82f6", hoverText, isGB ? theme.textHex : "#3b82f6", isGB, fontFam, isGB ? "9px" : "14px", true, "#3b82f6");
        setupButtonHover(leaveBtn, normalBg, normalText, normalBorder, isGB ? theme.textHex : "#ef4444", hoverText, isGB ? theme.textHex : "#ef4444", isGB, fontFam, isGB ? "9px" : "14px", true, "#ef4444");

        resumeBtn.setOnAction(e -> {
            SoundManager.playMenuSelect();
            hidePauseOverlay(root, model, gameLayout);
        });

        restartBtn.setOnAction(e -> {
            SoundManager.playMenuSelect();
            root.getChildren().remove(overlay);
            activePauseOverlay = null;
            model.setGameState("PLAYING");
            controller.initializeGame();
            gameLayout.requestFocus();
        });

        leaveBtn.setOnAction(e -> {
            SoundManager.playMenuSelect();
            // Swap with leave confirmation dialog
            buttonContainer.getChildren().clear();

            Label confirmLabel = new Label("Are you sure you want to leave?\nAll current wave progress will be lost.");
            confirmLabel.setAlignment(Pos.CENTER);
            confirmLabel.setStyle("-fx-font-family: " + fontFam + "; -fx-font-size: " + (isGB ? "8px" : "13px") + "; -fx-text-fill: " + theme.textHex + "; -fx-text-alignment: center;");

            HBox confirmButtons = new HBox(15);
            confirmButtons.setAlignment(Pos.CENTER);

            Button yesBtn = new Button("YES, LEAVE");
            Button noBtn = new Button("NO, STAY");

            setupButtonHover(yesBtn, normalBg, normalText, normalBorder, isGB ? theme.textHex : "#ef4444", hoverText, isGB ? theme.textHex : "#ef4444", isGB, fontFam, isGB ? "9px" : "13px", true, "#ef4444");
            setupButtonHover(noBtn, normalBg, normalText, normalBorder, isGB ? theme.textHex : theme.accentHex, hoverText, isGB ? theme.textHex : theme.accentHex, isGB, fontFam, isGB ? "9px" : "13px", true, theme.accentHex);

            yesBtn.setOnAction(y -> {
                SoundManager.playMenuSelect();
                root.getChildren().remove(overlay);
                activePauseOverlay = null;
                model.setGameState("MENU");
                SoundManager.stopMusic();
                root.getChildren().setAll(menuLayout);
            });

            noBtn.setOnAction(n -> {
                SoundManager.playMenuSelect();
                // Restore normal button view
                buttonContainer.getChildren().clear();
                buttonContainer.getChildren().addAll(resumeBtn, restartBtn, leaveBtn);
            });

            confirmButtons.getChildren().addAll(yesBtn, noBtn);
            buttonContainer.getChildren().addAll(confirmLabel, confirmButtons);
        });

        buttonContainer.getChildren().addAll(resumeBtn, restartBtn, leaveBtn);
        card.getChildren().addAll(pauseTitle, buttonContainer);
        overlay.getChildren().add(card);

        activePauseOverlay = overlay;
        root.getChildren().add(overlay);
        overlay.requestFocus();
    }

    private static void hidePauseOverlay(StackPane root, ChromaCascadeModel model, VBox gameLayout) {
        if (activePauseOverlay != null) {
            root.getChildren().remove(activePauseOverlay);
            activePauseOverlay = null;
        }
        model.setGameState("PLAYING");
        gameLayout.requestFocus();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
