package app.bridgelink.android;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One activity line. Newest rows have the highest id. */
@Entity(tableName = "lines")
public class HistoryEntry {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String line;

    public HistoryEntry(long id, String line) {
        this.id = id;
        this.line = line;
    }
}
