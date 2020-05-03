package org.example.coronasniffer;
import android.bluetooth.le.AdvertiseData;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.UUID;

class BeaconBuilder {
    private final static String TAG = BeaconBuilder.class.getSimpleName();

    public static AdvertiseData randomIBeacon() {
        UUID proximityUUID = UUID.randomUUID();
        short majorCode = 111;
        short minorCode = 222;

        Log.i(TAG, "random iBeacon payload: uuid = "
                + proximityUUID + ", major = " + majorCode + ", minor = " + minorCode);
        return iBeacon(proximityUUID, minorCode, majorCode);
    }

    public static AdvertiseData iBeacon(UUID proximityUUID, short majorCode, short minorCode) {
        ByteBuffer data = ByteBuffer.allocate(23); // 23 bytes if not using txPower

        data.put((byte) 0x02); // iBeacon type
        data.put((byte) 0x15); // length

        data.putLong(proximityUUID.getMostSignificantBits());
        data.putLong(proximityUUID.getLeastSignificantBits());
        data.putShort(majorCode);
        data.putShort(minorCode);

        final int manufacturerId = 76; // Apple
        return new AdvertiseData.Builder()
                .addManufacturerData(manufacturerId, data.array()) // 76 = Apple (for iBeacon)
                .setIncludeTxPowerLevel(false)
                .setIncludeDeviceName(false)
                .build();
    }
}
