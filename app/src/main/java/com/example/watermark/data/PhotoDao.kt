package com.example.watermark.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM watermark_photo ORDER BY timestamp DESC")
    fun getAll(): Flow<List<WatermarkPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: WatermarkPhoto)

    @Query("DELETE FROM watermark_photo WHERE galleryId = :galleryId")
    suspend fun deleteByGalleryId(galleryId: Long)

    /** 验真页：按防伪码反查这张照片的拍摄记录。 */
    @Query("SELECT * FROM watermark_photo WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): WatermarkPhoto?

    /** 相册顶部的项目分类：出现过且非空的标题。 */
    @Query("SELECT title FROM watermark_photo WHERE title != '' GROUP BY title ORDER BY MAX(timestamp) DESC")
    fun getTitles(): Flow<List<String>>
}
