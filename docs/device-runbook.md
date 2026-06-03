# Vyāyāma — Device Runbook (device-day = mechanical, not debugging)

The procedure for when the kit arrives. If prep (`setup-checklist.md`) is done, this is a checklist,
not a problem-solving session. Companion to `bible.md` §10/§22/§23.

---

## Step 0 — Identify the device (do this FIRST, at the workshop)
Plug in, then:
```bash
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.board.platform
adb shell getprop ro.soc.model          # e.g. SM8750 (8 Elite, V79) vs QCS6490 (V68-class)
adb shell cat /proc/cpuinfo | head
```
**Branch (bible §22 L1):**
- **Phone / 8-Elite (V79):** the reference DLC/skels work as-is — best case.
- **QCS6490 / RB3 board (V68-class):** you need V68 skels + a V68-matched DLC, and the display is HDMI
  + the camera is USB/MIPI (adapt the UI — see Step 6). Confirm with the workshop hosts.

## Step 1 — Enable the HTP (NPU) runtime  (bible §10.5 / §22 L2)
```bash
adb disable-verity
adb reboot
# wait for reboot, then:
adb root
adb remount
adb shell setenforce 0       # SELinux permissive — REQUIRED for DSP/HTP access
```
> `setenforce 0` **RESETS ON EVERY REBOOT.** Re-run `adb root && adb shell setenforce 0` after any reboot.
> If `adb root` fails (locked/retail build) → NPU is unreachable here; demo on CPU/GPU and use the prep
> NPU backup video. Do NOT fake an NPU number.

## Step 2 — Get the right model + skel libs for THIS Hexagon  (bible §22 L4)
- **Skels:** ensure `app/src/main/jniLibs/arm64-v8a/` has the matching `libSnpe…V{NN}Skel.so` (we bundle
  V68/69/73/75/79). The runtime selects the match; a mismatch = silent CPU fallback.
- **DLC (pick one):**
  - Path B (fast): the AI Hub `.dlc` you pre-downloaded for this exact chip → drop into `assets/`.
  - Path A (regen): re-run `GenerateDLC.ipynb` targeting this Hexagon arch.
- Verify the DLC tensor names match the app: `snpe-dlc-info -i <model>.dlc`.

## Step 3 — Install + sanity-check the backend is HONEST
```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```
Open the app → Settings/HUD → the **Backend badge must read "NPU (Hexagon HTP, V__)"**. If it reads GPU/CPU:
the skel/DLC arch is wrong or `setenforce 0` didn't take → fix Step 1/2. **Never demo claiming NPU if the
badge says otherwise** — the badge is the truth.

## Step 4 — Wire real pose + measure  (bible §10.3)
- Flip `PoseEngine` from `MockPoseEngine` to `SnpePoseEngine` (DI auto-detects the DLC asset; or force it).
- Confirm live keypoints track a person.
- Open the latency panel → **warm up** (let ~1 s pass) → read p50/p90 pose-infer ms + FPS.
- Run the A/B: NPU sweep, then `forceBackend(CPU)`, warm up, CPU sweep → record both. These are THE numbers
  for the deck (no others are valid).

## Step 5 — Tune thresholds on real bodies
Have 2–3 people do each Core-5 exercise. Use `tools/threshold_tuner` over freshly recorded clips to adjust
θ_top/θ_bottom and form cutoffs if reps mis-count or cues misfire. Commit the tuned constants.

## Step 6 — Board-only (QCS6490/RB3): display + camera
- Display: connect HDMI; the Compose UI renders to the attached display.
- Camera: the board uses a USB/MIPI camera — point `CameraEngine` at the correct camera id; verify frames.
- If touch isn't available, ensure the demo flow works with the on-board controls / a mouse.

## Step 7 — Record the backup video (NPU mode) + rehearse
On THIS device class, record a clean full-demo run in NPU mode (coaching + the live A/B). This is the
insurance for the finale. Rehearse the demo twice including the backup transitions (bible §21).

---

## Fast fault table
| Symptom | Cause | Fix |
|---|---|---|
| Backend badge = CPU after Step 1–2 | `setenforce` reset by reboot / wrong skel arch | re-run `adb root && setenforce 0`; check skel `.so` matches `ro.soc.model` |
| App falls to MockPoseEngine | DLC asset missing/wrong name | drop the correct `.dlc` into `assets/`; check filenames |
| Reps double-count | thresholds untuned / jitter | tune θ gap + min-rep-duration (Step 5); confirm EMA smoothing on |
| `adb root` denied | locked/retail build | demo on CPU/GPU + prep NPU backup video; do not fake numbers |
| Skeleton offset on board | wrong camera resolution/orientation | set camera id + rotation in `CameraEngine` |
