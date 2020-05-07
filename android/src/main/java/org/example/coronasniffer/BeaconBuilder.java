package org.example.coronasniffer;
import android.bluetooth.le.AdvertiseData;
import android.os.ParcelUuid;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import at.favre.lib.crypto.HKDF;

class BeaconBuilder {
    private final static String TAG = BeaconBuilder.class.getSimpleName();

    static ParcelUuid parcelUUIDFrom16BitUUID(int uuid16) {
        return ParcelUuid.fromString(String.format("0000%04x-0000-1000-8000-00805F9B34FB", uuid16));
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)  sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static byte[] fixedLengthKeyFromString(String str, int length) {
        byte[] key = new byte[length];
        byte[] strBytes = str.getBytes();
        if (strBytes.length > key.length)
            throw new IllegalArgumentException("'" + str + "' is too long to be used as a key");
        System.arraycopy(strBytes, 0, key, 0, strBytes.length);
        return key;
    }

    /**
     * Apple|Google Exposure Notification protocol (v1.2)
     */
    public static class AppleGoogleEN {
        private static final int SERVICE_UUID_16 = 0xFD6F;
        private static final int KEY_LENGTH_BYTES = 16;

        public static AdvertiseData example() {
            // note: the key should be regenerated every 10 minutes
            return build(
                    keyFromString("example"),
                    System.currentTimeMillis() / 1000);
        }

        static byte[] keyFromString(String str) {
            return fixedLengthKeyFromString(str, KEY_LENGTH_BYTES);
        }

        static byte[] paddedData(int enInterval) {
            ByteBuffer padded = ByteBuffer.allocate(KEY_LENGTH_BYTES);
            padded.put("EN-RPI".getBytes());
            for (int i=6; i<=11; ++i) padded.put((byte)0);
            padded.order(ByteOrder.LITTLE_ENDIAN);
            padded.putInt(enInterval);
            return padded.array();
        }

        static byte[] aes128(byte[] key, byte[] data) {
            try {
                final Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
                byte[] zeroIV = new byte[KEY_LENGTH_BYTES];
                cipher.init(Cipher.ENCRYPT_MODE,
                        new SecretKeySpec(key, "AES"),
                        new IvParameterSpec(zeroIV));
                return cipher.doFinal(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /** Rolling Proximity ID (RPI), based on RPIK and time */
        static byte[] rollingProximityID(byte[] temporaryExposureKey, long unixTime) {
            byte[] rpik = rollingProximityIdentifierKeyFromTEK(temporaryExposureKey);
            final int enInterval = (int)(unixTime / (60 * 10));
            byte[] data = paddedData(enInterval);
            byte[] proxId = aes128(rpik, data);

            return proxId;
        }

        static byte[] buildPayload(byte[] proxID, byte[] aem) {
            byte[] payload = new byte[20];
            System.arraycopy(proxID, 0, payload, 0, KEY_LENGTH_BYTES);
            System.arraycopy(aem, 0, payload, KEY_LENGTH_BYTES, 4);
            return payload;
        }

        static byte[] rollingProximityIdentifierKeyFromTEK(byte[] tek) {
            final SecretKey salt = null;
            byte[] prk = HKDF.fromHmacSha256().extract(salt, tek);
            return HKDF.fromHmacSha256().expand(prk, "EN-RPIK".getBytes(), KEY_LENGTH_BYTES);
        }

        public static AdvertiseData build(byte[] temporaryExposureKey, long unixTime) {
            byte[] rpi = rollingProximityID(temporaryExposureKey, unixTime);

            Log.i(TAG, "Contact tracing exposureKey " + bytesToHex(temporaryExposureKey)
                    + ", unixTime " + unixTime + " -> RPI " + bytesToHex(rpi));

            // TODO: zero AEM for now
            byte[] aem = new byte[4];
            byte[] payload = buildPayload(rpi, aem);

            Log.d(TAG, "Contact tracing payload " + bytesToHex(payload));

            final ParcelUuid SERVICE_UUID = parcelUUIDFrom16BitUUID(SERVICE_UUID_16);
            return new AdvertiseData.Builder()
                    .addServiceData(SERVICE_UUID, payload)
                    .addServiceUuid(SERVICE_UUID)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build();
        }
    }

    /**
     * DP-3T protocol beacons, see
     * https://github.com/DP-3T/dp3t-sdk-android/blob/master-alpha/dp3t-sdk/sdk/src/main/java/org/dpppt/android/sdk/internal/gatt/BleServer.java
     */
    public static class DP3T {
        private static final int SERVICE_UUID_16 = 0xFD68;
        private static final int KEY_LENGTH_BYTES = 16;
        private static final int EPOCHS_PER_DAY = 24 * 4;

        public static AdvertiseData example() {
            // note: should be rotated from time to time
            return build(keyFromString("example"), 1);
        }

        static byte[] keyFromString(String str) {
            return fixedLengthKeyFromString(str, KEY_LENGTH_BYTES);
        }

        static byte[] generateEphId(byte[] secretKey, int epochIdx) {
            if (epochIdx < 0 || epochIdx >= EPOCHS_PER_DAY) {
                throw new IllegalArgumentException("invalid epoch number");
            }
            try {
                Mac mac = Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
                mac.update("broadcast key".getBytes());
                byte[] prf = mac.doFinal();
                SecretKeySpec keySpec = new SecretKeySpec(prf, "AES");
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                byte[] zeros = new byte[KEY_LENGTH_BYTES];
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(zeros));
                byte[] result = zeros;
                for (int i = 0; i <= epochIdx; i++) result = cipher.update(zeros);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public static AdvertiseData build(byte[] secretKey, int epochIdx) {
            final ParcelUuid SERVICE_UUID = parcelUUIDFrom16BitUUID(SERVICE_UUID_16);

            byte[] ephId = generateEphId(secretKey, epochIdx);
            Log.i(TAG, "DP-3T secretKey " + bytesToHex(secretKey)
                    + ", daily epoch number " + epochIdx + " -> ephId " + bytesToHex(ephId));

            return new AdvertiseData.Builder()
                    .addServiceData(SERVICE_UUID, ephId)
                    .addServiceUuid(SERVICE_UUID)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build();
        }
    }

    /**
     * For reference: Eddystone UID payload. This is closer to contact the contact tracing
     * payload than iBeacon. The contact tracing message actually looks like a new Eddystone
     * frame type (in addition to the existing UUID, URL and TLM types). Then only differences
     * are the advertisement flags (0x06 vs 0x1A) and the 16-bit service UUID (0xFEAA vs 0xFD6F)
     */
    public static class Eddystone {
        private static final int SERVICE_UUID_16 = 0xFEAA;
        public static AdvertiseData exampleUID() {
            long nid = 333;
            int bid = 444;
            ByteBuffer nidBid = ByteBuffer.allocate(16);
            nidBid.putLong(nid);
            nidBid.putShort((short)0);
            nidBid.putInt(bid);
            nidBid.putShort((short)0);
            byte[] payload = nidBid.array();
            Log.i(TAG, "Example Eddystone UID " + bytesToHex(payload));
            return buildUID(payload, (byte)0);
        }

        private static byte[] buildUIDPayload(byte[] nidAndBid, byte txPower) {
            assert(nidAndBid.length == 16);
            byte[] payload = new byte[20];
            payload[0] = 0x0; // type = UID
            payload[1] = txPower;
            System.arraycopy(nidAndBid, 0, payload, 2, nidAndBid.length);
            // two last bytes (RFU) left as 0
            return payload;
        }

        public static AdvertiseData buildUID(byte[] nidAndBid, byte txPower) {
            final ParcelUuid SERVICE_UUID = parcelUUIDFrom16BitUUID(SERVICE_UUID_16);
            return new AdvertiseData.Builder()
                    .addServiceData(SERVICE_UUID, buildUIDPayload(nidAndBid, txPower))
                    .addServiceUuid(SERVICE_UUID)
                    .setIncludeTxPowerLevel(false)
                    .setIncludeDeviceName(false)
                    .build();
        }
    }

    /** For reference & debugging purposes: iBeacon payloads */
    public static class IBeacon {
        public static AdvertiseData random() {
            UUID proximityUUID = UUID.randomUUID();
            short majorCode = 111;
            short minorCode = 222;

            Log.i(TAG, "random iBeacon payload: uuid = "
                    + proximityUUID + ", major = " + majorCode + ", minor = " + minorCode);
            return build(proximityUUID, minorCode, majorCode);
        }

        public static AdvertiseData build(UUID proximityUUID, short majorCode, short minorCode) {
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
}
