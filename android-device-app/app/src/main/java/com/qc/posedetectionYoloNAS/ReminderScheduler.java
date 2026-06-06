//============================================================================
// Vyāyāma — AlarmManager scheduling for reminders. Self-rescheduling, INEXACT
// (setWindowAndAllowWhileIdle) so no exact-alarm permission is needed on API 31+.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public final class ReminderScheduler {

    static final String ACTION = "com.qc.posedetectionYoloNAS.FIRE";

    private ReminderScheduler() {}

    public static void reschedule(Context ctx, Reminder r) { schedule(ctx, r); }

    /** Arms the next occurrence (or cancels if disabled / no day). */
    public static void schedule(Context ctx, Reminder r) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = piForId(ctx, r.id);
        if (!r.enabled || !r.runsOnAnyDay()) { am.cancel(pi); return; }
        long when = computeNextTriggerMillis(r, System.currentTimeMillis());
        if (when <= 0) { am.cancel(pi); return; }
        // inexact + Doze-resilient; needs no exact-alarm permission on any API level (minSdk 24).
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
    }

    public static void cancel(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(piForId(ctx, id));   // identical action+requestCode+flags -> matches
    }

    private static PendingIntent piForId(Context ctx, long id) {
        Intent i = new Intent(ctx, AlarmReceiver.class).setAction(ACTION).putExtra("rid", id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getBroadcast(ctx, (int) id, i, flags);
    }

    /** First wall-clock match of {hour:minute on an enabled day} strictly after `from` (DST-safe). */
    static long computeNextTriggerMillis(Reminder r, long from) {
        if (!r.runsOnAnyDay()) return -1;
        for (int i = 0; i < 8; i++) {
            Calendar c = Calendar.getInstance();   // fresh each iter so a DST jump can't shift the wall time
            c.setTimeInMillis(from);
            c.add(Calendar.DAY_OF_YEAR, i);
            c.set(Calendar.HOUR_OF_DAY, r.hour);
            c.set(Calendar.MINUTE, r.minute);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long t = c.getTimeInMillis();
            if (t > from && r.firesOnCalendarDay(c.get(Calendar.DAY_OF_WEEK))) return t;
        }
        return -1;
    }
}
