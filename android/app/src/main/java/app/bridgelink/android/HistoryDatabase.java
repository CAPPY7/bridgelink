package app.bridgelink.android;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import app.bridgelink.android.protocol.HistoryQuery;

/** Activity and transfer rows that stay after the app restarts. Work stays off the main thread. */
@Database(entities = {HistoryEntry.class}, version = 2, exportSchema = true)
public abstract class HistoryDatabase extends RoomDatabase {
    public abstract HistoryDao history();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `kind` TEXT DEFAULT 'text'");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `direction` TEXT DEFAULT 'in'");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `title` TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `body` TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `size` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `at` INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE `lines` ADD COLUMN `status` TEXT DEFAULT 'done'");
        }
    };

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
                            .addMigrations(MIGRATION_1_2)
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
                dao.trim(HistoryQuery.CAP);
                done.accept(dao.latest(200));
            } catch (Exception error) {
                done.accept(null);
            }
        });
    }

    public static void record(Context context, String kind, String direction, String title, String body, long size, String status) {
        if (!HistoryQuery.keep((title == null ? "" : title) + "\n" + (body == null ? "" : body), false)) return;
        Context app = context.getApplicationContext();
        String shown = title == null || title.isEmpty() ? kind : title;
        if (shown.length() > 80) shown = shown.substring(0, 80);
        String line = direction + " " + kind + " " + shown;
        String finalShown = shown;
        IO.execute(() -> {
            try {
                HistoryDao dao = get(app).history();
                dao.insert(new HistoryEntry(0, line, kind, direction, finalShown, body == null ? "" : body, size, System.currentTimeMillis(), status));
                dao.trim(HistoryQuery.CAP);
            } catch (Exception ignored) {
            }
        });
    }

    public static void search(Context context, String query, int offset, Consumer<List<HistoryEntry>> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                String like = HistoryQuery.like(query);
                done.accept(get(app).history().search(like, HistoryQuery.PAGE, Math.max(0, offset)));
            } catch (Exception error) {
                done.accept(Collections.emptyList());
            }
        });
    }

    public static void one(Context context, long id, Consumer<HistoryEntry> done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                done.accept(get(app).history().one(id));
            } catch (Exception error) {
                done.accept(null);
            }
        });
    }

    public static void delete(Context context, long id, Runnable done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                get(app).history().delete(id);
            } catch (Exception ignored) {
            }
            if (done != null) done.run();
        });
    }

    public static void clear(Context context, Runnable done) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                get(app).history().clear();
            } catch (Exception ignored) {
            }
            if (done != null) done.run();
        });
    }
}
