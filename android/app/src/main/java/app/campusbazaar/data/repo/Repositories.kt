package app.campusbazaar.data.repo

import android.content.Context
import android.net.Uri
import app.campusbazaar.data.api.*
import app.campusbazaar.data.session.SessionManager
import app.campusbazaar.util.ImageCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthRepository(private val api: CampusBazaarApi, private val session: SessionManager) {
    suspend fun signIn(email: String, password: String): Result<Unit> =
        apiCall { api.login(LoginRequest(email.trim(), password)) }.mapCatching { session.onSignedIn(it) }

    suspend fun signUp(name: String, email: String, password: String): Result<Unit> =
        apiCall { api.signup(SignupRequest(name.trim(), email.trim(), password)) }.mapCatching { session.onSignedIn(it) }

    suspend fun searchCampuses(q: String?): Result<List<CampusDto>> = apiCall { api.campuses(q?.trim()?.ifBlank { null }).campuses }

    suspend fun createCampus(name: String, city: String): Result<CampusDto> =
        apiCall { api.createCampus(CampusCreateRequest(name.trim(), city.trim())).campus }.onSuccess { session.setCampus(it) }

    suspend fun joinCampus(id: String): Result<CampusDto> = apiCall { api.joinCampus(id).campus }.onSuccess { session.setCampus(it) }

    /** Deletes the account on the server (password confirmed), then signs out on this phone. */
    suspend fun deleteAccount(password: String): Result<Unit> =
        apiCall { api.deleteAccount(DeleteAccountRequest(password)) }.mapCatching { session.signOut() }

    suspend fun me(): Result<MeResponse> = apiCall { api.me() }.onSuccess { r ->
        session.update { it.copy(user = r.user, campus = r.campus ?: it.campus) }
    }

    suspend fun updateProfile(req: ProfileUpdateRequest): Result<UserDto> = apiCall { api.updateMe(req).user }.onSuccess { session.setUser(it) }
}

/** Listings, wanted posts, profiles, uploads, AI drafts and safety. */
class MarketRepository(private val context: Context, private val api: CampusBazaarApi) {
    suspend fun feed(
        q: String?, category: String?, conditions: Set<String>, minPrice: Int?, maxPrice: Int?, sort: String, cursor: String?,
    ): Result<ListingPageResponse> = apiCall {
        api.listings(q?.ifBlank { null }, category, conditions.joinToString(",").ifBlank { null }, minPrice, maxPrice, sort, cursor)
    }

    suspend fun listing(id: String) = apiCall { api.listing(id) }
    suspend fun create(req: ListingCreateRequest) = apiCall { api.createListing(req).listing }
    suspend fun update(id: String, req: ListingUpdateRequest) = apiCall { api.updateListing(id, req).listing }
    suspend fun delete(id: String) = apiCall { api.deleteListing(id) }
    suspend fun setSaved(id: String, saved: Boolean) = apiCall { if (saved) api.save(id) else api.unsave(id) }
    suspend fun hide(id: String) = apiCall { api.hide(id) }
    suspend fun markSold(id: String, buyerId: String?) = apiCall { api.markSold(id, SoldRequest(buyerId ?: "")).listing }
    suspend fun myListings() = apiCall { api.myListings().listings }
    suspend fun saved() = apiCall { api.saved().listings }
    suspend fun profile(userId: String) = apiCall { api.profile(userId) }

    suspend fun wanted(category: String?, mine: Boolean, cursor: String?) = apiCall { api.wanted(category, if (mine) "1" else null, cursor) }
    suspend fun createWanted(req: WantedCreateRequest) = apiCall { api.createWanted(req).wanted }
    suspend fun setWantedStatus(id: String, open: Boolean) = apiCall { api.setWantedStatus(id, WantedStatusRequest(if (open) "open" else "closed")).wanted }
    suspend fun deleteWanted(id: String) = apiCall { api.deleteWanted(id) }
    suspend fun respondWanted(id: String, listingId: String?, note: String) = apiCall { api.respondWanted(id, WantedRespondRequest(listingId, note)) }

    /** Compresses a photo on-device (max 1600 px, JPEG) and uploads it through our server to Blob. */
    suspend fun uploadPhoto(uri: Uri, kind: String = "listing"): Result<String> {
        val bytes = withContext(Dispatchers.IO) { runCatching { ImageCompressor.compress(context, uri) } }.getOrElse {
            return Result.failure(AppException("That photo couldn't be read. Try another one.", "bad_image"))
        }
        val part = MultipartBody.Part.createFormData("file", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        return apiCall { api.upload(part, kind.toRequestBody("text/plain".toMediaType())).url }
    }

    suspend fun suggest(urls: List<String>) = apiCall { api.suggest(SuggestRequest(urls)).suggestion }

    suspend fun blocks() = apiCall { api.blocks().blocked }
    suspend fun block(userId: String) = apiCall { api.block(BlockRequest(userId)) }
    suspend fun unblock(userId: String) = apiCall { api.unblock(userId) }
    suspend fun report(userId: String?, listingId: String?, reason: String, details: String) =
        apiCall { api.report(ReportRequest(userId, listingId, reason, details)) }

    suspend fun square() = apiCall { api.square() }
    suspend fun config() = apiCall { api.config() }
}

/** Server side of negotiations: threads, offers, meetups, ratings. */
class DealRepository(private val api: CampusBazaarApi) {
    suspend fun startChat(listingId: String) = apiCall { api.startChat(ChatStartRequest(listingId)) }
    suspend fun threads() = apiCall { api.threads().threads }
    suspend fun peerState(nexconnUserId: String) = apiCall { api.peerState(nexconnUserId) }
    suspend fun replied(peerNexconnUserId: String) = apiCall { api.replied(RepliedRequest(peerNexconnUserId)) }
    suspend fun relayLocation(peer: String, lat: Double, lng: Double, poi: String, thumb: String) =
        apiCall { api.relayLocation(LocationRelayRequest(peer, lat, lng, poi, thumb)) }

    suspend fun createOffer(listingId: String, amount: Int, threadId: String?) = apiCall { api.createOffer(OfferCreateRequest(listingId, amount, threadId)) }
    suspend fun accept(offerId: String) = apiCall { api.acceptOffer(offerId) }
    suspend fun decline(offerId: String) = apiCall { api.declineOffer(offerId) }
    suspend fun counter(offerId: String, amount: Int) = apiCall { api.counterOffer(offerId, AmountRequest(amount)) }
    suspend fun attachOfferMessage(offerId: String, messageId: String) = apiCall { api.attachOfferMessage(offerId, MessageIdRequest(messageId)) }

    suspend fun proposeMeetup(req: MeetupCreateRequest) = apiCall { api.createMeetup(req).meetup }
    suspend fun meetupAction(id: String, action: String) = apiCall { api.meetupAction(id, action).meetup }
    suspend fun attachMeetupMessage(id: String, messageId: String) = apiCall { api.attachMeetupMessage(id, MessageIdRequest(messageId)) }

    suspend fun rate(listingId: String, stars: Int, comment: String) = apiCall { api.rate(RatingRequest(listingId, stars, comment)) }
}

/** Caches Nexconn user id → profile so chat lists and Campus Square can show names and photos. */
class UserDirectory(private val api: CampusBazaarApi) {
    private val cache = HashMap<String, UserDto>()
    private val mutex = Mutex()

    fun cached(nexconnUserId: String): UserDto? = cache[nexconnUserId]

    fun put(user: UserDto) {
        cache[user.nexconnUserId] = user
    }

    suspend fun resolve(ids: Collection<String>): Map<String, UserDto> = mutex.withLock {
        val missing = ids.filter { it.isNotBlank() && it !in cache }.distinct()
        if (missing.isNotEmpty()) {
            missing.chunked(100).forEach { chunk ->
                apiCall { api.lookup(LookupRequest(chunk)).users }.onSuccess { users -> users.forEach { cache[it.nexconnUserId] = it } }
            }
        }
        ids.mapNotNull { id -> cache[id]?.let { id to it } }.toMap()
    }
}
