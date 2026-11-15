package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacLeaveTest {
    @Test
    public void theMacLocksOnlyAfterTheLinkHasBeenGone() {
        assertFalse(MacLeave.shouldLock(true, false, false, 1000, 30_000));
        assertTrue(MacLeave.shouldLock(true, false, false, 30_000, 30_000));
        assertFalse(MacLeave.shouldLock(true, false, true, 60_000, 30_000));
        assertFalse(MacLeave.shouldLock(true, true, false, 60_000, 30_000));
        assertFalse(MacLeave.shouldLock(false, false, false, 60_000, 30_000));
    }
}
