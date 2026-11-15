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

class TenantPaymentsMapperTest {
    @Test fun `deposit, pending on top, paid below`() {
        val p = RentMapper.tenantPayments(
            Json.parseToJsonElement(
                """{"deposit":{"amount":"36000","paid":true,"paidAt":"2026-06-01T00:00:00Z","receipt":{"id":"rd","number":"DEP-1"}},
                    "dues":[
                      {"id":"a","period":"Sep 2026","amount":"18000","status":"PAID","paidAt":"2026-09-04T00:00:00Z","receipt":{"id":"r9"}},
                      {"id":"b","period":"Nov 2026","amount":"18000","status":"DUE","dueDate":"2026-11-05T00:00:00Z"},
                      {"id":"c","period":"Oct 2026","amount":"18000","status":"OVERDUE","dueDate":"2026-10-05T00:00:00Z","daysOverdue":41},
                      {"id":"d","period":"Aug 2026","amount":"18000","status":"PAID","paidAt":"2026-08-03T00:00:00Z"}
                    ]}""",
            ),
        )
        assertTrue(p.deposit!!.paid)
        assertEquals("DEP-1", p.deposit!!.receipt?.number)
        assertEquals(listOf("c", "b"), p.pending.map { it.id })
        assertEquals(listOf("a", "d"), p.paid.map { it.id })
        assertEquals(1, p.overdueCount)
        assertEquals("Sep 2026", p.paid.first().receipt?.period)
    }
}


class PaymentProofTest {
    @Test fun `request mapper and form rules`() {
        val r = RentMapper.request(
            Json.parseToJsonElement(
                """{"id":"q1","due":{"id":"d1","period":"Nov 2026","amount":"18000"},"tenant":{"fullName":"Ravi"},"property":{"title":"Green Heights"},
                   "place":"Flat 302","amount":"18000","transactionId":"432198765012","proofUrl":"/api/v1/files/payment-proofs/q1","status":"pending"}""",
            ) as kotlinx.serialization.json.JsonObject,
        )
        assertEquals("d1", r.dueId)
        assertEquals("Nov 2026", r.period)
        assertEquals("PENDING", r.status)
        assertEquals("432198765012", r.transactionId)
        val rules = com.saferenttechnologies.android.domain.model.PaymentProofRules
        assertTrue(rules.canSubmit("18000", "4321", hasProof = true))
        assertEquals(false, rules.canSubmit("0", "432198", hasProof = true))
        assertEquals(false, rules.canSubmit("18000", "12", hasProof = true))
        assertEquals(false, rules.canSubmit("18000", "432198", hasProof = false))
        val due = RentMapper.due(Json.parseToJsonElement("""{"id":"d1","status":"DUE","paymentRequest":{"status":"pending"}}""") as kotlinx.serialization.json.JsonObject)
        assertTrue(due.requestPending)
    }
}

class RentPlusMaintenanceTest {
    private fun due(s: String) = RentMapper.due(Json.parseToJsonElement(s) as kotlinx.serialization.json.JsonObject)

    @Test fun `rent plus maintenance is the amount to pay`() {
        val d = due("""{"id":"d1","rentAmount":"15000","maintenanceAmount":"2000","amount":"15000"}""")
        assertEquals("17000", d.amount)
        assertTrue(d.hasMaintenance)
    }

    @Test fun `server total wins`() {
        assertEquals("17500", due("""{"id":"d1","totalAmount":"17500","rentAmount":"15000","maintenanceAmount":"2000"}""").amount)
    }

    @Test fun `no breakup falls back to amount`() {
        val d = due("""{"id":"d1","amount":"15000"}""")
        assertEquals("15000", d.amount)
        assertTrue(!d.hasMaintenance)
    }
}
