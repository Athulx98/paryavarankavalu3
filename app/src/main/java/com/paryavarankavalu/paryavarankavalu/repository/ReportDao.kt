package com.paryavarankavalu.paryavarankavalu.repository

import androidx.room.*
import com.paryavarankavalu.paryavarankavalu.model.LocalReport
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY timestamp DESC")
    fun getAllReports(): Flow<List<LocalReport>>

    @Query("SELECT * FROM reports WHERE id = :id LIMIT 1")
    suspend fun getReportById(id: String): LocalReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<LocalReport>)

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteReportById(id: String)

    @Query("DELETE FROM reports")
    suspend fun clearAll()
}
