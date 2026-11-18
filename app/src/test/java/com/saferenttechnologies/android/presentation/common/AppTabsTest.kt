package com.saferenttechnologies.android.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppTabsTest {
    @Test fun `landlord tabs`() {
        assertEquals(AppTab.HOME, AppTabs.current(AppTabs.Screens(landlord = true, overlayOpen = false, dashboardShown = true)))
        assertEquals(AppTab.PROPERTIES, AppTabs.current(AppTabs.Screens(landlord = true, overlayOpen = false, myPropertiesOpen = true, dashboardShown = true)))
        assertEquals(AppTab.TENANTS, AppTabs.current(AppTabs.Screens(landlord = true, overlayOpen = false, tenantsOpen = true, dashboardShown = true)))
        assertEquals(AppTab.REQUESTS, AppTabs.current(AppTabs.Screens(landlord = true, overlayOpen = false, requestsListOpen = true, dashboardShown = true)))
        // A detail screen on top → no bar.
        assertNull(AppTabs.current(AppTabs.Screens(landlord = true, overlayOpen = true, myPropertiesOpen = true, dashboardShown = true)))
    }

    @Test fun `tenant tabs`() {
        assertEquals(AppTab.EXPLORE, AppTabs.current(AppTabs.Screens(landlord = false, overlayOpen = false, exploreShown = true, dashboardShown = true)))
        assertEquals(AppTab.HOME, AppTabs.current(AppTabs.Screens(landlord = false, overlayOpen = false, dashboardShown = true)))
        assertEquals(AppTab.DOCUMENTS, AppTabs.current(AppTabs.Screens(landlord = false, overlayOpen = false, documentsVaultOpen = true, exploreShown = true)))
        assertNull(AppTabs.current(AppTabs.Screens(landlord = false, overlayOpen = false)))
    }
}
