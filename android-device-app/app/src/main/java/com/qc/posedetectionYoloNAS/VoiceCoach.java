//============================================================================
// Vyāyāma — VoiceCoach: pure-Java, deterministic voice brain. EYES-OFF mode:
// speaks EVERY rep — the count first, then a short comment (a correction on a
// faulty rep, an occasional "nice" on clean reps, hype on every 10th). No Android
// imports, no RNG, no clock calls (variety via an index counter). Called once per
// COMPLETED rep from CameraFragment; the line it returns is spoken by VoicePlayer.
//============================================================================
package com.qc.posedetectionYoloNAS;

public final class VoiceCoach {

    private static final int MILESTONE_EVERY = 10;
    private static final int PRAISE_EVERY    = 4;    // on a clean streak, drop a short praise every few reps

    // short tails so they flow naturally after the spoken count ("five, go a little deeper.")
    private static final String[] DEPTH   = { "go a little deeper.", "a bit lower.", "sink down further." };
    private static final String[] SAG     = { "hips in line.", "no sagging.", "lift those hips." };
    private static final String[] SWING   = { "control the swing.", "steady, isolate it.", "less momentum." };
    private static final String[] ROM     = { "full range.", "open it up.", "all the way." };
    private static final String[] TEMPO   = { "slow it down.", "ease the speed.", "control the tempo." };
    private static final String[] POSTURE = { "chest up.", "brace your core.", "stay tall." };
    private static final String[] LOCKOUT = { "lock it out.", "all the way up.", "finish overhead." };
    private static final String[] SYM     = { "even on both sides.", "balance it out.", "keep it level." };
    private static final String[] PRAISE  = { "nice.", "good.", "clean.", "strong.", "perfect." };
    private static final String[] MILE    = { "great work!", "keep it up!", "you've got this!", "on fire!" };

    private boolean enabled = true;
    private String  curEx = "";
    private int     rot = 0;            // rotates phrasing so it never sounds robotic
    private int     sincePraise = 0;

    public VoiceCoach() {}
    public void setEnabled(boolean on) { enabled = on; }
    public boolean isEnabled() { return enabled; }
    public void onExerciseChange(String newEx, long tsNs) { curEx = (newEx == null) ? "" : newEx; sincePraise = 0; }
    public void reset() { curEx = ""; rot = 0; sincePraise = 0; }

    /** Talkative / eyes-off: speaks EVERY rep — the count, then a short comment when useful. */
    public String onRep(String exKey, int repNumber, int formScore, String issue, long tsNs) {
        if (exKey == null) exKey = "";
        if (!exKey.equals(curEx)) { curEx = exKey; sincePraise = 0; }
        if (!enabled) return null;

        String code = (issue == null) ? "CLEAN" : issue;
        String suffix;
        if (repNumber > 0 && repNumber % MILESTONE_EVERY == 0) {
            suffix = pick(MILE); sincePraise = 0;                 // hype on every 10th rep
        } else if (!"CLEAN".equals(code)) {
            String[] t = tableFor(code);
            suffix = (t == null) ? null : pick(t);                // coach the fault on every faulty rep
            sincePraise = 0;
        } else {
            sincePraise++;
            if (sincePraise >= PRAISE_EVERY) { suffix = pick(PRAISE); sincePraise = 0; }  // a quick "nice"
            else suffix = null;                                   // otherwise just the count
        }
        return (suffix == null) ? (repNumber + ".") : (repNumber + ", " + suffix);
    }

    private String pick(String[] t) { String s = t[((rot % t.length) + t.length) % t.length]; rot++; return s; }
    private String[] tableFor(String c) {
        switch (c) {
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
}
