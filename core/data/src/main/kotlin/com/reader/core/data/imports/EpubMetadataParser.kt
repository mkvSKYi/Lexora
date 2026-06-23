package com.reader.core.data.imports

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Opens an EPUB file with Readium and extracts its title, first author and cover.
 *
 * Requires an Android [Context] because Readium's [AssetRetriever] /
 * [DefaultPublicationParser] depend on a [android.content.ContentResolver] and decode
 * the cover into an Android [Bitmap]. The parser test therefore runs under Robolectric.
 */
class EpubMetadataParser @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val httpClient by lazy { DefaultHttpClient() }

    private val assetRetriever by lazy {
        AssetRetriever(
            contentResolver = context.contentResolver,
            httpClient = httpClient,
        )
    }

    private val publicationOpener by lazy {
        PublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            ),
        )
    }

    suspend fun parse(file: File): ParsedMetadata {
        val publication = open(file)
        return try {
            ParsedMetadata(
                title = publication.metadata.title ?: file.nameWithoutExtension,
                author = publication.metadata.authors.firstOrNull()?.name,
            )
        } finally {
            publication.close()
        }
    }

    /** Returns the cover bitmap, or null if the publication has no cover. */
    suspend fun cover(file: File): Bitmap? {
        val publication = open(file)
        return try {
            publication.cover()
        } finally {
            publication.close()
        }
    }

    private suspend fun open(file: File): Publication {
        val asset = assetRetriever.retrieve(file.toUrl())
            .getOrElse { throw IOException("Failed to retrieve asset: $it") }
        return publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                throw IOException("Failed to open publication: $it")
            }
    }
}
