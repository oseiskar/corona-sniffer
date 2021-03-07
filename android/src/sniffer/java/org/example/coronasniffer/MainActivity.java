package org.example.coronasniffer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bosphere.filelogger.FL;
import com.bosphere.filelogger.FLConfig;
import com.bosphere.filelogger.FLConst;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {
    private static final String BEACON_LAYOUT
            // = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"; // iBeacon
            // = "s:0-1=feaa,m:2-2=00,p:3-3:-41,i:4-13,i:14-19";  // Eddystone UID
            = "s:0-1=fd6f,i:2-17,d:18-21"; // GAEN with AEM as the "d" field

    private BeaconStats stats = new BeaconStats();
    private BeaconManager beaconManager;
    private TextView countView, rssiView;
    private Region region = new Region("dummy-id", null, null, null);
    private FusedLocationProviderClient locationProvider;
    private File privateLogDir;

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

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult r) {
            Location location = r.getLastLocation();
            FL.d("onLocationChanged %f, %f (%g m)",
                    location.getLatitude(), location.getLongitude(), location.getAccuracy());
            stats.onLocationChanged(location);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        countView = findViewById(R.id.count_view);
        rssiView = findViewById(R.id.rssi_view);

        // logging
        privateLogDir = new File(getExternalCacheDir(), "logs");
        FL.init(new FLConfig.Builder(this)
                //.minLevel(FLConst.Level.V)
                .minLevel(FLConst.Level.D)
                .logToFile(true)
                .dir(privateLogDir)
                .defaultTag(MainActivity.class.getSimpleName())
                .retentionPolicy(FLConst.RetentionPolicy.NONE)
                .build());
        FL.setEnabled(true);
        FL.d("logging to " + privateLogDir.getAbsolutePath());

        locationProvider = LocationServices.getFusedLocationProviderClient(this);
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which){
                    case DialogInterface.BUTTON_POSITIVE:
                        AsyncTask.execute(new Runnable() {
                            @Override
                            public void run() {
                                exportLogs();
                            }
                        });
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        switch (item.getItemId()) {
            case R.id.export_logs:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(
                        "This will copy the sensitive logs to the Documents folder, " +
                        "which is accessible to all other apps too. Deleting them as soon " +
                        "as not needed anymore is highly recommended.")
                        .setPositiveButton("Do it!", dialogClickListener)
                        .setNegativeButton("Cancel", dialogClickListener).show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        // location listener
        try {
            LocationRequest locationRequest = LocationRequest.create()
                    .setFastestInterval(5 * 1000) // ms
                    .setInterval(30 * 1000) // ms
                    .setSmallestDisplacement(30f) // meters
                    .setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                    //.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            locationProvider.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
        } catch (SecurityException e) {
            throw new RuntimeException("permissions altered while app running", e);
        }
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
        if (locationProvider != null) {
            locationProvider.removeLocationUpdates(locationCallback);
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

    // Note: if not running Android Q+, you probably don't need this and can just copy files using ADB
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void exportLogs() {
        String storePath = Environment.DIRECTORY_DOCUMENTS + "/" + getPackageName() + "/logs";
        FL.d("exporting files to " + storePath);
        for (File f : privateLogDir.listFiles()) {
            FL.d("exporting " + f.getAbsolutePath());
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, f.getName());
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, storePath);
            cv.put(MediaStore.MediaColumns.DATA, f.getAbsolutePath());
            Uri uri = getContentResolver().insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), cv);
            try (
                OutputStream outStream = getContentResolver().openOutputStream(uri, "w");
                InputStream inputStream = new FileInputStream(f))
            {
                FileUtils.copy(inputStream, outStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}