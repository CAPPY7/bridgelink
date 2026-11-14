package app.bridgelink.android.protocol;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Socket writes never run on the caller thread. */
public final class Outbox {
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "bridgelink-writer");
        thread.setDaemon(true);
        return thread;
    });

    public void submit(Runnable write) {
        executor.execute(write);
    }

    public void close() {
        executor.shutdownNow();
    }
}
