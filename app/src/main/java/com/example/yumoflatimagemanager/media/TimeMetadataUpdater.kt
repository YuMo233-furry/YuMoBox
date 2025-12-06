package com.example.yumoflatimagemanager.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.InputStream

object TimeMetadataUpdater {
    fun updateTakenTimeWithExif(context: Context, uri: Uri, takenTimeMillis: Long): Boolean {
		return runCatching {
			context.contentResolver.openInputStream(uri)?.use { input: InputStream ->
				// ExifInterface 需要文件路径或可写流；SAF 场景下可复制到临时文件再回写，这里仅演示写入 MediaStore 字段
			}
			val values = ContentValues().apply {
				put(MediaStore.Images.Media.DATE_TAKEN, takenTimeMillis)
				put(MediaStore.MediaColumns.DATE_MODIFIED, takenTimeMillis / 1000)
			}
			context.contentResolver.update(uri, values, null, null) > 0
		}.getOrElse { false }
	}

	fun updateModifyTime(context: Context, uri: Uri, modifyTimeMillis: Long): Boolean {
		return runCatching {
			val values = ContentValues().apply {
				put(MediaStore.MediaColumns.DATE_MODIFIED, modifyTimeMillis / 1000)
			}
			context.contentResolver.update(uri, values, null, null) > 0
		}.getOrElse { false }
	}
}


