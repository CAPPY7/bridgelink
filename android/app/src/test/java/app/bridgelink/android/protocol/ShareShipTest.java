package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class ShareShipTest {
    @Test
    public void aBrokenShareExtensionIsNotShipped() {
        assertFalse(ShareShip.bundleBrokenDylib());
    }
}
