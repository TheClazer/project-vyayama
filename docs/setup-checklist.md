# Vyāyāma — Setup Checklist (do before the hackathon)

Exact, copy-paste steps for the **environment + reference bring-up**. Companion to `bible.md`.
These are the **user-action / machine-gated** items (Claude can't do them) — but none block the
**mock-mode build/demo**, which runs with zero hardware. Versions are pinned from the QIDK PDF.

> Pinned: **QAIRT SDK v2.46.0.260424 · Android NDK r26c · Ubuntu 22.04 · Docker (≥18 GB free) ·
> Android Studio Panda 4 v2025.3.4 · ADB.**

---

## 0. Two routes — pick based on time/risk
- **Route A (full):** WSL2 Ubuntu + Docker → generate your own `.dlc` from the reference. Most control.
- **Route B (fast escape hatch):** skip Docker entirely; download a pre-quantized `.dlc` from **AI Hub**
  for your target chip. Needs only an AI Hub account. **Recommended if Route A fights you (bible §22 L3).**

You can do Route B first to get unblocked, and Route A later for a device-matched/regenerated model.

---

## 1. WSL2 + Ubuntu 22.04 (Windows host) — for Route A
```powershell
wsl --install -d Ubuntu-22.04        # in an elevated PowerShell; reboot if prompted
wsl --set-default-version 2
```
Then inside Ubuntu: `sudo apt update && sudo apt install -y docker.io git python3-pip` and
`sudo usermod -aG docker $USER` (log out/in). Confirm: `docker run hello-world`. Ensure ≥18 GB free.

## 2. Clone the reference + build the QAIRT Docker (Route A)
```bash
git clone https://github.com/quic/qidk.git
cd qidk/Tools/qairt_docker
./run_qairt_docker.sh            # FIRST run: builds image, downloads QAIRT v2.46.0.260424 + NDK r26c (30+ min)
# inside the container, verify:
echo $QAIRT_SDK_ROOT
qnn-net-run --version
```
(Subsequent runs start in seconds. `exit` leaves the container.)

## 3. Generate the DLCs (Route A, inside Docker)
```bash
cd Solutions/VisionSolution4-PoseEstimation/Generate_models
jupyter notebook --ip=0.0.0.0 --no-browser --allow-root
# open GenerateDLC.ipynb → set the COCO dataset path (val2017 ~1 GB is enough for calibration)
# Kernel → Restart & Run All  → emits to app/src/main/assets/:
#   Quant_yoloNas_s_320.dlc   (person detector)
#   hrnet_axis_int8.dlc       (pose, 17 kpt)
```

## 4. Resolve Android deps on the host (Route A)
```bash
export QAIRT_SDK_ROOT=/path/to/qairt/2.46.0.260424
cd Solutions/VisionSolution4-PoseEstimation
bash resolveDependencies.sh
# downloads OpenCV 4.13.0 Android SDK; copies SNPE headers→app/src/main/cpp/inc/zdl/,
# snpe-release.aar, and .so libs→app/src/main/jniLibs/arm64-v8a/
```

## 5. AI Hub (Route B — the no-Docker path)
1. Create an account at **aihub.qualcomm.com**.
2. Computer Vision → **Pose Estimation** → choose your device (SM8750 or QCS6490).
3. Download the **pre-INT8-quantized `.dlc`** (it shows pre-measured latency on that chip).
   - **`HRNetPose-Quantized` (17 kpt)** = drop-in (matches our feature layer; no remap).
   - `MediaPipe-Pose` (33 kpt, single-stage) = optional richer/faster upgrade (needs a 33→17 map).
4. Read the model card (input shape, normalization, tensor names) — needed for pre/post-processing.

## 6. Android Studio + build the reference
1. Install **Android Studio Panda 4 Version 2025.3.4** (exact — other versions may break sync).
2. `File → Open → Solutions/VisionSolution4-PoseEstimation`; wait for Gradle sync.
3. `Build → Make Project` → APK at `app/build/outputs/apk/debug/app-debug.apk`.
4. Run it on your phone (CPU/GPU is fine pre-event) → confirm the skeleton tracks + the runtime toggle works.

## 7. Build the Vyāyāma app (mock mode — no kit needed)
```bash
cd <vyayama-repo>/android
./gradlew :app:assembleDebug          # builds the mock-mode app
./gradlew :app:testDebugUnitTest      # intelligence unit tests
```
Install on any phone/emulator → the full demo runs on recorded keypoints (skeleton + label + reps +
cues + latency panel), no kit attached.

---

## What's gated on what (so nothing is a surprise)
| Item | Gated on | Escape hatch |
|---|---|---|
| Generate own `.dlc` | WSL2 + Docker (18 GB) | Route B (AI Hub) |
| Device-matched DLC + skels | Knowing the venue SoC (workshop) | bundle all arches; Path B per device |
| NPU execution | rooted/userdebug device (`setenforce 0`) | CPU/GPU + honest label; venue device |
| Reference build | AS Panda 4 v2025.3.4 + OpenCV 4.13.0 | keep `cpp/` verbatim; exact versions |
| **Mock-mode app + demo** | **nothing** | — runs today on any phone |

See `device-runbook.md` for the device-day procedure and `bible.md` §22 for the full landmine register.
