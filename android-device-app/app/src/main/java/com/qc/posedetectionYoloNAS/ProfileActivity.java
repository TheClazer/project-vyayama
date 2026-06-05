//============================================================================
// Vyāyāma — profile picker. The app's launch screen: choose an athlete (each is a
// fully-offline profile with its own personal bests + streak), then drop into the
// camera coach. Cards are built programmatically so there's no adapter boilerplate.
//============================================================================
package com.qc.posedetectionYoloNAS;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;

import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ProfileStore.init(this);
        if (getSupportActionBar() != null) getSupportActionBar().hide();
        setContentView(R.layout.activity_profile);
        findViewById(R.id.btn_new).setOnClickListener(v -> promptNewProfile());
    }

    @Override
    protected void onResume() { super.onResume(); renderList(); }

    private void renderList() {
        LinearLayout list = findViewById(R.id.profile_list);
        list.removeAllViews();
        List<String> profiles = ProfileStore.listProfiles();
        for (String name : profiles) list.addView(card(name));
    }

    private View card(final String name) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        int pad = dp(18);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFF2F6FA);
        nameView.setTextSize(22);
        nameView.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        card.addView(nameView);

        TextView stats = new TextView(this);
        stats.setText(statLine(name));
        stats.setTextColor(0xFF8A97A6);
        stats.setTextSize(13);
        stats.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(6);
        stats.setLayoutParams(slp);
        card.addView(stats);

        card.setOnClickListener(v -> select(name));
        card.setOnLongClickListener(v -> { confirmDelete(name); return true; });
        return card;
    }

    private String statLine(String name) {
        StringBuilder b = new StringBuilder();
        int streak = ProfileStore.getStreak(name);
        b.append(streak > 0 ? (streak + "-day streak") : "Fresh start");
        String bestEx = null; int best = 0;
        for (String ex : ProfileStore.EXERCISES) {
            int pb = ProfileStore.getPB(name, ex);
            if (pb > best) { best = pb; bestEx = ex; }
        }
        if (bestEx != null) b.append("   ·   PB ").append(best).append(" ").append(ProfileStore.pretty(bestEx));
        else b.append("   ·   no records yet — let's change that");
        return b.toString();
    }

    private void select(String name) {
        ProfileStore.setActive(name);
        ProfileStore.startSession(name);
        startActivity(new Intent(this, MainActivity.class));
    }

    private void promptNewProfile() {
        final EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        in.setHint("Name");
        new AlertDialog.Builder(this)
                .setTitle("New profile")
                .setView(in)
                .setPositiveButton("Create", (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) { ProfileStore.addProfile(n); ProfileStore.setActive(n); renderList(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final String name) {
        new AlertDialog.Builder(this)
                .setTitle("Delete \"" + name + "\"?")
                .setMessage("Removes this profile and all its records.")
                .setPositiveButton("Delete", (d, w) -> {
                    ProfileStore.deleteProfile(name);
                    if (ProfileStore.listProfiles().isEmpty()) ProfileStore.addProfile("Athlete");
                    renderList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
