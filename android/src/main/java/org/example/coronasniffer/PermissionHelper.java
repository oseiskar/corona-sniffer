package org.example.coronasniffer;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper to ask the user permission to use BLE and location.
 */
class PermissionHelper {
    private static final String[] PERMISSIONS;
    static {
        List<String> p = new ArrayList<>();
        p.add(Manifest.permission.ACCESS_FINE_LOCATION);
        p.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        p.add(Manifest.permission.BLUETOOTH);
        p.add(Manifest.permission.BLUETOOTH_ADMIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            p.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        PERMISSIONS = p.toArray(new String[0]);
    }

    private static final int PERMISSION_REQUEST_CODE = 0;

    public static boolean hasPermissions(Activity activity) {
        for (String p : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, p) != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    public static void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, PERMISSIONS, PERMISSION_REQUEST_CODE);
    }
}