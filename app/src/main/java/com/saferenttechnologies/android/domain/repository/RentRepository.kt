package com.saferenttechnologies.android.domain.repository

import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.domain.model.RentDuesPage

interface RentRepository {
    suspend fun dues(bucket: RentBucket, period: String?): AppResult<RentDuesPage>
    /** Server message ("Reminder sent to Ravi"). */
    suspend fun remind(dueId: String): AppResult<String>
    suspend fun transactions(page: Int): AppResult<com.saferenttechnologies.android.domain.model.TransactionPage>
    /** PDF bytes of a receipt. */
    suspend fun receiptPdf(receiptId: String): AppResult<ByteArray>
}

/** Saves receipt PDFs on the device (app cache for view / share, Downloads to keep). */
interface ReceiptFiles {
    /** Cached copy → a content:// uri other apps can open. */
    fun cache(fileName: String, bytes: ByteArray): String
    /** Downloads folder (Android 10+). False → not possible here (the screen offers Share instead). */
    fun saveToDownloads(fileName: String, bytes: ByteArray): Boolean
}
