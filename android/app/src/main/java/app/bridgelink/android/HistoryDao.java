package app.bridgelink.android;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {
    @Insert
    void insert(HistoryEntry entry);

    @Query("SELECT line FROM lines ORDER BY id DESC LIMIT :cap")
    List<String> latest(int cap);

    @Query("DELETE FROM lines WHERE id NOT IN (SELECT id FROM lines ORDER BY id DESC LIMIT :cap)")
    void trim(int cap);
}
