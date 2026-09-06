package com.example.calculator.vault

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

object DeleteOriginalHelper {

    fun requestDelete(
        context: Context,
        uris: List<Uri>,
        launchIntentSender: (IntentSender) -> Unit
    ) {
        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaUris = uris.filter { it.authority == MediaStore.AUTHORITY }
            val otherUris = uris.filterNot { it.authority == MediaStore.AUTHORITY }

            otherUris.forEach { deleteSingle(context, it) }

            if (mediaUris.isNotEmpty()) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        mediaUris
                    )
                    launchIntentSender(pendingIntent.intentSender)
                    return
                } catch (t: Throwable) {
                    mediaUris.forEach { deleteSingle(context, it) }
                }
            }
        } else {
            uris.forEach { deleteSingle(context, it) }
        }
    }

    private fun deleteSingle(context: Context, uri: Uri) {
        val deletedByDocs = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrDefault(false)

        if (!deletedByDocs) {
            runCatching {
                context.contentResolver.delete(uri, null, null)
            }
        }
    }
}
