package com.example.yumoflatimagemanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.graphics.Color

// 水印类型
enum class WatermarkType {
    TEXT,    // 文字水印
    IMAGE    // 图片水印
}

// 水印锚点位置（预设的初始位置）
enum class WatermarkAnchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

// 水印保存选项
enum class WatermarkSaveOption {
    OVERWRITE,      // 覆盖原图
    CREATE_NEW      // 创建新文件
}

// 水印预设数据类
@Entity(tableName = "watermark_presets")
data class WatermarkPreset(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: WatermarkType,
    
    // 文字水印参数
    val text: String? = null,
    val textSize: Float = 48f,
    val textColor: Int = Color.WHITE,
    val textBold: Boolean = false,
    
    // 图片水印参数
    val imageUri: String? = null,
    val imageScale: Float = 0.2f,  // 相对于图片宽度的比例
    
    // 通用参数
    val anchor: WatermarkAnchor = WatermarkAnchor.BOTTOM_RIGHT,
    val offsetX: Int = 20,      // 从锚点的偏移X (dp)
    val offsetY: Int = 20,      // 从锚点的偏移Y (dp)
    val alpha: Int = 200,       // 透明度 (0-255)
    val rotation: Float = 0f,   // 旋转角度
    
    val createdAt: Long = System.currentTimeMillis(),
    val isDefault: Boolean = false
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readLong(),
        parcel.readString() ?: "",
        WatermarkType.values()[parcel.readInt()],
        parcel.readString(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readByte() != 0.toByte(),
        parcel.readString(),
        parcel.readFloat(),
        WatermarkAnchor.values()[parcel.readInt()],
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(name)
        parcel.writeInt(type.ordinal)
        parcel.writeString(text)
        parcel.writeFloat(textSize)
        parcel.writeInt(textColor)
        parcel.writeByte(if (textBold) 1 else 0)
        parcel.writeString(imageUri)
        parcel.writeFloat(imageScale)
        parcel.writeInt(anchor.ordinal)
        parcel.writeInt(offsetX)
        parcel.writeInt(offsetY)
        parcel.writeInt(alpha)
        parcel.writeFloat(rotation)
        parcel.writeLong(createdAt)
        parcel.writeByte(if (isDefault) 1 else 0)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : android.os.Parcelable.Creator<WatermarkPreset> {
        override fun createFromParcel(parcel: android.os.Parcel): WatermarkPreset {
            return WatermarkPreset(parcel)
        }

        override fun newArray(size: Int): Array<WatermarkPreset?> {
            return arrayOfNulls(size)
        }
    }
}

// 应用水印时的临时状态（用户可拖动调整）
data class WatermarkState(
    val preset: WatermarkPreset,
    var currentX: Float,    // 当前位置X (0-1，相对位置)
    var currentY: Float,    // 当前位置Y (0-1，相对位置)
    var currentRotation: Float = preset.rotation,
    var currentAlpha: Int = preset.alpha,
    var currentScale: Float = 1.0f  // 当前缩放比例
)

// 单张图片的水印参数
data class ImageWatermarkParams(
    val imageUri: String,
    val watermarkX: Float,
    val watermarkY: Float,
    val watermarkScale: Float = 1.0f,
    val watermarkRotation: Float = 0f,
    val watermarkAlpha: Int = 200,
    val previewImageWidth: Int = 0  // 预览时图片的宽度，用于保存时缩放补偿
) : android.os.Parcelable {
    constructor(parcel: android.os.Parcel) : this(
        parcel.readString() ?: "",
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: android.os.Parcel, flags: Int) {
        parcel.writeString(imageUri)
        parcel.writeFloat(watermarkX)
        parcel.writeFloat(watermarkY)
        parcel.writeFloat(watermarkScale)
        parcel.writeFloat(watermarkRotation)
        parcel.writeInt(watermarkAlpha)
        parcel.writeInt(previewImageWidth)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : android.os.Parcelable.Creator<ImageWatermarkParams> {
        override fun createFromParcel(parcel: android.os.Parcel): ImageWatermarkParams {
            return ImageWatermarkParams(parcel)
        }

        override fun newArray(size: Int): Array<ImageWatermarkParams?> {
            return arrayOfNulls(size)
        }
    }
}
