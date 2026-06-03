<div align="center">

# व्यायाम · Vyāyāma

### On-device **AI Form Coach** for Snapdragon — *skeletons in, coaching out.*

**Hack4SoC 3.0 · Qualcomm Edge AI Track · FitSense**
Real-time exercise detection + recognition + **rep counting + per-rep form scoring + corrective coaching**, fully on-device, with a live **CPU-vs-NPU** comparison.

`Kotlin` · `Jetpack Compose` · `CameraX` · `SNPE / QAIRT` · `Hexagon NPU` · `no cloud`

</div>

---

## The idea in one line
Qualcomm's reference app ends at **17 pose keypoints on the NPU**. **Vyāyāma begins there** — turning those keypoints into a transparent biomechanics coach that counts your reps, scores each one, and tells you the *one* thing to fix.

## What it does
- **Detects** when you're exercising and **recognizes** which of the **Core 5** (squat, push-up, lunge, bicep curl, jumping jack).
- **Counts reps** with a hysteresis state machine (rejects partials + jitter).
- **Scores form 0–100** per rep and surfaces **one corrective cue** ("go deeper", "knees out", "hips up").
- **Proves the NPU** — flip CPU↔NPU live and watch the latency change; the backend badge never lies.
- **Private** — no cloud, no internet, nothing leaves the device.

## Architecture (the seam)
```
Camera ─▶ PoseEngine (C++/JNI/SNPE) ─ float[51+5] ─▶ FeatureExtractor ─▶ Classifier
                                       THE SEAM       ─▶ RepCounter ─▶ FormAnalyzer ─▶ CoachState ─▶ Compose UI
```
Everything above the seam is pure, device-free Kotlin → **the whole brain is unit-tested with no device, no NPU, no camera.** Every module has a mock; the app runs with any subset real.

## Run it now (mock mode — no hardware)
```bash
cd android
./gradlew :app:assembleDebug        # builds the mock-mode app
./gradlew :app:testDebugUnitTest    # intelligence unit tests
```
With no `.dlc` present the app boots `MockPoseEngine` (a synthetic squatter) and runs the **full demo** — skeleton overlay, exercise label, rep counter, form cues, and the latency panel — on any phone/emulator.

## Proven, not just written
```bash
python tools/threshold_tuner/verify_core.py   # 13/13: angle math, rep FSM, classifier discrimination
python ml/tests/test_parity.py                 # 5/5: Python features == Kotlin features, invariant
```

## Repo
```
docs/      bible.md (source of truth) + (bible)project-vyayama.pdf + setup-checklist + device-runbook
android/   the app — api · pose(JNI) · camera · intelligence(feature/classify/reps/form) · bench · coach · ui · mocks
ml/        optional learned classifier (TCN) + the parity contract + dataset tooling
tools/     bible PDF builder · threshold tuner / core-proof harness
```

## Status
✅ Bible (self-rated **94.2/100**, 3 iterations) · ✅ full device-free app + intelligence (proven) · ✅ mock-mode demo · ✅ ml pipeline.
🔧 Device-gated (at the venue): fork the reference C++, drop the device-matched `.dlc` + Hexagon skel libs, `adb root` + `setenforce 0`, then flip `MockPoseEngine`→`SnpePoseEngine`. See `docs/device-runbook.md`.

> **The bible is the source of truth.** See `docs/bible.md` (and the PDF). Form guidance, not medical advice.
