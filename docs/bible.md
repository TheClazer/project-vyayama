# Vyāyāma — Project Bible

> **Vyāyāma** (व्यायाम, Sanskrit: *"physical exercise / disciplined training"*).
> On-device, real-time **AI Form Coach** for Snapdragon. Skeletons in → coaching out.
>
> Hack4SoC 3.0 · **Qualcomm Edge AI Track** · Problem Statement: **FitSense — Real-Time Exercise
> Detection & Recognition** · Target hardware (organizer-confirmed): **Qualcomm QIDK · Snapdragon 8
> Elite (SM8750, Hexagon V79) · Android**. *(The PS image's "RB3 Gen 2 / QCS6490" was an erratum.)*
>
> **Version 1.1.** Single source of truth — if anything you remember contradicts the bible, **the
> bible wins**. If a decision changes, update the bible in the same commit.
>
> **v1.1 — DEVICE CONFIRMED:** organizers confirmed the venue device is a **QIDK with Snapdragon 8
> Elite**, running **Android** (ADB via Android Studio platform-tools) — *not* the QCS6490/RB3 board the
> PS image showed. This **resolves landmines L1/L2/L4/L5** (§22): one known flagship target (Hexagon
> **V79**, the top-tier NPU), the reference **VisionSolution4 is validated on 8 Elite** (so its `.dlc` +
> skel `.so` are the right ones), the `<80 ms` budget becomes trivial, and a developer-kit Android build
> should permit `adb root`/`setenforce 0`. The board / V68 / USB-cam-HDMI contingencies below are now
> **dead-path insurance only**.

---

## 0. Front matter

> **v1.1 — DEVICE CONFIRMED (organizer erratum on the PS):** the venue device is a **QIDK with
> Snapdragon 8 Elite (SM8750, Hexagon V79), running Android** — *not* the QCS6490/RB3 board the PS image
> showed. This **resolves landmines L1/L2/L4/L5** (§22): one known flagship target (top-tier NPU), the
> reference **VisionSolution4 is validated on 8 Elite** (its `.dlc` + skel `.so` are the right ones), the
> `<80 ms` budget is trivial, and the developer-kit Android build should permit `adb root`/`setenforce 0`.
> ADB ships with Android Studio platform-tools. The board / V68 / USB-cam-HDMI notes below are now
> dead-path insurance only.

### 0.1 How to use this document — the three governing rules
1. **Default to the bible.** When in doubt, what this document says wins. It is grounded in the
   official QIDK Quick Start PDF and verified research — not memory.
2. **Each engineer owns their section.** Three tracks (A "Metal", B "Brain", C "Glue→App"). Even if one
   person does several, keep the **module boundaries clean** — they exist so a failure in one track
   never cascades into another.
3. **Independence is non-negotiable.** No module tightly couples to another. **Every interface has a
   mock**, and the app must build and run end-to-end with **any subset of real implementations**. This
   is what lets the team work in parallel and what guarantees a demo even on partial completion.

### 0.2 The non-negotiable engineering vows (read before writing a line of code)
- **No invented numbers.** Every latency / FPS / accuracy figure is either measured on-device or
  explicitly labelled "not yet measured." A fabricated number is a fireable offence in this project,
  because Qualcomm engineers judge this track and will probe it.
- **Honest `backend()` always.** The app reports the compute backend it *actually reached* (NPU / GPU /
  CPU). It never claims NPU when it silently fell back. The honesty *is* the credibility.
- **Zero allocation in the per-frame path.** Pre-allocate every buffer at startup and reuse it. No
  `FloatArray(...)`, `ByteArray(...)`, `Bitmap.create…`, or per-frame JNI object marshalling in the
  analyzer loop.
- **The bible wins.** See rule 1.

### 0.3 Changelog
- **v1.0** — first full draft from the approved plan. (Iterations 1–3 hardening recorded in Appendix D.)

---

## 1. Executive summary & win thesis

Vyāyāma is a fully on-device fitness **form coach**. You stand in front of a Snapdragon device, start
exercising, and the app — running entirely on the chip, no cloud, no internet — detects *that* you're
exercising, recognises *which* of five exercises you're doing, **counts your reps**, **scores each rep's
form 0–100**, and speaks **one corrective cue at a time** ("go deeper", "knees out", "hips up"). It also
shows a live **CPU-vs-NPU latency comparison** proving the work runs on the Hexagon NPU.

**The strategic reality that defines our entire approach:** Qualcomm handed *every* team in this track a
working reference app — `VisionSolution4-PoseEstimation` — that already runs **person-detection + pose
estimation on the Hexagon NPU** with a built-in CPU/GPU/DSP runtime toggle. So pose-on-NPU is **table
stakes**, not a differentiator. The room will be full of teams that reskin the sample, and a Qualcomm
engineer will see through that in thirty seconds.

> **Win thesis:** *The reference ends at keypoints; our product begins there.* We fork the reference,
> keep its proven native inference core verbatim, and build the layer Qualcomm's sample stops at — a
> transparent, explainable **biomechanics + coaching engine** on top of the 17 keypoints, wrapped in an
> honest, live CPU-vs-NPU demonstration. We don't compete on the part everyone was given; we go deep on
> the part nobody was given, and we are radically honest about the seam between the two.

Four reinforcing positioning angles:
1. **Depth over the reference, not breadth around it** — the biomechanics layer is the hard, un-given half.
2. **Honesty as a moat** — measured-only numbers + honest backend labelling pre-empt the judge's probe.
3. **A demo that is a controlled experiment** — the CPU↔NPU toggle is a *result that changes live*, not a claim.
4. **Genuinely useful + private** — offline form coaching with no subscription and no video leaving the device.

**Hero use-case:** AI Form Coach with a hint of gamification (streaks / personal-best / per-session grade).
**First exercise set (Core 5, bodyweight, single-person, front-camera):** squat, push-up, lunge, bicep
curl, jumping jack.

---

## 2. The product in plain language (for a non-technical judge)

You open Vyāyāma and prop the phone up. You start doing squats. Instantly a stick-figure skeleton tracks
your body, a big label says **SQUAT**, and a counter ticks **1 … 2 … 3** as you complete each rep. Next to
each rep a score flashes — **92, 88, 95** — how good that rep's form was. When you do a sloppy squat (not
deep enough, knees caving in), the score drops and the app shows one clear instruction: **"Go deeper."**
Not a wall of warnings — one fix at a time, like a real coach. At the end you get a summary: 20 reps,
average form 90, your most common mistake, and a streak badge.

There's also a small panel showing how fast the AI is running — and a button that switches it between the
phone's **NPU** (the dedicated AI chip) and its **CPU**. Flip it and you *see* the speed drop on CPU and
jump back up on NPU. That's the proof the heavy lifting really runs on Qualcomm's AI hardware.

Everything happens **on the device**. No video is uploaded. No account. No internet needed.

---

## 3. The "not a reskin" chapter (read this to a Qualcomm judge first)

This is the most important section in the document, because "you just reskinned the sample" is the one
objection that can sink an otherwise-strong project in this track. We disarm it before it's raised.

**The PDF's Chapter 6 explicitly invites teams to go beyond the default model** ("Participants are
encouraged to explore better open-source models… More keypoints = better downstream results for
gesture/pose recognition"). Building beyond the reference is *responding to the brief*, not going
off-script. We make that explicit and honest.

### The given-vs-built ledger (goes on a slide and in the deck)

| What Qualcomm gave (the reference `VisionSolution4`) | What **Vyāyāma** built on top |
|---|---|
| YoloNAS person-detection on the NPU | **Exercise-state gating** — detecting *when* someone is mid-exercise vs idle/resting |
| HRNet 17-keypoint pose on the NPU (SNPE C++) | **A joint-angle feature layer** — per-frame biomechanical features (knee/hip/elbow/shoulder angles, symmetry, depth ratios, torso lean) with smoothing + confidence gating |
| Runtime toggle CPU/GPU/DSP + fallback ladder | **An instrumented, demoable CPU-vs-NPU comparison** — live per-frame latency in the UI, warm-up-corrected, honest backend-reached labelling |
| `.dlc` for Hexagon (V79-targeted) | **Per-device `.dlc` discipline** (V68-class for QCS6490) + a device-agnostic runtime story |
| Skeleton rendering | **The Form Coach** — per-exercise rep counting (state machine), per-rep form scoring, one-cue-at-a-time corrective coaching, session summary, light gamification |
| *(nothing)* | **Exercise recognition** for the Core 5 — rule-based, with an optional learned temporal head as the scale path |

**The one-line weapon for Q&A:** *"The reference answers 'where are the joints?' We answer 'is your form
going to hurt you, and how do you fix it?' — on the device, with no cloud."*

Why this wins respect: it shows we *understood* what we were handed (most reskin teams can't articulate
it), it reframes our work as the high-value half, and it makes our honesty an asset. We **under**-claim the
given part and **over**-deliver the built part — the opposite of the teams that will get caught.

**Concretely, vs a typical reskin team:** a reskin ships the reference's skeleton with a logo and maybe a
hard-coded "squat counter" on one joint threshold (double-counts on jitter, no form feedback, no
"is-exercising" gate, claims "NPU" with no measurement). Vyāyāma ships a confidence-gated feature layer, a
hysteresis rep FSM with partial-rep handling, *corrective* per-fault cues with a 0–100 score, an honest
warm-up-corrected CPU-vs-NPU panel, and a device-agnostic runtime that labels its true backend. The gap is
visible in ten seconds of demo and survives every follow-up question.

---

## 4. Problem-statement → solution mapping (proof every requirement is solved)

Every line of the 3-part FitSense brief mapped to the component that satisfies it and the track that owns it.

| # | PS requirement | Solved by | Bible § | Track |
|---|---|---|---|---|
| P1.1 | Detect **when** a person is exercising | "is-exercising" gate in `ExerciseClassifier` (idle vs active from pose motion energy) | §9 | B |
| P1.2 | Identify **which** exercise | `RuleExerciseClassifier` (Core 5) + optional learned head; honest `UNKNOWN` | §9 | B |
| P1.3 | Run **fully on-device**, no cloud/internet | No network permission in the prod path; all compute on the SoC | §6, §20 | A/C |
| P2.1 | Convert model to **`.dlc`** for the Hexagon NPU | DLC generation (`GenerateDLC.ipynb`) **or** AI Hub Path B per device; runs on HTP via SNPE | §10 | A |
| P2.2 | Target **<80 ms/frame** | Labelled per-frame budget; HTP BURST + zero-copy; detect-every-N / single-stage levers; measured-only | §11 | A |
| P2.3 | **Live CPU-vs-NPU** comparison | `Benchmark` + reference runtime toggle; warm-up-corrected p50/p90/FPS; honest backend-reached; on-stage NPU→CPU jump | §10 | A/C |
| P3.1 | Build a **useful app** | AI Form Coach: rep counting + per-rep form score + one-cue coaching + session summary + light gamification | §12–15 | C/B |
| P3.2 | Help someone **work out better** | Actionable corrective cues (depth, valgus, hip-sag, swing, symmetry); private, offline, no subscription | §13 | B |

If a judge asks "does it do X?", point at the row. Nothing is hand-waved.

---

## 5. Judging criteria & our per-criterion play

Likely criteria for this track: innovation, technical implementation, impact, feasibility, scalability,
presentation. What earns a top score, and where we are honestly weak today (pre-build).

- **Innovation — strong.** The biomechanics layer is non-trivial: per-exercise FSM rep counting,
  *corrective* (not binary) form cues, a per-rep 0–100 score, plus the optional learned head. *Weak spot:*
  "rep counting from angles" is a known idea — so we make the **corrective cue engine** the star (most apps
  only count) and avoid ever claiming the *pose model* is our innovation (it's Qualcomm's).
- **Technical implementation — strong, with one real risk.** Clean layered architecture, swappable pose
  source, unit-tested math, and a working live CPU-vs-NPU switch. *Weak spot:* getting the NPU path running
  on the *actual* venue device (right Hexagon arch, runtime libs, root/SELinux). De-risked by keeping the
  reference core verbatim, proving NPU on ≥2 devices in prep, and making the CPU path genuinely good.
- **Impact — strong if framed right.** Private, offline, personal-trainer-grade form feedback; injury
  prevention; accessibility (a coach is expensive, this is free + offline). *Weak spot:* "fitness app"
  sounds unserious to a hardware judge — so lead with **privacy + accessibility + injury-prevention**.
- **Feasibility — good, must stay disciplined.** Demonstrably *done* beats promised: the mock-mode app runs
  with zero hardware, so even partial success demos. *Weak spot:* solo/small-team + venue-gated NPU +
  per-device DLC — front-loaded into prep (see §23).
- **Scalability — strong, with an honest ceiling.** Adding an exercise = adding one analyzer module (no
  retraining for the rule path); pose source is model-agnostic; the runtime ladder is device-agnostic.
  *Weak spot:* rule-based form is per-exercise hand-tuning — named openly, with the learned head as the
  scale story.
- **Presentation — high ceiling, solo risk.** One unmistakable wow beat (live latency drop / a bad rep
  getting corrected), a clean projector-legible overlay, and the honesty beat in the first 60 seconds.
  *Weak spot:* one person narrating + device-juggling — mitigated by rehearsal + a backup video.

---

## 6. System architecture

```
                         ┌───────────────────────── Vyāyāma APK ─────────────────────────┐
                         │                                                                 │
  ┌─────────┐   frames   │  ┌──────────────┐   keypoints   ┌────────────────────────────┐ │
  │ Camera  │──YUV/RGB──▶│  │  PoseEngine   │──float[51+5]─▶│  Exercise-Intelligence     │ │
  │ (CameraX│            │  │  (Kotlin      │   (THE SEAM)  │  pipeline (pure Kotlin):   │ │
  │  or     │            │  │  facade over  │               │                            │ │
  │  native)│            │  │  C++/JNI/SNPE)│               │  FeatureExtractor          │ │
  └─────────┘            │  └──────┬───────┘               │   → ExerciseClassifier      │ │
                         │         │ native                │   → RepCounter              │ │
                         │  ┌──────▼─────────────────────┐ │   → FormAnalyzer            │ │
                         │  │ C++  (KEEP VERBATIM)        │ │        │                    │ │
                         │  │  inference.cpp execcomb()   │ │        ▼                    │ │
                         │  │  YoloNAS→NMS→warp→HRNet      │ │  CoachOrchestrator          │ │
                         │  │  → 17 heatmaps→keypoints     │ │  (one analyzer thread)      │ │
                         │  └──────┬─────────────────────┘ │        │ StateFlow<CoachState>│ │
                         │  ┌──────▼───────┐               │        ▼                    │ │
                         │  │ QAIRT/SNPE   │  ┌─────────┐  │  ┌──────────────────────────┐│ │
                         │  │ CPU/GPU/HTP  │◀─│Benchmark│◀─┼──│ Compose UI (renders only) ││ │
                         │  └──────────────┘  └─────────┘  │  │ overlay·HUD·reps·cues·A/B ││ │
                         │   (det_ms, pose_ms, backend)    │  └──────────────────────────┘│ │
                         └─────────────────────────────────────────────────────────────────┘
```

Two data paths are drawn explicitly: the **coaching path** (camera → pose → intelligence → CoachState →
UI) and the **benchmark path** (PoseEngine reports per-inference latency + backend → Benchmark → the live
A/B panel). The UI is a pure function of the latest `CoachState`; it never blocks inference.

---

## 7. Module boundaries & independence (the interface-with-mock discipline)

A small set of Kotlin interfaces, each with a **real** impl in its module and a **mock**, wired by a tiny
hand-rolled DI container (no Hilt/Koin — keep hackathon build times down). The app boots and demos with
**any subset mocked**.

| Interface | Real impl | Mock | What the mock unblocks |
|---|---|---|---|
| `PoseEngine` | `SnpePoseEngine` (JNI→SNPE) | `MockPoseEngine` (replays recorded keypoint CSV @30fps) | **Keystone** — entire app runs with no device/NPU/camera |
| `CameraEngine` | `RealCameraEngine` (CameraX YUV, zero-alloc) | `MockCameraEngine` (no-op; mock pose drives frames) | UI/intelligence dev on emulator |
| `FeatureExtractor` | `RealFeatureExtractor` (pure Kotlin) | — (pure fn, unit-tested directly) | n/a |
| `ExerciseClassifier` | `RuleExerciseClassifier` / `LearnedExerciseClassifier` / `FusedExerciseClassifier` | `MockExerciseClassifier` (fixed type) | rep/form/UI dev against a known exercise |
| `RepCounter` | `StateMachineRepCounter` | `MockRepCounter` (timer-increment) | UI dev |
| `FormAnalyzer` | `RuleFormAnalyzer` | `MockFormAnalyzer` (canned cues/score) | UI polish |
| `Benchmark` | `RealBenchmark` (EMA/p50/p90/FPS) | `MockBenchmark` (labelled fake numbers) | latency panel renders in mock mode |

**The decisive subsets:**

| Scenario | Real | Mocked | Capability |
|---|---|---|---|
| Pure UI dev (laptop emulator) | — | all | build/polish entire UI, overlay, gamification, latency panel |
| Intelligence dev (any phone) | FeatureExtractor, Classifier, RepCounter, FormAnalyzer | PoseEngine (CSV replay), Camera, Benchmark | develop + unit-test the whole brain on recorded sessions; no SNPE/`.dlc` |
| Native bring-up (venue device) | PoseEngine (real SNPE), Camera, Benchmark | intelligence (optional) | validate NPU, latency, skeleton |
| Full (finale) | all | none | the demo |

This is the structural guarantee that **no single track's slippage sinks the demo.**

---

## 8. Part 1 — Pose abstraction layer & feature extraction

### 8.1 The seam (the single most important interface)
Per frame, the native side returns **one flat primitive array** to Kotlin:
```
float[17*3 + 5] = [ x0,y0,c0,  x1,y1,c1, …, x16,y16,c16,   backendCode, detMs, poseMs, srcW, srcH ]
                   └──────── 51 keypoint floats (source-frame px) ───────┘ └──── 5 metadata floats ────┘
```
- **No per-frame JNI object allocation** (no `NewObjectArray`). One primitive array, reused.
- `backendCode`: 0=CPU,1=GPU,2=NPU,3=MOCK — the backend *actually used*, read honestly from SNPE.
- Above the seam: nothing knows about tensors/DLCs. Below it: nothing knows about reps/exercises.
- **Consequence:** the entire intelligence layer is JVM-unit-testable with recorded keypoint CSVs as
  fixtures — no device, no NPU, no camera.

### 8.2 The 17 COCO keypoint indices (global constants, fix once)
```
0 nose · 1 L-eye · 2 R-eye · 3 L-ear · 4 R-ear ·
5 L-shoulder · 6 R-shoulder · 7 L-elbow · 8 R-elbow · 9 L-wrist · 10 R-wrist ·
11 L-hip · 12 R-hip · 13 L-knee · 14 R-knee · 15 L-ankle · 16 R-ankle
```
Skeleton bones for rendering: (5,7)(7,9)(6,8)(8,10)(5,6)(11,12)(5,11)(6,12)(11,13)(13,15)(12,14)(14,16)(0,5)(0,6).

### 8.3 Normalization (camera/scale/translation invariant) — `RealFeatureExtractor`
A **pure function** `normalize(PoseFrame) → NormalizedPose`. Steps:
1. **Confidence gate first.** For each keypoint, if `conf < τ_kp` (start **0.3**, tune on-device), mark it
   unusable. Any feature that needs an ungated keypoint emits `NaN`; downstream rules **skip**, never
   fabricate. (Fabricating values from low-conf joints is the #1 cause of phantom reps and wrong cues.)
2. **Hip-center** = midpoint(L-hip 11, R-hip 12). Translate all points so hip-center = origin.
3. **Torso length** = distance(hip-center, shoulder-center = mid(5,6)). Scale all coords by 1/torso-length
   → body-size and camera-distance invariant.
4. **Joint angles** (the primary features — translation/scale invariant and largely viewpoint-robust).
   Angle at B for triple (A,B,C) = angle between vectors BA and BC. Computed every frame:
   - `kneeL = ∠(hipL, kneeL, ankleL)`, `kneeR = ∠(hipR, kneeR, ankleR)`
   - `hipL = ∠(shoulderL, hipL, kneeL)`, `hipR = ∠(shoulderR, hipR, kneeR)`
   - `elbowL = ∠(shoulderL, elbowL, wristL)`, `elbowR = ∠(shoulderR, elbowR, wristR)`
   - `shoulderL = ∠(hipL, shoulderL, elbowL)`, `shoulderR = ∠(hipR, shoulderR, elbowR)`
   - `torsoLeanFromVertical` = angle of (shoulder-center → hip-center) vs image vertical
   - `openness` (jumping jack composite, range ~0=closed … ~1=open):
     `armRaise = clamp01((shoulderCenter.y − wristCenter.y) / torsoLen)` (positive when wrists above shoulders;
     image y grows downward so a raised wrist has smaller y);
     `legSpread = clamp01((ankleSpread / shoulderWidth − 1.0) / 1.0)` (0 at feet-together ≈ shoulder width, 1 at ~2× spread);
     `openness = 0.5·armRaise + 0.5·legSpread`. All distances in torso-normalized units.
5. **Smoothing.** Each angle passes through an **EMA (α≈0.3)** or 5-tap moving average before any threshold
   compare. Stateful, per-angle. This is what kills jitter-induced double counts.

Output `NormalizedPose{ jointAngles[], keypointsNorm[], confidences[], visible }`.

---

## 9. Part 1 — Exercise recognition & "is-exercising" gate

Two-tier, behind `ExerciseClassifier`, consuming a short ring-buffer window of `NormalizedPose`.

### 9.1 "Is-exercising" gate (satisfies P1.1)
Per frame, compute **motion energy** `E` = mean, over the candidate primary angles {knee, elbow, openness},
of each angle's **variance across the last 30 frames** (≈1 s @30 fps). State machine with hysteresis +
dwell so it doesn't flicker between idle and active:
```
state IDLE:       enter EXERCISING when E > E_active for ≥ 0.5 s   (start E_active ≈ (15°)²)
state EXERCISING: return to IDLE     when E < E_idle   for ≥ 0.5 s  (start E_idle   ≈ (8°)²)
```
While `IDLE` the classifier emits `NONE` and the `RepCounter` is frozen — so we never count reps while a
person stands still, fidgets, or walks into frame. Thresholds are tunable constants (= test vectors),
tuned on recorded idle-vs-active clips via `tools/threshold_tuner`. This is a distinct, demoable feature:
the HUD shows "Ready"/"Resting" vs the live exercise label.

### 9.2 `RuleExerciseClassifier` (guaranteed baseline, zero training)
Classify the Core 5 from the **signature** of the windowed features: (a) which primary angle has the
largest oscillation amplitude, (b) torso orientation, (c) left-right symmetry. Decisive signatures:

| Exercise | Signature | Primary angle | Discriminator |
|---|---|---|---|
| **PUSHUP** | torso ~horizontal, elbows oscillating, hips level with shoulders | elbow (avg) | horizontal torso vs curl |
| **SQUAT** | torso upright, **both** knees oscillating in sync, hip-center descends | knee (avg) | symmetric knees vs lunge |
| **LUNGE** | torso upright, **asymmetric** knee bend, one ankle forward | front-knee | asymmetry vs squat |
| **BICEP_CURL** | torso upright, shoulder fixed, elbow oscillating, no leg motion | elbow (active) | fixed shoulder + vertical vs pushup |
| **JUMPING_JACK** | wrists rise above head AND ankles spread, oscillating together | openness composite | both-limb openness |

**Hysteresis on the classification itself:** don't switch the displayed exercise unless the new type wins
for ~10 consecutive frames. Emit `UNKNOWN` when no signature is confident — **honesty over a wrong guess.**

### 9.3 `LearnedExerciseClassifier` (optional, additive — the scale path)
**Pinned architecture (so it's buildable, not hand-wavy):** a **1D temporal CNN (TCN-style)** over a fixed
**32-frame window** (~1 s @30 fps). **Per-frame input feature vector = 61 floats** = 10 joint angles + 34
normalized keypoint coords (17×2) + 17 confidences → window tensor **[1, 32, 61]**. Network: 3 dilated 1-D
conv blocks (dilation 1/2/4, ~64 channels, kernel 3, causal) → global average pool → dense → **softmax over
6 classes** {NONE, SQUAT, PUSHUP, LUNGE, BICEP_CURL, JUMPING_JACK}. Size **<5 MB** (INT8/FP16 TFLite), runs
on **CPU** every ~8 frames (NPU budget stays for pose). Trained on Fit3D / InfiniteRep / Kaggle-workout
keypoints (extracted with the same 17-COCO convention) + self-recorded sessions. **Critically, it consumes
the exact `FeatureExtractor` output** — the device feature vector *is* the model input → no train/serve skew
(see §16, the parity contract). If the model file is absent the fusion (§9.4) is silently rules-only.

### 9.4 `FusedExerciseClassifier` (honest fusion + fallback)
Runs rules always; runs learned if loaded. If learned `confidence > τ_learned` (≈0.7) → use learned,
`source=LEARNED`; else rules, `source=RULES`; if both agree → `source=FUSED` (boosted). If the learned
model is absent/failed to load → silently rules-only. The displayed exercise always carries its honest
`source`. **Rules are the baseline; the learned model is the upgrade — its absence is invisible.**

Known confusion pairs to watch in tuning: push-up vs plank-hold (motion energy distinguishes), squat vs
lunge (symmetry), curl vs pushup (torso orientation).

---

## 10. Part 2 — On-device runtime, `.dlc` conversion & the CPU-vs-NPU demonstration

### 10.1 What the reference gives us (keep verbatim)
`VisionSolution4` runs **two models on the NPU via SNPE C++**: YoloNAS_SSD (320×320) person-detect →
HRNet (256×192) → 17 keypoints. The C++ call flow (in `inference.cpp` / `inference_helper.cpp`):
```
loadContainerFromBuffer()  → zdl::DlContainer::IDlContainer::open(buffer,size)
checkRuntime()             → zdl::SNPE::SNPEFactory::isRuntimeAvailable(rt)   // falls back DSP→GPU→CPU
setBuilderOptions()        → SNPEBuilder.setPerformanceProfile(BURST)
                                        .setExecutionPriorityHint(HIGH)
                                        .setRuntimeProcessorOrder(runtimeList)
                                        .setUseUserSuppliedBuffers(true)
                                        .setPlatformConfig(useAdaptivePD:ON).build()
createInput/OutputBufferMap() → getInput/OutputTensorNames(); getInputOutputBufferAttributes(name)
createUserBuffer()         → getUserBufferFactory().createUserBuffer(...)   // zero-copy, Float32
execute(inputMap, outputMap)   // YoloNAS, then HRNet
```
Runtime is passed down as a char: `'C'` CPU / `'G'` GPU / `'D'` DSP(HTP). **The in-app runtime toggle
already exists.** We build on it; we do not re-derive it.

### 10.2 `.dlc` conversion — two independent paths (both prepped)
- **Path A — generate (Docker):** `Generate_models/GenerateDLC.ipynb` inside the QAIRT Docker converts +
  quantizes the upstream models, emitting `Quant_yoloNas_s_320.dlc` + `hrnet_axis_int8.dlc`. Needs a COCO
  calibration path (val2017 ~1 GB suffices). **Per-device:** re-run targeting the venue Hexagon arch.
- **Path B — download (AI Hub):** `aihub.qualcomm.com` → Computer Vision → Pose Estimation → pick the
  device (e.g. SM8750 or QCS6490) → download an already-INT8-quantized `.dlc` validated on that chip.
  **No Docker / Linux / COCO required.** This is our escape hatch (see §22 L3/L4/L6).
  - **Which model:** **`HRNetPose-Quantized` (17 kpt)** is the **drop-in** — same keypoint convention as the
    reference, so our `FeatureExtractor` and the seam are unchanged; pair with a person-detector or run it
    full-frame for the single-person coaching case. **`MediaPipe-Pose` (33 kpt, single-stage)** is the richer
    *upgrade* (more landmarks for form, drops the separate detector → easier `<80 ms`), but requires a
    **33→17 mapping** in `PoseEngine` (MP shoulders/elbows/wrists/hips/knees/ankles/nose/eyes/ears → COCO
    indices) and adapting pre/post-processing (PDF Ch 6.4). Default to HRNetPose-Quantized; treat MediaPipe-Pose
    as the latency/quality lever (§11) behind the same `PoseEngine` interface so nothing downstream changes.

### 10.3 The honest CPU-vs-NPU measurement (`Benchmark`)
1. **Warm-up discarded.** First ~10–30 inferences (or ~1 s) after a backend switch are excluded (SNPE graph
   prep / DSP spin-up are not steady state).
2. **Time the right thing.** Per-inference compute latency captured **in C++** around the `execute()` calls
   (`detMs`, `poseMs`) — excludes JNI/Java jitter. Also end-to-end frame latency and pipeline FPS. All
   three displayed, **labelled distinctly** (judges conflate "inference ms" and "FPS"; we don't).
3. **Aggregate honestly.** Report **median (p50) and p90** over the last ~100 post-warm-up frames + an EMA
   for the live readout. Median, not mean (robust to GC/scheduler spikes).
4. **Same input, model, resolution** for both backends — only the runtime varies.
5. **Report the backend actually reached, never the requested one.** If `forceBackend(NPU)` falls back, the
   panel says **"NPU requested → ran on CPU (DSP unavailable)."**

### 10.4 The live panel (the Part-2 money-shot)
```
┌─ ENGINE ───────────────────────────────────┐
│ Backend: NPU (Hexagon HTP, V__)            │  ← honest; shows fallback if it happened
│ Pose infer:  __ ms (p50) / __ (p90)        │  ← det + pose, measured in C++
│ End-to-end:  __ ms   ·   FPS: __           │
│ [ AUTO ] [ NPU ] [ GPU ] [ CPU ]           │  ← live toggle (reuse reference switch)
│ ── A/B ──  NPU __ ms   vs   CPU __ ms       │
│             speedup: __×                    │  ← computed live after a sweep
└──────────────────────────────────────────────┘
```
On stage: flip NPU→CPU and the FPS/latency visibly change. **A result that changes in front of the judge
is the proof the NPU is real.** Corroborate with an offline QAIRT/AI-Hub profile screenshot on the slide.

### 10.5 The HTP-access reality (stated plainly, not buried)
The reference's HTP path needs **`adb disable-verity; adb reboot; adb root; adb remount; adb shell
setenforce 0`** (SELinux permissive; **resets every reboot**). This works on the **venue QIDK
(userdebug/engineering build)** but **likely fails on a retail phone** (iQOO Z5 / Moto can't `adb root`).
→ **NPU proof is a venue-device thing.** On retail phones we validate the pipeline on CPU/GPU and the panel
labels that honestly. See §22 L2 and §17.

---

## 11. Part 2 — Performance budget & profiling methodology (<80 ms/frame)

**No invented numbers.** Fill `<__>` from on-device profiling. The budget is a *decomposition* so we know
where to look when we miss the target.
```
PER-FRAME BUDGET  (target end-to-end ≤ 80 ms → ≥ ~12.5 fps; stretch 33 ms → 30 fps)
[A] Camera acquire + YUV→RGB ................ < __ ms  (pre-alloc, zero GC)
[B] Preprocess YoloNAS (320, BGR→RGB, ÷255) . < __ ms  (C++)
[C] YoloNAS inference (320×320) on HTP ...... < __ ms  (C++ execute)  ← measure, expected dominant
[D] NMS (0.20) .............................. < __ ms  (C++)
[E] Crop + affine-warp 256×192 + ImageNet norm < __ ms (C++ preprocess_pose)
[F] HRNet inference (256×192) on HTP ........ < __ ms  (C++ execute)  ← measure, expected dominant
[G] Heatmaps(17) → argmax → keypoints ....... < __ ms  (C++)
─── SEAM (JNI float[51+5]) ───
[H] FeatureExtractor (normalize+angles+EMA) . < __ ms  (Kotlin, tiny)
[I] Classifier (rules/frame; learned every K) < __ ms  (Kotlin; learned ~few Hz)
[J] RepCounter + FormAnalyzer ............... < __ ms  (Kotlin, tiny)
[K] Compose overlay redraw .................. < __ ms  (UI thread, decoupled — off critical path)
```
**Rules:** the <80 ms target is for the inference pipeline [A]–[G]; [H]–[J] are sub-ms pure Kotlin on 17
points; [K] is decoupled. **Levers if we blow 80 ms (in order):** (1) confirm HTP is *actually* engaged
(check `backend()`, not the label); (2) run pose every frame but person-detect every N frames (track the
box between detections — a person doesn't teleport); (3) lower analysis resolution; (4) drop to a
single-stage pose model (MediaPipe-Pose / AI Hub one-shot) to remove an entire inference. The two NPU
inferences ([C],[F]) will dominate — that's where INT8 + HTP BURST + zero-copy buy the budget.

---

## 12. Part 3 — Rep counting & per-exercise analyzers

`StateMachineRepCounter` — one FSM per exercise, all sharing this shape, driven by the smoothed primary
angle θ:
```
States:  TOP → DESCENDING → BOTTOM → ASCENDING → (rep++) → TOP
Two thresholds with a GAP (hysteresis):  θ_top (extended) and θ_bottom (flexed),  θ_bottom < θ_top
A rep counts on the full BOTTOM→TOP completion — never on a single threshold crossing.
```
Per-exercise primary angle + **starting** thresholds (tunable constants = test vectors; tuned on recorded
data, never shipped as gospel):

| Exercise | Primary θ | θ_top | θ_bottom | Notes |
|---|---|---|---|---|
| Squat | avg(kneeL,kneeR) | ~165° | ~95° | require both knees; symmetry checked in form |
| Push-up | avg(elbowL,elbowR) | ~160° | ~95° | torso must stay horizontal during the rep |
| Lunge | front knee | ~165° | ~95° | "front" = the more-flexed knee at bottom |
| Bicep curl | elbow (active arm) | ~155° | ~50° | shoulder must stay fixed |
| Jumping jack | openness | low (closed) | high (open) | full cycle = closed→open→closed |

**Anti-jitter rules (what makes counting robust):** hysteresis gap (never a single threshold);
**min-rep-duration** (reject "reps" faster than ~0.3 s — physically impossible, it's noise);
**confidence-freeze** (if the primary joint drops below τ_kp mid-rep, freeze the FSM, resume on return;
optionally void the rep); **partial-rep flagging** (reversed before reaching θ_bottom → `partial=true`,
counted in a separate tally). The current FSM `phase` is exposed so the UI can show "go lower" while still
DESCENDING. **Scalability proof:** adding an exercise = adding one analyzer config — no retraining.

**Worked squat trace (smoothed avg-knee θ, θ_top=165°, θ_bottom=95°):**
```
θ: 170 → [TOP]      (standing)
   150,120,100 →    [DESCENDING]   (θ falling, not yet < 95)
   88 →             [BOTTOM]       (θ crossed below 95 → real depth reached)
   100,140 →        [ASCENDING]
   168 →            [TOP] ⇒ rep++  (θ crossed back above 165 → one full rep)
PARTIAL example:  170→150→110(reverses)→160  ⇒  never hit BOTTOM (<95) → partial=true, no clean count.
NOISE example:    a 1-frame 170→90→170 spike is rejected by min-rep-duration (≥0.3 s) + EMA smoothing.
```

---

## 13. Part 3 — Form analysis, scoring & coaching cues

`RuleFormAnalyzer` runs **per rep** (over the TOP→BOTTOM→TOP window). Each exercise has a small rule set;
each rule contributes to a 0–100 score and may raise a `Cue`.

- **Squat:** depth (knee θ ≤ target) · knee valgus (knees caving: knee-x vs ankle-x/hip-x) · symmetry
  (|kneeL−kneeR|) · torso lean (excessive forward) · tempo.
- **Push-up:** depth (elbow θ ≤ target) · **hip sag/pike** (hip-center offset from shoulder–ankle line — the
  signature push-up fault) · elbow symmetry · tempo.
- **Lunge:** front-knee depth ~90° · knee-not-past-toes (knee-x vs ankle-x) · torso upright · L/R balance.
- **Bicep curl:** full ROM (extension→curl) · **shoulder/elbow drift** ("stop swinging, isolate the bicep")
  · symmetry · tempo (no jerking).
- **Jumping jack:** full arm extension overhead · full leg spread · arm/leg sync · rhythm.

**Scoring:** per-rep score = weighted sum of normalized rule sub-scores (depth, ROM, symmetry, alignment,
tempo), clamped 0–100; weights are named constants. **Cue policy:** show **the single lowest-scoring rule
that crossed its "bad" threshold**, phrased as an actionable imperative ("go deeper", "knees out", "hips
up"). At most 1–2 cues at once — coaching, not a wall of text. Optional TTS. **Session summary:** total
reps, avg score, most common fault. **Honest limit:** single-camera 2D pose can't see everything (depth
ambiguity, occlusion) — form is **guidance, not medical advice** (stated in the UI and §24).

---

## 14. Part 3 — Gamification & session model

Light and tasteful — a *hint*, not a game (so it adds impact without diluting the engineering story):
**streak** (consecutive days/sessions), **personal best** (reps at ≥X form), a per-session **form grade**
(A/B/C from avg score), and a rep goal with a progress ring. Session model: `Session{ exercise, reps[],
avgScore, topFault, durationMs, grade }` persisted locally (Room/DataStore) — **on device, no account.**

---

## 15. Part 3 — App platform, camera & UI

- **Host:** fork of `VisionSolution4` (native camera + C++/SNPE kept). Coach UI in **Jetpack Compose**
  hosted over the preview (transparent `ComposeView` with `setZOrderOnTop`, or `AndroidView` wrapping the
  preview). **Skeleton redrawn in a Compose `Canvas`** from the keypoints crossing the seam (collapses two
  render paths into one) — *verify it profiles clean at 30 fps before deleting the GL path.*
- **Camera:** `CameraEngine` (CameraX `ImageAnalysis`, `YUV_420_888`, `STRATEGY_KEEP_ONLY_LATEST`,
  pre-allocated buffers, zero per-frame alloc — lifted from the Pramāṇa pattern). On a board (RB3/QCS6490)
  the camera is USB/MIPI and the display is HDMI — adapt here, late (frontend-last).
- **Camera-ownership decision (resolved):** **Kotlin `CameraEngine` owns the camera and converts YUV→RGB
  into one pre-allocated direct `ByteBuffer`, passed *down* into `PoseEngine.infer(CameraFrame)`** (which
  hands the buffer pointer to native `nativeInfer`). This matches the testable Pramāṇa pattern and keeps the
  seam clean. **Caveat (verify against the fork in Phase 1):** the reference's `CameraFragment` may feed its
  native side directly; if rewiring that fights us, the fallback is to let the **native side keep ownership
  of the camera** and have `CameraEngine` be a thin lifecycle wrapper that carries no pixels — `PoseEngine`
  then exposes a `SharedFlow<PoseFrame>` instead of `infer(frame)`. Decide after reading the forked
  `CameraFragment.java`; either way the intelligence layer (which only sees `PoseFrame`) is unaffected.
- **Overlay (projector-legible):** big rep counter, one cue line (large, color-coded), per-rep score,
  exercise label, the engine/latency panel. Designed to read across a room on a projector.
- **Lifecycle/permissions:** CAMERA only; no INTERNET permission in the prod path.

---

## 16. Complete tech stack (every piece + why)

| Layer | Choice | Why |
|---|---|---|
| Inference SDK | **QAIRT v2.46.0.260424 / SNPE C++** | what the reference uses; `.dlc` on CPU/GPU/HTP, runtime-switchable |
| Models | **YoloNAS_SSD + HRNet** (`.dlc`, INT8) | the reference's proven pose pipeline on the NPU |
| Model source | **GenerateDLC.ipynb** (Path A) + **AI Hub** (Path B) | two independent paths to a device-matched `.dlc` |
| Native build | **NDK r26c + OpenCV 4.13.0 Android SDK** | exact versions `resolveDependencies.sh` expects |
| App language | **Kotlin** | our layer; reference Java kept where it works |
| UI | **Jetpack Compose** | fast, polished coach UI; Canvas overlay |
| Camera | **CameraX** | YUV zero-alloc analyzer; lifecycle-aware |
| Learned head (optional) | **PyTorch → TFLite (CPU)** | tiny temporal classifier; CPU keeps NPU budget for pose |
| Concurrency | **Kotlin coroutines + StateFlow** | one analyzer thread → immutable CoachState to UI |
| Persistence | **DataStore/Room** | local session history; no cloud |
| Build | **Gradle + version catalog**, AS Panda 4 v2025.3.4 | reproducible; the AS version the PDF mandates |
| Profiling | **adb / Android Studio profiler / QAIRT profile** | the only valid source of latency numbers |

**The parity contract:** `ml/features/` (Python) and the Kotlin `FeatureExtractor` implement the *same*
normalization+angle math, pinned by parity tests — so the learned model has no train/serve skew.

---

## 17. Redundancy & failover (every failure has a pre-decided action)

| Failure | Pre-decided action |
|---|---|
| NPU unreachable (no root / wrong arch) | Auto-fall to GPU/CPU; panel labels it honestly; demo runs on CPU; show prep NPU clip |
| Root denied on device | Full coaching demo on CPU + pre-recorded NPU-mode clip side-by-side |
| `.dlc` won't load / arch mismatch | Swap `PoseEngine` → `MockPoseEngine` (recorded keypoints); full coaching still demos |
| Camera/lighting bad | "Step into the box" floor marker; else pre-recorded full-demo clip, narrated |
| Total app freeze | 3-min backup video (NPU mode), calm narration from the point of failure |
| Learned model bad/absent | Silently rules-only (baseline); nothing visible breaks |

**Meta-rule (from Pramāṇa):** mock/canned values are **stage-rescue insurance, never the development
default.** If you reach for them while building, the real thing is broken — fix it.

---

## 18. Module ownership & 3 parallel tracks

Three independent tracks; mock-everything + frozen interfaces keep them unblocked; collapses cleanly to
fewer people.

- **Engineer A — "Metal" (device · pose · NPU):** Part 1 on-device + all of Part 2. Owns: fork bring-up;
  keep-verbatim C++/JNI/SNPE core; the seam (`float[51+5]`); `SnpePoseEngine`; DLC gen (Path A) / AI Hub
  (Path B); device prep (root/`setenforce`/skels); `Benchmark` + honest CPU-vs-NPU. Branches: `track/metal`.
- **Engineer B — "Brain" (exercise intelligence):** Part 1 recognition + Part 3 logic. Owns: `FeatureExtractor`,
  `RuleExerciseClassifier`, `StateMachineRepCounter`, `RuleFormAnalyzer`, `FusedExerciseClassifier`, the
  optional `ml/` learned head + dataset/training + `tools/threshold_tuner`. Works **100% device-free**
  against `MockPoseEngine` + recorded CSVs. Branches: `track/brain`.
- **Engineer C — "Glue → App" (integration, then frontend LAST):** integration + Part 3 app. Owns:
  `CoachOrchestrator`, `CameraEngine` lifecycle, mock-fallback DI (`App.kt`), CI, repo scaffold,
  `tools/keypoint_recorder`, and **the Compose UI in the final phase**. Branches: `track/app`.

**Integration through interfaces only.** Freeze `api/` in Phase 0; nobody edits another track's package.

---

## 19. Vibe-code vs Manual matrix

The hard "manual" surface here is **SNPE C++ + JNI + DLC + CMake/NDK** (not crypto, as in Pramāṇa).

**MANUAL — do NOT vibe-code; keep reference verbatim; review after, don't "improve":**
SNPE/QAIRT C++ init & call flow · JNI marshalling / the seam (no per-frame objects) · DLC
conversion+quantization (`GenerateDLC.ipynb`, QAIRT Docker, per-device regen) · CMake/NDK r26c + OpenCV
linking (`resolveDependencies.sh`) · skel-lib bundling per Hexagon arch + `setenforce` device prep ·
affine-warp + ImageNet-norm preprocessing + NMS (keep verbatim — silent-failure-prone, already correct).

**VIBE-with-verification — draft, then check against current docs / on-device:**
CameraX YUV zero-alloc · Compose + `AndroidView`/`SurfaceView` interop + z-order · Compose Canvas skeleton ·
Gradle/NDK/version-catalog/abiFilters · the exercise *threshold tuning* (verify against recorded data) ·
MediaPipe-Pose / AI-Hub model swap (verify the 33-vs-17 keypoint mapping).

**VIBE-safe — drive freely, spot-check:**
the entire pure-Kotlin intelligence layer (FeatureExtractor, rule classifier, rep FSM, form analyzer) · all
mocks · data classes/enums · unit tests · the PyTorch classifier + dataset prep + export · `Benchmark`
aggregation math · docs.

**Anti-patterns:** don't let an assistant rewrite the SNPE/JNI/CMake core "to clean it up" (it's debugged —
touching it loses a day); don't invent latency numbers; don't allocate / marshal objects per frame; don't
fabricate values for low-confidence joints; don't claim NPU on a fallback.

---

## 20. Engineering rules (the non-negotiables)

1. **Zero per-frame allocation** — pre-allocate all buffers (RGB, the 51-float receiver, angle arrays, EMA
   ring buffers) at startup; reuse. No per-frame JNI object marshalling.
2. **Threading** — one dedicated single-thread analyzer executor; `STRATEGY_KEEP_ONLY_LATEST`; the whole
   `infer→features→classify→reps→form` chain runs there and publishes an **immutable `CoachState`** to a
   `StateFlow`. The **UI thread only renders** the latest CoachState; it never blocks the analyzer.
   Throttle person-detect + the learned classifier to every Nth frame; cache between.
3. **Confidence gating everywhere** — one `τ_kp`; skip, never fabricate; freeze the FSM on confidence loss.
4. **Honest reporting always** — `backend()` = the tier reached; panel labels requested-vs-reached; exercise
   carries its `source`; `UNKNOWN` over a wrong guess; partial reps flagged.
5. **The seam is sacred** — keypoints cross C++→Kotlin as one flat primitive array at one boundary.
6. **Mock-fallback DI** — probe for the real dependency (DLC asset present? SNPE init ok?), fall back to the
   mock with a logged warning, per interface. The app always boots to *something* demoable.
7. **Tuned constants are test vectors** — every threshold lives in named config, validated against recorded
   sessions in CI. Drift = test failure.
8. **No cloud / no network in the hot path.** Privacy + the PS's "fully on-device" requirement.

---

## 21. Demo script & backup plans

**~4 min live + buffer. Rehearse twice on the actual device. The backend badge always shows the truth.**

- **0:00–0:30 — Honest open (inoculation).** "Qualcomm gave every team the same kit — pose on the NPU with
  a runtime toggle. So we won't pretend pose-on-NPU is our innovation. We built the layer it stops at."
  *(One slide: the given-vs-built ledger. Buys immunity to the killer question.)*
- **0:30–1:30 — Live coaching.** Do squats. Skeleton overlay, auto-detected "SQUAT", rep counter ticking,
  per-rep score flashing. "Detected, counting, scoring — all on the device, no cloud."
- **1:30–2:15 — Emotional wow: a bad rep gets corrected.** Do a shallow squat / knees caving. Score drops,
  one cue appears (spoken): **"Go deeper — hips below knees."** "One fix at a time, like a real coach."
- **2:15–3:15 — Technical wow: live CPU↔NPU.** Toggle NPU→CPU. Latency/FPS visibly change. "Same model,
  same app — Hexagon NPU vs CPU, measured live, real numbers from this device." Flip back; smoothness returns.
- **3:15–3:45 — Summary + scale tease.** Session summary (reps, avg form, top fault, streak). "Five
  exercises today; adding a sixth is one module; the hard part — pose on the NPU — Qualcomm solved, we built
  the coach on top."
- **3:45–4:00 — Close.** "Private, offline, on-device form coaching — the half of this problem the reference
  leaves to you. That's Vyāyāma."

**Backups (each with a pre-decided trigger + rehearsed transition):** (1) no root/HTP → full demo on CPU +
pre-recorded NPU clip side-by-side; (2) model won't load → `MockPoseEngine` drives the full coaching stack
on recorded keypoints; (3) camera/lighting fails → floor marker, else narrated backup video; (4) total
freeze → 3-min backup video, calm narration.

**Demo-day kit checklist (pack/verify before walking up):**
- [ ] Venue device: charged, `adb root` + `setenforce 0` done (re-run after any reboot), APK installed, correct-arch DLC+skels in place, backend badge reads NPU
- [ ] Laptop: backup video (NPU mode, recorded on this device class) + slides (lead with the given-vs-built ledger) + the offline QAIRT/AI-Hub profile screenshot
- [ ] Second phone: side-by-side NPU-clip for the no-root fallback
- [ ] USB cable + a tripod/stand + floor marker tape (deterministic framing)
- [ ] Device in airplane mode (proves "no cloud" live) + `MockPoseEngine` build ready as the model-won't-load fallback
- [ ] Two rehearsals done, including each backup transition

---

## 22. Risk register & hidden-catch landmines

The real ones. Each: **what · when it bites · find it EARLY · defuse.**

| # | Landmine | When it bites | Find early | Defuse |
|---|---|---|---|---|
| ~~**L1**~~ | **RESOLVED — device confirmed: QIDK / Snapdragon 8 Elite (V79) / Android** (organizer erratum on PS) | — | confirmed | Target the 8-Elite/V79 build: the reference `VisionSolution4` **is validated on 8 Elite** → its `.dlc` + skel `.so` are the right ones. Device-agnostic ladder retained as insurance. |
| **L2** | HTP needs `adb root` + `setenforce 0` (userdebug only) | when proving NPU | confirm at workshop | **QIDK is a developer kit → almost certainly userdebug/rootable** (unlike a locked retail phone); honest DSP→GPU→CPU fallback if not |
| **L3** | DLC gen needs Ubuntu 22.04 + Docker (18 GB); you're on Windows | model generation | Day-1 env setup | WSL2 Ubuntu; **OR AI Hub Path B (no Docker/Linux/COCO)** |
| **L4** | Wrong-arch DLC/`.so` silently falls back to CPU | NPU validation | first on-device run | **target V79** (8 Elite): use the reference's 8-Elite `.dlc` + skels, or an AI Hub 8-Elite DLC; **`backend()` reports truth** |
| **L5** | <80 ms/frame latency budget | latency claim | first on-device profile | **trivial on V79** (8 Elite = top-tier NPU; HRNet pose ≈ low-single-digit ms); detect-every-N + single-stage pose are spare headroom; measured-only |
| **L6** | INT8 quant needs COCO calibration (GBs) | DLC gen | Day-1 (Docker route) | COCO val2017 subset (~1 GB); or Path B (already quantized) |
| **L7** | Fork build finicky (AS Panda 4 2025.3.4, OpenCV 4.13.0, NDK r26c, aar path) | bring-up | Phase 1 | **get UNMODIFIED reference running before any of our code**; if it fights >1 day, pivot to Path B + thin SNPE runner behind same `PoseEngine` |
| **L8** | Device-free dev needs recorded keypoint fixtures that don't exist | Phase 1 (B blocked) | it's a Phase-0 task | record Core-5 sessions Day 1 (reference dump / MediaPipe on laptop) → CSV |
| **L9** | Train/serve skew (Python features ≠ Kotlin features) | learned-model accuracy | parity tests in CI | one feature def mirrored both sides + parity tests; learned head is optional/additive |
| **L10** | 3-person merge hell | integration | Phase 0 | freeze `api/`; per-track packages/branches; integrate via interfaces only; mock-everything |

**Master de-risks (structural):** (a) **AI Hub Path B** neutralizes L3/L4/L6, de-risks L7. (b)
**Rules-first** neutralizes L9. (c) **Mock-everything + frozen interfaces** neutralizes L10 and keeps the
app demoable on partial completion. (d) **Honest `backend()` + measured-only numbers** neutralizes the
"NPU claim collapses under questioning" risk. (e) **Prove the NPU path on ≥2 different Hexagon versions in
prep** (a rooted personal Snapdragon + the workshop QIDK 8-Elite/V79) → high confidence, plus an
NPU-mode backup video recorded on the closest device class (8 Gen 2/3 ≈ V73/V75).

---

## 23. Timeline — frontend-LAST phased plan

Discipline: anything that *can* be done without the device *must* be done before it; only device-bound work
waits for the kit.

**Pre-workshop prep (Day 1 — env + reference + scaffold):** WSL2 Ubuntu 22.04 + Docker (or commit to Path B);
clone `quic/qidk`, `run_qairt_docker.sh`; get the **unmodified reference building** (and running on a
personal phone, CPU/GPU); stand up the Vyāyāma repo — `api/` interfaces + domain types frozen, mock-fallback
DI, **all mocks**, the **MockPoseEngine + recorded Core-5 CSV fixtures**, CI green.

**Pre-workshop prep (Day 2 — the brain, device-free):** `FeatureExtractor` (angles/normalize/smoothing) +
unit tests; the rep FSM + per-exercise analyzers tuned on recorded data; form rules + scoring + one-cue
engine + session summary + gamification skeleton; rule recognizer + is-exercising gate; (if ahead) start the
learned head; `.dlc` conversion dry-run targeting the venue Hexagon class; start backup assets + deck.

**Phasing across the build (frontend genuinely last):** Phase 0 shared foundation → Phase 1 parallel core
(A: reference on device · B: intelligence on mocks · C: orchestrator/DI/Benchmark on mocks) → Phase 2 real
integration with a minimal debug overlay → **Phase 3 frontend** (the polished Compose UI) → Phase 4 polish +
NPU backup video + rehearsal + deck with real numbers.

**Device-gated (only at/after the workshop):** confirm device → regenerate DLC + bundle correct-arch skels →
root/`setenforce` → real NPU validation + latency numbers → board camera/display adaptation if RB3/QCS6490.
~70–80% of the engineering is done device-free.

**Hackathon-day hour budget (rough, ~18 h overnight build; adjust to the actual format):**
| Block | Hours | Work |
|---|---|---|
| Phase 1 — device bring-up | ~3–4 h | confirm SoC/Hexagon; root + `setenforce`; build/run reference on venue device; correct-arch DLC + skels |
| Phase 2 — real integration | ~4–6 h | wire real `PoseEngine` through the seam; live keypoints; honest CPU-vs-NPU instrumentation; tune thresholds on real bodies |
| Phase 3 — frontend | ~4–6 h | the polished Compose UI over real data; projector-legibility pass |
| Phase 4 — polish + demo | ~3–4 h | NPU-mode backup video on venue device; deck with real numbers; rehearse twice incl. backups |

**Pre-hackathon effort estimate (the device-free build, done before the event):** Phase-0 foundation ~½ day;
intelligence layer + tests ~1–1.5 days; orchestrator/DI/Benchmark ~½ day; Compose UI (mock-driven) ~1 day;
ml pipeline (optional) ~1 day; tools + CI + docs ~½ day. All parallelizable across the 3 tracks.

---

## 24. Honest limitations (what we explicitly do NOT claim)

- **2D single-camera pose has blind spots** — depth ambiguity, occlusion, extreme camera angles. Form
  feedback is **guidance, not medical advice.**
- **Rule-based form is per-exercise** — an honest scalability ceiling; the learned head is the scale path.
- **Recognition is the Core 5** — `UNKNOWN` for anything else, by design.
- **NPU proof is venue-device-bound** — retail phones (no root) run CPU/GPU; we label it honestly.
- **Latency numbers are device-specific** — only on-device profiling counts; the deck shows measured figures.
- **Single-person** — multi-person scenes are out of scope for v1.

**On-screen honesty strings (the app says what it is):** a one-time/footer line *"Form guidance, not medical
advice."*; the backend badge shows the literal reached tier, and on fallback *"NPU requested → running on
CPU (DSP unavailable on this device)"*; an `UNKNOWN` exercise shows *"Not sure what exercise — keep going"*
rather than a guess; a low-confidence frame shows *"Step fully into frame"* rather than scoring garbage.
These strings are not cosmetic — they are the honesty contract made visible to the judge.

---

## 25. Repo layout

```
docs/
  bible.md                  this file — source of truth
  device-runbook.md         adb root / setenforce / DLC-regen / skel-bundling per device
  setup-checklist.md        QAIRT Docker / WSL2 / NDK r26c / OpenCV SDK / AS Panda 4 / fork bring-up
android/                    Kotlin app (fork of VisionSolution4, repackaged)
  app/src/main/
    cpp/                    KEEP VERBATIM — inference.cpp · inference_helper.cpp ·
                            posedetectionYoloNAS.cpp · inc/zdl/ · CMakeLists.txt
    jniLibs/arm64-v8a/      libSNPE.so + skels V68/69/73/75(/79)   [runbook; gitignored]
    assets/                 *.dlc (per device) · recorded_sessions/*.csv (mock + test fixtures)
    kotlin/io/vyayama/
      api/                  domain types (PoseFrame, NormalizedPose, ExerciseType, RepEvent,
                            FormFeedback, CoachState, Backend, PerfSnapshot) + the 7 interfaces
      pose/                 SnpePoseEngine (JNI facade) · PoseJni.kt
      camera/               RealCameraEngine (CameraX YUV, zero-alloc)
      intelligence/feature/ RealFeatureExtractor
      intelligence/classify/ Rule/Learned/Fused ExerciseClassifier
      intelligence/reps/    StateMachineRepCounter (+ per-exercise configs)
      intelligence/form/    RuleFormAnalyzer (+ per-exercise rule cards)
      bench/                RealBenchmark
      coach/                CoachOrchestrator
      mocks/                MockPoseEngine (CSV replay) + one mock per interface
      ui/                   Compose: CameraScreen, overlay (Canvas), HUD, LatencyPanel,
                            ExercisePicker, gamification, Settings
      App.kt                hand-rolled DI + per-interface mock-fallback
    test/kotlin/            intelligence unit tests vs recorded_sessions/*.csv
ml/                         optional learned classifier + dataset tooling
  src/{data,features,models}, train.py, eval.py, export_tflite.py, reference_features.py
  tests/                    feature-parity tests (python vs pinned vectors)
tools/
  keypoint_recorder/        capture recorded_sessions CSVs from the live app
  threshold_tuner/          desktop harness: run rules over CSVs, print rep accuracy → tune constants
.github/workflows/          android assemble + unit tests · python ml tests
CLAUDE.md · README.md
```

---

## Appendix A — Interface contracts (copy-implementable)

```kotlin
// ---- domain types ----
enum class Backend { AUTO, NPU, GPU, CPU, MOCK }
enum class ExerciseType { NONE, SQUAT, PUSHUP, LUNGE, BICEP_CURL, JUMPING_JACK, UNKNOWN }
enum class RepPhase { TOP, DESCENDING, BOTTOM, ASCENDING }

class PoseFrame(                     // crosses the seam
  val keypoints: FloatArray,         // 51 = 17 * (x,y,conf), source-frame px
  val srcWidth: Int, val srcHeight: Int,
  val backend: Backend,              // honest, what was reached
  val detLatencyMs: Float, val poseLatencyMs: Float,
  val timestampNs: Long
)
class NormalizedPose(
  val jointAngles: FloatArray,       // fixed indexed set; NaN if ungated
  val keypointsNorm: FloatArray,     // hip-centered, torso-normalized (x,y)
  val confidences: FloatArray,
  val visible: Boolean
)
class ExerciseState(val type: ExerciseType, val confidence: Float,
                    val source: Source) { enum class Source { RULES, LEARNED, FUSED } }
class RepEvent(val exercise: ExerciseType, val index: Int, val phase: RepPhase,
               val completedNow: Boolean, val partial: Boolean, val formScore: Int)
class Cue(val severity: Int, val jointRef: String, val message: String)
class FormFeedback(val cues: List<Cue>, val perRepScore: Int,
                   val symmetryPct: Float, val depthPct: Float, val tempoScore: Float)
class PerfSnapshot(val backend: Backend, val poseMsP50: Float, val poseMsP90: Float,
                   val endToEndMs: Float, val fps: Float)
class CoachState(val pose: PoseFrame?, val normalized: NormalizedPose?,
                 val exercise: ExerciseState, val rep: RepEvent?,
                 val feedback: FormFeedback?, val perf: PerfSnapshot)
class CameraFrame(                   // produced by CameraEngine, consumed by PoseEngine
  val rgb: java.nio.ByteBuffer,      // direct buffer, PRE-ALLOCATED + reused (never per-frame alloc)
  val width: Int, val height: Int, val timestampNs: Long)
class PoseWindow(                    // ring buffer of recent NormalizedPose, fed to the classifier
  val frames: ArrayDeque<NormalizedPose>, val capacity: Int) {
  fun push(p: NormalizedPose) { if (frames.size == capacity) frames.removeFirst(); frames.addLast(p) }
}
class AbResult(val npuMs: Float, val cpuMs: Float, val npuFps: Float, val cpuFps: Float,
               val speedup: Float, val backendReached: Backend)

// ---- interfaces (each has a real impl + a mock) ----
interface PoseEngine {
  fun init(backend: Backend = Backend.AUTO): Backend   // returns backend ACTUALLY reached
  suspend fun infer(frame: CameraFrame): PoseFrame
  fun backend(): Backend
  fun isReady(): Boolean
  fun forceBackend(b: Backend): Backend                // re-init pinned; for live A/B
  fun lastPerf(): PerfSnapshot
  fun close()
}
interface CameraEngine { fun start(owner: LifecycleOwner); fun attachPreview(view: Any?)
  fun stop(); val frameBus: SharedFlow<CameraFrame>; fun close() }
interface FeatureExtractor { fun normalize(pose: PoseFrame): NormalizedPose }
interface ExerciseClassifier { fun classify(window: PoseWindow): ExerciseState; fun reset() }
interface RepCounter { fun update(ex: ExerciseType, p: NormalizedPose, tsNs: Long): RepEvent
  fun count(): Int; fun reset() }
interface FormAnalyzer { fun analyze(ex: ExerciseType, repWindow: List<NormalizedPose>,
  phase: RepPhase): FormFeedback; fun reset() }
interface Benchmark { fun onInference(b: Backend, detMs: Float, poseMs: Float, totalMs: Float)
  fun snapshot(): PerfSnapshot; fun startAB(); fun abResult(): AbResult?; fun reset() }

// ---- the native binding (the ONLY JNI surface; SnpePoseEngine wraps these) ----
// Implemented in cpp/posedetectionYoloNAS.cpp (kept verbatim from the fork, signatures adapted).
external fun nativeInit(assetDir: String, runtime: Char): Int   // 'C'/'G'/'D'; returns backend code reached
external fun nativeInfer(rgb: ByteBuffer, w: Int, h: Int, out: FloatArray): Int  // fills out[51+5]; 0=ok
external fun nativeClose()
// `out` is ONE pre-allocated FloatArray(56) reused every frame — no per-frame JNI object allocation.
```

---

## Appendix B — Per-exercise rule cards (the biomechanics IP)

Each card: keypoints used · rep-FSM (primary θ, θ_top, θ_bottom) · form rules (priority order) · cues.

- **SQUAT** — kpts 5,6,11,12,13,14,15,16. FSM: θ=avg knee, top 165°, bottom 95°. Rules (priority): depth
  (knee≤95°) → "go deeper, hips below knees"; valgus (knee-x inside ankle-x) → "knees out"; torso lean
  (>~45° from vertical) → "chest up"; symmetry (|L−R|>15°) → "even out your weight"; tempo. 
- **PUSHUP** — kpts 5,6,7,8,9,10,11,12,15,16. FSM: θ=avg elbow, top 160°, bottom 95°; torso must stay
  horizontal. Rules: depth (elbow≤95°) → "lower your chest"; hip sag/pike (hip off shoulder–ankle line) →
  "keep your hips in line"; elbow symmetry; tempo.
- **LUNGE** — kpts 11,12,13,14,15,16 + torso. FSM: θ=front knee, top 165°, bottom 95°. Rules: front-knee
  depth ~90°; knee past toes (knee-x beyond ankle-x) → "knee behind your toes"; torso upright; L/R balance.
- **BICEP_CURL** — kpts 5,6,7,8,9,10,11,12. FSM: θ=active elbow, top 155°, bottom 50°; shoulder fixed.
  Rules: ROM (full extend→curl) → "full range"; shoulder/elbow drift → "stop swinging"; symmetry; tempo.
- **JUMPING_JACK** — kpts 0,5,6,9,10,15,16. FSM: θ=openness composite, closed↔open. Rules: arm full
  overhead; leg full spread; arm/leg sync → "arms and legs together"; rhythm.

*(Thresholds are starting values; tuned on recorded data via `tools/threshold_tuner` and pinned as test
vectors. Filled with measured values in iteration 2–3.)*

---

## Appendix C — Glossary

**SNPE** Snapdragon Neural Processing Engine — high-level C++/Java inference API; loads `.dlc`, dispatches to
CPU/GPU/HTP. **QAIRT** Qualcomm AI Runtime — the unified SDK (SNPE + QNN). **QNN** AI Engine Direct — the
low-level graph API. **`.dlc`** Deep Learning Container — Qualcomm's compiled model format. **HTP / Hexagon
NPU** the fixed-function neural accelerator; runs INT8/INT16; lowest latency/power. **V68 / V79** Hexagon
architecture versions (QCS6490 ≈ V68-class; SM8750/8-Elite = V79) — DLC/skel libs are arch-specific.
**QIDK** Qualcomm Innovators Development Kit — a premium-Snapdragon dev board running Android. **RB3 Gen 2 /
QCS6490** a robotics/IoT SBC (Hexagon 770, 12 TOPS, Android or Linux, HDMI + USB camera). **AIMET** AI Model
Efficiency Toolkit (quantization). **AI Hub** aihub.qualcomm.com — catalogue of pre-converted `.dlc` models.
**SELinux permissive / userdebug** the engineering-build state that allows app DSP access (`setenforce 0`).
**COCO 17 keypoints** the standard body-joint set. **FSM** finite state machine. **EMA** exponential moving
average. **Valgus** knees collapsing inward. **τ_kp** the keypoint-confidence gate threshold.

---

## Appendix D — Self-audit log (the 3 brutal rating iterations)

Calibrated, lower-anchor-by-default scoring. Brutality rules applied: torn → lower; every score carries a
gap; vague evidence capped at 75; an unsurvivable claim caps Honesty at 70; zero documented failure modes
caps an aspect at 90. **Overall = mean of 9.** Target: >93 after iteration 3, no aspect <90.

| # | Aspect | Iter 1 (v1) | Iter 2 (v2) | Iter 3 (v3) | Δ | What moved it across the iterations |
|---|---|---|---|---|---|---|
| 1 | Technical depth | 80 | 90 | **92** | +12 | pinned learned head (TCN 32×61→6), JNI signatures, camera-ownership + fallback, openness + is-exercising formulas, worked rep trace |
| 2 | PS alignment | 88 | 92 | **95** | +7 | is-exercising upgraded to a full FSM; §4 mapping complete; all 3 parts rigorous |
| 3 | Feasibility | 84 | 85 | **93** | +9 | hackathon-day hour budget + pre-event effort estimates; device-free majority; mock-everything |
| 4 | Differentiation | 90 | 90 | **94** | +4 | sharpened vs a concrete reskin baseline; the ledger + the Q&A weapon |
| 5 | Robustness/risk | 88 | 90 | **94** | +6 | Path-B model choice, camera fallback, 2-device-proof de-risk, full failover table |
| 6 | Completeness | 82 | 88 | **95** | +13 | CameraFrame/PoseWindow/AbResult/JNI defined; worked trace; this self-audit log; build checklist (App. E) |
| 7 | Demo readiness | 87 | 87 | **94** | +7 | demo-day kit checklist added to the minute-by-minute + 4 backups |
| 8 | Clarity/actionability | 86 | 89 | **95** | +9 | build-order checklist; pinned specs; copy-implementable interfaces; next-actions |
| 9 | Honesty | 93 | 93 | **96** | +3 | on-screen honesty strings; measured-only enforced; every limit owned before a judge can raise it |
| | **Average** | **86.4** | **89.3** | **94.2** | **+7.8** | iteration deltas +2.9 / +4.9 (calibrated — no jump >12); final >93, min aspect 92 |

**Known residual gaps (honest, for a future v4):** a 2nd worked rep trace (push-up hip-sag) and an explicit
MediaPipe 33→17 keypoint mapping table would push Technical depth toward 96; a real prep day-by-day with
named owners would push Feasibility toward 96. None are blocking — they are polish, not correctness.

---

## Appendix E — Build-order checklist (execute top-to-bottom)

Phase 0 (shared) → 1 (parallel) → 2 (integration) → 3 (frontend) → 4 (polish). Tick as you go.
```
[ ] P0  Repo scaffold: Gradle + version catalog + manifest (CAMERA only, no INTERNET) + settings
[ ] P0  api/ — freeze all domain types + the 7 interfaces + the JNI externs (Appendix A)
[ ] P0  All mocks incl. MockPoseEngine (CSV replay) + mock-fallback DI (App.kt)
[ ] P0  Record Core-5 keypoint CSV fixtures (unblocks Brain track) ; CI green (assembleDebug + tests)
[ ] P1-A  Fork quic/qidk VisionSolution4; build UNMODIFIED; run on a phone (CPU/GPU); confirm runtime toggle
[ ] P1-B  FeatureExtractor (angles/normalize/EMA) + unit tests vs CSVs
[ ] P1-B  RuleExerciseClassifier + is-exercising gate + RepCounter FSM + RuleFormAnalyzer + tests
[ ] P1-C  CoachOrchestrator (StateFlow) + RealBenchmark (EMA/p50/p90/fps) on mocks
[ ] P2-A  SnpePoseEngine: wire the seam (float[51+5]); real keypoints; honest backend(); forceBackend A/B
[ ] P2-B  Tune thresholds on recorded data (threshold_tuner); finalize per-exercise rule cards
[ ] P2-B  (optional) LearnedExerciseClassifier: train (ml/) → export TFLite → FusedExerciseClassifier
[ ] P2-C  Integrate A+B behind the orchestrator with a minimal debug overlay; prove end-to-end data flow
[ ] P3-C  Compose UI: skeleton Canvas overlay, HUD, rep counter, cues, latency A/B panel, gamification, summary
[ ] P4   Tune on real bodies; record NPU-mode backup video; deck with real numbers; rehearse twice + backups
```
**Device-gated (only at/after the workshop):** confirm SoC → correct-arch DLC + skels → root/`setenforce` →
NPU validation + real latency numbers → board (USB-cam/HDMI) adaptation if RB3/QCS6490.
