package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** One away sign-in wait at a time. A second post replaces the first. */
public class AwayAuthTest {
    private static final class Fake implements AwayAuth.Sink {
        Runnable pending;
        int writes;

        @Override
        public void writeAuth() {
            writes++;
        }

        @Override
        public void post(Runnable task, long delayMs) {
            if (pending != null) throw new AssertionError("two retry loops");
            pending = task;
        }

        @Override
        public void remove(Runnable task) {
            if (pending == task) pending = null;
        }
    }

    @Test
    public void onlyOneRetryLoop() {
        Fake fake = new Fake();
        AwayAuth auth = new AwayAuth(fake);
        auth.start();
        assertEquals(1, fake.writes);
        Runnable loop = fake.pending;
        auth.start();
        assertEquals(2, fake.writes);
        assertSame(loop, fake.pending);
        fake.pending.run();
        assertEquals(3, fake.writes);
        assertSame(loop, fake.pending);
    }

    @Test
    public void phoneJoinsFirstMacLaterThenStops() {
        Fake fake = new Fake();
        AwayAuth auth = new AwayAuth(fake);
        auth.start();
        assertEquals(1, fake.writes);
        auth.macJoined();
        assertEquals(2, fake.writes);
        assertEquals(1, pendingCount(fake));
        auth.ok();
        assertTrue(auth.accepted());
        assertNull(fake.pending);
        assertEquals(2, fake.writes);

        Fake gone = new Fake();
        AwayAuth forgot = new AwayAuth(gone);
        forgot.start();
        forgot.forgot();
        assertTrue(forgot.forgotten());
        assertNull(gone.pending);
        int held = gone.writes;
        forgot.macJoined();
        assertEquals(held, gone.writes);
    }

    private static int pendingCount(Fake fake) {
        return fake.pending == null ? 0 : 1;
    }
}
