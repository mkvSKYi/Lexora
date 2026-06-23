package com.reader.core.data.imports

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.reader.core.data.LibraryRepository
import com.reader.core.data.model.Book
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Imports an EPUB from a content [Uri] into app storage, extracts metadata + cover via
 * Readium and persists a [Book] through [LibraryRepository].
 */
class EpubImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val metadataParser: EpubMetadataParser,
    private val libraryRepository: LibraryRepository,
) {
    suspend fun import(uri: Uri): Result<Long> {
        val id = UUID.randomUUID().toString()
        val epubFile = File(booksDir(), "$id.epub")
        var coverFile: File? = null

        return runCatching {
            copyToFile(uri, epubFile)

            val metadata = metadataParser.parse(epubFile)
            coverFile = metadataParser.cover(epubFile)?.let { bitmap ->
                File(coversDir(), "$id.png").also { writeCover(bitmap, it) }
            }

            val book = Book(
                id = 0,
                title = metadata.title,
                author = metadata.author,
                coverPath = coverFile?.absolutePath,
                filePath = epubFile.absolutePath,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
            )
            libraryRepository.addBook(book)
        }.onFailure {
            epubFile.delete()
            coverFile?.delete()
        }
    }

    private fun copyToFile(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for $uri")
        input.use { source ->
            FileOutputStream(target).use { sink -> source.copyTo(sink) }
        }
    }

    private fun writeCover(bitmap: Bitmap, target: File) {
        FileOutputStream(target).use { sink ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, sink)
        }
    }

    private fun booksDir(): File = File(context.filesDir, "books").apply { mkdirs() }

    private fun coversDir(): File = File(context.filesDir, "covers").apply { mkdirs() }
}
