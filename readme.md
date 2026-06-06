<div align="center">

# व्यायाम · Vyāyāma

### An on-device AI exercise coach — **private, offline, real-time.**

Your phone watches your form, counts every rep, scores it, and **talks you through it** — with the camera feed **never leaving the device.**

![Hack4SoC 3.0 — Qualcomm Edge AI](https://img.shields.io/badge/Hack4SoC%203.0-Qualcomm%20Edge%20AI-151a17?style=flat-square)
![Snapdragon · Hexagon NPU](https://img.shields.io/badge/Snapdragon-Hexagon%20NPU-C8FF3C?style=flat-square&labelColor=151a17)
![100% offline](https://img.shields.io/badge/100%25-offline%20·%20no%20INTERNET%20permission-2C5721?style=flat-square)
![tests 123/123](https://img.shields.io/badge/tests-123%2F123%20passing-3C9A1E?style=flat-square)
![Android · Java 17](https://img.shields.io/badge/Android-Java%2017-555?style=flat-square)

</div>

---

## TL;DR

Qualcomm's reference app stops at **17 pose keypoints on the NPU.** **Vyāyāma begins there** — turning those keypoints into a full coach that recognises the exercise on its own, counts reps, scores each one 0–100, and **speaks** the count and corrective cues every rep, in a soft voice. All of it runs on the **Snapdragon Hexagon NPU** with **no network, no backend, no cloud** — the app doesn't even request the `INTERNET` permission.

---

## ✨ What it does

| | |
|---|---|
| **Sense** | YOLO-NAS person detector → HRNet 17-keypoint pose, **INT8 on SNPE / Hexagon NPU**; one-tap GPU/CPU fallback. |
| **Recognise** | Auto-detects **7 exercises** — squat · push-up · bicep curl · jumping jack · shoulder press · sit-up · plank. |
| **Count & score** | Real-time rep counting + a **0–100 form score** every rep + a live, colour-coded coaching cue. |
| 🔊 **Voice coach** | Speaks **every rep out loud** — the count *and* a per-rep cue (*"three… go a little deeper… ten, great work!"*) in a soft on-device voice. Built **eyes-off**, for when you're across the room and never looking at the screen. |
| **Manual mode** | Pin one exercise so it can **never** be misread. |
| **Offline profiles** | Personal bests, lifetime totals, daily streaks, a PB reward banner, and **daily reminders** (local notifications). |
| **Coach Vision** | A live overlay of the exact signals the engine is sensing — full transparency. |
| **Private** | No `INTERNET` permission. Nothing ever leaves the device. |

---

## 🧠 How we solved the hard parts

> Most "pose → count" demos break the moment a real person trains in a real room. This is where the work went.

- **Recognition under flicker & noise** — a sticky, *self-correcting* lock + a **One-Euro** keypoint filter + teleport/dropout rejection. Reps survive jitter, and a wrong first guess corrects itself in ~0.6 s.
- **Counting that fits real bodies** — a two-threshold rep state-machine + **adaptive per-user range calibration** + **peak/valley completion**, so partial-range and "didn't fully lock out" reps still count — and *fast* reps at a low frame-rate are never dropped.
- **Camera-angle robustness** — a viewpoint-stable **hip-drop** signal counts foreshortened, front-on squats that a knee angle alone misses.
- **Zero misreads** — *positive-evidence gates*: a sit-up's trunk-fold can never be mistaken for a bicep curl or a shoulder press.
- **Real-time on a phone** — **zero heap allocation per camera frame** (pre-allocated ring buffers) so the GC never stutters mid-rep; INT8 on the NPU keeps it fast and battery-light.
- **Storage treated like CPU memory** — profile stats (PBs, totals, streaks) load into a **write-back RAM buffer** on open and flush to flash **once, on close**; zero per-rep disk writes → less flash wear, lighter battery.
- **A voice tuned for eyes-off training** — it speaks the rep count *and* a short correction on every rep, rotates the phrasing so it never sounds robotic, and hypes you on every 10th. Counting out loud means you never have to look at the screen.
- **Proven, not hand-waved** — the entire brain (`VyayamaCoach` + `VoiceCoach`) is **pure Java** and validated by a **123-assertion offline test harness** that runs in milliseconds, with no device.

---

## 🏗️ Architecture

```
          ┌───────────────── 100% ON-DEVICE · NO INTERNET ─────────────────┐
 Camera ─▶ │  Hexagon NPU (SNPE)         Signal               VyāyamaCoach   │ ─▶ 🔊 Voice (on-device TTS)
 YUV·30fps │  YOLO-NAS → HRNet    ─▶  One-Euro smoothing  ─▶  recognise · rep │ ─▶ 🖥  HUD  (reps · form · cue)
           │  17 keypoints · INT8     + 13 biomech feats     FSM · form score │ ─▶ 💾 Profiles (PB · streak)
           └─────────────────────────────────────────────────────────────────┘
                                   ↻  profiles + per-user calibration personalise each session
```

The whole brain — `VyayamaCoach` (recognition + reps + form) and `VoiceCoach` (cadence) — is **pure Java with zero Android imports**, so it compiles and runs under a `javac` harness with **no device, no NPU, no camera.** That's why it can be exhaustively unit-tested.

---

## 📂 Repo layout

```
android-device-app/        ← the shipped app  (Qualcomm QIDK · package com.qc.posedetectionYoloNAS)
  app/src/main/java/…/        VyayamaCoach · VoiceCoach · VoicePlayer · VoicePrefs · VoiceSettingsDialog
                              ModePickerDialog · CameraFragment · FragmentRender · ProfileStore · Reminder*
  app/src/main/res/           Volt theme · drawables · layouts
  tools/coach_harness/        CoachHarness.java  — the 123-assertion pure-Java test suite
android/                   ← device-free Kotlin reference (intelligence layer + unit tests + mocks)
ml/                        ← optional learned classifier + Python⇄Kotlin feature-parity contract
docs/                      ← bible.md (design source of truth) + runbooks
tools/                     ← bible PDF builder · threshold tuner
```

---

## 🔬 Tested — 123 / 123, no device needed

The rep + voice engine is pure Java, so the full suite runs in seconds:

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

Covers: angle math, the rep FSM under jitter / noise / 2× scale / translation / joint dropout, partial & too-fast rejection, all 7 exercises, adaptive ROM, manual mode, the sit-up-vs-shoulder-press fix, and the voice coach (speaks every rep, count + per-rep comment, milestones, praise, silent when off).

Device-free Kotlin reference:
```bash
cd android && ./gradlew :app:testDebugUnitTest
```

---

## ▶️ Build & run

**The app** — open `android-device-app/` in **Android Studio**, build `:app`, install on a Snapdragon device. Pose runs on the **Hexagon NPU** via SNPE: drop the device-matched `.dlc` models + Hexagon runtime libs (see `docs/`), or switch the backend to **GPU / CPU** live from the in-app 3-dot menu. The voice coach uses the device's built-in **TextToSpeech** — offline, no download on most devices.

---

## 🔒 Privacy by construction

The manifest declares **no `INTERNET` permission** — there is no code path that can reach the network. No backend, no analytics, no cloud. Camera frames are processed on the NPU and discarded; only your counts, PBs and streak live locally in `SharedPreferences`, and the coaching voice is synthesised on-device.

---

## 👥 Team

**Team Vyāyāma** — **Rayyan Shaikh** (lead) · **Ashitha Patil** · **Vaibhav Rathod**
**R.V. College of Engineering (RVCE), Bengaluru**
Hack4SoC 3.0 · On-Device / Edge AI (Qualcomm)

---

<div align="center">

*Vyāyāma (व्यायाम) — Sanskrit for "exercise."  ·  Your phone already has the hardware; we just turned it into a coach.*
*Form guidance, not medical advice.*

</div>
