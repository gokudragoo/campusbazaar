package app.campusbazaar.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Listing categories. `id` matches the server enum. */
enum class Category(val id: String, val label: String) {
    Books("books", "Books"),
    Electronics("electronics", "Electronics"),
    Cycles("cycles", "Cycles"),
    HostelRoom("hostel_room", "Hostel & room"),
    NotesStationery("notes_stationery", "Notes & stationery"),
    Sports("sports", "Sports"),
    Other("other", "Other");

    companion object {
        fun of(id: String?): Category = entries.firstOrNull { it.id == id } ?: Other
    }
}

enum class Condition(val id: String, val label: String, val hint: String) {
    New("new", "New", "Unused, still sealed or boxed"),
    LikeNew("like_new", "Like new", "Barely used, no marks"),
    Good("good", "Good", "Normal wear, works perfectly"),
    Fair("fair", "Fair", "Visible wear, works fine"),
    ForParts("for_parts", "For parts", "Broken or needs repair");

    companion object {
        fun of(id: String?): Condition = entries.firstOrNull { it.id == id } ?: Good
    }
}

enum class ListingStatus(val id: String) {
    Active("active"), Reserved("reserved"), Sold("sold"), Removed("removed");

    companion object {
        fun of(id: String?): ListingStatus = entries.firstOrNull { it.id == id } ?: Active
    }
}

enum class SortOrder(val id: String, val label: String) {
    Newest("newest", "Newest first"),
    PriceLow("price_asc", "Price: low to high"),
    PriceHigh("price_desc", "Price: high to low"),
}

/** Popular safe meetup spots on most Indian campuses; the seller can type any other spot. */
val SUGGESTED_SPOTS = listOf(
    "Main gate security desk",
    "Central library entrance",
    "Canteen / food court",
    "Academic block lobby",
    "Hostel reception",
    "Sports complex entrance",
)

object Money {
    /** "₹1,25,000" with Indian digit grouping (last 3 digits, then pairs), independent of device locale data. */
    fun rupees(amount: Int): String {
        val digits = kotlin.math.abs(amount.toLong()).toString()
        val grouped = if (digits.length <= 3) digits else {
            val head = digits.dropLast(3)
            head.reversed().chunked(2).joinToString(",").reversed() + "," + digits.takeLast(3)
        }
        return (if (amount < 0) "-₹" else "₹") + grouped
    }

    /** Parses what a student typed ("1,200", "₹ 450") into whole rupees, or null. */
    fun parse(input: String): Int? {
        val digits = input.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 7) return null
        return digits.toIntOrNull()
    }
}

object TimeText {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val meetup = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

    fun parseIso(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** "just now", "5 min ago", "3 h ago", "yesterday", "12 Nov". */
    fun relative(instant: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (instant == null) return ""
        val d = Duration.between(instant, now)
        return when {
            d.isNegative || d.seconds < 60 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
            d.toHours() < 24 -> "${d.toHours()} h ago"
            instant.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate().minusDays(1) -> "yesterday"
            d.toDays() < 7 -> "${d.toDays()} days ago"
            else -> dayMonth.format(instant.atZone(zone))
        }
    }

    /** Conversation-list style: time today, else day + month. */
    fun short(epochMs: Long, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (epochMs <= 0) return ""
        val instant = Instant.ofEpochMilli(epochMs)
        val today = now.atZone(zone).toLocalDate()
        val day = instant.atZone(zone).toLocalDate()
        return when (day) {
            today -> time.format(instant.atZone(zone))
            today.minusDays(1) -> "Yesterday"
            else -> dayMonth.format(instant.atZone(zone))
        }
    }

    fun clock(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun meetup(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = meetup.format(Instant.ofEpochMilli(epochMs).atZone(zone))
}
