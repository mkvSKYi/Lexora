package com.reader.feature.reader

import android.graphics.RectF
import android.os.Bundle
import android.util.Log
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
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
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
            }
        }

        // Own taps: resolve the tapped word and emit a SelectionEvent. We deliberately do
        // NOT register a DirectionalNavigationAdapter, so a single tap never page-turns;
        // horizontal swipe still pages (handled by the navigator's R2ViewPager, which is
        // independent of InputListener). Returning true from onTap consumes the tap.
        navigator.addInputListener(tapListener)
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onDestroyView() {
        // Balance the addInputListener call in onViewCreated: across view recreation the
        // navigator would otherwise accumulate listeners and leak this fragment via the
        // captured tapListener. ::navigator is only initialized when a session exists.
        if (::navigator.isInitialized) {
            navigator.removeInputListener(tapListener)
        }
        super.onDestroyView()
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
            // Readium 3.1.2 exposes no selection-change callback, only the pull-based
            // currentSelection() snapshot. So we read it on tap: if the user has an active
            // phrase selection it wins; otherwise we resolve the single tapped word.
            val selection = navigator.currentSelection()
            if (selection != null) {
                emitSelection(selection.locator.text.highlight, selection.rect, session)
                return@launch
            }
            val json = navigator.evaluateJavascript(WordResolver.script(point.x, point.y))
            val parsed = parseWordResult(json) ?: return@launch
            session.onSelection(SelectionEvent(parsed.first, parsed.second))
            Log.d(TAG, "word=${parsed.first} rect=${parsed.second}")
        }
    }

    private fun emitSelection(text: String?, rect: RectF?, session: ReaderNavigatorHost.Session) {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty() || rect == null) return
        session.onSelection(SelectionEvent(trimmed, rect))
        Log.d(TAG, "selection=$trimmed rect=$rect")
    }

    /**
     * Parse the JSON returned by [WordResolver]. Readium's [EpubNavigatorFragment.evaluateJavascript]
     * returns the WebView's JSON-encoded result: our JS returns a JSON *string*, so it arrives
     * either as a bare object `{...}` or wrapped/escaped as a quoted string `"{\"word\":...}"`.
     * Returns the word + its rect (device pixels), or null when nothing resolved.
     */
    private fun parseWordResult(raw: String?): Pair<String, RectF>? {
        if (raw == null || raw == "null") return null
        val unwrapped = when {
            raw.startsWith("{") -> raw
            raw.startsWith("\"") -> JSONObject("{\"v\":$raw}").getString("v")
            else -> return null
        }
        return runCatching {
            val obj = JSONObject(unwrapped)
            val word = obj.getString("word").trim()
            if (word.isEmpty()) return null
            val rect = RectF(
                obj.getDouble("left").toFloat(),
                obj.getDouble("top").toFloat(),
                obj.getDouble("right").toFloat(),
                obj.getDouble("bottom").toFloat(),
            )
            word to rect
        }.getOrNull()
    }

    // EpubNavigatorFragment.Listener: only onExternalLinkActivated lacks a default impl.
    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    override fun onExternalLinkActivated(url: AbsoluteUrl) {
        // No-op: external links are not handled in this reader.
    }

    companion object {
        const val ARG_SESSION_ID = "sessionId"
        private const val TAG = "SelectionEvent"
        private val CONTAINER_VIEW_ID = View.generateViewId()
        private const val NAVIGATOR_TAG = "EpubNavigatorFragment"

        fun argsFor(sessionId: Long): Bundle = Bundle().apply {
            putLong(ARG_SESSION_ID, sessionId)
        }
    }
}

/**
 * In-memory hand-off between [ReaderScreen] (Compose) and [EpubReaderFragment].
 *
 * Holds the navigator factory, restored locator and the locator-change callback that the
 * fragment cannot receive through its [Bundle] arguments. Sessions are short-lived and
 * cleared when the screen leaves the composition.
 */
object ReaderNavigatorHost {
    class Session(
        val navigatorFactory: org.readium.r2.navigator.epub.EpubNavigatorFactory,
        val initialLocator: Locator?,
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
