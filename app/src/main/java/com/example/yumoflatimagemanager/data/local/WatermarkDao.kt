package com.example.yumoflatimagemanager.data.local

import androidx.room.*
import com.example.yumoflatimagemanager.data.WatermarkPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface WatermarkDao {
    @Query("SELECT * FROM watermark_presets ORDER BY isDefault DESC, createdAt DESC")
    fun getAllPresets(): Flow<List<WatermarkPreset>>
    
    @Query("SELECT * FROM watermark_presets WHERE id = :id")
    suspend fun getPresetById(id: Long): WatermarkPreset?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: WatermarkPreset): Long
    
    @Update
    suspend fun updatePreset(preset: WatermarkPreset)
    
    @Delete
    suspend fun deletePreset(preset: WatermarkPreset)
    
    @Query("DELETE FROM watermark_presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)
    
    @Query("SELECT COUNT(*) FROM watermark_presets")
    suspend fun getPresetsCount(): Int
}
