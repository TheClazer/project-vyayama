//============================================================================
// Vyāyāma — re-arms every reminder after a reboot or app update.
// Exported (required to receive the system BOOT_COMPLETED broadcast).
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ReminderStore.init(ctx);
        Notifications.ensureChannel(ctx);
        for (Reminder r : ReminderStore.all()) ReminderScheduler.schedule(ctx, r);  // arms enabled, cancels disabled
    }
}
