package com.saferenttechnologies.android.domain.model

// ======================= move-in vs move-out =======================

data class InspectionSide(
    val id: String,
    val status: String = "",
    val inspectionDate: String = "",
    val landlordAcknowledgedAt: String? = null,
    val tenantAcknowledgedAt: String? = null,
    val photos: List<InspectionPhoto> = emptyList(),
)

/** change WORSE | BETTER | SAME | NOT_COMPARABLE (items also MISSING | ADDED). */
data class AreaChange(val key: String, val label: String, val before: String?, val after: String?, val change: String)

data class ItemSnapshot(val quantity: Int, val condition: String?)

data class ItemChange(val item: String, val before: ItemSnapshot?, val after: ItemSnapshot?, val change: String, val missing: Int = 0)

data class ComparisonRepair(
    val item: String,
    val description: String = "",
    val priority: String = "",
    val estimatedCost: Double? = null,
    val responsibility: String = "",
    val status: String = "",
)

data class ComparisonSummary(val areasWorse: Int = 0, val itemsMissing: Int = 0, val itemsWorse: Int = 0, val hasIssues: Boolean = false)

data class InspectionDiff(
    val areas: List<AreaChange> = emptyList(),
    val furniture: List<ItemChange> = emptyList(),
    val appliances: List<ItemChange> = emptyList(),
    val meterIn: Double? = null,
    val meterOut: Double? = null,
    val meterUsed: Double? = null,
    val keysIn: Int? = null,
    val keysOut: Int? = null,
    val keysMissing: Int? = null,
    val damagesIn: String? = null,
    val damagesOut: String? = null,
    val repairs: List<ComparisonRepair> = emptyList(),
    val summary: ComparisonSummary = ComparisonSummary(),
)

data class InspectionComparison(
    val ready: Boolean = false,
    /** "MOVE_IN" / "MOVE_OUT" still to be done. */
    val missing: List<String> = emptyList(),
    val moveIn: InspectionSide? = null,
    val moveOut: InspectionSide? = null,
    val comparison: InspectionDiff? = null,
)

object ComparisonRules {
    /** Chips only for real changes. */
    fun showChip(change: String) = change == "WORSE" || change == "BETTER" || change == "MISSING" || change == "ADDED"

    /** "1000.5" / "1450". */
    fun number(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toBigDecimal().stripTrailingZeros().toPlainString()

    /** Photo categories present on both sides (for "Compare"), in the move-in order. */
    fun sharedCategories(moveIn: List<InspectionPhoto>, moveOut: List<InspectionPhoto>): List<String> {
        val after = moveOut.map { it.category.uppercase() }.toSet()
        return moveIn.map { it.category.uppercase() }.distinct().filter { it in after }
    }
}

// ======================= QR codes =======================

data class QrTarget(val code: String, val title: String, val url: String, val unitId: String? = null)

data class PropertyQr(val property: QrTarget, val units: List<QrTarget> = emptyList()) {
    val all: List<QrTarget> get() = listOf(property) + units
}

/** role TEAM | TENANT | VISITOR | NONE. */
data class QrResolution(
    val role: String = "NONE",
    val propertyId: String? = null,
    val propertyCode: String = "",
    val propertyTitle: String = "",
    val unitId: String? = null,
    val unitCode: String? = null,
    val unitLabel: String? = null,
    val leaseId: String? = null,
    val actions: List<String> = emptyList(),
    val message: String? = null,
) {
    val title: String get() = listOf(propertyTitle, unitLabel.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
}

/** Scanned text → SafeRent property / flat code (pure, unit-tested). */
object QrLinks {
    const val HOST = "app.saferenttechnologies.com"
    val ACTIONS = listOf("OPEN_PROPERTY", "START_INSPECTION", "OPEN_FURNITURE", "OPEN_LEASE", "RAISE_REQUEST", "EMERGENCY", "VIEW_LISTING", "BOOK_VISIT")

    data class Code(val property: String, val unit: String?)

    /** https://app.saferenttechnologies.com/p/{code}[/u/{unit}] only; anything else → null ("Not a SafeRent QR code"). */
    fun parse(text: String?): Code? {
        val t = text?.trim().orEmpty()
        val m = Regex("^https://([^/?#]+)(/[^?#]*)?(\\?[^#]*)?(#.*)?$", RegexOption.IGNORE_CASE).find(t) ?: return null
        if (!m.groupValues[1].equals(HOST, ignoreCase = true)) return null
        return path(m.groupValues[2])
    }

    /** "/p/SR-PROP-1/u/SR-UNIT-2" → codes. */
    fun path(path: String): Code? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.firstOrNull()?.lowercase() != "p") return null
        val property = parts.getOrNull(1)?.takeIf { ok(it) } ?: return null
        val unit = if (parts.getOrNull(2)?.lowercase() == "u") parts.getOrNull(3)?.takeIf { ok(it) } ?: return null else null
        if (parts.size > (if (unit != null) 4 else 2)) return null
        return Code(property, unit)
    }

    private fun ok(code: String) = code.length in 3..40 && code.all { it.isLetterOrDigit() || it == '-' || it == '_' }
}

// ======================= smart rent reminders =======================

data class ReminderSettings(
    val enabled: Boolean = true,
    val beforeDays: List<Int> = listOf(3, 1),
    val overdueDays: List<Int> = listOf(1, 3, 7),
    val smartEarly: Boolean = true,
)

data class ReminderData(val settings: ReminderSettings = ReminderSettings(), val defaults: ReminderSettings = ReminderSettings(), val sendingHours: String = "")

object ReminderRules {
    val BEFORE_OPTIONS = listOf(5, 3, 2, 1)
    val OVERDUE_OPTIONS = listOf(1, 3, 7, 15)
    const val MAX_BEFORE = 3
    const val MAX_OVERDUE = 4

    /** Tap a chip: add / remove; full → the oldest pick makes room. Sorted like the server (before: far → near, late: near → far). */
    fun toggleBefore(current: List<Int>, day: Int): List<Int> = toggle(current, day, MAX_BEFORE, 1..10).sortedDescending()
    fun toggleOverdue(current: List<Int>, day: Int): List<Int> = toggle(current, day, MAX_OVERDUE, 1..30).sorted()

    private fun toggle(current: List<Int>, day: Int, max: Int, range: IntRange): List<Int> {
        if (day !in range) return current
        if (day in current) return current - day
        val next = current.filter { it in range }.distinct() + day
        return if (next.size > max) next.drop(next.size - max) else next
    }

    fun clean(s: ReminderSettings) = s.copy(
        beforeDays = s.beforeDays.filter { it in 1..10 }.distinct().sortedDescending().take(MAX_BEFORE),
        overdueDays = s.overdueDays.filter { it in 1..30 }.distinct().sorted().take(MAX_OVERDUE),
    )
}
