//============================================================================
// Vyāyāma — exercise-recognition + rep-counting + form brain.
// Self-contained Java port of the proven Kotlin intelligence (verified 13/13 in
// tools/threshold_tuner/verify_core.py). Input: 17 COCO keypoints (UPRIGHT pixel coords,
// (0,0) = missing). No external deps. Feed one frame per camera frame via onFrame().
//============================================================================
package com.qc.posedetectionYoloNAS;

import java.util.ArrayDeque;
import java.util.ArrayList;

public class VyayamaCoach {

    // COCO-17 indices (HRNet output order)
    static final int L_SH = 5, R_SH = 6, L_EL = 7, R_EL = 8, L_WR = 9, R_WR = 10,
            L_HIP = 11, R_HIP = 12, L_KN = 13, R_KN = 14, L_AN = 15, R_AN = 16;

    public static class Result {
        public String exercise = "READY";
        public String key = "NONE";        // raw id (SQUAT/PUSHUP/...) for storage; exercise is the pretty form
        public int reps = 0;
        public String cue = "";
        public boolean cueWarn = false;     // true = correction (coral), false = praise/live coaching (volt)
        public int formScore = -1;
        public boolean exercising = false;
    }

    // ---- per-frame feature window ----
    private static final int WIN = 30;
    // sample = {kneeAvg, elbowAvg, openness, torso, kneeL, kneeR, elbowL, elbowR, hipSag}
    private final ArrayDeque<float[]> window = new ArrayDeque<>();

    // is-exercising + classification hysteresis (mirrors RuleExerciseClassifier)
    private boolean exercising = false;
    private int activeStreak = 0, idleStreak = 0;
    private String reported = "NONE", candidate = "NONE";
    private int candStreak = 0;
    // Sticky-lock tuning: ACQUIRE = frames to first-lock from NONE (snappy);
    // SWITCH = frames a DIFFERENT candidate must dominate to OVERRIDE a (possibly wrong) lock.
    private static final int ACQUIRE = 7;    // ~0.3s
    private static final int SWITCH  = 16;   // ~0.6–0.8s

    // rep FSM (mirrors StateMachineRepCounter)
    private String repExercise = "NONE";
    private String phase = "TOP";
    private int reps = 0;
    private long repStartNs = 0;
    private float maxP = 0f;

    // per-rep form buffer
    private final ArrayList<float[]> repFrames = new ArrayList<>();
    private String prevPhase = "TOP";
    private String lastCue = "";
    private int lastScore = -1;
    private boolean lastCueWarn = false;
    private long lastCueNs = 0;
    private float curP = 0f;            // latest rep progress (0=top, 1=bottom) — drives live cues
    private boolean displayWarn = false;

    public Result onFrame(float[][] kp, long tsNs) {
        float kneeL = angle(kp, L_HIP, L_KN, L_AN), kneeR = angle(kp, R_HIP, R_KN, R_AN);
        float elbowL = angle(kp, L_SH, L_EL, L_WR), elbowR = angle(kp, R_SH, R_EL, R_WR);
        float kneeAvg = avg(kneeL, kneeR), elbowAvg = avg(elbowL, elbowR);
        float torso = torsoLean(kp);
        float open = openness(kp);
        float sag = hipSag(kp);
        float[] s = {kneeAvg, elbowAvg, open, torso, kneeL, kneeR, elbowL, elbowR, sag};
        if (window.size() == WIN) window.removeFirst();
        window.addLast(s);

        classify();
        updateReps(tsNs, s);

        Result r = new Result();
        r.exercising = exercising;
        r.exercise = pretty(reported);
        r.key = reported;
        r.reps = reps;
        r.cue = displayCue(tsNs);
        r.cueWarn = displayWarn;
        r.formScore = lastScore;
        return r;
    }

    public void reset() {
        window.clear(); exercising = false; activeStreak = idleStreak = 0;
        reported = candidate = "NONE"; candStreak = 0;
        repExercise = "NONE"; phase = "TOP"; reps = 0; maxP = 0; repStartNs = 0;
        repFrames.clear(); prevPhase = "TOP"; lastCue = ""; lastScore = -1;
        lastCueWarn = false; lastCueNs = 0; curP = 0f; displayWarn = false;
    }

    // ---------------- classification ----------------
    private void classify() {
        if (window.size() < 10) { reported = "NONE"; return; }
        float kneeAmp = amp(0), elbowAmp = amp(1), openAmp = amp(2);
        float activity = Math.max(kneeAmp, Math.max(elbowAmp, openAmp * 140f));

        if (activity > 25f) { activeStreak++; idleStreak = 0; }
        else { idleStreak++; activeStreak = 0; }
        if (!exercising && activeStreak >= 8) exercising = true;
        // Only release the lock after a clear idle gap (~0.7s) — not a brief pause between reps.
        if (exercising && idleStreak >= 20) { exercising = false; reported = "NONE"; candidate = "NONE"; candStreak = 0; }
        if (!exercising) { reported = "NONE"; return; }

        float avgTorso = mean(3);
        float kneeSym = meanAbsDiff(4, 5);
        String cand;
        if (openAmp > 0.40f) cand = "JUMPING_JACK";
        else if (!Float.isNaN(avgTorso) && avgTorso > 50f && elbowAmp > 25f) cand = "PUSHUP";
        else if (kneeAmp > 30f && kneeSym < 25f) cand = "SQUAT";
        else if (kneeAmp > 30f && kneeSym >= 25f) cand = "LUNGE";
        else if (elbowAmp > 25f && kneeAmp < 20f && (!Float.isNaN(avgTorso) && avgTorso < 35f)) cand = "BICEP_CURL";
        else cand = "UNKNOWN";

        if (cand.equals(candidate)) candStreak++;
        else { candidate = cand; candStreak = 1; }
        // Sticky-but-self-correcting lock — fixes BOTH failure modes:
        //  • Flicker / lost reps: a brief (1–few frame) blip can NEVER move the lock, so reps survive.
        //  • Wrong first guess: the first half-rep often reads wrong (e.g. arms move at the top of a
        //    squat → "BICEP_CURL"). We no longer freeze that mistake. While no rep has been banked yet
        //    (reps == 0) a DIFFERENT candidate that stays consistent for the longer SWITCH window
        //    overrides the lock — so it self-corrects within ~0.7s without the user going idle.
        //    Once reps are actually counted the lock is trusted (only an idle gap re-opens it),
        //    which keeps a real set's count rock-stable.
        if (!cand.equals("UNKNOWN")) {
            if (reported.equals("NONE")) {
                if (candStreak >= ACQUIRE) reported = cand;
            } else if (reps == 0 && !cand.equals(reported) && candStreak >= SWITCH) {
                reported = cand;
            }
        }
    }

    // ---------------- rep FSM ----------------
    private void updateReps(long tsNs, float[] s) {
        String ex = reported;
        // Paused (idle/unknown): freeze the FSM but KEEP the reps — don't zero them on a brief gap.
        if (ex.equals("NONE") || ex.equals("UNKNOWN")) return;
        // Only reset when genuinely switching to a DIFFERENT exercise (after an idle re-lock).
        if (!ex.equals(repExercise)) { repExercise = ex; phase = "TOP"; reps = 0; maxP = 0; repFrames.clear(); prevPhase = "TOP"; }

        float top, bottom, v;
        switch (ex) {
            case "SQUAT":        top = 165; bottom = 95;  v = s[0]; break;             // knee avg
            case "PUSHUP":       top = 160; bottom = 95;  v = s[1]; break;             // elbow avg
            case "LUNGE":        top = 165; bottom = 95;  v = minv(s[4], s[5]); break; // front knee
            case "BICEP_CURL":   top = 155; bottom = 50;  v = minv(s[6], s[7]); break; // active elbow
            case "JUMPING_JACK": top = 0.15f; bottom = 0.85f; v = s[2]; break;         // openness
            default: return;
        }
        if (Float.isNaN(v)) return;                 // confidence-freeze
        float p = (v - top) / (bottom - top);
        curP = p;

        boolean completed = false;
        switch (phase) {
            case "TOP": if (p > 0.15f) { phase = "DESCENDING"; repStartNs = tsNs; maxP = p; } break;
            case "DESCENDING":
                maxP = Math.max(maxP, p);
                if (p >= 0.85f) phase = "BOTTOM";
                else if (p <= 0.15f) phase = "TOP";
                break;
            case "BOTTOM": maxP = Math.max(maxP, p); if (p < 0.85f) phase = "ASCENDING"; break;
            case "ASCENDING":
                if (p >= 0.85f) phase = "BOTTOM";
                else if (p <= 0.15f) {
                    if ((tsNs - repStartNs) / 1_000_000L >= 300L) { reps++; completed = true; }
                    phase = "TOP";
                }
                break;
        }

        // accumulate the in-flight rep, analyze form on completion
        if (prevPhase.equals("TOP") && phase.equals("DESCENDING")) repFrames.clear();
        if (!phase.equals("TOP")) repFrames.add(s);
        if (completed) {
            analyzeForm(ex);
            if (reps % 5 == 0) { lastCue = reps + " in a row!"; lastCueWarn = false; }   // milestone hype — intentionally overrides this rep's form cue
            lastCueNs = tsNs;
            repFrames.clear();
        } else if (phase.equals("TOP")) repFrames.clear();
        prevPhase = phase;
    }

    // ---------------- form + live coaching ----------------
    /** Pick the cue to show this frame: a fresh post-rep verdict wins for ~1.4 s, else live in-rep coaching. */
    private String displayCue(long tsNs) {
        if (!exercising || reported.equals("NONE") || reported.equals("UNKNOWN")) { displayWarn = false; return ""; }
        if (!lastCue.isEmpty() && (tsNs - lastCueNs) / 1_000_000L < 1400L) { displayWarn = lastCueWarn; return lastCue; }
        displayWarn = false;
        return liveCue();
    }

    /** Real-time, motion-aware coaching from the current rep phase + progress. */
    private String liveCue() {
        switch (reported) {
            case "SQUAT": case "LUNGE": case "PUSHUP":
                switch (phase) {
                    case "DESCENDING": return curP < 0.6f ? "Lower…" : "Almost — deeper";
                    case "BOTTOM":     return "Now drive up!";
                    case "ASCENDING":  return "Push!";
                    default:           return "";
                }
            case "BICEP_CURL":
                switch (phase) {
                    case "DESCENDING": return "Curl up…";
                    case "BOTTOM":     return "Squeeze!";
                    case "ASCENDING":  return "Lower slow";
                    default:           return "";
                }
            case "JUMPING_JACK":
                switch (phase) {
                    case "DESCENDING": return "Open wide!";
                    case "ASCENDING":  return "And in";
                    default:           return "";
                }
            default: return "";
        }
    }

    /** Per-rep biomechanics → 0..100 score + one actionable cue (highest-severity issue wins). */
    private void analyzeForm(String ex) {
        int frames = repFrames.size();
        if (frames == 0) { lastCue = "Clean rep!"; lastCueWarn = false; lastScore = 100; return; }
        float tempo = tempoScore(frames);
        String cue = "Clean rep!"; boolean warn = false; int sev = 0; float score = 90f;

        switch (ex) {
            case "SQUAT": {
                float depth = clamp01((165f - repMin(0)) / 70f);
                float sym = repMeanAbsDiff(4, 5);
                float maxTorso = repMax(3);
                float upright = clamp01(1f - Math.max(0f, maxTorso - 30f) / 45f);
                score = 100f * (0.45f * depth + 0.20f * clamp01(1f - sym / 40f) + 0.20f * upright + 0.15f * tempo);
                cue = "Strong squat!";
                if (depth < 0.75f && sev < 9)  { cue = "Go deeper — hips below knees"; warn = true; sev = 9; }
                if (sym > 22f && sev < 6)      { cue = "Even out your weight"; warn = true; sev = 6; }
                if (maxTorso > 50f && sev < 5) { cue = "Chest up — back straight"; warn = true; sev = 5; }
                break;
            }
            case "LUNGE": {
                float depth = clamp01((165f - repMinPair(4, 5)) / 70f);
                float maxTorso = repMax(3);
                float upright = clamp01(1f - Math.max(0f, maxTorso - 25f) / 45f);
                score = 100f * (0.5f * depth + 0.3f * upright + 0.2f * tempo);
                cue = "Nice lunge!";
                if (depth < 0.7f && sev < 8)   { cue = "Drop the back knee lower"; warn = true; sev = 8; }
                if (maxTorso > 45f && sev < 6) { cue = "Stay upright"; warn = true; sev = 6; }
                break;
            }
            case "PUSHUP": {
                float depth = clamp01((160f - repMin(1)) / 65f);
                float sagScore = clamp01(1f - repMax(8) / 0.22f);
                float sym = repMeanAbsDiff(6, 7);
                score = 100f * (0.35f * depth + 0.35f * sagScore + 0.15f * clamp01(1f - sym / 40f) + 0.15f * tempo);
                cue = "Solid push-up!";
                if (depth < 0.75f && sev < 8)   { cue = "Lower your chest further"; warn = true; sev = 8; }
                if (sagScore < 0.6f && sev < 9) { cue = "Hips in line — no sag"; warn = true; sev = 9; }
                if (sym > 22f && sev < 5)       { cue = "Press evenly both arms"; warn = true; sev = 5; }
                break;
            }
            case "BICEP_CURL": {
                float rom = clamp01(Math.max(repRange(6), repRange(7)) / 100f);
                float driftScore = clamp01(1f - repVar(3) / 400f);
                score = 100f * (0.45f * rom + 0.4f * driftScore + 0.15f * tempo);
                cue = "Full range!";
                if (rom < 0.7f && sev < 7)        { cue = "Extend and squeeze"; warn = true; sev = 7; }
                if (driftScore < 0.7f && sev < 9) { cue = "Stop swinging — isolate it"; warn = true; sev = 9; }
                break;
            }
            case "JUMPING_JACK": {
                float ext = clamp01(repMax(2) / 0.9f);
                score = 100f * (0.7f * ext + 0.3f * tempo);
                cue = "Great pace!";
                if (ext < 0.7f && sev < 7) { cue = "Bigger — arms up, feet wide"; warn = true; sev = 7; }
                break;
            }
            default: break;
        }
        lastCue = cue; lastCueWarn = warn; lastScore = Math.round(clamp01(score / 100f) * 100f);
    }

    private static float tempoScore(int frames) {
        if (frames < 12) return 0.4f;
        if (frames <= 18) return 0.75f;
        if (frames <= 70) return 1f;
        if (frames <= 100) return 0.8f;
        return 0.6f;
    }

    // ---------------- per-rep window helpers ----------------
    private float repMin(int col) {
        float m = Float.MAX_VALUE; boolean any = false;
        for (float[] f : repFrames) { float v = f[col]; if (!Float.isNaN(v)) { m = Math.min(m, v); any = true; } }
        return any ? m : Float.NaN;
    }
    private float repMax(int col) {
        float m = -Float.MAX_VALUE; boolean any = false;
        for (float[] f : repFrames) { float v = f[col]; if (!Float.isNaN(v)) { m = Math.max(m, v); any = true; } }
        return any ? m : 0f;
    }
    private float repRange(int col) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE; boolean any = false;
        for (float[] f : repFrames) { float v = f[col]; if (!Float.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); any = true; } }
        return any ? hi - lo : 0f;
    }
    private float repVar(int col) {
        float sum = 0; int n = 0;
        for (float[] f : repFrames) { float v = f[col]; if (!Float.isNaN(v)) { sum += v; n++; } }
        if (n < 2) return 0f;
        float mean = sum / n, acc = 0;
        for (float[] f : repFrames) { float v = f[col]; if (!Float.isNaN(v)) acc += (v - mean) * (v - mean); }
        return acc / n;
    }
    private float repMinPair(int a, int b) {
        float m = Float.MAX_VALUE; boolean any = false;
        for (float[] f : repFrames) { float v = minv(f[a], f[b]); if (!Float.isNaN(v)) { m = Math.min(m, v); any = true; } }
        return any ? m : Float.NaN;
    }
    private float repMeanAbsDiff(int a, int b) {
        float sum = 0; int n = 0;
        for (float[] f : repFrames) { if (!Float.isNaN(f[a]) && !Float.isNaN(f[b])) { sum += Math.abs(f[a] - f[b]); n++; } }
        return n > 0 ? sum / n : 0f;
    }

    // ---------------- window helpers ----------------
    private float amp(int col) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE; boolean any = false;
        for (float[] s : window) { float v = s[col]; if (!Float.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); any = true; } }
        return any ? hi - lo : 0f;
    }
    private float mean(int col) {
        float sum = 0; int n = 0;
        for (float[] s : window) { float v = s[col]; if (!Float.isNaN(v)) { sum += v; n++; } }
        return n > 0 ? sum / n : Float.NaN;
    }
    private float meanAbsDiff(int a, int b) {
        float sum = 0; int n = 0;
        for (float[] s : window) { if (!Float.isNaN(s[a]) && !Float.isNaN(s[b])) { sum += Math.abs(s[a] - s[b]); n++; } }
        return n > 0 ? sum / n : 0f;
    }

    // ---------------- geometry ----------------
    private static boolean missing(float[][] kp, int i) { return kp[i][0] == 0f && kp[i][1] == 0f; }

    private static float angle(float[][] kp, int a, int b, int c) {
        if (missing(kp, a) || missing(kp, b) || missing(kp, c)) return Float.NaN;
        float v1x = kp[a][0] - kp[b][0], v1y = kp[a][1] - kp[b][1];
        float v2x = kp[c][0] - kp[b][0], v2y = kp[c][1] - kp[b][1];
        double m1 = Math.hypot(v1x, v1y), m2 = Math.hypot(v2x, v2y);
        if (m1 < 1e-6 || m2 < 1e-6) return Float.NaN;
        double cos = (v1x * v2x + v1y * v2y) / (m1 * m2);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    private static float torsoLean(float[][] kp) {
        if (missing(kp, L_SH) || missing(kp, R_SH) || missing(kp, L_HIP) || missing(kp, R_HIP)) return Float.NaN;
        float shx = (kp[L_SH][0] + kp[R_SH][0]) / 2f, shy = (kp[L_SH][1] + kp[R_SH][1]) / 2f;
        float hpx = (kp[L_HIP][0] + kp[R_HIP][0]) / 2f, hpy = (kp[L_HIP][1] + kp[R_HIP][1]) / 2f;
        // angle of (shoulder→hip) vs image-down (0,1); upright ≈ 0°, horizontal ≈ 90°
        float vx = hpx - shx, vy = hpy - shy;
        double m = Math.hypot(vx, vy);
        if (m < 1e-6) return Float.NaN;
        double cos = (vy) / m;                       // dot with (0,1)
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    private static float openness(float[][] kp) {
        boolean wr = !missing(kp, L_WR) && !missing(kp, R_WR);
        boolean an = !missing(kp, L_AN) && !missing(kp, R_AN);
        boolean sh = !missing(kp, L_SH) && !missing(kp, R_SH);
        boolean hp = !missing(kp, L_HIP) && !missing(kp, R_HIP);
        if (!sh || !hp || (!wr && !an)) return Float.NaN;
        float shy = (kp[L_SH][1] + kp[R_SH][1]) / 2f;
        float torsoLen = (float) Math.hypot((kp[L_SH][0] + kp[R_SH][0]) / 2f - (kp[L_HIP][0] + kp[R_HIP][0]) / 2f,
                shy - (kp[L_HIP][1] + kp[R_HIP][1]) / 2f);
        if (torsoLen < 1e-3) torsoLen = 1f;
        float arm = 0f;
        if (wr) { float wy = (kp[L_WR][1] + kp[R_WR][1]) / 2f; arm = clamp01((shy - wy) / torsoLen); }
        float leg = 0f;
        if (an) {
            float aSpread = (float) Math.hypot(kp[L_AN][0] - kp[R_AN][0], kp[L_AN][1] - kp[R_AN][1]);
            float shW = (float) Math.hypot(kp[L_SH][0] - kp[R_SH][0], kp[L_SH][1] - kp[R_SH][1]);
            if (shW < 1e-3) shW = torsoLen;
            leg = clamp01(aSpread / shW - 1f);
        }
        return 0.5f * arm + 0.5f * leg;
    }

    /** Hip-line sag/pike for push-ups: normalized perpendicular distance from the hips to the
     *  shoulder→ankle line (0 = perfectly in line). NaN unless shoulders/hips/ankles are all visible. */
    private static float hipSag(float[][] kp) {
        if (missing(kp, L_SH) || missing(kp, R_SH) || missing(kp, L_HIP) || missing(kp, R_HIP)
                || missing(kp, L_AN) || missing(kp, R_AN)) return Float.NaN;
        float sx = (kp[L_SH][0] + kp[R_SH][0]) / 2f, sy = (kp[L_SH][1] + kp[R_SH][1]) / 2f;
        float hx = (kp[L_HIP][0] + kp[R_HIP][0]) / 2f, hy = (kp[L_HIP][1] + kp[R_HIP][1]) / 2f;
        float ax = (kp[L_AN][0] + kp[R_AN][0]) / 2f, ay = (kp[L_AN][1] + kp[R_AN][1]) / 2f;
        float len = (float) Math.hypot(ax - sx, ay - sy);
        if (len < 1e-3f) return Float.NaN;
        float cross = Math.abs((ax - sx) * (hy - sy) - (ay - sy) * (hx - sx));
        return (cross / len) / len;     // distance ÷ body length → dimensionless sag ratio
    }

    private static float avg(float a, float b) {
        boolean na = Float.isNaN(a), nb = Float.isNaN(b);
        if (!na && !nb) return (a + b) / 2f;
        if (!na) return a; if (!nb) return b; return Float.NaN;
    }
    private static float minv(float a, float b) {
        boolean na = Float.isNaN(a), nb = Float.isNaN(b);
        if (!na && !nb) return Math.min(a, b);
        if (!na) return a; if (!nb) return b; return Float.NaN;
    }
    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static String pretty(String s) {
        switch (s) {
            case "SQUAT": return "SQUAT";
            case "PUSHUP": return "PUSH-UP";
            case "LUNGE": return "LUNGE";
            case "BICEP_CURL": return "BICEP CURL";
            case "JUMPING_JACK": return "JUMPING JACK";
            case "UNKNOWN": return "…";
            default: return "READY";
        }
    }
}
