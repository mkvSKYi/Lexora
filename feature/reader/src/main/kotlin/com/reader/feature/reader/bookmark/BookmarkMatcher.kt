package com.reader.feature.reader.bookmark

import com.reader.core.data.model.Bookmark
import kotlin.math.abs

/** Decides whether a reading position equals a stored [Bookmark]. Pure; takes primitives. */
object BookmarkMatcher {
    fun matches(href: String?, progression: Double?, bookmark: Bookmark, epsilon: Double): Boolean {
        if (href == null || href != bookmark.href) return false
        return abs((progression ?: 0.0) - bookmark.progression) < epsilon
    }
}
