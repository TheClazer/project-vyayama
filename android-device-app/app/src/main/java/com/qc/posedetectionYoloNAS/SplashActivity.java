package com.qc.posedetectionYoloNAS;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Vyāyāma launch sequence (~2.8s, skippable on tap).
 *
 *   0.0s  athlete runs in from off-screen-left to centre (ease-out + bob),
 *         limbs swinging via the AnimatedVectorDrawable run-cycle.
 *   1.0s  wordmark rises + fades in with expanding letter-spacing; a thin
 *         volt underline sweeps left -> right.
 *   1.5s  "AI FORM COACH" tagline fades up, muted.
 *   2.0s  hold, then the whole scene fades + lifts out -> the profile picker.
 */
public class SplashActivity extends AppCompatActivity {

    private AnimatorSet sequence;
    private boolean handedOff = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // draw behind the system bars for a true fullscreen feel
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_splash);

        final View root = findViewById(R.id.splash_root);
        final ImageView runner = findViewById(R.id.runner);
        final TextView wordmark = findViewById(R.id.wordmark);
        final View underline = findViewById(R.id.underline);
        final TextView tagline = findViewById(R.id.tagline);

        // kick off the limb run-cycle
        Drawable d = runner.getDrawable();
        if (d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        }

        // resting (pre-animation) states
        wordmark.setAlpha(0f);
        wordmark.setTranslationY(dp(24));
        wordmark.setLetterSpacing(0f);

        underline.setScaleX(0f);
        underline.setPivotX(0f);          // grow from the left edge -> sweep right

        tagline.setAlpha(0f);
        tagline.setTranslationY(dp(8));

        final float startX = -getResources().getDisplayMetrics().widthPixels - dp(172);
        runner.setTranslationX(startX);

        // ---- 1. run-in -------------------------------------------------
        ObjectAnimator runIn = ObjectAnimator.ofFloat(runner, View.TRANSLATION_X, startX, 0f);
        runIn.setDuration(1050);
        runIn.setStartDelay(120);
        runIn.setInterpolator(new DecelerateInterpolator(1.7f));

        ObjectAnimator bob = ObjectAnimator.ofFloat(runner, View.TRANSLATION_Y,
                0f, -dp(10), 0f, -dp(6), 0f);
        bob.setDuration(1050);
        bob.setStartDelay(120);

        // stop the run-cycle once he settles centre-screen
        runIn.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                Drawable dr = runner.getDrawable();
                if (dr instanceof AnimatedVectorDrawable) {
                    ((AnimatedVectorDrawable) dr).stop();
                }
            }
        });

        // ---- 2. wordmark ----------------------------------------------
        ObjectAnimator wAlpha = ObjectAnimator.ofFloat(wordmark, View.ALPHA, 0f, 1f);
        ObjectAnimator wRise = ObjectAnimator.ofFloat(wordmark, View.TRANSLATION_Y, dp(24), 0f);
        wAlpha.setDuration(560);
        wRise.setDuration(560);
        wAlpha.setStartDelay(950);
        wRise.setStartDelay(950);
        wAlpha.setInterpolator(new DecelerateInterpolator());
        wRise.setInterpolator(new DecelerateInterpolator());

        final ValueAnimator wSpacing = ValueAnimator.ofFloat(0f, 0.06f);
        wSpacing.setDuration(680);
        wSpacing.setStartDelay(950);
        wSpacing.setInterpolator(new DecelerateInterpolator());
        wSpacing.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                wordmark.setLetterSpacing((Float) a.getAnimatedValue());
            }
        });

        // ---- underline sweep ------------------------------------------
        ObjectAnimator line = ObjectAnimator.ofFloat(underline, View.SCALE_X, 0f, 1f);
        line.setDuration(650);
        line.setStartDelay(1120);
        line.setInterpolator(new AccelerateDecelerateInterpolator());

        // ---- 3. tagline -----------------------------------------------
        ObjectAnimator tAlpha = ObjectAnimator.ofFloat(tagline, View.ALPHA, 0f, 1f);
        ObjectAnimator tRise = ObjectAnimator.ofFloat(tagline, View.TRANSLATION_Y, dp(8), 0f);
        tAlpha.setDuration(520);
        tRise.setDuration(520);
        tAlpha.setStartDelay(1500);
        tRise.setStartDelay(1500);
        tAlpha.setInterpolator(new DecelerateInterpolator());
        tRise.setInterpolator(new DecelerateInterpolator());

        // ---- 4. hold + exit -------------------------------------------
        ObjectAnimator outAlpha = ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f);
        ObjectAnimator outRise = ObjectAnimator.ofFloat(root, View.TRANSLATION_Y, 0f, -dp(40));
        outAlpha.setDuration(460);
        outRise.setDuration(460);
        outAlpha.setStartDelay(2750);
        outRise.setStartDelay(2750);
        outAlpha.setInterpolator(new AccelerateInterpolator());
        outRise.setInterpolator(new AccelerateInterpolator());

        sequence = new AnimatorSet();
        sequence.playTogether(runIn, bob, wAlpha, wRise, wSpacing, line,
                tAlpha, tRise, outAlpha, outRise);
        sequence.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                goNext();
            }
        });
        sequence.start();

        // tap to skip
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                goNext();
            }
        });
    }

    private void goNext() {
        if (handedOff) return;
        handedOff = true;
        if (sequence != null) sequence.cancel();
        // hand off to the profile picker (the app's real first screen)
        startActivity(new Intent(this, ProfileActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDestroy() {
        if (sequence != null) sequence.cancel();
        super.onDestroy();
    }

    // Splash should not be re-enterable via the back stack.
    @Override
    public void onBackPressed() {
        goNext();
    }
}
