package app.bridgelink.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import app.bridgelink.android.protocol.StageSix;

/** Runs on the emulator. Mirror, control, and camera rules must match the phone build. */
@RunWith(AndroidJUnit4.class)
public class StageSixDeviceTest {
    @Test
    public void stageSixOnDevice() {
        assertEquals(1280, StageSix.quality("medium").width);
        assertEquals(30, StageSix.quality("medium").fps);
        assertTrue(StageSix.CONTROL_STEPS.contains("Accessibility"));
        assertTrue(StageSix.CONTROL_STEPS.contains("restricted"));
        assertTrue(StageSix.CAM_NOTE.contains("Zoom"));
        assertFalse(StageSix.cameraExtension(false));
        assertTrue(StageSix.stopWhenClosed(false));
        assertFalse(StageSix.flashOn("front", true));
        assertEquals("back", StageSix.control("back", 0, 0, 0, 0, "").kind);
    }
}
