package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OtpGateTest {
    @Test
    public void theSameCodeWaitsAMinute() {
        assertTrue(OtpGate.fresh("", 0, "482193", 1_000));
        assertFalse(OtpGate.fresh("482193", 1_000, "482193", 30_000));
        assertTrue(OtpGate.fresh("482193", 1_000, "482193", 61_000));
        assertTrue(OtpGate.fresh("482193", 1_000, "111111", 2_000));
        assertFalse(OtpGate.fresh("482193", 1_000, "", 2_000));
    }
}
