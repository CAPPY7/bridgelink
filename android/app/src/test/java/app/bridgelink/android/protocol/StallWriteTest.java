package app.bridgelink.android.protocol;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** A peer that stops reading must not freeze the thread that closes the socket. */
public class StallWriteTest {
    @Test
    public void closeReturnsWhileThePeerIsNotReading() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            Socket client = new Socket();
            client.connect(server.getLocalSocketAddress(), 3000);
            client.setSendBufferSize(1024);
            Socket peer = server.accept();
            peer.setReceiveBufferSize(1024);
            FrameChannel channel = new FrameChannel(client, 20_000);
            Thread writer = new Thread(() -> {
                byte[] blob = new byte[64 * 1024];
                try {
                    while (channel.writeBlockedForMs() < 200) channel.write(blob);
                } catch (Exception ignored) {
                }
            }, "stall-writer");
            writer.setDaemon(true);
            writer.start();
            try {
                long start = System.currentTimeMillis();
                while (channel.writeBlockedForMs() < 200 && System.currentTimeMillis() - start < 8_000) {
                    Thread.sleep(20);
                }
                assertTrue("the write never stalled", channel.writeBlockedForMs() >= 200 || channel.isWriteInProgress());
                long closedAt = System.nanoTime();
                channel.close();
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - closedAt);
                assertTrue("close blocked for " + elapsedMs + " ms", elapsedMs < 2_000);
                writer.join(3_000);
            } finally {
                channel.close();
                peer.close();
                client.close();
            }
        }
    }

    @Test
    public void writeDeadlineUnblocksAStalledWriter() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            Socket client = new Socket();
            client.connect(server.getLocalSocketAddress(), 3000);
            client.setSendBufferSize(1024);
            Socket peer = server.accept();
            peer.setReceiveBufferSize(1024);
            FrameChannel channel = new FrameChannel(client, 400);
            try {
                long start = System.currentTimeMillis();
                try {
                    byte[] blob = new byte[256 * 1024];
                    for (int i = 0; i < 64; i++) channel.write(blob);
                    fail("expected the write deadline");
                } catch (IOException expected) {
                    assertTrue(expected instanceof LinkFailure);
                    assertTrue(LinkFailure.classify(expected) == LinkFailure.Kind.TIMEOUT);
                }
                assertTrue(System.currentTimeMillis() - start < 8_000);
            } finally {
                channel.close();
                peer.close();
                client.close();
            }
        }
    }

    @Test
    public void oneWatchdogServesEveryWrite() throws Exception {
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            Socket client = new Socket();
            client.connect(server.getLocalSocketAddress(), 3000);
            Socket peer = server.accept();
            Thread reader = new Thread(() -> {
                try {
                    InputStream in = peer.getInputStream();
                    byte[] buf = new byte[1024];
                    while (in.read(buf) >= 0) {
                        // drain so the client writes finish
                    }
                } catch (IOException ignored) {
                }
            }, "stall-reader");
            reader.setDaemon(true);
            reader.start();
            FrameChannel channel = new FrameChannel(client, 2_000);
            try {
                for (int i = 0; i < 6; i++) channel.write(new byte[]{1, 2, 3, 4});
                Thread.sleep(80);
                assertEquals(1, FrameChannel.deadlineThreads());
            } finally {
                channel.close();
                peer.close();
                client.close();
            }
        }
    }
}
