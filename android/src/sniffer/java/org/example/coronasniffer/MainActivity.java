package org.example.coronasniffer;

import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;

public class MainActivity extends Activity implements BeaconConsumer {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String BEACON_LAYOUT
            // = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"; // iBeacon
            // = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";  // Eddystone UID
            = "s:0-1=fd6f,i:2-17,d:18-21"; // GAEN with AEM as the "d" field

    private BeaconManager beaconManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!PermissionHelper.hasPermissions(this)) {
            Log.d(TAG, "Request permissions");
            PermissionHelper.requestPermissions(this);
            return;
        }
        ensureBeaconManager();
        beaconManager.bind(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (beaconManager != null) beaconManager.unbind(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (!PermissionHelper.hasPermissions(this)) {
            throw new RuntimeException("Necessary permissions denied");
        }
        ensureBeaconManager();
    }

    private void ensureBeaconManager() {
        if (beaconManager != null) return;
        beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BEACON_LAYOUT));
        BeaconManager.setDebug(true);
    }

    @Override
    public void onBeaconServiceConnect() {
        RangeNotifier rangeNotifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                Log.v(TAG, "didRangeBeaconsInRegion count: " + beacons.size());
                for (Beacon b : beacons) {
                    Log.d(TAG, String.format("GAEN RPI %s, AEM %04x, RSSI %d (mean %g, n obs %d)",
                            b.getId1(),
                            b.getDataFields().get(0),
                            b.getRssi(),
                            b.getRunningAverageRssi(),
                            b.getMeasurementCount()));
                }
            }
        };
        try {
            Log.d(TAG, "Starting AltBeacon ranging");
            beaconManager.startRangingBeaconsInRegion(new Region("dummy-id", null, null, null));
            beaconManager.addRangeNotifier(rangeNotifier);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }
}