package org.example.coronasniffer;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.example.coronasniffer.BeaconBuilder.bytesToHex;

public class BeaconBuilderTest {
    @Test
    public void testAes128() {
        // cf https://kavaliro.com/wp-content/uploads/2014/03/AES.pdf
        byte[] plainText = "Two One Nine Two".getBytes();
        byte[] key = "Thats my Kung Fu".getBytes();
        byte[] ciphertext = BeaconBuilder.AppleGoogleEN.aes128(key, plainText);
        String expectedHex = "29C3505F571420F6402299B31A02D73A".toLowerCase();
        assertEquals(expectedHex, bytesToHex(ciphertext));
    }

    @Test
    public void testAppleGoogleENRollingProximityIDs() {
        // Temporary Exposure Key
        byte[] tek = BeaconBuilder.AppleGoogleEN.keyFromString("foo");
        int time1 = 0;
        int time2 = 60 * 10 + 1;
        byte[] proxID1 = BeaconBuilder.AppleGoogleEN.rollingProximityID(tek, time1);
        byte[] proxID2 = BeaconBuilder.AppleGoogleEN.rollingProximityID(tek, time2);
        assertEquals("ebaca2b735c90d01c361e8ca6ec167f2", bytesToHex(proxID1));
        assertEquals("a9141c2b822e1dc3fbe916c4ba64c368", bytesToHex(proxID2));
    }
}
