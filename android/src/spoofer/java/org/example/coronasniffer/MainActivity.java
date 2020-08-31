package org.example.coronasniffer;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

public class MainActivity extends Activity {
    BleBroadcast bleBroadcast;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bleBroadcast = new BleBroadcast();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!PermissionHelper.hasPermissions(this)) {
            PermissionHelper.requestPermissions(this);
            return;
        }
        bleBroadcast.ensureRunning();
    }

    @Override
    protected void onPause() {
        super.onPause();
        bleBroadcast.ensureStopped();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!PermissionHelper.hasPermissions(this)) {
            throw new RuntimeException("Necessary permissions denied");
        }
        bleBroadcast.ensureRunning();
    }
}