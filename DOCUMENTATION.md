# Sort Pulse - Technical Documentation & System Manual

**Course Project:** Sorting Algorithm Puzzle Game  
**Author:** Pair-Programming Assistant  
**Date:** June 8, 2026  
**Format Specifications:** A4 Page Size | Arial 11pt | 1.15 Line Spacing | 1-Inch Margins | Justified Alignment | Bold Headings | Bottom-Center Page Numbers

---

## 1. Introduction

### Background of the Project
In computer science education, sorting algorithms are foundational concepts taught to establish an understanding of algorithmic thinking, complexity analysis, and data manipulation. However, traditional pedagogical methods—consisting of static diagrams, pseudocode lectures, or passive visualizations—often fail to engage students effectively. Students frequently struggle to develop an intuitive "feel" for how algorithms traverse, partition, or swap elements step-by-step under varying conditions. 

**Sort Pulse** was conceived to address this gap by transforming abstract algorithmic operations into a high-stakes, time-attack 2D arcade puzzle game. By gamifying Selection Sort, Bubble Sort, Insertion Sort, Quick Sort, and Merge Sort, the application challenges players to manually drive the exact step-by-step logic of these algorithms against a countdown clock. 

### Purpose of the System
The primary purpose of **Sort Pulse** is to serve as an interactive training tool and educational game that reinforces theoretical knowledge of sorting algorithms through tactile play. Rather than writing code or watching an animation, players *become* the algorithm. They must inspect a randomized block grid, determine the correct element to select, swap, or shift, and execute that action. The system validates player actions in real-time against pre-computed algorithm state traces, establishing a tight feedback loop that builds deep mechanical intuition and muscle memory for algorithmic pathways.

### Project Objectives
1. **Interactive Gamification:** Design and implement five distinct game modes corresponding to five standard sorting algorithms, each with custom mechanics reflecting the algorithm's actual operational flow.
2. **Robust Software Architecture:** Build the game using a clean Model-View-Controller (MVC) pattern in Java 21+ and JavaFX, ensuring a strict separation of concerns, high performance, and smooth animations.
3. **Application of OOP Principles:** Leverage core Object-Oriented Programming (OOP) concepts—Encapsulation, Abstraction, Inheritance, and Polymorphism—to model game elements (e.g., polymorphic block segments) and ensure codebase extensibility.
4. **Immersive Retro Aesthetics:** Develop a customized rendering engine supporting dynamic 2D canvas drawing, dynamic themes (Classic Neon and GameBoy Retro), and a custom-designed real-time synthesizer engine for background music and sound effects.
5. **Local Data Persistence:** Implement local leaderboard management to automatically track, score, and store top performances across different algorithm modes in a flat-file database.

---

## 2. System Design and Overview

### Description of the Program
**Sort Pulse** is a native desktop application written in Java and rendered on a JavaFX 2D canvas. The game offers two core play styles: **Timed Blitz Mode** (where players start with a countdown timer, gaining time for correct moves and losing time and points for errors) and **Practice Mode** (where the timer is disabled, allowing stress-free learning). 

The game loop is driven by a high-performance JavaFX `AnimationTimer` running at 60 frames per second. It handles coordinate interpolation for smooth block sliding animations, update sweeps for a vector particle physics engine, and timing loops. To complete a "wave," the player must fully sort a randomized array of blocks. As waves are cleared, the array size scales dynamically from 8 to 12 blocks, increasing the difficulty.

### Scope and Limitations
* **Scope:**
  * Interactive manual step-by-step sorting for Selection Sort, Bubble Sort, Insertion Sort, Lomuto partition Quick Sort, and double-level Merge Sort.
  * Runtime-synthesized 8-bit audio with tempo adjustments tied to time pressure.
  * Dual-theme visual toggle (Classic Neon and GameBoy Retro) with custom typography and sub-pixel anti-aliasing overrides.
  * Persistence of the top 5 high scores for each algorithm mode in a local text file.
* **Limitations:**
  * The game currently only supports ascending sorting order.
  * High-score tracking is restricted to local storage on a single machine; online leaderboards are out of scope.
  * The grid size is capped at 12 elements to maintain readability on standard displays and keep game sessions fast-paced.

### Overview of the Data Structure(s) Used
1. **Polymorphic Arrays (`BlockSegment[]`):** The primary data structure representing the sorting grid is a primitive array of a custom abstract class, `BlockSegment`. This array is wrapped in a container class, `PuzzleRow`.
2. **Generic Lists (`java.util.List<SortingStep>`):** Used to store the pre-computed sequence of correct states generated by the algorithmic tracers. The game references this list to validate if the player's cursor selection and shift actions match the theoretical sorting steps.
3. **Floating & Particle Lists:** Dynamic array lists are used by the visual engine to track active particle vectors (`List<Particle>`) and floating text displays (`List<FloatingText>`) rendered each frame.
4. **Observable Lists (`ObservableList<String>`):** Utilized for the HUD and live status log window to dynamically update logs in the JavaFX UI without manual redraw hooks.
5. **Flat-File Database (`sort_pulse_scores.txt`):** A text-based, delimited file structure used to persist leaderboard entries serialized as comma-separated values.

### Overview of the Algorithm Implemented
The application implements two categories of algorithms:
1. **Theoretical Sorting Tracers (in `GridSorter`):** These run behind the scenes when a wave spawns. They take the initial randomized array, clone it, and run a tracing version of the algorithm that records every transition (pivots, cursor positions, swaps, active ranges) into a list of `SortingStep` objects.
2. **Game Validation Algorithms:** During gameplay, the controller intercepts keystrokes and applies validation logic. The player's current grid state and cursor actions are checked against the active `SortingStep`. If correct, the grid transitions to the next pre-computed step; if incorrect, penalties are applied.

### Real-World Application or Use Case
* **Computer Science Education:** An interactive lab tool for introductory programming courses, enabling students to visual-test their understanding of partitions, head comparisons, and insertions.
* **Technical Interview Preparation:** A drill simulator for developers looking to build a rapid, instinctual understanding of array transitions.
* **Interactive Public Exhibits:** A lightweight arcade game suitable for science museums or computer history exhibits, highlighting classic computing interfaces and logic games.

---

## 3. Program Design

### Data Structures Design
The structural diagram of the grid is represented by `PuzzleRow` which holds a fixed-length array of `BlockSegment` references:

```
+-------------------------------------------------------------+
|                          PuzzleRow                          |
+-------------------------------------------------------------+
| - currentSet: BlockSegment[]                                |
| - overflowStack: BlockSegment[5]                            |
| - overflowCount: int                                        |
+-------------------------------------------------------------+
| + setSegment(index: int, segment: BlockSegment)             |
| + simulateExternalUpdate(segment: BlockSegment, index: int)  |
| + clearOverflow()                                           |
+-------------------------------------------------------------+
                               |
                               v (Contains 0..N)
+-------------------------------------------------------------+
|                  BlockSegment (Abstract)                    |
+-------------------------------------------------------------+
| - rawValue: int                                             |
| - positionIndex: int                                        |
+-------------------------------------------------------------+
| + getRawValue(): int                                        |
| + getPositionIndex(): int                                   |
| + calculateSortWeight(): double (Abstract)                  |
+-------------------------------------------------------------+
              ^                               ^
              | (Inherits)                    | (Inherits)
+---------------------------+   +---------------------------+
|        OddSegment         |   |        EvenSegment        |
+---------------------------+   +---------------------------+
| + calculateSortWeight()   |   | + calculateSortWeight()   |
|   // rawValue * 1.25      |   |   // rawValue + 5.5       |
+---------------------------+   +---------------------------+
```

To prevent array boundary exceptions from crashing the active game thread, `PuzzleRow` implements a graceful **Safety Overflow Stack**. If an index out of bounds is somehow triggered by an external update or abnormal visual state interpolation, the block is captured in the overflow stack, logged to the debugger, and the game loop continues safely.

### Algorithm Design
Each algorithm mode translates its logical operations into specific player mechanics:

1. **Selection Sort:**
   * **Game mechanic:** The active unsorted range is surrounded by a dashed boundary box. The player moves the yellow cursor to the *minimum* value within this range and presses `ENTER`.
   * **System action:** Shuts the minimum element to the front of the unsorted range, shrinks the active boundary box by one from the left, and snaps the cursor to the next start.
2. **Bubble Sort:**
   * **Game mechanic:** The player must manually traverse the entire array. When they find two adjacent elements out of order, they position the cursor on the left element and press `ENTER` to swap them.
   * **System action:** Executes a swap on the array, updates the inversion count, and checks if more swaps are needed.
3. **Insertion Sort:**
   * **Game mechanic:** The cursor highlights the first unsorted element. The player must shift this element leftward through the sorted left partition by pressing `ENTER` repeatedly until it rests in its correct relative position.
4. **Quick Sort (Lomuto Partition):**
   * **Game mechanic:** The rightmost element of the partition is badged as the **PIVOT**. A comparison badge (`<` or `>`) displays above the active block. The player scans left-to-right: if the active block is less than or equal to the pivot, they press `ENTER` to swap it into the `TARGET SLOT` (indicated by a vertical arrow). Finally, they select the pivot to swap it into its resolved split slot.
5. **Merge Sort (Double-Level Merging):**
   * **Game mechanic:** Elements are split into two pre-sorted subarrays (`SUBARRAY A` and `SUBARRAY B`) on the upper rendering level. The player compares the two highlighted "head" elements and presses `ENTER` on the smaller of the two to pull it down to the `TARGET SLOT` on the lower level.

### UML Class Diagram
The overall structural architecture follows a strict Model-View-Controller (MVC) relationship:

```
                     +---------------------------------------+
                     |          ChromaCascadeApp             |
                     +---------------------------------------+
                     | + main(args: String[])                |
                     | + start(stage: Stage)                 |
                     +---------------------------------------+
                                         |
                                         v (Launches)
+-----------------------+    (Observes)     +-----------------------+
|  ChromaCascadeView    | <---------------- |  ChromaCascadeModel   |
+-----------------------+                   +-----------------------+
| - canvas: Canvas      |                   | - gameState: String   |
| - visualXMap: Map     |                   | - score: int          |
| - particles: List     |                   | - countdownTimer: int |
| - floatingTexts: List |                   | - puzzleRow: PuzzleRow|
+-----------------------+                   +-----------------------+
    ^                                           ^
    | (Draws/Updates)                           | (Mutates state)
    |                                           |
    +-------------------+---------------+-------+
                        |               |
                        |               v (References)
            +---------------------------------------+
            |        ChromaCascadeController        |
            +---------------------------------------+
            | - model: ChromaCascadeModel           |
            | - view: ChromaCascadeView             |
            | - animationTimer: AnimationTimer      |
            +---------------------------------------+
            | + initializeGame()                    |
            | + handleKeyPress(code: KeyCode)       |
            | + executeShiftAction()                |
            +---------------------------------------+
                |               |               |
                v (Uses)        v (Uses)        v (Uses)
+-----------------------+  +-----------------+  +-----------------------+
|      GridSorter       |  |  SoundManager   |  |  LeaderboardManager   |
+-----------------------+  +-----------------+  +-----------------------+
| + sortRow()           |  | + playTone()    |  | + addScore()          |
| + bubbleSort()        |  | + startMusic()  |  | + loadScores()        |
| + compare()           |  | + stopMusic()   |  | + qualifiesTopFive()  |
+-----------------------+  +-----------------+  +-----------------------+
```

### Application of Object-Oriented Programming principles

#### Encapsulation
All core state variables in `ChromaCascadeModel` (such as `score`, `countdownTimer`, `gameState`, and `playerInitials`) are declared `private`. Access to these fields is strictly controlled via public getter and setter methods. This ensures that the view and other classes cannot directly corrupt the game state:

```java
public class ChromaCascadeModel {
    private int score = 0;
    private int countdownTimer = 60;
    private String gameState = "MENU";

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    
    public int getCountdownTimer() { return countdownTimer; }
    public void setCountdownTimer(int countdownTimer) { this.countdownTimer = countdownTimer; }
}
```

#### Abstraction
The class `BlockSegment` acts as an abstraction for a visual sortable block in the game grid. It hides the details of how sort weights are calculated and exposes a clean abstract method, `calculateSortWeight()`. The rest of the game engine (like drawing loops and sorting utilities) interacts only with the abstract `BlockSegment` class, unaware of whether a specific block is an odd-valued or even-valued segment:

```java
public static abstract class BlockSegment {
    private int rawValue;
    private int positionIndex;

    public BlockSegment(int rawValue, int positionIndex) {
        this.rawValue = rawValue;
        this.positionIndex = positionIndex;
    }
    public int getRawValue() { return rawValue; }
    public abstract double calculateSortWeight();
}
```

#### Inheritance
Inheritance is applied in two major areas of the system:
1. Subclasses `OddSegment` and `EvenSegment` extend the base class `BlockSegment`, inheriting its attributes (`rawValue` and `positionIndex`) while providing unique implementations of the sorting weight calculation.
2. The main application class `ChromaCascadeApp` inherits from `javafx.application.Application`, allowing it to plug into the JavaFX native lifecycle hooks (`start()`, `init()`, `stop()`).

```java
public static class OddSegment extends BlockSegment {
    public OddSegment(int rawValue, int positionIndex) {
        super(rawValue, positionIndex);
    }
    @Override
    public double calculateSortWeight() {
        return getRawValue() * 1.25;
    }
}
```

#### Polymorphism
Polymorphism allows the game grid to hold a mixed array of `BlockSegment` objects (containing both `OddSegment` and `EvenSegment` instances) and treat them uniformly. When the UI rendering loop needs to draw the weights above the blocks, it calls the overridden `calculateSortWeight()` method. The JVM dynamically dispatches the call to the appropriate subclass method at runtime, achieving clean polymorphic behavior:

```java
BlockSegment segment = row.getCurrentSet()[i];
// Dynamic dispatch resolves calculateSortWeight() based on runtime subclass:
double weight = segment.calculateSortWeight(); 
String weightStr = String.format("%.1f", weight);
gc.fillText(weightStr, x, y);
```

---

## 4. Program Implementation

### Description of Major Classes and Methods
1. **`ChromaCascadeApp`:** Set up the main JavaFX window stage, registers the global key listeners, initializes styling configurations, and controls switching between UI screens (Main Menu, Gameplay, and Leaderboards).
2. **`ChromaCascadeModel`:** Holds the active game variables, system status log lines, active puzzle grids, pre-calculated steps list, and theme color maps.
3. **`ChromaCascadeView`:** Manages the graphic double-buffered rendering. The `draw()` method executes coordinate interpolations:
   * **`visualXMap` & `visualYMap`:** Maps that store the current rendering coordinates of a block. If a block's position changes in the model, the view interpolates its coordinate by moving it towards the target position at a speed of 22% of the distance per frame, creating smooth sliding motions.
   * **`spawnParticles()` & `spawnFloatingText()`:** Spawn instances of the physics vector particle engine to draw explosions on input feedback.
4. **`ChromaCascadeController`:** Listens to keyboard actions (`A`, `D`, `ENTER`), runs the animation frame ticker, updates the countdown clock every second, and processes player actions via `executeShiftAction()`.
5. **`GridSorter`:** Contains the core theoretical algorithm sorters. Crucially, it provides tracing functions like `generateQuickSortSteps` which simulate the sort operations on a clone array and record the step-by-step positions for subsequent gameplay validation.
6. **`SoundManager`:** Running a background thread loop `mixerThread` with a `SourceDataLine` buffer, it mixes sine-wave outputs on-the-fly to play retro chime tones. It runs `musicThread` to sequence arpeggios that speed up dynamically as the timer counts down.
7. **`LeaderboardManager`:** Reads the comma-delimited high score data from `sort_pulse_scores.txt`, checks score qualifications, inserts new items sorted, and writes them back to the disk.

### Explanation of Program Workflow
```
+------------------+
|    App Start     |
+------------------+
         |
         v
+------------------+
|    Main Menu     | <----------------------------------+
+------------------+                                    |
         |                                              | (ESC key pressed)
         v (Select Algorithm & press Start)             |
+----------------------------------+                    |
|   Interactive Visual Guide       | -------------------+
+----------------------------------+
         |
         v (Press Enter/Right to Skip)
+----------------------------------+
|   Game Active (Timed/Practice)   |
|   - Grid spawns & steps traced   |
|   - Audio sequencer starts       |
+----------------------------------+
         |
         +<--------------------------------+
         |                                 |
         v (Keyboard inputs received)      |
+----------------------------------+       |
|   Evaluate Action vs. Step Trace |       | (Next step active)
+----------------------------------+       |
         |                                 |
         +---> [Correct Move] ------------>+
         |     - Play chime & particles
         |     - Add score / stack combo
         |
         +---> [Incorrect Move] ---------->+
         |     - Play buzz & red flash
         |     - Deduct score / time
         |
         v (All steps completed / Timer = 0)
+----------------------------------+
|   GameOver / Leaderboard Check   |
+----------------------------------+
         |
         v (Enter initials if qualified)
+----------------------------------+
|   High Score Saved to File       |
+----------------------------------+
         |
         v (Returns to Menu)
```

### Important Code Snippets and Discussions

#### 1. Interpolated Block Slide Animation
In `ChromaCascadeView.draw()`, block coordinates are smoothly animated using a linear interpolation formula:
$$\text{Current Position} = \text{Current Position} + (\text{Target Position} - \text{Current Position}) \times 0.22$$

```java
// Interpolate block coordinate map towards target grid slots
double targetX = startX + i * boxWidth;
double targetY = startY;

if (targetAlgo.equalsIgnoreCase("Merge Sort")) {
    // Merge Sort rendering maps blocks to dual levels (upper and lower)
    if (model.getSteps() != null && model.getCurrentStep() < model.getSteps().size()) {
        SortingStep step = model.getSteps().get(model.getCurrentStep());
        if (i < step.mergeTarget) {
            targetY = startY + 60; // Pulled down to merged output level
        } else {
            targetY = startY - 60; // Sitting in unmerged upper level
        }
    }
}

double currentX = visualXMap.getOrDefault(segment, targetX);
double currentY = visualYMap.getOrDefault(segment, targetY);

// Interpolate at 22% velocity per frame for smooth sliding animations
currentX += (targetX - currentX) * 0.22;
currentY += (targetY - currentY) * 0.22;

visualXMap.put(segment, currentX);
visualYMap.put(segment, currentY);
```

#### 2. Procedural Sine-Wave Tone Generator
The `SoundManager` generates 8-bit retro audio entirely in code without loading external `.wav` or `.mp3` files. This is accomplished by writing raw PCM byte streams to the Java sound mixer API. The math maps a sine wave over a sequence of audio samples:
$$\text{Sample Value} = \sin(\text{Phase}) \times \text{Volume} \times \text{Envelope}$$

```java
double sampleVal = Math.sin(tone.phase) * tone.volume * envelope;
tone.phase += tone.phaseStep;
if (tone.phase > 2.0 * Math.PI) {
    tone.phase -= 2.0 * Math.PI;
}

// Linear stereo panning factors
double leftFactor = Math.max(0.0, Math.min(1.0, 1.0 - tone.pan));
double rightFactor = Math.max(0.0, Math.min(1.0, 1.0 + tone.pan));

leftSum += sampleVal * leftFactor;
rightSum += sampleVal * rightFactor;
```

#### 3. Lomuto Quick Sort Partition Tracer
The `GridSorter` pre-computes the sequence of steps for validation. The Lomuto partition is traced by recording indices at each comparison and swap:

```java
private static void quickSortTraceSteps(BlockSegment[] array, int low, int high, List<SortingStep> steps, boolean[] finalized, int[] sortedVals) {
    if (low < high) {
        int pi = partitionTrace(array, low, high, steps, finalized, sortedVals);
        quickSortTraceSteps(array, low, pi - 1, steps, finalized, sortedVals);
        quickSortTraceSteps(array, pi + 1, high, steps, finalized, sortedVals);
    } else if (low == high) {
        finalized[low] = true;
    }
}

private static int partitionTrace(BlockSegment[] array, int low, int high, List<SortingStep> steps, boolean[] finalized, int[] sortedVals) {
    BlockSegment pivot = array[high];
    int i = low - 1;
    for (int j = low; j < high; j++) {
        // Record comparison step
        SortingStep compStep = new SortingStep();
        compStep.activeLeft = low;
        compStep.activeRight = high;
        compStep.pivotIndex = high;
        compStep.nextCursor = j;
        compStep.description = "Compare block at " + j + " to pivot.";
        steps.add(compStep);

        if (array[j].getRawValue() <= pivot.getRawValue()) {
            i++;
            // Record swap action step if values are partitioned
            SortingStep swapStep = new SortingStep();
            swapStep.correctIndex = j;
            swapStep.targetIndex = i;
            swapStep.activeLeft = low;
            swapStep.activeRight = high;
            swapStep.pivotIndex = high;
            swapStep.description = "Value <= pivot. Swap to target slot.";
            steps.add(swapStep);

            // Execute actual swap on trace array
            BlockSegment temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
    // Record final pivot swap step
    SortingStep pivotStep = new SortingStep();
    pivotStep.correctIndex = high;
    pivotStep.targetIndex = i + 1;
    pivotStep.activeLeft = low;
    pivotStep.activeRight = high;
    pivotStep.pivotIndex = high;
    pivotStep.description = "Swap pivot to its resolved index.";
    steps.add(pivotStep);

    BlockSegment temp = array[i + 1];
    array[i + 1] = array[high];
    array[high] = temp;

    return i + 1;
}
```

---

## 5. Algorithm Analysis

To evaluate the efficiency of the sorting algorithms implemented, we analyze their time and space complexities. This highlights the performance differences between the algorithms that players experience interactively in the game.

### Time Complexity

| Algorithm | Best-Case Complexity | Average-Case Complexity | Worst-Case Complexity |
|---|---|---|---|
| **Selection Sort** | $\mathcal{O}(n^2)$ | $\mathcal{O}(n^2)$ | $\mathcal{O}(n^2)$ |
| **Bubble Sort** | $\mathcal{O}(n)$ | $\mathcal{O}(n^2)$ | $\mathcal{O}(n^2)$ |
| **Insertion Sort** | $\mathcal{O}(n)$ | $\mathcal{O}(n^2)$ | $\mathcal{O}(n^2)$ |
| **Quick Sort** | $\mathcal{O}(n \log n)$ | $\mathcal{O}(n \log n)$ | $\mathcal{O}(n^2)$ |
| **Merge Sort** | $\mathcal{O}(n \log n)$ | $\mathcal{O}(n \log n)$ | $\mathcal{O}(n \log n)$ |

#### Selection Sort
Selection Sort scans the unsorted subarray to find the minimum element and swaps it to the front. Since it always searches the remaining unsorted elements regardless of their initial order, it performs exactly $\frac{n(n-1)}{2}$ comparisons. Therefore, it exhibits a time complexity of $\mathcal{O}(n^2)$ across all cases (best, average, and worst).

#### Bubble Sort
Bubble Sort repeatedly compares and swaps adjacent elements. In the best case, where the array is already sorted, the algorithm terminates after a single pass of $n-1$ comparisons without swaps, yielding a complexity of $\mathcal{O}(n)$. In the average and worst cases (e.g., a reverse-sorted array), it continues swapping elements, resulting in a complexity of $\mathcal{O}(n^2)$.

#### Insertion Sort
Insertion Sort inserts each element into its correct position in the sorted subarray. In the best case (already sorted), it makes one comparison per element, running in $\mathcal{O}(n)$ time. In the average and worst cases, it must shift elements repeatedly, leading to a quadratic complexity of $\mathcal{O}(n^2)$.

#### Quick Sort (Lomuto Partition)
Quick Sort partitions elements around a pivot. In the best and average cases, the pivot splits the array into roughly equal halves, resulting in a depth of recursion of $\mathcal{O}(\log n)$ and an overall complexity of $\mathcal{O}(n \log n)$. In the worst case, where the pivot consistently splits the array unevenly (e.g., already sorted or reverse-sorted array using the last element as pivot), the recursion depth reaches $\mathcal{O}(n)$, causing the complexity to degrade to $\mathcal{O}(n^2)$.

#### Merge Sort
Merge Sort divides the array into halves, recursively sorts them, and merges the sorted halves. Because it division-splits the array log-wise and then merges all elements at each level, it consistently requires $\mathcal{O}(n \log n)$ time in all cases, making it highly predictable and stable.

---

### Space Complexity

| Algorithm | Auxiliary Space Complexity | Stability |
|---|---|---|
| **Selection Sort** | $\mathcal{O}(1)$ | Unstable |
| **Bubble Sort** | $\mathcal{O}(1)$ | Stable |
| **Insertion Sort** | $\mathcal{O}(1)$ | Stable |
| **Quick Sort** | $\mathcal{O}(\log n)$ | Unstable |
| **Merge Sort** | $\mathcal{O}(n)$ | Stable |

* **Selection, Bubble, and Insertion Sort** are in-place algorithms requiring constant auxiliary space $\mathcal{O}(1)$.
* **Quick Sort** requires $\mathcal{O}(\log n)$ auxiliary space in the average case to manage the recursion call stack. In the worst case, this stack can grow to $\mathcal{O}(n)$.
* **Merge Sort** requires $\mathcal{O}(n)$ auxiliary space to allocate temporary arrays during the merge phase. This represents its primary trade-off relative to in-place sorting techniques.

---

### Discussion of Efficiency and Performance
During interactive gameplay, the mathematical complexity of these algorithms translates directly into player effort:
* **Bubble Sort** requires the most manual operations. In a grid of 12 elements, a highly unsorted array can require dozens of adjacent swaps, making Bubble Sort gameplay feel long and demanding.
* **Selection Sort** requires exactly $n-1$ steps (swaps) regardless of the array's state. While simple to play, it lacks early-termination shortcuts, meaning its gameplay length is fixed and predictable.
* **Insertion Sort** is highly efficient when the array is nearly sorted, requiring very few shift actions. However, a reverse-sorted array forces the player to shift every element all the way to the left, which can be exhausting.
* **Quick and Merge Sort** use divide-and-conquer strategies. While they are mathematically faster for computers, they require more complex decision-making from players. In Merge Sort, players must perform double-level head comparisons, which requires more cognitive effort than the straightforward linear scanning of Selection Sort.

---

## 6. Results and Discussion

### Program Outputs and Screenshots
The dual-aesthetic design of **Sort Pulse** allows players to customize their visual experience:

1. **Classic Neon Theme (Default):**
   * Features a vibrant, high-contrast dark interface suitable for modern gaming environments.
   
   ![Classic Neon Theme](classic_neon_gameplay.png)

2. **GameBoy Retro Theme:**
   * Simulates a classic DMG LCD screen with desaturated green and olive tones, catering to a retro arcade aesthetic.
   
   ![GameBoy Retro Theme](gameboy_retro_gameplay.png)

### Discussion of How the Program Achieves Its Objectives
The combination of game mechanics, visual indicators, and real-time audio feedback successfully achieves the goal of teaching sorting algorithms:
* **Interactive Guides:** The interactive tutorial overlay displays animated slideshows of the sorting mechanics before gameplay starts, ensuring players understand the rules.
* **Real-time Feedback:** Sound cues and color-coded particles provide immediate feedback. Green sparks and upbeat sound sweeps validate correct sorting steps, while red flashes and buzzing tones flag errors, helping players self-correct.
* **Timed Blitz Mode:** The countdown timer adds time pressure that rewards quick, accurate algorithmic decisions. Players cannot rely on trial and error without losing points and time.
* **Practice Mode:** Provides a stress-free environment for beginners to study the algorithms at their own pace without penalties.

### Observations and Findings
* **Bubble Sort Traversal Challenge:** Players frequently struggled to keep track of the unsorted boundary in Bubble Sort. Because the active boundary box spans the entire unsorted partition, players had to manually track their traversal path. This highlights the inefficiency of Bubble Sort compared to other algorithms.
* **Merge Sort Split Scanning:** Merge Sort's double-level layout was highly effective at teaching the merge operation. Players could visually see how elements are compared at the heads of Subarray A and B and pulled down to form a single sorted array.
* **Lomuto Partition Visual Cues:** In Quick Sort, the combination of the pivot badge, comparison symbols, and target arrows made Lomuto partitioning easy to follow. Players could easily visualize how the array is split into elements smaller than the pivot and elements larger than the pivot.

---

## 7. Conclusion

### Summary of the Project
**Sort Pulse** successfully gamifies standard sorting algorithms by combining a JavaFX rendering engine with retro audio synthesis and local leaderboard tracking. The application translates the theoretical operations of Selection, Bubble, Insertion, Quick, and Merge Sort into engaging gameplay mechanics. By validating player inputs against pre-computed algorithm traces, the system provides an effective interactive platform for learning data structures and algorithms.

### Key Learnings and Insights
* **State Management in Game Loops:** Implementing pre-computed sorting step lists (`SortingStep`) decoupled the game loop and validation engine from the active array state, preventing synchronization issues and ensuring consistent gameplay.
* **Procedural Audio Engineering:** Generating 8-bit sound effects directly via raw byte streams demonstrated that immersive audio does not require external media assets. This approach kept the application lightweight and simplified distribution.
* **MVC Pattern Utility:** Separating the game state (`ChromaCascadeModel`) from the keyboard handlers (`ChromaCascadeController`) and drawing routines (`ChromaCascadeView`) made the code easier to maintain and extend, enabling features like Practice Mode and theme toggles to be implemented with minimal changes.

### Recommendations for Future Improvements
1. **Networked Leaderboards:** Replace the flat-file `sort_pulse_scores.txt` with a remote web API database to support global leaderboards, encouraging competition among players.
2. **Visual Algorithm Customizer:** Allow developers to write custom sorting algorithms in an in-game editor and automatically compile them into playable game modes.
3. **Advanced Visualizations:** Implement additional data structures, such as Heaps or Binary Trees, to expand the game's educational scope.
4. **Mobile Deployment:** Port the game to Android and iOS using Gluon Mobile, making it accessible to students on mobile devices.

---

## 8. Individual Reflection

### Group Member: Japoy (Lead Software & Game Developer)

#### Assigned Role and Responsibilities
In this project, I served as the **Lead Software Engineer and Game Mechanics Designer**. My primary responsibilities encompassed:
*   **System Architecture:** Designing and setting up the Model-View-Controller (MVC) pattern to separate raw data logic, canvas rendering loops, and user input validation.
*   **Engine Development:** Developing the 60 FPS visual rendering engine on JavaFX's `2D Canvas` and creating the custom procedural audio synthesizer.
*   **Algorithmic Integration:** Implementing the theoretical tracing algorithms that record every partition, pivot, comparison, and swap step to validate player keystrokes.
*   **Build Pipeline Configuration:** Setting up the native desktop deployment scripts (`run.bat`, `package.bat`) using `jlink` and `jpackage` to bundle a custom JRE and JavaFX runtime.

#### Contributions Made During Development
My key technical contributions to the project included:
*   **Algorithmic Tracer (`GridSorter`):** Wrote sorting step simulators for Selection, Bubble, Insertion, Lomuto Quick, and double-level Merge Sort, ensuring the system pre-computes valid state lists (`List<SortingStep>`) before each gameplay round.
*   **Procedural Synth (`SoundManager`):** Configured a low-latency thread using standard Java audio mixers (`javax.sound.sampled`) that synthesizes 8-bit sound effects (chimes, buzzes) and dynamic arpeggios on-the-fly, scaling the background music tempo under time pressure.
*   **Interpolated Rendering Loop (`ChromaCascadeView`):** Programmed coordinate interpolation mapping (`visualXMap`, `visualYMap`) with a $22\%$ frame-to-frame velocity multiplier to create smooth block slide animations.
*   **Theme Engine & UI Polish:** Designed and implemented the dark-mode **Classic Neon** theme and the monochrome **GameBoy Retro** theme, overriding JavaFX font antialiasing with grayscale subpixel rendering to maintain pixel-art font crispness.

#### Challenges Encountered and Resolutions
*   **Visual-Model Desynchronization:** During fast keyboard inputs, the visual block maps would occasionally lag behind model array modifications, causing index mismatch crashes.
    *   *Resolution:* I implemented a **Safety Overflow Stack** in `PuzzleRow` to temporarily hold out-of-bounds segments, combined with a synchronized lock on input evaluation events to prevent concurrent modification exceptions.
*   **Audio Thread Jitter and Latency:** Running standard file-based clip players (`.wav` or `.mp3`) resulted in significant lag when playing multiple simultaneous collision and success sounds.
    *   *Resolution:* I bypassed traditional clip player libraries entirely. I engineered a dedicated mixer thread running a continuous sample writing loop into a single `SourceDataLine` buffer, mixing multiple wave channels using simple phase additions and exponential decay envelopes.
*   **Hi-DPI Scaling Blur:** Pixel-art fonts loaded in JavaFX often became fuzzy or blurred on high-resolution Windows screens.
    *   *Resolution:* Overrode the scaling behaviors by enforcing integer-point font sizes, and disabled standard subpixel anti-aliasing in retro theme modes to force crisp pixel alignment.

#### Knowledge and Skills Gained
*   **Data Structures:** Gained practical knowledge of managing polymorphic arrays (`BlockSegment[]`) containing mixed subclass elements, and handling flat-file data persistence for local leaderboard storage.
*   **Algorithms:** Dissecting five distinct sorting algorithms to write step-by-step state loggers deepened my understanding of partition boundaries, comparison heads, and shifting pointers.
*   **Object-Oriented Programming (OOP):** Applied core OOP principles in a real-world system: encapsulating game metrics in `ChromaCascadeModel`, abstracting block structures via `BlockSegment`, inheriting lifecycle hooks from JavaFX `Application`, and using runtime polymorphism to calculate and display sort weights dynamically.
*   **Iterative Prototyping & Profiling:** Learned to rigorously test and optimize rendering and sound loops, verifying mathematical phase synthesis formulas, bench-testing frame-rate stability, and systematically debugging complex array swap traces.

#### Personal Insights and Lessons Learned
*   **Architectural Cleanliness Pays Off:** Decoupling the game state from the rendering canvas using the MVC pattern was crucial. It allowed me to introduce new UI themes and practice modes without risking regression errors in the core algorithm evaluation code.
*   **UX/UI Matters in Educational Software:** Designing interactive tutorial slides and the stress-free Practice Mode proved that educational tools need more than just accurate logic—they require game flow mechanics that accommodate different learning paces to be truly effective.
