package app.bridgelink.android.protocol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Picture frames must not queue without a limit. */
public class MediaQueueTest {
    private static byte[] frame(int flags) {
        byte[] packed = StageSix.pack(1, flags, 0, 1, new byte[]{1, 2, 3});
        return Frame.encode(Frame.BINARY, packed);
    }

    @Test
    public void aFullQueueDropsNonKeyFramesAndAsksForAKey() {
        MediaQueue queue = new MediaQueue();
        for (int i = 0; i < MediaQueue.CAP; i++) {
            MediaQueue.Result kept = queue.offer(frame(0), false);
            assertTrue(kept.kept);
            assertFalse(kept.askKey);
        }
        MediaQueue.Result dropped = queue.offer(frame(0), false);
        assertFalse(dropped.kept);
        assertTrue(dropped.askKey);
        assertEquals(MediaQueue.CAP, queue.size());
        MediaQueue.Result key = queue.offer(frame(1), true);
        assertTrue(key.kept);
        assertFalse(key.askKey);
        assertEquals(MediaQueue.CAP, queue.size());
        assertTrue(MediaQueue.keyFrame(frame(1)));
        assertFalse(MediaQueue.keyFrame(frame(0)));
    }

    @Test
    public void aFullLaneDropsAWholeFrameAndLeavesTheMicAlone() {
        MediaQueue queue = new MediaQueue();
        for (int i = 0; i < MediaQueue.CAP; i++) {
            assertTrue(queue.offer(piece(1, 0, 0, 1), false).kept);
        }
        MediaQueue.Result dropped = queue.offer(piece(1, 0, 0, 2), false);
        assertTrue(dropped.kept);
        MediaQueue.Result rest = queue.offer(piece(1, 0, 1, 2), false);
        assertFalse(rest.kept);
        assertTrue(rest.askKey);
        assertEquals(MediaQueue.CAP, queue.size(1));
        assertTrue(queue.offer(piece(3, 0, 0, 1), false).kept);
        assertEquals(1, queue.size(3));
        assertEquals(MediaQueue.CAP + 1, queue.size());
    }

    private static byte[] piece(int stream, int flags, int part, int parts) {
        byte[] packed = StageSix.pack(stream, flags, part, parts, new byte[]{1, 2, 3});
        return Frame.encode(Frame.BINARY, packed);
    }
}
