//============================================================================
// Vyāyāma — fires a reminder notification, then arms the next occurrence.
// Internal only (not exported); only our own PendingIntents target it.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ReminderStore.init(ctx);
        long rid = intent.getLongExtra("rid", -1L);
        Reminder r = ReminderStore.get(rid);
        if (r == null || !r.enabled || !r.runsOnAnyDay()) return;
        Notifications.postReminder(ctx, r);
        ReminderScheduler.schedule(ctx, r);   // re-arm for the next matching day/time
    }
}
