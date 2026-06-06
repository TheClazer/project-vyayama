//============================================================================
// Vyāyāma — fully-offline user profiles with a WRITE-BACK MEMORY BUFFER.
//
// Memory architecture: we treat storage the way a CPU treats memory — a hot RAM
// buffer for the live session, flushed to persistent flash only on close.
//   • OPEN  (load):  a profile's stats (PB / total / session / streak) are read
//                    once from flash into an in-RAM buffer.
//   • LIVE:          every rep reads/writes the RAM buffer — ZERO flash I/O per rep
//                    (no per-rep disk writes → less flash wear, battery-light).
//   • CLOSE (flush): the dirty buffer is written back to flash in ONE batched commit.
// SharedPreferences only — no network, no database, no cloud.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProfileStore {

    private static final String PREFS    = "vyayama_profiles";
    private static final String K_LIST   = "profiles";   // newline-joined names
    private static final String K_ACTIVE = "active";
    private static final String DEFAULT  = "Athlete";

    /** canonical exercise ids — must match VyayamaCoach reported ids. */
    public static final String[] EXERCISES = {"SQUAT", "PUSHUP", "BICEP_CURL", "JUMPING_JACK",
            "SHOULDER_PRESS", "SITUP"};   // PLANK excluded — its reps encode seconds, not reps

    private static SharedPreferences sp;

    // ---- write-back session buffer (the "RAM" tier) ----
    // All numeric stats are read THROUGH this buffer and written INTO it; flash is
    // touched only by flush(). Synchronized accessors: written on the camera thread
    // (per rep), read on the UI thread (stat strip / profile screen).
    private static final Map<String, Integer> buf = new HashMap<>();
    private static final Set<String> dirty = new LinkedHashSet<>();

    private ProfileStore() {}

    public static synchronized void init(Context ctx) {
        if (sp != null) return;
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (listProfiles().isEmpty()) addProfile(DEFAULT);
        if (getActive().isEmpty()) setActive(listProfiles().get(0));
        load(getActive());                 // OPEN: warm the buffer for the active profile
    }

    // ---------------- memory buffer: load (open) / flush (close) ----------------

    /** OPEN a profile session: pull its stats from flash into the RAM buffer (one read). */
    public static synchronized void load(String profile) {
        if (sp == null || profile == null || profile.isEmpty()) return;
        for (String ex : EXERCISES) {
            warm(key(profile, "pb", ex));   warm(key(profile, "total", ex));
            warm(key(profile, "cur", ex));  warm(key(profile, "last", ex));
        }
        warm(streakKey(profile)); warm(lastYmdKey(profile));
    }
    private static void warm(String k) { if (!buf.containsKey(k)) buf.put(k, sp.getInt(k, 0)); }

    /** CLOSE the session: write the dirty buffer back to flash in ONE batched commit. */
    public static synchronized void flush() {
        if (sp == null || dirty.isEmpty()) return;
        SharedPreferences.Editor e = sp.edit();
        for (String k : dirty) { Integer v = buf.get(k); e.putInt(k, v == null ? 0 : v); }
        e.apply();
        dirty.clear();
    }

    /** read-through: serve from RAM; on a miss, fault in from flash and cache it. */
    private static synchronized int getInt(String k) {
        Integer v = buf.get(k);
        if (v != null) return v;
        int val = (sp == null) ? 0 : sp.getInt(k, 0);
        buf.put(k, val);
        return val;
    }
    /** write-back: update RAM + mark dirty; flash is untouched until flush(). */
    private static synchronized void putInt(String k, int val) { buf.put(k, val); dirty.add(k); }

    // ---------------- profiles (list/active live on flash directly — rare, must never be lost) ----------------
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

    public static synchronized void deleteProfile(String name) {
        Set<String> set = new LinkedHashSet<>(listProfiles());
        if (!set.remove(name)) return;
        SharedPreferences.Editor e = sp.edit().putString(K_LIST, join(set));
        for (String ex : EXERCISES) {
            for (String kind : new String[]{"pb", "total", "cur", "last"}) {
                String k = key(name, kind, ex);
                e.remove(k); buf.remove(k); dirty.remove(k);     // purge the buffer too
            }
        }
        e.remove(streakKey(name)).remove(lastYmdKey(name));
        buf.remove(streakKey(name)); buf.remove(lastYmdKey(name));
        dirty.remove(streakKey(name)); dirty.remove(lastYmdKey(name));
        if (name.equals(getActive())) e.putString(K_ACTIVE, set.isEmpty() ? "" : set.iterator().next());
        e.apply();
    }

    public static synchronized void setActive(String name) {
        flush();                                       // persist the outgoing profile's buffer
        sp.edit().putString(K_ACTIVE, name).apply();
        load(name);                                    // warm the incoming profile
    }
    public static String getActive() { return sp.getString(K_ACTIVE, ""); }

    // ---------------- personal best ----------------
    public static int getPB(String profile, String ex) { return getInt(key(profile, "pb", ex)); }

    /** returns true iff reps set a NEW personal best (buffered; persisted on flush). */
    public static boolean maybeUpdatePB(String profile, String ex, int reps) {
        if (reps <= 0) return false;
        if (reps > getPB(profile, ex)) { putInt(key(profile, "pb", ex), reps); return true; }
        return false;
    }

    // ---------------- totals + session ----------------
    public static int getTotal(String profile, String ex)       { return getInt(key(profile, "total", ex)); }
    public static int getLastSession(String profile, String ex) { return getInt(key(profile, "last", ex)); }
    public static int getSession(String profile, String ex)     { return getInt(key(profile, "cur", ex)); }

    /** add freshly-counted reps to the lifetime total + current-session tally (buffer only). */
    public static void addReps(String profile, String ex, int delta) {
        if (delta <= 0) return;
        putInt(key(profile, "total", ex), getTotal(profile, ex) + delta);
        putInt(key(profile, "cur", ex),   getSession(profile, ex) + delta);
    }

    /** begin a fresh training session: freeze the current tallies into "last", then clear. */
    public static void startSession(String profile) {
        for (String ex : EXERCISES) {
            putInt(key(profile, "last", ex), getSession(profile, ex));
            putInt(key(profile, "cur", ex), 0);
        }
    }

    // ---------------- streak ----------------
    public static int getStreak(String profile) { return getInt(streakKey(profile)); }

    /** call on the user's first counted rep of the day — advances or rolls the streak by calendar day. */
    public static void bumpStreak(String profile) {
        long now = System.currentTimeMillis();
        int today = ymd(now);
        int last = getInt(lastYmdKey(profile));
        if (last == today) return;
        Calendar y = Calendar.getInstance(); y.setTimeInMillis(now); y.add(Calendar.DAY_OF_YEAR, -1);
        int streak = (last == ymdOf(y)) ? getStreak(profile) + 1 : 1;     // DST-safe "yesterday"
        putInt(streakKey(profile), streak); putInt(lastYmdKey(profile), today);
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
            case "BICEP_CURL":   return "Bicep Curl";
            case "JUMPING_JACK": return "Jumping Jack";
            case "SHOULDER_PRESS": return "Shoulder Press";
            case "SITUP":        return "Sit-up";
            case "PLANK":        return "Plank";
            default:             return exKey;
        }
    }
}
