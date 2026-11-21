package app.campusbazaar.chat

import ai.nexconn.chat.message.ImageMessage
import ai.nexconn.chat.message.LocationMessage
import ai.nexconn.chat.message.Message
import ai.nexconn.chat.message.TextMessage
import ai.nexconn.chat.message.model.MessageDirection
import ai.nexconn.chat.message.model.SentStatus
import app.campusbazaar.chat.messages.DealUpdateMessage
import app.campusbazaar.chat.messages.ListingCardMessage
import app.campusbazaar.chat.messages.MeetupCardMessage
import app.campusbazaar.chat.messages.NoticeMessage
import app.campusbazaar.chat.messages.OfferCardMessage
import app.campusbazaar.domain.Money

enum class SendState { Sending, Sent, Failed, Read }

/** UI-facing message bodies (no SDK types leak into Compose). */
sealed interface ChatBody {
    data class Text(val text: String) : ChatBody
    data class Image(val url: String?, val thumbnailBase64: String?, val progress: Int? = null) : ChatBody
    data class Location(val lat: Double, val lng: Double, val label: String, val thumbnailBase64: String? = null) : ChatBody
    data class ListingCard(val listingId: String, val title: String, val price: Int, val photo: String, val condition: String) : ChatBody
    data class OfferCard(
        val offerId: String,
        val listingId: String,
        val listingTitle: String,
        val amount: Int,
        val askingPrice: Int,
        val fromUserId: String,
        val parentAmount: Int,
        val metadataState: String?,
    ) : ChatBody
    data class MeetupCard(
        val meetupId: String,
        val listingId: String,
        val listingTitle: String,
        val spot: String,
        val scheduledAt: Long,
        val lat: Double?,
        val lng: Double?,
        val note: String,
        val metadataState: String?,
    ) : ChatBody
    data class Deal(val kind: String, val listingId: String, val listingTitle: String, val amount: Int) : ChatBody
    data class Notice(val kind: String, val title: String, val body: String, val listingId: String?, val wantedId: String?, val peerUserId: String?) : ChatBody
    data object Unsupported : ChatBody
}

data class ChatItem(
    /** Stable key: server message id once known, otherwise the local client id. */
    val key: String,
    val messageId: String?,
    val clientId: Int,
    val senderId: String,
    val mine: Boolean,
    val sentTime: Long,
    val body: ChatBody,
    val state: SendState,
)

object ChatMapper {
    fun toItem(message: Message, myUserId: String?, readByPeer: Boolean = false, progress: Int? = null): ChatItem {
        val mine = message.direction == MessageDirection.SEND || (myUserId != null && message.senderUserId == myUserId)
        val meta = message.metadata.orEmpty()
        val body: ChatBody = when (val c = message.content) {
            is TextMessage -> ChatBody.Text(c.text)
            is ImageMessage -> ChatBody.Image(c.remoteUrl?.takeIf { it.isNotBlank() } ?: c.localPath, c.thumbnailBase64, progress)
            is LocationMessage -> ChatBody.Location(c.latitude, c.longitude, c.poi.orEmpty(), c.thumbnailBase64)
            is ListingCardMessage -> ChatBody.ListingCard(c.listingId, c.title, c.price, c.photo, c.condition)
            is OfferCardMessage -> ChatBody.OfferCard(
                c.offerId, c.listingId, c.listingTitle, c.amount, c.askingPrice, c.fromUserId, c.parentAmount,
                meta[OfferCardMessage.META_STATE],
            )
            is MeetupCardMessage -> ChatBody.MeetupCard(
                c.meetupId, c.listingId, c.listingTitle, c.spot, c.scheduledAt, c.lat, c.lng, c.note,
                meta[OfferCardMessage.META_STATE],
            )
            is DealUpdateMessage -> ChatBody.Deal(c.kind, c.listingId, c.listingTitle, c.amount)
            is NoticeMessage -> ChatBody.Notice(c.kind, c.title, c.body, c.listingId, c.wantedId, c.peerUserId)
            else -> ChatBody.Unsupported
        }
        val state = when (message.sentStatus) {
            SentStatus.SENDING -> SendState.Sending
            SentStatus.FAILED, SentStatus.CANCELED -> SendState.Failed
            SentStatus.READ -> SendState.Read
            else -> if (mine && readByPeer) SendState.Read else SendState.Sent
        }
        val id = message.messageId?.takeIf { it.isNotBlank() }
        return ChatItem(
            key = id ?: "local-${message.clientId}",
            messageId = id,
            clientId = message.clientId,
            senderId = message.senderUserId.orEmpty(),
            mine = mine,
            sentTime = message.sentTime,
            body = body,
            state = state,
        )
    }

    /** One-line preview for conversation lists and notifications. */
    fun preview(message: Message?, myUserId: String?): String {
        if (message == null) return ""
        val mine = message.direction == MessageDirection.SEND || message.senderUserId == myUserId
        val prefix = if (mine) "You: " else ""
        val text = when (val c = message.content) {
            is TextMessage -> c.text
            is ImageMessage -> "Photo"
            is LocationMessage -> "Location: ${c.poi.orEmpty().ifBlank { "shared a pin" }}"
            is ListingCardMessage -> "About ${c.title}"
            is OfferCardMessage -> "Offer ${Money.rupees(c.amount)} for ${c.listingTitle}"
            is MeetupCardMessage -> "Meetup at ${c.spot}"
            is DealUpdateMessage -> when (c.kind) {
                DealUpdateMessage.KIND_SOLD -> "Marked sold: ${c.listingTitle}"
                DealUpdateMessage.KIND_RESERVED -> "Reserved: ${c.listingTitle}"
                else -> "Available again: ${c.listingTitle}"
            }
            is NoticeMessage -> c.title
            else -> "New message"
        }
        return prefix + text.replace('\n', ' ')
    }
}
