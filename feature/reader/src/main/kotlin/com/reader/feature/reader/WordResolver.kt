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
 * aware), falling back to a whitespace/punctuation scan. It also computes a best-effort
 * enclosing `sentence` by expanding from the caret to sentence terminators `.!?…` within the
 * tapped text node (same boundary scan as [SentenceResolver], simplified to a single node).
 * Returns the JSON string `{"word","sentence","left","top","right","bottom"}`, or `null` when no
 * word is under the point. `sentence` may be null when none can be resolved.
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

            // Reject taps that don't actually land on the word. caretRangeFromPoint snaps to
            // the nearest caret even for taps in empty margins / above or below the text, which
            // would otherwise translate a word the user didn't tap. A small padding keeps
            // near-misses on a real word working.
            var pad = 6;
            if (cssX < rect.left - pad || cssX > rect.right + pad ||
                cssY < rect.top - pad || cssY > rect.bottom + pad) {
              return null;
            }

            // Best-effort enclosing sentence: expand from the caret to sentence terminators
            // within this text node. Spanning inline elements isn't attempted here (kept simple);
            // a null/partial sentence must never block the word translation.
            var sentence = null;
            try {
              var terminators = '.!?…';
              function isTerminator(ch) { return terminators.indexOf(ch) !== -1; }
              function isClosing(ch) { return /["'’”»)\]}]/.test(ch); }
              var sStart = 0;
              for (var p = offset - 1; p > 0; p--) {
                if (isTerminator(text.charAt(p))) {
                  var q = p + 1;
                  while (q < text.length && isClosing(text.charAt(q))) q++;
                  while (q < text.length && /\s/.test(text.charAt(q))) q++;
                  if (q <= offset) { sStart = q; break; }
                }
              }
              var sEnd = text.length;
              for (var r = Math.max(offset, end); r < text.length; r++) {
                if (isTerminator(text.charAt(r))) {
                  var e = r + 1;
                  while (e < text.length && isClosing(text.charAt(e))) e++;
                  sEnd = e;
                  break;
                }
              }
              if (sEnd > sStart) {
                var s = text.substring(sStart, sEnd).replace(/\s+/g, ' ').trim();
                if (s) sentence = s;
              }
            } catch (sErr) {
              sentence = null;
            }

            return JSON.stringify({
              word: word,
              sentence: sentence,
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
