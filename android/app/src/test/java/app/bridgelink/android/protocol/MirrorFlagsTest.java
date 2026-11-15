package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MirrorFlagsTest {
    @Test
    public void mirroringDoesNotWipeOtherSettings() {
        String flags = MirrorFlags.apply("folder=1,input=1,auto=1", true);
        assertTrue(flags.contains("folder=1"));
        assertTrue(flags.contains("input=1"));
        assertTrue(flags.contains("auto=1"));
        assertTrue(flags.contains("notify=1"));
        assertTrue(flags.contains("otp=1"));
        String off = MirrorFlags.apply(flags, false);
        assertTrue(off.contains("folder=1"));
        assertTrue(off.contains("notify=0"));
        assertTrue(off.contains("otp=0"));
    }
}
