package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SafePathTest {
    @Test
    public void aHiddenPartIsRefused() {
        assertEquals("photos/cat.jpg", Features.safeRelative("photos/cat.jpg"));
        assertNull(Features.safeRelative("photos/.seen"));
        assertNull(Features.safeRelative(".hidden"));
        assertNull(Features.safeRelative("photos/../secret"));
    }
}
