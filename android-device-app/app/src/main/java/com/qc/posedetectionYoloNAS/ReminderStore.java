//============================================================================
// Vyāyāma — offline reminder store (SharedPreferences StringSet of pipe records).
// Record: id | exercise | targetReps | hour | minute | daysOfWeek | enabled(0|1) | profile
// Separate prefs file from vyayama_profiles so reminder writes never race stat writes.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class ReminderStore {

    private static final String PREFS = "vyayama_reminders", K_SET = "reminders", K_SEQ = "next_id", SEP = "|";
    private static SharedPreferences sp;

    private ReminderStore() {}

    public static synchronized void init(Context ctx) {
        if (sp == null) sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static List<Reminder> all() {
        List<Reminder> out = new ArrayList<>();
        for (String line : sp.getStringSet(K_SET, new HashSet<String>())) {
            Reminder r = parse(line);
            if (r != null) out.add(r);
        }
        Collections.sort(out, (a, b) -> {
            int t = (a.hour * 60 + a.minute) - (b.hour * 60 + b.minute);
            return t != 0 ? t : Long.compare(a.id, b.id);
        });
        return out;
    }

    public static Reminder get(long id) { for (Reminder r : all()) if (r.id == id) return r; return null; }

    public static Reminder add(Reminder r) {
        if (r.id == 0L) r.id = mintId();
        Set<String> s = mutableSet(); s.add(serialize(r)); commit(s); return r;
    }
    public static void update(Reminder r) {
        Set<String> s = mutableSet(); removeById(s, r.id); s.add(serialize(r)); commit(s);
    }
    public static void delete(long id) { Set<String> s = mutableSet(); removeById(s, id); commit(s); }
    public static void setEnabled(long id, boolean on) {
        Reminder r = get(id); if (r == null) return; r.enabled = on; update(r);
    }

    // ---------------- helpers ----------------
    private static long mintId() {
        long id = System.currentTimeMillis(), last = sp.getLong(K_SEQ, 0L);
        if (id <= last) id = last + 1;
        sp.edit().putLong(K_SEQ, id).apply();
        return id;
    }
    private static Set<String> mutableSet() { return new HashSet<>(sp.getStringSet(K_SET, new HashSet<String>())); }
    private static void removeById(Set<String> set, long id) {
        String pre = id + SEP;
        for (Iterator<String> it = set.iterator(); it.hasNext(); ) if (it.next().startsWith(pre)) it.remove();
    }
    private static void commit(Set<String> set) { sp.edit().putStringSet(K_SET, set).apply(); }

    static String serialize(Reminder r) {
        return r.id + SEP + san(r.exercise) + SEP + r.targetReps + SEP + r.hour + SEP + r.minute + SEP
                + r.daysOfWeek + SEP + (r.enabled ? "1" : "0") + SEP + san(r.profile);
    }
    static Reminder parse(String line) {
        if (line == null) return null;
        String[] p = line.split("\\|", -1);
        if (p.length != 7 && p.length != 8) return null;
        try {
            String prof = p.length == 8 ? p[7] : "";
            return new Reminder(Long.parseLong(p[0]), p[1], Integer.parseInt(p[2]),
                    Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    "1".equals(p[6]), prof);
        } catch (NumberFormatException e) { return null; }
    }
    private static String san(String s) { return s == null ? "" : s.replace(SEP, "-"); }
}
