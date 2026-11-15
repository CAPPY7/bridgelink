package app.bridgelink.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

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
        List<StageFive.Rule> rules = StageFive.addRule(List.of(), "sms_code", "copy_code", "");
        assertEquals("copy_code", StageFive.fire(rules, "sms_code", "1234").get(0).thenAction);
        assertTrue(StageFive.fire(rules, "sms_code", "").isEmpty());
        assertTrue(StageFive.SMS_STEPS.contains("SMS"));
    }
}
