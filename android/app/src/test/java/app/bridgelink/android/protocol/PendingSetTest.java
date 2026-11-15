package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PendingSetTest {
    @Test
    public void aSecondWaitingConnectionDoesNotDropTheFirst() {
        PendingSet set = new PendingSet();
        set.add("one");
        set.add("two");
        assertTrue(set.contains("one"));
        assertTrue(set.contains("two"));
        assertEquals(2, set.count());
        set.remove("two");
        assertTrue(set.contains("one"));
        assertEquals(1, set.count());
    }
}
