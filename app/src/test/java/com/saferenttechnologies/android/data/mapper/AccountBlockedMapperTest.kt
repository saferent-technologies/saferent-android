package com.saferenttechnologies.android.data.mapper

import com.saferenttechnologies.android.core.log.NoOpLogger
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.data.remote.dto.PhoneCompleteDto
import com.saferenttechnologies.android.domain.model.AccountStatus
import com.saferenttechnologies.android.domain.model.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AccountBlockedMapperTest {

    private val json = SafeRentJson.instance

    private val deletedBody = """
        {
          "success": false,
          "message": "This account has been deleted by SafeRent and can no longer be used.",
          "errorCode": "ACCOUNT_DELETED",
          "reason": "ACCOUNT_DELETED",
          "details": {
            "accountStatus": "DELETED",
            "title": "Account deleted",
            "reason": "Fake listings reported by several tenants",
            "at": "2026-11-14T10:32:05.120Z",
            "support": {
              "email": "support@saferenttechnologies.com",
              "phone": "+919876543210",
              "whatsapp": "+919876543210",
              "helpCenterUrl": "https://saferenttechnologies.com/help"
            }
          },
          "data": null
        }
    """.trimIndent()

    private fun error403(body: String): Response<ApiEnvelope<PhoneCompleteDto>> =
        Response.error(403, body.toResponseBody("application/json".toMediaType()))

    @Test
    fun `403 ACCOUNT_DELETED through SafeApiCall becomes AccountBlocked with all details`() = runTest {
        val r = SafeApiCall(json, NoOpLogger)("POST auth/phone/complete") { error403(deletedBody) }
        val info = ((r as ApiResult.Failure).error as AppError.AccountBlocked).info
        assertEquals(AccountStatus.DELETED, info.status)
        assertEquals("Account deleted", info.title)
        assertEquals("This account has been deleted by SafeRent and can no longer be used.", info.message)
        assertEquals("Fake listings reported by several tenants", info.reason)
        assertEquals(1_794_652_325_120, info.changedAtMillis) // 2026-11-14T10:32:05.120Z
        assertEquals("support@saferenttechnologies.com", info.support?.email)
        assertEquals("https://saferenttechnologies.com/help", info.support?.helpCenterUrl)
    }

    @Test
    fun `ACCOUNT_DISABLED with null reason, date and support`() {
        val body = """{"success":false,"message":"Disabled","errorCode":"ACCOUNT_DISABLED",
            "details":{"accountStatus":"SUSPENDED","title":null,"reason":null,"at":null,"support":null}}"""
        val info = accountBlockedFromBody(json, 403, body)!!
        assertEquals(AccountStatus.SUSPENDED, info.status)
        assertEquals("", info.title) // UI falls back to strings.xml
        assertNull(info.reason)
        assertNull(info.changedAtMillis)
        assertNull(info.support)
    }

    @Test
    fun `missing details still blocked - status from errorCode`() {
        val deleted = accountBlockedFromBody(json, 403, """{"success":false,"message":"","errorCode":"ACCOUNT_DELETED"}""")!!
        assertEquals(AccountStatus.DELETED, deleted.status)
        val disabled = accountBlockedFromBody(json, 403, """{"success":false,"errorCode":"ACCOUNT_DISABLED"}""")!!
        assertEquals(AccountStatus.DEACTIVATED, disabled.status)
    }

    @Test
    fun `unknown status is DEACTIVATED, other 403s are not blocked`() {
        val odd = accountBlockedFromBody(json, 403, """{"errorCode":"ACCOUNT_DISABLED","details":{"accountStatus":"FROZEN"}}""")!!
        assertEquals(AccountStatus.DEACTIVATED, odd.status)
        assertNull(accountBlockedFromBody(json, 403, """{"errorCode":"FORBIDDEN"}"""))
        assertNull(accountBlockedFromBody(json, 500, deletedBody))
        assertTrue(AccountStatus.fromServer(null) == AccountStatus.ACTIVE)
    }

    @Test
    fun `lenient - other 4xx codes, reason field or details status also count`() {
        assertEquals(AccountStatus.DELETED, accountBlockedFromBody(json, 401, deletedBody)!!.status)
        val byReason = accountBlockedFromBody(json, 403, """{"errorCode":"FORBIDDEN","reason":"ACCOUNT_SUSPENDED"}""")!!
        assertEquals(AccountStatus.SUSPENDED, byReason.status)
        val byDetails = accountBlockedFromBody(json, 403, """{"errorCode":"FORBIDDEN","details":{"accountStatus":"DEACTIVATED"}}""")!!
        assertEquals(AccountStatus.DEACTIVATED, byDetails.status)
    }
}
