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
        // Rolling Proximity Identifier Key
        byte[] rpik = BeaconBuilder.AppleGoogleEN.keyFromString("foo");
        int time1 = 0;
        int time2 = 60 * 10 + 1;
        byte[] proxID1 = BeaconBuilder.AppleGoogleEN.rollingProximityID(rpik, time1);
        byte[] proxID2 = BeaconBuilder.AppleGoogleEN.rollingProximityID(rpik, time2);
        assertEquals("7ebc4679238b6dc389bf322e6c8712a7", bytesToHex(proxID1));
        assertEquals("b50f064dfa20c6e31b0bdd1f92cf755d", bytesToHex(proxID2));
    }
}
