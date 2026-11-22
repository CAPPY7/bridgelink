package app.bridgelink.android.protocol;

/**
 * One away sign-in retry at a time. The same runnable is posted and removed.
 * The caller writes the sign-in off the main thread.
 */
public final class AwayAuth {
    public interface Sink {
        void writeAuth();

        void post(Runnable task, long delayMs);

        void remove(Runnable task);
    }

    private final Sink sink;
    private final Runnable tick;
    private volatile boolean accepted;
    private volatile boolean forgotten;

    public AwayAuth(Sink sink) {
        this.sink = sink;
        tick = this::tick;
    }

    /** Phone joined the away relay first. */
    public synchronized void start() {
        accepted = false;
        forgotten = false;
        kick();
    }

    /** Mac joined later. Sign in again unless this phone was forgotten. */
    public synchronized void macJoined() {
        if (forgotten) return;
        accepted = false;
        kick();
    }

    public synchronized void ok() {
        accepted = true;
        sink.remove(tick);
    }

    public synchronized void forgot() {
        forgotten = true;
        accepted = false;
        sink.remove(tick);
    }

    public synchronized void stop() {
        sink.remove(tick);
    }

    public synchronized boolean accepted() {
        return accepted;
    }

    public synchronized boolean forgotten() {
        return forgotten;
    }

    private synchronized void kick() {
        sink.remove(tick);
        tick.run();
    }

    private synchronized void tick() {
        if (!RelayAuth.keepSending(accepted, forgotten ? "forgotten" : "")) {
            sink.remove(tick);
            return;
        }
        sink.writeAuth();
        sink.remove(tick);
        sink.post(tick, 2_000);
    }
}
