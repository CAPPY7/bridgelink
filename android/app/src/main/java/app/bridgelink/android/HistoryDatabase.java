package app.bridgelink.android;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Activity lines that stay after the app restarts. Reads and writes stay off the main thread. */
@Database(entities = {HistoryEntry.class}, version = 1, exportSchema = false)
public abstract class HistoryDatabase extends RoomDatabase {
    public abstract HistoryDao history();

    private static volatile HistoryDatabase instance;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "bridgelink-history");
        thread.setDaemon(true);
        return thread;
    });

    public static HistoryDatabase get(Context context) {
        if (instance == null) {
            synchronized (HistoryDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), HistoryDatabase.class, "history.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    public static void readLatest(Context context, Consumer<List<String>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                done.accept(get(app).history().latest(200));
            } catch (Exception error) {
                done.accept(Collections.emptyList());
            }
        });
    }

    public static void addLine(Context context, String line, Consumer<List<String>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                HistoryDao dao = get(app).history();
                dao.insert(new HistoryEntry(0, line));
                dao.trim(200);
                done.accept(dao.latest(200));
            } catch (Exception error) {
                done.accept(null);
            }
        });
    }
}
