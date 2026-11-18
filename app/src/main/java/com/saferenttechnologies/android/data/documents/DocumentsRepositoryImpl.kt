package com.saferenttechnologies.android.data.documents

import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.DocumentsApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DocPage
import com.saferenttechnologies.android.domain.model.DocShare
import com.saferenttechnologies.android.domain.model.DocType
import com.saferenttechnologies.android.domain.model.DocumentVault
import com.saferenttechnologies.android.domain.model.ShareTarget
import com.saferenttechnologies.android.domain.model.SharedDocuments
import com.saferenttechnologies.android.domain.model.TenantDoc
import com.saferenttechnologies.android.domain.repository.DocumentsRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Never logs document types, numbers, names or urls — only counts. */
@Singleton
class DocumentsRepositoryImpl @Inject constructor(
    private val api: DocumentsApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
    private val http: okhttp3.OkHttpClient,
) : DocumentsRepository {

    override suspend fun vault() = call("GET me/documents", { api.vault() }) { DocumentsMapper.vault(it) }

    override suspend fun upload(type: String, pages: List<ByteArray>, pdf: ByteArray?): AppResult<DocumentVault> {
        logger.d(TAG, "upload document (pages=${pages.size}, pdf=${pdf != null})")
        val parts = if (pdf != null) {
            listOf(MultipartBody.Part.createFormData("files", "document.pdf", pdf.toRequestBody("application/pdf".toMediaType())))
        } else {
            pages.mapIndexed { i, b -> MultipartBody.Part.createFormData("files", "page-$i.jpg", b.toRequestBody("image/jpeg".toMediaType())) }
        }
        return mutate("POST me/documents") { api.upload(type.toRequestBody("text/plain".toMediaType()), parts) }
    }

    override suspend fun delete(id: String) = mutate("DELETE me/document") { api.delete(id) }
    override suspend fun share(id: String, leaseId: String) = mutate("POST document/share") { api.share(id, buildJsonObject { put("leaseId", leaseId) }) }
    override suspend fun unshare(id: String, leaseId: String) = mutate("DELETE document/share") { api.unshare(id, leaseId) }

    override suspend fun fileBytes(url: String): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val full = if (url.startsWith("http")) url else com.saferenttechnologies.android.BuildConfig.API_BASE_URL.trimEnd('/') + "/" + url.trimStart('/')
        runCatching {
            http.newCall(okhttp3.Request.Builder().url(full).header("Cache-Control", "no-store").build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    override suspend fun shared(leaseId: String) = call("GET lease/documents", { api.shared(leaseId) }) { DocumentsMapper.shared(it) }

    /** A change → server sends the new vault back (else we load it again). */
    private suspend fun mutate(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>): AppResult<DocumentVault> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> DocumentsMapper.vault(r.data ?: JsonNull)?.takeIf { it.types.isNotEmpty() || it.documents.isNotEmpty() }
                ?.let { AppResult.Success(it) } ?: vault()
            is ApiResult.NotModified -> vault()
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private suspend fun <R> call(name: String, request: suspend () -> Response<ApiEnvelope<JsonElement>>, map: (JsonElement) -> R?): AppResult<R> =
        when (val r = safeApiCall(name, request)) {
            is ApiResult.Success -> map(r.data ?: JsonNull)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
            is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
            is ApiResult.Failure -> AppResult.Failure(r.error)
        }

    private companion object { const val TAG = "SR-Documents" }
}

/** JSON → document models (pure, unit-tested). */
object DocumentsMapper {
    fun looksLikePdf(bytes: ByteArray): Boolean = com.saferenttechnologies.android.domain.model.DocumentRules.looksLikePdf(bytes)

    private fun JsonObject.int(k: String) = (this[k] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt()

    fun vault(data: JsonElement): DocumentVault? {
        val o = data as? JsonObject ?: return null
        return DocumentVault(
            documents = o.arrayAt("documents").mapNotNull { (it as? JsonObject)?.let(::doc) }.filter { it.id.isNotBlank() },
            types = o.arrayAt("types").mapNotNull { t ->
                val to = t as? JsonObject ?: return@mapNotNull null
                to.text("key").ifBlank { null }?.let { DocType(it, to.text("label").ifBlank { it }, to.int("pages") ?: 1) }
            },
            leases = o.arrayAt("leases", "shareTargets").mapNotNull { l ->
                val lo = l as? JsonObject ?: return@mapNotNull null
                lo.text("leaseId").ifBlank { lo.text("id") }.ifBlank { null }?.let {
                    ShareTarget(it, (lo["landlord"] as? JsonObject)?.text("fullName").orEmpty().ifBlank { lo.text("landlordName") }, lo.text("propertyTitle"), lo.text("place"))
                }
            },
        )
    }

    fun doc(o: JsonObject) = TenantDoc(
        id = o.text("id"),
        type = o.text("type").uppercase(),
        typeLabel = o.text("typeLabel").ifBlank { o.text("type") },
        pages = o.arrayAt("pages").mapNotNull { p ->
            val po = p as? JsonObject ?: return@mapNotNull null
            po.text("url").ifBlank { null }?.let { DocPage(po.text("id"), it, po.text("mimeType").ifBlank { po.text("contentType") }) }
        },
        uploadedAt = o.text("uploadedAt").ifBlank { o.text("createdAt") },
        shares = o.arrayAt("shares").mapNotNull { s ->
            val so = s as? JsonObject ?: return@mapNotNull null
            so.text("leaseId").ifBlank { null }?.let {
                DocShare(it, (so["landlord"] as? JsonObject)?.text("fullName").orEmpty().ifBlank { so.text("landlordName") }, so.text("propertyTitle"), so.text("place"), so.text("sharedAt"))
            }
        },
    )

    fun shared(data: JsonElement): SharedDocuments? {
        val o = data as? JsonObject ?: return null
        return SharedDocuments(
            tenantName = (o["tenant"] as? JsonObject)?.text("fullName").orEmpty(),
            documents = o.arrayAt("documents").mapNotNull { (it as? JsonObject)?.let(::doc) }.filter { it.id.isNotBlank() },
        )
    }
}

/** Reads a picked PDF (content:// uri). Max 10 MB; must start with "%PDF". */
@Singleton
class DocumentFilesImpl @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : com.saferenttechnologies.android.domain.repository.DocumentFiles {
    override fun readPdf(uri: String): com.saferenttechnologies.android.domain.repository.PdfRead {
        val u = android.net.Uri.parse(uri)
        val bytes = runCatching {
            context.contentResolver.openInputStream(u)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > com.saferenttechnologies.android.domain.model.DocumentRules.PDF_MAX_BYTES) {
                        return com.saferenttechnologies.android.domain.repository.PdfRead.TooBig
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return com.saferenttechnologies.android.domain.repository.PdfRead.NotPdf
        return if (DocumentsMapper.looksLikePdf(bytes)) com.saferenttechnologies.android.domain.repository.PdfRead.Ok(bytes)
        else com.saferenttechnologies.android.domain.repository.PdfRead.NotPdf
    }

    override fun displayName(uri: String): String = runCatching {
        context.contentResolver.query(android.net.Uri.parse(uri), arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}
