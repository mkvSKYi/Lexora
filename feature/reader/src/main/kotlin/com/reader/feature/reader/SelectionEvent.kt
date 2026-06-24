package com.reader.feature.reader

import android.graphics.RectF

/**
 * A resolved word (from a tap) or an explicit text selection, together with its bounding
 * box in the navigator view's pixel coordinate space.
 *
 * [rectInView] is expressed in the same coordinate space as the navigator's publication
 * view (device pixels, origin top-left of the view) so the host can position a popover
 * over the word/selection.
 *
 * [contextSentence] is the enclosing sentence for a tapped word (best-effort), used when
 * saving the word. It is null for the long-press path, where [text] already is the sentence.
 */
data class SelectionEvent(
    val text: String,
    val rectInView: RectF,
    val contextSentence: String? = null,
)
