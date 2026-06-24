package com.reader.feature.reader

import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.KeyEvent
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.input.DragEvent
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.AbsoluteUrl
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Parent fragment that hosts a Readium [EpubNavigatorFragment].
 *
 * The navigator requires a custom [androidx.fragment.app.FragmentFactory] built from the
 * publication's [org.readium.r2.navigator.epub.EpubNavigatorFactory], which cannot travel
 * through a [Bundle]. Instead, the caller registers a [Session] with [ReaderNavigatorHost]
 * and passes only its id via [ARG_SESSION_ID]. The fragment looks the session up in
 * [onCreate] before installing the factory.
 */
class EpubReaderFragment : Fragment(), EpubNavigatorFragment.Listener {

    private var session: ReaderNavigatorHost.Session? = null
    private lateinit var navigator: EpubNavigatorFragment

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val sessionId = arguments?.getLong(ARG_SESSION_ID, -1L) ?: -1L
        val session = ReaderNavigatorHost.get(sessionId)
        this.session = session

        if (session == null) {
            // Process death or missing session: fall back to a dummy factory so the
            // fragment manager can still instantiate something, then bail.
            childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
            super.onCreate(savedInstanceState)
            return
        }

        childFragmentManager.fragmentFactory = session.navigatorFactory.createFragmentFactory(
            initialLocator = session.initialLocator,
            listener = this,
            configuration = EpubNavigatorFragment.Configuration(
                // Sentence translation is driven by our own long-press → JS pipeline, so the
                // native WebView text-selection toolbar must never appear. A no-op ActionMode
                // callback suppresses it (we also inject CSS user-select:none per resource).
                selectionActionModeCallback = NoOpActionModeCallback,
            ),
        )
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val containerView = FragmentContainerView(requireContext()).apply {
            id = CONTAINER_VIEW_ID
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        if (session == null) return containerView

        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(CONTAINER_VIEW_ID, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
            }
        }
        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as EpubNavigatorFragment
        return containerView
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val session = session ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator
                    .onEach { session.onLocatorChanged(it) }
                    .launchIn(this)

                // Apply persisted reading-appearance preferences to the live navigator on the
                // initial Ready and re-apply on every change. submitPreferences is synchronous and
                // main-thread only; collecting here (STARTED) satisfies both.
                session.epubPreferences
                    .onEach { navigator.submitPreferences(it) }
                    .launchIn(this)
            }
        }

        // Own taps: resolve the tapped word and emit a SelectionEvent. We deliberately do
        // NOT register a DirectionalNavigationAdapter, so a single tap never page-turns;
        // horizontal swipe still pages (handled by the navigator's R2ViewPager, which is
        // independent of InputListener). Returning true from onTap consumes the tap.
        navigator.addInputListener(tapListener)

        // Readium's InputListener has no long-press event (verified against 3.1.2: onTap/onDrag/
        // onKey only). Each reflowable page lives in an R2WebView created lazily by an
        // R2EpubPageFragment. We attach an Android GestureDetector to every such WebView as it is
        // created and resolve the sentence at the long-press point via JS. The callback also
        // injects CSS user-select:none so a long-press can't start native text selection.
        navigator.childFragmentManager.registerFragmentLifecycleCallbacks(
            pageFragmentCallbacks,
            false,
        )
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onDestroyView() {
        // Balance the addInputListener call in onViewCreated: across view recreation the
        // navigator would otherwise accumulate listeners and leak this fragment via the
        // captured tapListener. ::navigator is only initialized when a session exists.
        if (::navigator.isInitialized) {
            navigator.removeInputListener(tapListener)
            navigator.childFragmentManager.unregisterFragmentLifecycleCallbacks(
                pageFragmentCallbacks,
            )
        }
        super.onDestroyView()
    }

    /**
     * Attaches long-press handling to each reflowable page WebView as its view is created. The
     * WebView is reached reflectively through Readium's R2EpubPageFragment.getWebView() (the page
     * fragment classes are internal to the navigator module).
     */
    private val pageFragmentCallbacks = object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(
            fm: androidx.fragment.app.FragmentManager,
            f: Fragment,
            v: View,
            savedInstanceState: Bundle?,
        ) {
            val webView = webViewOf(f) ?: return
            attachLongPress(webView)
        }
    }

    /** Returns the [android.webkit.WebView] owned by a Readium R2EpubPageFragment, or null. */
    private fun webViewOf(fragment: Fragment): android.webkit.WebView? =
        runCatching {
            val getter = fragment.javaClass.getMethod("getWebView")
            getter.invoke(fragment) as? android.webkit.WebView
        }.getOrNull()

    /**
     * Installs a [android.view.GestureDetector] long-press listener on [webView] plus CSS that
     * disables native text selection. The touch listener does NOT consume events (returns false),
     * so taps/swipes/scrolling still reach the WebView and the navigator; it only observes them to
     * detect a press-and-hold and capture its point.
     */
    private fun attachLongPress(webView: android.webkit.WebView) {
        if (webView.getTag(R.id.reader_longpress_wired) == true) return
        webView.setTag(R.id.reader_longpress_wired, true)

        // Belt-and-suspenders against native selection: disable long-click selection at the view
        // level and inject user-select:none. The no-op selectionActionModeCallback (set on the
        // navigator Configuration) already suppresses the toolbar; this stops selection starting.
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.evaluateJavascript(DISABLE_SELECTION_CSS, null)

        var lastX = 0f
        var lastY = 0f
        val detector = android.view.GestureDetector(
            webView.context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: android.view.MotionEvent) {
                    onLongPressResolveSentence(lastX, lastY)
                }
            },
        )
        detector.setIsLongpressEnabled(true)
        webView.setOnTouchListener { _, event ->
            lastX = event.x
            lastY = event.y
            detector.onTouchEvent(event)
            false // never consume: paging/scrolling/tap handling stay intact
        }
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private val tapListener = object : InputListener {
        override fun onTap(event: TapEvent): Boolean {
            onTapResolveWord(event)
            return true // consume — do not let anything page-turn on a tap
        }

        override fun onDrag(event: DragEvent): Boolean = false
        override fun onKey(event: KeyEvent): Boolean = false
    }

    /** Resolve the tapped word in the current resource WebView, then emit a [SelectionEvent]. */
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private fun onTapResolveWord(event: TapEvent) {
        val session = session ?: return
        val point = event.point // device pixels relative to the navigator view
        viewLifecycleOwner.lifecycleScope.launch {
            val json = navigator.evaluateJavascript(WordResolver.script(point.x, point.y))
            val parsed = parseResolved(json, "word") ?: return@launch
            session.onSelection(SelectionEvent(parsed.first, parsed.second))
        }
    }

    /**
     * Resolve the sentence containing the long-pressed word and emit a [SelectionEvent] reusing
     * the same translation pipeline as a tap. [pressX]/[pressY] are device pixels relative to the
     * pressed WebView, which (full-bleed, single page) align with the navigator view space the JS
     * and popover anchor against.
     */
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    private fun onLongPressResolveSentence(pressX: Float, pressY: Float) {
        val session = session ?: return
        if (!::navigator.isInitialized) return
        viewLifecycleOwner.lifecycleScope.launch {
            val json = navigator.evaluateJavascript(SentenceResolver.script(pressX, pressY))
            val parsed = parseResolved(json, "sentence") ?: return@launch
            session.onSelection(SelectionEvent(parsed.first, parsed.second))
        }
    }

    /**
     * Parse the JSON returned by [WordResolver]/[SentenceResolver].
     * [EpubNavigatorFragment.evaluateJavascript] returns the WebView's JSON-encoded result: our JS
     * returns a JSON *string*, so it arrives either as a bare object `{...}` or wrapped/escaped as a
     * quoted string. [textKey] is the field holding the resolved text (`word` or `sentence`).
     * Returns the text + its rect (device pixels), or null when nothing resolved.
     */
    private fun parseResolved(raw: String?, textKey: String): Pair<String, RectF>? {
        if (raw == null || raw == "null") return null
        val unwrapped = when {
            raw.startsWith("{") -> raw
            raw.startsWith("\"") -> JSONObject("{\"v\":$raw}").getString("v")
            else -> return null
        }
        return runCatching {
            val obj = JSONObject(unwrapped)
            val text = obj.getString(textKey).trim()
            if (text.isEmpty()) return null
            val rect = RectF(
                obj.getDouble("left").toFloat(),
                obj.getDouble("top").toFloat(),
                obj.getDouble("right").toFloat(),
                obj.getDouble("bottom").toFloat(),
            )
            text to rect
        }.getOrNull()
    }

    // EpubNavigatorFragment.Listener: only onExternalLinkActivated lacks a default impl.
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // No-op: external links are not handled in this reader.
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private val CONTAINER_VIEW_ID = View.generateViewId()
        private const val NAVIGATOR_TAG = "EpubNavigatorFragment"

        // Disables native text selection in the resource document, so a long-press never starts
        // the WebView's selection. Injected once per resource as it loads.
        private const val DISABLE_SELECTION_CSS =
            "(function(){var s=document.createElement('style');" +
                "s.textContent='*{-webkit-user-select:none!important;user-select:none!important;" +
                "-webkit-touch-callout:none!important;}';" +
                "(document.head||document.documentElement).appendChild(s);})();"

        fun argsFor(sessionId: Long): Bundle = Bundle().apply {
            putLong(ARG_SESSION_ID, sessionId)
        }
    }
}

/**
 * An [android.view.ActionMode.Callback] that refuses to create any action mode. Supplied to the
 * navigator's selection callback so the native text-selection toolbar never appears.
 */
private object NoOpActionModeCallback : android.view.ActionMode.Callback {
    override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
    override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?) = false
    override fun onActionItemClicked(
        mode: android.view.ActionMode?,
        item: android.view.MenuItem?,
    ) = false
    override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
}

/**
 * In-memory hand-off between [ReaderScreen] (Compose) and [EpubReaderFragment].
 *
 * Holds the navigator factory, restored locator and the locator-change callback that the
 * fragment cannot receive through its [Bundle] arguments. Sessions are short-lived and
 * cleared when the screen leaves the composition.
 */
@OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
object ReaderNavigatorHost {
    class Session(
        val navigatorFactory: org.readium.r2.navigator.epub.EpubNavigatorFactory,
        val initialLocator: Locator?,
        val epubPreferences: StateFlow<EpubPreferences>,
        val onLocatorChanged: (Locator) -> Unit,
        val onSelection: (SelectionEvent) -> Unit = {},
    )

    private val sessions = ConcurrentHashMap<Long, Session>()
    private val nextId = AtomicLong(0L)

    fun register(session: Session): Long {
        val id = nextId.incrementAndGet()
        sessions[id] = session
        return id
    }

    fun get(id: Long): Session? = sessions[id]

    fun unregister(id: Long) {
        sessions.remove(id)
    }
}
