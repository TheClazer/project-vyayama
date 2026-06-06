//============================================================================
// Vyāyāma — host activity. Boots straight to the camera on the NPU (DSP).
// Engine (NPU / GPU / CPU) is switchable from the top-right 3-dot menu.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.WindowManager;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    static { System.loadLibrary("posedetectionYoloNAS"); }

    public static char runtime_var = 'D';   // default = DSP = Hexagon NPU
    // Manual mode pin (null = automatic recognition). Static + volatile so it survives the
    // per-onResume fragment/coach rebuild (mirrors runtime_var / FragmentRender.SHOW_VISION).
    // Written on the UI thread (mode dialog), read on the camera thread (CameraFragment).
    public static volatile String MANUAL_EXERCISE = null;
    // Offline voice coach: an app-scoped TTS player (survives the per-resume fragment rebuild) + the
    // pure-Java cadence brain. Both live here so they outlive CameraFragment recreation.
    public static volatile VoicePlayer VOICE_PLAYER = null;
    public static final VoiceCoach VOICE_COACH = new VoiceCoach();

    private static final int M_NPU = 1, M_GPU = 2, M_CPU = 3, M_VISION = 4, M_PROFILE = 5, M_MODE = 6, M_VOICE = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProfileStore.init(this);
        VoicePrefs.init(this);
        if (VOICE_PLAYER == null)
            VOICE_PLAYER = new VoicePlayer(getApplicationContext(), VoicePrefs.getRate(), 1.05f, VoicePrefs.getVoiceName());
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateBar();
        OpenCVLoader.initDebug();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overToCamera(runtime_var);
        refreshStatStrip();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu sub = menu.addSubMenu("Engine");
        sub.add(0, M_NPU, 0, "NPU  (Hexagon)");
        sub.add(0, M_GPU, 0, "GPU  (Adreno)");
        sub.add(0, M_CPU, 0, "CPU");
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);  // lives in the 3-dot overflow
        MenuItem vis = menu.add(0, M_VISION, 1, "Coach Vision");
        vis.setCheckable(true);
        vis.setChecked(FragmentRender.SHOW_VISION);
        // manual mode — pick one exercise (or Automatic). Title reflects the current pin.
        MenuItem mode = menu.add(0, M_MODE, 2, modeTitle());
        mode.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);   // lives in the 3-dot overflow
        // voice coach settings (enable, voice, rate)
        MenuItem voice = menu.add(0, M_VOICE, 3, "Voice settings");
        voice.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        // profile/stats — the only action button (shows as a volt icon left of the overflow)
        MenuItem prof = menu.add(0, M_PROFILE, 0, "Profile");
        prof.setIcon(R.drawable.ic_profile);
        prof.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == M_PROFILE) {
            startActivity(new Intent(this, ProfileDetailActivity.class));
            return true;
        }
        if (item.getItemId() == M_VISION) {
            FragmentRender.SHOW_VISION = !FragmentRender.SHOW_VISION;
            item.setChecked(FragmentRender.SHOW_VISION);
            return true;
        }
        if (item.getItemId() == M_MODE) {
            ModePickerDialog.show(this, MANUAL_EXERCISE, key -> {
                MANUAL_EXERCISE = key;                                    // null = auto, else raw key
                FragmentRender.MANUAL_PINNED = (MANUAL_EXERCISE != null); // subtle HUD tag
                updateBar();                                             // refresh subtitle
                invalidateOptionsMenu();                                 // refresh "Mode: X" title
            });
            return true;
        }
        if (item.getItemId() == M_VOICE) {
            VoiceSettingsDialog.show(this, VOICE_PLAYER, this::invalidateOptionsMenu);
            return true;
        }
        char r;
        switch (item.getItemId()) {
            case M_NPU: r = 'D'; break;
            case M_GPU: r = 'G'; break;
            case M_CPU: r = 'C'; break;
            default: return super.onOptionsItemSelected(item);
        }
        if (r != runtime_var) {
            runtime_var = r;
            updateBar();
            overToCamera(r);
        }
        return true;
    }

    private static String engineName(char r) {
        return r == 'D' ? "NPU" : r == 'G' ? "GPU" : "CPU";
    }

    private void updateBar() {
        if (getSupportActionBar() == null) return;
        String prof = ProfileStore.getActive();
        String coach = (MANUAL_EXERCISE == null) ? "Coach" : "Manual · " + ProfileStore.pretty(MANUAL_EXERCISE);
        getSupportActionBar().setTitle("Vyāyāma");
        getSupportActionBar().setSubtitle((prof.isEmpty() ? "" : prof + "  ·  ") + coach + " · " + engineName(runtime_var));
    }

    /** Overflow item title reflecting the current mode pin. */
    private static String modeTitle() {
        return MANUAL_EXERCISE == null ? "Mode: Automatic" : "Mode: " + ProfileStore.pretty(MANUAL_EXERCISE);
    }

    /** Streak + today's best chip above the preview. Called on resume + on each new rep (not per frame). */
    void refreshStatStrip() {
        View strip = findViewById(R.id.stat_strip);
        if (strip == null) return;
        String prof = ProfileStore.getActive();
        if (prof == null || prof.isEmpty()) { strip.setVisibility(View.GONE); return; }
        TextView streakV = findViewById(R.id.strip_streak);
        TextView bestV = findViewById(R.id.strip_best);
        int streak = ProfileStore.getStreak(prof);
        streakV.setText(streak > 0 ? ("🔥 " + streak) : "Day 1");
        String bestEx = null; int best = 0; boolean session = false;
        for (String ex : ProfileStore.EXERCISES) {
            int v = ProfileStore.getSession(prof, ex);
            if (v > best) { best = v; bestEx = ex; session = true; }
        }
        if (bestEx == null) {
            for (String ex : ProfileStore.EXERCISES) {
                int v = ProfileStore.getPB(prof, ex);
                if (v > best) { best = v; bestEx = ex; }
            }
        }
        bestV.setText(bestEx == null ? "Let's get the first rep in"
                : (session ? "Today: " : "PB: ") + best + " " + ProfileStore.pretty(bestEx));
        strip.setVisibility(View.VISIBLE);
    }

    private void overToCamera(char runtime_value) {
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            Bundle args = new Bundle();
            args.putChar("key", runtime_value);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_content, CameraFragment.create(args))
                    .commitAllowingStateLoss();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        overToCamera(runtime_var);
    }

    @Override
    protected void onStop() {
        // session close / app backgrounded → flush the RAM stats buffer to persistent storage
        ProfileStore.flush();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        ProfileStore.flush();
        // free the TTS engine only on a real teardown (not a config-change/rotation rebuild)
        if (isFinishing() && VOICE_PLAYER != null) { VOICE_PLAYER.shutdown(); VOICE_PLAYER = null; }
        super.onDestroy();
    }
}
