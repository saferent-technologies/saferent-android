package com.saferenttechnologies.android.data.compliance

import com.saferenttechnologies.android.data.mapper.arrayAt
import com.saferenttechnologies.android.data.mapper.text
import com.saferenttechnologies.android.data.owner.OwnerMapper
import com.saferenttechnologies.android.data.remote.ApiCache
import com.saferenttechnologies.android.data.remote.ApiResult
import com.saferenttechnologies.android.data.remote.ComplianceApi
import com.saferenttechnologies.android.data.remote.SafeApiCall
import com.saferenttechnologies.android.data.remote.dto.ApiEnvelope
import com.saferenttechnologies.android.domain.model.AppError
import com.saferenttechnologies.android.domain.model.AppResult
import com.saferenttechnologies.android.domain.model.ComplianceData
import com.saferenttechnologies.android.domain.model.ComplianceDocument
import com.saferenttechnologies.android.domain.model.ComplianceRules
import com.saferenttechnologies.android.domain.model.ExpiringDocument
import com.saferenttechnologies.android.domain.model.LeaseAgreement
import com.saferenttechnologies.android.domain.model.LeaseCompliance
import com.saferenttechnologies.android.domain.model.OwnershipDeclaration
import com.saferenttechnologies.android.domain.model.PoliceVerification
import com.saferenttechnologies.android.domain.model.PropertyCompliance
import com.saferenttechnologies.android.domain.repository.ComplianceRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComplianceRepositoryImpl @Inject constructor(
    private val api: ComplianceApi,
    private val safeApiCall: SafeApiCall,
) : ComplianceRepository {

    override suspend fun compliance(propertyId: String): AppResult<ComplianceData> =
        call("GET compliance", { api.compliance(propertyId) }) { d, _ -> ComplianceMapper.data(d) }

    override suspend fun saveCompliance(propertyId: String, compliance: PropertyCompliance) =
        call("PUT compliance", { api.saveCompliance(propertyId, ComplianceMapper.body(compliance)) }) { d, m -> m to ComplianceMapper.data(d) }

    override suspend fun declare(propertyId: String, version: String) =
        call("POST ownership declaration", { api.declare(propertyId, buildJsonObject { put("accepted", true); put("version", version) }) }) { d, m -> m to ComplianceMapper.data(d) }

    override suspend fun leaseCompliance(leaseId: String): AppResult<LeaseCompliance> =
        call("GET lease compliance", { api.leaseCompliance(leaseId) }) { d, _ -> ComplianceMapper.lease(d, leaseId) }

    override suspend fun saveLeaseCompliance(leaseId: String, changes: Map<String, Any?>) =
        call("PUT lease compliance", { api.saveLeaseCompliance(leaseId, ComplianceMapper.leaseBody(changes)) }) { d, m -> m to ComplianceMapper.lease(d, leaseId) }

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
        is ApiResult.Failure -> AppResult.Failure(OwnerMapper.error(r.error))
    }
}

/** JSON ⇄ compliance models (pure, unit-tested). */
object ComplianceMapper {
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.bool(k: String) = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.equals("true", true) } == true
    private fun JsonObject.textOrNull(k: String) = text(k).trim().ifBlank { null }
    private fun JsonObject.amount(k: String) = ComplianceRules.amountText((this[k] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.doubleOrNull)

    fun data(d: JsonElement): ComplianceData {
        val o = d as? JsonObject ?: return ComplianceData()
        val dec = o.obj("declaration")
        val c = o.obj("compliance")
        return ComplianceData(
            declaration = OwnershipDeclaration(
                text = dec?.text("text").orEmpty(),
                version = dec?.textOrNull("version") ?: ComplianceRules.DECLARATION_VERSION,
                declaredAt = dec?.textOrNull("declaredAt"),
                declaredBy = dec?.textOrNull("declaredBy"),
                current = dec?.bool("current") == true,
            ),
            compliance = PropertyCompliance(
                assessmentNumber = c?.text("assessmentNumber").orEmpty(),
                propertyTaxStatus = c?.textOrNull("propertyTaxStatus")?.uppercase(),
                propertyTaxPaidYear = c?.textOrNull("propertyTaxPaidYear"),
                propertyTaxAmount = c?.amount("propertyTaxAmount").orEmpty(),
                buildingApprovalNumber = c?.text("buildingApprovalNumber").orEmpty(),
                buildingApprovalAuthority = c?.text("buildingApprovalAuthority").orEmpty(),
                buildingApprovalDate = c?.textOrNull("buildingApprovalDate")?.take(10),
            ),
            documents = o.arrayAt("documents").mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                ComplianceDocument(
                    docType = x.text("docType").uppercase().ifBlank { return@mapNotNull null },
                    label = x.text("label"),
                    whereApplicable = x.bool("whereApplicable"),
                    uploaded = x.bool("uploaded"),
                    documentId = x.textOrNull("documentId"),
                    status = x.textOrNull("status")?.uppercase(),
                    expiryDate = x.textOrNull("expiryDate")?.take(10),
                    expiryState = x.textOrNull("expiryState")?.uppercase(),
                )
            },
            expiring = o.arrayAt("expiring").mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                ExpiringDocument(x.text("documentId"), x.text("docType").uppercase(), x.text("name"), x.text("expiryDate").take(10), x.text("expiryState").uppercase())
            },
            updatedAt = o.textOrNull("updatedAt"),
        )
    }

    /** PUT body: the whole object (null = not answered). */
    fun body(raw: PropertyCompliance): JsonObject {
        val c = ComplianceRules.clean(raw)
        return buildJsonObject {
            put("assessmentNumber", c.assessmentNumber.ifBlank { null })
            put("propertyTaxStatus", c.propertyTaxStatus)
            put("propertyTaxPaidYear", c.propertyTaxPaidYear)
            put("propertyTaxAmount", ComplianceRules.amount(c.propertyTaxAmount)?.takeUnless { it.isNaN() })
            put("buildingApprovalNumber", c.buildingApprovalNumber.ifBlank { null })
            put("buildingApprovalAuthority", c.buildingApprovalAuthority.ifBlank { null })
            put("buildingApprovalDate", c.buildingApprovalDate)
        }
    }

    fun lease(d: JsonElement, leaseId: String): LeaseCompliance {
        val o = d as? JsonObject ?: return LeaseCompliance(leaseId)
        val a = o.obj("agreement")
        val p = o.obj("policeVerification")
        val can = o.obj("can")
        return LeaseCompliance(
            leaseId = o.textOrNull("leaseId") ?: leaseId,
            agreement = LeaseAgreement(
                type = a?.textOrNull("type")?.uppercase(),
                registrationNumber = a?.text("registrationNumber").orEmpty(),
                office = a?.text("office").orEmpty(),
                date = a?.textOrNull("date")?.take(10),
                stampDutyAmount = a?.amount("stampDutyAmount").orEmpty(),
            ),
            police = PoliceVerification(
                status = p?.textOrNull("status")?.uppercase() ?: "NOT_STARTED",
                reference = p?.text("reference").orEmpty(),
                submittedOn = p?.textOrNull("submittedOn")?.take(10),
                verifiedOn = p?.textOrNull("verifiedOn")?.take(10),
            ),
            canEditAgreement = can?.bool("editAgreement") == true,
            canEditPolice = can?.bool("editPoliceVerification") == true,
        )
    }

    /** Changed fields → JSON (null clears). */
    fun leaseBody(changes: Map<String, Any?>): JsonObject = buildJsonObject {
        changes.forEach { (k, v) ->
            when (v) {
                null -> put(k, JsonNull)
                is Number -> put(k, v)
                is Boolean -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}
