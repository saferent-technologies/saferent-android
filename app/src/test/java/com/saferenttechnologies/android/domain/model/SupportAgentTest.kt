package com.saferenttechnologies.android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportAgentTest {
    private val agent = TicketPerson("a1", "Ravi Kumar", "SUPPORT")

    @Test fun `connected only after staff opened it`() {
        assertEquals("Ravi Kumar", SupportRules.agentName(Ticket("t", connectedTo = agent)))
        assertNull(SupportRules.agentName(Ticket("t", assignedTo = agent))) // assigned is not connected
    }

    @Test fun `falls back to last support reply`() {
        val t = Ticket("t", messages = listOf(TicketMessage("m", author = agent)))
        assertEquals("Ravi Kumar", SupportRules.agentName(t))
    }

    @Test fun `nobody yet`() {
        assertNull(SupportRules.agentName(Ticket("t")))
        assertEquals("RK", SupportRules.initials("Ravi Kumar"))
    }
}
