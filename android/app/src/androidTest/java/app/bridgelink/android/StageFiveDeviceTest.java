package app.bridgelink.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import app.bridgelink.android.protocol.StageFive;

/** Runs on the emulator. Texts, folder conflicts, and the trackpad must match the phone build. */
@RunWith(AndroidJUnit4.class)
public class StageFiveDeviceTest {
    @Test
    public void stageFiveOnDevice() {
        assertEquals(80, StageFive.SMS_CAP);
        assertEquals("notes/a (BridgeLink conflict).txt", StageFive.conflictName("notes/a.txt"));
        assertEquals(StageFive.Choice.CONFLICT, StageFive.decide(false, true, "old", false, "new"));
        assertEquals(1, StageFive.pointer("laser", 4, 0)[0]);
        assertFalse(StageFive.pointer("laser", 4, 0) == null);
        assertTrue(StageFive.SMS_STEPS.contains("SMS"));
    }
}
