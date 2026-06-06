//============================================================================
// Vyāyāma — local notification channel + posting (offline; no network).
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class Notifications {

    static final String CHANNEL = "vyayama_reminders";

    private Notifications() {}

    /** Idempotent — safe to call from any entry point (must precede the first notify). */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Training reminders",
                NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Daily exercise reminders and challenges");
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 220, 120, 220});
        nm.createNotificationChannel(ch);
    }

    public static boolean canPost(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void postReminder(Context ctx, Reminder r) {
        ensureChannel(ctx);                       // first fire may come straight from AlarmReceiver
        if (!canPost(ctx)) return;

        String pretty = ProfileStore.pretty(r.exercise);
        String unit = pretty.toLowerCase() + (r.targetReps == 1 ? "" : "s");
        String title = (r.profile == null || r.profile.isEmpty()) ? "Time to train" : (r.profile + ", time to train");
        String text = r.targetReps + " " + unit + " — let's go";

        Intent open = new Intent(ctx, SplashActivity.class)
                .putExtra("from_reminder", true)
                .putExtra("exercise", r.exercise)
                .putExtra("targetReps", r.targetReps)
                .putExtra("profile", r.profile == null ? "" : r.profile)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(ctx, 0x100000 + (int) r.id, open, piFlags);

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_vyayama)
                .setColor(0xFFC8FF3C)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(content);

        try {
            NotificationManagerCompat.from(ctx).notify((int) r.id, b.build());
        } catch (SecurityException ignored) {
            // permission revoked between canPost() and notify() — drop silently
        }
    }
}
