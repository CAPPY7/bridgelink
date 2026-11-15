package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MediaWatchTest {
    @Test
    public void pollsOnlyWhileTheRemotePageIsOpen() {
        assertFalse(MediaWatch.shouldPoll(false, true, false));
        assertFalse(MediaWatch.shouldPoll(true, false, false));
        assertFalse(MediaWatch.shouldPoll(true, true, true));
        assertTrue(MediaWatch.shouldPoll(true, true, false));
        assertEquals("watch", MediaWatch.command("watch")[0]);
        assertEquals("idle", MediaWatch.command(" idle ")[0]);
        assertNull(MediaWatch.command("play"));
    }
}
