package app.bridgelink.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import app.bridgelink.android.protocol.StageFour;

/** Runs on the emulator. Leave-lock and clipboard history must match the phone build. */
@RunWith(AndroidJUnit4.class)
public class StageFourDeviceTest {
    @Test
    public void stageFourOnDevice() {
        assertEquals(100, StageFour.CLIP_CAP);
        assertFalse(StageFour.lockRequest("unlock"));
        assertTrue(StageFour.lockRequest("away"));
        assertTrue(StageFour.shouldLock(true, -90, true, -70, 30_000, 30_000, false, false, false));
        assertFalse(StageFour.shouldLock(true, -90, true, -70, 30_000, 30_000, true, false, false));
        assertEquals("5-c21", StageFour.clipId("ab", 5));
    }
}
