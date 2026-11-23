package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DndFollowTest {
    @Test
    public void toggleFollowsThePhoneReplyNotTheGuess() {
        assertFalse(DndFollow.shown(true, false));
        assertTrue(DndFollow.shown(false, true));
        assertTrue(DndFollow.needAccess().contains("Allow Do Not Disturb access on the phone"));
    }
}
