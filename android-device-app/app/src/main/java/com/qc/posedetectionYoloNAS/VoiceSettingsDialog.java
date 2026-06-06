//============================================================================
// Vyāyāma — Voice settings (Volt dialog, mirrors ModePickerDialog). Enable toggle,
// speech-rate slider, offline-voice picker, and a Test button. All on-device.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import java.util.List;
import java.util.Locale;

public final class VoiceSettingsDialog {

    private static final int INK = 0xFF0A0E12, SURFACE = 0xFF121821, SURFACE2 = 0xFF1B2330,
            TEXT = 0xFFF2F6FA, MUTED = 0xFF8A97A6, VOLT = 0xFFC8FF3C, HAIRLINE = 0xFF1F2A36,
            VOLT_WASH = 0x14C8FF3C, VOLT_EDGE = 0x3DC8FF3C, VOLT_SUB = 0xCCC8FF3C;

    private VoiceSettingsDialog() {}

    public static void show(final Activity a, final VoicePlayer player, final Runnable onChanged) {
        final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        final Typeface med = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        final Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(SURFACE, dp(a, 22), HAIRLINE, dp(a, 1)));
        card.setPadding(dp(a, 18), dp(a, 20), dp(a, 18), dp(a, 12));

        // header
        TextView title = text(a, "VOICE COACH", 22, VOLT, black);
        title.setLetterSpacing(0.10f);
        card.addView(title, mw(dp(a, 2)));
        card.addView(text(a, "Spoken cues every few reps — fully on-device.", 13, MUTED, med), mw(dp(a, 14)));

        // (1) enable toggle row
        LinearLayout enRow = new LinearLayout(a);
        enRow.setOrientation(LinearLayout.HORIZONTAL);
        enRow.setGravity(Gravity.CENTER_VERTICAL);
        enRow.setBackground(round(SURFACE2, dp(a, 14), HAIRLINE, dp(a, 1)));
        enRow.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        TextView enLbl = text(a, "Voice coaching", 16, TEXT, med);
        enLbl.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        enRow.addView(enLbl);
        SwitchCompat sw = new SwitchCompat(a);
        sw.setChecked(VoicePrefs.isEnabled());
        sw.setThumbTintList(ColorStateList.valueOf(VOLT));
        sw.setOnCheckedChangeListener((b, on) -> {
            VoicePrefs.setEnabled(on);
            if (!on && player != null) player.stopIfSpeaking();
            if (onChanged != null) onChanged.run();
        });
        enRow.addView(sw);
        card.addView(enRow, mw(0));

        // (2) speech rate
        card.addView(sectionLabel(a, black, "SPEECH RATE"), sectLp(a));
        LinearLayout rateCard = new LinearLayout(a);
        rateCard.setOrientation(LinearLayout.VERTICAL);
        rateCard.setBackground(round(SURFACE2, dp(a, 14), HAIRLINE, dp(a, 1)));
        rateCard.setPadding(dp(a, 14), dp(a, 10), dp(a, 14), dp(a, 12));
        final TextView rateVal = text(a, fmtRate(VoicePrefs.getRate()), 18, VOLT, black);
        rateCard.addView(rateVal);
        SeekBar sb = new SeekBar(a);
        sb.setMax(60);   // 0.7 .. 1.3 over 0..60
        sb.setProgress(Math.max(0, Math.min(60, Math.round((VoicePrefs.getRate() - 0.7f) * 100f))));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float r = 0.7f + p / 100f;
                rateVal.setText(fmtRate(r));
                if (fromUser) { VoicePrefs.setRate(r); if (player != null) player.setRate(r); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.topMargin = dp(a, 2);
        rateCard.addView(sb, sbLp);
        card.addView(rateCard, mw(0));

        // (3) voice picker
        card.addView(sectionLabel(a, black, "VOICE"), sectLp(a));
        final LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.addView(list);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 168));
        card.addView(sv, svLp);
        populateVoices(a, black, med, list, player, sw, onChanged);

        // (4) test button
        TextView test = text(a, "Test voice", 16, INK, black);
        test.setGravity(Gravity.CENTER);
        test.setPadding(dp(a, 18), dp(a, 14), dp(a, 18), dp(a, 14));
        test.setBackground(round(VOLT, dp(a, 16), 0, 0));
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        testLp.topMargin = dp(a, 14);
        test.setOnClickListener(v -> {
            if (!VoicePrefs.isEnabled()) { VoicePrefs.setEnabled(true); sw.setChecked(true); }
            if (player != null) player.test();
        });
        card.addView(test, testLp);

        // footer
        View hr = new View(a);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 1));
        hp.topMargin = dp(a, 10); hp.bottomMargin = dp(a, 6); hr.setLayoutParams(hp);
        hr.setBackgroundColor(HAIRLINE);
        card.addView(hr);
        TextView done = text(a, "Done", 15, MUTED, med);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        done.setOnClickListener(v -> dlg.dismiss());
        card.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dlg.setContentView(card);
        dlg.setCanceledOnTouchOutside(true);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.62f; w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.getDecorView().setPadding(dp(a, 20), 0, dp(a, 20), 0);
        }
        dlg.show();
    }

    private static void populateVoices(final Activity a, final Typeface black, final Typeface med,
                                       final LinearLayout list, final VoicePlayer player,
                                       final SwitchCompat sw, final Runnable onChanged) {
        list.removeAllViews();
        List<Voice> voices = (player == null) ? null : player.getOfflineEnVoices();
        if (voices == null || voices.isEmpty()) {
            list.addView(text(a, "Loading voices… reopen in a moment.", 13, MUTED, med), mw(dp(a, 6)));
            return;
        }
        String selectedName = VoicePrefs.getVoiceName();   // null = auto
        int max = Math.min(voices.size(), 8);
        for (int i = 0; i < max; i++) {
            final Voice v = voices.get(i);
            boolean selected = selectedName != null && selectedName.equals(v.getName());
            Locale loc = v.getLocale();
            String label = (loc != null) ? loc.getDisplayName() : (v.getName() == null ? "Voice" : v.getName());
            String hint = ((loc != null) ? loc.toString() : "en")
                    + "  ·  " + (v.getQuality() >= Voice.QUALITY_HIGH ? "High quality" : "Standard");
            View row = voiceRow(a, black, med, label, hint, selected);
            row.setOnClickListener(view -> {
                VoicePrefs.setVoiceName(v.getName());
                if (player != null) player.setVoiceByName(v.getName());
                if (!VoicePrefs.isEnabled()) { VoicePrefs.setEnabled(true); if (sw != null) sw.setChecked(true); }  // previewing a voice = enabling
                populateVoices(a, black, med, list, player, sw, onChanged);   // refresh selection state
                if (player != null) player.test();
                if (onChanged != null) onChanged.run();
            });
            list.addView(row);
        }
    }

    private static View voiceRow(Activity a, Typeface black, Typeface med,
                                 String label, String hint, boolean selected) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(a, 50));
        row.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10));
        row.setClickable(true);
        row.setBackground(selected
                ? round(VOLT_WASH, dp(a, 12), VOLT_EDGE, dp(a, 1))
                : round(SURFACE2, dp(a, 12), HAIRLINE, dp(a, 1)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(a, 8); row.setLayoutParams(rp);

        FrameLayout marker = new FrameLayout(a);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(a, 16), ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.setMarginEnd(dp(a, 12)); marker.setLayoutParams(mp);
        View dot = new View(a);
        if (selected) {
            FrameLayout.LayoutParams d = new FrameLayout.LayoutParams(dp(a, 4), dp(a, 24), Gravity.CENTER);
            dot.setLayoutParams(d); dot.setBackground(round(VOLT, dp(a, 2), 0, 0));
        } else {
            FrameLayout.LayoutParams d = new FrameLayout.LayoutParams(dp(a, 16), dp(a, 16), Gravity.CENTER);
            dot.setLayoutParams(d); dot.setBackground(round(0x00000000, dp(a, 8), MUTED, dp(a, 2)));
        }
        marker.addView(dot);
        row.addView(marker);

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(text(a, label, 15, selected ? VOLT : TEXT, selected ? black : med));
        texts.addView(text(a, hint, 11, selected ? VOLT_SUB : MUTED, med));
        row.addView(texts);
        return row;
    }

    // ---- helpers (mirror ModePickerDialog) ----
    private static TextView sectionLabel(Activity a, Typeface black, String s) {
        TextView t = text(a, s, 12, MUTED, black);
        t.setLetterSpacing(0.14f);
        return t;
    }
    private static LinearLayout.LayoutParams sectLp(Activity a) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 16); lp.bottomMargin = dp(a, 6);
        return lp;
    }
    private static String fmtRate(float r) {
        int x = Math.round(r * 100f);
        return (x / 100) + "." + String.format(Locale.US, "%02d", x % 100) + "x";
    }
    private static TextView text(Activity a, String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(a);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        return t;
    }
    private static LinearLayout.LayoutParams mw(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = bottomMargin;
        return lp;
    }
    private static GradientDrawable round(int fill, int radius, int stroke, int strokeW) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius);
        if (strokeW > 0) g.setStroke(strokeW, stroke);
        return g;
    }
    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }
}
