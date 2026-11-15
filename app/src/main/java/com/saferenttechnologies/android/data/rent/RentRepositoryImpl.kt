package com.saferenttechnologies.android.data.rent

import com.saferenttechnologies.android.domain.model.TransactionPage
import com.saferenttechnologies.android.domain.model.RentTransaction
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.RentApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.RentBucket
import com.saferenttechnologies.android.domain.model.RentDue
import com.saferenttechnologies.android.domain.model.RentDuesPage
import com.saferenttechnologies.android.domain.model.RentReceipt
import com.saferenttechnologies.android.domain.repository.ReceiptFiles
import com.saferenttechnologies.android.domain.repository.RentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RentRepositoryImpl @Inject constructor(
    private val api: RentApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
) : RentRepository {

    override suspend fun dues(bucket: RentBucket, period: String?): AppResult<RentDuesPage> =
        when (val r = safeApiCall("GET rent/dues") { api.dues(bucket.key, period?.ifBlank { null }) }) {
            is ApiResult.Success -> AppResult.Success(RentMapper.page(r.data ?: JsonNull, r.meta))
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun remind(dueId: String): AppResult<String> {
        logger.d(TAG, "send rent reminder")
        return when (val r = safeApiCall("POST rent/remind") { api.remind(dueId, buildJsonObject { }) }) {
            is ApiResult.Success -> AppResult.Success(r.message)
            is ApiResult.NotModified -> AppResult.Success("")
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }
    }

    override suspend fun transactions(page: Int): AppResult<TransactionPage> =
        when (val r = safeApiCall("GET rent/transactions") { api.transactions(page, PAGE_SIZE) }) {
            is ApiResult.Success -> AppResult.Success(RentMapper.transactions(r.data ?: JsonNull, r.meta, page))
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun receiptPdf(receiptId: String): AppResult<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val res = api.receiptPdf(receiptId)
            val body = res.body()
            if (res.isSuccessful && body != null) AppResult.Success(body.use { it.bytes() })
            else AppResult.Failure(AppError.Server(res.code(), message = "Couldn't get the receipt. Please try again."))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network())
        }
    }

    private companion object {
        const val TAG = "SR-Rent"
        const val PAGE_SIZE = 30
    }
}

/** JSON → rent models (pure, unit-tested). */
object RentMapper {
    fun page(data: JsonElement, meta: JsonElement?): RentDuesPage {
        val m = meta as? JsonObject
        val d = data as? JsonObject
        return RentDuesPage(
            items = data.arrayAt("items", "dues").mapNotNull { (it as? JsonObject)?.let(::due) },
            total = m?.text("total").orEmpty().ifBlank { d?.text("total").orEmpty() },
            period = m?.text("period").orEmpty().ifBlank { d?.text("period").orEmpty() },
        )
    }

    fun transactions(data: JsonElement, meta: JsonElement?, requested: Int): TransactionPage {
        val m = meta as? JsonObject
        val page = (m?.get("page") as? JsonPrimitive)?.content?.toIntOrNull() ?: requested
        val pages = (m?.get("totalPages") as? JsonPrimitive)?.content?.toIntOrNull() ?: page
        return TransactionPage(data.arrayAt("items", "transactions").mapNotNull { (it as? JsonObject)?.let(::transaction) }, page, pages)
    }

    fun transaction(o: JsonObject): RentTransaction {
        val tenant = o["tenant"] as? JsonObject
        val prop = o["property"] as? JsonObject
        val r = o["receipt"] as? JsonObject
        return RentTransaction(
            id = o.text("id"),
            kind = o.text("type").ifBlank { o.text("kind") }.uppercase(),
            direction = o.text("direction").ifBlank { "IN" }.uppercase(),
            amount = o.text("amount"),
            status = o.text("status").ifBlank { "SUCCESS" }.uppercase(),
            tenantName = tenant?.text("fullName").orEmpty().ifBlank { o.text("tenantName") },
            propertyTitle = prop?.text("title").orEmpty(),
            place = o.text("place"),
            at = o.text("paidAt").ifBlank { o.text("createdAt") },
            mode = o.text("mode"),
            reference = o.text("reference"),
            receipt = r?.let {
                RentReceipt(it.text("id"), it.text("number"), it.text("amount").ifBlank { o.text("amount") }, it.text("paidAt").ifBlank { o.text("paidAt") }, it.text("mode").ifBlank { o.text("mode") }, it.text("period"))
            }?.takeIf { it.id.isNotBlank() },
        )
    }

    fun due(o: JsonObject): RentDue {
        val tenant = o["tenant"] as? JsonObject
        val prop = o["property"] as? JsonObject
        val r = o["receipt"] as? JsonObject
        return RentDue(
            id = o.text("id"),
            leaseId = o.text("leaseId"),
            tenantName = tenant?.text("fullName").orEmpty().ifBlank { o.text("tenantName") },
            propertyId = prop?.text("id").orEmpty().ifBlank { o.text("propertyId") },
            propertyTitle = prop?.text("title").orEmpty(),
            place = o.text("place"),
            amount = o.text("amount"),
            dueDate = o.text("dueDate"),
            status = o.text("status").uppercase(),
            paidAmount = o.text("paidAmount"),
            paidAt = o.text("paidAt"),
            daysOverdue = (o["daysOverdue"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.toInt(),
            lastReminderAt = o.text("lastReminderAt").ifBlank { null },
            canRemind = (o["canRemind"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false,
            receipt = r?.let {
                RentReceipt(
                    id = it.text("id"),
                    number = it.text("number"),
                    amount = it.text("amount").ifBlank { o.text("paidAmount") },
                    paidAt = it.text("paidAt").ifBlank { o.text("paidAt") },
                    mode = it.text("mode"),
                    period = it.text("period"),
                )
            },
        )
    }
}

@Singleton
class ReceiptFilesImpl @Inject constructor(@ApplicationContext private val context: Context) : ReceiptFiles {
    override fun cache(fileName: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(dir, fileName).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
    }

    override fun saveToDownloads(fileName: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            true
        }.getOrElse {
            resolver.delete(uri, null, null)
            false
        }
    }
}
