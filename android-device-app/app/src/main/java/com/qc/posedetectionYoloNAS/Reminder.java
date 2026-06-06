//============================================================================
// Vyāyāma — one reminder / daily-challenge. Fully offline; persisted by ReminderStore.
// daysOfWeek is a 7-bit mask, bit0=Mon .. bit6=Sun (NOT Calendar order — convert below).
// id is unique/stable/monotonic; (int) id is the AlarmManager + notification request code.
//============================================================================
package com.qc.posedetectionYoloNAS;

import java.util.Calendar;

public final class Reminder {
    public long    id;
    public String  exercise;    // one of ProfileStore.EXERCISES
    public int     targetReps;
    public int     hour;        // 0..23 local wall-clock
    public int     minute;      // 0..59
    public int     daysOfWeek;  // 7-bit mask bit0=Mon..bit6=Sun; 0 = no day
    public boolean enabled;
    public String  profile;     // owning profile name (for the greeting)

    public Reminder() {}
    public Reminder(long id, String exercise, int targetReps, int hour, int minute,
                    int daysOfWeek, boolean enabled, String profile) {
        this.id = id; this.exercise = exercise; this.targetReps = targetReps;
        this.hour = hour; this.minute = minute; this.daysOfWeek = daysOfWeek;
        this.enabled = enabled; this.profile = profile;
    }

    public static final int MON = 1, TUE = 2, WED = 4, THU = 8, FRI = 16, SAT = 32, SUN = 64;
    public static final int ALL_DAYS = 0b1111111;   // 127
    public static final int WEEKDAYS = 0b0011111;   // 31
    public static final int WEEKENDS = 0b1100000;   // 96

    public boolean isDayOn(int bit0to6) { return (daysOfWeek & (1 << bit0to6)) != 0; }
    public boolean runsOnAnyDay()       { return daysOfWeek != 0; }

    /** True if this fires on the given Calendar.DAY_OF_WEEK (SUN=1..SAT=7). */
    public boolean firesOnCalendarDay(int calDow) {
        int bit = (calDow == Calendar.SUNDAY) ? 6 : (calDow - 2);   // SUN->6, MON->0 .. SAT->5
        return isDayOn(bit);
    }

    public String daysLabel() {
        if (daysOfWeek == 0)        return "—";
        if (daysOfWeek == ALL_DAYS) return "Every day";
        if (daysOfWeek == WEEKDAYS) return "Weekdays";
        if (daysOfWeek == WEEKENDS) return "Weekends";
        String[] s = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) if (isDayOn(i)) { if (b.length() > 0) b.append(' '); b.append(s[i]); }
        return b.toString();
    }

    public String timeLabel() { return TimeFmt.hm(hour, minute); }
}

/** 12-hour wall-clock formatter (package-private helper). */
final class TimeFmt {
    static String hm(int h, int m) {
        String ap = h < 12 ? "AM" : "PM";
        int h12 = h % 12; if (h12 == 0) h12 = 12;
        return h12 + ":" + (m < 10 ? "0" + m : "" + m) + " " + ap;
    }
}
