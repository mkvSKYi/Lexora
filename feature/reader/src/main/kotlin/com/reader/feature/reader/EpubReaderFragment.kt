package com.reader.feature.reader

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
import org.readium.r2.navigator.epub.EpubNavigatorFragment
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
