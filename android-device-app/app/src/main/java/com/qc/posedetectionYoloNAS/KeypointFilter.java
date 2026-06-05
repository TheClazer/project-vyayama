//============================================================================
// KeypointFilter — pre-processing for VyāyāmaCoach.
// Per-joint One-Euro smoothing + teleport rejection + short-gap hold, with an
// advisory per-joint reliability signal. Standalone (mirrors the COCO indices it
// needs), pure java.util-free actually: no imports at all. Zero per-frame heap
// allocation: every buffer is pre-allocated in the constructor and reused.
//
// Contract: filter(raw, tsNs) returns the INTERNAL [17][2] cleaned buffer.
//  - A truly-missing joint (raw (0,0)) stays (0,0) after a >GAP_HOLD_FRAMES gap,
//    so VyāyāmaCoach's existing missing()/NaN logic is unchanged.
//  - A present joint is smoothed; a teleport (jump > frac*bodySize) is rejected by
//    holding the last good estimate for up to MAX_HOLD_ON_OUTLIER frames, then it
//    re-seeds (the new position is accepted as real).
//  - The filter NEVER suppresses a present joint permanently; reliability is advisory.
//============================================================================
package com.qc.posedetectionYoloNAS;

public final class KeypointFilter {
    public static final int KP = 17;

    // COCO indices (mirror VyayamaCoach so the filter is standalone)
    static final int L_SH = 5, R_SH = 6, L_HIP = 11, R_HIP = 12;

    // ---- One-Euro (per axis, per joint) ----
    private static final float OE_MIN_CUTOFF = 1.3f;
    private static final float OE_BETA       = 0.012f;
    private static final float OE_DCUTOFF    = 1.0f;
    private static final float DEFAULT_FPS   = 30.0f;
    private static final float MIN_DT        = 1.0f / 120.0f;  // 8.3 ms
    private static final float MAX_DT        = 1.0f / 5.0f;    // 200 ms
    // ---- teleport / outlier ----
    private static final float TELEPORT_FRAC       = 0.55f;
    private static final int   MAX_HOLD_ON_OUTLIER = 3;
    // ---- gap hold ----
    private static final int   GAP_HOLD_FRAMES = 4;
    // ---- reliability ----
    private static final int   WARMUP_FRAMES   = 3;
    private static final float BODY_SIZE_FLOOR = 40f;

    // ---- pre-allocated per-joint state (zero per-frame new) ----
    private final float[][] xHat     = new float[KP][2]; // smoothed position estimate
    private final float[][] dxHat    = new float[KP][2]; // smoothed derivative estimate
    private final float[][] xPrevRaw = new float[KP][2]; // last accepted raw position
    private final float[][] out      = new float[KP][2]; // returned buffer
    private final boolean[] oeInit   = new boolean[KP];
    private final boolean[] alive    = new boolean[KP];  // currently emitting a position
    private final boolean[] reliable = new boolean[KP];
    private final int[]     gapCount    = new int[KP];
    private final int[]     outlierHold = new int[KP];
    private final int[]     seenStreak  = new int[KP];
    private long    lastTsNs;
    private boolean haveTs;
    private float   lastReliability;

    public KeypointFilter() { reset(); }

    public void reset() {
        for (int i = 0; i < KP; i++) {
            xHat[i][0] = xHat[i][1] = 0f;
            dxHat[i][0] = dxHat[i][1] = 0f;
            xPrevRaw[i][0] = xPrevRaw[i][1] = 0f;
            out[i][0] = out[i][1] = 0f;
            oeInit[i] = false; alive[i] = false; reliable[i] = false;
            gapCount[i] = 0; outlierHold[i] = 0; seenStreak[i] = 0;
        }
        lastTsNs = 0L; haveTs = false; lastReliability = 0f;
    }

    public float reliability() { return lastReliability; }
    public boolean isReliable(int kpIndex) { return reliable[kpIndex]; }

    /** Filter a raw [17][2] frame. Returns the internal cleaned [17][2] ((0,0)=missing). */
    public float[][] filter(float[][] raw, long tsNs) {
        // ---- effective dt (clamped) ----
        float dt;
        if (!haveTs) { dt = 1.0f / DEFAULT_FPS; }
        else {
            float d = (tsNs - lastTsNs) / 1_000_000_000f;
            if (d < MIN_DT) d = MIN_DT;
            if (d > MAX_DT) d = MAX_DT;
            dt = d;
        }
        lastTsNs = tsNs; haveTs = true;

        // ---- per-frame body-size → teleport threshold ----
        float body = bodySize(raw);
        float teleport = TELEPORT_FRAC * body;
        float teleportSq = teleport * teleport;

        for (int i = 0; i < KP; i++) {
            boolean present = !(raw[i][0] == 0f && raw[i][1] == 0f);
            if (!present) {
                handleMissing(i);
                continue;
            }
            float rx = raw[i][0], ry = raw[i][1];

            if (!alive[i] || !oeInit[i]) {
                // first sighting (or re-acquire after a long gap): seed, no smoothing yet.
                reseed(i, rx, ry);
                seenStreak[i]++;
                emitFromHat(i);
                continue;
            }

            // teleport rejection: jump from last accepted raw beyond threshold → hold.
            float jdx = rx - xPrevRaw[i][0], jdy = ry - xPrevRaw[i][1];
            float jumpSq = jdx * jdx + jdy * jdy;
            if (jumpSq > teleportSq && outlierHold[i] < MAX_HOLD_ON_OUTLIER) {
                outlierHold[i]++;
                seenStreak[i] = 0;          // unreliable while holding
                emitHeld(i);                // keep the last good smoothed estimate
                continue;
            }
            // accepted (either in-range, or we've held long enough → snap to new truth)
            if (outlierHold[i] >= MAX_HOLD_ON_OUTLIER) {
                reseed(i, rx, ry);          // K-frame snap: the new spot is real
                outlierHold[i] = 0;
                seenStreak[i] = 1;
                emitFromHat(i);
                continue;
            }
            outlierHold[i] = 0;
            oneEuro(i, rx, ry, dt);
            xPrevRaw[i][0] = rx; xPrevRaw[i][1] = ry;
            seenStreak[i]++;
            emitFromHat(i);
        }

        finalizeReliability();
        return out;
    }

    // ---- per-joint handlers ----
    private void handleMissing(int i) {
        if (alive[i] && gapCount[i] < GAP_HOLD_FRAMES) {
            gapCount[i]++;
            seenStreak[i] = 0;
            emitHeld(i);                    // bridge a short dropout with the held estimate
        } else {
            alive[i] = false; oeInit[i] = false;
            gapCount[i] = 0; outlierHold[i] = 0; seenStreak[i] = 0;
            out[i][0] = 0f; out[i][1] = 0f; // truly missing → (0,0)
        }
    }

    private void reseed(int i, float x, float y) {
        xHat[i][0] = x; xHat[i][1] = y;
        dxHat[i][0] = 0f; dxHat[i][1] = 0f;
        xPrevRaw[i][0] = x; xPrevRaw[i][1] = y;
        oeInit[i] = true; alive[i] = true;
        gapCount[i] = 0; outlierHold[i] = 0;
    }

    private void emitFromHat(int i) {
        gapCount[i] = 0;
        out[i][0] = xHat[i][0]; out[i][1] = xHat[i][1];
    }

    private void emitHeld(int i) {
        out[i][0] = xHat[i][0]; out[i][1] = xHat[i][1];
    }

    // ---- One-Euro filter (per axis) ----
    private void oneEuro(int i, float rx, float ry, float dt) {
        oneEuroAxis(i, 0, rx, dt);
        oneEuroAxis(i, 1, ry, dt);
    }

    private void oneEuroAxis(int i, int ax, float x, float dt) {
        float prevX = xHat[i][ax];
        float dxRaw = (x - prevX) / dt;
        float aD = alphaFor(OE_DCUTOFF, dt);
        float dxS = aD * dxRaw + (1f - aD) * dxHat[i][ax];
        dxHat[i][ax] = dxS;
        float cutoff = OE_MIN_CUTOFF + OE_BETA * Math.abs(dxS);
        float a = alphaFor(cutoff, dt);
        xHat[i][ax] = a * x + (1f - a) * prevX;
    }

    private static float alphaFor(float cutoff, float dt) {
        float tau = 1f / (2f * (float) Math.PI * cutoff);
        return 1f / (1f + tau / dt);
    }

    // ---- body-size cascade (all floored at BODY_SIZE_FLOOR) ----
    private float bodySize(float[][] raw) {
        float torso = pairCentreDist(raw, L_SH, R_SH, L_HIP, R_HIP);
        if (!Float.isNaN(torso) && torso > 0f) return Math.max(torso, BODY_SIZE_FLOOR);
        float shW = pairDist(raw, L_SH, R_SH);
        if (!Float.isNaN(shW) && shW > 0f) return Math.max(2f * shW, BODY_SIZE_FLOOR);
        float hipW = pairDist(raw, L_HIP, R_HIP);
        if (!Float.isNaN(hipW) && hipW > 0f) return Math.max(2.5f * hipW, BODY_SIZE_FLOOR);
        return Math.max(300f, BODY_SIZE_FLOOR);
    }

    private static boolean present(float[][] raw, int i) {
        return !(raw[i][0] == 0f && raw[i][1] == 0f);
    }

    private static float pairDist(float[][] raw, int a, int b) {
        if (!present(raw, a) || !present(raw, b)) return Float.NaN;
        return (float) Math.hypot(raw[a][0] - raw[b][0], raw[a][1] - raw[b][1]);
    }

    private static float pairCentreDist(float[][] raw, int a1, int a2, int b1, int b2) {
        if (!present(raw, a1) || !present(raw, a2) || !present(raw, b1) || !present(raw, b2)) return Float.NaN;
        float ax = (raw[a1][0] + raw[a2][0]) * 0.5f, ay = (raw[a1][1] + raw[a2][1]) * 0.5f;
        float bx = (raw[b1][0] + raw[b2][0]) * 0.5f, by = (raw[b1][1] + raw[b2][1]) * 0.5f;
        return (float) Math.hypot(ax - bx, ay - by);
    }

    private void finalizeReliability() {
        int rel = 0;
        for (int i = 0; i < KP; i++) {
            boolean r = alive[i] && seenStreak[i] >= WARMUP_FRAMES && outlierHold[i] == 0;
            reliable[i] = r;
            if (r) rel++;
        }
        lastReliability = (float) rel / KP;
    }
}
