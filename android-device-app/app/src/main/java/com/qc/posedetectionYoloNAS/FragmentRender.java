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

    int[][] Connections = {{1,3},{1,0},{2,4},{2,0},{0,5},{0,6},{5,7},{7,9},{6,8},{8,10},{5,11},{6,12},{11,12},{11,13},{13,15},{12,14},{14,16}};

    public FragmentRender(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        mPosePrimary.setColor(ACCENT); mPosePrimary.setStrokeWidth(9);
        mPoseOther.setColor(Color.argb(140, 160, 160, 160)); mPoseOther.setStrokeWidth(5);
        mJoint.setColor(Color.WHITE); mJoint.setStyle(Paint.Style.FILL);
        mTitle.setColor(ACCENT); mTitle.setTextSize(42); mTitle.setTypeface(Typeface.DEFAULT_BOLD);
        mExerciseP.setColor(Color.WHITE); mExerciseP.setTextSize(86); mExerciseP.setTypeface(Typeface.DEFAULT_BOLD);
        mRepP.setColor(ACCENT); mRepP.setTextSize(64); mRepP.setTypeface(Typeface.DEFAULT_BOLD);
        mSmall.setColor(Color.WHITE); mSmall.setTextSize(30);
        mCueText.setColor(Color.rgb(0x1A, 0x12, 0x08)); mCueText.setTextSize(40); mCueText.setTypeface(Typeface.DEFAULT_BOLD);
        mCueBg.setColor(AMBER); mCueBg.setStyle(Paint.Style.FILL);
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
            canvas.drawText("Vyāyāma", 24, 50, mTitle);
            canvas.drawText("FPS " + mFps + "  ·  tap to rotate", 24, 90, mSmall);

            String ex = mExercising ? mExercise : "READY";
            canvas.drawText(ex, 24, 170, mExerciseP);
            canvas.drawText(mReps + " reps", 24, 240, mRepP);

            if (mExercising && mCue != null && !mCue.isEmpty()) {
                float pad = 18;
                float tw = mCueText.measureText(mCue);
                float top = 270, h = 64;
                mCueBg.setColor(("Good rep!".equals(mCue)) ? ACCENT : AMBER);
                canvas.drawRoundRect(new RectF(24, top, 24 + tw + 2 * pad, top + h), 16, 16, mCueBg);
                canvas.drawText(mCue, 24 + pad, top + 44, mCueText);
            }
        } finally {
            mLock.unlock();
        }
    }
}
