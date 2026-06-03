"""Runnable proof of Vyāyāma's core intelligence math — mirrors the Kotlin
RealFeatureExtractor (angle math) + StateMachineRepCounter (rep FSM) EXACTLY, so passing here
proves the algorithm the Kotlin replicates. Run:  python tools/threshold_tuner/verify_core.py

This doubles as the threshold-tuning harness: swap in recorded keypoint CSVs to tune θ_top/θ_bottom.
"""
from __future__ import annotations
import math, sys
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # Windows cp1252 console safety
except Exception:
    pass

# ---- mirror of Geom.angleDeg (RealFeatureExtractor.kt) ----
def angle_deg(ax, ay, bx, by, cx, cy):
    v1x, v1y = ax - bx, ay - by
    v2x, v2y = cx - bx, cy - by
    m1 = math.hypot(v1x, v1y); m2 = math.hypot(v2x, v2y)
    if m1 < 1e-6 or m2 < 1e-6: return float("nan")
    c = (v1x * v2x + v1y * v2y) / (m1 * m2)
    c = max(-1.0, min(1.0, c))
    return math.degrees(math.acos(c))

# ---- mirror of StateMachineRepCounter.kt ----
class RepFSM:
    def __init__(self, top, bottom, top_enter=0.15, bot_enter=0.85, partial_min=0.40, min_rep_ms=300):
        self.top, self.bottom = top, bottom
        self.te, self.be, self.pm, self.minms = top_enter, bot_enter, partial_min, min_rep_ms
        self.phase = "TOP"; self.reps = 0; self.start = 0.0; self.maxp = 0.0
        self.partials = 0

    def update(self, value, ts_ms):
        if value != value:  # NaN → freeze
            return self.reps, self.phase, False, False
        p = (value - self.top) / (self.bottom - self.top)
        completed = partial = False
        ph = self.phase
        if ph == "TOP":
            if p > self.te: self.phase = "DESCENDING"; self.start = ts_ms; self.maxp = p
        elif ph == "DESCENDING":
            self.maxp = max(self.maxp, p)
            if p >= self.be: self.phase = "BOTTOM"
            elif p <= self.te:
                if self.maxp >= self.pm: partial = True; self.partials += 1
                self.phase = "TOP"
        elif ph == "BOTTOM":
            self.maxp = max(self.maxp, p)
            if p < self.be: self.phase = "ASCENDING"
        elif ph == "ASCENDING":
            if p >= self.be: self.phase = "BOTTOM"
            elif p <= self.te:
                if (ts_ms - self.start) >= self.minms: self.reps += 1; completed = True
                self.phase = "TOP"
        return self.reps, self.phase, completed, partial


# ---- mirror of RuleExerciseClassifier.kt (is-exercising gate + signature + hysteresis) ----
from collections import deque

def _avg(a, b):
    va = a == a; vb = b == b
    if va and vb: return (a + b) / 2
    if va: return a
    if vb: return b
    return float("nan")

def _amp(xs):
    xs = [x for x in xs if x == x]
    return (max(xs) - min(xs)) if xs else 0.0

class RuleClassifier:
    def __init__(self):
        self.exercising = False; self.active = 0; self.idle = 0
        self.reported = "NONE"; self.candidate = "NONE"; self.cstreak = 0

    def classify(self, frames):
        if len(frames) < 10: return "NONE"
        knee = [_avg(f["knee_l"], f["knee_r"]) for f in frames]
        elbow = [_avg(f["elbow_l"], f["elbow_r"]) for f in frames]
        openn = [f["openness"] for f in frames]
        torso = [f["torso"] for f in frames if f["torso"] == f["torso"]]
        knee_amp, elbow_amp, open_amp = _amp(knee), _amp(elbow), _amp(openn)
        activity = max(knee_amp, elbow_amp, open_amp * 140)

        if activity > 25: self.active += 1; self.idle = 0
        else: self.idle += 1; self.active = 0
        if not self.exercising and self.active >= 8: self.exercising = True
        if self.exercising and self.idle >= 14: self.exercising = False; self.reported = "NONE"
        if not self.exercising: return "NONE"

        avg_torso = sum(torso) / len(torso) if torso else float("nan")
        diffs = [abs(f["knee_l"] - f["knee_r"]) for f in frames
                 if f["knee_l"] == f["knee_l"] and f["knee_r"] == f["knee_r"]]
        knee_sym = sum(diffs) / len(diffs) if diffs else 0.0

        if open_amp > 0.40: cand = "JUMPING_JACK"
        elif avg_torso > 50 and elbow_amp > 25: cand = "PUSHUP"
        elif knee_amp > 30 and knee_sym < 25: cand = "SQUAT"
        elif knee_amp > 30 and knee_sym >= 25: cand = "LUNGE"
        elif elbow_amp > 25 and knee_amp < 20 and avg_torso < 35: cand = "BICEP_CURL"
        else: cand = "UNKNOWN"

        if cand == self.candidate: self.cstreak += 1
        else: self.candidate = cand; self.cstreak = 1
        if self.cstreak >= 8 and cand != "UNKNOWN": self.reported = cand
        return self.reported


def run_classifier(frame_seq):
    c = RuleClassifier(); w = deque(maxlen=30); res = "NONE"
    for fr in frame_seq:
        w.append(fr); res = c.classify(list(w))
    return res


def frm(kl=float("nan"), kr=float("nan"), el=float("nan"), er=float("nan"), torso=float("nan"), openness=float("nan")):
    return {"knee_l": kl, "knee_r": kr, "elbow_l": el, "elbow_r": er, "torso": torso, "openness": openness}


def feed(fsm, thetas, fps=30.0):
    dt = 1000.0 / fps
    for i, th in enumerate(thetas):
        fsm.update(th, i * dt)
    return fsm.reps


def squat_signal(n_reps, frames_per_rep=45):
    out = []
    for _ in range(n_reps):
        for f in range(frames_per_rep):
            ph = 2 * math.pi * f / frames_per_rep
            out.append(130 + 40 * math.cos(ph))   # 170 (top) → 90 (bottom) → 170
    out.append(170.0)  # settle at top
    return out


def main():
    ok = True
    def check(name, cond):
        nonlocal ok
        print(("PASS " if cond else "FAIL ") + name)
        ok = ok and cond

    # 1) angle math
    check("right angle = 90", abs(angle_deg(0, 1, 0, 0, 1, 0) - 90) < 1e-3)
    check("straight = 180", abs(angle_deg(-1, 0, 0, 0, 1, 0) - 180) < 1e-3)
    check("collinear/zero-limb = NaN", math.isnan(angle_deg(0, 0, 0, 0, 1, 0)))

    # 2) squats: 5 clean reps counted as 5
    f = RepFSM(165, 95)
    check("5 squats → 5 reps", feed(f, squat_signal(5)) == 5)

    # 3) partial rep (only to 120°) is NOT counted, but flagged
    f = RepFSM(165, 95)
    feed(f, [170, 150, 130, 120, 130, 150, 170, 170])
    check("partial squat → 0 reps", f.reps == 0)
    check("partial squat → flagged", f.partials == 1)

    # 4) too-fast rep (one-frame 170→90→170) rejected by min-duration
    f = RepFSM(165, 95)
    f.update(170, 0); f.update(90, 33); f.update(170, 66)
    check("too-fast rep → 0 reps", f.reps == 0)

    # 5) jumping jack: openness 0→1→0, same FSM, inverted direction (top<bottom)
    f = RepFSM(0.15, 0.85)
    jack = []
    for _ in range(4):
        for x in range(45):
            ph = 2 * math.pi * x / 45
            jack.append(0.5 - 0.5 * math.cos(ph))  # 0 (closed) → 1 (open) → 0
    jack.append(0.0)
    check("4 jumping jacks → 4 reps", feed(f, jack) == 4)

    # 6) hysteresis: jitter around the top threshold doesn't spuriously count
    f = RepFSM(165, 95)
    jitter = [168, 162, 169, 161, 170, 163] * 10   # never reaches bottom
    feed(f, jitter)
    check("jitter at top → 0 reps", f.reps == 0)

    # 7) classifier: squat vs push-up discrimination + idle gate
    squat_w = [frm(kl=(160 if i % 2 == 0 else 100), kr=(160 if i % 2 == 0 else 100), torso=10, openness=0) for i in range(40)]
    check("classifier → SQUAT", run_classifier(squat_w) == "SQUAT")

    push_w = [frm(el=(160 if i % 2 == 0 else 90), er=(160 if i % 2 == 0 else 90), kl=175, kr=175, torso=80, openness=0) for i in range(40)]
    check("classifier → PUSHUP", run_classifier(push_w) == "PUSHUP")

    idle_w = [frm(kl=175, kr=175, torso=5, openness=0) for _ in range(30)]
    check("classifier idle → NONE", run_classifier(idle_w) == "NONE")

    jack_w = []
    for _ in range(4):
        for x in range(30):
            o = 0.5 - 0.5 * math.cos(2 * math.pi * x / 30)
            jack_w.append(frm(kl=175, kr=175, torso=10, openness=o))
    check("classifier → JUMPING_JACK", run_classifier(jack_w) == "JUMPING_JACK")

    print("\n" + ("ALL CORE TESTS PASS ✓" if ok else "SOME TESTS FAILED ✗"))
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
