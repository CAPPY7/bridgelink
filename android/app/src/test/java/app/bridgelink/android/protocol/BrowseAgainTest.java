package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrowseAgainTest {
    @Test
    public void browseStartsAfterTwoMissesAndKeepsTheSavedKey() {
        assertFalse(BrowseAgain.shouldBrowse(1));
        assertTrue(BrowseAgain.shouldBrowse(2));
        assertTrue(BrowseAgain.sameMac("Ab", "ab"));
        assertFalse(BrowseAgain.sameMac("ab", "cd"));
        assertFalse(BrowseAgain.sameMac("", "ab"));
    }
}
