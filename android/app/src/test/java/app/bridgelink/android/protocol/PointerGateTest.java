package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PointerGateTest {
    @Test
    public void theAlertShowsOnceUntilAccessibilityIsOn() {
        assertTrue(PointerGate.shouldAlert(false, false));
        assertFalse(PointerGate.shouldAlert(true, false));
        assertFalse(PointerGate.shouldAlert(false, true));
    }
}
