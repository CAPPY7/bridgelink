package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MacBookTest {
    @Test
    public void eachMacKeepsItsOwnKey() {
        String tokens = MacTokens.put("{}", "aa", "token-for-mac-aaa1");
        tokens = MacTokens.put(tokens, "bb", "token-for-mac-bbb2");
        String list = MacList.add("[]", "Office", "10.0.0.2", 8787, "aa", "mac-a");
        list = MacList.add(list, "Home", "10.0.0.3", 8787, "bb", "mac-b");
        MacList.Saved home = MacList.findByName(list, "home");
        assertEquals("10.0.0.3", home.host);
        assertEquals("bb", home.fp);
        assertEquals("mac-b", home.deviceId);
        assertEquals("token-for-mac-bbb2", MacTokens.tokenFor(tokens, home.fp));
        assertTrue(MacBook.known(tokens, "aa"));
        assertFalse(MacBook.known(tokens, "cc"));
        assertEquals("token-for-mac-aaa1", MacTokens.tokenFor(tokens, "aa"));
    }
}
