package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class FindStopTest {
    @Test
    public void stopRingingAlsoTellsTheMac() {
        assertTrue(FindStop.tellsMac());
    }
}
