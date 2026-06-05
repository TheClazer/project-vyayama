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

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    static { System.loadLibrary("posedetectionYoloNAS"); }

    public static char runtime_var = 'D';   // default = DSP = Hexagon NPU

    private static final int M_NPU = 1, M_GPU = 2, M_CPU = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProfileStore.init(this);
        setContentView(R.layout.main_activity);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateBar();
        OpenCVLoader.initDebug();
    }

    @Override
    protected void onResume() {
        super.onResume();
        overToCamera(runtime_var);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu sub = menu.addSubMenu("Engine");
        sub.add(0, M_NPU, 0, "NPU  (Hexagon)");
        sub.add(0, M_GPU, 0, "GPU  (Adreno)");
        sub.add(0, M_CPU, 0, "CPU");
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);  // lives in the 3-dot overflow
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
        getSupportActionBar().setTitle("Vyāyāma");
        getSupportActionBar().setSubtitle((prof.isEmpty() ? "" : prof + "  ·  ") + "Coach · " + engineName(runtime_var));
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
}
