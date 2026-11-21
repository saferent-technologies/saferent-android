package com.saferenttechnologies.android.domain.model

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** One visiting window: day 0 = Sunday … 6 = Saturday (India time), "HH:mm". */
data class WeeklyWindow(val day: Int, val start: String, val end: String)

data class VisitSettings(
    val enabled: Boolean = false,
    /** MANUAL = confirm each | AUTO = confirm automatically */
    val approvalMode: String = "MANUAL",
    val slotMinutes: Int = 30,
    val capacityPerSlot: Int = 1,
    val minNoticeHours: Int = 4,
    val maxDaysAhead: Int = 14,
    val weekly: List<WeeklyWindow> = emptyList(),
    /** "YYYY-MM-DD" */
    val blockedDates: List<String> = emptyList(),
    /** null = automatic (assigned manager, else agent, else owner). */
    val hostId: String? = null,
    val instructions: String = "",
)

data class VisitHost(val id: String, val fullName: String, val role: String = "")

data class VisitSettingsData(val settings: VisitSettings = VisitSettings(), val hosts: List<VisitHost> = emptyList())

data class VisitSlot(val start: String, val end: String, val remaining: Int = 1)
data class SlotDay(val date: String, val weekday: Int, val slots: List<VisitSlot>)
data class UpcomingVisit(val id: String, val scheduledAt: String, val status: String)

data class VisitSlots(
    val enabled: Boolean = false,
    val approvalMode: String = "MANUAL",
    val slotMinutes: Int = 30,
    val maxDaysAhead: Int = 14,
    val days: List<SlotDay> = emptyList(),
    val myUpcomingVisit: UpcomingVisit? = null,
)

data class VisitFeedback(val rating: Int, val interested: Boolean, val comment: String = "", val at: String = "")

/** A visit as the tenant (VisitorVisit) or the host (HostVisit) sees it. Private parts come only when allowed. */
data class Visit(
    val id: String,
    val propertyId: String = "",
    val propertyCode: String = "",
    val propertyTitle: String = "",
    val locality: String = "",
    val city: String = "",
    val unitId: String? = null,
    val bedId: String? = null,
    val scheduledAt: String = "",
    val endsAt: String = "",
    /** REQUESTED | SCHEDULED | REJECTED | COMPLETED | CANCELLED | NO_SHOW */
    val status: String = "REQUESTED",
    val visitors: Int = 1,
    val note: String = "",
    // tenant view (only when SCHEDULED / COMPLETED)
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hostName: String = "",
    val hostPhone: String = "",
    val instructions: String = "",
    // both
    val rejectReason: String = "",
    val cancelReason: String = "",
    /** YOU / OWNER / SYSTEM (tenant) or VISITOR / OWNER / SYSTEM (host). */
    val cancelledBy: String = "",
    val canCancel: Boolean = false,
    val canGiveFeedback: Boolean = false,
    val feedback: VisitFeedback? = null,
    // host view
    val source: String = "",
    val visitorName: String = "",
    val visitorPhone: String = "",
    val hostId: String = "",
    val outcome: String = "",
    val confirmedAt: String = "",
    val completedAt: String = "",
    val createdAt: String = "",
) {
    val place: String get() = listOf(locality, city).filter { it.isNotBlank() }.joinToString(", ")
    val isOpen: Boolean get() = status == "REQUESTED" || status == "SCHEDULED"
}

data class BookVisit(val start: String, val unitId: String? = null, val bedId: String? = null, val visitors: Int = 1, val note: String = "")

/** Pure rules + India time helpers (unit-tested). */
object VisitRules {
    val IST: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    val SLOT_LENGTHS = listOf(15, 30, 45, 60)
    const val MAX_WINDOWS = 21
    const val MAX_BLOCKED = 60
    private val HHMM = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    private fun minutes(t: String): Int? = HHMM.matchEntire(t)?.let { it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }

    enum class Problem { NO_WINDOWS, TOO_SHORT, OVERLAP, BAD_TIME, INSTRUCTIONS, TOO_MANY }

    /** Settings checks (only when bookings are on). Keys: "weekly", "weekly.{i}", "instructions", "blockedDates". */
    fun validate(s: VisitSettings): Map<String, Problem> = buildMap {
        if (s.instructions.trim().length > 300) put("instructions", Problem.INSTRUCTIONS)
        if (s.blockedDates.size > MAX_BLOCKED) put("blockedDates", Problem.TOO_MANY)
        if (s.weekly.size > MAX_WINDOWS) put("weekly", Problem.TOO_MANY)
        if (!s.enabled) return@buildMap
        if (s.weekly.isEmpty()) put("weekly", Problem.NO_WINDOWS)
        s.weekly.forEachIndexed { i, w ->
            val a = minutes(w.start)
            val b = minutes(w.end)
            when {
                a == null || b == null -> put("weekly.$i", Problem.BAD_TIME)
                b - a < 30 -> put("weekly.$i", Problem.TOO_SHORT)
            }
        }
        // Same day overlaps.
        s.weekly.withIndex().groupBy { it.value.day }.values.forEach { group ->
            val sorted = group.mapNotNull { (i, w) -> minutes(w.start)?.let { st -> minutes(w.end)?.let { en -> Triple(i, st, en) } } }.sortedBy { it.second }
            sorted.zipWithNext().forEach { (x, y) -> if (y.second < x.third) put("weekly.${y.first}", Problem.OVERLAP) }
        }
    }

    /** "Weekends 10–1" / "Weekday evenings 6–8". */
    fun weekends() = listOf(WeeklyWindow(6, "10:00", "13:00"), WeeklyWindow(0, "10:00", "13:00"))
    fun weekdayEvenings() = (1..5).map { WeeklyWindow(it, "18:00", "20:00") }

    /** Add [preset] rows that aren't there yet (max 21). */
    fun addPreset(weekly: List<WeeklyWindow>, preset: List<WeeklyWindow>): List<WeeklyWindow> =
        (weekly + preset.filter { it !in weekly }).take(MAX_WINDOWS)

    fun reasonOk(r: String): Boolean = r.trim().length in 3..300

    // ---- India time ----

    private fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = IST }

    /** "10:00 AM" (India). */
    fun time(iso: String): String = IsoTime.parse(iso)?.let { fmt("h:mm a").format(java.util.Date(it)) }.orEmpty()

    /** "Sat 21 Nov, 10:00 AM" (India). */
    fun dateTime(iso: String): String = IsoTime.parse(iso)?.let { fmt("EEE d MMM, h:mm a").format(java.util.Date(it)) }.orEmpty()

    /** "2026-11-21" → ("Sat", "21", "Nov"). */
    fun dayParts(date: String): Triple<String, String, String> {
        val d = SimpleDate.parse(date) ?: return Triple("", date, "")
        val c = java.util.Calendar.getInstance(IST).apply { clear(); set(d.year, d.month - 1, d.day) }
        return Triple(fmt("EEE").format(c.time), d.day.toString(), fmt("MMM").format(c.time))
    }

    /** Today in India ("YYYY-MM-DD"). */
    fun todayIst(now: Long = System.currentTimeMillis()): String = fmt("yyyy-MM-dd").format(java.util.Date(now))

    /** The day after "YYYY-MM-DD". */
    fun nextDay(date: String): String = SimpleDate.parse(date)?.plusDays(1)?.iso() ?: date

    fun isPast(iso: String, now: Long = System.currentTimeMillis()): Boolean = IsoTime.parse(iso)?.let { it <= now } == true

    /** Host buttons for a visit (the server checks again). */
    fun canConfirm(v: Visit, now: Long = System.currentTimeMillis()) = v.status == "REQUESTED" && !isPast(v.scheduledAt, now)
    fun canReject(v: Visit) = v.status == "REQUESTED"
    fun canHostCancel(v: Visit) = v.status == "REQUESTED" || v.status == "SCHEDULED"
    fun canComplete(v: Visit, now: Long = System.currentTimeMillis()) = v.status == "SCHEDULED" && isPast(v.scheduledAt, now)

    /** Host sections. */
    fun needsConfirmation(items: List<Visit>) = items.filter { it.status == "REQUESTED" }.sortedBy { it.scheduledAt }
    fun upcoming(items: List<Visit>) = items.filter { it.status == "SCHEDULED" }.sortedBy { it.scheduledAt }
    fun past(items: List<Visit>) = items.filter { it.status !in setOf("REQUESTED", "SCHEDULED") }.sortedByDescending { it.scheduledAt }
}
