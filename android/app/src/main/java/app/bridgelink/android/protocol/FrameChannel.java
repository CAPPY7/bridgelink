package app.bridgelink.android.protocol;

import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One writer thread per socket. close() does not take a lock the writer holds,
 * so a stalled peer cannot freeze the thread that asked to close.
 */
public final class FrameChannel implements AutoCloseable {
    public static final int DEFAULT_DEADLINE_MS = 20_000;
    private static final int QUEUE = 8;

    private final Socket socket;
    private final int deadlineMs;
    private final ArrayBlockingQueue<Write> queue = new ArrayBlockingQueue<>(QUEUE);
    private final Thread writer;
    private final Thread watchdog;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile boolean writeInProgress;
    private volatile long writeStartedNanos;
    private volatile boolean deadlineHit;

    public FrameChannel(Socket socket) {
        this(socket, DEFAULT_DEADLINE_MS);
    }

    public FrameChannel(Socket socket, int deadlineMs) {
        this.socket = socket;
        this.deadlineMs = deadlineMs;
        this.writer = new Thread(this::writeLoop, "bridgelink-write");
        this.writer.setDaemon(true);
        this.writer.start();
        this.watchdog = new Thread(this::watchLoop, "bridgelink-write-deadline");
        this.watchdog.setDaemon(true);
        this.watchdog.start();
    }

    public boolean isWriteInProgress() {
        return writeInProgress;
    }

    public long writeBlockedForMs() {
        if (!writeInProgress || writeStartedNanos == 0) return 0;
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - writeStartedNanos);
    }

    public void write(byte[] frame) throws IOException {
        refuseMainThread();
        if (closed.get()) throw new IOException("not connected");
        Write write = new Write(frame);
        boolean offered;
        try {
            offered = queue.offer(write, deadlineMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("not connected");
        }
        if (!offered) {
            throw new LinkFailure(LinkFailure.Kind.TIMEOUT, LinkFailure.message(LinkFailure.Kind.TIMEOUT));
        }
        write.await(deadlineMs);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        // SSLSocket.close writes a close alert. That is network I/O, so it must
        // not run on the thread that asked to close (often the main thread).
        Thread closer = new Thread(() -> {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            writer.interrupt();
            watchdog.interrupt();
            drain(new IOException("not connected"));
        }, "bridgelink-close");
        closer.setDaemon(true);
        closer.start();
    }

    private void drain(IOException error) {
        Write pending;
        while ((pending = queue.poll()) != null) pending.fail(error);
    }

    private void writeLoop() {
        while (!closed.get()) {
            Write write;
            try {
                write = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException error) {
                if (closed.get()) return;
                continue;
            }
            if (write == null) continue;
            writeStartedNanos = System.nanoTime();
            writeInProgress = true;
            deadlineHit = false;
            try {
                OutputStream out = socket.getOutputStream();
                out.write(write.frame);
                out.flush();
                write.ok();
            } catch (IOException error) {
                if (deadlineHit) {
                    write.fail(new LinkFailure(LinkFailure.Kind.TIMEOUT, LinkFailure.message(LinkFailure.Kind.TIMEOUT)));
                } else {
                    write.fail(error);
                }
                close();
                return;
            } finally {
                writeInProgress = false;
                writeStartedNanos = 0;
            }
        }
    }

    private void watchLoop() {
        while (!closed.get()) {
            try {
                Thread.sleep(Math.min(Math.max(deadlineMs, 50), 250));
            } catch (InterruptedException error) {
                if (closed.get()) return;
                continue;
            }
            if (!writeInProgress || writeStartedNanos == 0 || closed.get()) continue;
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - writeStartedNanos);
            if (elapsed >= deadlineMs) {
                deadlineHit = true;
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** How many deadline threads this process still has. One channel keeps one. */
    public static int deadlineThreads() {
        int count = 0;
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.isAlive() && "bridgelink-write-deadline".equals(thread.getName())) count++;
        }
        return count;
    }

    private static void refuseMainThread() {
        Looper main = Looper.getMainLooper();
        if (main != null && main == Looper.myLooper()) {
            throw new IllegalStateException("socket write on the main thread");
        }
    }

    private static final class Write {
        final byte[] frame;
        private boolean done;
        private IOException error;

        Write(byte[] frame) {
            this.frame = frame;
        }

        synchronized void await(int deadlineMs) throws IOException {
            long end = System.currentTimeMillis() + deadlineMs + 1_000L;
            while (!done) {
                long wait = end - System.currentTimeMillis();
                if (wait <= 0) throw new LinkFailure(LinkFailure.Kind.TIMEOUT, LinkFailure.message(LinkFailure.Kind.TIMEOUT));
                try {
                    wait(wait);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("not connected");
                }
            }
            if (error != null) throw error;
        }

        synchronized void ok() {
            done = true;
            notifyAll();
        }

        synchronized void fail(IOException error) {
            this.error = error;
            done = true;
            notifyAll();
        }
    }
}
