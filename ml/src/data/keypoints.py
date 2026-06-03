"""Build a training set of feature windows from labeled keypoint sequences.

Input layout:  <root>/<CLASS>/*.npy   where each .npy is a (T,17,3) array of COCO-17 [x,y,conf]
(extract these upstream from Fit3D/InfiniteRep skeletons or by running a pose model over workout
videos — same 17-COCO convention the device uses). Output: an .npz with X:(N,32,61), y:(N,).

Run:  python ml/src/data/keypoints.py <root> dataset.npz
"""
from __future__ import annotations
import sys, os, glob
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from features.reference_features import normalize_frame, feature_vector, ema_sequence, FEATURE_DIM  # noqa: E402
from models.tcn import WINDOW, CLASSES  # noqa: E402

STRIDE = 8


def features_from_seq(seq):
    """(T,17,3) keypoints → (T,61) EMA-smoothed feature vectors (matches device FeatureExtractor)."""
    feats = []
    for frame in seq:
        a, k, c, vis = normalize_frame(frame)
        feats.append(feature_vector(a, k, c) if vis else np.zeros(FEATURE_DIM, dtype=np.float32))
    return ema_sequence(np.asarray(feats, dtype=np.float32))


def windows(feats, label, stride=STRIDE):
    out = []
    for s in range(0, max(0, len(feats) - WINDOW + 1), stride):
        out.append((feats[s:s + WINDOW], label))
    return out


def build_dataset(root: str, out_npz: str = "dataset.npz"):
    X, Y = [], []
    for ci, cls in enumerate(CLASSES):
        for f in glob.glob(os.path.join(root, cls, "*.npy")):
            feats = features_from_seq(np.load(f))
            for w, lbl in windows(feats, ci):
                if w.shape[0] == WINDOW:
                    X.append(w); Y.append(lbl)
    if not X:
        raise SystemExit(f"No windows built from {root} (expected <root>/<CLASS>/*.npy of (T,17,3))")
    X = np.asarray(X, dtype=np.float32); Y = np.asarray(Y, dtype=np.int64)
    np.savez(out_npz, X=X, y=Y)
    dist = {CLASSES[i]: int((Y == i).sum()) for i in range(len(CLASSES))}
    print(f"saved {out_npz}  X={X.shape}  per-class={dist}")


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "datasets/keypoints"
    out = sys.argv[2] if len(sys.argv) > 2 else "dataset.npz"
    build_dataset(root, out)
