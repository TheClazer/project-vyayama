//============================================================================
// Vyāyāma — VoiceCoach: pure-Java, deterministic cadence brain for the offline
// voice coach. NO Android imports, NO RNG, NO System.nanoTime — time enters only
// via the tsNs argument. Zero allocation on the null-returning path; the single
// short String it decides to speak is the only allocation. Called once per
// COMPLETED rep from CameraFragment (rep edge), never per frame.
//============================================================================
package com.qc.posedetectionYoloNAS;

public final class VoiceCoach {

    // ---- cadence constants ----
    private static final int  WINDOW              = 4;
    private static final int  CADENCE_MIN         = 3;             // >=3 reps banked since last spoke
    private static final long MIN_GAP_NS          = 4_000_000_000L;// >=4s between any two spoken lines
    private static final int  CLEAN_PRAISE_STREAK = 4;            // 4 consecutive CLEAN reps -> praise
    private static final int  MILESTONE_EVERY     = 10;

    // ---- phrase tables (static final, shared, never re-allocated) ----
    private static final String[] DEPTH   = { "Go a little deeper.", "A bit lower next time.", "Sink down further." };
    private static final String[] SAG     = { "Keep your hips in line.", "No sagging — tighten up.", "Lift those hips level." };
    private static final String[] SWING   = { "Stop the swing — control it.", "Steady the body, isolate it.", "Less momentum, more control." };
    private static final String[] ROM     = { "Full range — all the way.", "Open it up bigger.", "Reach the whole way." };
    private static final String[] TEMPO   = { "Slow it down a touch.", "Ease off the speed.", "Control the tempo." };
    private static final String[] POSTURE = { "Chest up, back tall.", "Brace your core.", "Keep it tight and upright." };
    private static final String[] LOCKOUT = { "Press all the way up.", "Lock it out at the top.", "Finish overhead." };
    private static final String[] SYM     = { "Even on both sides.", "Balance left and right.", "Keep it symmetrical." };
    private static final String[] PRAISE  = { "Beautiful form — keep going.", "That's it, nice and clean.",
                                              "Looking strong — stay with it.", "Smooth reps, lovely control." };

    // ---- state (all primitives / fixed-size arrays — no per-rep alloc) ----
    private boolean enabled = true;
    private String  curEx = "";
    private boolean hasSpoken = false;     // gates the time-guard so the sentinel never overflows the subtraction
    private long    lastSpeakNs = 0L;
    private int     repsSinceSpoke = 0;
    private int     totalReps = 0;
    private int     lastMilestoneSpoken = 0;

    private final String[] issueBuf = new String[WINDOW];
    private int issueCount = 0, issueHead = 0;

    private int    rot = 0;
    private String lastSpokenLine = "";

    public VoiceCoach() {}

    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }

    /** Called ONCE per COMPLETED rep. Returns a line to SPEAK, or null for silence. */
    public String onRep(String exKey, int repNumber, int formScore, String issue, long tsNs) {
        // 1) implicit exercise change guard, then buffer
        if (exKey == null) exKey = "";
        if (!exKey.equals(curEx)) flush(exKey);          // flush BEFORE buffering this rep
        totalReps = repNumber;                            // engine's authoritative count
        pushIssue(issue == null ? "CLEAN" : issue);
        repsSinceSpoke++;

        // 2) disabled -> silent, counters already advanced
        if (!enabled) return null;

        // 3) MILESTONE first (highest priority, still time-gated; NOT cadence-gated)
        if (totalReps > 0 && totalReps % MILESTONE_EVERY == 0 && totalReps != lastMilestoneSpoken
                && (!hasSpoken || (tsNs - lastSpeakNs) >= MIN_GAP_NS)) {
            lastMilestoneSpoken = totalReps;
            return speak(milestoneLine(totalReps), tsNs);
        }

        // 4) cadence gate (warm-up silence; never every rep)
        if (repsSinceSpoke < CADENCE_MIN) return null;

        // 5) min-time guard (never overlaps itself)
        if (hasSpoken && (tsNs - lastSpeakNs) < MIN_GAP_NS) return null;

        // 6) praise path (4 consecutive CLEAN in the window)
        if (cleanStreak() >= CLEAN_PRAISE_STREAK) return speak(praiseLine(), tsNs);

        // 7) dominant-issue path
        String dominant = mostFrequentNonClean();
        if (dominant == null) return null;               // window all-CLEAN (streak<4) or empty -> silent
        return speak(cueLineFor(dominant), tsNs);
    }

    /** Locked-exercise change (or new bout). Flushes buffer + cadence; KEEPS lastSpeakNs & rot. */
    public void onExerciseChange(String newExKey, long tsNs) { flush(newExKey == null ? "" : newExKey); }

    /** Full clear (new session / fragment rebuild). KEEPS enabled (config-like). */
    public void reset() {
        curEx = ""; lastSpeakNs = 0L; hasSpoken = false;
        repsSinceSpoke = 0; totalReps = 0; lastMilestoneSpoken = 0;
        issueCount = 0; issueHead = 0;
        rot = 0; lastSpokenLine = "";
    }

    // ---- internal ----
    private void flush(String newEx) {
        curEx = newEx;
        issueCount = 0; issueHead = 0;
        repsSinceSpoke = 0; totalReps = 0; lastMilestoneSpoken = 0;
        lastSpokenLine = "";
        // deliberately KEEP lastSpeakNs (respect the global 4s min-gap across a switch) and rot (variety).
    }

    private void pushIssue(String code) {
        issueBuf[issueHead] = code;                      // reference store of an interned literal — no alloc
        issueHead = (issueHead + 1) % WINDOW;
        if (issueCount < WINDOW) issueCount++;
    }

    /** Number of consecutive CLEAN reps ending at the most-recent rep. */
    private int cleanStreak() {
        int n = 0;
        for (int i = 0; i < issueCount; i++) {
            int idx = (issueHead - 1 - i + WINDOW) % WINDOW; // newest -> oldest
            if ("CLEAN".equals(issueBuf[idx])) n++; else break;
        }
        return n;
    }

    /** Most frequent non-CLEAN code over the window, requiring a real PATTERN (>=2 reps share it);
     *  ties -> most recent. null if no fault repeats — a single off-rep is treated as noise, not corrected. */
    private static final int MIN_PATTERN = 2;
    private String mostFrequentNonClean() {
        String best = null; int bestCount = 0;
        for (int i = 0; i < issueCount; i++) {                       // candidate = newest -> oldest
            int idx = (issueHead - 1 - i + WINDOW) % WINDOW;
            String code = issueBuf[idx];
            if (code == null || "CLEAN".equals(code)) continue;
            int c = 0;
            for (int j = 0; j < issueCount; j++) if (code.equals(issueBuf[j])) c++;
            if (c > bestCount) { bestCount = c; best = code; }       // strict > + newest-first scan = most-recent tie-break
        }
        return bestCount >= MIN_PATTERN ? best : null;              // one-off fault -> stay silent (pattern, not per-rep)
    }

    private String speak(String line, long tsNs) {
        if (line == null || line.equals(lastSpokenLine)) {
            line = nextDistinctVariant();                 // bump rot once, re-resolve in the SAME group
            if (line == null || line.equals(lastSpokenLine)) return null; // group size 1 -> rather silent than repeat
        }
        lastSpokenLine = line; lastSpeakNs = tsNs; hasSpoken = true; repsSinceSpoke = 0; rot++;
        return line;
    }

    /** Re-resolve the line that speak() was about to emit, advancing rot by one (for back-to-back avoidance).
     *  We re-derive the group from the same decision that produced the repeat. Praise and cue groups always
     *  have >=2 variants, so a distinct alternative exists; milestones rotate by (n/10). */
    private String nextDistinctVariant() {
        rot++;
        if (cleanStreak() >= CLEAN_PRAISE_STREAK) return praiseLine();
        String dom = mostFrequentNonClean();
        if (dom != null) return cueLineFor(dom);
        if (totalReps > 0 && totalReps % MILESTONE_EVERY == 0) return milestoneLine(totalReps);
        return null;
    }

    private String[] tableFor(String code) {
        switch (code) {
            case "DEPTH":   return DEPTH;
            case "SAG":     return SAG;
            case "SWING":   return SWING;
            case "ROM":     return ROM;
            case "TEMPO":   return TEMPO;
            case "POSTURE": return POSTURE;
            case "LOCKOUT": return LOCKOUT;
            case "SYM":     return SYM;
            default:        return null;
        }
    }
    private String cueLineFor(String code) {
        String[] t = tableFor(code);
        if (t == null) return null;
        return t[((rot % t.length) + t.length) % t.length];
    }
    private String praiseLine() { return PRAISE[((rot % PRAISE.length) + PRAISE.length) % PRAISE.length]; }
    private String milestoneLine(int n) {
        int i = ((n / MILESTONE_EVERY) % 4 + 4) % 4;
        switch (i) {
            case 0:  return n + " reps — strong work!";
            case 1:  return n + " in the bank, keep it up!";
            case 2:  return "Nice — that's " + n + ".";
            default: return n + " down, you've got this!";
        }
    }
}
