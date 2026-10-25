package com.example.tracker.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareUtil {

    /** Opens the system share sheet with the given exported file attached. */
    fun share(context: Context, file: File, mime: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться"))
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
