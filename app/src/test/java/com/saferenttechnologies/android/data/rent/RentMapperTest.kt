package com.saferenttechnologies.android.data.rent

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RentMapperTest {
    @Test fun `dues page with receipt and overdue fields`() {
        val page = RentMapper.page(
            Json.parseToJsonElement(
                """{"items":[
                  {"id":"d1","leaseId":"l1","tenant":{"fullName":"Ravi Kumar"},"property":{"id":"p1","title":"Green Heights"},"place":"Flat 302",
                   "amount":"18000.00","status":"paid","paidAmount":"18000","paidAt":"2026-11-05T10:00:00Z",
                   "receipt":{"id":"r1","number":"RCPT-0012","mode":"UPI","period":"Nov 2026"}},
                  {"id":"d2","tenantName":"Anil","amount":9000,"status":"OVERDUE","daysOverdue":12,"canRemind":true}
                ]}""",
            ),
            Json.parseToJsonElement("""{"total":"27000","period":"2026-11"}"""),
        )
        assertEquals("27000", page.total)
        assertEquals("2026-11", page.period)
        val paid = page.items[0]
        assertEquals("Ravi Kumar", paid.tenantName)
        assertEquals("PAID", paid.status)
        assertEquals("RCPT-0012", paid.receipt?.number)
        assertEquals("18000", paid.receipt?.amount)
        val late = page.items[1]
        assertEquals("Anil", late.tenantName)
        assertEquals(12, late.daysOverdue)
        assertTrue(late.canRemind)
        assertNull(late.receipt)
    }
}

class TransactionsMapperTest {
    @Test fun `transactions page with in, out and paging`() {
        val page = RentMapper.transactions(
            Json.parseToJsonElement(
                """{"items":[
                  {"id":"t1","type":"rent","amount":"18000","status":"success","tenant":{"fullName":"Ravi"},"paidAt":"2026-11-05T10:00:00Z","mode":"UPI","receipt":{"id":"r1","number":"RCPT-1"}},
                  {"id":"t2","type":"REFUND","direction":"OUT","amount":"36000","createdAt":"2026-10-30T10:00:00Z","receipt":null}
                ]}""",
            ),
            Json.parseToJsonElement("""{"page":1,"totalPages":3}"""),
            1,
        )
        assertTrue(page.hasMore)
        val (inT, outT) = page.items
        assertTrue(inT.isIn)
        assertEquals("RENT", inT.kind)
        assertEquals("SUCCESS", inT.status)
        assertEquals("2026-11", inT.month)
        assertEquals("RCPT-1", inT.receipt?.number)
        assertEquals(false, outT.isIn)
        assertEquals("2026-10", outT.month)
        assertNull(outT.receipt)
    }
}
