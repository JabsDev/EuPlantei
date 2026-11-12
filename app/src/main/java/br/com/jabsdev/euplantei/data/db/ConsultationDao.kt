package br.com.jabsdev.euplantei.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id")
    fun observeById(id: Long): Flow<ConsultationEntity?>

    @Query("SELECT * FROM consultations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConsultationEntity?

    @Query("SELECT * FROM consultations ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ConsultationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ConsultationEntity): Long

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM consultations")
    suspend fun clear()
}
