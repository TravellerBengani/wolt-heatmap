package com.example.woltheatmap.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StopDao {
    @Insert
    suspend fun insert(stop: StopEntity)

    @Query("SELECT * FROM queued_stops")
    suspend fun getAll(): List<StopEntity>

    @Query("SELECT COUNT(*) FROM queued_stops")
    suspend fun count(): Int

    @Query("DELETE FROM queued_stops WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}
