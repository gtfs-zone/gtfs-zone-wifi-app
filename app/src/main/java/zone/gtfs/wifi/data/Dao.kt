package zone.gtfs.wifi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun get(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observe(id: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE state = :state ORDER BY startedAt")
    suspend fun withState(state: SessionState): List<SessionEntity>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: String)

    @Insert
    suspend fun insertSample(sample: SampleEntity)

    @Insert
    suspend fun insertNetTest(test: NetTestEntity)

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY t")
    suspend fun samples(id: String): List<SampleEntity>

    @Query("SELECT * FROM net_tests WHERE sessionId = :id ORDER BY t")
    suspend fun netTests(id: String): List<NetTestEntity>

    @Query("SELECT COUNT(*) FROM samples WHERE sessionId = :id")
    fun observeSampleCount(id: String): Flow<Int>

    @Query("SELECT * FROM samples WHERE sessionId = :id ORDER BY t DESC LIMIT 1")
    fun observeLastSample(id: String): Flow<SampleEntity?>

    @Query("SELECT * FROM net_tests WHERE sessionId = :id ORDER BY t DESC LIMIT 1")
    fun observeLastNetTest(id: String): Flow<NetTestEntity?>

    @Query("DELETE FROM samples WHERE sessionId = :id")
    suspend fun deleteSamples(id: String)

    @Query("DELETE FROM net_tests WHERE sessionId = :id")
    suspend fun deleteNetTests(id: String)

    /** Stores the built payload and drops the untrimmed rows behind it. */
    @Transaction
    suspend fun queue(session: SessionEntity) {
        update(session)
        deleteSamples(session.id)
        deleteNetTests(session.id)
    }
}
