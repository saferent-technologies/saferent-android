package com.saferenttechnologies.android.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.saferenttechnologies.android.R
import com.saferenttechnologies.android.core.ui.sdp
import com.saferenttechnologies.android.core.ui.ssp
import com.saferenttechnologies.android.presentation.auth.AuthColors
import com.saferenttechnologies.android.ui.theme.Inter

/** Bottom bar tabs. Landlord: Home, Properties, Tenants, Requests. Tenant: Home, Explore, Requests, Documents. */
enum class AppTab(val icon: Int, val label: Int) {
    HOME(R.drawable.ic_home_outline, R.string.tab_home),
    PROPERTIES(R.drawable.ic_building, R.string.tab_properties),
    TENANTS(R.drawable.ic_people, R.string.tab_tenants),
    EXPLORE(R.drawable.ic_search, R.string.tab_explore),
    REQUESTS(R.drawable.ic_edit, R.string.tab_requests),
    DOCUMENTS(R.drawable.ic_lock, R.string.tab_documents),
    ;

    companion object {
        val LANDLORD = listOf(HOME, PROPERTIES, TENANTS, REQUESTS)
        val TENANT = listOf(HOME, EXPLORE, REQUESTS, DOCUMENTS)
    }
}

/** App bottom bar (only on the tab screens). [badges] = red count per tab (e.g. requests waiting). */
@Composable
fun AppBottomBar(tabs: List<AppTab>, selected: AppTab, onSelect: (AppTab) -> Unit, badges: Map<AppTab, Int> = emptyMap()) {
    Column(Modifier.fillMaxWidth().background(AuthColors.Card)) {
        Box(Modifier.fillMaxWidth().height(1.sdp).background(AuthColors.Border))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.sdp, vertical = 6.sdp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEach { tab ->
                val on = tab == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.sdp))
                        .clickable(role = Role.Tab) { onSelect(tab) }
                        .padding(vertical = 6.sdp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(if (on) AuthColors.OrangeSoft else Color.Transparent)
                                .padding(horizontal = 16.sdp, vertical = 4.sdp),
                        ) {
                            Icon(painterResource(tab.icon), null, tint = if (on) AuthColors.OrangeText else AuthColors.Body, modifier = Modifier.size(20.sdp))
                        }
                        val count = badges[tab] ?: 0
                        if (count > 0) {
                            Box(
                                Modifier.align(Alignment.TopEnd).offset(x = 2.sdp, y = (-2).sdp).size(16.sdp).clip(CircleShape).background(AuthColors.Error),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (count > 9) "9+" else count.toString(), color = Color.White, fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 9.ssp) }
                        }
                    }
                    Spacer(Modifier.height(2.sdp))
                    Text(
                        stringResource(tab.label),
                        color = if (on) AuthColors.OrangeText else AuthColors.Body,
                        fontFamily = Inter,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 11.ssp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Which tab a screen state is (pure, unit-tested): null = not a tab screen → no bar. */
object AppTabs {
    data class Screens(
        val landlord: Boolean,
        val overlayOpen: Boolean,
        val myPropertiesOpen: Boolean = false,
        val tenantsOpen: Boolean = false,
        val requestsListOpen: Boolean = false,
        val documentsVaultOpen: Boolean = false,
        val exploreShown: Boolean = false,
        val dashboardShown: Boolean = false,
    )

    fun current(s: Screens): AppTab? = when {
        s.overlayOpen -> null
        s.requestsListOpen -> AppTab.REQUESTS
        s.landlord && s.myPropertiesOpen -> AppTab.PROPERTIES
        s.landlord && s.tenantsOpen -> AppTab.TENANTS
        !s.landlord && s.documentsVaultOpen -> AppTab.DOCUMENTS
        !s.landlord && s.exploreShown -> AppTab.EXPLORE
        s.dashboardShown -> AppTab.HOME
        else -> null
    }
}
