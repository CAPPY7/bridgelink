package app.bridgelink.android;

import android.os.Handler;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import app.bridgelink.android.protocol.FrameChannel;

import static org.junit.Assert.assertTrue;

/**
 * Closing a stalled socket from the main thread must return, and a later
 * main-thread message must still run. A frozen UI fails this watchdog.
 */
@RunWith(AndroidJUnit4.class)
public class StallWriteTest {
    @Test
    public void mainThreadSurvivesAPeerThatStopsReading() throws Exception {
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
                    while (!Thread.currentThread().isInterrupted()) channel.write(blob);
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
                assertTrue(channel.isWriteInProgress() || channel.writeBlockedForMs() > 0);
                CountDownLatch beat = new CountDownLatch(2);
                Handler main = new Handler(Looper.getMainLooper());
                main.post(() -> {
                    channel.close();
                    beat.countDown();
                });
                main.post(beat::countDown);
                assertTrue("main thread froze while a peer stopped reading", beat.await(3, TimeUnit.SECONDS));
            } finally {
                channel.close();
                writer.interrupt();
                peer.close();
                client.close();
            }
        }
    }
}
