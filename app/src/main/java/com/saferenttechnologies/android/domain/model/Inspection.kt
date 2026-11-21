package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** GOOD (green) · FAIR (amber) · POOR (red) · NOT_APPLICABLE / NOT_INSPECTED (grey). */
enum class Condition { GOOD, FAIR, POOR, NOT_APPLICABLE, NOT_INSPECTED;
    companion object {
        fun of(v: String?): Condition? = entries.firstOrNull { it.name == v?.trim()?.uppercase() }
    }
}

enum class InspectionType { MOVE_IN, MOVE_OUT, ROUTINE, VERIFICATION;
    /** Move-in / move-out belong to a lease. */
    val needsLease: Boolean get() = this == MOVE_IN || this == MOVE_OUT
}

data class InventoryItem(
    val item: String = "",
    /** As typed (0–1000). */
    val quantity: String = "1",
    val condition: Condition = Condition.GOOD,
    /** Linked furniture register item ("In register"); saved as COMPLETED → the server updates it. */
    val inventoryItemId: String? = null,
    val workingStatus: WorkingStatus? = null,
)

data class RepairItem(
    val item: String = "",
    val description: String = "",
    /** LOW | MEDIUM | HIGH | URGENT */
    val priority: String = "MEDIUM",
    /** ₹, as typed. */
    val estimatedCost: String = "",
    /** OWNER | TENANT | SHARED (who pays) */
    val responsibility: String = "OWNER",
    /** OPEN | IN_PROGRESS | DONE */
    val status: String = "OPEN",
)

data class InspectionPhoto(val id: String, val category: String, val url: String)

/** One inspection report (GET properties/{id}/inspections item). Private: never logged. */
data class Inspection(
    val id: String,
    val propertyId: String = "",
    /** MOVE_IN | MOVE_OUT | ROUTINE | VERIFICATION */
    val type: String = "ROUTINE",
    val unitId: String? = null,
    val roomId: String? = null,
    val bedId: String? = null,
    val leaseId: String? = null,
    val scheduledAt: String = "",
    val inspectionDate: String = "",
    /** SCHEDULED | COMPLETED | CANCELLED */
    val status: String = "SCHEDULED",
    /** API field name → rating (see [InspectionRules.CONDITION_KEYS]). Missing = not inspected. */
    val conditions: Map<String, Condition> = emptyMap(),
    val furniture: List<InventoryItem> = emptyList(),
    val appliances: List<InventoryItem> = emptyList(),
    val repairs: List<RepairItem> = emptyList(),
    val existingDamages: String = "",
    val meterReading: String = "",
    val keysHandedOver: String = "",
    /** PASSED | NEEDS_REPAIRS | FAILED | "" */
    val result: String = "",
    /** Shown as "Inspector remarks". */
    val notes: String = "",
    val landlordAcknowledgedAt: String = "",
    val tenantAcknowledgedAt: String = "",
    val photos: List<InspectionPhoto> = emptyList(),
) {
    val isCompleted: Boolean get() = status.equals("COMPLETED", true)
    val isCancelled: Boolean get() = status.equals("CANCELLED", true)
    val acknowledged: Boolean get() = landlordAcknowledgedAt.isNotBlank() || tenantAcknowledgedAt.isNotBlank()
    /** The date to show: when it was done, else when it's planned. */
    val shownDate: String get() = inspectionDate.ifBlank { scheduledAt }
    val openRepairs: Int get() = repairs.count { !it.status.equals("DONE", true) }
    val overall: Condition get() = conditions["propertyCondition"] ?: Condition.NOT_INSPECTED
}

/** What the "Record report" screen edits (PATCH body). Texts / numbers kept as typed. */
data class InspectionForm(
    /** "Mark as completed": sends status COMPLETED (needs [inspectionDate]). */
    val complete: Boolean = true,
    /** ISO in UTC ("2026-11-21T05:10:00.000Z"), "" = not chosen. */
    val inspectionDate: String = "",
    val conditions: Map<String, Condition> = InspectionRules.CONDITION_KEYS.associateWith { Condition.NOT_INSPECTED },
    val furniture: List<InventoryItem> = emptyList(),
    val appliances: List<InventoryItem> = emptyList(),
    val repairs: List<RepairItem> = emptyList(),
    val existingDamages: String = "",
    val meterReading: String = "",
    val keysHandedOver: String = "",
    val result: String = "",
    val notes: String = "",
) {
    companion object {
        fun from(i: Inspection) = InspectionForm(
            complete = true,
            inspectionDate = i.inspectionDate,
            conditions = InspectionRules.CONDITION_KEYS.associateWith { i.conditions[it] ?: Condition.NOT_INSPECTED },
            furniture = i.furniture,
            appliances = i.appliances,
            repairs = i.repairs,
            existingDamages = i.existingDamages,
            meterReading = i.meterReading,
            keysHandedOver = i.keysHandedOver,
            result = i.result,
            notes = i.notes,
        )
    }
}

/** Schedule a new inspection (POST). */
data class ScheduleForm(
    val type: InspectionType = InspectionType.ROUTINE,
    /** ISO in UTC, "" = not chosen. */
    val scheduledAt: String = "",
    val unitId: String? = null,
    val roomId: String? = null,
    val bedId: String? = null,
    val leaseId: String? = null,
    val notes: String = "",
)

/** Local checks + clean up (pure, unit-tested). Keys = the server's issue paths. */
object InspectionRules {
    val CONDITION_KEYS = listOf(
        "propertyCondition", "wallCondition", "flooringCondition", "doorsAndWindows", "electricalCondition",
        "plumbingCondition", "bathroomCondition", "kitchenCondition", "furnitureCondition", "applianceCondition",
    )
    val FURNITURE_SUGGESTIONS = listOf("Bed", "Sofa", "Wardrobe", "Dining table", "Chair", "Curtains")
    val APPLIANCE_SUGGESTIONS = listOf("Refrigerator", "Washing machine", "AC", "Geyser", "TV", "Microwave", "Fan", "Inverter")
    val PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")
    val RESPONSIBILITIES = listOf("OWNER", "TENANT", "SHARED")
    val REPAIR_STATUSES = listOf("OPEN", "IN_PROGRESS", "DONE")
    val RESULTS = listOf("PASSED", "NEEDS_REPAIRS", "FAILED")
    val PHOTO_AREAS = listOf("LIVING_ROOM", "BEDROOM", "KITCHEN", "BATHROOM", "BALCONY", "ENTRANCE", "METER_READING", "OTHER")

    const val MAX_ROWS = 200
    const val MAX_REPAIRS = 100
    const val MAX_TEXT = 5000
    private val MAX_COST = BigDecimal(10_000_000)

    /** Empty rows are dropped before sending. */
    fun cleaned(f: InspectionForm): InspectionForm = f.copy(
        furniture = f.furniture.map { it.copy(item = it.item.trim(), quantity = it.quantity.trim()) }.filter { it.item.isNotEmpty() },
        appliances = f.appliances.map { it.copy(item = it.item.trim(), quantity = it.quantity.trim()) }.filter { it.item.isNotEmpty() },
        repairs = f.repairs.map { it.copy(item = it.item.trim(), description = it.description.trim(), estimatedCost = it.estimatedCost.trim()) }
            .filter { it.item.isNotEmpty() || it.description.isNotEmpty() || it.estimatedCost.isNotEmpty() },
        existingDamages = f.existingDamages.trim(),
        meterReading = f.meterReading.trim(),
        keysHandedOver = f.keysHandedOver.trim(),
        notes = f.notes.trim(),
    )

    enum class Problem { DATE_REQUIRED, ITEM_LENGTH, QUANTITY, TOO_MANY, REPAIR_ITEM, DESCRIPTION, COST, TEXT_LONG, KEYS, METER }

    /** Checks the cleaned form; keys like "furnitureInventory.0.item", "repairRequirements.1.estimatedCost". */
    fun validate(raw: InspectionForm): Map<String, Problem> = buildMap {
        val f = cleaned(raw)
        if (f.complete && f.inspectionDate.isBlank()) put("inspectionDate", Problem.DATE_REQUIRED)
        fun inventory(name: String, rows: List<InventoryItem>) {
            if (rows.size > MAX_ROWS) put(name, Problem.TOO_MANY)
            rows.forEachIndexed { i, r ->
                if (r.item.length !in 1..80) put("$name.$i.item", Problem.ITEM_LENGTH)
                val q = r.quantity.toIntOrNull()
                if (q == null || q !in 0..1000) put("$name.$i.quantity", Problem.QUANTITY)
            }
        }
        inventory("furnitureInventory", f.furniture)
        inventory("applianceInventory", f.appliances)
        if (f.repairs.size > MAX_REPAIRS) put("repairRequirements", Problem.TOO_MANY)
        f.repairs.forEachIndexed { i, r ->
            if (r.item.length !in 2..120) put("repairRequirements.$i.item", Problem.REPAIR_ITEM)
            if (r.description.length > 1000) put("repairRequirements.$i.description", Problem.DESCRIPTION)
            if (r.estimatedCost.isNotEmpty()) {
                val c = r.estimatedCost.toBigDecimalOrNull()
                if (c == null || c < BigDecimal.ZERO || c > MAX_COST) put("repairRequirements.$i.estimatedCost", Problem.COST)
            }
        }
        if (f.existingDamages.length > MAX_TEXT) put("existingDamages", Problem.TEXT_LONG)
        if (f.notes.length > MAX_TEXT) put("notes", Problem.TEXT_LONG)
        if (f.keysHandedOver.isNotEmpty()) {
            val k = f.keysHandedOver.toIntOrNull()
            if (k == null || k !in 0..100) put("keysHandedOver", Problem.KEYS)
        }
        if (f.meterReading.isNotEmpty() && f.meterReading.toBigDecimalOrNull()?.let { it >= BigDecimal.ZERO } != true) {
            put("handoverMeterReading", Problem.METER)
        }
    }

    /** An open repair and no result yet → "Needs repairs". */
    fun withSuggestedResult(f: InspectionForm): InspectionForm =
        if (f.result.isBlank() && f.repairs.any { it.status == "OPEN" && it.item.isNotBlank() }) f.copy(result = "NEEDS_REPAIRS") else f

    /** Editing allowed: manager permission, not cancelled, nobody acknowledged it yet. */
    fun canEdit(i: Inspection, canManage: Boolean): Boolean = canManage && !i.isCancelled && !i.acknowledged

    /** Acknowledge (owner side): completed and the landlord hasn't acknowledged. */
    fun canAcknowledge(i: Inspection, canManage: Boolean): Boolean = canManage && i.isCompleted && i.landlordAcknowledgedAt.isBlank()

    /** Digits and one dot, at most [decimals] decimals. */
    fun cleanDecimal(raw: String, decimals: Int = 2): String {
        val d = raw.filter { it.isDigit() || it == '.' }
        val dot = d.indexOf('.')
        return if (dot < 0) d else d.substring(0, dot) + "." + d.substring(dot + 1).filter(Char::isDigit).take(decimals)
    }
}

/** ISO (UTC) ⇄ epoch millis without java.time (minSdk 24). */
object IsoTime {
    private fun utc() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    fun format(millis: Long): String = utc().format(java.util.Date(millis))

    fun parse(iso: String): Long? {
        if (iso.isBlank()) return null
        val clean = iso.trim().let { if (it.endsWith("Z") && !it.contains('.')) it.dropLast(1) + ".000Z" else it }
        return runCatching { utc().parse(clean)?.time }.getOrNull()
    }

    /** "21 Nov 2026, 10:00 AM" in the phone's time zone; "" when not set. */
    fun pretty(iso: String): String = parse(iso)?.let { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH).format(java.util.Date(it)) }.orEmpty()

    fun prettyDay(iso: String): String = parse(iso)?.let { SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(java.util.Date(it)) }.orEmpty()
}
