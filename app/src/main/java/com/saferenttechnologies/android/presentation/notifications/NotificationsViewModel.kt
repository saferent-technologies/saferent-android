package com.saferenttechnologies.android.presentation.notifications

import androidx.lifecycle.viewModelScope
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.core.mvi.MviViewModel
import com.saferenttechnologies.android.core.mvi.UiEffect
import com.saferenttechnologies.android.core.mvi.UiIntent
import com.saferenttechnologies.android.core.mvi.UiState
import com.saferenttechnologies.android.core.session.SessionEvent
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppNotification
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DeepLinkRouter
import com.saferenttechnologies.android.domain.model.NavRoute
import com.saferenttechnologies.android.domain.usecase.GetNotificationsUseCase
import com.saferenttechnologies.android.domain.usecase.IncomingPush
import com.saferenttechnologies.android.domain.usecase.MarkAllNotificationsReadUseCase
import com.saferenttechnologies.android.domain.usecase.MarkNotificationReadUseCase
import com.saferenttechnologies.android.domain.usecase.NotificationEvents
import com.saferenttechnologies.android.domain.usecase.RefreshUnreadCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsState(
    val isOpen: Boolean = false,
    val unreadOnly: Boolean = false,
    val items: List<AppNotification> = emptyList(),
    val page: Int = 0,
    val totalPages: Int = 1,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** Bell badge (meta.unread). */
    val unread: Int = 0,
    /** Snackbar text; [MESSAGE_NOT_AVAILABLE] / [MESSAGE_GONE] are mapped to strings by the screen. */
    val message: String? = null,
    /** Push that arrived while the app is open → top banner. */
    val banner: IncomingPush? = null,
    /** Opened from a notification before the session was ready → handled once the dashboard shows. */
    val pendingLink: String? = null,
    val pendingId: String? = null,
    val pendingType: String? = null,
) : UiState {
    val hasMore: Boolean get() = page < totalPages
    val hasPending: Boolean get() = pendingLink != null || pendingId != null
}

sealed interface NotificationsIntent : UiIntent {
    data object Open : NotificationsIntent
    data object Close : NotificationsIntent
    data class SelectTab(val unreadOnly: Boolean) : NotificationsIntent
    data object Refresh : NotificationsIntent
    data object LoadMore : NotificationsIntent
    data class Tap(val notification: AppNotification) : NotificationsIntent
    data object MarkAllRead : NotificationsIntent
    data object RefreshBadge : NotificationsIntent

    /** A notification was tapped in the system tray (intent extras). */
    data class FromPush(val link: String?, val notificationId: String?, val type: String? = null) : NotificationsIntent

    /** Session + dashboard ready → open what [FromPush] remembered. */
    data object ConsumePending : NotificationsIntent
    data object OpenBanner : NotificationsIntent
    data object DismissBanner : NotificationsIntent

    /** The linked item is gone (404). */
    data object ItemGone : NotificationsIntent
    data object MessageShown : NotificationsIntent
}

sealed interface NotificationsEffect : UiEffect {
    data class Navigate(val route: NavRoute) : NotificationsEffect
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val getNotifications: GetNotificationsUseCase,
    private val markRead: MarkNotificationReadUseCase,
    private val markAllRead: MarkAllNotificationsReadUseCase,
    private val refreshUnread: RefreshUnreadCountUseCase,
    private val events: NotificationEvents,
    private val logger: AppLogger,
    sessionEvents: SessionEvents,
) : MviViewModel<NotificationsState, NotificationsIntent, NotificationsEffect>(NotificationsState()) {

    private var loadJob: Job? = null

    init {
        viewModelScope.launch { events.unread.collect { n -> setState { copy(unread = n) } } }
        viewModelScope.launch {
            events.pushes.collect { push ->
                setState { copy(banner = push) }
                onIntent(NotificationsIntent.RefreshBadge)
                if (currentState.isOpen) onIntent(NotificationsIntent.Refresh)
            }
        }
        viewModelScope.launch {
            sessionEvents.events.collect {
                if (it == SessionEvent.SignedOut) {
                    events.setUnread(0)
                    setState { NotificationsState() }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: NotificationsIntent) {
        when (intent) {
            NotificationsIntent.Open -> {
                if (!currentState.isOpen) {
                    setState { copy(isOpen = true, items = emptyList(), page = 0, totalPages = 1, error = null) }
                    load(page = 1, refreshing = false)
                }
            }
            NotificationsIntent.Close -> {
                loadJob?.cancel()
                setState { copy(isOpen = false, items = emptyList(), page = 0, message = null) }
            }
            is NotificationsIntent.SelectTab -> if (intent.unreadOnly != currentState.unreadOnly) {
                setState { copy(unreadOnly = intent.unreadOnly, items = emptyList(), page = 0, totalPages = 1, error = null) }
                load(page = 1, refreshing = false)
            }
            NotificationsIntent.Refresh -> load(page = 1, refreshing = true)
            NotificationsIntent.LoadMore -> if (currentState.hasMore && !currentState.loading) load(currentState.page + 1, refreshing = false)
            is NotificationsIntent.Tap -> tap(intent.notification)
            NotificationsIntent.MarkAllRead -> markAll()
            NotificationsIntent.RefreshBadge -> viewModelScope.launch { refreshUnread() }
            is NotificationsIntent.FromPush -> {
                logger.d(TAG, "opened from a notification (hasLink=${!intent.link.isNullOrBlank()})")
                setState { copy(pendingLink = intent.link.orEmpty(), pendingId = intent.notificationId?.takeIf { it.isNotBlank() }, pendingType = intent.type) }
            }
            NotificationsIntent.ConsumePending -> {
                val link = currentState.pendingLink ?: return
                val id = currentState.pendingId
                val type = currentState.pendingType
                setState { copy(pendingLink = null, pendingId = null, pendingType = null) }
                // Fire and forget: POST notifications/read, then the real badge count.
                if (id != null) viewModelScope.launch {
                    markRead(id, wasUnread = false)
                    refreshUnread()
                }
                // Tray tap carries no text → find the notification for its title (picks the right tab).
                val n = id?.let { nid -> currentState.items.firstOrNull { it.id == nid } ?: findNotification(nid) }
                open(link, listOfNotNull(n?.title, n?.body, type).joinToString(" "))
            }
            NotificationsIntent.OpenBanner -> {
                val push = currentState.banner ?: return
                setState { copy(banner = null) }
                if (push.notificationId.isNotBlank()) viewModelScope.launch { markRead(push.notificationId) }
                markLocallyRead(push.notificationId)
                open(push.link, "${push.title} ${push.type}")
            }
            NotificationsIntent.DismissBanner -> setState { copy(banner = null) }
            NotificationsIntent.ItemGone -> {
                setState { copy(message = MESSAGE_GONE) }
                onIntent(NotificationsIntent.Open)
            }
            NotificationsIntent.MessageShown -> setState { copy(message = null) }
        }
    }

    private fun load(page: Int, refreshing: Boolean) {
        loadJob?.cancel()
        setState { copy(loading = !refreshing, refreshing = refreshing, error = null) }
        val unreadOnly = currentState.unreadOnly
        loadJob = viewModelScope.launch {
            when (val r = getNotifications(page, unreadOnly)) {
                is AppResult.Success -> setState {
                    copy(
                        items = if (page == 1) r.data.items else (items + r.data.items).distinctBy { it.id },
                        page = r.data.page,
                        totalPages = r.data.totalPages,
                        loading = false,
                        refreshing = false,
                    )
                }
                is AppResult.Failure -> setState {
                    copy(loading = false, refreshing = false, error = r.error.takeUnless { it is AppError.AccountBlocked }?.message)
                }
            }
        }
    }

    /** Optimistic read + open the link. */
    private fun tap(n: AppNotification) {
        if (n.isUnread) {
            markLocallyRead(n.id)
            viewModelScope.launch {
                if (markRead(n.id, wasUnread = true) is AppResult.Failure) refreshUnread()
            }
        }
        open(n.link, "${n.title} ${n.body}")
    }

    private suspend fun findNotification(id: String): AppNotification? =
        (getNotifications(page = 1, unreadOnly = false) as? AppResult.Success)?.data?.items?.firstOrNull { it.id == id }

    private fun markLocallyRead(id: String) {
        if (id.isBlank()) return
        setState { copy(items = items.map { if (it.id == id && it.isUnread) it.copy(readAt = READ_NOW) else it }) }
    }

    private suspend fun markAll() {
        val before = currentState.items
        setState { copy(items = if (unreadOnly) emptyList() else items.map { if (it.isUnread) it.copy(readAt = READ_NOW) else it }) }
        if (markAllRead() is AppResult.Failure) {
            setState { copy(items = before) }
            refreshUnread()
        }
    }

    /** Opens what the notification is about (screens the app has), else stays here with a note. */
    private fun open(link: String, hint: String? = null) {
        val route = DeepLinkRouter.route(link, hint)
        logger.d(TAG, "open link → ${route::class.simpleName}")
        when {
            route == NavRoute.Notifications -> onIntent(NotificationsIntent.Open)
            // Home / Explore: leave the notifications list.
            route == NavRoute.Home || route == NavRoute.Explore -> {
                setState { copy(isOpen = false, items = emptyList(), page = 0) }
                sendEffect(NotificationsEffect.Navigate(route))
            }
            // Screens on top of the list → back (or a 404) lands on Notifications.
            route.available -> {
                onIntent(NotificationsIntent.Open)
                sendEffect(NotificationsEffect.Navigate(route))
            }
            else -> {
                setState { copy(message = MESSAGE_NOT_AVAILABLE) }
                onIntent(NotificationsIntent.Open)
            }
        }
    }

    companion object {
        const val MESSAGE_NOT_AVAILABLE = "@not_available"
        const val MESSAGE_GONE = "@gone"
        private const val READ_NOW = "now"
        private const val TAG = "SR-Push"
    }
}
