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

    @Query("SELECT * FROM lines WHERE title LIKE :like ESCAPE '\\' OR body LIKE :like ESCAPE '\\' OR line LIKE :like ESCAPE '\\' ORDER BY id DESC LIMIT :limit OFFSET :offset")
    List<HistoryEntry> search(String like, int limit, int offset);

    @Query("SELECT * FROM lines WHERE id = :id LIMIT 1")
    HistoryEntry one(long id);

    @Query("DELETE FROM lines WHERE id = :id")
    void delete(long id);

    @Query("DELETE FROM lines")
    void clear();

    @Query("DELETE FROM lines WHERE id NOT IN (SELECT id FROM lines ORDER BY id DESC LIMIT :cap)")
    void trim(int cap);
}
