package app.bridgelink.android.protocol;

/**
 * Background code must not crash when Android refuses a foreground start.
 * A running service takes the message in-process. Otherwise the start is
 * tried, and a blocked start is queued with a visible notice.
 */
public final class ServiceDoor {
    public static final String RECONNECT = "Open BridgeLink to reconnect";

    public interface Starter {
        void start();
    }

    public interface Parker<T> {
        void add(T ticket);
    }

    public static final class Result {
        public final boolean handled;
        public final boolean queued;
        public final String notice;

        public Result(boolean handled, boolean queued, String notice) {
            this.handled = handled;
            this.queued = queued;
            this.notice = notice;
        }
    }

    private ServiceDoor() {}

    public static boolean backgroundBlocked(Throwable error) {
        for (Throwable cursor = error; cursor != null; cursor = cursor.getCause()) {
            if (cursor.getClass().getName().endsWith("ForegroundServiceStartNotAllowedException")) return true;
        }
        return false;
    }

    public static <T> Result deliver(boolean serviceRunning, Starter inProcess, Starter startService, Parker<T> park, T ticket) {
        if (serviceRunning) {
            inProcess.start();
            return new Result(true, false, null);
        }
        try {
            startService.start();
            return new Result(true, false, null);
        } catch (RuntimeException error) {
            if (!backgroundBlocked(error)) throw error;
            park.add(ticket);
            return new Result(false, true, RECONNECT);
        }
    }
}
