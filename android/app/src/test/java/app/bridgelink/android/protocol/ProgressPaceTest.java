package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProgressPaceTest {
    @Test
    public void sendingLinesWaitASecondUnlessFinished() {
        assertFalse(ProgressPace.show(1_000, 1_000, 0));
        assertFalse(ProgressPace.show(1_000, 1_500, 40));
        assertTrue(ProgressPace.show(1_000, 2_000, 40));
        assertTrue(ProgressPace.show(5_000, 5_100, 100));
    }
}
