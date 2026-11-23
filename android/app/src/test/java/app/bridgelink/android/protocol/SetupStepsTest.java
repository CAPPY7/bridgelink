package app.bridgelink.android.protocol;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SetupStepsTest {
    @Test
    public void pagesFollowTheAndroidVersion() {
        List<String> api26 = SetupSteps.forSdk(26);
        assertEquals("pair", api26.get(0));
        assertFalse(api26.contains("notifications"));
        assertFalse(api26.contains("decline"));
        assertFalse(api26.contains("fullscreen"));
        assertTrue(api26.contains("photos"));

        List<String> api29 = SetupSteps.forSdk(29);
        assertTrue(api29.contains("decline"));
        assertFalse(api29.contains("notifications"));
        assertFalse(api29.contains("fullscreen"));

        List<String> api31 = SetupSteps.forSdk(31);
        assertTrue(api31.contains("bluetooth"));
        assertFalse(api31.contains("notifications"));

        List<String> api33 = SetupSteps.forSdk(33);
        assertTrue(api33.contains("notifications"));
        assertFalse(api33.contains("fullscreen"));

        List<String> api34 = SetupSteps.forSdk(34);
        assertTrue(api34.contains("fullscreen"));
        assertTrue(api34.contains("notifications"));
        assertTrue(api34.contains("decline"));
    }
}
