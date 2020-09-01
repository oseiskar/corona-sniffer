package org.example.coronasniffer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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

    private BeaconStats stats = new BeaconStats();
    private BeaconManager beaconManager;
    private TextView countView, rssiView;
    private Region region = new Region("dummy-id", null, null, null);

    private RangeNotifier rangeNotifier = new RangeNotifier() {
        @SuppressLint("DefaultLocale")
        @Override
        public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
            BeaconStats.Entry nearest = stats.add(beacons);
            countView.setText(String.format("%d", stats.getNearbyDeviceCount()));
            if (nearest == null) {
                rssiView.setText("");
            } else {
                rssiView.setText(String.format("Strongest RSSI: %d dBm\nRolling ID: %s",
                        Math.round(nearest.maxRssi),
                        nearest.rpi));
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        countView = findViewById(R.id.count_view);
        rssiView = findViewById(R.id.rssi_view);

        // logging
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

        ensureScanning();
    }

    private void ensureScanning() {
        if (beaconManager != null) return;
        beaconManager = org.altbeacon.beacon.BeaconManager.getInstanceForApplication(this);
        if (beaconManager.isAnyConsumerBound()) return;

        // AltBeacon foreground service
        beaconManager.enableForegroundServiceScanning(buildForegroundServiceNotification(), 112233);
        beaconManager.setEnableScheduledScanJobs(false);
        beaconManager.setBackgroundBetweenScanPeriod(0);
        beaconManager.setBackgroundScanPeriod(1100);

        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout(BEACON_LAYOUT));
        // BeaconManager.setDebug(true);

        beaconManager.bind(this);
    }

    private void stopScanning() {
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
    protected void onDestroy() {
        super.onDestroy();
        stats.flush();
        stopScanning();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        FL.d( "onRequestPermissionsResult");
        if (!PermissionHelper.hasPermissions(this)) {
            throw new RuntimeException("Necessary permissions denied");
        }

        ensureScanning();
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

    private Notification buildForegroundServiceNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.drawable.ic_fg_service)
                .setContentTitle("BLE scanning active")
                .setOngoing(true)
                .setContentIntent(
                        PendingIntent.getActivity(this, 0, intent, 0));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("dummy-id-2",
                    "dummy-name", NotificationManager.IMPORTANCE_LOW); // no alert sound
            channel.setDescription("dummy-descr");
            NotificationManager notificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManager == null)
                throw new RuntimeException("could not create fg service");

            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(channel.getId());
        }
        return builder.build();
    }
}