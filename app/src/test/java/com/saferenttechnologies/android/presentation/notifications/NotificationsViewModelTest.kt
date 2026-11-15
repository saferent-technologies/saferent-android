package com.saferenttechnologies.android.presentation.notifications

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.core.session.SessionEvents
import com.saferenttechnologies.android.domain.model.AppNotification
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.NavRoute
import com.saferenttechnologies.android.domain.model.NotificationPage
import com.saferenttechnologies.android.domain.repository.NotificationRepository
import com.saferenttechnologies.android.domain.usecase.GetNotificationsUseCase
import com.saferenttechnologies.android.domain.usecase.IncomingPush
import com.saferenttechnologies.android.domain.usecase.MarkAllNotificationsReadUseCase
import com.saferenttechnologies.android.domain.usecase.MarkNotificationReadUseCase
import com.saferenttechnologies.android.domain.usecase.NotificationEvents
import com.saferenttechnologies.android.domain.usecase.RefreshUnreadCountUseCase
import com.saferenttechnologies.android.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NotificationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private class FakeRepo : NotificationRepository {
        var items = listOf(
            AppNotification("n1", "Approved", "b", link = "/properties/p1"),
            AppNotification("n2", "Paid", "b", link = "/rent"),
            AppNotification("n3", "Old", "b", readAt = "2026-11-01T00:00:00.000Z"),
        )
        val read = mutableListOf<String>()
        var allRead = 0

        override suspend fun list(page: Int, unreadOnly: Boolean) = AppResult.Success(
            NotificationPage(if (unreadOnly) items.filter { it.isUnread } else items, 1, 1, items.count { it.isUnread }),
        )
        override suspend fun markRead(id: String): AppResult<Unit> {
            read += id
            items = items.map { if (it.id == id) it.copy(readAt = "x") else it }
            return AppResult.Success(Unit)
        }
        override suspend fun markAllRead(): AppResult<Unit> {
            allRead++
            items = items.map { it.copy(readAt = "x") }
            return AppResult.Success(Unit)
        }
    }

    private val repo = FakeRepo()
    private val events = NotificationEvents()
    private fun vm(): NotificationsViewModel {
        val get = GetNotificationsUseCase(repo, events)
        return NotificationsViewModel(
            get,
            MarkNotificationReadUseCase(repo, events),
            MarkAllNotificationsReadUseCase(repo, events),
            RefreshUnreadCountUseCase(get),
            events,
            NoOpLogger,
            SessionEvents(),
        )
    }

    @Test
    fun `tap marks read right away, badge drops, property link navigates`() = runTest {
        val vm = vm()
        vm.onIntent(NotificationsIntent.Open)
        assertEquals(2, vm.state.value.unread)

        vm.onIntent(NotificationsIntent.Tap(vm.state.value.items.first()))
        assertFalse(vm.state.value.items.first().isUnread)
        assertEquals(1, vm.state.value.unread)
        assertEquals(listOf("n1"), repo.read)
        assertEquals(NotificationsEffect.Navigate(NavRoute.PropertyDetail("p1")), vm.effect.first())
    }

    @Test
    fun `mark all as read clears the badge`() = runTest {
        val vm = vm()
        vm.onIntent(NotificationsIntent.Open)
        vm.onIntent(NotificationsIntent.MarkAllRead)
        assertEquals(0, vm.state.value.unread)
        assertTrue(vm.state.value.items.none { it.isUnread })
        assertEquals(1, repo.allRead)
    }

    @Test
    fun `opened from the tray - waits for the session, then marks read and opens`() = runTest {
        val vm = vm()
        vm.onIntent(NotificationsIntent.FromPush("/properties/p9?tab=documents", "n9"))
        assertTrue(vm.state.value.hasPending)
        assertTrue(repo.read.isEmpty()) // nothing before the dashboard is ready

        vm.onIntent(NotificationsIntent.ConsumePending)
        assertFalse(vm.state.value.hasPending)
        assertEquals(listOf("n9"), repo.read)
        assertTrue(vm.state.value.isOpen) // Notifications stays underneath
        assertEquals(NotificationsEffect.Navigate(NavRoute.PropertyDetail("p9", "documents")), vm.effect.first())
    }

    @Test
    fun `screen not in the app yet stays on notifications with a note`() = runTest {
        val vm = vm()
        vm.onIntent(NotificationsIntent.FromPush("/rent", null))
        vm.onIntent(NotificationsIntent.ConsumePending)
        assertTrue(vm.state.value.isOpen)
        assertEquals(NotificationsViewModel.MESSAGE_NOT_AVAILABLE, vm.state.value.message)
    }

    @Test
    fun `push while open shows the banner and refreshes the badge`() = runTest {
        val vm = vm()
        events.newNotification(IncomingPush("n1", "Approved", "/properties/p1", "GENERAL"))
        assertEquals("Approved", vm.state.value.banner?.title)
        assertEquals(2, vm.state.value.unread)
        vm.onIntent(NotificationsIntent.DismissBanner)
        assertNull(vm.state.value.banner)
    }

    @Test fun `badge text`() {
        assertEquals("3", badgeText(3))
        assertEquals("9+", badgeText(12))
    }
}
