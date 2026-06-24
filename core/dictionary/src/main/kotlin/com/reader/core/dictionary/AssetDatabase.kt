package com.reader.core.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Opens the bundled read-only dictionary SQLite database via raw [SQLiteDatabase]
 * (deliberately not Room, to avoid Room's prepackaged-DB identity-hash requirement).
 *
 * In production the asset is copied to internal storage on first use and then opened
 * read-only, caching the handle. Tests pass an [override] file to open a real SQLite
 * file directly.
 */
object AssetDatabase {
    private const val DEFAULT_ASSET_NAME = "dictionary.db"

    @Volatile
    private var cached: SQLiteDatabase? = null

    fun openReadable(
        context: Context,
        assetName: String = DEFAULT_ASSET_NAME,
        override: File? = null,
    ): SQLiteDatabase {
        if (override != null) {
            return SQLiteDatabase.openDatabase(
                override.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        }

        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val target = File(context.filesDir, assetName)
            if (!target.exists()) {
                context.assets.open(assetName).use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            val db = SQLiteDatabase.openDatabase(
                target.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            cached = db
            return db
        }
    }
}
