"""Export the trained TCN to ONNX, then (offline) to TFLite for on-device CPU inference.

Run:  python ml/src/export_tflite.py --ckpt runs/best.pt --onnx exercise_classifier.onnx

Then convert ONNX → TFLite via one of:
  • ai-edge-torch (recommended): export straight from torch to .tflite, OR
  • onnx2tf:  onnx2tf -i exercise_classifier.onnx -o tf_out   # yields a .tflite
Drop the .tflite into android/app/src/main/assets/exercise_classifier.tflite and wire a
LearnedExerciseClassifier (TFLite Interpreter) behind FusedExerciseClassifier (bible §9.3, §9.4).
Input tensor: float32 [1, 32, 61] (the parity feature window). Output: logits [1, 6] over CLASSES.
"""
from __future__ import annotations
import sys, os, argparse

sys.path.insert(0, os.path.dirname(__file__))
import torch
from models.tcn import ExerciseTCN, WINDOW, FEATURE_DIM, CLASSES  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="runs/best.pt")
    ap.add_argument("--onnx", default="exercise_classifier.onnx")
    args = ap.parse_args()

    model = ExerciseTCN()
    state = torch.load(args.ckpt, map_location="cpu", weights_only=False)  # ckpt stores CLASSES list
    model.load_state_dict(state["model"]); model.eval()

    dummy = torch.zeros(1, WINDOW, FEATURE_DIM, dtype=torch.float32)
    torch.onnx.export(
        model, dummy, args.onnx,
        input_names=["features"], output_names=["logits"],
        dynamic_axes={"features": {0: "batch"}, "logits": {0: "batch"}},
        opset_version=13,
    )
    print(f"wrote {args.onnx}  input=[batch,{WINDOW},{FEATURE_DIM}]  classes={CLASSES}")
    print("next: convert ONNX→TFLite (ai-edge-torch or onnx2tf) → android assets; see header.")


if __name__ == "__main__":
    main()
