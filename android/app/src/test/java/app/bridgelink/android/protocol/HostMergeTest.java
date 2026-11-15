package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HostMergeTest {
    @Test
    public void connectKeepsTheOtherAddresses() {
        assertEquals("10.0.0.2,10.0.0.8", HostMerge.merge("10.0.0.2,10.0.0.8", "10.0.0.2"));
        assertEquals("10.0.0.2,10.0.0.9", HostMerge.merge("10.0.0.2", "10.0.0.9"));
    }
}
