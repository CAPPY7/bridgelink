package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LockPlanTest {
    @Test
    public void lockDoesNotWriteSystemSettings() {
        assertFalse(LockPlan.writesSettings());
        assertEquals("/usr/bin/pmset", LockPlan.command()[0]);
        assertEquals("displaysleepnow", LockPlan.command()[1]);
        assertTrue(LockPlan.note(false).contains("Require password"));
        assertEquals("Locked the screen", LockPlan.note(true));
    }
}
