package org.example.coronasniffer;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.util.Log;

class BleBroadcast {
    private final static String TAG = BleBroadcast.class.getSimpleName();

    // NOTE: advertisement is BLE jargon for broadcasting and is not related to ads
    private BluetoothLeAdvertiser advertiser;

    private AdvertiseCallback callback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "BLE advertisement started");
            super.onStartSuccess(settingsInEffect);
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "could not start BLE broadcast");
            super.onStartFailure(errorCode);
        }
    };

    void ensureRunning() {
        if (advertiser != null) return;

        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            throw new RuntimeException("failed to obtain advertiser (BLE off?)");
        }

        // iBeacon payload for testing: can be easily read by 3rd party apps
        final AdvertiseData data =
                //BeaconBuilder.IBeacon.random();
                //BeaconBuilder.Eddystone.exampleUID();
                BeaconBuilder.AppleGoogleEN.example();

        final AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        advertiser.startAdvertising(settings, data, callback);
        Log.d(TAG, "started broadcast");
    }

    void ensureStopped() {
        if (advertiser == null) return;

        advertiser.stopAdvertising(callback);
        advertiser = null;

        Log.d(TAG, "stopped broadcast");
    }
}