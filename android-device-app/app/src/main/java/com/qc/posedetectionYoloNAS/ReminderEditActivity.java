//============================================================================
// Vyāyāma — add/edit one reminder / daily challenge. Volt-themed, programmatic.
// POST_NOTIFICATIONS launcher is registered up-front (required on API 33+) and
// only launched at first save; save + schedule happen regardless of the grant.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class ReminderEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "rid";
    static final int INK = 0xFF0A0E12, SURFACE = 0xFF121821, SURFACE2 = 0xFF1B2330,
            TEXT = 0xFFF2F6FA, MUTED = 0xFF8A97A6, VOLT = 0xFFC8FF3C, CORAL = 0xFFFF6A5A, HAIRLINE = 0xFF1F2A36;

    private long editingId = 0L;
    private String exercise = "SQUAT";
    private int reps = 15, hour = 7, minute = 0, daysMask = Reminder.ALL_DAYS;
    private boolean enabled = true;

    private Typeface black, med;
    private TextView exVal, timeVal, repsVal;
    private final TextView[] dayChips = new TextView[7];
    private SwitchCompat enabledSwitch;

    // registered during construction (BEFORE the activity is STARTED) — required on API 33+
    private final ActivityResultLauncher<String> notifPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { /* save proceeds either way */ });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ProfileStore.init(this);
        ReminderStore.init(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        med = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        editingId = getIntent().getLongExtra(EXTRA_ID, 0L);
        if (editingId != 0L) {
            Reminder r = ReminderStore.get(editingId);
            if (r != null) {
                exercise = r.exercise; reps = r.targetReps; hour = r.hour; minute = r.minute;
                daysMask = r.daysOfWeek; enabled = r.enabled;
            }
        }
        setContentView(buildScreen());
    }

    private View buildScreen() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(INK);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(28));
        sv.addView(col);

        TextView back = text("‹  Back", 16, MUTED, med);
        back.setPadding(0, dp(8), 0, dp(8));
        back.setOnClickListener(v -> finish());
        col.addView(back);

        col.addView(title(editingId == 0L ? "NEW CHALLENGE" : "EDIT CHALLENGE"));

        col.addView(label("EXERCISE"));
        exVal = valueRow(ProfileStore.pretty(exercise));
        exVal.setOnClickListener(v -> pickExercise());
        col.addView(exVal);

        col.addView(label("TARGET REPS"));
        col.addView(repsRow());

        col.addView(label("TIME"));
        timeVal = valueRow(TimeFmt.hm(hour, minute));
        timeVal.setOnClickListener(v -> pickTime());
        col.addView(timeVal);

        col.addView(label("DAYS"));
        col.addView(daysRow());

        LinearLayout en = card();
        en.setOrientation(LinearLayout.HORIZONTAL);
        en.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams enlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        enlp.topMargin = dp(14); en.setLayoutParams(enlp);
        TextView el = text("Enabled", 16, TEXT, med);
        el.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        en.addView(el);
        enabledSwitch = new SwitchCompat(this);
        enabledSwitch.setChecked(enabled);
        enabledSwitch.setThumbTintList(ColorStateList.valueOf(VOLT));
        en.addView(enabledSwitch);
        col.addView(en);

        TextView save = text("Save", 17, INK, black);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(18), dp(16), dp(18), dp(16));
        save.setBackground(round(VOLT, dp(16), 0, 0));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(24); save.setLayoutParams(sp);
        save.setOnClickListener(v -> save());
        col.addView(save);

        if (editingId != 0L) {
            TextView del = text("Delete reminder", 15, CORAL, med);
            del.setGravity(Gravity.CENTER);
            del.setPadding(dp(18), dp(16), dp(18), dp(8));
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dl.topMargin = dp(8); del.setLayoutParams(dl);
            del.setOnClickListener(v -> confirmDelete());
            col.addView(del);
        }
        return sv;
    }

    private void pickExercise() {
        final String[] ids = ProfileStore.EXERCISES;
        String[] names = new String[ids.length];
        int sel = 0;
        for (int i = 0; i < ids.length; i++) { names[i] = ProfileStore.pretty(ids[i]); if (ids[i].equals(exercise)) sel = i; }
        new AlertDialog.Builder(this).setTitle("Exercise")
                .setSingleChoiceItems(names, sel, (d, w) -> { exercise = ids[w]; exVal.setText(ProfileStore.pretty(exercise)); d.dismiss(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void pickTime() {
        new TimePickerDialog(this, (view, h, m) -> { hour = h; minute = m; timeVal.setText(TimeFmt.hm(h, m)); }, hour, minute, false).show();
    }
    private View repsRow() {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(8); row.setLayoutParams(rp);
        TextView minus = stepBtn("–");
        minus.setOnClickListener(v -> { reps = Math.max(1, reps - (reps > 20 ? 5 : 1)); repsVal.setText(String.valueOf(reps)); });
        repsVal = text(String.valueOf(reps), 26, VOLT, black);
        repsVal.setGravity(Gravity.CENTER);
        repsVal.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView plus = stepBtn("+");
        plus.setOnClickListener(v -> { reps = Math.min(999, reps + (reps >= 20 ? 5 : 1)); repsVal.setText(String.valueOf(reps)); });
        row.addView(minus); row.addView(repsVal); row.addView(plus);
        return row;
    }
    private TextView stepBtn(String s) {
        TextView t = text(s, 26, VOLT, black);
        t.setGravity(Gravity.CENTER);
        t.setBackground(round(0x14C8FF3C, dp(14), 0x3DC8FF3C, dp(1)));
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(56), dp(56)));
        return t;
    }
    private View daysRow() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = dp(8); wrap.setLayoutParams(wp);
        String[] d = {"M", "T", "W", "T", "F", "S", "S"};
        for (int i = 0; i < 7; i++) {
            final int bit = i;
            TextView chip = text(d[i], 15, TEXT, black);
            chip.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            lp.setMarginEnd(i < 6 ? dp(6) : 0);
            chip.setLayoutParams(lp);
            dayChips[i] = chip;
            paintDay(chip, (daysMask & (1 << bit)) != 0);
            chip.setOnClickListener(v -> { daysMask ^= (1 << bit); paintDay(chip, (daysMask & (1 << bit)) != 0); });
            wrap.addView(chip);
        }
        return wrap;
    }
    private void paintDay(TextView chip, boolean on) {
        chip.setBackground(round(on ? VOLT : SURFACE2, dp(12), on ? VOLT : HAIRLINE, dp(1)));
        chip.setTextColor(on ? INK : TEXT);
    }

    private void save() {
        if (daysMask == 0) {
            new AlertDialog.Builder(this).setMessage("Pick at least one day.").setPositiveButton("OK", null).show();
            return;
        }
        enabled = enabledSwitch.isChecked();
        Reminder r = (editingId == 0L) ? new Reminder() : ReminderStore.get(editingId);
        if (r == null) r = new Reminder();
        r.id = editingId; r.exercise = exercise; r.targetReps = reps; r.hour = hour; r.minute = minute;
        r.daysOfWeek = daysMask; r.enabled = enabled; r.profile = ProfileStore.getActive();
        if (editingId == 0L) r = ReminderStore.add(r); else ReminderStore.update(r);

        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.canPost(this)) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        ReminderScheduler.reschedule(this, r);
        finish();
    }
    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("Delete reminder?")
                .setPositiveButton("Delete", (d, w) -> {
                    ReminderScheduler.cancel(this, editingId);
                    ReminderStore.delete(editingId);
                    finish();
                })
                .setNegativeButton("Cancel", null).show();
    }

    // ---- builders ----
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(SURFACE, dp(16), HAIRLINE, dp(1)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        return c;
    }
    private TextView valueRow(String s) {
        TextView t = text(s, 17, TEXT, med);
        t.setBackground(round(SURFACE, dp(16), HAIRLINE, dp(1)));
        t.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8); t.setLayoutParams(lp);
        return t;
    }
    private TextView label(String s) {
        TextView t = text(s, 12, MUTED, black);
        t.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(20); t.setLayoutParams(lp);
        return t;
    }
    private TextView title(String s) {
        TextView t = text(s, 24, VOLT, black);
        t.setLetterSpacing(0.06f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6); lp.bottomMargin = dp(4); t.setLayoutParams(lp);
        return t;
    }
    private TextView text(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        return t;
    }
    private static GradientDrawable round(int fill, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
