//============================================================================
// Vyāyāma — Profile hub: greeting + streak, personal bests, lifetime totals,
// last session, and the reminders/challenges list. Built programmatically on the
// Volt system (AppTheme, action bar hidden, in-layout back).
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import java.util.Calendar;
import java.util.List;

public class ProfileDetailActivity extends AppCompatActivity {

    static final int INK = 0xFF0A0E12, SURFACE = 0xFF121821, SURFACE2 = 0xFF1B2330,
            TEXT = 0xFFF2F6FA, MUTED = 0xFF8A97A6, VOLT = 0xFFC8FF3C, CORAL = 0xFFFF6A5A,
            HAIRLINE = 0xFF1F2A36, DIM = 0xFF3A4452;

    private Typeface black, med;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ProfileStore.init(this);
        ReminderStore.init(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        getWindow().getDecorView().setBackgroundColor(INK);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setContentView(buildScreen());   // rebuild so edits/sessions reflect on return
    }

    @Override
    protected void onPause() {
        ProfileStore.flush();   // leaving the profile screen → persist the RAM stats buffer
        super.onPause();
    }

    private View buildScreen() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(INK);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(14), dp(20), dp(28));
        sv.addView(col);

        String prof = ProfileStore.getActive();
        if (prof == null || prof.isEmpty()) prof = "Athlete";

        TextView back = text("‹  Back", 16, MUTED, med);
        back.setPadding(0, dp(8), 0, dp(8));
        back.setOnClickListener(v -> finish());
        col.addView(back);

        // header card
        LinearLayout header = card();
        header.addView(text(greeting() + ",", 15, MUTED, med));
        TextView name = text(prof, 34, TEXT, black);
        LinearLayout.LayoutParams np = wrap(); np.topMargin = dp(2); name.setLayoutParams(np);
        header.addView(name);
        int streak = ProfileStore.getStreak(prof);
        TextView sc = chip(streak > 0 ? ("🔥  " + streak + "-day streak") : "Fresh start", streak > 0);
        LinearLayout.LayoutParams scp = wrap(); scp.topMargin = dp(12); sc.setLayoutParams(scp);
        header.addView(sc);
        addCard(col, header);

        col.addView(sectionLabel("PERSONAL BESTS"));
        col.addView(pbGrid(prof));

        col.addView(sectionLabel("LIFETIME TOTALS"));
        addCard(col, totalsCard(prof));

        col.addView(sectionLabel("LAST SESSION"));
        addCard(col, lastSessionCard(prof));

        col.addView(sectionLabel("REMINDERS & CHALLENGES"));
        col.addView(remindersBlock());
        return sv;
    }

    // ---- sections ----
    private View pbGrid(String prof) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        String[] ex = ProfileStore.EXERCISES;
        for (int i = 0; i < ex.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.topMargin = dp(10); row.setLayoutParams(rp);
            row.addView(pbCell(prof, ex[i], true));
            if (i + 1 < ex.length) row.addView(pbCell(prof, ex[i + 1], false));
            grid.addView(row);
        }
        return grid;
    }
    private View pbCell(String prof, String exKey, boolean left) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(SURFACE, dp(16), HAIRLINE, dp(1)));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginEnd(left ? dp(5) : 0); lp.setMarginStart(left ? 0 : dp(5));
        c.setLayoutParams(lp);
        int pb = ProfileStore.getPB(prof, exKey);
        c.addView(text(pb > 0 ? String.valueOf(pb) : "—", 30, pb > 0 ? VOLT : DIM, black));
        c.addView(text(ProfileStore.pretty(exKey), 13, MUTED, med));
        return c;
    }
    private LinearLayout totalsCard(String prof) {
        LinearLayout card = card();
        long grand = 0;
        for (String exKey : ProfileStore.EXERCISES) {
            int t = ProfileStore.getTotal(prof, exKey);
            grand += t;
            card.addView(statRow(ProfileStore.pretty(exKey), String.valueOf(t), t > 0 ? TEXT : MUTED));
        }
        card.addView(divider());
        card.addView(statRow("Total reps", String.valueOf(grand), VOLT));
        return card;
    }
    private LinearLayout lastSessionCard(String prof) {
        LinearLayout card = card();
        boolean any = false;
        for (String exKey : ProfileStore.EXERCISES) {
            int v = ProfileStore.getLastSession(prof, exKey);
            if (v > 0) { card.addView(statRow(ProfileStore.pretty(exKey), String.valueOf(v), TEXT)); any = true; }
        }
        if (!any) card.addView(text("No completed sessions yet — your next set starts the record.", 14, MUTED, med));
        return card;
    }
    private View remindersBlock() {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        List<Reminder> rs = ReminderStore.all();
        if (rs.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No reminders yet. Add a daily challenge and we'll nudge you.", 14, MUTED, med));
            addCard(block, empty);
        } else {
            for (Reminder r : rs) addCard(block, reminderRow(r));
        }
        TextView add = text("+  Add reminder / challenge", 16, VOLT, black);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(18), dp(18), dp(18), dp(18));
        add.setBackground(round(0x14C8FF3C, dp(16), 0x3DC8FF3C, dp(1)));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = dp(10); add.setLayoutParams(ap);
        add.setOnClickListener(v -> startActivity(new Intent(this, ReminderEditActivity.class)));
        block.addView(add);
        return block;
    }
    private LinearLayout reminderRow(final Reminder r) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String title = r.targetReps + " " + ProfileStore.pretty(r.exercise) + (r.targetReps == 1 ? "" : "s");
        texts.addView(text(title, 18, TEXT, black));
        texts.addView(text(r.timeLabel() + "   ·   " + r.daysLabel(), 13, MUTED, med));
        texts.setOnClickListener(v -> startActivity(new Intent(this, ReminderEditActivity.class).putExtra(ReminderEditActivity.EXTRA_ID, r.id)));
        row.addView(texts);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setOnCheckedChangeListener(null);   // recycle-safe: set state before wiring the listener
        sw.setChecked(r.enabled);
        sw.setThumbTintList(ColorStateList.valueOf(VOLT));
        sw.setOnCheckedChangeListener((bv, on) -> {
            ReminderStore.setEnabled(r.id, on);
            Reminder fresh = ReminderStore.get(r.id);
            if (fresh != null) ReminderScheduler.reschedule(this, fresh);
        });
        row.addView(sw);
        return row;
    }

    // ---- helpers ----
    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(SURFACE, dp(18), HAIRLINE, dp(1)));
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        return c;
    }
    private void addCard(LinearLayout parent, View card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10); parent.addView(card, lp);
    }
    private TextView sectionLabel(String s) {
        TextView t = text(s, 12, MUTED, black);
        t.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams lp = wrap(); lp.topMargin = dp(22); lp.bottomMargin = dp(2);
        t.setLayoutParams(lp);
        return t;
    }
    private TextView text(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        return t;
    }
    private View statRow(String label, String value, int valueColor) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(7); rp.bottomMargin = dp(7); row.setLayoutParams(rp);
        TextView l = text(label, 15, MUTED, med);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(l);
        row.addView(text(value, 17, valueColor, black));
        return row;
    }
    private View divider() {
        View v = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(6); lp.bottomMargin = dp(6); v.setLayoutParams(lp);
        v.setBackgroundColor(HAIRLINE);
        return v;
    }
    private TextView chip(String s, boolean strong) {
        TextView t = text(s, 13, strong ? INK : MUTED, black);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(round(strong ? VOLT : 0x00000000, dp(20), strong ? VOLT : MUTED, dp(1)));
        return t;
    }
    private static GradientDrawable round(int fill, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }
    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    private String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
