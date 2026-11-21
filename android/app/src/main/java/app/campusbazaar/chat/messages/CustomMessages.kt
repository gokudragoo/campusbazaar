package app.campusbazaar.chat.messages

import ai.nexconn.chat.message.CustomMessageContent
import ai.nexconn.chat.message.model.MessagePersistent

/*
 * CampusBazaar's custom Nexconn message types. Each has a public no-arg constructor (the SDK
 * instantiates them by reflection) and encodes only JSON-friendly fields. The server sends
 * cbz:notice with the same field names through the Platform Chat API.
 */

internal fun Map<String, Any?>.str(key: String): String = (this[key] as? String).orEmpty()
internal fun Map<String, Any?>.strOrNull(key: String): String? = (this[key] as? String)?.takeIf { it.isNotEmpty() }
internal fun Map<String, Any?>.long(key: String): Long = when (val v = this[key]) {
    is Number -> v.toLong()
    is String -> v.toLongOrNull() ?: 0L
    else -> 0L
}
internal fun Map<String, Any?>.int(key: String): Int = long(key).toInt()
internal fun Map<String, Any?>.doubleOrNull(key: String): Double? = when (val v = this[key]) {
    is Number -> v.toDouble()
    is String -> v.toDoubleOrNull()
    else -> null
}

/** Listing context card that anchors a negotiation inside the buyer–seller direct channel. */
class ListingCardMessage() : CustomMessageContent() {
    var listingId: String = ""
    var title: String = ""
    var price: Int = 0
    var photo: String = ""
    var condition: String = ""
    var negotiable: Boolean = true

    override fun messageType(): String = TYPE
    override fun persistentFlag(): Int = MessagePersistent.COUNT
    override fun encodeFields(): Map<String, Any?> = mapOf(
        "listingId" to listingId, "title" to title, "price" to price,
        "photo" to photo, "condition" to condition, "negotiable" to negotiable,
    )
    override fun decodeFields(fields: Map<String, Any?>) {
        listingId = fields.str("listingId"); title = fields.str("title"); price = fields.int("price")
        photo = fields.str("photo"); condition = fields.str("condition"); negotiable = fields["negotiable"] as? Boolean ?: true
    }
    override fun searchableWords(): List<String> = listOf(title).filter { it.isNotBlank() }

    companion object { const val TYPE = "cbz:listing" }
}

/**
 * Offer card (₹ amount). Its live state (pending / accepted / declined / countered / withdrawn)
 * lives in the message's Nexconn metadata under [META_STATE]; the server holds the canonical copy.
 */
class OfferCardMessage() : CustomMessageContent() {
    var offerId: String = ""
    var listingId: String = ""
    var listingTitle: String = ""
    var amount: Int = 0
    var askingPrice: Int = 0
    var fromUserId: String = ""
    var parentAmount: Int = 0

    override fun messageType(): String = TYPE
    override fun persistentFlag(): Int = MessagePersistent.COUNT
    override fun encodeFields(): Map<String, Any?> = mapOf(
        "offerId" to offerId, "listingId" to listingId, "listingTitle" to listingTitle, "amount" to amount,
        "askingPrice" to askingPrice, "fromUserId" to fromUserId, "parentAmount" to parentAmount,
    )
    override fun decodeFields(fields: Map<String, Any?>) {
        offerId = fields.str("offerId"); listingId = fields.str("listingId"); listingTitle = fields.str("listingTitle")
        amount = fields.int("amount"); askingPrice = fields.int("askingPrice"); fromUserId = fields.str("fromUserId")
        parentAmount = fields.int("parentAmount")
    }

    companion object {
        const val TYPE = "cbz:offer"
        const val META_STATE = "state"
        const val META_BY = "by"
        const val META_AT = "at"
    }
}

/** Meetup proposal: a campus spot and a time. State mirrored in metadata like offers. */
class MeetupCardMessage() : CustomMessageContent() {
    var meetupId: String = ""
    var listingId: String = ""
    var listingTitle: String = ""
    var spot: String = ""
    var scheduledAt: Long = 0
    var lat: Double? = null
    var lng: Double? = null
    var note: String = ""

    override fun messageType(): String = TYPE
    override fun persistentFlag(): Int = MessagePersistent.COUNT
    override fun encodeFields(): Map<String, Any?> = buildMap {
        put("meetupId", meetupId); put("listingId", listingId); put("listingTitle", listingTitle)
        put("spot", spot); put("scheduledAt", scheduledAt); put("note", note)
        lat?.let { put("lat", it) }; lng?.let { put("lng", it) }
    }
    override fun decodeFields(fields: Map<String, Any?>) {
        meetupId = fields.str("meetupId"); listingId = fields.str("listingId"); listingTitle = fields.str("listingTitle")
        spot = fields.str("spot"); scheduledAt = fields.long("scheduledAt"); note = fields.str("note")
        lat = fields.doubleOrNull("lat"); lng = fields.doubleOrNull("lng")
    }

    companion object { const val TYPE = "cbz:meetup" }
}

/** Deal milestone posted in chat: reserved, released, or sold (which prompts both sides to rate). */
class DealUpdateMessage() : CustomMessageContent() {
    var kind: String = ""
    var listingId: String = ""
    var listingTitle: String = ""
    var amount: Int = 0

    override fun messageType(): String = TYPE
    override fun persistentFlag(): Int = MessagePersistent.COUNT
    override fun encodeFields(): Map<String, Any?> = mapOf("kind" to kind, "listingId" to listingId, "listingTitle" to listingTitle, "amount" to amount)
    override fun decodeFields(fields: Map<String, Any?>) {
        kind = fields.str("kind"); listingId = fields.str("listingId"); listingTitle = fields.str("listingTitle"); amount = fields.int("amount")
    }

    companion object {
        const val TYPE = "cbz:deal"
        const val KIND_SOLD = "sold"
        const val KIND_RESERVED = "reserved"
        const val KIND_RELEASED = "released"
    }
}

/** Activity notice sent by the server bot into the Nexconn system channel. */
class NoticeMessage() : CustomMessageContent() {
    var kind: String = ""
    var title: String = ""
    var body: String = ""
    var listingId: String? = null
    var wantedId: String? = null
    var peerUserId: String? = null

    override fun messageType(): String = TYPE
    override fun persistentFlag(): Int = MessagePersistent.COUNT
    override fun encodeFields(): Map<String, Any?> = buildMap {
        put("kind", kind); put("title", title); put("body", body)
        listingId?.let { put("listingId", it) }; wantedId?.let { put("wantedId", it) }; peerUserId?.let { put("peerUserId", it) }
    }
    override fun decodeFields(fields: Map<String, Any?>) {
        kind = fields.str("kind"); title = fields.str("title"); body = fields.str("body")
        listingId = fields.strOrNull("listingId"); wantedId = fields.strOrNull("wantedId"); peerUserId = fields.strOrNull("peerUserId")
    }

    companion object { const val TYPE = "cbz:notice" }
}

val ALL_CUSTOM_MESSAGES = listOf(
    ListingCardMessage::class.java,
    OfferCardMessage::class.java,
    MeetupCardMessage::class.java,
    DealUpdateMessage::class.java,
    NoticeMessage::class.java,
)
