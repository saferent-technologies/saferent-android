package com.saferenttechnologies.android.data.rent

import java.io.ByteArrayOutputStream
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaType
import com.saferenttechnologies.android.domain.repository.ProofImages
import com.saferenttechnologies.android.domain.model.SimpleDate
import com.saferenttechnologies.android.domain.model.PaymentRequest
import com.saferenttechnologies.android.domain.model.DepositInfo
import com.saferenttechnologies.android.domain.model.TenantPayments
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

    override suspend fun tenantPayments(leaseId: String): AppResult<TenantPayments> =
        when (val r = safeApiCall("GET lease payments") { api.leasePayments(leaseId) }) {
            is ApiResult.Success -> AppResult.Success(RentMapper.tenantPayments(r.data ?: JsonNull))
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun requestPaymentUpdate(dueId: String, amount: String, transactionId: String, proofJpeg: ByteArray): AppResult<String> {
        // The transaction id is the tenant's payment reference → not logged.
        logger.d(TAG, "send payment proof (${proofJpeg.size / 1024} KB)")
        val text = "text/plain".toMediaType()
        return message("POST payment-request") {
            api.requestPaymentUpdate(
                dueId,
                amount.trim().toRequestBody(text),
                transactionId.trim().toRequestBody(text),
                MultipartBody.Part.createFormData("proof", "proof.jpg", proofJpeg.toRequestBody("image/jpeg".toMediaType())),
            )
        }
    }

    override suspend fun useDeposit(dueId: String): AppResult<String> {
        logger.d(TAG, "ask to pay rent from the deposit")
        return message("POST use-deposit") { api.useDeposit(dueId, buildJsonObject { }) }
    }

    override suspend fun paymentRequests(): AppResult<List<PaymentRequest>> =
        when (val r = safeApiCall("GET payment-requests") { api.paymentRequests("PENDING") }) {
            is ApiResult.Success -> AppResult.Success((r.data ?: JsonNull).arrayAt("items", "requests").mapNotNull { (it as? JsonObject)?.let(RentMapper::request) })
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    override suspend fun approveRequest(id: String, amount: String): AppResult<String> =
        message("POST payment-request/approve") { api.approveRequest(id, buildJsonObject { amount.toBigDecimalOrNull()?.let { put("amount", it.toDouble()) } }) }

    override suspend fun rejectRequest(id: String, reason: String): AppResult<String> =
        message("POST payment-request/reject") { api.rejectRequest(id, buildJsonObject { put("reason", reason.trim()) }) }

    override suspend fun recordDeposit(leaseId: String, amount: String, receivedOn: SimpleDate): AppResult<String> =
        message("POST lease deposit") {
            api.recordDeposit(leaseId, buildJsonObject {
                amount.toBigDecimalOrNull()?.let { put("amount", it.toDouble()) }
                put("paidAt", receivedOn.iso())
            })
        }

    private suspend fun message(name: String, request: suspend () -> retrofit2.Response<com.saferenttechnologies.android.data.remote.dto.ApiEnvelope<JsonElement>>): AppResult<String> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> AppResult.Success(r.message)
            is ApiResult.NotModified -> AppResult.Success("")
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

    fun request(o: JsonObject): PaymentRequest {
        val due = o["due"] as? JsonObject
        val tenant = o["tenant"] as? JsonObject
        val prop = o["property"] as? JsonObject
        return PaymentRequest(
            id = o.text("id"),
            dueId = due?.text("id").orEmpty().ifBlank { o.text("dueId") },
            period = due?.text("period").orEmpty().ifBlank { o.text("period") },
            dueAmount = due?.text("amount").orEmpty(),
            amount = o.text("amount"),
            transactionId = o.text("transactionId"),
            proofUrl = o.text("proofUrl"),
            tenantName = tenant?.text("fullName").orEmpty(),
            propertyTitle = prop?.text("title").orEmpty(),
            place = o.text("place"),
            createdAt = o.text("createdAt"),
            status = o.text("status").ifBlank { "PENDING" }.uppercase(),
            kind = o.text("type").ifBlank { "PROOF" }.uppercase(),
        )
    }

    fun tenantPayments(data: JsonElement): TenantPayments {
        val o = data as? JsonObject
        val d = o?.get("deposit") as? JsonObject
        return TenantPayments(
            deposit = d?.let {
                DepositInfo(
                    amount = it.text("amount"),
                    paid = (it["paid"] as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: p.content.equals("true", true) }
                        ?: it.text("status").equals("PAID", true),
                    paidAmount = it.text("paidAmount"),
                    paidAt = it.text("paidAt"),
                    receipt = (it["receipt"] as? JsonObject)?.let { r -> receipt(r, it) },
                    available = it.text("available").ifBlank { null },
                    usableAsRent = listOf("usableAsRent", "depositUsableAsRent").any { k ->
                        (it[k] as? JsonPrimitive)?.let { p -> p.booleanOrNull ?: p.content.equals("true", true) } == true
                    },
                )
            }?.takeIf { it.amount.isNotBlank() || it.paid },
            dues = data.arrayAt("dues", "items").mapNotNull { (it as? JsonObject)?.let(::due) },
        )
    }

    private fun receipt(r: JsonObject, parent: JsonObject) = RentReceipt(
        id = r.text("id"),
        number = r.text("number"),
        amount = r.text("amount").ifBlank { parent.text("paidAmount").ifBlank { parent.text("amount") } },
        paidAt = r.text("paidAt").ifBlank { parent.text("paidAt") },
        mode = r.text("mode"),
        period = r.text("period").ifBlank { parent.text("period") },
    )

    /**
     * What the tenant pays this month = rent + maintenance.
     * Prefers the server's "totalAmount"; else adds "rentAmount" + "maintenanceAmount"; else "amount".
     */
    fun totalOf(o: JsonObject): String {
        o.text("totalAmount").takeIf { it.isNotBlank() }?.let { return it }
        val rent = o.text("rentAmount").toBigDecimalOrNull() ?: return o.text("amount")
        val maint = o.text("maintenanceAmount").toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
        return rent.add(maint).stripTrailingZeros().toPlainString()
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
            amount = totalOf(o),
            rentAmount = o.text("rentAmount"),
            maintenanceAmount = o.text("maintenanceAmount"),
            dueDate = o.text("dueDate"),
            status = o.text("status").uppercase(),
            paidAmount = o.text("paidAmount"),
            paidAt = o.text("paidAt"),
            daysOverdue = (o["daysOverdue"] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }?.toInt(),
            lastReminderAt = o.text("lastReminderAt").ifBlank { null },
            canRemind = (o["canRemind"] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false,
            receipt = r?.let { receipt(it, o) },
            period = o.text("period"),
            requestStatus = (o["paymentRequest"] as? JsonObject)?.text("status")?.uppercase()?.ifBlank { null },
            requestNote = (o["paymentRequest"] as? JsonObject)?.text("rejectReason")?.ifBlank { null },
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

/** Screenshot → JPEG ≤ 1600 px on the long side, quality 80 (small upload, still readable). */
@Singleton
class ProofImagesImpl @Inject constructor(@ApplicationContext private val context: Context) : ProofImages {
    override fun readJpeg(uri: String): ByteArray? = runCatching {
        val u = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val bitmap = context.contentResolver.openInputStream(u)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val scale = MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
        ByteArrayOutputStream().use { bos ->
            out.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            bos.toByteArray()
        }
    }.getOrNull()

    private companion object {
        const val MAX_SIDE = 1600
    }
}
