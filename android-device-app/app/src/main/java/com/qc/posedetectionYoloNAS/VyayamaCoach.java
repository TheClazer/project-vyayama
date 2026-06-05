//============================================================================
// Vyāyāma — exercise-recognition + rep-counting + form brain (10× engine).
// Self-contained Java port of the proven Kotlin intelligence (verified 13/13 in
// tools/threshold_tuner/verify_core.py), extended with:
//   • a KeypointFilter preprocessing pass (One-Euro smoothing + teleport reject + gap-hold),
//   • new exercises (SHOULDER_PRESS, SITUP, optional isometric PLANK),
//   • adaptive per-user range calibration,
//   • restored partial-rep flagging,
//   • zero per-frame heap allocation (pre-allocated ring buffers, no ArrayDeque/ArrayList).
// Input: 17 COCO keypoints (UPRIGHT pixel coords, (0,0) = missing). No external deps,
// no Android imports (compiles with javac). Feed one frame per camera frame via onFrame().
//============================================================================
package com.qc.posedetectionYoloNAS;

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

    // ---- preprocessing ----
    private final KeypointFilter kpFilter = new KeypointFilter();
    private boolean filterEnabled = true;

    // ---- per-frame feature window (zero-alloc ring; replaces ArrayDeque) ----
    private static final int WIN = 30;
    private static final int SAMPLE_LEN = 13;
    // sample columns (see spec C0.1):
    //  0 kneeAvg 1 elbowAvg 2 openness 3 torso 4 kneeL 5 kneeR 6 elbowL 7 elbowR 8 hipSag
    //  9 wristAboveSh 10 hipFlex 11 kneeLiftL 12 kneeLiftR
    private final float[][] ring = new float[WIN][SAMPLE_LEN];
    private int ringCount = 0;          // number of valid samples (<= WIN)
    private int ringHead = 0;           // index where the NEXT sample will be written

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

    // rep FSM thresholds (FROZEN — reproduce the 13 vectors)
    static final float TOP_ENTER    = 0.15f;
    static final float BOTTOM_ENTER = 0.85f;
    static final float PARTIAL_MIN  = 0.40f;
    static final long  MIN_REP_MS   = 300L;
    private boolean lastPartial = false;     // advisory only (analytics/cue); never changes reps
    private int     partialTotal = 0;        // count of flagged partials since reset (analytics/test)

    // in-rep form buffer (zero-alloc ring; replaces ArrayList)
    private static final int REP_CAP = 256;
    private final float[][] repRing = new float[REP_CAP][SAMPLE_LEN];
    private int repCount = 0;

    private String prevPhase = "TOP";
    private String lastCue = "";
    private int lastScore = -1;
    private boolean lastCueWarn = false;
    private long lastCueNs = 0;
    private float curP = 0f;            // latest rep progress (0=top, 1=bottom) — drives live cues
    private boolean displayWarn = false;

    // ---- adaptive range calibration ----
    static final float REST_LERP   = 0.5f;
    static final float EFFORT_LERP = 0.5f;
    static final int   CAL_REPS    = 2;
    static final float MIN_SPAN_FRAC       = 0.35f;
    static final float RANGE_MARGIN        = 0.15f;
    static final float TOP_CLAMP_FRAC      = 0.40f;
    static final float BOTTOM_CLAMP_FRAC   = 0.40f;
    static final float MIN_ADAPT_SPAN_FRAC = 0.45f;
    private boolean calArmed = false, calLocked = false;
    private int   calRepsSeen = 0;
    private float calObsTop = Float.NaN, calObsBottom = Float.NaN;
    private float defTop, defBottom, dir;     // loaded per exercise
    private float adaptTop, adaptBottom;

    // ---- new-exercise classifier thresholds ----
    static final float WRIST_UP_PRESS     = 0.35f;
    static final float WRIST_UP_JACK      = 0.55f;
    static final float ELBOW_PRESS_AMP    = 25f;
    static final float PRESS_TORSO_MAX    = 35f;
    static final float PRESS_KNEE_AMP_MAX = 18f;
    static final float SITUP_HIP_AMP      = 35f;
    static final float SITUP_TORSO_AMP    = 25f;
    static final float SITUP_KNEE_AMP_MAX = 22f;
    static final float SITUP_TORSO_MIN    = 30f;   // amplitude-driven floor (was a hard avgTorso>50 gate)
    // ---- bicep-curl positive-evidence gates ----
    static final float CURL_TORSO_AMP_MAX = 18f;   // a real curl keeps the trunk still (no swing)
    static final float CURL_HIP_AMP_MAX   = 18f;   // no trunk fold → this is what excludes sit-ups
    static final float CURL_FLEX_MIN      = 80f;   // active elbow must reach a genuinely flexed angle

    // ---- PLANK (optional isometric) ----
    static final float PLANK_TORSO_MIN   = 60f;
    static final float PLANK_TORSO_MAX   = 110f;
    static final float PLANK_SAG_MAX     = 0.18f;
    static final int   PLANK_HOLD_FRAMES = 45;
    private long  plankPrevAccumMs = 0, plankAccumMs = 0, plankHoldStartNs = 0;
    private boolean plankActive = false;
    private int   plankStillStreak = 0;
    private long  plankShownSec = -1;     // cache so the held-seconds cue allocates ≤1×/second
    private String plankCueCache = "";

    // ============================== public API ==============================

    public Result onFrame(float[][] kp, long tsNs) {
        float[][] f = filterEnabled ? kpFilter.filter(kp, tsNs) : kp;

        float kneeL = angle(f, L_HIP, L_KN, L_AN), kneeR = angle(f, R_HIP, R_KN, R_AN);
        float elbowL = angle(f, L_SH, L_EL, L_WR), elbowR = angle(f, R_SH, R_EL, R_WR);
        float kneeAvg = avg(kneeL, kneeR), elbowAvg = avg(elbowL, elbowR);
        float torso = torsoLean(f);
        float open = openness(f);
        float sag = hipSag(f);
        float wristUp = wristAboveShoulder(f);
        float hipF = hipFlex(f);
        float liftL = kneeLift(f, L_HIP, L_KN);
        float liftR = kneeLift(f, R_HIP, R_KN);

        // write the 13-wide sample in place into the ring (zero-alloc)
        float[] s = ring[ringHead];
        s[0] = kneeAvg; s[1] = elbowAvg; s[2] = open; s[3] = torso;
        s[4] = kneeL; s[5] = kneeR; s[6] = elbowL; s[7] = elbowR; s[8] = sag;
        s[9] = wristUp; s[10] = hipF; s[11] = liftL; s[12] = liftR;
        ringHead = (ringHead + 1) % WIN;
        if (ringCount < WIN) ringCount++;

        // plank stillness streak (maintained before classify so the PLANK branch can read it)
        updatePlankStreak();

        classify();

        if (reported.equals("PLANK")) updatePlank(tsNs, s);
        else updateReps(tsNs, s);

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
        kpFilter.reset();
        ringCount = 0; ringHead = 0;
        exercising = false; activeStreak = idleStreak = 0;
        reported = candidate = "NONE"; candStreak = 0;
        repExercise = "NONE"; phase = "TOP"; reps = 0; maxP = 0; repStartNs = 0;
        repCount = 0; prevPhase = "TOP"; lastCue = ""; lastScore = -1;
        lastCueWarn = false; lastCueNs = 0; curP = 0f; displayWarn = false;
        lastPartial = false; partialTotal = 0;
        calArmed = calLocked = false; calRepsSeen = 0;
        calObsTop = calObsBottom = Float.NaN;
        defTop = defBottom = dir = 0f; adaptTop = adaptBottom = 0f;
        plankPrevAccumMs = plankAccumMs = plankHoldStartNs = 0;
        plankActive = false; plankStillStreak = 0;
        plankShownSec = -1; plankCueCache = "";
    }

    /** Test-only A/B switch for the One-Euro preprocessing pass (default ON). */
    public void setFilterEnabled(boolean on) { filterEnabled = on; }

    /** Number of partial reps flagged since reset (a "real attempt" that didn't reach bottom). */
    public int partialCount() { return partialTotal; }

    // ============================== classification ==============================

    private void classify() {
        if (ringCount < 10) { reported = "NONE"; return; }
        float kneeAmp = amp(0), elbowAmp = amp(1), openAmp = amp(2);
        // new-move motion signals (NaN→0 for the proven vectors, so the gate is unchanged there).
        float hipAngAmp = amp(10);                       // sit-up trunk-fold amplitude (degrees)
        // activity is the SUPERSET of the original metric: adding more max-terms can only raise it,
        // never lower it, so every proven-13 vector keeps its exact activity value (new amps are 0).
        float activity = Math.max(kneeAmp, Math.max(elbowAmp,
                Math.max(openAmp * 140f, hipAngAmp)));

        // Isometric-hold override: a PLANK has ~zero amplitude, so the amplitude gate would never
        // wake it. A sustained, still, in-plane horizontal hold (plankStillStreak, which itself
        // REQUIRES torso in [60,110] + near-zero elbow/knee motion) counts as active. This can only
        // fire for a body held horizontal-and-still, so vertical idle/standing vectors (torso≈0,
        // streak stays 0) are unaffected.
        boolean isometricHold = plankStillStreak >= PLANK_HOLD_FRAMES;
        if (activity > 25f || isometricHold) { activeStreak++; idleStreak = 0; }
        else { idleStreak++; activeStreak = 0; }
        if (!exercising && activeStreak >= 8) exercising = true;
        // Only release the lock after a clear idle gap (~0.7s) — not a brief pause between reps.
        if (exercising && idleStreak >= 20) { exercising = false; reported = "NONE"; candidate = "NONE"; candStreak = 0; }
        if (!exercising) { reported = "NONE"; return; }

        // ---- precompute new + existing features (NaN-safe) ----
        float avgTorso = mean(3);
        float wristUp  = mean(9);
        float torsoAmp  = amp(3);
        boolean torsoVal = !Float.isNaN(avgTorso);

        // ---- ordered 7-way decision (most-specific first; first match wins) ----
        // SITUP is ordered BEFORE PUSHUP/PRESS/CURL so an arm-moving sit-up is claimed by SITUP
        // before any elbow-amplitude move can grab it. BICEP_CURL now requires positive evidence
        // (still trunk + a genuinely flexed elbow) so it is no longer the catch-all.
        String cand;
        if (openAmp > 0.40f && (Float.isNaN(wristUp) || wristUp > WRIST_UP_JACK)) {
            cand = "JUMPING_JACK";
        } else if (torsoVal && avgTorso > SITUP_TORSO_MIN
                && hipAngAmp > SITUP_HIP_AMP && torsoAmp > SITUP_TORSO_AMP
                && kneeAmp < SITUP_KNEE_AMP_MAX && elbowAmp < 40f) {
            cand = "SITUP";
        } else if (torsoVal && avgTorso > 50f && elbowAmp > 25f && hipAngAmp < SITUP_HIP_AMP) {
            cand = "PUSHUP";
        } else if (!Float.isNaN(wristUp) && wristUp > WRIST_UP_PRESS && elbowAmp > ELBOW_PRESS_AMP
                && torsoVal && avgTorso < PRESS_TORSO_MAX && kneeAmp < PRESS_KNEE_AMP_MAX && openAmp <= 0.40f) {
            cand = "SHOULDER_PRESS";
        } else if (kneeAmp > 30f) {
            cand = "SQUAT";
        } else if (elbowAmp > 25f && kneeAmp < 20f && openAmp <= 0.40f
                && torsoAmp < CURL_TORSO_AMP_MAX
                && hipAngAmp < CURL_HIP_AMP_MAX
                && minActiveElbow() < CURL_FLEX_MIN
                && (Float.isNaN(wristUp) || wristUp < WRIST_UP_PRESS)) {
            cand = "BICEP_CURL";
        } else if (torsoVal && avgTorso > PLANK_TORSO_MIN && elbowAmp < 12f && kneeAmp < 12f
                && plankStillStreak >= PLANK_HOLD_FRAMES) {
            cand = "PLANK";
        } else {
            cand = "UNKNOWN";
        }

        if (cand.equals(candidate)) candStreak++;
        else { candidate = cand; candStreak = 1; }
        // Sticky-but-self-correcting lock (unchanged):
        //  • A brief blip can NEVER move the lock → reps survive flicker.
        //  • While no rep is banked (reps == 0) a DIFFERENT candidate that dominates for the longer
        //    SWITCH window overrides a wrong first guess; once reps are counted the lock is trusted.
        if (!cand.equals("UNKNOWN")) {
            if (reported.equals("NONE")) {
                if (candStreak >= ACQUIRE) reported = cand;
            } else if (reps == 0 && !cand.equals(reported) && candStreak >= SWITCH) {
                reported = cand;
            }
        }
    }

    // ============================== rep FSM ==============================

    private void updateReps(long tsNs, float[] s) {
        String ex = reported;
        // Paused (idle/unknown): freeze the FSM but KEEP the reps — don't zero them on a brief gap.
        if (ex.equals("NONE") || ex.equals("UNKNOWN")) return;
        boolean justLocked = false;
        // Only reset when genuinely switching to a DIFFERENT exercise (after an idle re-lock).
        if (!ex.equals(repExercise)) {
            repExercise = ex; phase = "TOP"; reps = 0; maxP = 0; repCount = 0; prevPhase = "TOP"; lastPartial = false;
            justLocked = true;
            // (re)arm calibration for the new bout
            calArmed = !ex.equals("PLANK");
            calLocked = false; calRepsSeen = 0;
            calObsTop = calObsBottom = Float.NaN;
            loadDefaults(ex);
            adaptTop = defTop; adaptBottom = defBottom;
        }

        float v = primary(ex, s);
        if (Float.isNaN(v)) return;                 // confidence-freeze

        if (calArmed && !calLocked) updateCalObs(v);

        float p = (v - adaptTop) / (adaptBottom - adaptTop);
        curP = p;

        // Recognition latency catch-up: cyclic exercises (jack, press, sit-up, high-knees) are often
        // recognised only AFTER the user has passed the BOTTOM of the first rep — typically the lock
        // lands on the way back up. Without this, that first rep is dropped (the FSM starts at TOP and
        // waits for the NEXT cycle). At the lock frame we inspect the in-flight excursion via the
        // analysis window's peak progress:
        //   • already at/past bottom now  → seed BOTTOM (rep finishes on the coming ascent);
        //   • past bottom earlier, now mid-ascent → seed ASCENDING (rep finishes when p returns to TOP).
        // The excursion genuinely happened over many frames, so repStartNs is back-dated safely past
        // minRepMs. Fires only on the lock frame → cannot double-count. Proven vectors prime from rest
        // (p low, window never bottomed at their lock frame), so this branch never triggers for them.
        if (justLocked) {
            float recentPeakP = windowPeakProgress(ex, adaptTop, adaptBottom);
            if (p >= BOTTOM_ENTER) {
                phase = "BOTTOM"; repStartNs = tsNs; maxP = p;
            } else if (recentPeakP >= BOTTOM_ENTER && p > TOP_ENTER) {
                phase = "ASCENDING"; maxP = recentPeakP;
                repStartNs = tsNs - 2L * MIN_REP_MS * 1_000_000L; // the dip already took real time
            }
        }

        boolean completed = false;
        boolean calCycle = false;   // a meaningful attempt that returned to TOP without bottoming
        switch (phase) {
            case "TOP":
                if (p > TOP_ENTER) { phase = "DESCENDING"; repStartNs = tsNs; maxP = p; }
                break;
            case "DESCENDING":
                maxP = Math.max(maxP, p);
                if (p >= BOTTOM_ENTER) phase = "BOTTOM";
                else if (p <= TOP_ENTER) {
                    if (maxP >= PARTIAL_MIN) { lastPartial = true; calCycle = true; partialTotal++; }
                    phase = "TOP";
                }
                break;
            case "BOTTOM":
                maxP = Math.max(maxP, p);
                if (p < BOTTOM_ENTER) phase = "ASCENDING";
                break;
            case "ASCENDING":
                if (p >= BOTTOM_ENTER) phase = "BOTTOM";
                else if (p <= TOP_ENTER) {
                    if ((tsNs - repStartNs) / 1_000_000L >= MIN_REP_MS) { reps++; completed = true; }
                    phase = "TOP";
                }
                break;
        }

        // accumulate the in-flight rep, analyze form on completion
        if (prevPhase.equals("TOP") && phase.equals("DESCENDING")) repCount = 0;
        if (!phase.equals("TOP")) {
            if (repCount < REP_CAP) { System.arraycopy(s, 0, repRing[repCount], 0, SAMPLE_LEN); repCount++; }
        }
        if (completed) {
            analyzeForm(ex);
            if (calArmed && !calLocked) { calRepsSeen++; maybeAdoptRange(); }
            if (reps % 5 == 0) { lastCue = reps + " in a row!"; lastCueWarn = false; }   // milestone hype
            lastCueNs = tsNs;
            repCount = 0;
            lastPartial = false;
        } else if (phase.equals("TOP")) {
            // A meaningful attempt that didn't bottom out still calibrates range (lets a limited-ROM
            // user narrow their band so subsequent reps reach the adapted bottom and count).
            if (calCycle && calArmed && !calLocked) { calRepsSeen++; maybeAdoptRange(); }
            repCount = 0;
        }
        prevPhase = phase;
    }

    /** Per-exercise primary signal (direction-agnostic progress is computed by the caller). */
    private static float primary(String ex, float[] s) {
        switch (ex) {
            case "SQUAT":          return s[0];               // knee avg
            case "PUSHUP":         return s[1];               // elbow avg
            case "BICEP_CURL":     return minv(s[6], s[7]);   // active elbow
            case "JUMPING_JACK":   return s[2];               // openness
            case "SHOULDER_PRESS": return minv(s[6], s[7]);   // active elbow (inverted anchors)
            case "SITUP":          return s[10];              // hip flexion (trunk fold)
            default:               return Float.NaN;
        }
    }

    /** Peak rep-progress reached over the current analysis window for a locked exercise (for the
     *  recognition-latency catch-up). Iterates the ring's primary signal; NaN frames skipped. */
    private float windowPeakProgress(String ex, float top, float bottom) {
        float denom = bottom - top;
        if (Math.abs(denom) < 1e-6f) return Float.NaN;
        float peak = -Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < ringCount; i++) {
            float v = primary(ex, ring[i]);
            if (Float.isNaN(v)) continue;
            float pp = (v - top) / denom;
            if (pp > peak) peak = pp;
            any = true;
        }
        return any ? peak : Float.NaN;
    }

    /** Default rest/effort anchors + direction for an exercise (the signal contract). */
    private void loadDefaults(String ex) {
        switch (ex) {
            case "SQUAT":          defTop = 165; defBottom = 95;  break;
            case "PUSHUP":         defTop = 160; defBottom = 95;  break;
            case "BICEP_CURL":     defTop = 155; defBottom = 50;  break;
            case "JUMPING_JACK":   defTop = 0.15f; defBottom = 0.85f; break;
            case "SHOULDER_PRESS": defTop = 95;  defBottom = 165; break;   // inverted: bent rest → lockout
            case "SITUP":          defTop = 150; defBottom = 70;  break;
            default:               defTop = 0;   defBottom = 1;   break;
        }
        dir = Math.signum(defBottom - defTop);
    }

    // ---- adaptive range ----
    private void updateCalObs(float v) {
        if (Float.isNaN(calObsTop) || Float.isNaN(calObsBottom)) { calObsTop = v; calObsBottom = v; return; }
        if (dir < 0) {
            if (v > calObsTop)    calObsTop    = 0.5f * v + 0.5f * calObsTop;
            if (v < calObsBottom) calObsBottom = 0.5f * v + 0.5f * calObsBottom;
        } else {
            if (v < calObsTop)    calObsTop    = 0.5f * v + 0.5f * calObsTop;
            if (v > calObsBottom) calObsBottom = 0.5f * v + 0.5f * calObsBottom;
        }
    }

    private void maybeAdoptRange() {
        if (calRepsSeen < CAL_REPS) return;
        float defSpan = Math.abs(defBottom - defTop);
        float obsSpan = Math.abs(calObsBottom - calObsTop);
        // safeguard 1: too-small observed span → keep defaults, lock.
        if (Float.isNaN(obsSpan) || obsSpan < MIN_SPAN_FRAC * defSpan) {
            adaptTop = defTop; adaptBottom = defBottom; calLocked = true; return;
        }
        // safeguard 2: inward margin (a rep reaching ~90% of the user's depth still counts).
        float t = calObsTop + RANGE_MARGIN * (calObsBottom - calObsTop);
        float b = calObsBottom - RANGE_MARGIN * (calObsBottom - calObsTop);
        // safeguard 3: clamp each anchor to defAnchor ± frac*defSpan.
        t = clampToBand(t, defTop, TOP_CLAMP_FRAC * defSpan);
        b = clampToBand(b, defBottom, BOTTOM_CLAMP_FRAC * defSpan);
        // safeguard 4: adapted span floor.
        if (Math.abs(b - t) < MIN_ADAPT_SPAN_FRAC * defSpan) {
            adaptTop = defTop; adaptBottom = defBottom; calLocked = true; return;
        }
        adaptTop = t; adaptBottom = b;
        calLocked = true;   // safeguard 5: lock until exercise switch re-arms.
    }

    private static float clampToBand(float v, float centre, float halfBand) {
        float lo = centre - halfBand, hi = centre + halfBand;
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // ---- PLANK isometric path ----
    private void updatePlankStreak() {
        if (ringCount < 10) { plankStillStreak = 0; return; }
        float avgTorso = mean(3);
        float elbowAmp = amp(1), kneeAmp = amp(0);
        boolean inPlane = !Float.isNaN(avgTorso) && avgTorso >= PLANK_TORSO_MIN && avgTorso <= PLANK_TORSO_MAX;
        if (inPlane && elbowAmp < 12f && kneeAmp < 12f) plankStillStreak++;
        else plankStillStreak = 0;
    }

    private void updatePlank(long tsNs, float[] s) {
        if (!repExercise.equals("PLANK")) {
            // entering plank mode: reset rep/timer state once.
            repExercise = "PLANK"; phase = "TOP"; reps = 0; maxP = 0; repCount = 0; prevPhase = "TOP";
            calArmed = false; calLocked = false;
            plankPrevAccumMs = plankAccumMs = 0; plankHoldStartNs = 0; plankActive = false;
        }
        boolean inPlane = !Float.isNaN(s[3]) && s[3] >= PLANK_TORSO_MIN && s[3] <= PLANK_TORSO_MAX;
        boolean level   = !Float.isNaN(s[8]) && s[8] <= PLANK_SAG_MAX;
        boolean holding = inPlane && level;
        if (holding) {
            if (!plankActive) { plankActive = true; plankHoldStartNs = tsNs; }
            plankAccumMs = plankPrevAccumMs + (tsNs - plankHoldStartNs) / 1_000_000L;
            reps = (int) (plankAccumMs / 1000L);   // reps == whole seconds held
        } else if (plankActive) {
            plankActive = false; plankPrevAccumMs = plankAccumMs;   // bank + pause
        }
        float lvl = Float.isNaN(s[8]) ? 0f : clamp01(1f - Math.max(0f, s[8] - PLANK_SAG_MAX) / 0.2f);
        lastScore = Math.round(lvl * 100f);
        curP = 0f;
    }

    // ============================== form + live coaching ==============================

    /** Held-seconds cue, cached so it allocates at most once per whole second (off the per-frame path). */
    private String plankHoldCue() {
        long sec = plankAccumMs / 1000L;
        if (sec != plankShownSec) { plankShownSec = sec; plankCueCache = sec + "s — hold strong!"; }
        return plankCueCache;
    }

    /** Pick the cue to show this frame: a fresh post-rep verdict wins for ~1.4 s, else live coaching. */
    private String displayCue(long tsNs) {
        if (!exercising || reported.equals("NONE") || reported.equals("UNKNOWN")) { displayWarn = false; return ""; }
        if (reported.equals("PLANK")) {
            if (plankActive) { displayWarn = false; return plankHoldCue(); }
            displayWarn = true; return "Get level — hips in line";
        }
        if (!lastCue.isEmpty() && (tsNs - lastCueNs) / 1_000_000L < 1400L) { displayWarn = lastCueWarn; return lastCue; }
        displayWarn = false;
        return liveCue();
    }

    /** Real-time, motion-aware coaching from the current rep phase + progress. */
    private String liveCue() {
        switch (reported) {
            case "SQUAT": case "PUSHUP":
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
            case "SHOULDER_PRESS":
                switch (phase) {
                    case "DESCENDING": return "Press up!";
                    case "BOTTOM":     return "Lock out!";
                    case "ASCENDING":  return "Lower controlled";
                    default:           return "";
                }
            case "SITUP":
                switch (phase) {
                    case "DESCENDING": return "Crunch up!";
                    case "BOTTOM":     return "Squeeze";
                    case "ASCENDING":  return "Roll back down";
                    default:           return "";
                }
            case "PLANK":
                return plankActive ? plankHoldCue() : "Get level — hips in line";
            default: return "";
        }
    }

    /** Per-rep biomechanics → 0..100 score + one actionable cue (highest-severity issue wins). */
    private void analyzeForm(String ex) {
        int frames = repCount;
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
            case "SHOULDER_PRESS": {
                float lockoutD = clamp01((Math.max(repMax(6), repMax(7)) - 95f) / (170f - 95f));
                float sym = repMeanAbsDiff(6, 7);
                float drift = clamp01(1f - repVar(3) / 400f);
                score = 100f * (0.45f * lockoutD + 0.25f * clamp01(1f - sym / 40f) + 0.15f * drift + 0.15f * tempo);
                cue = "Strong press!";
                if (lockoutD < 0.75f && sev < 8) { cue = "Press fully overhead — lock it out"; warn = true; sev = 8; }
                if (sym > 22f && sev < 6)        { cue = "Press evenly — both arms together"; warn = true; sev = 6; }
                if (drift < 0.6f && sev < 7)     { cue = "Tighten core — don't arch your back"; warn = true; sev = 7; }
                break;
            }
            case "SITUP": {
                float depth = clamp01((150f - repMin(10)) / (150f - 70f));
                float control = clamp01(1f - repVar(3) / 600f);
                score = 100f * (0.55f * depth + 0.25f * tempo + 0.20f * control);
                cue = "Clean sit-up!";
                if (depth < 0.7f && sev < 8)  { cue = "Come up higher — chest to knees"; warn = true; sev = 8; }
                if (frames < 14 && sev < 6)   { cue = "Slow it down — control the descent"; warn = true; sev = 6; }
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

    // ============================== per-rep window helpers (iterate repRing[0..repCount)) ==============================

    private float repMin(int col) {
        float m = Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < repCount; i++) { float v = repRing[i][col]; if (!Float.isNaN(v)) { m = Math.min(m, v); any = true; } }
        return any ? m : Float.NaN;
    }
    private float repMax(int col) {
        float m = -Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < repCount; i++) { float v = repRing[i][col]; if (!Float.isNaN(v)) { m = Math.max(m, v); any = true; } }
        return any ? m : 0f;
    }
    private float repRange(int col) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < repCount; i++) { float v = repRing[i][col]; if (!Float.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); any = true; } }
        return any ? hi - lo : 0f;
    }
    private float repVar(int col) {
        float sum = 0; int n = 0;
        for (int i = 0; i < repCount; i++) { float v = repRing[i][col]; if (!Float.isNaN(v)) { sum += v; n++; } }
        if (n < 2) return 0f;
        float m = sum / n, acc = 0;
        for (int i = 0; i < repCount; i++) { float v = repRing[i][col]; if (!Float.isNaN(v)) acc += (v - m) * (v - m); }
        return acc / n;
    }
    private float repMeanAbsDiff(int a, int b) {
        float sum = 0; int n = 0;
        for (int i = 0; i < repCount; i++) { float va = repRing[i][a], vb = repRing[i][b]; if (!Float.isNaN(va) && !Float.isNaN(vb)) { sum += Math.abs(va - vb); n++; } }
        return n > 0 ? sum / n : 0f;
    }

    // ============================== window helpers (iterate the ring) ==============================

    private float amp(int col) {
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < ringCount; i++) { float v = ring[i][col]; if (!Float.isNaN(v)) { lo = Math.min(lo, v); hi = Math.max(hi, v); any = true; } }
        return any ? hi - lo : 0f;
    }
    private float mean(int col) {
        float sum = 0; int n = 0;
        for (int i = 0; i < ringCount; i++) { float v = ring[i][col]; if (!Float.isNaN(v)) { sum += v; n++; } }
        return n > 0 ? sum / n : Float.NaN;
    }

    /** Minimum over the window of the more-flexed elbow (min(elbowL,elbowR) per frame). A secondary
     *  positive-evidence gate that the elbow genuinely flexed during a curl — NOT the sit-up
     *  discriminator (sit-ups are excluded by the still-trunk/still-hips gates torsoAmp/hipAngAmp<18
     *  and by SITUP being ordered ahead of BICEP_CURL in the ladder). */
    private float minActiveElbow() {
        float m = Float.MAX_VALUE; boolean any = false;
        for (int i = 0; i < ringCount; i++) {
            float v = minv(ring[i][6], ring[i][7]);   // 6=elbowL 7=elbowR
            if (!Float.isNaN(v)) { m = Math.min(m, v); any = true; }
        }
        return any ? m : Float.NaN;
    }

    // ============================== geometry ==============================

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

    /** Hip-line sag/pike for push-ups: normalized perpendicular distance from hips to the
     *  shoulder→ankle line (0 = perfectly in line). NaN unless shoulders/hips/ankles visible. */
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

    // ---- new features (NaN-safe; signed torso-normalized verticals) ----

    /** Signed (shoulderCy - wristCy)/torsoLen; >0 = wrists above shoulders. */
    private static float wristAboveShoulder(float[][] kp) {
        if (missing(kp, L_SH) || missing(kp, R_SH) || missing(kp, L_HIP) || missing(kp, R_HIP)
                || missing(kp, L_WR) || missing(kp, R_WR)) return Float.NaN;
        float shy = (kp[L_SH][1] + kp[R_SH][1]) / 2f;
        float wy  = (kp[L_WR][1] + kp[R_WR][1]) / 2f;
        float tl = torsoLenOf(kp);
        if (Float.isNaN(tl)) return Float.NaN;
        return (shy - wy) / tl;
    }

    /** avg(angle(SH,HIP,KNEE)_L, _R) — trunk↔thigh fold (sit-up driver). */
    private static float hipFlex(float[][] kp) {
        float l = angle(kp, L_SH, L_HIP, L_KN);
        float r = angle(kp, R_SH, R_HIP, R_KN);
        return avg(l, r);
    }

    /** (hipCy - kneeCy)/torsoLen for one side; +ve = knee lifted. */
    private static float kneeLift(float[][] kp, int hipSide, int knIdx) {
        if (missing(kp, L_SH) || missing(kp, R_SH) || missing(kp, L_HIP) || missing(kp, R_HIP)
                || missing(kp, knIdx)) return Float.NaN;
        float hipCy = (kp[L_HIP][1] + kp[R_HIP][1]) / 2f;
        float kneeCy = kp[knIdx][1];
        float tl = torsoLenOf(kp);
        if (Float.isNaN(tl)) return Float.NaN;
        return (hipCy - kneeCy) / tl;
    }

    private static float torsoLenOf(float[][] kp) {
        if (missing(kp, L_SH) || missing(kp, R_SH) || missing(kp, L_HIP) || missing(kp, R_HIP)) return Float.NaN;
        float shx = (kp[L_SH][0] + kp[R_SH][0]) / 2f, shy = (kp[L_SH][1] + kp[R_SH][1]) / 2f;
        float hpx = (kp[L_HIP][0] + kp[R_HIP][0]) / 2f, hpy = (kp[L_HIP][1] + kp[R_HIP][1]) / 2f;
        float tl = (float) Math.hypot(shx - hpx, shy - hpy);
        return tl < 1e-3f ? 1f : tl;
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
            case "BICEP_CURL": return "BICEP CURL";
            case "JUMPING_JACK": return "JUMPING JACK";
            case "SHOULDER_PRESS": return "SHOULDER PRESS";
            case "SITUP": return "SIT-UP";
            case "PLANK": return "PLANK";
            case "UNKNOWN": return "…";
            default: return "READY";
        }
    }
}
