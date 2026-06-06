//============================================================================
// Vyāyāma — Training-mode picker. A clean, programmatic Volt dialog that lets the
// user stay on Automatic recognition or PIN one of the 7 exercises (manual mode,
// so the coach can never misread it). Matches the Volt design used across the app
// (near-black sheet, volt #C8FF3C accents, rounded cards). No XML, no new drawables.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class ModePickerDialog {

    /** Callback: key==null means Automatic; otherwise a raw exercise key (e.g. "SQUAT"). */
    public interface OnMode { void pick(String key); }

    // Volt palette (identical to ProfileDetailActivity / FragmentRender).
    private static final int INK = 0xFF0A0E12, SURFACE = 0xFF121821, SURFACE2 = 0xFF1B2330,
            TEXT = 0xFFF2F6FA, MUTED = 0xFF8A97A6, VOLT = 0xFFC8FF3C, HAIRLINE = 0xFF1F2A36,
            VOLT_WASH = 0x14C8FF3C, VOLT_EDGE = 0x3DC8FF3C, VOLT_SUB = 0xCCC8FF3C;

    // null = Automatic; then the 7 exercises in the same order as the rest of the app.
    private static final String[] KEYS = {null, "SQUAT", "PUSHUP", "BICEP_CURL",
            "JUMPING_JACK", "SHOULDER_PRESS", "SITUP", "PLANK"};
    private static final String[] HINTS = {
            "Auto-detect the exercise",
            "Knee bend · counts reps",
            "Elbow bend · counts reps",
            "Forearm curl · counts reps",
            "Arms + legs open · counts reps",
            "Press overhead · counts reps",
            "Torso flex · counts reps",
            "Isometric hold · timed in seconds",
    };

    private ModePickerDialog() {}

    public static void show(final Activity a, final String current, final OnMode cb) {
        final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        final Typeface med = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        final Dialog dlg = new Dialog(a);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        // root sheet
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(round(SURFACE, dp(a, 22), HAIRLINE, dp(a, 1)));
        card.setPadding(dp(a, 18), dp(a, 20), dp(a, 18), dp(a, 12));

        // header
        TextView title = text(a, "CHOOSE MODE", 22, VOLT, black);
        title.setLetterSpacing(0.10f);
        card.addView(title, mw(dp(a, 2)));
        card.addView(text(a, "Pin one exercise so the coach never misreads it.", 13, MUTED, med), mw(dp(a, 14)));

        // scrollable list of options
        ScrollView sv = new ScrollView(a);
        sv.setVerticalFadingEdgeEnabled(false);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        for (int i = 0; i < KEYS.length; i++) {
            final String key = KEYS[i];
            boolean selected = equalsKey(current, key);
            String label = (key == null) ? "Automatic" : ProfileStore.pretty(key);
            View row = buildRow(a, black, med, label, HINTS[i], selected, i == 0);
            row.setOnClickListener(v -> { cb.pick(key); dlg.dismiss(); });
            list.addView(row);
        }
        card.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // footer: hairline + Cancel
        View hr = new View(a);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(a, 1));
        hp.topMargin = dp(a, 10); hp.bottomMargin = dp(a, 6); hr.setLayoutParams(hp);
        hr.setBackgroundColor(HAIRLINE);
        card.addView(hr);

        TextView cancel = text(a, "Cancel", 15, MUTED, med);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(a, 14), dp(a, 14), dp(a, 14), dp(a, 14));
        cancel.setOnClickListener(v -> dlg.dismiss());
        card.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dlg.setContentView(card);
        dlg.setCanceledOnTouchOutside(true);

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));      // transparent → rounded corners show
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.62f; w.setAttributes(lp);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.getDecorView().setPadding(dp(a, 20), 0, dp(a, 20), 0);     // side inset from screen edges
        }
        dlg.show();
    }

    /** One selectable option row: [marker] [label + hint] [✓ when selected]. */
    private static View buildRow(Activity a, Typeface black, Typeface med,
                                 String label, String hint, boolean selected, boolean first) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(a, 56));
        row.setPadding(dp(a, 14), dp(a, 12), dp(a, 14), dp(a, 12));
        row.setClickable(true);
        row.setBackground(selected
                ? round(VOLT_WASH, dp(a, 14), VOLT_EDGE, dp(a, 1))
                : round(SURFACE2, dp(a, 14), HAIRLINE, dp(a, 1)));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.topMargin = first ? 0 : dp(a, 8);
        row.setLayoutParams(rp);

        // marker holder (fixed width so labels align across selected/unselected states)
        FrameLayout marker = new FrameLayout(a);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(dp(a, 18), ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.setMarginEnd(dp(a, 14)); marker.setLayoutParams(mp);
        View dot = new View(a);
        if (selected) {
            FrameLayout.LayoutParams dp4 = new FrameLayout.LayoutParams(dp(a, 4), dp(a, 28), Gravity.CENTER);
            dot.setLayoutParams(dp4);
            dot.setBackground(round(VOLT, dp(a, 2), 0, 0));         // volt accent bar
        } else {
            FrameLayout.LayoutParams ring = new FrameLayout.LayoutParams(dp(a, 18), dp(a, 18), Gravity.CENTER);
            dot.setLayoutParams(ring);
            dot.setBackground(round(0x00000000, dp(a, 9), MUTED, dp(a, 2)));   // hollow ring
        }
        marker.addView(dot);
        row.addView(marker);

        // label + hint column (weight 1 so the check pushes to the far right)
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        texts.addView(text(a, label, 17, selected ? VOLT : TEXT, selected ? black : med));
        texts.addView(text(a, hint, 12, selected ? VOLT_SUB : MUTED, med));
        row.addView(texts);

        if (selected) {
            TextView check = text(a, "✓", 18, VOLT, black);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.setMarginStart(dp(a, 8)); check.setLayoutParams(cp);
            row.addView(check);
        }
        return row;
    }

    // ---- helpers (match ProfileDetailActivity) ----
    private static boolean equalsKey(String a, String b) { return a == null ? b == null : a.equals(b); }

    private static TextView text(Activity a, String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(a);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(tf);
        return t;
    }

    private static LinearLayout.LayoutParams mw(int bottomMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
