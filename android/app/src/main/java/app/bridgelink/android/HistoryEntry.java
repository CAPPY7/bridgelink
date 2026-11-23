package app.bridgelink.android;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** One activity or transfer row. Newest rows have the highest id. */
@Entity(tableName = "lines")
public class HistoryEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String line;
    @ColumnInfo(defaultValue = "'text'")
    public String kind;
    @ColumnInfo(defaultValue = "'in'")
    public String direction;
    @ColumnInfo(defaultValue = "''")
    public String title;
    @ColumnInfo(defaultValue = "''")
    public String body;
    @ColumnInfo(defaultValue = "0")
    public long size;
    @ColumnInfo(name = "at", defaultValue = "0")
    public long at;
    @ColumnInfo(defaultValue = "'done'")
    public String status;

    public HistoryEntry(long id, String line, String kind, String direction, String title, String body, long size, long at, String status) {
        this.id = id;
        this.line = line;
        this.kind = kind == null || kind.isEmpty() ? "text" : kind;
        this.direction = direction == null || direction.isEmpty() ? "in" : direction;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.size = size;
        this.at = at;
        this.status = status == null || status.isEmpty() ? "done" : status;
    }

    @Ignore
    public HistoryEntry(long id, String line) {
        this(id, line, "text", "in", clip(line), "", 0, System.currentTimeMillis(), "done");
    }

    private static String clip(String line) {
        if (line == null) return "";
        String text = line.trim();
        return text.length() <= 80 ? text : text.substring(0, 80);
    }
}
