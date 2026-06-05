//============================================================================
// Vyāyāma — fully-offline user profiles. SharedPreferences only: no network,
// no database, no cloud. Per profile we persist a per-exercise personal best,
// a lifetime total, the current + last session reps, and a daily training streak.
// Keys are namespaced "<profile>::<kind>::<exercise>".
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProfileStore {

    private static final String PREFS    = "vyayama_profiles";
    private static final String K_LIST   = "profiles";   // newline-joined names
    private static final String K_ACTIVE = "active";
    private static final String DEFAULT  = "Athlete";

    /** canonical exercise ids — must match VyayamaCoach reported ids. */
    public static final String[] EXERCISES = {"SQUAT", "PUSHUP", "LUNGE", "BICEP_CURL", "JUMPING_JACK"};

    private static SharedPreferences sp;

    private ProfileStore() {}

    public static synchronized void init(Context ctx) {
        if (sp != null) return;
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (listProfiles().isEmpty()) addProfile(DEFAULT);
        if (getActive().isEmpty()) setActive(listProfiles().get(0));
    }

    // ---------------- profiles ----------------
    public static List<String> listProfiles() {
        String csv = sp.getString(K_LIST, "");
        ArrayList<String> out = new ArrayList<>();
        if (!csv.isEmpty()) for (String s : csv.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public static void addProfile(String name) {
        name = clean(name);
        if (name.isEmpty()) return;
        Set<String> set = new LinkedHashSet<>(listProfiles());
        if (set.add(name)) sp.edit().putString(K_LIST, join(set)).apply();
    }

    public static void deleteProfile(String name) {
        Set<String> set = new LinkedHashSet<>(listProfiles());
        if (!set.remove(name)) return;
        SharedPreferences.Editor e = sp.edit().putString(K_LIST, join(set));
        for (String ex : EXERCISES) {
            e.remove(key(name, "pb", ex)).remove(key(name, "total", ex))
             .remove(key(name, "cur", ex)).remove(key(name, "last", ex));
        }
        e.remove(streakKey(name)).remove(lastYmdKey(name));
        if (name.equals(getActive())) e.putString(K_ACTIVE, set.isEmpty() ? "" : set.iterator().next());
        e.apply();
    }

    public static void setActive(String name) { sp.edit().putString(K_ACTIVE, name).apply(); }
    public static String getActive() { return sp.getString(K_ACTIVE, ""); }

    // ---------------- personal best ----------------
    public static int getPB(String profile, String ex) { return sp.getInt(key(profile, "pb", ex), 0); }

    /** returns true iff reps set a NEW personal best (and persists it). */
    public static boolean maybeUpdatePB(String profile, String ex, int reps) {
        if (reps <= 0) return false;
        if (reps > getPB(profile, ex)) { sp.edit().putInt(key(profile, "pb", ex), reps).apply(); return true; }
        return false;
    }

    // ---------------- totals + session ----------------
    public static int getTotal(String profile, String ex)       { return sp.getInt(key(profile, "total", ex), 0); }
    public static int getLastSession(String profile, String ex) { return sp.getInt(key(profile, "last", ex), 0); }
    public static int getSession(String profile, String ex)     { return sp.getInt(key(profile, "cur", ex), 0); }

    /** add freshly-counted reps to the lifetime total + current-session tally. */
    public static void addReps(String profile, String ex, int delta) {
        if (delta <= 0) return;
        sp.edit().putInt(key(profile, "total", ex), getTotal(profile, ex) + delta)
                 .putInt(key(profile, "cur", ex),   getSession(profile, ex) + delta).apply();
    }

    /** begin a fresh training session: freeze the current tallies into "last", then clear. */
    public static void startSession(String profile) {
        SharedPreferences.Editor e = sp.edit();
        for (String ex : EXERCISES) { e.putInt(key(profile, "last", ex), getSession(profile, ex))
                                       .putInt(key(profile, "cur", ex), 0); }
        e.apply();
    }

    // ---------------- streak ----------------
    public static int getStreak(String profile) { return sp.getInt(streakKey(profile), 0); }

    /** call on the user's first counted rep of the day — advances or rolls the streak by calendar day. */
    public static void bumpStreak(String profile) {
        long now = System.currentTimeMillis();
        int today = ymd(now);
        int last = sp.getInt(lastYmdKey(profile), 0);
        if (last == today) return;
        Calendar y = Calendar.getInstance(); y.setTimeInMillis(now); y.add(Calendar.DAY_OF_YEAR, -1);
        int streak = (last == ymdOf(y)) ? getStreak(profile) + 1 : 1;     // DST-safe "yesterday"
        sp.edit().putInt(streakKey(profile), streak).putInt(lastYmdKey(profile), today).apply();
    }

    // ---------------- helpers ----------------
    private static String key(String p, String kind, String ex) { return p + "::" + kind + "::" + ex; }
    private static String streakKey(String p)  { return p + "::streak"; }
    private static String lastYmdKey(String p) { return p + "::lastYmd"; }

    private static int ymd(long ms) {
        Calendar c = Calendar.getInstance(); c.setTimeInMillis(ms);
        return ymdOf(c);
    }
    private static int ymdOf(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static String clean(String s) { return s == null ? "" : s.trim().replace("\n", " ").replace("::", "-"); }

    private static String join(Set<String> set) {
        StringBuilder b = new StringBuilder();
        for (String s : set) { if (b.length() > 0) b.append('\n'); b.append(s); }
        return b.toString();
    }

    public static String pretty(String exKey) {
        switch (exKey) {
            case "SQUAT":        return "Squat";
            case "PUSHUP":       return "Push-up";
            case "LUNGE":        return "Lunge";
            case "BICEP_CURL":   return "Bicep Curl";
            case "JUMPING_JACK": return "Jumping Jack";
            default:             return exKey;
        }
    }
}
