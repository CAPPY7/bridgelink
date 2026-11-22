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
    private boolean accepted;
    private boolean forgotten;

    public AwayAuth(Sink sink) {
        this.sink = sink;
        tick = this::tick;
    }

    /** Phone joined the away relay first. */
    public void start() {
        accepted = false;
        forgotten = false;
        kick();
    }

    /** Mac joined later. Sign in again unless this phone was forgotten. */
    public void macJoined() {
        if (forgotten) return;
        accepted = false;
        kick();
    }

    public void ok() {
        accepted = true;
        sink.remove(tick);
    }

    public void forgot() {
        forgotten = true;
        accepted = false;
        sink.remove(tick);
    }

    public void stop() {
        sink.remove(tick);
    }

    public boolean accepted() {
        return accepted;
    }

    public boolean forgotten() {
        return forgotten;
    }

    private void kick() {
        sink.remove(tick);
        tick.run();
    }

    private void tick() {
        if (!RelayAuth.keepSending(accepted, forgotten ? "forgotten" : "")) {
            sink.remove(tick);
            return;
        }
        sink.writeAuth();
        sink.remove(tick);
        sink.post(tick, 2_000);
    }
}
