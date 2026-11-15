package com.saferenttechnologies.android.domain.model

import com.saferenttechnologies.android.presentation.manage.DetailTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRouterTest {
    private fun route(link: String?) = DeepLinkRouter.route(link)

    @Test fun `property detail`() = assertEquals(NavRoute.PropertyDetail("8f1c", null), route("/properties/8f1c"))

    @Test fun `property detail with any tab`() {
        assertEquals(NavRoute.PropertyDetail("8f1c", "documents"), route("/properties/8f1c?tab=documents"))
        assertEquals(NavRoute.PropertyDetail("8f1c", "history"), route("/properties/8f1c?x=1&tab=history"))
    }

    @Test fun `tab as a path segment, applications of one property, share links, home`() {
        assertEquals(NavRoute.PropertyDetail("8f1c", "invitations"), route("/properties/8f1c/invitations"))
        assertEquals(NavRoute.PropertyDetail("8f1c", "applications"), route("/applications?propertyId=8f1c"))
        assertEquals(NavRoute.ListingDetail("abc"), route("https://dev.saferenttechnologies.com/homes/abc"))
        assertEquals(NavRoute.Explore, route("/browse"))
        assertEquals(NavRoute.Home, route("/dashboard"))
        assertEquals(NavRoute.Home, route("/"))
        assertEquals(NavRoute.Notifications, route("/notifications"))
    }

    @Test fun `property link without a tab - the notification text picks the tab`() {
        assertEquals(NavRoute.PropertyDetail("p1", "applications"), DeepLinkRouter.route("/properties/p1", "New application for Flat 302"))
        assertEquals(NavRoute.PropertyDetail("p1", "invitations"), DeepLinkRouter.route("/properties/p1", "Ravi accepted your invitation"))
        assertEquals(NavRoute.PropertyDetail("p1", "tenants"), DeepLinkRouter.route("/properties/p1", "Move-out requested by your tenant"))
        assertEquals(NavRoute.PropertyDetail("p1", "documents"), DeepLinkRouter.route("/properties/p1", "Ownership document rejected"))
        // Verification news stays on the overview even though the body mentions tenants.
        assertEquals(
            NavRoute.PropertyDetail("p1", null),
            DeepLinkRouter.route("/properties/p1", "Your property was approved \"Royals\" is verified. Publish it to make it visible to tenants."),
        )
        // An explicit tab in the link always wins.
        assertEquals(NavRoute.PropertyDetail("p1", "units"), DeepLinkRouter.route("/properties/p1?tab=units", "New application"))
    }

    @Test fun `which routes have a screen today`() {
        assertTrue(NavRoute.PropertyDetail("p").available)
        assertTrue(NavRoute.Applications.available)
        assertFalse(NavRoute.Wallet.available)
        assertTrue(NavRoute.Rent.available)
        assertTrue(NavRoute.LeaseDetail("l").available)
        assertTrue(NavRoute.Invitation("t").available)
    }

    @Test fun `full url works too`() = assertEquals(NavRoute.PropertyDetail("8f1c", null), route("https://saferent.in/properties/8f1c"))

    @Test fun `tenant and other links`() {
        assertEquals(NavRoute.ListingDetail("l1"), route("/browse/l1"))
        assertEquals(NavRoute.LeaseDetail("le1"), route("/leases/le1"))
        assertEquals(NavRoute.Applications, route("/applications"))
        assertEquals(NavRoute.Invitation("tok"), route("/invite/tok"))
        assertEquals(NavRoute.Rent, route("/rent"))
        assertEquals(NavRoute.Rent, route("/payments"))
        assertEquals(NavRoute.OwnerVerification, route("/verify"))
        assertEquals(NavRoute.Reviews, route("/reviews"))
        assertEquals(NavRoute.Reviews, route("/reviews/pending"))
    }

    @Test fun `unknown or empty goes to notifications`() {
        assertEquals(NavRoute.Notifications, route(null))
        assertEquals(NavRoute.Notifications, route(""))
        assertEquals(NavRoute.Notifications, route("/something/else"))
        assertEquals(NavRoute.MyProperties, route("/properties"))
    }

    @Test fun `relative time`() {
        val min = 60_000L
        val hour = 60 * min
        val today = 1_000 * hour // local midnight (any value works)
        val now = today + 10 * hour
        assertEquals(RelativeTime.JustNow, RelativeTime.of(now - 10_000, now, today))
        assertEquals(RelativeTime.Minutes(5), RelativeTime.of(now - 5 * min, now, today))
        assertEquals(RelativeTime.Hours(3), RelativeTime.of(now - 3 * hour, now, today))
        assertEquals(RelativeTime.Yesterday, RelativeTime.of(today - 2 * hour, now, today))
        assertEquals(RelativeTime.Date(today - 30 * hour), RelativeTime.of(today - 30 * hour, now, today))
    }
}

class DetailTabFromLinkTest {
    @Test fun `tab names map to detail tabs`() {
        assertEquals(DetailTab.DOCUMENTS, DetailTab.fromLink("documents"))
        assertEquals(DetailTab.UNITS, DetailTab.fromLink("rooms"))
        assertEquals(DetailTab.OVERVIEW, DetailTab.fromLink(null))
        assertEquals(DetailTab.UNITS, DetailTab.fromLink("nope"))
        assertEquals(DetailTab.UNITS, DetailTab.fromLink("tenants"))
        assertEquals(DetailTab.OVERVIEW, DetailTab.VISIBLE.last())
        assertEquals(false, DetailTab.TENANTS in DetailTab.VISIBLE)
    }
}

class DeepLinksTest {
    private val view = "android.intent.action.VIEW"

    @Test fun `web and app links become router links`() {
        assertEquals("/homes/abc?unitId=u1", DeepLinks.linkOf(view, "https", "dev.saferenttechnologies.com", "/homes/abc", "unitId=u1"))
        assertEquals("/homes/abc", DeepLinks.linkOf(view, "saferent", "homes", "/abc", null))
        assertEquals("/invite/tok", DeepLinks.linkOf(view, "https", "app.saferenttechnologies.com", "/invite/tok", null))
        assertEquals("/properties/p1", DeepLinks.linkOf(view, "saferent", "properties", "/p1/", null))
        assertEquals(NavRoute.ListingDetail("abc", "u1"), DeepLinkRouter.route(DeepLinks.linkOf(view, "https", "saferenttechnologies.com", "/homes/abc", "unitId=u1")))
    }

    @Test fun `other hosts, plain http or launcher intents are ignored`() {
        assertEquals(null, DeepLinks.linkOf(view, "https", "evil.example.com", "/homes/abc", null))
        assertEquals(null, DeepLinks.linkOf("android.intent.action.MAIN", "https", "saferenttechnologies.com", "/homes/abc", null))
        assertEquals(null, DeepLinks.linkOf(view, "http", "saferenttechnologies.com", "/homes/abc", null))
    }
}

class AppLinkRoutesTest {
    @Test fun `backend app link paths`() {
        assertEquals(NavRoute.ListingDetail("l1", "f302"), DeepLinkRouter.routeOrNull("/homes/l1?unitId=f302"))
        assertEquals(NavRoute.ListingDetail("l1", null), DeepLinkRouter.routeOrNull("/homes/l1/"))
        assertEquals(NavRoute.Explore, DeepLinkRouter.routeOrNull("/browse"))
        assertEquals(NavRoute.Invitation("TOK"), DeepLinkRouter.routeOrNull("/invite/TOK"))
        assertEquals(NavRoute.PropertyDetail("p1", "documents"), DeepLinkRouter.routeOrNull("/properties/p1?tab=documents"))
        assertEquals(NavRoute.PropertyDetail("p1", "applications"), DeepLinkRouter.routeOrNull("/applications?propertyId=p1"))
        assertEquals(NavRoute.Applications, DeepLinkRouter.routeOrNull("/applications"))
        assertEquals(NavRoute.LeaseDetail("le1"), DeepLinkRouter.routeOrNull("/leases/le1"))
        assertEquals(NavRoute.Notifications, DeepLinkRouter.routeOrNull("/notifications"))
        assertEquals(null, DeepLinkRouter.routeOrNull("/something-else"))
    }

    @Test fun `notification-only paths`() {
        assertEquals(NavRoute.Leases, DeepLinkRouter.route("/leases"))
        assertEquals(NavRoute.Rent, DeepLinkRouter.route("/payments"))
        assertEquals(NavRoute.RentRequests, DeepLinkRouter.route("/rent/requests"))
        assertEquals(NavRoute.Wallet, DeepLinkRouter.route("/wallet"))
        assertEquals(NavRoute.Maintenance, DeepLinkRouter.route("/maintenance"))
        assertEquals(NavRoute.SupportTicket("t9"), DeepLinkRouter.route("/support/t9"))
        assertEquals(NavRoute.Reviews, DeepLinkRouter.route("/reviews/pending"))
        assertEquals(NavRoute.Referrals, DeepLinkRouter.route("/referrals"))
        assertEquals(NavRoute.OwnerVerification, DeepLinkRouter.route("/verify"))
        assertEquals(NavRoute.MyDocuments, DeepLinkRouter.route("/my-documents"))
        assertEquals(NavRoute.Home, DeepLinkRouter.route("/dashboard"))
        assertEquals(NavRoute.Notifications, DeepLinkRouter.route("/unknown/thing"))
    }

    @Test fun `role check for app links`() {
        assertEquals(false, DeepLinks.allowedFor(NavRoute.PropertyDetail("p1"), isTenant = true))
        assertEquals(true, DeepLinks.allowedFor(NavRoute.PropertyDetail("p1"), isTenant = false))
        assertEquals(true, DeepLinks.allowedFor(NavRoute.ListingDetail("l1"), isTenant = true))
        assertEquals(false, DeepLinks.allowedFor(NavRoute.Explore, isTenant = false))
    }
}


class LeaseLinksTest {
    @Test fun `bare leases link plus lease id opens that lease`() {
        assertEquals("/leases/le1", LeaseLinks.withLeaseId("/leases", "le1"))
        assertEquals("/leases/le1", LeaseLinks.withLeaseId("", "le1"))
        assertEquals("/leases/le9", LeaseLinks.withLeaseId("/leases/le9", "le1"))
        assertEquals("/properties/p1", LeaseLinks.withLeaseId("/properties/p1", "le1"))
        assertEquals("/leases", LeaseLinks.withLeaseId("/leases", null))
        assertEquals(NavRoute.LeaseDetail("le1"), DeepLinkRouter.route(LeaseLinks.withLeaseId("/leases", "le1")))
    }
}
