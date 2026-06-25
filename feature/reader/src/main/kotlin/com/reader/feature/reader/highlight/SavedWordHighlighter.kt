package com.reader.feature.reader.highlight

import com.reader.core.data.model.SavedWord

/** The learning-term set + on/off toggle that drives in-text highlighting. */
data class HighlightState(val terms: Set<String>, val enabled: Boolean)

/**
 * Builds the JavaScript that underlines saved (not-yet-learned) words in the current EPUB
 * resource. Pure (no Android), so the escaping/term logic is unit-testable; only the emitted
 * DOM walk runs in the WebView. The script is idempotent: it unwraps existing highlights before
 * re-wrapping, so an empty/disabled state simply clears.
 */
object SavedWordHighlighter {
    const val DEFAULT_ACCENT = "#9B8CFF"

    /** Lowercased terms of the saved words still being learned. */
    fun learningTerms(words: List<SavedWord>): Set<String> =
        words.filter { !it.learned }.map { it.term.lowercase() }.toSet()

    /** A JSON array literal of the active terms (sorted), or `[]` when disabled/empty. */
    fun termsJson(state: HighlightState): String {
        if (!state.enabled || state.terms.isEmpty()) return "[]"
        return state.terms.sorted().joinToString(prefix = "[", postfix = "]", separator = ",") {
            "\"" + escape(it) + "\""
        }
    }

    fun script(state: HighlightState, accentHex: String = DEFAULT_ACCENT): String =
        TEMPLATE
            .replace("__ACCENT__", accentHex)
            .replace("__TERMS__", termsJson(state))

    private fun escape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    // __TERMS__ is substituted with a JSON array; __ACCENT__ with the hex color.
    private val TEMPLATE = """
(function(){
  var STYLE_ID='lex-hl-style', CLS='lex-hl';
  if(!document.body) return;
  var st=document.getElementById(STYLE_ID);
  if(!st){st=document.createElement('style');st.id=STYLE_ID;(document.head||document.body).appendChild(st);}
  st.textContent='.'+CLS+'{text-decoration:underline;text-decoration-color:__ACCENT__;text-decoration-thickness:2px;text-underline-offset:2px;}';
  var old=document.querySelectorAll('.'+CLS);
  for(var i=0;i<old.length;i++){var e=old[i];var p=e.parentNode;if(!p)continue;p.replaceChild(document.createTextNode(e.textContent),e);p.normalize();}
  var __lexTerms=__TERMS__;
  if(!__lexTerms.length) return;
  function esc(x){return x.replace(/[.*+?^${'$'}{}()|[\]\\]/g,'\\${'$'}&');}
  var re=new RegExp('\\b('+__lexTerms.map(esc).join('|')+')\\b','gi');
  function blocked(n){var p=n.parentNode;while(p){var t=(p.nodeName||'').toLowerCase();if(t==='a'||t==='script'||t==='style')return true;if(p.classList&&p.classList.contains(CLS))return true;p=p.parentNode;}return false;}
  var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null);
  var nodes=[];while(w.nextNode()){var n=w.currentNode;if(n.nodeValue&&n.nodeValue.trim()&&!blocked(n))nodes.push(n);}
  for(var j=0;j<nodes.length;j++){
    var node=nodes[j],text=node.nodeValue;re.lastIndex=0;
    if(!re.test(text))continue;re.lastIndex=0;
    var frag=document.createDocumentFragment(),last=0,m;
    while((m=re.exec(text))!==null){
      if(m.index>last)frag.appendChild(document.createTextNode(text.slice(last,m.index)));
      var span=document.createElement('span');span.className=CLS;span.textContent=m[0];frag.appendChild(span);
      last=m.index+m[0].length;
      if(m.index===re.lastIndex)re.lastIndex++;
    }
    if(last<text.length)frag.appendChild(document.createTextNode(text.slice(last)));
    if(node.parentNode)node.parentNode.replaceChild(frag,node);
  }
})();
""".trimIndent()
}
