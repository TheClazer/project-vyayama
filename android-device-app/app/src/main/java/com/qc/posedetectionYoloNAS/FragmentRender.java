//============================================================================
// Vyāyāma overlay. Draws the camera frame (rotated to upright), the pose skeleton
// (aligned via the same matrix), the locked athlete highlighted, and the coaching HUD
// (exercise · reps · form cue). Tap anywhere to cycle rotation 0/90/180/270 (live, no rebuild).
//============================================================================

package com.qc.posedetectionYoloNAS;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class FragmentRender extends View {

    private final ReentrantLock mLock = new ReentrantLock();
    private ArrayList<float[][]> coordsList = new ArrayList<>();
    private ArrayList<RectangleBox> boxlist = new ArrayList<>();

    private Bitmap mFrame;
    private volatile int mRotationDeg = 0;      // tap to cycle (0/90/180/270) — same value drives frame + skeleton + coach
    private int mPrimaryIdx = -1;

    // coaching HUD state
    private volatile String mExercise = "READY";
    private volatile int mReps = 0;
    private volatile String mCue = "";
    private volatile int mFormScore = -1;
    private volatile boolean mExercising = false;
    private int mFps = 0;

    private static final int ACCENT = Color.rgb(0x2F, 0xD9, 0xB6);   // teal
    private static final int AMBER  = Color.rgb(0xE0, 0x85, 0x3B);

    private final Paint mFramePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint mPosePrimary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPoseOther = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mJoint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mExerciseP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRepP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSmall = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCueText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mCueBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPanel = new Paint(Paint.ANTI_ALIAS_FLAG);

    int[][] Connections = {{1,3},{1,0},{2,4},{2,0},{0,5},{0,6},{5,7},{7,9},{6,8},{8,10},{5,11},{6,12},{11,12},{11,13},{13,15},{12,14},{14,16}};

    public FragmentRender(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        Typeface med   = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        mPosePrimary.setColor(ACCENT); mPosePrimary.setStrokeWidth(10); mPosePrimary.setStrokeCap(Paint.Cap.ROUND);
        mPoseOther.setColor(Color.argb(120, 150, 160, 170)); mPoseOther.setStrokeWidth(5); mPoseOther.setStrokeCap(Paint.Cap.ROUND);
        mJoint.setColor(Color.WHITE); mJoint.setStyle(Paint.Style.FILL);
        mTitle.setColor(ACCENT); mTitle.setTextSize(34); mTitle.setTypeface(black); mTitle.setLetterSpacing(0.06f);
        mExerciseP.setColor(Color.WHITE); mExerciseP.setTextSize(78); mExerciseP.setTypeface(black); mExerciseP.setShadowLayer(8, 0, 3, 0xAA000000);
        mRepP.setColor(ACCENT); mRepP.setTextSize(98); mRepP.setTypeface(black); mRepP.setShadowLayer(8, 0, 3, 0xAA000000);
        mSmall.setColor(0xFFB8C0CC); mSmall.setTextSize(26); mSmall.setTypeface(med);
        mCueText.setColor(0xFF0E1014); mCueText.setTextSize(38); mCueText.setTypeface(black);
        mCueBg.setColor(AMBER); mCueBg.setStyle(Paint.Style.FILL);
        mPanel.setColor(0xDD0E1014); mPanel.setStyle(Paint.Style.FILL);
    }

    public void setFrame(Bitmap b) { mFrame = b; }

    public void setCoach(String exercise, int reps, String cue, int formScore, boolean exercising, int fps) {
        mExercise = exercise; mReps = reps; mCue = cue; mFormScore = formScore; mExercising = exercising; mFps = fps;
    }

    public void setPrimaryIndex(int i) { mPrimaryIdx = i; }

    public int getRotationDeg() { return mRotationDeg; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            mRotationDeg = (mRotationDeg + 90) % 360;
            postInvalidate();
            return true;
        }
        return super.onTouchEvent(e);
    }

    public void setCoordsList(ArrayList<float[][]> newcoordslist, ArrayList<RectangleBox> t_boxlist) {
        mLock.lock();
        if (newcoordslist == null) { mLock.unlock(); postInvalidate(); return; }
        coordsList.clear(); boxlist.clear();
        for (int j = 0; j < newcoordslist.size(); j++) { coordsList.add(newcoordslist.get(j)); boxlist.add(t_boxlist.get(j)); }
        mLock.unlock();
        postInvalidate();
    }

    /** bitmap-space → view-space: rotate to upright + cover-scale + center. */
    private Matrix frameMatrix() {
        Matrix m = new Matrix();
        if (mFrame == null) return m;
        float bw = mFrame.getWidth(), bh = mFrame.getHeight();
        m.postTranslate(-bw / 2f, -bh / 2f);
        m.postRotate(mRotationDeg);
        boolean swap = ((mRotationDeg / 90) % 2) != 0;
        float rw = swap ? bh : bw, rh = swap ? bw : bh;
        float scale = Math.max(getWidth() / rw, getHeight() / rh);
        m.postScale(scale, scale);
        m.postTranslate(getWidth() / 2f, getHeight() / 2f);
        return m;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mLock.lock();
        try {
            Matrix m = frameMatrix();
            if (mFrame != null) canvas.drawBitmap(mFrame, m, mFramePaint);

            for (int j = 0; j < coordsList.size(); j++) {
                float[][] coords = coordsList.get(j);
                boolean primary = (j == mPrimaryIdx) || (mPrimaryIdx < 0 && j == 0);
                Paint bone = primary ? mPosePrimary : mPoseOther;

                // map all 17 points through the same matrix; track validity from ORIGINAL coords
                float[] pts = new float[coords.length * 2];
                boolean[] valid = new boolean[coords.length];
                for (int k = 0; k < coords.length; k++) {
                    pts[k * 2] = coords[k][0]; pts[k * 2 + 1] = coords[k][1];
                    valid[k] = !(coords[k][0] == 0f && coords[k][1] == 0f);
                }
                m.mapPoints(pts);

                for (int[] c : Connections) {
                    int a = c[0], b = c[1];
                    if (valid[a] && valid[b]) canvas.drawLine(pts[a*2], pts[a*2+1], pts[b*2], pts[b*2+1], bone);
                }
                for (int k = 0; k < coords.length; k++) {
                    if (valid[k]) canvas.drawCircle(pts[k*2], pts[k*2+1], primary ? 9 : 6, mJoint);
                }
            }

            // ---- coaching HUD ----
            String engine = MainActivity.runtime_var == 'D' ? "NPU"
                    : MainActivity.runtime_var == 'G' ? "GPU" : "CPU";
            String ex = mExercising ? mExercise : "READY";
            String repStr = Integer.toString(mReps);

            float L = 28, panelTop = 24, panelH = 252;
            float exW = mExerciseP.measureText(ex);
            float panelW = Math.max(exW + 48, 380);
            canvas.drawRoundRect(new RectF(16, panelTop, 16 + panelW, panelTop + panelH), 22, 22, mPanel);

            canvas.drawText("VYĀYĀMA", L, panelTop + 44, mTitle);
            canvas.drawText(engine + "  ·  " + mFps + " FPS  ·  tap to rotate", L, panelTop + 78, mSmall);
            canvas.drawText(ex, L, panelTop + 156, mExerciseP);
            canvas.drawText(repStr, L, panelTop + 240, mRepP);
            float rx = L + mRepP.measureText(repStr) + 16;
            canvas.drawText("REPS", rx, panelTop + 240, mSmall);
            if (mExercising && mFormScore >= 0) canvas.drawText("form " + mFormScore, rx, panelTop + 208, mSmall);

            if (mExercising && mCue != null && !mCue.isEmpty()) {
                float pad = 22, cueTop = panelTop + panelH + 14, h = 58;
                float tw = mCueText.measureText(mCue);
                mCueBg.setColor("Good rep!".equals(mCue) ? ACCENT : AMBER);
                canvas.drawRoundRect(new RectF(16, cueTop, 16 + tw + 2 * pad, cueTop + h), 16, 16, mCueBg);
                canvas.drawText(mCue, 16 + pad, cueTop + 40, mCueText);
            }
        } finally {
            mLock.unlock();
        }
    }
}
