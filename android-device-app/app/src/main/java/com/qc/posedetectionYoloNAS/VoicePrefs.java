//============================================================================
// Vyāyāma — VoicePrefs: tiny offline SharedPreferences store for the voice coach
// (enabled / chosen voice / speech rate). In-memory enabledCache lets the camera
// thread read the on/off flag every rep with no disk I/O. No network, no cloud.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.content.SharedPreferences;

public final class VoicePrefs {
    private static final String PREFS    = "vyayama_voice";
    private static final String K_ENABLED = "enabled";
    private static final String K_VOICE   = "voiceName";   // null = auto-pick a soft female offline voice
    private static final String K_RATE    = "rate";
    private static SharedPreferences sp;
    private static volatile boolean enabledCache = true;   // camera-thread read, no disk; written on the UI thread

    private VoicePrefs() {}

    public static synchronized void init(Context ctx) {
        if (sp != null) return;
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        enabledCache = sp.getBoolean(K_ENABLED, true);
    }
    public static boolean isEnabled() { return (sp == null) ? enabledCache : sp.getBoolean(K_ENABLED, true); }
    public static boolean isEnabledFast() { return enabledCache; }     // rep-edge fast path, no disk
    public static void setEnabled(boolean on) { enabledCache = on; if (sp != null) sp.edit().putBoolean(K_ENABLED, on).apply(); }
    public static String getVoiceName() { return sp == null ? null : sp.getString(K_VOICE, null); }
    public static void setVoiceName(String name) { if (sp != null) sp.edit().putString(K_VOICE, name).apply(); }
    public static float getRate() { return sp == null ? 0.92f : sp.getFloat(K_RATE, 0.92f); }
    public static void setRate(float r) { if (sp != null) sp.edit().putFloat(K_RATE, clampRate(r)).apply(); }
    private static float clampRate(float r) { return r < 0.6f ? 0.6f : (r > 1.4f ? 1.4f : r); }
}
