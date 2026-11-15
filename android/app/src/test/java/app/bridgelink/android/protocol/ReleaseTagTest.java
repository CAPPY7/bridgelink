package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReleaseTagTest {
    @Test
    public void theReleaseTagMustMatchTheVersion() {
        assertTrue(ReleaseCheck.tagMatches("v1.0.0", "1.0.0"));
        assertFalse(ReleaseCheck.tagMatches("v0.13.0", "1.0.0"));
        assertFalse(ReleaseCheck.tagMatches("1.0.0", "1.0.0"));
        assertFalse(ReleaseCheck.installsWithSparkle());
    }
}
