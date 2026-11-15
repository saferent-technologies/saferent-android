package com.saferenttechnologies.android.domain.usecase

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.NotificationPage
import com.saferenttechnologies.android.domain.repository.NotificationRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A push that arrived while the app process was alive (title for the in-app banner). */
data class IncomingPush(val notificationId: String, val title: String, val link: String, val type: String)

/**
 * App-wide notification signals:
 * - [pushes]: a push arrived (bell + list refresh, in-app banner).
 * - [unread]: bell badge count (meta.unread).
 */
@Singleton
class NotificationEvents @Inject constructor() {
    private val _pushes = MutableSharedFlow<IncomingPush>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pushes: SharedFlow<IncomingPush> = _pushes.asSharedFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    fun newNotification(push: IncomingPush) {
        _pushes.tryEmit(push)
    }

    fun setUnread(count: Int) {
        _unread.value = count.coerceAtLeast(0)
    }
}

class GetNotificationsUseCase @Inject constructor(
    private val repository: NotificationRepository,
    private val events: NotificationEvents,
) {
    suspend operator fun invoke(page: Int, unreadOnly: Boolean): AppResult<NotificationPage> =
        repository.list(page, unreadOnly).also { r -> if (r is AppResult.Success) events.setUnread(r.data.unread) }
}

/** Just the unread count (badge): smallest unread page. */
class RefreshUnreadCountUseCase @Inject constructor(private val getNotifications: GetNotificationsUseCase) {
    suspend operator fun invoke() {
        getNotifications(page = 1, unreadOnly = true)
    }
}

class MarkNotificationReadUseCase @Inject constructor(
    private val repository: NotificationRepository,
    private val events: NotificationEvents,
) {
    /** [wasUnread] → badge drops by one right away (optimistic). */
    suspend operator fun invoke(id: String, wasUnread: Boolean = true): AppResult<Unit> {
        if (wasUnread) events.setUnread(events.unread.value - 1)
        return repository.markRead(id)
    }
}

class MarkAllNotificationsReadUseCase @Inject constructor(
    private val repository: NotificationRepository,
    private val events: NotificationEvents,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val before = events.unread.value
        events.setUnread(0)
        return repository.markAllRead().also { if (it is AppResult.Failure) events.setUnread(before) }
    }
}
