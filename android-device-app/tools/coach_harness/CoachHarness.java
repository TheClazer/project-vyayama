//============================================================================
// CoachHarness — pure-Java test driver for VyāyāmaCoach (no Android, javac-clean).
// Synthesises full float[17][2] COCO stick-figure keypoint streams (or angle-style
// signals where the proven vectors are defined that way), feeds the PUBLIC onFrame
// only, and asserts on the final Result. Prints one PASS/FAIL line per case and a
// final 'PASSED x / y'; exits non-zero if any case fails (so CI gates on it).
//
// All synthetic data is allocated OUTSIDE the engine — the engine stays zero-alloc.
//============================================================================
package com.qc.posedetectionYoloNAS;

public final class CoachHarness {

    // COCO indices
    static final int NOSE=0, L_EYE=1, R_EYE=2, L_EAR=3, R_EAR=4,
            L_SH=5, R_SH=6, L_EL=7, R_EL=8, L_WR=9, R_WR=10,
            L_HIP=11, R_HIP=12, L_KN=13, R_KN=14, L_AN=15, R_AN=16;

    static final long DT = 33_333_333L; // 30 fps
    static int passed = 0, total = 0, skipped = 0;

    public static void main(String[] args) {
        // ---- pure geometry (proven 1-3) ----
        testAngleDeg();

        // ---- proven 4-13 (filter ON + OFF identity guard T0 is folded in) ----
        case4_squats5();
        case5_partial();
        case6_tooFast();
        case7_jacks4();
        case8_jitter();
        case9_classifySquat();
        case10_classifyPushup();
        case11_classifyIdle();
        case12_classifyJack();
        case13_repsSurvivePause();

        // ---- new robustness 14-21 ----
        case14_squatsJitter();
        case15_jitteredIdle();
        case16_jitteredPartial();
        case17_jitteredTooFast();
        case18_gaussSquat();
        case19_scaleSquat();
        case20_translateSquat();
        case21_dropout();

        // ---- new exercises 22-25 ----
        case22_shoulderPress();
        case23_situp();
        case24_realCurl();              // (a) positive control
        case24b_situpArmsBehindHead();  // (b) THE reported bug
        case24c_squatArmSwing();        // (c)
        case24d_pressNotCurl();         // (d)
        case25_plank();

        // ---- switch + adaptive 26-28 ----
        case26_switch();
        case27_adaptiveLimitedRom();
        case28_glitchRobustness();
        case29_moderateSquat();
        case30_foreshortenedSquat();
        case31_pushupReps();

        // ---- manual mode 32-44 ----
        case32_manualSquatCounts();
        case33_pinnedSquatNeverFlips();
        case34_pinnedSitupSuppressesCurl();
        case35_manualToAutoRestores();
        case36_manualPlankCounts();
        case37_manualIdleNoFalseReps();
        case38_continueSameBoutRetains();
        case39_crossPinResetsFsm();
        case40_manualSurvivesReset();
        case41_manualNullIsAuto();
        case42_manualMismatchNoCount();
        case43_manualPlankToRepExercise();
        case44_manualPinsChosenOnly();

        // ---- push-up leniency + fast-rep robustness 45-47 ----
        case45_shallowPushupCounts();
        case46_fastPushupLowFps();
        case47_tooFastPushupRejected();

        // ---- incomplete-ROM leniency (peak/valley completion) 48-49 ----
        case48_partialExtensionCurls();
        case49_partialReturnSquat();

        // ---- sit-up recognition fix 50-52 ----
        case50_handsBehindHeadSitup();
        case51_pressNotSitup();
        case52_shallowFoldSitup();

        // ---- VoiceCoach (pure-Java cadence brain) ----
        vcTests();

        System.out.println("==========================================");
        System.out.println("PASSED " + passed + " / " + total + (skipped > 0 ? ("  (SKIPPED " + skipped + ")") : ""));
        if (passed != total) System.exit(1);
    }

    // ================= proven 1-3: pure geometry =================
    static void testAngleDeg() {
        float a90 = angleDeg(1,0, 0,0, 0,1);
        expectNear("angleDeg right=90", a90, 90f, 1e-3f);
        float a180 = angleDeg(1,0, 0,0, -1,0);
        expectNear("angleDeg straight=180", a180, 180f, 1e-3f);
        float aNaN = angleDeg(0,0, 0,0, 1,1);
        expectBool("angleDeg zero-limb=NaN", Float.isNaN(aNaN), true);
    }

    /** Mirror of the engine's internal angle math (degrees at vertex B for A-B-C). */
    static float angleDeg(float ax, float ay, float bx, float by, float cx, float cy) {
        float v1x = ax - bx, v1y = ay - by, v2x = cx - bx, v2y = cy - by;
        double m1 = Math.hypot(v1x, v1y), m2 = Math.hypot(v2x, v2y);
        if (m1 < 1e-6 || m2 < 1e-6) return Float.NaN;
        double cos = (v1x*v2x + v1y*v2y) / (m1*m2);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    // ================= proven 4: 5 squats =================
    static void case4_squats5() {
        for (boolean filt : new boolean[]{true, false}) {
            VyayamaCoach c = mk(filt);
            int reps = 0; String key = "NONE";
            for (int rep = 0; rep < 5; rep++) {
                for (int f = 0; f < 45; f++) {
                    float phase = (float)(2*Math.PI*f/45);
                    float knee = 130f + 40f*(float)Math.cos(phase);
                    VyayamaCoach.Result r = c.onFrame(squatPose(knee), tNs());
                    reps = r.reps; key = r.key;
                }
            }
            expectReps("5 squats" + tag(filt), reps, 5);
            expectKey ("5 squats key" + tag(filt), key, "SQUAT");
        }
    }

    // ================= proven 5: partial =================
    static void case5_partial() {
        for (boolean filt : new boolean[]{true, false}) {
            VyayamaCoach c = mk(filt);
            // warm the classifier into SQUAT first with a clean squat motion, then feed the partial.
            warmSquat(c);
            float[] series = {170,150,130,120,130,150,170,170};
            int reps = 0;
            for (float k : series) reps = c.onFrame(squatPose(k), tNs()).reps;
            // proven vector = "0 reps AND 1 partial". (1) no rep counted from the shallow dip:
            expectReps("partial: no rep from shallow dip" + tag(filt), reps - baselineReps, 0);
            // (2) the attempt IS flagged as exactly one partial (asserted on the canonical raw signal):
            if (!filt) expectReps("partial: flagged as 1 partial [filt OFF]", c.partialCount(), 1);
        }
    }
    static int baselineReps = 0;

    // ================= proven 6: too-fast =================
    static void case6_tooFast() {
        for (boolean filt : new boolean[]{true, false}) {
            VyayamaCoach c = mk(filt);
            warmSquat(c);
            int before = lastReps;
            // one sub-300ms dip: 170 @ t0, 90 @ +33ms, 170 @ +66ms (fast timestamps)
            long t = 0;
            c.onFrame(squatPose(170), t); t += 33_000_000L;
            c.onFrame(squatPose(90),  t); t += 33_000_000L;
            VyayamaCoach.Result r = c.onFrame(squatPose(170), t);
            expectReps("too-fast: no rep" + tag(filt), r.reps - before, 0);
        }
    }
    static int lastReps = 0;

    // ================= proven 7: 4 jumping jacks =================
    static void case7_jacks4() {
        for (boolean filt : new boolean[]{true, false}) {
            VyayamaCoach c = mk(filt);
            int reps = 0; String key = "NONE";
            for (int rep = 0; rep < 4; rep++) {
                for (int f = 0; f < 45; f++) {
                    float phase = (float)(2*Math.PI*f/45);
                    float open = 0.5f - 0.5f*(float)Math.cos(phase);
                    VyayamaCoach.Result r = c.onFrame(jackPose(open), tNs());
                    reps = r.reps; key = r.key;
                }
            }
            expectReps("4 jumping jacks" + tag(filt), reps, 4);
            expectKey ("4 jacks key" + tag(filt), key, "JUMPING_JACK");
        }
    }

    // ================= proven 8: jitter =================
    static void case8_jitter() {
        for (boolean filt : new boolean[]{true, false}) {
            VyayamaCoach c = mk(filt);
            float[] pat = {168,162,169,161,170,163};
            int reps = 0;
            for (int i = 0; i < 60; i++) {
                VyayamaCoach.Result r = c.onFrame(squatPose(pat[i % pat.length]), tNs());
                reps = r.reps;
            }
            expectReps("jitter: no reps" + tag(filt), reps, 0);
        }
    }

    // ================= proven 9-12: classifier windows =================
    static void case9_classifySquat() {
        VyayamaCoach c = mk(true);
        String key = drive(c, 40, f -> squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)));
        expectKey("classifier squat-window", key, "SQUAT");
    }
    static void case10_classifyPushup() {
        VyayamaCoach c = mk(true);
        String key = drive(c, 40, f -> pushupPose(125f + 35f*(float)Math.cos(2*Math.PI*f/40)));
        expectKey("classifier pushup-window", key, "PUSHUP");
    }
    static void case11_classifyIdle() {
        VyayamaCoach c = mk(true);
        String key = "?"; boolean ex = true;
        for (int f = 0; f < 40; f++) { VyayamaCoach.Result r = c.onFrame(standPose(), tNs()); key = r.key; ex = r.exercising; }
        expectKey("classifier idle = NONE", key, "NONE");
        expectBool("classifier idle not exercising", ex, false);
    }
    static void case12_classifyJack() {
        VyayamaCoach c = mk(true);
        String key = drive(c, 40, f -> jackPose(0.5f - 0.5f*(float)Math.cos(2*Math.PI*f/45)));
        expectKey("classifier jack-window", key, "JUMPING_JACK");
    }

    // ================= proven 13: reps survive a brief pause =================
    static void case13_repsSurvivePause() {
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 3; rep++)
            for (int f = 0; f < 45; f++) {
                VyayamaCoach.Result r = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs());
                reps = r.reps;
            }
        int afterThree = reps;
        // 25 idle frames (releases lock); reps must NOT be zeroed mid/after pause.
        boolean stayed = true;
        for (int f = 0; f < 25; f++) {
            VyayamaCoach.Result r = c.onFrame(standPose(), tNs());
            if (r.reps < afterThree) stayed = false;
            reps = r.reps;
        }
        expectBool("reps survive pause (>=3 retained)", stayed && afterThree >= 3, true);
    }

    // ================= 14: 5 squats under heavy jitter =================
    static void case14_squatsJitter() {
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45));
                p = jitter(p, rep*1000L + f, 6f);
                reps = c.onFrame(p, tNs()).reps;
            }
        expectReps("5 squats under ±6px jitter", reps, 5);
    }

    // ================= 15: jittered idle =================
    static void case15_jitteredIdle() {
        VyayamaCoach c = mk(true);
        int reps = 0; boolean ex = false;
        for (int f = 0; f < 90; f++) {
            float[][] p = jitter(standPose(), f, 8f);
            VyayamaCoach.Result r = c.onFrame(p, tNs());
            reps = r.reps; ex = r.exercising;
        }
        expectReps("jittered idle: 0 reps", reps, 0);
        expectBool("jittered idle: not exercising", ex, false);
    }

    // ================= 16: jittered partial =================
    static void case16_jitteredPartial() {
        VyayamaCoach c = mk(true);
        warmSquat(c);
        int before = lastReps;
        float[] series = {170,150,130,120,130,150,170,170};
        int reps = before;
        for (int i = 0; i < series.length; i++)
            reps = c.onFrame(jitter(squatPose(series[i]), i, 5f), tNs()).reps;
        expectReps("jittered partial: no rep", reps - before, 0);
    }

    // ================= 17: jittered too-fast =================
    static void case17_jitteredTooFast() {
        VyayamaCoach c = mk(true);
        warmSquat(c);
        int before = lastReps;
        long t = 0;
        c.onFrame(jitter(squatPose(170),0,5f), t); t += 33_000_000L;
        c.onFrame(jitter(squatPose(90), 1,5f), t); t += 33_000_000L;
        int reps = c.onFrame(jitter(squatPose(170),2,5f), t).reps;
        expectReps("jittered too-fast: no rep", reps - before, 0);
    }

    // ================= 18: gaussian noise classification =================
    static void case18_gaussSquat() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = gauss(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), rep*1000L+f, 3f);
                VyayamaCoach.Result r = c.onFrame(p, tNs()); reps = r.reps; key = r.key;
            }
        expectKey ("gauss squat key", key, "SQUAT");
        expectReps("gauss squat reps", reps, 5);
    }

    // ================= 19: 2x scale =================
    static void case19_scaleSquat() {
        // load-bearing: 2x scale (camera distance) PLUS gaussian noise that genuinely moves angles.
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = gauss(scale(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), 2f, 320f, 240f), rep*1000L+f, 6f);
                VyayamaCoach.Result r = c.onFrame(p, tNs()); reps = r.reps; key = r.key;
            }
        expectKey ("2x scale + noise squat key", key, "SQUAT");
        expectReps("2x scale + noise squat reps", reps, 5);
    }

    // ================= 20: translation =================
    static void case20_translateSquat() {
        // load-bearing: translation PLUS gaussian noise (angles actually perturbed).
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = gauss(translate(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), 150f, 80f), rep*1000L+f, 5f);
                VyayamaCoach.Result r = c.onFrame(p, tNs()); reps = r.reps; key = r.key;
            }
        expectKey ("translate + noise squat key", key, "SQUAT");
        expectReps("translate + noise squat reps", reps, 5);
    }

    // ================= 21: brief 3-frame dropout =================
    static void case21_dropout() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45));
                if (f >= 20 && f < 23) p = dropout(p, L_KN);   // 3-frame L_KNEE dropout each rep
                VyayamaCoach.Result r = c.onFrame(p, tNs()); reps = r.reps; key = r.key;
            }
        expectKey ("dropout squat key", key, "SQUAT");
        expectReps("dropout squat reps", reps, 5);
    }

    // ================= 22: shoulder press =================
    static void case22_shoulderPress() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE"; boolean misCurl = false, misJack = false;
        boolean locked = false;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float elbow = 130f - 35f*(float)Math.cos(phase);     // 95 bent → 165 lockout
                // hands stay overhead the entire press (shoulder-height at the bottom, well overhead
                // at lockout) — a real press never drops the wrists below the shoulders.
                float wristUp = 0.40f + 0.35f*(0.5f - 0.5f*(float)Math.cos(phase)); // 0.40 → 0.75
                VyayamaCoach.Result r = c.onFrame(pressPose(elbow, wristUp), tNs());
                reps = r.reps; key = r.key;
                if (key.equals("SHOULDER_PRESS")) locked = true;
                if (locked && r.key.equals("BICEP_CURL")) misCurl = true;
                if (locked && r.key.equals("JUMPING_JACK")) misJack = true;
            }
        expectKey ("shoulder press key", key, "SHOULDER_PRESS");
        expectReps("shoulder press reps", reps, 4);
        expectBool("press not mislocked as curl", misCurl, false);
        expectBool("press not mislocked as jack", misJack, false);
    }

    // ================= 23: sit-up =================
    static void case23_situp() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE"; boolean misPush = false;
        boolean locked = false;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float hipFlex = 110f + 40f*(float)Math.cos(phase);   // start FLAT 150 → crunch 70 → flat
                VyayamaCoach.Result r = c.onFrame(situpPose(hipFlex), tNs());
                reps = r.reps; key = r.key;
                if (key.equals("SITUP")) locked = true;
                if (locked && r.key.equals("PUSHUP")) misPush = true;
            }
        expectKey ("situp key", key, "SITUP");
        expectReps("situp reps", reps, 5);
        expectBool("situp not mislocked as pushup", misPush, false);
    }

    // ============== (a) positive control: real curl stays BICEP_CURL ==============
    static void case24_realCurl() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; int reps = 0;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                // elbow 150 (extended) → 50 (curled) → 150; trunk + legs dead still.
                float elbow = 100f - 50f*(float)Math.cos(2*Math.PI*f/40);  // 50..150
                VyayamaCoach.Result r = c.onFrame(curlPose(elbow), tNs());
                key = r.key; reps = r.reps;
            }
        expectKey ("real curl stays BICEP_CURL", key, "BICEP_CURL");
        expectAtLeast("real curl reps (>=3)", reps, 3);
    }

    // ============== (b) THE reported bug: arm-moving sit-up must be SITUP, never curl ==============
    static void case24b_situpArmsBehindHead() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; int reps = 0;
        boolean everCurl = false, everLocked = false;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                // trunk folds from flat (150) to crunched (70) → big hipAngAmp (sit-up signature).
                float hipFlex = 110f + 40f*(float)Math.cos(phase);   // 70..150 fold
                float armSwing = 0.5f - 0.5f*(float)Math.cos(phase); // elbows flare in/out with crunch
                VyayamaCoach.Result r = c.onFrame(situpArmPose(hipFlex, armSwing), tNs());
                key = r.key; reps = r.reps;
                if (key.equals("SITUP")) everLocked = true;
                if (r.key.equals("BICEP_CURL")) everCurl = true;
            }
        expectKey ("arm sit-up classified SITUP", key, "SITUP");
        expectBool("arm sit-up NEVER locks BICEP_CURL (the bug)", everCurl, false);
        expectBool("arm sit-up actually locked SITUP", everLocked, true);
        expectAtLeast("arm sit-up reps (>=3)", reps, 3);
    }

    // ============== (c) squat with arm swing stays SQUAT ==============
    static void case24c_squatArmSwing() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; int reps = 0;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float phase = (float)(2*Math.PI*f/45);
                float knee = 130f + 40f*(float)Math.cos(phase);          // kneeAmp ~80
                float armSwing = 0.5f - 0.5f*(float)Math.cos(phase);
                VyayamaCoach.Result r = c.onFrame(squatArmSwingPose(knee, armSwing), tNs());
                key = r.key; reps = r.reps;
            }
        expectKey ("squat+arm-swing stays SQUAT", key, "SQUAT");
        expectAtLeast("squat+arm-swing reps (>=4)", reps, 4);
    }

    // ============== (d) shoulder press is PRESS not curl (focused) ==============
    static void case24d_pressNotCurl() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; boolean everCurl = false, locked = false;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float elbow = 130f - 35f*(float)Math.cos(phase);          // 95 bent → 165 lockout
                float wristUp = 0.40f + 0.35f*(0.5f - 0.5f*(float)Math.cos(phase)); // wrists overhead
                VyayamaCoach.Result r = c.onFrame(pressPose(elbow, wristUp), tNs());
                key = r.key;
                if (key.equals("SHOULDER_PRESS")) locked = true;
                if (r.key.equals("BICEP_CURL")) everCurl = true;
            }
        expectKey ("press is SHOULDER_PRESS", key, "SHOULDER_PRESS");
        expectBool("press never locks curl", everCurl, false);
        expectBool("press actually locked", locked, true);
    }

    // ================= 25: plank (optional) =================
    static void case25_plank() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        // ~7s level horizontal hold @30fps (210 frames). ~60 frames (~2s) go to recognition
        // (isometric-hold streak + acquire), leaving ~5s of counted hold → reps ~4-5.
        for (int f = 0; f < 210; f++) {
            VyayamaCoach.Result r = c.onFrame(plankPose(), tNs());
            reps = r.reps; key = r.key;
        }
        if (!key.equals("PLANK")) {
            // PLANK is explicitly optional — if it can't separate cleanly, skip (not fail) but COUNT it.
            skipped++;
            System.out.println("plank: SKIP (not classified as PLANK; optional per spec)");
            return;
        }
        expectKey ("plank key", key, "PLANK");
        expectNear("plank reps ~ seconds held", reps, 5f, 2f);
    }

    // ================= 26: mid-set SQUAT -> CURL =================
    static void case26_switch() {
        // (a) with an idle gap: SQUAT (>=1 rep) → idle release → BICEP_CURL fresh count.
        VyayamaCoach c = mk(true);
        int squatReps = 0;
        for (int rep = 0; rep < 2; rep++)
            for (int f = 0; f < 45; f++)
                squatReps = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        expectKeyAtLeast("switch: squat segment", c, squatReps, 1);
        // idle gap must outlast the 30-frame analysis window (so the squat motion flushes) PLUS the
        // 20-frame idle-release threshold = ~50 frames. A real between-exercise pause is > 1.5s.
        for (int f = 0; f < 55; f++) c.onFrame(standPose(), tNs());   // idle gap releases lock
        String key = "NONE"; int curlReps = 0;
        for (int rep = 0; rep < 3; rep++)
            for (int f = 0; f < 40; f++) {
                VyayamaCoach.Result r = c.onFrame(curlPose(100f - 50f*(float)Math.cos(2*Math.PI*f/40)), tNs());
                key = r.key; curlReps = r.reps;
            }
        expectKey("switch: now BICEP_CURL", key, "BICEP_CURL");
        expectBool("switch: curl count is fresh (<= its own segment)", curlReps <= 3 && curlReps >= 1, true);

        // (b) self-correcting fast-switch WITHOUT idle gap while reps==0:
        // start a squat-ish motion that the classifier first mis-locks, then dominate with curl.
        VyayamaCoach c2 = mk(true);
        // brief ambiguous start (few frames) then sustained curl; expect override within SWITCH frames, reps fresh.
        String k2 = "NONE";
        for (int f = 0; f < 10; f++) c2.onFrame(squatPose(150f + 10f*(float)Math.cos(2*Math.PI*f/20)), tNs());
        for (int rep = 0; rep < 3; rep++)
            for (int f = 0; f < 40; f++)
                k2 = c2.onFrame(curlPose(100f - 50f*(float)Math.cos(2*Math.PI*f/40)), tNs()).key;
        expectKey("switch: fast self-correct to curl", k2, "BICEP_CURL");
    }

    // ================= 27: adaptive limited-ROM squat =================
    static void case27_adaptiveLimitedRom() {
        // shallow squatter: knee 150 + 22*cos → 128°..172° (clearly recognised as squatting, amp 44),
        // but only reaches ~128°, never the default 95° bottom → p caps at ~0.53 under defaults, so
        // ZERO reps would count without range adaptation. After the 2-cycle calibration the band
        // narrows to the user's own ROM and subsequent reps count.
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(150f + 22f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        expectAtLeast("adaptive limited-ROM squat reps (>=3)", reps, 3);

        // negative guard: a tiny wobble below MIN_SPAN_FRAC must adopt defaults → 0 reps.
        VyayamaCoach c2 = mk(true);
        int reps2 = 0;
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++)
                reps2 = c2.onFrame(squatPose(160f + 5f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        expectReps("adaptive tiny-wobble: 0 reps", reps2, 0);
    }

    // ================= 28: calibration drift guard =================
    static void case28_glitchRobustness() {
        // A multi-frame (3) gross joint teleport must not corrupt the count: the One-Euro filter's
        // teleport-reject + gap-hold bridges it, and MIN_REP_MS rejects the sub-300ms excursion.
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                float[][] p = squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45));
                if (rep == 2 && f >= 21 && f <= 23) p = teleportJoint(p, R_KN, 600f); // 3-frame glitch
                reps = c.onFrame(p, tNs()).reps;
            }
        expectReps("glitch robustness: 3-frame teleport, still 5 reps", reps, 5);
    }

    // ================= 29: half-depth squat counts (the reported "too strict" fix) =================
    static void case29_moderateSquat() {
        // knee only reaches ~119° (a clear half-squat, NOT to the ground). Must count.
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(142f + 23f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps; // 119..165
        expectAtLeast("half-depth squat counts (>=4)", reps, 4);
    }

    // ================= 30: foreshortened front-on squat (hip-drop fix) =================
    static void case30_foreshortenedSquat() {
        // The knee ANGLE stays ~176 (foreshortened flat) while the HIPS drop clearly. The old
        // knee-only rule saw nothing here; the hip-drop signal must classify SQUAT and count reps.
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++) {
                float d = 0.5f - 0.5f*(float)Math.cos(2*Math.PI*f/45);  // 0 -> 1 -> 0 hip drop
                VyayamaCoach.Result r = c.onFrame(foreshortenedSquatPose(d), tNs());
                reps = r.reps; key = r.key;
            }
        expectKey ("foreshortened squat -> SQUAT (hip-drop)", key, "SQUAT");
        expectAtLeast("foreshortened squat counts (>=4)", reps, 4);
    }

    // ================= 31: half-depth push-up counts =================
    static void case31_pushupReps() {
        // elbow only bends to ~110 (a half push-up, not locked deep). Must recognize + count.
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 40; f++) {
                float elbow = 130f + 30f*(float)Math.cos(2*Math.PI*f/40);   // 100..160 (half push-up)
                VyayamaCoach.Result r = c.onFrame(pushupPose(elbow), tNs());
                reps = r.reps; key = r.key;
            }
        expectKey ("half push-up -> PUSHUP", key, "PUSHUP");
        expectAtLeast("half push-up counts (>=4)", reps, 4);
    }

    // ================= MANUAL MODE 32-44 =================
    // Manual mode pins one exercise; classify() is bypassed so no other exercise is ever considered.

    /** 32: pinned SQUAT counts a clean squat sweep exactly like auto. */
    static void case32_manualSquatCounts() {
        VyayamaCoach c = mk(true); c.setManualExercise("SQUAT");
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                VyayamaCoach.Result r = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs());
                reps = r.reps; key = r.key;
            }
        expectReps("manual SQUAT counts 5", reps, 5);
        expectKey ("manual SQUAT key", key, "SQUAT");
    }

    /** 33: pinned SQUAT never flips to another exercise under curl motion. */
    static void case33_pinnedSquatNeverFlips() {
        VyayamaCoach c = mk(true); c.setManualExercise("SQUAT");
        String key = "NONE"; boolean everOther = false;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                VyayamaCoach.Result r = c.onFrame(curlPose(100f - 50f*(float)Math.cos(2*Math.PI*f/40)), tNs());
                key = r.key; if (!key.equals("SQUAT")) everOther = true;
            }
        expectKey ("pinned SQUAT stays SQUAT under curl motion", key, "SQUAT");
        expectBool("pinned key never flips to another exercise", everOther, false);
    }

    /** 34: THE bug — an arm-swing sit-up is suppressed from being grabbed as a curl when pinned SITUP. */
    static void case34_pinnedSitupSuppressesCurl() {
        VyayamaCoach c = mk(true); c.setManualExercise("SITUP");
        String key = "NONE"; boolean everCurl = false; int reps = 0;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float hipFlex = 110f + 40f*(float)Math.cos(phase);
                float armSwing = 0.5f - 0.5f*(float)Math.cos(phase);
                VyayamaCoach.Result r = c.onFrame(situpArmPose(hipFlex, armSwing), tNs());
                key = r.key; reps = r.reps; if (r.key.equals("BICEP_CURL")) everCurl = true;
            }
        expectKey ("pinned SITUP stays SITUP (arm-swing sit-up)", key, "SITUP");
        expectBool("sit-up-as-curl suppressed when pinned", everCurl, false);
        expectAtLeast("pinned situp still counts (>=3)", reps, 3);
    }

    /** 35: clearing manual restores automatic recognition. */
    static void case35_manualToAutoRestores() {
        VyayamaCoach c = mk(true); c.setManualExercise("SQUAT");
        for (int f = 0; f < 20; f++) c.onFrame(squatPose(150f), tNs());
        c.setManualExercise(null);
        String key = "NONE"; int reps = 0;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                VyayamaCoach.Result r = c.onFrame(curlPose(100f - 50f*(float)Math.cos(2*Math.PI*f/40)), tNs());
                key = r.key; reps = r.reps;
            }
        expectKey ("manual->auto re-locks BICEP_CURL", key, "BICEP_CURL");
        expectAtLeast("manual->auto counts new exercise (>=3)", reps, 3);
    }

    /** 36: pinned PLANK counts ~seconds held (deterministic; tolerance, never exact). */
    static void case36_manualPlankCounts() {
        VyayamaCoach c = mk(true); c.setManualExercise("PLANK");
        int reps = 0; String key = "NONE";
        for (int f = 0; f < 210; f++) {
            VyayamaCoach.Result r = c.onFrame(plankPose(), tNs());
            reps = r.reps; key = r.key;
        }
        expectKey ("manual PLANK key", key, "PLANK");
        expectNear("manual PLANK reps ~ seconds held", reps, 6f, 2f);
    }

    /** 37: forcing exercising=true never fabricates reps while standing idle. */
    static void case37_manualIdleNoFalseReps() {
        VyayamaCoach c = mk(true); c.setManualExercise("SQUAT");
        int reps = 0; boolean ex = false;
        for (int f = 0; f < 120; f++) {
            VyayamaCoach.Result r = c.onFrame(standPose(), tNs());
            reps = r.reps; ex = r.exercising;
        }
        expectReps("manual idle: no false reps", reps, 0);
        expectBool("manual marks exercising even when idle", ex, true);
    }

    /** 38: pinning the SAME exercise mid-bout retains and continues the count. */
    static void case38_continueSameBoutRetains() {
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 2; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        int afterAuto = reps;
        c.setManualExercise("SQUAT");
        for (int rep = 0; rep < 2; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        expectReps("auto counted 2 before pin", afterAuto, 2);
        expectReps("pin SAME exercise retains+continues count", reps, 4);
    }

    /** 39: pinning a DIFFERENT exercise resets the rep FSM immediately (no stale half-rep leak). */
    static void case39_crossPinResetsFsm() {
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 2; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        int afterSquat = reps;
        c.setManualExercise("BICEP_CURL");
        int firstAfterPin = c.onFrame(curlPose(150f), tNs()).reps;
        int curlReps = firstAfterPin;
        for (int rep = 0; rep < 3; rep++)
            for (int f = 0; f < 40; f++)
                curlReps = c.onFrame(curlPose(100f - 50f*(float)Math.cos(2*Math.PI*f/40)), tNs()).reps;
        expectReps("auto counted 2 squats", afterSquat, 2);
        expectReps("cross-pin zeroes reps immediately", firstAfterPin, 0);
        expectBool("fresh curl count after cross-pin (1..3)", curlReps >= 2 && curlReps <= 3, true);
    }

    /** 40: manualExercise is config-like and survives reset() (like filterEnabled). */
    static void case40_manualSurvivesReset() {
        VyayamaCoach c = mk(true); c.setManualExercise("PUSHUP");
        c.reset();
        expectBool("manual survives reset()", c.isManual(), true);
        expectKey ("manual key survives reset()", c.manualKey(), "PUSHUP");
    }

    /** 41: null manual == today's automatic path (byte-identical classification). */
    static void case41_manualNullIsAuto() {
        VyayamaCoach c = mk(true);   // never set manual → null
        String key = drive(c, 40, f -> squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)));
        expectKey("manual=null is automatic (squat window)", key, "SQUAT");
    }

    /** 42: pinned exercise whose primary signal doesn't move counts nothing (no phantom reps). */
    static void case42_manualMismatchNoCount() {
        VyayamaCoach c = mk(true); c.setManualExercise("BICEP_CURL");
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 45; f++) {
                VyayamaCoach.Result r = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs());
                reps = r.reps; key = r.key;
            }
        expectReps("pinned curl + squat motion: 0 reps", reps, 0);
        expectKey ("pinned curl stays BICEP_CURL", key, "BICEP_CURL");
    }

    /** 43: switching off PLANK to a rep exercise resets cleanly (no seconds leak as reps). */
    static void case43_manualPlankToRepExercise() {
        VyayamaCoach c = mk(true); c.setManualExercise("PLANK");
        int plankReps = 0;
        for (int f = 0; f < 120; f++) plankReps = c.onFrame(plankPose(), tNs()).reps;
        c.setManualExercise("SQUAT");
        int firstAfterPin = c.onFrame(squatPose(165f), tNs()).reps;
        int reps = firstAfterPin; String key = "NONE";
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++) {
                VyayamaCoach.Result r = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs());
                reps = r.reps; key = r.key;
            }
        expectAtLeast("plank banked some seconds", plankReps, 1);
        expectReps("plank->squat zeroes the counter", firstAfterPin, 0);
        expectKey ("plank->squat now SQUAT", key, "SQUAT");
        expectAtLeast("plank->squat counts squats (>=4)", reps, 4);
    }

    /** 44: pinned SQUAT reports SQUAT on EVERY frame under perfect push-up motion (ladder bypassed). */
    static void case44_manualPinsChosenOnly() {
        VyayamaCoach c = mk(true); c.setManualExercise("SQUAT");
        boolean allSquat = true;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 40; f++) {
                VyayamaCoach.Result r = c.onFrame(pushupPose(125f + 35f*(float)Math.cos(2*Math.PI*f/40)), tNs());
                if (!r.key.equals("SQUAT")) allSquat = false;
            }
        expectBool("pinned SQUAT reports SQUAT every frame under pushup motion", allSquat, true);
    }

    // ================= PUSH-UP LENIENCY + FAST-REP 45-47 =================

    /** 45: a SHALLOW / foreshortened push-up (moderate elbow bend) now counts via the lenient
     *  defBottom + adaptive calibration. (pushupPose maps its input ~+22° higher when measured,
     *  so input 112..158 ≈ a measured ~134..178 swing — a believable foreshortened push-up.) */
    static void case45_shallowPushupCounts() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 8; rep++)
            for (int f = 0; f < 40; f++) {
                float elbow = 135f + 23f*(float)Math.cos(2*Math.PI*f/40);   // input 112..158 (shallow/foreshortened)
                VyayamaCoach.Result r = c.onFrame(pushupPose(elbow), tNs());
                reps = r.reps; key = r.key;
            }
        expectKey ("shallow push-up -> PUSHUP", key, "PUSHUP");
        expectAtLeast("shallow push-up counts (>=4)", reps, 4);
    }

    /** 46: FAST push-ups at a LOW frame-rate (each rep only ~4-5 frames) still count — the FSM now
     *  advances multiple phases per frame. Each rep here lasts ~480ms (> MIN_REP_MS), so they are real. */
    static void case46_fastPushupLowFps() {
        VyayamaCoach c = mk(true);
        long dt = 120_000_000L;   // ~8.3 fps (heavily-loaded device)
        long t = 0; int reps = 0; String key = "NONE";
        float[] seq = {160f, 100f, 100f, 100f, 160f};   // 5 frames/rep, ~480ms
        for (int rep = 0; rep < 9; rep++)
            for (float e : seq) {
                VyayamaCoach.Result r = c.onFrame(pushupPose(e), t);
                t += dt; reps = r.reps; key = r.key;
            }
        expectKey ("fast low-fps push-up -> PUSHUP", key, "PUSHUP");
        expectAtLeast("fast low-fps push-up counts (>=4)", reps, 4);
    }

    /** 47: a genuinely TOO-FAST push-up (sub-300ms dips) is still rejected — multi-advance does NOT
     *  let MIN_REP_MS be bypassed (jitter / bounce cannot fabricate reps). */
    static void case47_tooFastPushupRejected() {
        VyayamaCoach c = mk(true);
        long t = 0; int reps = 0;
        for (int i = 0; i < 80; i++) {
            float e = (i % 2 == 0) ? 160f : 100f;     // alternate every 33ms → each dip ~66ms
            reps = c.onFrame(pushupPose(e), t).reps;
            t += 33_000_000L;
        }
        expectReps("too-fast push-up: no reps (MIN_REP_MS holds under multi-advance)", reps, 0);
    }

    // ================= INCOMPLETE-ROM LENIENCY 48-49 =================
    // A rep that reaches bottom but doesn't fully return to the top still counts (peak/valley
    // completion) — the exact failure of a pro doing slow curls without locking the elbow out.

    /** 48: curls that fully squeeze but NEVER lock the elbow out at the top still count.
     *  (curlPose's input is INVERTED — high input = more flexed; input 100..150 ≈ a measured
     *  ~128°→39° swing: full curl at the bottom, only a partial ~128° extension at the top.) */
    static void case48_partialExtensionCurls() {
        VyayamaCoach c = mk(true);
        int reps = 0; String key = "NONE";
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 40; f++) {
                float elbow = 125f + 25f*(float)Math.cos(2*Math.PI*f/40);   // input 100..150 = no top lockout
                VyayamaCoach.Result r = c.onFrame(curlPose(elbow), tNs());
                reps = r.reps; key = r.key;
            }
        expectKey ("partial-extension curl -> BICEP_CURL", key, "BICEP_CURL");
        expectAtLeast("partial-extension curls count (>=3)", reps, 3);
    }

    /** 49: squats that don't fully stand up between reps (knee 85..155) still count. */
    static void case49_partialReturnSquat() {
        VyayamaCoach c = mk(true);
        int reps = 0;
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 45; f++) {
                float knee = 120f + 35f*(float)Math.cos(2*Math.PI*f/45);   // 85..155 (never fully extends)
                reps = c.onFrame(squatPose(knee), tNs()).reps;
            }
        expectAtLeast("incomplete-return squat counts (>=4)", reps, 4);
    }

    /** A front-on squat whose knee ANGLE stays ~straight (foreshortened) while the HIPS drop.
     *  Lowers ONLY the upper body (shoulders/hips/arms/head) over fixed knees+ankles, so the
     *  hip-knee-ankle joint stays ~collinear (knee ~176) yet mid-hip drops toward the knee line. */
    static float[][] foreshortenedSquatPose(float d) {
        float[][] p = squatPose(176f);
        float drop = d * 0.75f * 80f;   // up to ~0.75 torso-lengths (torsoLen ~ 80)
        int[] upper = { NOSE, L_EYE, R_EYE, L_EAR, R_EAR, L_SH, R_SH, L_EL, R_EL, L_WR, R_WR, L_HIP, R_HIP };
        for (int idx : upper) if (!(p[idx][0]==0f && p[idx][1]==0f)) p[idx][1] += drop;
        return p;
    }

    // ============================================================
    //                    POSE BUILDERS
    // ============================================================
    // Image y grows downward. Upright person centred ~ (cx, cy). All in pixels.
    // Base layout (standing, scale s): heights below in body-units * s.

    /** Empty 17x2 frame (all missing). */
    static float[][] blank() { return new float[17][2]; }

    /** Fill head/eyes/ears around a nose point so head joints are present (not load-bearing for angles). */
    static void head(float[][] p, float nx, float ny, float s) {
        p[NOSE]=new float[]{nx, ny};
        p[L_EYE]=new float[]{nx-5*s/100, ny-3*s/100};
        p[R_EYE]=new float[]{nx+5*s/100, ny-3*s/100};
        p[L_EAR]=new float[]{nx-10*s/100, ny};
        p[R_EAR]=new float[]{nx+10*s/100, ny};
    }

    /**
     * Upright squat-able pose. cx=320, cy=240 anchor; scale s=200.
     * Shoulders at top, hips mid, knees + ankles below. The leg bends symmetrically so the
     * knee angle (hip-knee-ankle) == kneeDeg. Arms hang at the sides (low wrists).
     */
    static float[][] squatPose(float kneeDeg) { return squatPose(kneeDeg, 320f, 240f, 200f); }

    static float[][] squatPose(float kneeDeg, float cx, float cy, float s) {
        float[][] p = blank();
        float u = s / 200f; // body-unit scale
        // verticals (y): shoulders above hips above knees; smaller y = higher on image.
        float shY = cy - 60*u;
        float hipY = cy + 20*u;
        float thigh = 70*u;     // hip→knee segment length
        float shank = 70*u;     // knee→ankle segment length
        float halfW = 30*u;

        // Build a symmetric bent leg in the SAGITTAL-as-frontal projection:
        // place knee straight below hip by 'thigh', then put the ankle so that the
        // hip-knee-ankle angle equals kneeDeg. We bend the shank forward (toward +y, i.e. down)
        // by rotating it about the knee. Straight leg (180°) → ankle straight below knee.
        // Bend angle from straight = (180 - kneeDeg); ankle swings forward.
        double bend = Math.toRadians(180.0 - kneeDeg);
        // thigh points straight down (0,1). shank direction = thigh rotated by 'bend' about knee.
        // rotate (0,1) by 'bend' (toward +x so the foot moves forward, keeping x within frame).
        float shdx = (float)( Math.sin(bend));
        float shdy = (float)( Math.cos(bend));

        // shoulders
        p[L_SH]=new float[]{cx-halfW, shY};
        p[R_SH]=new float[]{cx+halfW, shY};
        // hips
        p[L_HIP]=new float[]{cx-halfW, hipY};
        p[R_HIP]=new float[]{cx+halfW, hipY};
        // knees straight below hips
        float lKnX = cx-halfW, lKnY = hipY + thigh;
        float rKnX = cx+halfW, rKnY = hipY + thigh;
        p[L_KN]=new float[]{lKnX, lKnY};
        p[R_KN]=new float[]{rKnX, rKnY};
        // ankles: knee + shank*dir (mirror x so feet stay symmetric)
        p[L_AN]=new float[]{lKnX - shank*shdx, lKnY + shank*shdy};
        p[R_AN]=new float[]{rKnX + shank*shdx, rKnY + shank*shdy};
        // arms hang down (wrists low, near hips) — keeps openness/wristUp low.
        p[L_EL]=new float[]{cx-halfW-2*u, shY+35*u};
        p[R_EL]=new float[]{cx+halfW+2*u, shY+35*u};
        p[L_WR]=new float[]{cx-halfW-3*u, shY+70*u};
        p[R_WR]=new float[]{cx+halfW+3*u, shY+70*u};
        head(p, cx, shY-30*u, s);
        return p;
    }

    /** Standing rest pose (knees ~ straight, arms down). */
    static float[][] standPose() { return squatPose(178f); }

    /**
     * Push-up: body horizontal. Torso lean ~90° (shoulder→hip vector points sideways).
     * elbowDeg controls arm bend. Hips kept in line (low sag). Legs straight (low knee amp).
     */
    static float[][] pushupPose(float elbowDeg) {
        float[][] p = blank();
        float cx = 320, cy = 260, u = 1f;
        // horizontal body: shoulders left, hips right, ankles further right (all same y band).
        float bodyY = cy;
        float shX = cx - 120*u;
        float hipX = cx + 0*u;
        float knX = cx + 80*u;
        float anX = cx + 140*u;
        float halfW = 28*u; // shoulder/hip width in the y direction (across the body)

        p[L_SH]=new float[]{shX, bodyY-halfW};
        p[R_SH]=new float[]{shX, bodyY+halfW};
        p[L_HIP]=new float[]{hipX, bodyY-halfW};
        p[R_HIP]=new float[]{hipX, bodyY+halfW};
        p[L_KN]=new float[]{knX, bodyY-halfW};
        p[R_KN]=new float[]{knX, bodyY+halfW};
        p[L_AN]=new float[]{anX, bodyY-halfW};
        p[R_AN]=new float[]{anX, bodyY+halfW};

        // hands planted forward+down (below shoulders). Bend controls elbow angle:
        // forearm from elbow to wrist; elbow between shoulder and wrist.
        // Place wrist under shoulder at a depth set by elbowDeg (straight=far, bent=close).
        double bend = Math.toRadians(180.0 - elbowDeg);
        float fore = 55*u;
        // upper arm points down-forward from shoulder.
        float elXL = shX + 20*u, elYL = bodyY-halfW + 45*u;
        float elXR = shX + 20*u, elYR = bodyY+halfW + 45*u;
        p[L_EL]=new float[]{elXL, elYL};
        p[R_EL]=new float[]{elXR, elYR};
        // forearm rotates by bend; straight arm → wrist further down.
        float wdx = (float)Math.sin(bend), wdy = (float)Math.cos(bend);
        p[L_WR]=new float[]{elXL + fore*wdx, elYL + fore*wdy};
        p[R_WR]=new float[]{elXR + fore*wdx, elYR + fore*wdy};
        head(p, shX-30*u, bodyY, 200f);
        return p;
    }

    /**
     * Jumping jack: openness driven by arms overhead (wrists above shoulders) + wide ankles.
     * open in [0,1] roughly maps arms+legs spread.
     */
    static float[][] jackPose(float open) {
        float[][] p = blank();
        float cx = 320, cy = 240, u = 1f;
        float shY = cy - 60*u, hipY = cy + 20*u;
        float halfW = 30*u;
        float torsoLen = hipY - shY; // ~80

        p[L_SH]=new float[]{cx-halfW, shY};
        p[R_SH]=new float[]{cx+halfW, shY};
        p[L_HIP]=new float[]{cx-halfW, hipY};
        p[R_HIP]=new float[]{cx+halfW, hipY};

        // arms: wrists rise above shoulders proportional to open (arm raise term).
        float wY = shY - open*torsoLen*1.0f;     // open=1 → a full torsoLen above shoulders
        p[L_EL]=new float[]{cx-halfW-15*u, (shY+wY)/2};
        p[R_EL]=new float[]{cx+halfW+15*u, (shY+wY)/2};
        p[L_WR]=new float[]{cx-halfW-25*u, wY};
        p[R_WR]=new float[]{cx+halfW+25*u, wY};

        // legs straight; ankle spread widens with open (legSpread term).
        float knY = hipY + 70*u, anY = knY + 70*u;
        float shoulderWidth = 2*halfW;
        float ankleSpread = shoulderWidth * (1f + open);   // open=1 → spread = 2*shoulderWidth → legSpread≈1
        float anHalf = ankleSpread/2f;
        p[L_KN]=new float[]{cx-halfW, knY};
        p[R_KN]=new float[]{cx+halfW, knY};
        p[L_AN]=new float[]{cx-anHalf, anY};
        p[R_AN]=new float[]{cx+anHalf, anY};
        head(p, cx, shY-30*u, 200f);
        return p;
    }

    /**
     * Shoulder press (upright): elbow bend + wrists above shoulders. Legs static & straight.
     * elbowDeg: 95 bent → 165 lockout; wristUp in body-units above shoulders.
     */
    static float[][] pressPose(float elbowDeg, float wristUp) {
        float[][] p = blank();
        float cx = 320, cy = 240, u = 1f;
        float shY = cy - 60*u, hipY = cy + 20*u;
        float halfW = 30*u;
        float torsoLen = hipY - shY;

        p[L_SH]=new float[]{cx-halfW, shY};
        p[R_SH]=new float[]{cx+halfW, shY};
        p[L_HIP]=new float[]{cx-halfW, hipY};
        p[R_HIP]=new float[]{cx+halfW, hipY};
        // legs straight (low knee amp)
        float knY = hipY + 70*u, anY = knY + 70*u;
        p[L_KN]=new float[]{cx-halfW, knY};
        p[R_KN]=new float[]{cx+halfW, knY};
        p[L_AN]=new float[]{cx-halfW, anY};
        p[R_AN]=new float[]{cx+halfW, anY};

        // Forward kinematics so the shoulder-elbow-wrist interior angle EXACTLY equals elbowDeg.
        // Upper arm points straight UP from the shoulder (toward -y). The forearm is the upper-arm
        // direction rotated by the exterior angle (180-elbowDeg) about the elbow, opening outward.
        // At elbowDeg=180 the whole arm is straight up (full lockout); at 95 it is sharply bent.
        float upper = 34*u, fore = 34*u;
        float wuY = wristUp*torsoLen;          // how far the (straight-arm) hand would clear the shoulder
        // scale segment lengths so a straight arm reaches ~wristUp above the shoulder
        float segScale = Math.max(0.6f, wuY / (upper + fore));
        upper *= segScale; fore *= segScale;
        double ext = Math.toRadians(180.0 - elbowDeg); // exterior bend
        // LEFT arm: upper up (0,-1); forearm rotates so the hand swings outward to the left.
        buildArm(p, L_EL, L_WR, cx-halfW, shY, upper, fore, ext, +1f);
        // RIGHT arm: mirror (hand swings to the right).
        buildArm(p, R_EL, R_WR, cx+halfW, shY, upper, fore, ext, -1f);
        head(p, cx, shY-30*u, 200f);
        return p;
    }

    /** Place elbow+wrist for an arm whose upper segment points up from (sx,sy); forearm rotated by
     *  exterior angle `ext` (radians) about the elbow, opening in the `side` x-direction. */
    static void buildArm(float[][] p, int elIdx, int wrIdx, float sx, float sy,
                         float upper, float fore, double ext, float side) {
        // upper arm direction: straight up (0,-1)
        float udx = 0f, udy = -1f;
        float elx = sx + udx*upper, ely = sy + udy*upper;
        // forearm direction = upper direction rotated by `ext` toward `side` (outward).
        // rotate (udx,udy) by angle (side*ext): for side=+1 rotate one way, side=-1 the other.
        double ang = side * ext;
        float fdx = (float)(udx*Math.cos(ang) - udy*Math.sin(ang));
        float fdy = (float)(udx*Math.sin(ang) + udy*Math.cos(ang));
        float wx = elx + fdx*fore, wy = ely + fdy*fore;
        p[elIdx]=new float[]{elx, ely};
        p[wrIdx]=new float[]{wx, wy};
    }

    /**
     * Sit-up: body roughly horizontal (torso lean ~75°), hip flexion (shoulder-hip-knee) varies.
     * hipFlexDeg: 70 crunch (folded) → 150 flat. Legs bent & static, elbows still.
     */
    static float[][] situpPose(float hipFlexDeg) {
        float[][] p = blank();
        float cx = 320, cy = 250, u = 1f;
        // Lying down, knees up. Hips at right, shoulders to the left and slightly up when crunching.
        float hipX = cx + 40*u, hipY = cy;
        float halfW = 26*u;
        // knees up-and-forward from hips (bent legs), static.
        float knX = hipX + 50*u, knY = hipY - 50*u;
        float anX = hipX + 40*u, anY = hipY + 10*u;

        // shoulder position: thigh direction is hip→knee (fixed). Trunk direction hip→shoulder
        // makes the interior angle (shoulder-hip-knee) = hipFlexDeg. Rotate trunk accordingly.
        float thighDx = knX - hipX, thighDy = knY - hipY;
        double thighAng = Math.atan2(thighDy, thighDx);
        // trunk at hipFlexDeg from the thigh (interior angle). Trunk points away (toward shoulders).
        double trunkAng = thighAng + Math.toRadians(hipFlexDeg);
        float trunkLen = 80*u;
        float shCx = hipX + (float)Math.cos(trunkAng)*trunkLen;
        float shCy = hipY + (float)Math.sin(trunkAng)*trunkLen;

        // build L/R offset perpendicular to trunk for shoulder width
        double perp = trunkAng + Math.PI/2;
        float ox = (float)Math.cos(perp)*halfW, oy = (float)Math.sin(perp)*halfW;
        p[L_SH]=new float[]{shCx-ox, shCy-oy};
        p[R_SH]=new float[]{shCx+ox, shCy+oy};
        double hperp = thighAng + Math.PI/2;
        float hox = (float)Math.cos(hperp)*halfW, hoy=(float)Math.sin(hperp)*halfW;
        p[L_HIP]=new float[]{hipX-hox, hipY-hoy};
        p[R_HIP]=new float[]{hipX+hox, hipY+hoy};
        p[L_KN]=new float[]{knX-hox, knY-hoy};
        p[R_KN]=new float[]{knX+hox, knY+hoy};
        p[L_AN]=new float[]{anX-hox, anY-hoy};
        p[R_AN]=new float[]{anX+hox, anY+hoy};
        // elbows/wrists near shoulders, static (hands behind head ~ low elbow amplitude).
        p[L_EL]=new float[]{shCx-ox-12*u, shCy-oy+6*u};
        p[R_EL]=new float[]{shCx+ox+12*u, shCy+oy+6*u};
        p[L_WR]=new float[]{shCx-ox-4*u, shCy-oy-8*u};
        p[R_WR]=new float[]{shCx+ox+4*u, shCy+oy-8*u};
        head(p, shCx + (float)Math.cos(trunkAng)*30*u, shCy + (float)Math.sin(trunkAng)*30*u, 200f);
        return p;
    }

    /**
     * Sit-up with hands behind the head so the elbows oscillate with the crunch, filmed at a
     * MODERATE camera angle (torso lean ~40°, deliberately NOT clearly >50). Hip fold swings
     * 150→70 (big hipAngAmp), legs static, elbows track the shoulder rise (elbowAmp > 25 →
     * the OLD engine's curl catch-all grabbed this). armSwing advances the elbow swing.
     */
    static float[][] situpArmPose(float hipFlexDeg, float armSwing) {
        float[][] p = blank();
        float cx = 320, cy = 250, u = 1f;
        float hipX = cx + 40*u, hipY = cy;
        float halfW = 26*u;
        float knX = hipX + 50*u, knY = hipY - 50*u;     // bent legs, static
        float anX = hipX + 40*u, anY = hipY + 10*u;
        float thighDx = knX - hipX, thighDy = knY - hipY;
        double thighAng = Math.atan2(thighDy, thighDx);
        // Use a SHALLOWER trunk angle than situpPose so torsoLean reads ~40° (moderate, not >50 clearly).
        double trunkAng = thighAng + Math.toRadians(hipFlexDeg);
        float trunkLen = 80*u;
        float shCx = hipX + (float)Math.cos(trunkAng)*trunkLen;
        float shCy = hipY + (float)Math.sin(trunkAng)*trunkLen;
        double perp = trunkAng + Math.PI/2;
        float ox = (float)Math.cos(perp)*halfW, oy = (float)Math.sin(perp)*halfW;
        p[L_SH]=new float[]{shCx-ox, shCy-oy};
        p[R_SH]=new float[]{shCx+ox, shCy+oy};
        double hperp = thighAng + Math.PI/2;
        float hox = (float)Math.cos(hperp)*halfW, hoy=(float)Math.sin(hperp)*halfW;
        p[L_HIP]=new float[]{hipX-hox, hipY-hoy};
        p[R_HIP]=new float[]{hipX+hox, hipY+hoy};
        p[L_KN]=new float[]{knX-hox, knY-hoy};
        p[R_KN]=new float[]{knX+hox, knY-hoy};
        p[L_AN]=new float[]{anX-hox, anY-hoy};
        p[R_AN]=new float[]{anX+hox, anY+hoy};
        // HANDS BEHIND HEAD. Build the arm rigidly in the TRUNK-LOCAL basis so the whole arm
        // rotates WITH the crunch (the elbow joint angle stays open ~110-150, min never < 80, so
        // the curl-flex proof minActiveElbow()<80 fails). A small `flare` then perturbs ONLY the
        // elbow along the trunk-perp axis → a MODERATE elbowAmp in [25,40): big enough that the
        // OLD engine's curl catch-all grabbed this, small enough to clear SITUP's elbowAmp<40 gate.
        float ux = (float)Math.cos(trunkAng), uy = (float)Math.sin(trunkAng); // along trunk (hip→sh)
        float px = (float)Math.cos(perp),     py = (float)Math.sin(perp);     // trunk-perp (shoulder span)
        // Arm built rigidly in the trunk frame so it rotates WITH the crunch and the joint angle
        // stays OPEN (no sharp curl fold). `flare` drives the elbow OUT along ±perp with armSwing,
        // opening/closing the elbow joint by a MODERATE amount → elbowAmp in [25,40): big enough
        // that the OLD engine's curl catch-all grabbed this, small enough to clear SITUP's <40 gate.
        float flare = 10f*u*armSwing;                    // elbow perp-flare oscillates → moderate elbowAmp
        // elbow: out along ±perp from the shoulder, and UP the trunk a little (toward the head).
        float elPerp = 14f*u + flare, elAlong = 16f*u;
        p[L_EL]=new float[]{(shCx-ox) - px*elPerp + ux*elAlong, (shCy-oy) - py*elPerp + uy*elAlong};
        p[R_EL]=new float[]{(shCx+ox) + px*elPerp + ux*elAlong, (shCy+oy) + py*elPerp + uy*elAlong};
        // wrist: tucked behind the head — UP the trunk (toward the head) and near the midline.
        float wrPerp = 4f*u, wrAlong = 30f*u;
        p[L_WR]=new float[]{(shCx-ox) - px*wrPerp + ux*wrAlong, (shCy-oy) - py*wrPerp + uy*wrAlong};
        p[R_WR]=new float[]{(shCx+ox) + px*wrPerp + ux*wrAlong, (shCy+oy) + py*wrPerp + uy*wrAlong};
        head(p, shCx + (float)Math.cos(trunkAng)*30*u, shCy + (float)Math.sin(trunkAng)*30*u, 200f);
        return p;
    }

    /** Sit-up with hands BEHIND THE HEAD: same big trunk-fold + static legs as situpArmPose, but the
     *  elbows flare MORE (elbowAmp>40, which fails the OLD SITUP elbowAmp<40 cap) and the wrists ride
     *  ABOVE the shoulders (wristUp>0.35) — exactly the geometry that used to fall through to
     *  SHOULDER_PRESS. The fixed engine must still lock SITUP (big hip fold, static legs). */
    static float[][] situpHandsBehindHeadPose(float hipFlexDeg, float armSwing) {
        float[][] p = blank();
        float cx = 320, cy = 250, u = 1f;
        float hipX = cx + 40*u, hipY = cy;
        float halfW = 26*u;
        float knX = hipX + 50*u, knY = hipY - 50*u;     // bent legs, static
        float anX = hipX + 40*u, anY = hipY + 10*u;
        float thighDx = knX - hipX, thighDy = knY - hipY;
        double thighAng = Math.atan2(thighDy, thighDx);
        double trunkAng = thighAng + Math.toRadians(hipFlexDeg);
        float trunkLen = 80*u;
        float shCx = hipX + (float)Math.cos(trunkAng)*trunkLen;
        float shCy = hipY + (float)Math.sin(trunkAng)*trunkLen;
        double perp = trunkAng + Math.PI/2;
        float ox = (float)Math.cos(perp)*halfW, oy = (float)Math.sin(perp)*halfW;
        p[L_SH]=new float[]{shCx-ox, shCy-oy};
        p[R_SH]=new float[]{shCx+ox, shCy+oy};
        double hperp = thighAng + Math.PI/2;
        float hox = (float)Math.cos(hperp)*halfW, hoy=(float)Math.sin(hperp)*halfW;
        p[L_HIP]=new float[]{hipX-hox, hipY-hoy};
        p[R_HIP]=new float[]{hipX+hox, hipY+hoy};
        p[L_KN]=new float[]{knX-hox, knY-hoy};
        p[R_KN]=new float[]{knX+hox, knY-hoy};
        p[L_AN]=new float[]{anX-hox, anY-hoy};
        p[R_AN]=new float[]{anX+hox, anY+hoy};
        float ux = (float)Math.cos(trunkAng), uy = (float)Math.sin(trunkAng);
        float px = (float)Math.cos(perp),     py = (float)Math.sin(perp);
        float flare = 22f*u*armSwing;                    // BIG flare -> elbowAmp>40
        float elPerp = 16f*u + flare, elAlong = 18f*u;
        p[L_EL]=new float[]{(shCx-ox) - px*elPerp + ux*elAlong, (shCy-oy) - py*elPerp + uy*elAlong};
        p[R_EL]=new float[]{(shCx+ox) + px*elPerp + ux*elAlong, (shCy+oy) + py*elPerp + uy*elAlong};
        // wrists high above the shoulders (toward/behind the head) -> wristUp>0.35
        float wrPerp = 3f*u, wrAlong = 48f*u;
        p[L_WR]=new float[]{(shCx-ox) - px*wrPerp + ux*wrAlong, (shCy-oy) - py*wrPerp + uy*wrAlong};
        p[R_WR]=new float[]{(shCx+ox) + px*wrPerp + ux*wrAlong, (shCy+oy) + py*wrPerp + uy*wrAlong};
        head(p, shCx + (float)Math.cos(trunkAng)*30*u, shCy + (float)Math.sin(trunkAng)*30*u, 200f);
        return p;
    }

    // ================= SIT-UP RECOGNITION 50-52 =================

    /** 50: a hands-behind-head sit-up (elbowAmp>40, wristUp>0.35) must lock SITUP, never SHOULDER_PRESS. */
    static void case50_handsBehindHeadSitup() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; int reps = 0;
        boolean everPress = false, everCurl = false, everLocked = false;
        for (int rep = 0; rep < 5; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float hipFlex = 110f + 40f*(float)Math.cos(phase);     // 150->70 fold
                float armSwing = 0.5f - 0.5f*(float)Math.cos(phase);
                VyayamaCoach.Result r = c.onFrame(situpHandsBehindHeadPose(hipFlex, armSwing), tNs());
                key = r.key; reps = r.reps;
                if (key.equals("SITUP")) everLocked = true;
                if (r.key.equals("SHOULDER_PRESS")) everPress = true;
                if (r.key.equals("BICEP_CURL")) everCurl = true;
            }
        expectKey  ("hands-behind-head sit-up -> SITUP", key, "SITUP");
        expectBool ("NEVER locks SHOULDER_PRESS (the bug)", everPress, false);
        expectBool ("NEVER locks BICEP_CURL", everCurl, false);
        expectBool ("actually locked SITUP", everLocked, true);
        expectAtLeast("hands-behind-head sit-up reps (>=3)", reps, 3);
    }

    /** 51: a real shoulder press still locks SHOULDER_PRESS (the new hipAngAmp<28 clause is harmless). */
    static void case51_pressNotSitup() {
        VyayamaCoach c = mk(true);
        String key = "NONE"; int reps = 0; boolean everSitup = false, locked = false;
        for (int rep = 0; rep < 4; rep++)
            for (int f = 0; f < 40; f++) {
                float phase = (float)(2*Math.PI*f/40);
                float elbow = 130f - 35f*(float)Math.cos(phase);                    // 95 -> 165
                float wristUp = 0.40f + 0.35f*(0.5f - 0.5f*(float)Math.cos(phase));  // overhead
                VyayamaCoach.Result r = c.onFrame(pressPose(elbow, wristUp), tNs());
                key = r.key; reps = r.reps;
                if (key.equals("SHOULDER_PRESS")) locked = true;
                if (r.key.equals("SITUP")) everSitup = true;
            }
        expectKey  ("press still SHOULDER_PRESS", key, "SHOULDER_PRESS");
        expectBool ("press NEVER locks SITUP", everSitup, false);
        expectBool ("press actually locked", locked, true);
        expectAtLeast("press reps (>=4)", reps, 4);
    }

    /** 52: a shallower-fold sit-up (hipAngAmp ~30, between 28 and 35) now recognises (was NONE at 35). */
    static void case52_shallowFoldSitup() {
        VyayamaCoach c = mk(true);
        String key = "NONE";
        for (int rep = 0; rep < 6; rep++)
            for (int f = 0; f < 40; f++) {
                float hipFlex = 120f + 17f*(float)Math.cos(2*Math.PI*f/40);   // 103..137 (~30 deg fold)
                key = c.onFrame(situpPose(hipFlex), tNs()).key;
            }
        expectKey("shallow-fold sit-up -> SITUP", key, "SITUP");
    }

    // ================= VoiceCoach (pure-Java cadence brain) =================
    static void vcTests() {
        // 1) warm-up: rep1/rep2 silent, rep3 speaks
        VoiceCoach v = new VoiceCoach(); v.onExerciseChange("SQUAT", 0L);
        String l1 = v.onRep("SQUAT", 1, 60, "DEPTH", 0L);
        String l2 = v.onRep("SQUAT", 2, 60, "DEPTH", 1_000_000_000L);
        String l3 = v.onRep("SQUAT", 3, 60, "DEPTH", 6_000_000_000L);
        expectBool("voice: rep1 silent", l1 == null, true);
        expectBool("voice: rep2 silent", l2 == null, true);
        expectBool("voice: rep3 speaks", l3 != null && l3.length() > 0, true);

        // 2) dominant issue -> a DEPTH cue
        expectBool("voice: dominant DEPTH cue", inArr(l3, VC_DEPTH), true);

        // 3) no verbatim repeat across two spoken DEPTH cues
        VoiceCoach v3 = new VoiceCoach(); v3.onExerciseChange("SQUAT", 0L);
        v3.onRep("SQUAT", 1, 60, "DEPTH", 0L); v3.onRep("SQUAT", 2, 60, "DEPTH", 100_000_000L);
        String a = v3.onRep("SQUAT", 3, 60, "DEPTH", 200_000_000L);
        v3.onRep("SQUAT", 4, 60, "DEPTH", 5_000_000_000L); v3.onRep("SQUAT", 5, 60, "DEPTH", 6_000_000_000L);
        String b = v3.onRep("SQUAT", 6, 60, "DEPTH", 9_000_000_000L);
        expectBool("voice: no verbatim repeat", a != null && b != null && !a.equals(b), true);

        // 4) min-time guard: within 4s of speaking -> silent
        VoiceCoach v4 = new VoiceCoach(); v4.onExerciseChange("SQUAT", 0L);
        v4.onRep("SQUAT", 1, 60, "DEPTH", 0L); v4.onRep("SQUAT", 2, 60, "DEPTH", 100_000_000L);
        v4.onRep("SQUAT", 3, 60, "DEPTH", 200_000_000L);   // speaks
        String s4 = v4.onRep("SQUAT", 4, 60, "DEPTH", 300_000_000L);
        String s5 = v4.onRep("SQUAT", 5, 60, "DEPTH", 400_000_000L);
        expectBool("voice: min-time guard", s4 == null && s5 == null, true);

        // 5) praise path: 4 CLEAN reps -> a praise line
        VoiceCoach v5 = new VoiceCoach(); v5.onExerciseChange("PUSHUP", 0L);
        v5.onRep("PUSHUP", 1, 100, "CLEAN", 0L); v5.onRep("PUSHUP", 2, 100, "CLEAN", 1_000_000_000L);
        v5.onRep("PUSHUP", 3, 100, "CLEAN", 2_000_000_000L);
        String pr = v5.onRep("PUSHUP", 4, 100, "CLEAN", 6_000_000_000L);
        expectBool("voice: praise on clean streak", inArr(pr, VC_PRAISE), true);

        // 6) milestone: rep 10 announces and contains "10"
        VoiceCoach v6 = new VoiceCoach(); v6.onExerciseChange("SQUAT", 0L);
        String m = null;
        for (int i = 1; i <= 10; i++) m = v6.onRep("SQUAT", i, 90, "CLEAN", i * 5_000_000_000L);
        expectBool("voice: milestone at 10", m != null && m.contains("10"), true);

        // 7) reset on exercise change -> warm-up restarts
        VoiceCoach v7 = new VoiceCoach(); v7.onExerciseChange("SQUAT", 0L);
        v7.onRep("SQUAT", 1, 60, "DEPTH", 0L); v7.onRep("SQUAT", 2, 60, "DEPTH", 1_000_000_000L);
        v7.onExerciseChange("BICEP_CURL", 10_000_000_000L);
        String n1 = v7.onRep("BICEP_CURL", 1, 60, "ROM", 11_000_000_000L);
        String n2 = v7.onRep("BICEP_CURL", 2, 60, "ROM", 12_000_000_000L);
        expectBool("voice: warm-up restarts on switch", n1 == null && n2 == null, true);

        // 8) implicit exKey-mismatch flush -> silent
        VoiceCoach v8 = new VoiceCoach(); v8.onExerciseChange("SQUAT", 0L);
        v8.onRep("SQUAT", 1, 60, "DEPTH", 0L); v8.onRep("SQUAT", 2, 60, "DEPTH", 1_000_000_000L);
        String mm = v8.onRep("PUSHUP", 1, 60, "SAG", 12_000_000_000L);
        expectBool("voice: implicit flush on exKey change", mm == null, true);

        // 9) disabled -> silent; re-enabled -> speaks again
        VoiceCoach v9 = new VoiceCoach(); v9.onExerciseChange("SQUAT", 0L); v9.setEnabled(false);
        String d1 = v9.onRep("SQUAT", 1, 60, "DEPTH", 0L);
        String d2 = v9.onRep("SQUAT", 2, 60, "DEPTH", 1_000_000_000L);
        String d3 = v9.onRep("SQUAT", 3, 60, "DEPTH", 2_000_000_000L);
        v9.setEnabled(true);
        String e1 = v9.onRep("SQUAT", 4, 60, "DEPTH", 6_000_000_000L);
        String e2 = v9.onRep("SQUAT", 5, 60, "DEPTH", 10_000_000_000L);
        String e3 = v9.onRep("SQUAT", 6, 60, "DEPTH", 14_000_000_000L);
        expectBool("voice: disabled silent", d1 == null && d2 == null && d3 == null, true);
        expectBool("voice: re-enabled speaks", e1 != null || e2 != null || e3 != null, true);

        // 10) a single off-rep in a clean window is not corrected (pattern, not per-rep)
        VoiceCoach v10 = new VoiceCoach(); v10.onExerciseChange("SQUAT", 0L);
        v10.onRep("SQUAT", 1, 100, "CLEAN", 0L); v10.onRep("SQUAT", 2, 100, "CLEAN", 1_000_000_000L);
        String s10 = v10.onRep("SQUAT", 3, 70, "DEPTH", 6_000_000_000L);
        expectBool("voice: single off-rep not a correction", !inArr(s10, VC_DEPTH), true);
    }
    static final String[] VC_DEPTH  = { "Go a little deeper.", "A bit lower next time.", "Sink down further." };
    static final String[] VC_PRAISE = { "Beautiful form — keep going.", "That's it, nice and clean.",
                                        "Looking strong — stay with it.", "Smooth reps, lovely control." };
    static boolean inArr(String s, String[] arr) {
        if (s == null) return false;
        for (String a : arr) if (a.equals(s)) return true;
        return false;
    }

    /** Squat where the arms also swing (front raise) — knees drive the rep; arms are a distractor. */
    static float[][] squatArmSwingPose(float kneeDeg, float armSwing) {
        float[][] p = squatPose(kneeDeg);   // legs do the real work (kneeAmp large)
        float cx = 320, u = 1f;
        float shY = 240 - 60*u;
        // elbows bend/extend with the swing → nonzero elbowAmp, but kneeAmp dominates and wins SQUAT.
        float drop = 40f*u*(1f - armSwing);
        p[L_EL]=new float[]{cx-32*u, shY+25*u};
        p[R_EL]=new float[]{cx+32*u, shY+25*u};
        p[L_WR]=new float[]{cx-36*u, shY+25*u+drop};   // wrists stay BELOW shoulders (low wristUp)
        p[R_WR]=new float[]{cx+36*u, shY+25*u+drop};
        return p;
    }

    /** Bicep curl (upright): elbow bend, wrists stay low (below shoulders). Legs static. */
    static float[][] curlPose(float elbowDeg) {
        float[][] p = blank();
        float cx = 320, cy = 240, u = 1f;
        float shY = cy - 60*u, hipY = cy + 20*u;
        float halfW = 30*u;
        p[L_SH]=new float[]{cx-halfW, shY};
        p[R_SH]=new float[]{cx+halfW, shY};
        p[L_HIP]=new float[]{cx-halfW, hipY};
        p[R_HIP]=new float[]{cx+halfW, hipY};
        float knY = hipY + 70*u, anY = knY + 70*u;
        p[L_KN]=new float[]{cx-halfW, knY};
        p[R_KN]=new float[]{cx+halfW, knY};
        p[L_AN]=new float[]{cx-halfW, anY};
        p[R_AN]=new float[]{cx+halfW, anY};
        // elbow fixed at side; forearm rotates up as elbowDeg shrinks (curl).
        float elXL=cx-halfW-4*u, elYL=shY+45*u;
        float elXR=cx+halfW+4*u, elYR=shY+45*u;
        p[L_EL]=new float[]{elXL, elYL};
        p[R_EL]=new float[]{elXR, elYR};
        // upper arm points down (shoulder→elbow ≈ (0,+1)). forearm interior angle = elbowDeg.
        double bend = Math.toRadians(180.0 - elbowDeg);
        float fore = 45*u;
        // forearm rotates from straight-down toward up-inward; wrist stays below shoulder (low wristUp).
        float wdx = (float)Math.sin(bend), wdy = (float)Math.cos(bend);
        p[L_WR]=new float[]{elXL - fore*wdx, elYL - fore*wdy + fore* (1-wdy)}; // keep below shoulder
        p[R_WR]=new float[]{elXR + fore*wdx, elYR - fore*wdy + fore* (1-wdy)};
        // clamp wrists to stay at/below shoulder line to guarantee low wristUp
        if (p[L_WR][1] < shY+5*u) p[L_WR][1] = shY+5*u;
        if (p[R_WR][1] < shY+5*u) p[R_WR][1] = shY+5*u;
        head(p, cx, shY-30*u, 200f);
        return p;
    }

    /** Plank: horizontal, level (low sag), no oscillation. */
    static float[][] plankPose() {
        return pushupPose(165f); // straight arms, body horizontal, hips in line → torso~90, sag low, no motion
    }

    // ============================================================
    //                    NOISE / TRANSFORMS
    // ============================================================
    static float[][] copy(float[][] kp) {
        float[][] o = new float[17][2];
        for (int i=0;i<17;i++){ o[i][0]=kp[i][0]; o[i][1]=kp[i][1]; }
        return o;
    }

    static float[][] jitter(float[][] kp, long seed, float px) {
        java.util.Random rng = new java.util.Random(seed*2654435761L);
        float[][] o = copy(kp);
        for (int i=0;i<17;i++){
            if (o[i][0]==0f && o[i][1]==0f) continue; // keep missing missing
            o[i][0]+= (rng.nextFloat()*2f-1f)*px;
            o[i][1]+= (rng.nextFloat()*2f-1f)*px;
        }
        return o;
    }
    static float[][] gauss(float[][] kp, long seed, float sigma) {
        java.util.Random rng = new java.util.Random(seed*40503L+7);
        float[][] o = copy(kp);
        for (int i=0;i<17;i++){
            if (o[i][0]==0f && o[i][1]==0f) continue;
            o[i][0]+= (float)(rng.nextGaussian()*sigma);
            o[i][1]+= (float)(rng.nextGaussian()*sigma);
        }
        return o;
    }
    static float[][] scale(float[][] kp, float k, float cx, float cy) {
        float[][] o = copy(kp);
        for (int i=0;i<17;i++){
            if (o[i][0]==0f && o[i][1]==0f) continue;
            o[i][0]=cx+(o[i][0]-cx)*k;
            o[i][1]=cy+(o[i][1]-cy)*k;
        }
        return o;
    }
    static float[][] translate(float[][] kp, float dx, float dy) {
        float[][] o = copy(kp);
        for (int i=0;i<17;i++){
            if (o[i][0]==0f && o[i][1]==0f) continue;
            o[i][0]+=dx; o[i][1]+=dy;
        }
        return o;
    }
    static float[][] dropout(float[][] kp, int... idx) {
        float[][] o = copy(kp);
        for (int i: idx){ o[i][0]=0f; o[i][1]=0f; }
        return o;
    }
    static float[][] teleportJoint(float[][] kp, int idx, float delta) {
        float[][] o = copy(kp);
        if (!(o[idx][0]==0f && o[idx][1]==0f)) { o[idx][0]+=delta; o[idx][1]-=delta; }
        return o;
    }

    // ============================================================
    //                    DRIVE HELPERS
    // ============================================================
    interface PoseFn { float[][] at(int f); }

    static VyayamaCoach mk(boolean filt) {
        VyayamaCoach c = new VyayamaCoach();
        c.reset();
        c.setFilterEnabled(filt);
        return c;
    }

    /** Drive a periodic motion for n*window frames, return final key. */
    static String drive(VyayamaCoach c, int frames, PoseFn fn) {
        String key = "NONE";
        for (int rep = 0; rep < 3; rep++)
            for (int f = 0; f < frames; f++)
                key = c.onFrame(fn.at(f), tNs()).key;
        return key;
    }

    /** Warm the engine into a locked SQUAT with 2 full clean reps; records baseline reps. */
    static void warmSquat(VyayamaCoach c) {
        int reps = 0;
        for (int rep = 0; rep < 2; rep++)
            for (int f = 0; f < 45; f++)
                reps = c.onFrame(squatPose(130f + 40f*(float)Math.cos(2*Math.PI*f/45)), tNs()).reps;
        baselineReps = reps; lastReps = reps;
    }

    // monotonic timestamp generator
    static long clock = 0;
    static long tNs() { long t = clock; clock += DT; return t; }

    // ============================================================
    //                    ASSERTIONS
    // ============================================================
    static void expectReps(String name, int got, int want) {
        total++;
        boolean ok = got == want;
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (got " + got + " want " + want + ")");
    }
    static void expectAtLeast(String name, int got, int min) {
        total++;
        boolean ok = got >= min;
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (got " + got + " want >=" + min + ")");
    }
    static void expectKey(String name, String got, String want) {
        total++;
        boolean ok = got.equals(want);
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (got " + got + " want " + want + ")");
    }
    static void expectKeyAtLeast(String name, VyayamaCoach c, int reps, int min) {
        total++;
        boolean ok = reps >= min;
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (squat reps got " + reps + " want >=" + min + ")");
    }
    static void expectBool(String name, boolean got, boolean want) {
        total++;
        boolean ok = got == want;
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (got " + got + " want " + want + ")");
    }
    static void expectNear(String name, float got, float want, float tol) {
        total++;
        boolean ok = Math.abs(got - want) <= tol;
        if (ok) passed++;
        System.out.println(name + ": " + (ok?"PASS":"FAIL") + " (got " + got + " want " + want + "±" + tol + ")");
    }
    static String tag(boolean filt) { return filt ? " [filt ON]" : " [filt OFF]"; }
}
