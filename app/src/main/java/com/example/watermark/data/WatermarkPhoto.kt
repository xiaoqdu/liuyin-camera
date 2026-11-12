package com.example.watermark.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watermark_photo")
data class WatermarkPhoto(
    @PrimaryKey val uri: String,
    val timestamp: Long,
    val galleryId: Long,
    /** 拍摄时的标题（原「昵称」），相册按项目分类与搜索用。 */
    val title: String = "",
    /** 拍摄时的地点文本。 */
    val location: String = "",
    /** 防伪码，验真页按码反查。 */
    val code: String = ""
)
