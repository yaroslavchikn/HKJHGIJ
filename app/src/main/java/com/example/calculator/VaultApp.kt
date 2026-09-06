package com.example.calculator

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.decode.VideoFrameDecoder
import java.io.File

class VaultApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // При старте процесса чистим временные расшифрованные файлы.
        runCatching {
            File(cacheDir, "vault_cache").deleteRecursively()
        }

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(VideoFrameDecoder.Factory())
                }
                .crossfade(true)
                .build()
        )
    }
}
