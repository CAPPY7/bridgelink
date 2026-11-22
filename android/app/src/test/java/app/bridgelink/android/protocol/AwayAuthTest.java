package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

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
}
