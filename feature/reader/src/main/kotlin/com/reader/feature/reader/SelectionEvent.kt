package com.reader.feature.reader

import android.graphics.RectF

/**
 * A resolved word (from a tap) or an explicit text selection, together with its bounding
 * box in the navigator view's pixel coordinate space.
 *
 * [rectInView] is expressed in the same coordinate space as the navigator's publication
 * view (device pixels, origin top-left of the view) so the host can position a popover
 * over the word/selection.
 */
data class SelectionEvent(
    val text: String,
    val rectInView: RectF,
)
