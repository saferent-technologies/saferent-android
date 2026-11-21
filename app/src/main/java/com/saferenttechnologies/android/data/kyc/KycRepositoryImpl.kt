package com.saferenttechnologies.android.data.kyc

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.saferenttechnologies.android.core.di.KycPreferences
import com.saferenttechnologies.android.core.log.AppLogger
import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.mapper.toPropertyError
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.KycApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.SafeRentJson
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.DigiLockerResult
import com.saferenttechnologies.android.domain.model.DigiLockerSession
import com.saferenttechnologies.android.domain.model.KycDocument
import com.saferenttechnologies.android.domain.model.KycIdentity
import com.saferenttechnologies.android.domain.model.KycProperty
import com.saferenttechnologies.android.domain.model.KycStatus
import com.saferenttechnologies.android.domain.repository.KycRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KycRepositoryImpl @Inject constructor(
    private val api: KycApi,
    private val safeApiCall: SafeApiCall,
    private val logger: AppLogger,
    @KycPreferences private val prefs: DataStore<Preferences>,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : KycRepository {

    override suspend fun status(): AppResult<KycStatus> = call("GET kyc/me", { api.me() }) { d, _ -> KycMapper.status(d) }

    // The DigiLocker url / session id are not logged.
    override suspend fun startDigiLocker(): AppResult<DigiLockerSession> =
        call("POST kyc/digilocker/start", { api.startDigiLocker() }) { d, _ ->
            val o = d as? JsonObject ?: return@call null
            val url = o.text("url").ifBlank { return@call null }
            DigiLockerSession(url, o.text("sessionId"), o.text("returnUrl"), o.bool("test"))
        }

    override suspend fun completeDigiLocker(sessionId: String, success: Boolean?, errMessage: String?): AppResult<DigiLockerResult> {
        logger.d(TAG, "digilocker complete (success=$success)")
        val body = buildJsonObject {
            put("id", sessionId)
            success?.let { put("success", if (it) "True" else "False") }
            errMessage?.takeIf { it.isNotBlank() }?.let { put("errMessage", it.take(300)) }
        }
        return call("POST kyc/digilocker/complete", { api.completeDigiLocker(body) }) { d, _ ->
            val o = d as? JsonObject
            DigiLockerResult(o?.text("nameMatch")?.ifBlank { null }, o?.bool("already") == true)
        }
    }

    override suspend fun uploadSelfie(filePath: String): AppResult<String> {
        val file = File(filePath)
        if (!file.exists()) return AppResult.Failure(AppError.Parse())
        val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody("image/jpeg".toMediaType()))
        return call("POST kyc/selfie", { api.uploadSelfie(part) }) { _, message -> message }.also { file.delete() }
    }

    // Never logged: numbers, share code / password, file names.
    override suspend fun uploadIdentity(form: com.saferenttechnologies.android.domain.model.IdForm): AppResult<String> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { uploadIdentityIo(form) }

    private suspend fun uploadIdentityIo(form: com.saferenttechnologies.android.domain.model.IdForm): AppResult<String> {
        logger.d(TAG, "upload identity ${form.type}")
        val temp = mutableListOf<File>()
        fun part(field: String, source: String?, mime: String, ext: String): MultipartBody.Part? {
            val src = source ?: return null
            val file = if (src.startsWith("content:")) {
                // Picked file → private copy to upload (deleted after).
                val out = File(context.cacheDir, "kyc_${System.currentTimeMillis()}_$field.$ext")
                context.contentResolver.openInputStream(android.net.Uri.parse(src))?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
                temp += out
                out
            } else File(src)
            if (!file.exists()) return null
            return MultipartBody.Part.createFormData(field, "$field.$ext", file.asRequestBody(mime.toMediaType()))
        }
        fun text(v: String) = v.toRequestBody("text/plain".toMediaType())
        return try {
            val type = form.type
            val filePart = when (type) {
                com.saferenttechnologies.android.domain.model.IdDocType.AADHAAR_XML -> part("file", form.file, "application/zip", "zip")
                com.saferenttechnologies.android.domain.model.IdDocType.EAADHAAR_PDF -> part("file", form.file, "application/pdf", "pdf")
                else -> null
            }
            if (type.needsFile && filePart == null) return AppResult.Failure(AppError.Parse())
            call("POST kyc/identity", {
                api.uploadIdentity(
                    type = text(type.name),
                    number = form.number.takeIf { type.needsNumber && it.isNotBlank() }?.let(::text),
                    secret = form.secret.takeIf { !type.needsNumber && it.isNotBlank() }?.let(::text),
                    file = filePart,
                    front = part("front", form.front, "image/jpeg", "jpg"),
                    back = part("back", form.back, "image/jpeg", "jpg"),
                )
            }) { _, message -> message }
        } finally {
            temp.forEach { it.delete() }
            listOfNotNull(form.front, form.back).filterNot { it.startsWith("content:") }.forEach { File(it).delete() }
        }
    }

    override suspend fun submit(): AppResult<String> = call("POST kyc/submit", { api.submit(buildJsonObject { }) }) { _, message -> message }

    override suspend fun pendingSession(): String? = runCatching { prefs.data.first()[SESSION] }.getOrNull()?.ifBlank { null }

    override suspend fun savePendingSession(sessionId: String?) {
        runCatching { prefs.edit { if (sessionId == null) it.remove(SESSION) else it[SESSION] = sessionId } }
    }

    override suspend fun promptDone(): Boolean = runCatching { prefs.data.first()[PROMPT_DONE] }.getOrNull() == true

    override suspend fun setPromptDone(done: Boolean) {
        runCatching { prefs.edit { if (done) it[PROMPT_DONE] = true else it.remove(PROMPT_DONE) } }
    }

    override suspend fun tutorials(): AppResult<List<com.saferenttechnologies.android.domain.model.KycTutorial>> =
        call("GET kyc/tutorials", { api.tutorials() }) { d, _ -> KycMapper.tutorials(d) }

    // Guide PDFs are public files: own plain client (no sign in token, no cache / inspector), kept in the cache folder.
    private val plainHttp by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override suspend fun downloadTutorialPdf(url: String): AppResult<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext AppResult.Failure(AppError.Parse())
        val dir = java.io.File(context.cacheDir, "kyc_guides").apply { mkdirs() }
        val file = java.io.File(dir, "guide_${url.hashCode().toUInt()}.pdf")
        if (file.length() > 0) return@withContext AppResult.Success(file.absolutePath)
        val tmp = java.io.File(dir, file.name + ".part")
        runCatching {
            plainHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { r ->
                check(r.isSuccessful) { "HTTP ${r.code}" }
                val body = r.body ?: error("empty")
                check(body.contentLength() <= MAX_GUIDE_BYTES) { "too big" }
                body.byteStream().use { input -> tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024); var total = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        total += n; check(total <= MAX_GUIDE_BYTES) { "too big" }
                        out.write(buf, 0, n)
                    }
                } }
            }
            // Must really be a PDF.
            val head = tmp.inputStream().use { i -> ByteArray(4).also { i.read(it) } }
            check(String(head, Charsets.US_ASCII) == "%PDF") { "not a pdf" }
            check(tmp.renameTo(file)) { "rename" }
        }.fold(
            onSuccess = { AppResult.Success(file.absolutePath) },
            onFailure = {
                tmp.delete()
                logger.w(TAG, "Guide PDF download failed: ${it.javaClass.simpleName}")
                AppResult.Failure(AppError.Network("Couldn't open the guide. Check your internet and try again."))
            },
        )
    }

    // Saved on the server so a reinstall / another phone doesn't ask again. Best effort: the local flag covers a failure.
    override suspend fun markPromptDoneOnServer(): AppResult<String> =
        call("POST kyc/prompt-done", { api.promptDone(buildJsonObject { }) }) { _, message -> message }

    private suspend fun <R> call(
        name: String,
        request: suspend () -> Response<ApiEnvelope<JsonElement>>,
        map: (data: JsonElement, message: String) -> R?,
    ): AppResult<R> = when (val r = safeApiCall(name, request)) {
        is ApiResult.Success -> {
            if (!name.startsWith("GET")) ApiCache.clear()
            map(r.data ?: JsonNull, r.message)?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Parse())
        }
        is ApiResult.NotModified -> AppResult.Failure(AppError.Parse())
        is ApiResult.Failure -> AppResult.Failure(r.error.toPropertyError(SafeRentJson.instance))
    }

    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false

    companion object {
        const val NAME = "saferent_kyc"
        private const val TAG = "SR-Kyc"
        private val SESSION = stringPreferencesKey("digilocker_session")
        private val PROMPT_DONE = booleanPreferencesKey("verification_prompt_done")
        private const val MAX_GUIDE_BYTES = 30L * 1024 * 1024
    }
}

/** GET kyc/me → [KycStatus] (pure, unit-tested). */
object KycMapper {
    /** data = [ … ] or { tutorials: [ … ] }. Items without a known type or an https url are dropped. */
    fun tutorials(data: JsonElement): List<com.saferenttechnologies.android.domain.model.KycTutorial> =
        data.arrayAt("tutorials", "items").mapNotNull { e ->
            val t = e as? JsonObject ?: return@mapNotNull null
            val type = com.saferenttechnologies.android.domain.model.TutorialType.entries.firstOrNull { it.name == t.text("type").uppercase() } ?: return@mapNotNull null
            val url = t.text("url").trim()
            if (!url.startsWith("https://")) return@mapNotNull null
            com.saferenttechnologies.android.domain.model.KycTutorial(
                id = t.text("id").ifBlank { url },
                title = t.text("title"),
                description = t.text("description").ifBlank { t.text("note") },
                type = type,
                url = url,
                idType = t.text("idType").uppercase(),
            )
        }

    fun status(data: JsonElement): KycStatus? {
        val o = data as? JsonObject ?: return null
        val id = o["identity"] as? JsonObject
        return KycStatus(
            stage = o.text("stage").uppercase().ifBlank { "NOT_STARTED" },
            note = o.text("note").ifBlank { null },
            submittedAt = o.text("submittedAt").ifBlank { null },
            reviewedAt = o.text("reviewedAt").ifBlank { null },
            provider = o.text("provider").ifBlank { "off" },
            phoneVerified = o.bool("phoneVerified"),
            promptDone = o.bool("promptDone"),
            idTypes = o.arrayAt("idTypes").mapNotNull { e ->
                val t = (e as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.uppercase() ?: return@mapNotNull null
                com.saferenttechnologies.android.domain.model.IdDocType.entries.firstOrNull { it.name == t }
            }.toSet(),
            identity = id?.let {
                KycIdentity(
                    name = it.text("name"),
                    dob = it.text("dob"),
                    gender = it.text("gender"),
                    // Only ever the last 4 digits.
                    aadhaarLast4 = it.text("aadhaarLast4").takeLast(4),
                    city = it.text("city"),
                    state = it.text("state"),
                    pincode = it.text("pincode"),
                    test = it.bool("test"),
                    nameMatch = it.text("nameMatch").uppercase().ifBlank { "EXACT" },
                    documentType = it.text("documentType").uppercase().ifBlank { it.text("provider").uppercase() },
                )
            },
            selfie = o.bool("selfie"),
            properties = o.arrayAt("properties").mapNotNull { e ->
                val p = e as? JsonObject ?: return@mapNotNull null
                KycProperty(
                    id = p.text("id").ifBlank { return@mapNotNull null },
                    code = p.text("code"),
                    title = p.text("title"),
                    city = p.text("city"),
                    publicationStatus = p.text("publicationStatus").uppercase(),
                    ownership = p.text("ownership").uppercase().ifBlank { "MISSING" },
                    documents = p.arrayAt("documents").mapNotNull { d ->
                        val x = d as? JsonObject ?: return@mapNotNull null
                        KycDocument(x.text("id"), x.text("docType"), x.text("status").uppercase().ifBlank { "PENDING" }, x.text("rejectionReason").ifBlank { null })
                    },
                )
            },
            canEdit = o.bool("canEdit"),
            canSubmit = o.bool("canSubmit"),
        )
    }

    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } ?: false
}
