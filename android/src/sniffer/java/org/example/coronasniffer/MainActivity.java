package org.example.coronasniffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.RemoteException;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.bosphere.filelogger.FLConfig;
import com.bosphere.filelogger.FLConst;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.File;
import java.util.Collection;

public class MainActivity extends Activity implements BeaconConsumer {
    private static final String BEACON_LAYOUT
            // = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"; // iBeacon
            // = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";  // Eddystone UID
            = "s:0-1=fd6f,i:2-17,d:18-21"; // GAEN with AEM as the "d" field

    private BeaconManager beaconManager;
    private TextView countView, rssiView;
    private Region region = new Region("dummy-id", null, null, null);

    private RangeNotifier rangeNotifier = new RangeNotifier() {
        @SuppressLint("DefaultLocale")
        @Override
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            FL.v("didRangeBeaconsInRegion count: " + beacons.size());
            countView.setText(String.format("%d", beacons.size()));
            for (Beacon b : beacons) {
                FL.i(String.format("GAEN RPI %s, AEM %04x, RSSI %d (mean %g, n obs %d)",
                        b.getId1(),
                        b.getDataFields().get(0),
                        b.getRssi(),
                        b.getRunningAverageRssi(),
                        b.getMeasurementCount()));
            }
            Beacon nearest = maxRssiBeacon(beacons);
            if (nearest == null) {
                rssiView.setText("");
            } else {
                rssiView.setText(String.format("Nearest mean RSSI: %d dBm\nRolling ID: %s",
                        Math.round(nearest.getRunningAverageRssi()),
                        nearest.getId1()));
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        countView = findViewById(R.id.count_view);
        rssiView = findViewById(R.id.rssi_view);

        File logdir = new File(getExternalCacheDir(), "logs");
        FL.init(new FLConfig.Builder(this)
                .minLevel(FLConst.Level.V)
                .logToFile(true)
                .dir(logdir)
                .defaultTag(MainActivity.class.getSimpleName())
                .retentionPolicy(FLConst.RetentionPolicy.NONE)
                .build());
        FL.setEnabled(true);
        FL.d("logging to " + logdir.getAbsolutePath());
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!PermissionHelper.hasPermissions(this)) {
            FL.d("Request permissions");
            PermissionHelper.requestPermissions(this);
            return;
        }
        ensureBeaconManager();
        beaconManager.bind(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (beaconManager != null) {
            try {
                beaconManager.stopMonitoringBeaconsInRegion(region);
            } catch (RemoteException e) {
                FL.w("Failed to stop scanning", e);
            }
            beaconManager.removeRangeNotifier(rangeNotifier);
            beaconManager.unbind(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        FL.d( "onRequestPermissionsResult");
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
        // BeaconManager.setDebug(true);
    }

    @Override
    public void onBeaconServiceConnect() {
        try {
            FL.d("Starting AltBeacon ranging");
            beaconManager.startRangingBeaconsInRegion(region);
            beaconManager.addRangeNotifier(rangeNotifier);
        } catch (RemoteException e) {
            FL.e( "Failed to start scanning", e);
        }
    }

    private static Beacon maxRssiBeacon(Collection<Beacon> beacons) {
        // unfortunately, this is still the simples implementation of "array.maxBy(x -> x.rssi)"
        // in common Android Java versions
        double maxRssi = -1000;
        Beacon maxElem = null;
        for (Beacon b : beacons) {
            double rssi = b.getRunningAverageRssi();
            if (rssi > maxRssi) {
                maxRssi = rssi;
                maxElem = b;
            }
        }
        return maxElem;
    }
}