package com.example.calculator.vault.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class MediaItem(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val createdAt: Long,
    val size: Long
)

class VaultRepository(private val context: Context) {

    private val mutex = Mutex()

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val vaultDir by lazy {
        File(context.filesDir, "vault").apply {
            mkdirs()
            runCatching { File(this, ".nomedia").createNewFile() }
        }
    }

    private val mediaDir by lazy {
        File(vaultDir, "media").apply {
            mkdirs()
        }
    }

    private val metadataFile by lazy {
        File(vaultDir, "metadata.json")
    }

    private val cacheDir by lazy {
        File(context.cacheDir, "vault_cache").apply {
            mkdirs()
            runCatching { File(this, ".nomedia").createNewFile() }
        }
    }

    suspend fun listItems(): List<MediaItem> = mutex.withLock {
        readItemsLocked()
    }

    suspend fun importMedia(uri: Uri, fallbackMime: String?): MediaItem = mutex.withLock {
        val mimeType = context.contentResolver.getType(uri)
            ?: fallbackMime
            ?: "application/octet-stream"

        val displayName = queryDisplayName(uri) ?: defaultName(mimeType)
        val id = UUID.randomUUID().toString()
        val target = File(mediaDir, "$id.bin")

        val size = try {
            encryptedFile(target).openFileOutput().use { output ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open input")
                input.use { copyStreams(it, output) }
            }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }

        if (size <= 0) {
            target.delete()
            throw IOException("Empty file")
        }

        val item = MediaItem(
            id = id,
            displayName = displayName,
            mimeType = mimeType,
            createdAt = System.currentTimeMillis(),
            size = size
        )

        val items = readItemsLocked().toMutableList()
        items.add(0, item)
        writeItemsLocked(items)

        item
    }

    suspend fun deleteItem(item: MediaItem) = mutex.withLock {
        runCatching { File(mediaDir, "${item.id}.bin").delete() }
        runCatching { cacheFileFor(item).delete() }

        val items = readItemsLocked().filterNot { it.id == item.id }
        writeItemsLocked(items)
    }

    fun getDecryptedFile(item: MediaItem): File {
        val output = cacheFileFor(item)

        if (output.exists() && output.length() == item.size) {
            return output
        }

        val encrypted = File(mediaDir, "${item.id}.bin")
        if (!encrypted.exists()) throw IOException("File not found")

        if (output.exists()) output.delete()

        try {
            encryptedFile(encrypted).openFileInput().use { input ->
                FileOutputStream(output).use { out ->
                    copyStreams(input, out)
                }
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        }

        return output
    }

    fun clearCache() {
        runCatching {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun encryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_1MB
        ).build()
    }

    private fun readItemsLocked(): List<MediaItem> {
        if (!metadataFile.exists()) return emptyList()

        return try {
            val text = encryptedFile(metadataFile)
                .openFileInput()
                .bufferedReader()
                .use { it.readText() }

            parseItems(text)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun writeItemsLocked(items: List<MediaItem>) {
        val json = encodeItems(items)

        if (metadataFile.exists()) {
            metadataFile.delete()
        }

        encryptedFile(metadataFile)
            .openFileOutput()
            .bufferedWriter()
            .use { it.write(json) }
    }

    private fun parseItems(json: String): List<MediaItem> {
        val root = JSONObject(json)
        val array = root.optJSONArray("items") ?: return emptyList()
        val result = mutableListOf<MediaItem>()

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                MediaItem(
                    id = obj.getString("id"),
                    displayName = obj.getString("name"),
                    mimeType = obj.getString("mime"),
                    createdAt = obj.getLong("created"),
                    size = obj.getLong("size")
                )
            )
        }

        return result
    }

    private fun encodeItems(items: List<MediaItem>): String {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.displayName)
                    .put("mime", item.mimeType)
                    .put("created", item.createdAt)
                    .put("size", item.size)
            )
        }

        return JSONObject().put("items", array).toString()
    }

    private fun copyStreams(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            total += read
        }

        output.flush()
        return total
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun defaultName(mimeType: String): String {
        val prefix = if (mimeType.startsWith("video/")) "VID" else "IMG"
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return prefix + "_" + date + extensionForMime(mimeType)
    }

    private fun cacheFileFor(item: MediaItem): File {
        val extension = item.displayName.substringAfterLast('.', "")
        val ext = if (extension.isNotEmpty()) ".$extension" else extensionForMime(item.mimeType)
        return File(cacheDir, item.id + ext)
    }

    private fun extensionForMime(mimeType: String): String {
        return when {
            mimeType == "image/jpeg" -> ".jpg"
            mimeType == "image/png" -> ".png"
            mimeType == "image/webp" -> ".webp"
            mimeType == "image/gif" -> ".gif"
            mimeType == "video/mp4" -> ".mp4"
            mimeType == "video/3gpp" -> ".3gp"
            mimeType == "video/webm" -> ".webm"
            mimeType.startsWith("image/") -> ".img"
            mimeType.startsWith("video/") -> ".vid"
            else -> ".bin"
        }
    }

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
