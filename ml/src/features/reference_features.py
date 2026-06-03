"""THE PARITY CONTRACT (bible §9.3, §16).

This MUST stay byte-for-byte equivalent to the Kotlin RealFeatureExtractor.kt so the learned
classifier sees the SAME features on the training host and on the device → zero train/serve skew.
Pure numpy; runnable + tested (ml/tests/test_parity.py).

Per-frame feature vector fed to the temporal model = 61 floats:
    10 joint angles  +  34 normalized keypoints (17 * x,y)  +  17 confidences
NaN (confidence-gated-out) values are replaced by 0.0 on BOTH sides.
"""
from __future__ import annotations
import math
import numpy as np

# ---- COCO-17 keypoint indices (must match io.vyayama.api.Kp) ----
NOSE, L_EYE, R_EYE, L_EAR, R_EAR = 0, 1, 2, 3, 4
L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST = 5, 6, 7, 8, 9, 10
L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE = 11, 12, 13, 14, 15, 16
N_KP = 17

# ---- angle indices (must match io.vyayama.api.Angles) ----
KNEE_L, KNEE_R, HIP_L, HIP_R, ELBOW_L, ELBOW_R, SHOULDER_L, SHOULDER_R, TORSO_LEAN, OPENNESS = range(10)
N_ANG = 10
FEATURE_DIM = N_ANG + N_KP * 2 + N_KP  # 10 + 34 + 17 = 61


def angle_deg(ax, ay, bx, by, cx, cy):
    v1x, v1y = ax - bx, ay - by
    v2x, v2y = cx - bx, cy - by
    m1 = math.hypot(v1x, v1y); m2 = math.hypot(v2x, v2y)
    if m1 < 1e-6 or m2 < 1e-6:
        return float("nan")
    c = (v1x * v2x + v1y * v2y) / (m1 * m2)
    c = max(-1.0, min(1.0, c))
    return math.degrees(math.acos(c))


def _clamp01(v):
    return max(0.0, min(1.0, v))


def normalize_frame(kps, tau=0.3):
    """kps: (17,3) array of [x, y, conf]. Returns (angles[10], kps_norm[34], conf[17], visible)."""
    kps = np.asarray(kps, dtype=np.float64)
    gated = kps[:, 2] >= tau
    angles = np.full(N_ANG, np.nan)
    kps_norm = np.full(N_KP * 2, np.nan)
    conf = kps[:, 2].astype(np.float32).copy()

    hips_ok = gated[L_HIP] and gated[R_HIP]
    sh_ok = gated[L_SHOULDER] and gated[R_SHOULDER]
    if not (hips_ok and sh_ok):
        return angles, kps_norm, conf, False

    hipc = (kps[L_HIP, :2] + kps[R_HIP, :2]) / 2.0
    shc = (kps[L_SHOULDER, :2] + kps[R_SHOULDER, :2]) / 2.0
    torso = max(math.hypot(hipc[0] - shc[0], hipc[1] - shc[1]), 1e-3)

    for i in range(N_KP):
        if gated[i]:
            kps_norm[i * 2] = (kps[i, 0] - hipc[0]) / torso
            kps_norm[i * 2 + 1] = (kps[i, 1] - hipc[1]) / torso

    def ang(a, b, c):
        if gated[a] and gated[b] and gated[c]:
            return angle_deg(kps[a, 0], kps[a, 1], kps[b, 0], kps[b, 1], kps[c, 0], kps[c, 1])
        return float("nan")

    angles[KNEE_L] = ang(L_HIP, L_KNEE, L_ANKLE)
    angles[KNEE_R] = ang(R_HIP, R_KNEE, R_ANKLE)
    angles[HIP_L] = ang(L_SHOULDER, L_HIP, L_KNEE)
    angles[HIP_R] = ang(R_SHOULDER, R_HIP, R_KNEE)
    angles[ELBOW_L] = ang(L_SHOULDER, L_ELBOW, L_WRIST)
    angles[ELBOW_R] = ang(R_SHOULDER, R_ELBOW, R_WRIST)
    angles[SHOULDER_L] = ang(L_HIP, L_SHOULDER, L_ELBOW)
    angles[SHOULDER_R] = ang(R_HIP, R_SHOULDER, R_ELBOW)
    angles[TORSO_LEAN] = angle_deg(shc[0], shc[1] + 1.0, shc[0], shc[1], hipc[0], hipc[1])
    angles[OPENNESS] = _openness(kps, gated, shc, torso)
    return angles, kps_norm, conf, True


def _openness(kps, gated, shc, torso):
    wrists_ok = gated[L_WRIST] and gated[R_WRIST]
    ankles_ok = gated[L_ANKLE] and gated[R_ANKLE]
    if not wrists_ok and not ankles_ok:
        return float("nan")
    arm = 0.0
    if wrists_ok:
        wcy = (kps[L_WRIST, 1] + kps[R_WRIST, 1]) / 2.0
        arm = _clamp01((shc[1] - wcy) / torso)
    leg = 0.0
    if ankles_ok:
        asp = math.hypot(kps[L_ANKLE, 0] - kps[R_ANKLE, 0], kps[L_ANKLE, 1] - kps[R_ANKLE, 1])
        sw = math.hypot(kps[L_SHOULDER, 0] - kps[R_SHOULDER, 0], kps[L_SHOULDER, 1] - kps[R_SHOULDER, 1])
        sw = torso if sw < 1e-3 else sw
        leg = _clamp01((asp / sw - 1.0) / 1.0)
    return 0.5 * arm + 0.5 * leg


def feature_vector(angles, kps_norm, conf):
    """Concatenate to the 61-d model input; NaN → 0.0 (the device does the same)."""
    a = np.nan_to_num(np.asarray(angles, dtype=np.float32), nan=0.0)
    k = np.nan_to_num(np.asarray(kps_norm, dtype=np.float32), nan=0.0)
    c = np.asarray(conf, dtype=np.float32)
    return np.concatenate([a, k, c]).astype(np.float32)


def ema_sequence(seq, alpha=0.3):
    """EMA-smooth a (T, D) feature sequence the way the device FeatureExtractor smooths angles."""
    seq = np.asarray(seq, dtype=np.float32)
    out = np.empty_like(seq)
    prev = None
    for t in range(seq.shape[0]):
        prev = seq[t] if prev is None else alpha * seq[t] + (1 - alpha) * prev
        out[t] = prev
    return out
