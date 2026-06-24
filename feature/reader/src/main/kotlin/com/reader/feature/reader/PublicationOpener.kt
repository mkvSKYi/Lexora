package com.reader.feature.reader

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import org.readium.r2.streamer.PublicationOpener as ReadiumPublicationOpener

/**
 * Opens an EPUB file at [path] into a Readium [Publication] for rendering.
 *
 * Mirrors the parsing path used by `core:data`'s `EpubMetadataParser`, but returns the
 * full publication so the navigator can render it. Requires an Android [Context] because
 * Readium's [AssetRetriever] / [DefaultPublicationParser] depend on a
 * [android.content.ContentResolver].
 */
class PublicationOpener @Inject constructor(
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
        ReadiumPublicationOpener(
            publicationParser = DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null,
            ),
        )
    }

    /** Returns the opened [Publication], or `null` if the file cannot be opened. */
    suspend fun open(path: String): Publication? {
        val asset = assetRetriever.retrieve(File(path).toUrl())
            .getOrElse { return null }
        return publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                null
            }
    }
}
