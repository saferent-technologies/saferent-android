package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.domain.model.RentDuesPage

interface RentRepository {
    suspend fun dues(bucket: RentBucket, period: String?): AppResult<RentDuesPage>
    /** Server message ("Reminder sent to Ravi"). */
    suspend fun remind(dueId: String): AppResult<String>
    suspend fun transactions(page: Int): AppResult<com.saferenttechnologies.android.domain.model.TransactionPage>
    /** Tenant: deposit + rent history of a lease. */
    suspend fun tenantPayments(leaseId: String): AppResult<com.saferenttechnologies.android.domain.model.TenantPayments>
    /** Tenant: proof of a payment made outside the app. Server message. */
    suspend fun requestPaymentUpdate(dueId: String, amount: String, transactionId: String, proofJpeg: ByteArray): AppResult<String>
    /** Tenant: "use my deposit for this month" → a request the landlord approves. */
    suspend fun useDeposit(dueId: String): AppResult<String>
    suspend fun paymentRequests(): AppResult<List<com.saferenttechnologies.android.domain.model.PaymentRequest>>
    suspend fun approveRequest(id: String, amount: String): AppResult<String>
    suspend fun rejectRequest(id: String, reason: String): AppResult<String>
    /** Landlord: deposit received. */
    suspend fun recordDeposit(leaseId: String, amount: String, receivedOn: com.saferenttechnologies.android.domain.model.SimpleDate): AppResult<String>
    /** PDF bytes of a receipt. */
    suspend fun receiptPdf(receiptId: String): AppResult<ByteArray>
}

/** Reads a picked screenshot (content:// uri) as a downscaled JPEG; null → unreadable. */
interface ProofImages {
    fun readJpeg(uri: String): ByteArray?
}

/** Saves receipt PDFs on the device (app cache for view / share, Downloads to keep). */
interface ReceiptFiles {
    /** Cached copy → a content:// uri other apps can open. */
    fun cache(fileName: String, bytes: ByteArray): String
    /** Downloads folder (Android 10+). False → not possible here (the screen offers Share instead). */
    fun saveToDownloads(fileName: String, bytes: ByteArray): Boolean
}
