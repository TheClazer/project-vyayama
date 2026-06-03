# ml/ — the optional learned exercise classifier

**Optional / additive** (bible §9.3–9.4). The **rule-based classifier ships the demo**; this learned
head is the robustness/scale upgrade. If the `.tflite` is absent, the app is silently rules-only.

## The parity contract (why this won't have train/serve skew)
`src/features/reference_features.py` is a **byte-for-byte mirror** of the Kotlin
`RealFeatureExtractor.kt`. The model trains on the exact 61-d feature vector the device produces
(10 joint angles + 34 normalized keypoints + 17 confidences). Verified:

```
python ml/tests/test_parity.py     # angle math + scale/translation invariance + 61-d vector  → ALL PASS
```

## Pipeline
```
1. Get keypoint sequences (17-COCO [x,y,conf]) per exercise, as <root>/<CLASS>/*.npy of shape (T,17,3):
     - Fit3D / InfiniteRep skeletons (re-mapped to 17-COCO), or
     - run a pose model (MediaPipe/HRNet) over workout videos (Kaggle "workout/exercise" sets).
   CLASSES = NONE, SQUAT, PUSHUP, LUNGE, BICEP_CURL, JUMPING_JACK
2. Build feature windows:   python ml/src/data/keypoints.py <root> dataset.npz
3. Train the TCN:           python ml/src/train.py --data dataset.npz --epochs 30
4. Export:                  python ml/src/export_tflite.py --ckpt runs/best.pt
                            then ONNX→TFLite (ai-edge-torch or onnx2tf)
5. Wire on device:          drop exercise_classifier.tflite into android assets; implement a
                            LearnedExerciseClassifier (TFLite, input [1,32,61]) behind
                            FusedExerciseClassifier (bible §9.4). NaN→0.0 on both sides.
```

## Datasets (open/free, practical)
- **Fit3D** — fitness skeletons + labels (50+ exercises); re-map to 17-COCO.
- **InfiniteRep** — rep-labeled exercise sequences.
- **Kaggle workout/exercise recognition** — videos; extract keypoints with a pose model.
Keep only the Core-5 classes (+ a NONE/idle class) for v1.

## Model
`src/models/tcn.py` — causal 1-D Temporal CNN, 32-frame window, ~64 channels, 3 dilated blocks,
6-class softmax. <5 MB, runs on **CPU TFLite** at a few Hz (NPU budget stays for pose).
