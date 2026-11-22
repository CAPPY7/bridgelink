package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressPaceTest {
    @Test
    public void sendingLinesWaitASecondUnlessFinished() {
        assertTrue(ProgressPace.show(0, 0, 0));
        assertFalse(ProgressPace.show(0, 500, 40));
        assertTrue(ProgressPace.show(0, 1000, 40));
        assertTrue(ProgressPace.show(5000, 5100, 100));
    }
}
