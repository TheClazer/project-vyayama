# CLAUDE.md — Vyāyāma project context

> **Read this first** at the start of any session. Cliff-notes for `docs/bible.md`.

---

## What this project is

**Vyāyāma** (व्यायाम, "physical exercise") is an **on-device AI Form Coach** for the **FitSense** problem
statement (Hack4SoC 3.0, **Qualcomm Edge AI Track**). It runs real-time **pose → exercise recognition →
rep counting → per-rep form scoring → one-cue coaching** on a Snapdragon device (QIDK / RB3 Gen 2 /
QCS6490), fully on-device, no cloud, plus a **live CPU-vs-NPU latency comparison**.

The full spec is `docs/bible.md`. **The bible wins** if anything you remember contradicts it. Update the
bible in the same commit as any decision change.

> **Predecessor:** *Pramāṇa* (deepfake detection, `D:\Work\project-pramana`) is a **separate, shelved**
> project for the *same* hackathon. **Reuse its patterns, not its domain code** (see §"How to work").

---

## Who the user is
- **Rayyan** (GitHub **TheClazer**), Windows 11, PowerShell, repo at `C:\Users\Rayyan Shaikh\Desktop\vyayama\project-vyayama` (remote `github.com/TheClazer/project-vyayama`).
- Team of 3 (tracks A/B/C below). Hardware in hand: iQOO Z5, Moto Edge 50 Fusion (for CPU/GPU dev; NPU needs the venue device). Workshop June 5, 1:30 PM.
- Mandate: **win, no excuses; be honest; no hallucinated numbers; no hidden catch that eats all the time; build everything device-free BEFORE the hackathon.**

---

## The strategic fact that shapes everything
Qualcomm handed every team the working reference **`VisionSolution4-PoseEstimation`** (`github.com/quic/qidk`):
YoloNAS person-detect → HRNet → **17 COCO keypoints** on the **Hexagon NPU via SNPE C++**, with a built-in
CPU/GPU/DSP toggle. So pose-on-NPU is **table stakes**. **We fork it, keep the native core verbatim, and
build the layer it stops at** — the biomechanics + coaching engine. *The reference ends at keypoints; our
product begins there.* Be honest about this (the "given-vs-built" ledger, bible §3) — it disarms the
"you reskinned the sample" attack.

---

## How to work on this codebase

### Three governing rules (always)
1. **Default to the bible.** `docs/bible.md` wins.
2. **Each engineer owns their track.** Keep module boundaries clean so failures don't cascade.
3. **Independence is non-negotiable.** Every interface has a **mock**; the app runs with **any subset** real.

### The non-negotiable vows
- **No invented numbers** — latency/FPS/accuracy is measured-on-device or labelled "not yet."
- **Honest `backend()`** — report the tier actually reached; never claim NPU on a fallback.
- **Zero per-frame allocation** — pre-allocate + reuse; **no per-frame JNI object marshalling**.

### Reuse Pramāṇa PATTERNS, not its code
Steal the skeleton: `Engine`-interface-with-mock, hand-rolled DI with per-interface mock-fallback
(`PramanaApp.kt`), zero-alloc CameraX YUV (`RealCameraEngine.kt`), honest `backend()` + `forceBackend()`
live A/B (`TfliteRunner.kt`), "throttle expensive work to every Nth frame," the bible/Vibe-vs-Manual
governance. **Do NOT reuse the inference seam** — Pramāṇa is TFLite + QNN *delegate* (Java); Vyāyāma is
SNPE/QAIRT *C++ API* through JNI. Different SDK, different failure modes.

### Vibe-code vs Manual (bible §19)
- ❌ **MANUAL:** SNPE C++ init/call-flow, JNI marshalling/the seam, DLC conversion+quantization
  (`GenerateDLC.ipynb`), CMake/NDK r26c + OpenCV linking, skel bundling + `setenforce` device prep,
  affine-warp + ImageNet-norm + NMS (keep reference verbatim).
- ⚠️ **VIBE-with-verify:** CameraX YUV, Compose + interop, Canvas skeleton, Gradle/version-catalog,
  threshold *tuning*, MediaPipe/AI-Hub model swap.
- ✅ **VIBE-safe:** the pure-Kotlin intelligence layer, all mocks, data classes, unit tests, the PyTorch
  classifier + dataset prep + export, Benchmark math, docs.

---

## The seam (the most important interface)
Native returns per frame **one flat `float[17*3 + 5] = float[56]`**: 51 keypoint floats (x,y,conf,
source-frame px) + `[backendCode, detMs, poseMs, srcW, srcH]`. Above it: no tensors/DLCs. Below it: no
reps/exercises. → the whole intelligence layer is JVM-unit-testable on recorded keypoint CSVs.

---

## Module ownership (3 tracks)

| Track | Owner | Scope | PS |
|---|---|---|---|
| **A "Metal"** | device/pose/NPU | fork core (verbatim cpp), the seam, `SnpePoseEngine`, DLC/Path-B, device prep, `Benchmark` + CPU-vs-NPU | P1 on-device, all P2 |
| **B "Brain"** | exercise intelligence | `FeatureExtractor`, Rule/Learned/Fused classifier, `StateMachineRepCounter`, `RuleFormAnalyzer`, `ml/` | P1 recognition, P3 logic |
| **C "Glue→App"** | integration + frontend LAST | `CoachOrchestrator`, `CameraEngine`, DI (`App.kt`), CI, tools, **Compose UI** | P3 app |

Interfaces: `PoseEngine · CameraEngine · FeatureExtractor · ExerciseClassifier · RepCounter · FormAnalyzer ·
Benchmark` → `CoachOrchestrator` → `StateFlow<CoachState>` → Compose UI.

Exercise set (Core 5, front-camera, bodyweight): **squat, push-up, lunge, bicep curl, jumping jack.**

---

## STATUS

### ✅ Done (device-free, keyboard work)
- `docs/bible.md` — full master spec, rated 94.2/100 over 3 brutal iterations (self-audit log in App. D).
- `docs/setup-checklist.md`, `docs/device-runbook.md`, this `CLAUDE.md`.
- *(in progress)* repo scaffold + frozen `api/` → mocks/DI/orchestrator → intelligence layer + tests →
  Compose UI (mock-mode) → ml pipeline → tools/CI. See the task list / bible Appendix E build checklist.

### 🔴 Gated on the kit / user actions (see HANDOFF / setup-checklist)
- WSL2+Docker or AI Hub account (model); Android Studio Panda 4 v2025.3.4; fork-build on a phone.
- Device-day: confirm SoC → correct-arch DLC + skels → `adb root`/`setenforce 0` → NPU validation + REAL
  latency numbers → board (USB-cam/HDMI) adaptation if RB3/QCS6490.

### Where to resume
1. Read this + bible §0–7. 2. Check the task list. 3. Follow bible **Appendix E** build-order checklist.
4. Every interface has a mock — `:app:assembleDebug` runs the full demo in mock mode with no kit.

---

## What NOT to do
- **Don't** rewrite the SNPE/JNI/CMake core "to clean it up" — it's debugged; touching it loses a day.
- **Don't** invent latency numbers. Only `adb`/QAIRT profiling on real hardware.
- **Don't** allocate or marshal Java objects per frame.
- **Don't** fabricate keypoint values for low-confidence joints — gate and skip.
- **Don't** claim NPU when you fell back — `backend()` reports the truth.
- **Don't** add cloud/network in the prod path — on-device is the PS requirement + the pitch.
- **Don't** hard-pin a single Hexagon version — bundle all skels; stay device-agnostic.
- **Don't** treat mock mode as a shortcut — it's stage-rescue insurance, not a dev default.
