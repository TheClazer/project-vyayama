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
    private volatile boolean mCueWarn = false;
    private volatile int mFormScore = -1;
    private volatile boolean mExercising = false;
    private int mFps = 0;

    private static final int ACCENT = Color.rgb(0xC8, 0xFF, 0x3C);   // volt (brand primary)
    private static final int AMBER  = Color.rgb(0xFF, 0x6A, 0x5A);   // coral (form-correction)

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
    private final Paint mStripe = new Paint(Paint.ANTI_ALIAS_FLAG);     // volt left accent bar
    private final Paint mPill = new Paint(Paint.ANTI_ALIAS_FLAG);       // engine badge bg
    private final Paint mBadgeText = new Paint(Paint.ANTI_ALIAS_FLAG);  // engine badge text
    private final Paint mFormP = new Paint(Paint.ANTI_ALIAS_FLAG);      // form score
    private final Paint mPbBg = new Paint(Paint.ANTI_ALIAS_FLAG);       // personal-best banner
    private final Paint mPbBig = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPbSmall = new Paint(Paint.ANTI_ALIAS_FLAG);

    // reused per-frame so onDraw allocates nothing (bible zero-alloc rule)
    private final RectF mRect = new RectF();
    private final RectF mPanelRect = new RectF();
    private final Matrix mMatrix = new Matrix();            // reused each frame (no per-frame alloc)
    private final float[] mPts = new float[34];             // 17 keypoints × 2 — reused
    private final boolean[] mValid = new boolean[17];

    // personal-best celebration (set from CameraFragment, auto-expires)
    private volatile String mPbText = null;
    private volatile long mPbUntilMs = 0;

    int[][] Connections = {{1,3},{1,0},{2,4},{2,0},{0,5},{0,6},{5,7},{7,9},{6,8},{8,10},{5,11},{6,12},{11,12},{11,13},{13,15},{12,14},{14,16}};

    public FragmentRender(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
        Typeface med   = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        mPosePrimary.setColor(ACCENT); mPosePrimary.setStrokeWidth(11); mPosePrimary.setStrokeCap(Paint.Cap.ROUND); mPosePrimary.setStrokeJoin(Paint.Join.ROUND);
        mPoseOther.setColor(Color.argb(110, 138, 151, 166)); mPoseOther.setStrokeWidth(5); mPoseOther.setStrokeCap(Paint.Cap.ROUND);
        mJoint.setColor(Color.WHITE); mJoint.setStyle(Paint.Style.FILL);
        mTitle.setColor(ACCENT); mTitle.setTextSize(26); mTitle.setTypeface(black); mTitle.setLetterSpacing(0.22f);
        mExerciseP.setColor(0xFFF2F6FA); mExerciseP.setTextSize(74); mExerciseP.setTypeface(black); mExerciseP.setLetterSpacing(-0.01f); mExerciseP.setShadowLayer(10, 0, 3, 0x99000000);
        mRepP.setColor(ACCENT); mRepP.setTextSize(96); mRepP.setTypeface(black); mRepP.setShadowLayer(12, 0, 3, 0x66000000);
        mSmall.setColor(0xFF8A97A6); mSmall.setTextSize(24); mSmall.setTypeface(med); mSmall.setLetterSpacing(0.08f);
        mFormP.setColor(0xFF8A97A6); mFormP.setTextSize(24); mFormP.setTypeface(med); mFormP.setLetterSpacing(0.08f);
        mCueText.setColor(0xFF0A0E12); mCueText.setTextSize(36); mCueText.setTypeface(black); mCueText.setLetterSpacing(0.02f);
        mCueBg.setColor(AMBER); mCueBg.setStyle(Paint.Style.FILL);
        mPanel.setColor(0xE6121821); mPanel.setStyle(Paint.Style.FILL);
        mStripe.setColor(ACCENT); mStripe.setStyle(Paint.Style.FILL);
        mPill.setColor(0x33C8FF3C); mPill.setStyle(Paint.Style.FILL);
        mBadgeText.setColor(ACCENT); mBadgeText.setTextSize(22); mBadgeText.setTypeface(black); mBadgeText.setLetterSpacing(0.06f);
        mPbBg.setColor(ACCENT); mPbBg.setStyle(Paint.Style.FILL);
        mPbBig.setColor(0xFF0A0E12); mPbBig.setTextSize(54); mPbBig.setTypeface(black);
        mPbSmall.setColor(0xFF0A0E12); mPbSmall.setTextSize(22); mPbSmall.setTypeface(black); mPbSmall.setLetterSpacing(0.14f);
    }

    /** Flash a "new personal best" banner for ~2.6 s. Called from the capture loop. */
    public void showNewPB(String ex, int reps) {
        mPbText = ex + "  ·  " + reps;
        mPbUntilMs = System.currentTimeMillis() + 2600;
        postInvalidate();
    }

    public void setFrame(Bitmap b) { mFrame = b; }

    public void setCoach(String exercise, int reps, String cue, boolean cueWarn, int formScore, boolean exercising, int fps) {
        mExercise = exercise; mReps = reps; mCue = cue; mCueWarn = cueWarn; mFormScore = formScore; mExercising = exercising; mFps = fps;
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
        Matrix m = mMatrix;
        m.reset();
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
                int n = coords.length;
                float[] pts = (n <= 17) ? mPts : new float[n * 2];
                boolean[] valid = (n <= 17) ? mValid : new boolean[n];
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
            boolean live = mExercising && !"READY".equals(mExercise) && !"…".equals(mExercise);
            String ex = live ? mExercise : "READY";
            String repStr = Integer.toString(mReps);

            float pad = 26f, panelL = 16, panelTop = 22, panelH = 250;
            float exW = mExerciseP.measureText(ex);
            float panelW = Math.max(exW + 2 * pad + 8, 396);
            mPanelRect.set(panelL, panelTop, panelL + panelW, panelTop + panelH);
            canvas.drawRoundRect(mPanelRect, 26, 26, mPanel);
            // volt accent stripe down the left edge
            mRect.set(panelL, panelTop + 16, panelL + 6, mPanelRect.bottom - 16);
            canvas.drawRoundRect(mRect, 3, 3, mStripe);

            float x = panelL + pad;
            canvas.drawText("VYĀYĀMA", x, panelTop + 42, mTitle);

            // engine + fps badge, right-aligned in the panel header
            String badge = engine + " · " + mFps + " FPS";
            float bw = mBadgeText.measureText(badge) + 28;
            mRect.set(mPanelRect.right - pad - bw, panelTop + 22, mPanelRect.right - pad, panelTop + 52);
            canvas.drawRoundRect(mRect, 15, 15, mPill);
            canvas.drawText(badge, mRect.left + 14, mRect.bottom - 9, mBadgeText);

            // exercise name
            canvas.drawText(ex, x, panelTop + 134, mExerciseP);

            // big rep counter + label + form score
            canvas.drawText(repStr, x, panelTop + 226, mRepP);
            float rx = x + mRepP.measureText(repStr) + 18;
            canvas.drawText("REPS", rx, panelTop + 226, mSmall);
            if (live && mFormScore >= 0) canvas.drawText("FORM " + mFormScore, rx, panelTop + 194, mFormP);

            // coaching cue chip — coral for a correction, volt for praise / live coaching
            if (live && mCue != null && !mCue.isEmpty()) {
                mCueBg.setColor(mCueWarn ? AMBER : ACCENT);
                mCueText.setColor(mCueWarn ? 0xFFFFFFFF : 0xFF0A0E12);
                float cueTop = mPanelRect.bottom + 14, h = 60;
                float tw = mCueText.measureText(mCue);
                mRect.set(panelL, cueTop, panelL + tw + 2 * pad, cueTop + h);
                canvas.drawRoundRect(mRect, 18, 18, mCueBg);
                canvas.drawText(mCue, panelL + pad, cueTop + 40, mCueText);
            }

            // ---- personal-best celebration (auto-expires) ----
            String pb = mPbText;
            if (pb != null) {
                long now = System.currentTimeMillis();
                if (now < mPbUntilMs) {
                    float remain = (mPbUntilMs - now) / 2600f;                  // 1 → 0
                    int a = (int) (255 * Math.max(0f, Math.min(1f, remain * 1.6f)));
                    float cy = getHeight() * 0.30f;
                    float tw = mPbBig.measureText(pb);
                    float w = Math.max(tw + 64, 300);
                    mRect.set((getWidth() - w) / 2f, cy - 60, (getWidth() + w) / 2f, cy + 22);
                    mPbBg.setAlpha((int) (a * 0.95f));
                    canvas.drawRoundRect(mRect, 24, 24, mPbBg);
                    String head = "NEW PERSONAL BEST";
                    mPbSmall.setAlpha(a);
                    canvas.drawText(head, (getWidth() - mPbSmall.measureText(head)) / 2f, cy - 28, mPbSmall);
                    mPbBig.setAlpha(a);
                    canvas.drawText(pb, (getWidth() - tw) / 2f, cy + 10, mPbBig);
                }
            }
        } finally {
            mLock.unlock();
        }
    }
}
