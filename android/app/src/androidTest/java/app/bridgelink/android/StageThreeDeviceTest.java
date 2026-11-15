package app.bridgelink.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import app.bridgelink.android.protocol.StageRules;
import app.bridgelink.android.protocol.Transfers;

/** Runs on the emulator. The cap and the silent-ring rule must match the phone build. */
@RunWith(AndroidJUnit4.class)
public class StageThreeDeviceTest {
    @Test
    public void stageThreeOnDevice() {
        assertEquals(4L * 1024 * 1024 * 1024, Transfers.MAX_BYTES);
        assertTrue(StageRules.findUsesAlarmStream());
        assertEquals(60_000L, StageRules.OTP_CLEAR_MS);
    }
}
