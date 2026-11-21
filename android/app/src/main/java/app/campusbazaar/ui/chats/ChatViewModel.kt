package app.campusbazaar.ui.chats

import ai.nexconn.chat.channel.BaseChannel
import ai.nexconn.chat.channel.ChannelType
import ai.nexconn.chat.channel.DirectChannel
import ai.nexconn.chat.channel.model.ChannelIdentifier
import ai.nexconn.chat.channel.query.MessagesQuery
import ai.nexconn.chat.message.ImageMessage
import ai.nexconn.chat.message.LocationMessage
import ai.nexconn.chat.message.Message
import ai.nexconn.chat.message.MessageContent
import ai.nexconn.chat.message.TextMessage
import ai.nexconn.chat.message.model.MessageDirection
import ai.nexconn.chat.params.MessagesQueryParams
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.campusbazaar.AppContainer
import app.campusbazaar.chat.ChatBody
import app.campusbazaar.chat.ChatEvent
import app.campusbazaar.chat.ChatException
import app.campusbazaar.chat.ChatItem
import app.campusbazaar.chat.ChatMapper
import app.campusbazaar.chat.SendState
import app.campusbazaar.chat.messages.DealUpdateMessage
import app.campusbazaar.chat.messages.ListingCardMessage
import app.campusbazaar.chat.messages.MeetupCardMessage
import app.campusbazaar.chat.messages.OfferCardMessage
import app.campusbazaar.data.api.ListingDto
import app.campusbazaar.data.api.MeetupCreateRequest
import app.campusbazaar.data.api.OfferDto
import app.campusbazaar.data.api.PeerStateResponse
import app.campusbazaar.data.api.PeerThreadDto
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.ListingStatus
import app.campusbazaar.domain.Money
import app.campusbazaar.util.ImageCompressor
import app.campusbazaar.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

sealed interface ChatSheet {
    data class MakeOffer(val listing: ListingDto, val threadId: String) : ChatSheet
    data class Counter(val offerId: String, val listingTitle: String, val theirAmount: Int, val askingPrice: Int, val negotiable: Boolean) : ChatSheet
    data class Meetup(val threadId: String, val listingTitle: String, val defaultSpot: String) : ChatSheet
    data class Rate(val listingId: String, val listingTitle: String, val peerName: String, val peerRole: String) : ChatSheet
}

/**
 * One buyer–seller conversation on a Nexconn direct channel. Text, photos and locations are plain
 * Nexconn messages; listing/offer/meetup/deal cards are custom messages whose live state is kept
 * in message metadata, mirrored from the server's deal records.
 */
class ChatViewModel(
    private val c: AppContainer,
    private val appContext: Context,
    val peerId: String,
    initialName: String,
    private val listingIdArg: String?,
    private val openOfferArg: Boolean,
) : ViewModel() {
    data class State(
        val peerName: String,
        val peerAvatar: String? = null,
        val peerInternalId: String? = null,
        val items: List<ChatItem> = emptyList(),
        val loading: Boolean = true,
        val loadingOlder: Boolean = false,
        val hasMore: Boolean = true,
        val typing: Boolean = false,
        val deal: PeerStateResponse? = null,
        val activeThreadId: String? = null,
        val blockedByMe: Boolean = false,
        val sheet: ChatSheet? = null,
        val busy: Boolean = false,
        val sendingLocation: Boolean = false,
    ) {
        val activeThread: PeerThreadDto? get() = deal?.threads?.firstOrNull { it.id == activeThreadId } ?: deal?.threads?.firstOrNull()
        val offersById: Map<String, OfferDto> get() = deal?.threads?.flatMap { it.offers }?.associateBy { it.id }.orEmpty()
        val meetupStates: Map<String, String> get() = deal?.threads?.flatMap { it.meetups }?.associate { it.id to it.status }.orEmpty()
    }

    private val channel = DirectChannel(peerId)
    private val identifier = ChannelIdentifier(ChannelType.DIRECT, peerId)
    private val messages = LinkedHashMap<String, Message>()
    private val failed = HashSet<String>()
    private val readByPeer = HashSet<String>()
    private val progress = HashMap<String, Int>()
    private var query: MessagesQuery? = null
    private var typingJob: Job? = null
    private var repliedSent = false
    private var listingCardChecked = false

    private val _state = MutableStateFlow(State(peerName = initialName.ifBlank { c.users.cached(peerId)?.name ?: "Student" }))
    val state = _state.asStateFlow()
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts = _toasts.asSharedFlow()

    val me: String? get() = c.nexconn.currentUserId ?: c.session.current?.nexconnUserId
    private val myName: String get() = c.session.me?.name ?: "A student"
    val myInternalId: String? get() = c.session.me?.id

    init {
        c.users.cached(peerId)?.let { u -> _state.update { it.copy(peerName = u.name, peerAvatar = u.avatarUrl, peerInternalId = u.id) } }
        viewModelScope.launch { c.nexconn.events.collect(::onEvent) }
        viewModelScope.launch {
            c.nexconn.connection.collect { conn ->
                if (conn == app.campusbazaar.chat.ChatConnection.Connected && _state.value.items.isEmpty()) loadHistory(initial = true)
            }
        }
        loadHistory(initial = true)
        refreshDeal()
        if (listingIdArg != null) startFromListing(listingIdArg)
    }

    /* ----------------------------------------------------------------- loading --- */

    private fun loadHistory(initial: Boolean) {
        if (initial) {
            query = BaseChannel.createMessagesQuery(MessagesQueryParams(identifier).apply { pageSize = 30; isAscending = false })
        }
        val q = query ?: return
        if (q.isLoading) return
        _state.update { it.copy(loadingOlder = !initial) }
        viewModelScope.launch {
            c.nexconn.loadNextPage(q).onSuccess { page ->
                page.forEach { put(it) }
                _state.update { it.copy(loading = false, loadingOlder = false, hasMore = q.hasMore) }
                publish()
                if (initial) {
                    markRead()
                    refreshReceipts()
                    ensureListingCard()
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, loadingOlder = false) }
                if ((e as? ChatException)?.code != 33001) _toasts.tryEmit(e.message ?: "Couldn't load messages")
            }
        }
    }

    fun loadOlder() {
        if (_state.value.hasMore && !_state.value.loadingOlder && !_state.value.loading) loadHistory(initial = false)
    }

    fun refreshDeal() {
        viewModelScope.launch {
            c.deals.peerState(peerId).onSuccess { deal ->
                c.users.put(
                    app.campusbazaar.data.api.UserDto(deal.peer.id, deal.peer.nexconnUserId, deal.peer.name, deal.peer.avatarUrl, deal.peer.hostel, deal.peer.department, deal.peer.campusId),
                )
                _state.update { s ->
                    val preferred = s.activeThreadId ?: listingIdArg?.let { lid -> deal.threads.firstOrNull { it.listing?.id == lid }?.id } ?: deal.threads.firstOrNull()?.id
                    s.copy(deal = deal, peerName = deal.peer.name, peerAvatar = deal.peer.avatarUrl, peerInternalId = deal.peer.id, blockedByMe = deal.blockedByMe, activeThreadId = preferred)
                }
                publish()
                if (openOfferArg && !openedOfferOnce) maybeOpenOffer()
            }.onFailure { e -> if (_state.value.deal == null) _toasts.tryEmit(e.message ?: "Couldn't load the deal") }
        }
    }

    private var openedOfferOnce = false
    private fun maybeOpenOffer() {
        val thread = _state.value.activeThread ?: return
        val listing = thread.listing ?: return
        if (thread.role == "buyer" && ListingStatus.of(listing.status) == ListingStatus.Active) {
            openedOfferOnce = true
            _state.update { it.copy(sheet = ChatSheet.MakeOffer(listing, thread.id)) }
        }
    }

    /** Opened from a listing: record the thread on the server, then anchor the chat with a listing card. */
    private fun startFromListing(listingId: String) {
        viewModelScope.launch {
            c.deals.startChat(listingId).onSuccess { r ->
                c.users.put(r.peer)
                _state.update { it.copy(activeThreadId = r.threadId) }
                pendingCardListing = r.listing
                ensureListingCard()
                refreshDeal()
            }.onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't open this chat") }
        }
    }

    private var pendingCardListing: ListingDto? = null

    private fun ensureListingCard() {
        val listing = pendingCardListing ?: return
        if (_state.value.loading || listingCardChecked) return
        listingCardChecked = true
        val latestCard = messages.values.filter { it.content is ListingCardMessage }.maxByOrNull { it.sentTime }?.content as? ListingCardMessage
        if (latestCard?.listingId == listing.id) return
        val card = ListingCardMessage().apply {
            listingId = listing.id; title = listing.title; price = listing.price
            photo = listing.photos.firstOrNull().orEmpty(); condition = listing.condition; negotiable = listing.negotiable
        }
        send(card, "$myName is asking about ${listing.title}")
    }

    /* ------------------------------------------------------------------ events --- */

    private fun isThisChannel(m: Message): Boolean = m.channelIdentifier?.let { it.channelType == ChannelType.DIRECT && it.channelId == peerId } == true

    private fun onEvent(e: ChatEvent) {
        when (e) {
            is ChatEvent.Received -> if (isThisChannel(e.message)) {
                put(e.message)
                publish()
                if (e.message.direction == MessageDirection.RECEIVE) {
                    stopTyping()
                    markRead()
                }
                if (e.message.content.let { it is OfferCardMessage || it is MeetupCardMessage || it is DealUpdateMessage || it is ListingCardMessage }) refreshDeal()
            }
            is ChatEvent.MetadataChanged -> if (isThisChannel(e.message)) {
                val existing = findMessage(e.message)
                val merged = (existing?.metadata.orEmpty() + e.message.metadata.orEmpty() + e.metadata)
                (existing ?: e.message).metadata = merged
                put(existing ?: e.message)
                publish()
                refreshDeal()
            }
            is ChatEvent.MetadataRemoved -> if (isThisChannel(e.message)) {
                findMessage(e.message)?.let { m -> m.metadata = m.metadata.orEmpty() - e.keys.toSet() }
                publish()
            }
            is ChatEvent.Updated -> e.messages.filter(::isThisChannel).forEach { put(it) }.also { publish() }
            is ChatEvent.Receipts -> {
                val mine = e.responses.filter { it.channelIdentifier?.channelId == peerId && it.readCount > 0 }.mapNotNull { it.messageId }
                if (mine.isNotEmpty()) {
                    readByPeer.addAll(mine)
                    publish()
                }
            }
            is ChatEvent.Typing -> if (e.channel.channelType == ChannelType.DIRECT && e.channel.channelId == peerId) {
                if (e.userIds.contains(peerId)) startTyping() else stopTyping()
            }
            is ChatEvent.UnreadChanged -> Unit
        }
    }

    private fun startTyping() {
        _state.update { it.copy(typing = true) }
        typingJob?.cancel()
        typingJob = viewModelScope.launch { delay(6000); _state.update { it.copy(typing = false) } }
    }

    private fun stopTyping() {
        typingJob?.cancel()
        _state.update { it.copy(typing = false) }
    }

    private fun keyOf(m: Message) = m.messageId?.takeIf { it.isNotBlank() } ?: "local-${m.clientId}"

    private fun findMessage(m: Message): Message? = messages[keyOf(m)] ?: messages.values.firstOrNull { it.clientId == m.clientId && m.clientId != 0 }

    private fun put(m: Message) {
        // A local message gains its server id after sending: drop the local key to avoid duplicates.
        if (m.clientId != 0) messages.remove("local-${m.clientId}")
        messages[keyOf(m)] = m
    }

    private fun publish() {
        val myId = me
        val items = messages.values.sortedBy { it.sentTime }.map { m ->
            val key = keyOf(m)
            val item = ChatMapper.toItem(m, myId, readByPeer = m.messageId in readByPeer, progress = progress[key])
            if (key in failed || "local-${m.clientId}" in failed) item.copy(state = SendState.Failed) else item
        }
        _state.update { it.copy(items = items) }
    }

    private var onScreen = false
    private val acked = HashSet<String>()

    /** Set by the screen on resume and pause: messages count as read only while the chat is visible. */
    fun setOnScreen(visible: Boolean) {
        onScreen = visible
        if (visible) markRead()
    }

    private fun markRead() {
        if (!onScreen) return
        c.nexconn.clearUnread(channel)
        val toAck = messages.values
            .filter { it.direction == MessageDirection.RECEIVE && it.needReceipt && !it.sentReceipt }
            .mapNotNull { it.messageId }
            .filter { acked.add(it) }
        c.nexconn.sendReadReceipts(channel, toAck)
    }

    private fun refreshReceipts() {
        val recentMine = messages.values.filter { it.direction == MessageDirection.SEND && it.needReceipt }.sortedByDescending { it.sentTime }.take(20).mapNotNull { it.messageId }
        if (recentMine.isEmpty()) return
        viewModelScope.launch {
            val info = c.nexconn.readReceiptInfo(channel, recentMine)
            readByPeer.addAll(info.filter { it.readCount > 0 }.mapNotNull { it.messageId })
            publish()
        }
    }

    /* ----------------------------------------------------------------- sending --- */

    private fun send(content: MessageContent, push: String, metadata: Map<String, String>? = null, after: suspend (Message) -> Unit = {}) {
        viewModelScope.launch {
            var attachedKey: String? = null
            val result = c.nexconn.send(channel, content, push, needReceipt = true, metadata = metadata, onAttached = { m ->
                attachedKey = keyOf(m)
                viewModelScope.launch { put(m); publish() }
            })
            result.onSuccess { m ->
                attachedKey?.let { failed.remove(it) }
                put(m)
                publish()
                markRepliedIfSeller()
                after(m)
            }.onFailure { e ->
                attachedKey?.let { failed.add(it) }
                publish()
                _toasts.tryEmit(e.message ?: "Message not sent")
            }
        }
    }

    fun sendText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        send(TextMessage(trimmed), "$myName: ${trimmed.take(80)}")
    }

    fun onTyping() = c.nexconn.sendTyping(peerId)

    /** Resends a failed message: removes the failed local copy and sends the same content again. */
    fun retry(key: String) {
        val m = messages[key] ?: return
        messages.remove(key)
        failed.remove(key)
        publish()
        channel.deleteMessagesForMe(listOf(m)) { }
        val content = m.content ?: return
        val preview = ChatMapper.preview(m, me)
        // Offer and meetup cards need their initial metadata (it enables live updates on the message)
        // and need to be linked to the server's deal record again.
        when (content) {
            is ImageMessage -> content.localPath?.let { path -> sendImageFile(Uri.parse(path)) }
            is OfferCardMessage -> send(content, preview, metadata = mapOf(OfferCardMessage.META_STATE to DealRules.PENDING)) { sent ->
                sent.messageId?.let { c.deals.attachOfferMessage(content.offerId, it) }
                refreshDeal()
            }
            is MeetupCardMessage -> send(content, preview, metadata = mapOf(OfferCardMessage.META_STATE to "proposed")) { sent ->
                sent.messageId?.let { c.deals.attachMeetupMessage(content.meetupId, it) }
                refreshDeal()
            }
            else -> send(content, preview)
        }
    }

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { ImageCompressor.compressToCacheFile(appContext, uri) }.getOrNull() }
            if (file == null) {
                _toasts.tryEmit("That photo couldn't be read. Try another one.")
                return@launch
            }
            sendImageFile(Uri.fromFile(file))
        }
    }

    private fun sendImageFile(fileUri: Uri) {
        viewModelScope.launch {
            var attachedKey: String? = null
            val content = ImageMessage().apply { localPath = fileUri.toString() }
            c.nexconn.sendMedia(channel, content, "$myName sent a photo", onAttached = { m ->
                attachedKey = keyOf(m)
                viewModelScope.launch { put(m); publish() }
            }, onProgress = { m, p ->
                viewModelScope.launch { progress[keyOf(m)] = p; publish() }
            }).onSuccess { m ->
                attachedKey?.let { progress.remove(it); failed.remove(it) }
                put(m)
                publish()
                markRepliedIfSeller()
            }.onFailure { e ->
                attachedKey?.let { failed.add(it); progress.remove(it) }
                publish()
                _toasts.tryEmit(e.message ?: "Photo not sent")
            }
        }
    }

    /** Shares the current GPS position as a Nexconn location message (labelled with the meetup spot). */
    fun sendLocation(label: String) {
        if (_state.value.sendingLocation) return
        _state.update { it.copy(sendingLocation = true) }
        viewModelScope.launch {
            val loc = LocationHelper.currentLocation(appContext)
            _state.update { it.copy(sendingLocation = false) }
            if (loc == null) {
                _toasts.tryEmit("Couldn't get your location. Turn on location and try again.")
                return@launch
            }
            shareLocation(loc.latitude, loc.longitude, label.ifBlank { "My location" })
        }
    }

    /**
     * Sends a Nexconn location message. If the SDK refuses it (error 31610 on this app key), the
     * server relays the same RC:LBSMsg through the Platform Chat API and echoes it back to us.
     */
    private suspend fun shareLocation(lat: Double, lng: Double, label: String) {
        val thumb = withContext(Dispatchers.Default) { app.campusbazaar.util.LocationThumb.render(lat, lng, label) }
        val content = LocationMessage().apply { latitude = lat; longitude = lng; poi = label; thumbnailBase64 = thumb }
        var attached: Message? = null
        val result = c.nexconn.send(channel, content, "$myName shared a location", needReceipt = true, onAttached = { attached = it })
        val sent = result.getOrNull()
        if (sent != null) {
            put(sent)
            publish()
            return
        }
        attached?.let { failedMsg -> channel.deleteMessagesForMe(listOf(failedMsg)) { } }
        c.deals.relayLocation(peerId, lat, lng, label, thumb)
            .onFailure { _toasts.tryEmit(it.message ?: "Couldn't share your location") }
    }

    private fun markRepliedIfSeller() {
        if (repliedSent) return
        if (_state.value.deal?.threads?.any { it.role == "seller" } != true) return
        repliedSent = true
        viewModelScope.launch { c.deals.replied(peerId) }
    }

    /* ------------------------------------------------------------- negotiation --- */

    fun selectThread(id: String) = _state.update { it.copy(activeThreadId = id) }
    fun dismissSheet() = _state.update { it.copy(sheet = null) }

    fun openMakeOffer() {
        val t = _state.value.activeThread ?: return
        val l = t.listing ?: return
        _state.update { it.copy(sheet = ChatSheet.MakeOffer(l, t.id)) }
    }

    fun openCounter(offerId: String) {
        val offer = _state.value.offersById[offerId] ?: return
        val thread = _state.value.deal?.threads?.firstOrNull { it.id == offer.threadId }
        val l = thread?.listing ?: return
        _state.update { it.copy(sheet = ChatSheet.Counter(offerId, l.title, offer.amount, l.price, l.negotiable)) }
    }

    fun openMeetup() {
        val t = _state.value.activeThread ?: return
        val l = t.listing ?: return
        _state.update { it.copy(sheet = ChatSheet.Meetup(t.id, l.title, l.meetupSpot)) }
    }

    fun openRate() {
        val t = _state.value.activeThread ?: return
        val l = t.listing ?: return
        _state.update { it.copy(sheet = ChatSheet.Rate(l.id, l.title, it.peerName, if (t.role == "buyer") "seller" else "buyer")) }
    }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun offerMessage(offerId: String): Message? =
        messages.values.firstOrNull { (it.content as? OfferCardMessage)?.offerId == offerId && !it.messageId.isNullOrBlank() }

    private fun meetupMessage(meetupId: String): Message? =
        messages.values.firstOrNull { (it.content as? MeetupCardMessage)?.meetupId == meetupId && !it.messageId.isNullOrBlank() }

    /** Mirrors a final state into the card's Nexconn message metadata so both phones update live. */
    private suspend fun mirror(message: Message?, state: String) {
        message ?: return
        val values = mapOf(
            OfferCardMessage.META_STATE to state,
            OfferCardMessage.META_BY to (me ?: ""),
            OfferCardMessage.META_AT to System.currentTimeMillis().toString(),
        )
        c.nexconn.setMetadata(message, values).onSuccess {
            message.metadata = message.metadata.orEmpty() + values
            publish()
        }
    }

    private fun sendOfferCard(offer: OfferDto, listing: ListingDto, parentAmount: Int) {
        val card = OfferCardMessage().apply {
            offerId = offer.id; listingId = listing.id; listingTitle = listing.title; amount = offer.amount
            askingPrice = listing.price; fromUserId = me ?: ""; this.parentAmount = parentAmount
        }
        send(card, "$myName offered ${Money.rupees(offer.amount)} for ${listing.title}", metadata = mapOf(OfferCardMessage.META_STATE to DealRules.PENDING)) { m ->
            m.messageId?.let { c.deals.attachOfferMessage(offer.id, it) }
            refreshDeal()
        }
    }

    fun submitOffer(amount: Int) {
        val sheet = _state.value.sheet as? ChatSheet.MakeOffer ?: return
        busy {
            c.deals.createOffer(sheet.listing.id, amount, sheet.threadId).onSuccess { r ->
                _state.update { it.copy(sheet = null) }
                r.superseded.forEach { old -> mirror(offerMessage(old.id), old.status) }
                sendOfferCard(r.offer, sheet.listing, 0)
            }.onFailure { _toasts.tryEmit(it.message ?: "Offer not sent") }
        }
    }

    fun accept(offerId: String) = busy {
        c.deals.accept(offerId).onSuccess { r ->
            mirror(offerMessage(offerId), DealRules.ACCEPTED)
            val listing = r.listing
            if (listing != null) {
                send(DealUpdateMessage().apply { kind = DealUpdateMessage.KIND_RESERVED; listingId = listing.id; listingTitle = listing.title; amount = r.offer.amount },
                    "$myName accepted ${Money.rupees(r.offer.amount)} for ${listing.title}")
            }
            refreshDeal()
        }.onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't accept"); refreshDeal() }
    }

    fun decline(offerId: String) = busy {
        c.deals.decline(offerId).onSuccess {
            mirror(offerMessage(offerId), DealRules.DECLINED)
            refreshDeal()
        }.onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't decline"); refreshDeal() }
    }

    fun submitCounter(amount: Int) {
        val sheet = _state.value.sheet as? ChatSheet.Counter ?: return
        busy {
            c.deals.counter(sheet.offerId, amount).onSuccess { r ->
                _state.update { it.copy(sheet = null) }
                mirror(offerMessage(sheet.offerId), DealRules.COUNTERED)
                val listing = _state.value.deal?.threads?.firstOrNull { it.id == r.offer.threadId }?.listing
                if (listing != null) sendOfferCard(r.offer, listing, sheet.theirAmount)
            }.onFailure { e -> _toasts.tryEmit(e.message ?: "Counter not sent"); refreshDeal() }
        }
    }

    fun submitMeetup(spot: String, at: Instant, note: String, shareLocation: Boolean) {
        val sheet = _state.value.sheet as? ChatSheet.Meetup ?: return
        busy {
            val loc = if (shareLocation) LocationHelper.currentLocation(appContext) else null
            if (shareLocation && loc == null) _toasts.tryEmit("Couldn't get your location; sending the spot without it.")
            c.deals.proposeMeetup(MeetupCreateRequest(sheet.threadId, spot, at.toString(), loc?.latitude, loc?.longitude, note)).onSuccess { meetup ->
                _state.update { it.copy(sheet = null) }
                val card = MeetupCardMessage().apply {
                    meetupId = meetup.id; listingId = meetup.listingId; listingTitle = sheet.listingTitle; this.spot = meetup.spot
                    scheduledAt = at.toEpochMilli(); lat = loc?.latitude; lng = loc?.longitude; this.note = note
                }
                send(card, "$myName suggested meeting at ${meetup.spot}", metadata = mapOf(OfferCardMessage.META_STATE to "proposed")) { m ->
                    m.messageId?.let { c.deals.attachMeetupMessage(meetup.id, it) }
                    refreshDeal()
                }
                if (loc != null) shareLocation(loc.latitude, loc.longitude, spot)
            }.onFailure { _toasts.tryEmit(it.message ?: "Couldn't propose the meetup") }
        }
    }

    fun meetupAction(meetupId: String, action: String) = busy {
        c.deals.meetupAction(meetupId, action).onSuccess { m ->
            mirror(meetupMessage(meetupId), m.status)
            refreshDeal()
        }.onFailure { e -> _toasts.tryEmit(e.message ?: "Couldn't update the meetup"); refreshDeal() }
    }

    fun markSold() = busy {
        val t = _state.value.activeThread ?: return@busy
        val listing = t.listing ?: return@busy
        val buyerId = _state.value.peerInternalId ?: return@busy
        c.market.markSold(listing.id, buyerId).onSuccess { sold ->
            val amount = t.offers.firstOrNull { it.status == DealRules.ACCEPTED }?.amount ?: sold.price
            send(DealUpdateMessage().apply { kind = DealUpdateMessage.KIND_SOLD; listingId = sold.id; listingTitle = sold.title; this.amount = amount },
                "$myName marked ${sold.title} as sold to you")
            refreshDeal()
            _state.update { it.copy(sheet = ChatSheet.Rate(sold.id, sold.title, it.peerName, "buyer")) }
        }.onFailure { _toasts.tryEmit(it.message ?: "Couldn't mark sold") }
    }

    fun submitRating(stars: Int, comment: String) {
        val sheet = _state.value.sheet as? ChatSheet.Rate ?: return
        busy {
            c.deals.rate(sheet.listingId, stars, comment).onSuccess {
                _state.update { it.copy(sheet = null) }
                _toasts.tryEmit("Thanks for rating ${sheet.peerName}")
                refreshDeal()
            }.onFailure { e -> _toasts.tryEmit(e.message ?: "Rating not saved"); if ((e as? app.campusbazaar.data.api.AppException)?.code == "already_rated") _state.update { it.copy(sheet = null) } }
        }
    }

    /* ------------------------------------------------------------------ safety --- */

    fun block() = busy {
        val internal = _state.value.peerInternalId ?: return@busy
        c.nexconn.block(peerId) // best effort: the server also adds them to the Nexconn blocklist
        c.market.block(internal).onSuccess {
            _state.update { it.copy(blockedByMe = true) }
            _toasts.tryEmit("${_state.value.peerName} is blocked")
        }.onFailure { _toasts.tryEmit(it.message ?: "Couldn't block") }
    }

    fun unblock() = busy {
        val internal = _state.value.peerInternalId ?: return@busy
        c.nexconn.unblock(peerId)
        c.market.unblock(internal).onSuccess {
            _state.update { it.copy(blockedByMe = false) }
            _toasts.tryEmit("${_state.value.peerName} is unblocked")
        }
    }

    fun report(reason: String, details: String) {
        val internal = _state.value.peerInternalId ?: return
        viewModelScope.launch {
            c.market.report(internal, _state.value.activeThread?.listing?.id, reason, details)
                .onSuccess { _toasts.tryEmit("Thanks. We'll review this.") }
                .onFailure { _toasts.tryEmit(it.message ?: "Couldn't send report") }
        }
    }

    fun offerState(body: ChatBody.OfferCard): String = DealRules.resolveState(body.metadataState, _state.value.offersById[body.offerId]?.status)
    fun meetupState(body: ChatBody.MeetupCard): String = DealRules.resolveState(body.metadataState, _state.value.meetupStates[body.meetupId], initial = "proposed")
}
