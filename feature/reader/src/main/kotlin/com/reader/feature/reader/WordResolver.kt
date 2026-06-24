package com.reader.feature.reader

/**
 * JavaScript that resolves the word under a tap point inside a Readium resource WebView.
 *
 * Coordinate spaces (see Readium's reflowable injectable: it reports tap points as
 * `clientX * devicePixelRatio`, i.e. device pixels relative to the WebView):
 *  - **Input** `viewX`/`viewY` are device pixels (exactly [org.readium.r2.navigator.input.TapEvent]'s
 *    `point`). The script divides by `devicePixelRatio` to get CSS px for [Document.caretRangeFromPoint].
 *  - **Output** `left/top/right/bottom` are device pixels (CSS-px rect multiplied back by
 *    `devicePixelRatio`), so the Kotlin side gets a rect already in the navigator view's space.
 *
 * The word is expanded from the caret using [Intl.Segmenter] when available (grapheme/locale
 * aware), falling back to a whitespace/punctuation scan. Returns the JSON string
 * `{"word","left","top","right","bottom"}`, or `null` when no word is under the point.
 */
internal object WordResolver {

    /** Build the JS expression to evaluate. [viewX]/[viewY] are device pixels. */
    fun script(viewX: Float, viewY: Float): String =
        // language=JavaScript
        """
        (function() {
          try {
            var dpr = window.devicePixelRatio || 1;
            var cssX = $viewX / dpr;
            var cssY = $viewY / dpr;

            var range = (document.caretRangeFromPoint
              ? document.caretRangeFromPoint(cssX, cssY)
              : null);
            if (!range) return null;

            var node = range.startContainer;
            if (!node || node.nodeType !== Node.TEXT_NODE) return null;
            var text = node.textContent;
            if (!text) return null;

            var offset = range.startOffset;
            if (offset > text.length) offset = text.length;

            var start = offset, end = offset;

            function isWordChar(ch) {
              // Letters, marks, numbers, and intra-word punctuation (apostrophe/hyphen).
              return /[\p{L}\p{M}\p{N}'’\-]/u.test(ch);
            }

            if (typeof Intl !== 'undefined' && Intl.Segmenter) {
              try {
                var seg = new Intl.Segmenter(undefined, { granularity: 'word' });
                var segments = seg.segment(text);
                var found = null;
                for (var s of segments) {
                  if (offset >= s.index && offset < s.index + s.segment.length) { found = s; break; }
                  // Tap may land just after the last char of a word.
                  if (offset === s.index + s.segment.length) { found = s; }
                }
                if (found && found.isWordLike) {
                  start = found.index;
                  end = found.index + found.segment.length;
                } else {
                  found = null;
                }
                if (!found) {
                  // Fall through to the manual scan below.
                  start = offset; end = offset;
                }
              } catch (segErr) {
                start = offset; end = offset;
              }
            }

            if (start === end) {
              // Whitespace/punctuation boundary scan.
              while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
              while (end < text.length && isWordChar(text.charAt(end))) end++;
            }

            if (end <= start) return null;
            var word = text.substring(start, end).trim();
            if (!word) return null;

            var wordRange = document.createRange();
            wordRange.setStart(node, start);
            wordRange.setEnd(node, end);
            var rect = wordRange.getBoundingClientRect();
            if (!rect || (rect.width === 0 && rect.height === 0)) return null;

            return JSON.stringify({
              word: word,
              left: rect.left * dpr,
              top: rect.top * dpr,
              right: rect.right * dpr,
              bottom: rect.bottom * dpr
            });
          } catch (e) {
            return null;
          }
        })();
        """.trimIndent()
}
