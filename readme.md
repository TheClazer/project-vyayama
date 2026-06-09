<div align="center">

# व्यायाम · Vyāyāma

### An on-device AI exercise coach — private, offline, real-time.

Your phone watches your form, counts every rep, scores it 0–100, and **talks you through the set** — while the camera feed **never leaves the device**.

![Hack4SoC 3.0 — Qualcomm Edge AI](https://img.shields.io/badge/Hack4SoC%203.0-Qualcomm%20Edge%20AI-151a17?style=flat-square)
![Snapdragon · Hexagon NPU](https://img.shields.io/badge/Snapdragon-Hexagon%20NPU-C8FF3C?style=flat-square&labelColor=151a17)
![100% offline](https://img.shields.io/badge/100%25-offline%20·%20no%20INTERNET%20permission-2C5721?style=flat-square)
![tests 123/123](https://img.shields.io/badge/tests-123%2F123%20passing-3C9A1E?style=flat-square)
![Android · Java 17](https://img.shields.io/badge/Android-Java%2017-555?style=flat-square)

**[Features](#features) · [Why it's different](#why-its-different) · [How it works](#how-it-works) · [Architecture](#architecture) · [Tested](#tested) · [Build and run](#build-and-run) · [Team](#team)**

</div>

---

> **Qualcomm's reference app renders 17 pose keypoints on the NPU — and stops there.**
> Vyāyāma treats those keypoints as the starting line: it recognises the exercise on its own, counts reps that survive a real workout, scores every rep, and **speaks** the count and a correction out loud — all on the Snapdragon Hexagon NPU, with no network, no backend, no cloud.

## At a glance

| | |
|---|---|
| **Platform** | Android · Java 17 · built on the Qualcomm QIDK base (package `com.qc.posedetectionYoloNAS`) |
| **Inference** | YOLO-NAS person detector → HRNet 17-keypoint pose · **INT8 `.dlc`** on the **Snapdragon Hexagon NPU** via SNPE |
| **Runtime switch** | NPU / GPU / CPU, switchable live from the in-app menu |
| **Exercises** | 7 — auto-recognised, or manually pinned so they can't be misread |
| **Coaching** | rep count · 0–100 form score · live colour-coded cue · spoken voice on **every** rep |
| **Personalisation** | per-user range calibration · offline profiles, personal bests, streaks |
| **Storage** | local `SharedPreferences` behind a write-back RAM buffer — zero per-rep disk writes |
| **Network** | none. The manifest declares **no `INTERNET` permission** |
| **Tested** | **123 / 123** assertions, pure-Java harness, no device required |

---

## Features

| | |
|---|---|
| **Sense** | YOLO-NAS → HRNet, 17 keypoints, INT8 on SNPE / Hexagon NPU, with a one-tap GPU/CPU fallback. |
| **Recognise** | Auto-detects **7 exercises** — squat, push-up, bicep curl, jumping jack, shoulder press, sit-up, plank. |
| **Count and score** | Real-time rep counting, a **0–100 form score** every rep, and a live, colour-coded coaching cue. |
| **Voice coach** | Speaks **every rep out loud** — the count *and* a per-rep cue (*"three… go a little deeper… ten, great work"*) in a soft on-device voice. Built eyes-off, for when you're across the room and never looking at the screen. |
| **Manual mode** | Pin one exercise so it can never be misread — ideal for a noisy demo floor or an unusual camera angle. |
| **Offline profiles** | Personal bests, lifetime totals, daily streaks, a PB reward banner, and daily reminders via local notifications. |
| **Coach Vision** | A live overlay of the exact signals the engine is sensing — full transparency, nothing hidden. |
| **Private by construction** | No `INTERNET` permission. Camera frames are processed and discarded; nothing ever leaves the device. |

---

## Why it's different

Pose estimation is the easy 20%. The hard 80% is turning a noisy stream of keypoints into something that counts correctly for a real person, in a real room, at a real camera angle — and says something useful while doing it.

| | Qualcomm reference app | **Vyāyāma** |
|---|:---:|:---:|
| 17 pose keypoints on the NPU | ✓ | ✓ |
| Recognises which exercise you're doing | — | ✓ &nbsp;auto, 7 moves |
| Counts reps | — | ✓ &nbsp;robust state machine |
| Scores form 0–100 | — | ✓ &nbsp;every rep |
| Speaks coaching out loud | — | ✓ &nbsp;every rep, offline TTS |
| Adapts to your body and camera angle | — | ✓ &nbsp;per-user calibration |
| Profiles, personal bests, streaks | — | ✓ &nbsp;fully offline |
| Runs with no network | ✓ | ✓ &nbsp;(no `INTERNET` permission) |

---

## How it works

The camera streams YUV frames at 30 fps. Every frame passes through five stages — all on-device, all allocation-free:

1. **Detect and pose** — YOLO-NAS locates the person, HRNet regresses 17 keypoints. INT8, on the Hexagon NPU.
2. **Stabilise** — a **One-Euro filter** per keypoint smooths jitter without adding lag; teleport rejection drops physically impossible jumps; a brief gap-hold rides out short occlusions instead of flickering to zero.
3. **Extract features** — 13 biomechanical signals (joint angles, limb ratios, a viewpoint-stable hip-drop, vertical travel and cadence) are computed into pre-allocated buffers.
4. **Recognise** — a sticky, self-correcting classifier locks onto the current exercise from those signals, or honours a manually pinned one.
5. **Count, score, coach** — a two-threshold rep state machine with adaptive range calibration counts the rep; a form rule set scores it 0–100 and picks a cue; the voice layer speaks the count and the correction.

<details>
<summary><b>The rep state machine — why counting actually holds up</b></summary>

<br>

A naive "angle crosses a line" counter falls apart on real reps. This one is built to survive them:

- **Two thresholds** (enter-top ≈ 0.15, enter-bottom ≈ 0.85 of the normalised range) with hysteresis, so a trembling joint at the turnaround can't double-count.
- **Adaptive per-user calibration** — it learns your actual top and bottom from the first rep, so a limited range of motion or an unusual camera distance still counts correctly.
- **Peak/valley completion** — reps where you don't quite lock out at the top still register, instead of deadlocking the way a strict threshold would.
- **Partial- and too-fast-rejection** — half-reps and twitches below a minimum duration are ignored.
- **Multi-phase advance per frame** — fast reps at a low frame rate are never silently dropped.
- **NaN-freeze** — a missing joint freezes the rep state rather than corrupting the count.

</details>

<details>
<summary><b>Recognition that doesn't flicker</b></summary>

<br>

- A **sticky lock** holds the current exercise once confident, instead of relabelling every frame.
- It is fed the *smoothed* signal, not raw keypoints, so noise doesn't trigger spurious switches.
- **Positive-evidence gates** keep distinct moves distinct — a sit-up's trunk fold can never be mistaken for a bicep curl or a shoulder press.
- A wrong first guess **self-corrects in about 0.6 s**; a manually pinned exercise overrides recognition entirely.

</details>

<details>
<summary><b>A voice built for eyes-off training</b></summary>

<br>

- Speaks the **count and a short correction on every rep**, so you never have to look at the screen.
- Rotates its phrasing so it never sounds robotic; praises good streaks and calls out every tenth rep.
- A faulty rep triggers a targeted cue — depth, back sag, swing, range, tempo, posture, lockout, or symmetry.
- Fully **deterministic** (no random, no clock) and synthesised by the device's offline TextToSpeech, with a soft default voice and a bottom-left toggle plus settings.

</details>

---

## Engineering highlights

> The decisions that separate a demo that works on stage from one that works in a living room.

- **Camera-angle robustness** — a viewpoint-stable hip-drop signal counts foreshortened, front-on squats that a knee angle alone would miss.
- **Real-time on a phone** — **zero heap allocation per camera frame** (pre-allocated ring buffers), so the garbage collector never stutters mid-rep; INT8 on the NPU keeps it fast and battery-light.
- **Storage treated like memory** — profile stats load into a **write-back RAM buffer** on open and flush to flash once, on close. Zero per-rep disk writes means less flash wear and lighter battery.
- **Proven, not hand-waved** — the entire engine (`VyayamaCoach` + `VoiceCoach`) is **pure Java with zero Android imports**, validated by a **123-assertion offline harness** that runs in milliseconds, with no device, NPU, or camera.

---

## Architecture

```
 Camera           ┌────────────  100% ON-DEVICE · NO INTERNET  ────────────┐           Outputs
 YUV · 30 fps  ─▶ │  YOLO-NAS → HRNet   →  One-Euro filter  →  VyāyamaCoach  │ ─▶  Voice     · on-device TTS
                  │  17 keypoints, INT8    + 13 biomech feats   recognise·rep│     HUD       · reps, form, cue
                  │  on the Hexagon NPU                         FSM·form 0–100│     Profiles  · PB, streak
                  └─────────────────────────────────────────────────────────┘
                       ↻  per-user calibration + offline profiles personalise every session
```

The brain — `VyayamaCoach` (recognition, reps, form) and `VoiceCoach` (cadence) — is **pure Java with zero Android imports**, so it compiles and runs under a `javac` harness with no device, no NPU, and no camera. That is exactly what makes it exhaustively unit-testable.

---

## Exercises

| Exercise | Primary signal | Example spoken cue |
|---|---|---|
| **Squat** | knee flexion + viewpoint-stable hip-drop | *"go a little deeper"* |
| **Push-up** | elbow flexion + torso line | *"keep your back flat"* |
| **Bicep curl** | elbow flexion, upper arm held still | *"don't swing — control it"* |
| **Jumping jack** | arm/leg open-close cadence | *"full range, all the way up"* |
| **Shoulder press** | elbow extension overhead, wrists above shoulders | *"lock out at the top"* |
| **Sit-up** | hip and trunk flexion | *"all the way up"* |
| **Plank** | isometric hold, torso line (timer, not reps) | *"hold — hips level"* |

---

## Tech stack

| Layer | Choice |
|---|---|
| **Inference** | Qualcomm SNPE · Hexagon NPU · INT8 `.dlc` (GPU/CPU fallback) |
| **Models** | YOLO-NAS (person detection) · HRNet (17-keypoint pose) |
| **App** | Android · Java 17 · Camera2/CameraX YUV pipeline |
| **Smoothing** | One-Euro filter, teleport + dropout rejection |
| **Voice** | Android TextToSpeech, offline, deterministic cadence layer |
| **Storage** | `SharedPreferences` behind a write-back buffer |
| **Base** | Qualcomm QIDK · `VisionSolution4-PoseEstimation` |
| **Verification** | pure-Java `javac` harness, 123 assertions |

---

## Tested

The rep and voice engine is pure Java, so the full suite runs in seconds — no device, no emulator:

```bash
cd android-device-app/tools/coach_harness
javac -d out \
  ../../app/src/main/java/com/qc/posedetectionYoloNAS/VyayamaCoach.java \
  ../../app/src/main/java/com/qc/posedetectionYoloNAS/KeypointFilter.java \
  ../../app/src/main/java/com/qc/posedetectionYoloNAS/VoiceCoach.java \
  CoachHarness.java
java -cp out com.qc.posedetectionYoloNAS.CoachHarness
# →  PASSED 123 / 123
```

**Coverage:** angle math; the rep FSM under jitter, noise, 2× scale, translation, and joint dropout; partial- and too-fast-rejection; all 7 exercises; adaptive ROM; manual mode; the sit-up-vs-shoulder-press fix; and the voice coach (count plus per-rep comment, milestones, praise, and silence when off).

Device-free Kotlin reference suite:

```bash
cd android && ./gradlew :app:testDebugUnitTest
```

---

## Build and run

**Prerequisites** — Android Studio (recent), a Snapdragon device, and the device-matched SNPE `.dlc` models plus Hexagon runtime libraries (see `docs/device-runbook.md`).

1. Open `android-device-app/` in Android Studio and let Gradle sync.
2. Drop the `.dlc` models and Hexagon runtime libs into the locations described in `docs/`.
3. Build `:app` and install on the device.
4. Pose runs on the **Hexagon NPU** by default — switch to **GPU / CPU** live from the in-app menu. The voice coach uses the device's built-in **TextToSpeech**: offline, with no download on most devices.

No `.dlc`? The pure-Java engine and its full test suite run on any machine with a JDK — see [Tested](#tested).

---

## Project structure

```
android-device-app/        the shipped app  (Qualcomm QIDK · package com.qc.posedetectionYoloNAS)
  app/src/main/java/…/        VyayamaCoach · VoiceCoach · VoicePlayer · VoicePrefs · VoiceSettingsDialog
                              ModePickerDialog · CameraFragment · FragmentRender · ProfileStore · Reminder*
  app/src/main/res/           Volt theme · drawables · layouts
  tools/coach_harness/        CoachHarness.java — the 123-assertion pure-Java test suite
android/                   device-free Kotlin reference (intelligence layer, unit tests, mocks)
ml/                        optional learned classifier + Python⇄Kotlin feature-parity contract
docs/                      bible.md (design source of truth), runbooks, pitch deck, report
tools/                     bible PDF builder · threshold tuner
```

---

## Privacy by construction

The manifest declares **no `INTERNET` permission**, so no code path can reach the network — no backend, no analytics, no cloud. Camera frames are processed on the NPU and discarded; only your counts, personal bests, and streak live locally in `SharedPreferences`, and the coaching voice is synthesised on-device.

---

## Roadmap

- Real per-joint confidence sourced from the HRNet heatmaps (currently visibility + motion-consistency).
- A YOLO11-pose model option alongside the YOLO-NAS → HRNet pipeline.
- More movements, and richer per-exercise form rubrics.

---

## Team

**Team Vyāyāma** — **Rayyan Shaikh** (lead) · **Ashitha Patil** · **Vaibhav Rathod**
R.V. College of Engineering (RVCE), Bengaluru
Hack4SoC 3.0 · On-Device / Edge AI (Qualcomm)

---

<div align="center">

*Vyāyāma (व्यायाम) — Sanskrit for "exercise."  ·  Your phone already has the hardware; we just turned it into a coach.*

*Form guidance, not medical advice.*

</div>
