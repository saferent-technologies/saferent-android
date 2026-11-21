package com.saferenttechnologies.android.domain.model

import java.math.BigDecimal

/** WORKING (green) · NEEDS_REPAIR (amber) · NOT_WORKING (red). */
enum class WorkingStatus { WORKING, NEEDS_REPAIR, NOT_WORKING;
    companion object {
        fun of(v: String?): WorkingStatus? = entries.firstOrNull { it.name == v?.trim()?.uppercase() }
    }
}

data class RegisterPhoto(val id: String, val url: String)

/** One repair in an item's history. Cost as text ("" = not given). */
data class RegisterRepair(
    val id: String,
    val repairDate: String = "",
    val description: String = "",
    val cost: String = "",
    /** OWNER | TENANT | SHARED */
    val paidBy: String = "OWNER",
    val doneBy: String = "",
    /** OPEN | IN_PROGRESS | DONE */
    val status: String = "DONE",
    val createdAt: String = "",
) {
    val isOpen: Boolean get() = status != "DONE"
}

/**
 * Furniture & appliances register item (GET properties/{id}/inventory). unitId + roomId both null =
 * building / common area. Tenants get a reduced copy (no serial, date, notes or repairs). Never logged.
 */
data class RegisterItem(
    val id: String,
    val unitId: String? = null,
    val roomId: String? = null,
    val name: String = "",
    /** BEDROOM | LIVING_ROOM | KITCHEN | BATHROOM | APPLIANCES | OTHER */
    val category: String = "OTHER",
    val quantity: Int = 1,
    val brand: String = "",
    val model: String = "",
    val purchaseDate: String = "",
    val condition: Condition = Condition.GOOD,
    val workingStatus: WorkingStatus = WorkingStatus.WORKING,
    val serialNumber: String = "",
    val notes: String = "",
    val displayOrder: Int = 0,
    val photos: List<RegisterPhoto> = emptyList(),
    val repairs: List<RegisterRepair> = emptyList(),
    val openRepairs: Int = 0,
) {
    val isBuilding: Boolean get() = unitId == null && roomId == null
    /** "LG GL-B201" */
    val brandModel: String get() = listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ")
}

data class RegisterSummary(val total: Int = 0, val needsRepair: Int = 0, val openRepairs: Int = 0)

data class Register(
    val items: List<RegisterItem> = emptyList(),
    val summary: RegisterSummary = RegisterSummary(),
    /** Category → common item names (name chips). */
    val suggestions: Map<String, List<String>> = emptyMap(),
)

/** Add / edit form. Numbers / dates as typed. */
data class RegisterItemForm(
    val unitId: String? = null,
    val roomId: String? = null,
    val name: String = "",
    val category: String = "OTHER",
    val quantity: String = "1",
    val brand: String = "",
    val model: String = "",
    /** "YYYY-MM-DD" or "". */
    val purchaseDate: String = "",
    val condition: Condition = Condition.GOOD,
    val workingStatus: WorkingStatus = WorkingStatus.WORKING,
    val serialNumber: String = "",
    val notes: String = "",
) {
    companion object {
        fun from(i: RegisterItem) = RegisterItemForm(
            i.unitId, i.roomId, i.name, i.category, i.quantity.toString(), i.brand, i.model, i.purchaseDate,
            i.condition, i.workingStatus, i.serialNumber, i.notes,
        )
    }
}

data class RepairForm(
    /** "YYYY-MM-DD" */
    val repairDate: String = "",
    val description: String = "",
    val cost: String = "",
    val paidBy: String = "OWNER",
    val doneBy: String = "",
    /** Default DONE; OPEN / IN_PROGRESS for a pending repair. */
    val status: String = "DONE",
)

/** Local checks, PATCH diffs and the inspection hand off (pure, unit-tested). */
object RegisterRules {
    val CATEGORIES = listOf("BEDROOM", "LIVING_ROOM", "KITCHEN", "BATHROOM", "APPLIANCES", "OTHER")
    const val MAX_PHOTOS = 6
    private val MAX_COST = BigDecimal(10_000_000)

    /** The register matters for SEMI_FURNISHED / FULLY_FURNISHED places. */
    fun furnished(furnishing: String?): Boolean = furnishing?.uppercase() in setOf("SEMI_FURNISHED", "FULLY_FURNISHED")

    enum class Problem { NAME, QUANTITY, TOO_LONG, FUTURE_DATE, BAD_DATE, DESCRIPTION, COST, DATE_REQUIRED }

    private fun dateProblem(raw: String, required: Boolean, today: SimpleDate): Problem? {
        if (raw.isBlank()) return if (required) Problem.DATE_REQUIRED else null
        val d = SimpleDate.parse(raw) ?: return Problem.BAD_DATE
        return if (d > today) Problem.FUTURE_DATE else null
    }

    fun validate(f: RegisterItemForm, today: SimpleDate = SimpleDate.today()): Map<String, Problem> = buildMap {
        if (f.name.trim().length !in 2..80) put("name", Problem.NAME)
        val q = f.quantity.trim().toIntOrNull()
        if (q == null || q !in 1..500) put("quantity", Problem.QUANTITY)
        if (f.brand.trim().length > 80) put("brand", Problem.TOO_LONG)
        if (f.model.trim().length > 80) put("model", Problem.TOO_LONG)
        if (f.serialNumber.trim().length > 80) put("serialNumber", Problem.TOO_LONG)
        if (f.notes.trim().length > 500) put("notes", Problem.TOO_LONG)
        dateProblem(f.purchaseDate, required = false, today)?.let { put("purchaseDate", it) }
    }

    fun validateRepair(f: RepairForm, today: SimpleDate = SimpleDate.today()): Map<String, Problem> = buildMap {
        dateProblem(f.repairDate, required = true, today)?.let { put("repairDate", it) }
        if (f.description.trim().length !in 3..1000) put("description", Problem.DESCRIPTION)
        val c = f.cost.trim()
        if (c.isNotEmpty()) {
            val v = c.toBigDecimalOrNull()
            if (v == null || v < BigDecimal.ZERO || v > MAX_COST || v.scale() > 2) put("cost", Problem.COST)
        }
        if (f.doneBy.trim().length > 120) put("doneBy", Problem.TOO_LONG)
    }

    /** Create body fields (all of them) or, with [original], only what changed. Values: String, Int or null. */
    fun fields(f: RegisterItemForm, original: RegisterItem? = null): Map<String, Any?> {
        val all: Map<String, Any?> = mapOf(
            "unitId" to f.unitId,
            "roomId" to f.roomId,
            "name" to f.name.trim(),
            "category" to f.category,
            "quantity" to (f.quantity.trim().toIntOrNull() ?: 1),
            "brand" to f.brand.trim().ifEmpty { null },
            "model" to f.model.trim().ifEmpty { null },
            "purchaseDate" to f.purchaseDate.trim().ifEmpty { null },
            "condition" to f.condition.name,
            "workingStatus" to f.workingStatus.name,
            "serialNumber" to f.serialNumber.trim().ifEmpty { null },
            "notes" to f.notes.trim().ifEmpty { null },
        )
        if (original == null) return all
        val before = fields(RegisterItemForm.from(original))
        // Place: both ids go together so the server never sees a flat and a room at once.
        val placeChanged = before["unitId"] != all["unitId"] || before["roomId"] != all["roomId"]
        return all.filter { (k, v) -> if (k == "unitId" || k == "roomId") placeChanged else before[k] != v }
    }

    /** Inspection "Load from furniture register": the inspection's flat / room + building items (all when it has none). */
    fun forInspection(items: List<RegisterItem>, unitId: String?, roomId: String?): List<RegisterItem> =
        if (unitId == null && roomId == null) items
        else items.filter { it.isBuilding || (unitId != null && it.unitId == unitId) || (roomId != null && it.roomId == roomId) }

    /** Register rows → inspection rows (appliances separate), linked by inventoryItemId. */
    fun inspectionRows(items: List<RegisterItem>): Pair<List<InventoryItem>, List<InventoryItem>> {
        fun row(i: RegisterItem) = InventoryItem(item = i.name.take(80), quantity = i.quantity.toString(), condition = i.condition, inventoryItemId = i.id, workingStatus = i.workingStatus)
        val (appliances, furniture) = items.sortedWith(compareBy({ it.displayOrder }, { it.name })).partition { it.category == "APPLIANCES" }
        return furniture.map(::row) to appliances.map(::row)
    }

    /** Merge into what the inspector already has: linked rows are replaced, typed rows stay. */
    fun merge(existing: List<InventoryItem>, loaded: List<InventoryItem>): List<InventoryItem> {
        val loadedIds = loaded.mapNotNull { it.inventoryItemId }.toSet()
        val keep = existing.filter { it.inventoryItemId == null || it.inventoryItemId !in loadedIds }.filter { it.item.isNotBlank() }
        val byId = existing.filter { it.inventoryItemId != null }.associateBy { it.inventoryItemId }
        // A row the inspector already rated keeps the rating.
        return loaded.map { l -> byId[l.inventoryItemId]?.let { old -> l.copy(condition = old.condition, workingStatus = old.workingStatus ?: l.workingStatus) } ?: l } + keep
    }
}
