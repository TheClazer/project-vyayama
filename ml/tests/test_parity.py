"""Runnable proof that ml/features mirrors the Kotlin RealFeatureExtractor and is
scale/translation invariant (the parity contract, bible §16). Run:
    python ml/tests/test_parity.py     (or: pytest ml/tests)
"""
from __future__ import annotations
import math, sys, os
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
from features.reference_features import (  # noqa: E402
    normalize_frame, feature_vector, angle_deg,
    FEATURE_DIM, KNEE_L, KNEE_R, TORSO_LEAN,
    L_SHOULDER, R_SHOULDER, L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE, N_KP,
)


def _frame(ankle_l, ankle_r):
    kps = np.zeros((N_KP, 3)); kps[:, 2] = 1.0
    kps[L_SHOULDER] = [90, 100, 1]; kps[R_SHOULDER] = [110, 100, 1]
    kps[L_HIP] = [90, 200, 1]; kps[R_HIP] = [110, 200, 1]
    kps[L_KNEE] = [90, 300, 1]; kps[R_KNEE] = [110, 300, 1]
    kps[L_ANKLE] = [*ankle_l, 1]; kps[R_ANKLE] = [*ankle_r, 1]
    return kps


def test_angle_math():
    assert abs(angle_deg(0, 1, 0, 0, 1, 0) - 90) < 1e-3
    assert abs(angle_deg(-1, 0, 0, 0, 1, 0) - 180) < 1e-3
    assert math.isnan(angle_deg(0, 0, 0, 0, 1, 0))


def test_knee_angles():
    a, _, _, vis = normalize_frame(_frame([190, 300], [210, 300]))   # right-angle knees
    assert vis
    assert abs(a[KNEE_L] - 90) < 1.5 and abs(a[KNEE_R] - 90) < 1.5
    a2, _, _, _ = normalize_frame(_frame([90, 400], [110, 400]))     # straight legs
    assert abs(a2[KNEE_L] - 180) < 1.5


def test_scale_translation_invariance():
    base = _frame([190, 300], [210, 300])
    a1, _, _, _ = normalize_frame(base)
    moved = base.copy(); moved[:, :2] = base[:, :2] * 2.0 + 50.0       # scale ×2, translate +50
    a2, _, _, _ = normalize_frame(moved)
    assert abs(a1[KNEE_L] - a2[KNEE_L]) < 1e-2
    assert abs(a1[TORSO_LEAN] - a2[TORSO_LEAN]) < 1e-2


def test_feature_vector_dim():
    a, k, c, _ = normalize_frame(_frame([190, 300], [210, 300]))
    v = feature_vector(a, k, c)
    assert v.shape[0] == FEATURE_DIM == 61
    assert not np.isnan(v).any()       # NaN must be zeroed for the model


def test_low_confidence_not_visible():
    kps = _frame([190, 300], [210, 300]); kps[L_HIP, 2] = 0.0          # drop a hip
    _, _, _, vis = normalize_frame(kps)
    assert vis is False


def test_torso_lean_fires_chest_up_cue():
    # Mirrors MockPoseEngine's "bad form" rep: shoulders shifted forward → large torso lean.
    # Proves the RealFeatureExtractor math yields TORSO_LEAN > 50 (the squat "Chest up" threshold),
    # so the coaching cue actually fires in the mock demo.
    upright = _frame([190, 300], [210, 300])
    a_up, _, _, _ = normalize_frame(upright)
    assert a_up[TORSO_LEAN] < 8                                        # good rep → near-vertical torso

    leaning = _frame([190, 300], [210, 300])
    leaning[L_SHOULDER, 0] += 155; leaning[R_SHOULDER, 0] += 155       # forward lean, like the mock
    a_lean, _, _, _ = normalize_frame(leaning)
    assert a_lean[TORSO_LEAN] > 50                                     # bad rep → fires "Chest up"


def main():
    ok = True
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn(); print(f"PASS {name}")
            except AssertionError as e:
                ok = False; print(f"FAIL {name}: {e}")
    print("\n" + ("ALL PARITY TESTS PASS" if ok else "SOME PARITY TESTS FAILED"))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
