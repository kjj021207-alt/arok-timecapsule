package com.arok.timecapsule.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 캡슐 사진 보관함. 기기에서 고른 사진을 앱 전용 폴더(files/photos)로 복사해 둔다.
 * 원본을 지우거나 사진 앱 권한이 끊겨도 캡슐 사진은 남는다.
 */
class PhotoStore(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** content:// 사진을 복사하고 저장된 파일 경로를 돌려준다. 실패하면 null */
    suspend fun copyIn(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val target = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            target.absolutePath
        } catch (e: Exception) {
            Log.w("PhotoStore", "photo copy failed", e)
            null
        }
    }
}
