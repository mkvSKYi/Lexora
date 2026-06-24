package com.reader.core.dictionary

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import javax.inject.Inject

interface DictionaryRepository {
    suspend fun lookup(word: String): DictionaryEntry?
}

class SqliteDictionaryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val openOverride: File? = null,
) : DictionaryRepository {

    override suspend fun lookup(word: String): DictionaryEntry? = withContext(Dispatchers.IO) {
        val w = word.trim().lowercase()
        runCatching {
            val db = AssetDatabase.openReadable(context, override = openOverride)
            queryEntry(db, w) ?: resolveViaForms(db, w)?.let { base -> queryEntry(db, base) }
        }.getOrNull()
    }

    private fun queryEntry(
        db: android.database.sqlite.SQLiteDatabase,
        headword: String,
    ): DictionaryEntry? {
        db.rawQuery(
            "SELECT headword,ipa,pos,definitions,uk_translations FROM entries WHERE headword=? LIMIT 1",
            arrayOf(headword),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return DictionaryEntry(
                headword = cursor.getString(0),
                ipa = cursor.getString(1),
                partOfSpeech = cursor.getString(2),
                definitions = parseJsonArray(cursor.getString(3)),
                translations = parseJsonArray(cursor.getString(4)),
            )
        }
    }

    private fun resolveViaForms(
        db: android.database.sqlite.SQLiteDatabase,
        form: String,
    ): String? {
        db.rawQuery(
            "SELECT headword FROM forms WHERE form=? LIMIT 1",
            arrayOf(form),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    private fun parseJsonArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }
        }.getOrDefault(emptyList())
    }
}
