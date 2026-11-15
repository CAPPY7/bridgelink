package app.bridgelink.android.protocol;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import app.bridgelink.android.MirrorStop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Stopping the mirror must not release the encoder while drain is still in it. */
public class MirrorStopTest {
    @Test
    public void releaseWaitsUntilTheDrainThreadFinishes() throws Exception {
        AtomicInteger phase = new AtomicInteger();
        AtomicInteger releasedAt = new AtomicInteger(-1);
        CountDownLatch entered = new CountDownLatch(1);
        Thread drain = new Thread(() -> {
            phase.set(1);
            entered.countDown();
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            phase.set(2);
        }, "mirror-drain");
        drain.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));
        MirrorStop.stopEncoder(drain, 2_000, () -> releasedAt.set(phase.get()));
        assertEquals(2, releasedAt.get());
        assertFalse(drain.isAlive());
    }

    @Test
    public void aCodecErrorOnStopDoesNotEscape() {
        AtomicBoolean ran = new AtomicBoolean();
        MirrorStop.stopEncoder(null, 10, () -> {
            ran.set(true);
            throw new IllegalStateException("codec already released");
        });
        assertTrue(ran.get());
    }
}
