package com.reader.feature.reader

/**
 * JavaScript that resolves the *sentence* under a long-press point inside a Readium resource
 * WebView. Mirrors [WordResolver]'s coordinate handling: input `viewX`/`viewY` are device
 * pixels (the press point relative to the WebView), divided by `devicePixelRatio` to get CSS px
 * for [Document.caretRangeFromPoint]; the returned rect is converted back to device pixels.
 *
 * From the caret it first resolves the pressed *word* (same boundary logic as [WordResolver],
 * used only to anchor the popover), then expands OUTWARD to sentence boundaries within the
 * caret's containing block element. To support sentences that span inline elements (`<em>`,
 * `<a>`, …) it walks the block's descendant text nodes in document order and works on the
 * block's concatenated `textContent`, mapping the caret offset into that flat string.
 *
 * Sentence boundaries: a terminator `.!?…` optionally followed by closing quotes/brackets and
 * whitespace marks the end; the symmetric pattern (or start-of-block) marks the start.
 *
 * Returns the JSON string `{"sentence","left","top","right","bottom"}`, or `null` when no
 * sentence can be resolved (e.g. press not on text).
 */
internal object SentenceResolver {

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

            var caretNode = range.startContainer;
            if (!caretNode || caretNode.nodeType !== Node.TEXT_NODE) return null;

            // Block element containing the caret. Walk up until a block-level (or body) ancestor.
            function isBlock(el) {
              if (!el || el.nodeType !== Node.ELEMENT_NODE) return false;
              var d = window.getComputedStyle(el).display;
              return d === 'block' || d === 'list-item' || d === 'table-cell' ||
                     el.tagName === 'BODY' || el.tagName === 'P' || el.tagName === 'LI' ||
                     el.tagName === 'BLOCKQUOTE' || el.tagName === 'TD';
            }
            var block = caretNode.parentNode;
            while (block && block.nodeType === Node.ELEMENT_NODE && !isBlock(block) &&
                   block.tagName !== 'BODY') {
              block = block.parentNode;
            }
            if (!block || block.nodeType !== Node.ELEMENT_NODE) block = caretNode.parentNode;
            if (!block) return null;

            // Collect descendant text nodes of the block in document order, building the flat
            // text and a per-node [start,end) index map so we can translate flat offsets back to
            // (node, offset) for the word-rect anchor.
            var nodes = [];
            var flat = '';
            (function collect(n) {
              if (n.nodeType === Node.TEXT_NODE) {
                var t = n.textContent || '';
                if (t.length) {
                  nodes.push({ node: n, start: flat.length, end: flat.length + t.length });
                  flat += t;
                }
              } else if (n.nodeType === Node.ELEMENT_NODE) {
                for (var c = n.firstChild; c; c = c.nextSibling) collect(c);
              }
            })(block);
            if (!flat) return null;

            // Map the caret (caretNode, range.startOffset) to a flat offset.
            var caretFlat = -1;
            for (var i = 0; i < nodes.length; i++) {
              if (nodes[i].node === caretNode) {
                var off = range.startOffset;
                if (off > (nodes[i].end - nodes[i].start)) off = nodes[i].end - nodes[i].start;
                caretFlat = nodes[i].start + off;
                break;
              }
            }
            if (caretFlat < 0) return null;

            // --- Resolve the pressed word (for the anchor rect), same logic as WordResolver. ---
            function isWordChar(ch) {
              return /[\p{L}\p{M}\p{N}'’\-]/u.test(ch);
            }
            var wStart = caretFlat, wEnd = caretFlat;
            while (wStart > 0 && isWordChar(flat.charAt(wStart - 1))) wStart--;
            while (wEnd < flat.length && isWordChar(flat.charAt(wEnd))) wEnd++;

            // --- Expand outward to sentence boundaries on the flat text. ---
            var terminators = '.!?…';
            function isTerminator(ch) { return terminators.indexOf(ch) !== -1; }
            function isClosing(ch) { return /["'’”»)\]}]/.test(ch); }

            // Sentence start: scan left for a terminator (skipping any closing punctuation that
            // follows it), the boundary is just after that closer run + whitespace.
            var sStart = 0;
            for (var p = caretFlat - 1; p > 0; p--) {
              if (isTerminator(flat.charAt(p))) {
                var q = p + 1;
                while (q < flat.length && isClosing(flat.charAt(q))) q++;
                while (q < flat.length && /\s/.test(flat.charAt(q))) q++;
                if (q <= caretFlat) { sStart = q; break; }
              }
            }

            // Sentence end: scan right for a terminator, include trailing closers.
            var sEnd = flat.length;
            for (var r = Math.max(caretFlat, wEnd); r < flat.length; r++) {
              if (isTerminator(flat.charAt(r))) {
                var e = r + 1;
                while (e < flat.length && isClosing(flat.charAt(e))) e++;
                sEnd = e;
                break;
              }
            }

            if (sEnd <= sStart) return null;
            var sentence = flat.substring(sStart, sEnd).replace(/\s+/g, ' ').trim();
            if (!sentence) return null;

            // --- Anchor rect: the pressed word's bounding box (fallback to press point). ---
            function flatToNode(flatOffset) {
              for (var k = 0; k < nodes.length; k++) {
                if (flatOffset >= nodes[k].start && flatOffset <= nodes[k].end) {
                  return { node: nodes[k].node, offset: flatOffset - nodes[k].start };
                }
              }
              return null;
            }
            var rect = null;
            var a = flatToNode(wStart), b = flatToNode(wEnd);
            if (a && b && wEnd > wStart) {
              try {
                var wr = document.createRange();
                wr.setStart(a.node, a.offset);
                wr.setEnd(b.node, b.offset);
                var rr = wr.getBoundingClientRect();
                if (rr && (rr.width > 0 || rr.height > 0)) rect = rr;
              } catch (re) { rect = null; }
            }
            var left, top, right, bottom;
            if (rect) {
              left = rect.left; top = rect.top; right = rect.right; bottom = rect.bottom;
            } else {
              left = cssX - 1; top = cssY - 1; right = cssX + 1; bottom = cssY + 1;
            }

            return JSON.stringify({
              sentence: sentence,
              left: left * dpr,
              top: top * dpr,
              right: right * dpr,
              bottom: bottom * dpr
            });
          } catch (e) {
            return null;
          }
        })();
        """.trimIndent()
}
