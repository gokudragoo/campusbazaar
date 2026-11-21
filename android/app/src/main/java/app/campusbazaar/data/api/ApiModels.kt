package app.campusbazaar.data.api

import kotlinx.serialization.Serializable

/* Wire models for the CampusBazaar API. Field names match the server's JSON exactly. */

@Serializable data class ApiErrorBody(val error: ApiErrorDetail? = null)
@Serializable data class ApiErrorDetail(val code: String = "", val message: String = "")

@Serializable
data class UserDto(
    val id: String,
    val nexconnUserId: String,
    val name: String,
    val avatarUrl: String? = null,
    val hostel: String? = null,
    val department: String? = null,
    val campusId: String? = null,
    val joinedAt: String = "",
    val email: String? = null,
)

@Serializable
data class CampusDto(
    val id: String,
    val name: String,
    val city: String,
    val memberCount: Int = 0,
    val communityChannelId: String,
)

@Serializable data class NexconnCreds(val appKey: String, val userId: String, val token: String)

@Serializable
data class SessionResponse(
    val token: String,
    val expiresAt: String,
    val user: UserDto,
    val campus: CampusDto? = null,
    val nexconn: NexconnCreds,
)

@Serializable data class SignupRequest(val name: String, val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)

@Serializable
data class UserStatsDto(
    val ratingAvg: Double? = null,
    val ratingCount: Int = 0,
    val responseRate: Int? = null,
    val activeListings: Int = 0,
    val soldCount: Int = 0,
)

@Serializable data class MeResponse(val user: UserDto, val campus: CampusDto? = null, val stats: UserStatsDto? = null)

@Serializable
data class ProfileUpdateRequest(
    val name: String? = null,
    val hostel: String? = null,
    val department: String? = null,
    val avatarUrl: String? = null,
)

@Serializable data class UserResponse(val user: UserDto)
@Serializable data class NexconnTokenResponse(val appKey: String, val userId: String, val token: String)

@Serializable data class CampusListResponse(val campuses: List<CampusDto>)
@Serializable data class CampusCreateRequest(val name: String, val city: String)
@Serializable data class CampusResponse(val campus: CampusDto, val created: Boolean = false)

@Serializable data class SubchannelDto(val id: String, val name: String, val description: String = "")
@Serializable data class SquareResponse(val campus: CampusDto, val channelId: String, val subchannels: List<SubchannelDto>)

@Serializable
data class ConfigResponse(
    val nexconnAppKey: String,
    val apkUrl: String? = null,
    val categories: List<String> = emptyList(),
    val conditions: List<String> = emptyList(),
    val subchannels: List<SubchannelDto> = emptyList(),
    val aiEnabled: Boolean = false,
    val noticeSenderId: String? = null,
)

@Serializable data class UploadResponse(val url: String)
@Serializable data class SuggestRequest(val imageUrls: List<String>)

@Serializable
data class SuggestionDto(
    val title: String,
    val category: String,
    val condition: String,
    val description: String,
    val priceMin: Int,
    val priceMax: Int,
    val confidence: String = "medium",
    val model: String = "",
)

@Serializable data class SuggestResponse(val suggestion: SuggestionDto)

@Serializable
data class SellerCardDto(
    val id: String,
    val nexconnUserId: String,
    val name: String,
    val avatarUrl: String? = null,
    val hostel: String? = null,
    val department: String? = null,
    val campusId: String? = null,
    val joinedAt: String = "",
    val ratingAvg: Double? = null,
    val ratingCount: Int = 0,
    val responseRate: Int? = null,
    val activeListings: Int = 0,
    val soldCount: Int = 0,
)

@Serializable
data class ListingDto(
    val id: String,
    val campusId: String,
    val sellerId: String,
    val title: String,
    val description: String = "",
    val category: String,
    val condition: String,
    val price: Int,
    val negotiable: Boolean = true,
    val meetupSpot: String = "",
    val photos: List<String> = emptyList(),
    val status: String,
    val reservedForId: String? = null,
    val soldToId: String? = null,
    val aiAssisted: Boolean = false,
    val aiPriceMin: Int? = null,
    val aiPriceMax: Int? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val soldAt: String? = null,
    val seller: SellerCardDto? = null,
    val saved: Boolean? = null,
    val sellerName: String? = null,
    val sellerAvatarUrl: String? = null,
    val interestedBuyers: List<UserDto> = emptyList(),
)

@Serializable data class ListingPageResponse(val listings: List<ListingDto>, val nextCursor: String? = null)
@Serializable data class ListingsResponse(val listings: List<ListingDto>)
@Serializable data class ListingResponse(val listing: ListingDto)

@Serializable
data class ListingDetailResponse(
    val listing: ListingDto,
    val myThreadId: String? = null,
    val myOffers: List<OfferDto> = emptyList(),
)

@Serializable
data class ListingCreateRequest(
    val title: String,
    val description: String,
    val category: String,
    val condition: String,
    val price: Int,
    val negotiable: Boolean,
    val meetupSpot: String,
    val photos: List<String>,
    val aiAssisted: Boolean,
    val aiPriceMin: Int? = null,
    val aiPriceMax: Int? = null,
)

@Serializable
data class ListingUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val category: String? = null,
    val condition: String? = null,
    val price: Int? = null,
    val negotiable: Boolean? = null,
    val meetupSpot: String? = null,
    val photos: List<String>? = null,
    val status: String? = null,
)

/** `buyerId` empty string means "sold outside the app"; omitted means "the reserved buyer". */
@Serializable data class SoldRequest(val buyerId: String? = null)

@Serializable
data class OfferDto(
    val id: String,
    val listingId: String,
    val threadId: String,
    val buyerId: String,
    val sellerId: String,
    val fromUserId: String,
    val amount: Int,
    val status: String,
    val parentOfferId: String? = null,
    val messageId: String? = null,
    val createdAt: String = "",
    val respondedAt: String? = null,
)

@Serializable data class OfferCreateRequest(val listingId: String, val amount: Int, val threadId: String? = null)
@Serializable data class OfferCreateResponse(val offer: OfferDto, val superseded: List<OfferDto> = emptyList(), val threadId: String)
@Serializable data class OfferActionResponse(val offer: OfferDto, val listing: ListingDto? = null, val countered: OfferDto? = null)
@Serializable data class AmountRequest(val amount: Int)
@Serializable data class MessageIdRequest(val messageId: String)

@Serializable
data class MeetupDto(
    val id: String,
    val listingId: String,
    val threadId: String,
    val proposedById: String,
    val buyerId: String,
    val sellerId: String,
    val spot: String,
    val scheduledAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String = "",
    val status: String,
    val messageId: String? = null,
    val createdAt: String = "",
)

@Serializable
data class MeetupCreateRequest(
    val threadId: String,
    val spot: String,
    val scheduledAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val note: String = "",
)

@Serializable data class MeetupResponse(val meetup: MeetupDto)
@Serializable data class RatingRequest(val listingId: String, val stars: Int, val comment: String)

@Serializable data class ChatStartRequest(val listingId: String)
@Serializable data class ChatStartResponse(val threadId: String, val created: Boolean, val listing: ListingDto, val peer: UserDto)

@Serializable
data class ThreadListingDto(
    val id: String,
    val title: String,
    val price: Int,
    val photo: String? = null,
    val status: String,
    val reservedForId: String? = null,
)

@Serializable
data class ThreadSummaryDto(
    val id: String,
    val role: String,
    val peer: UserDto? = null,
    val listing: ThreadListingDto? = null,
    val lastActivityAt: String = "",
)

@Serializable data class ThreadsResponse(val threads: List<ThreadSummaryDto>)

@Serializable
data class PeerThreadDto(
    val id: String,
    val role: String,
    val listing: ListingDto? = null,
    val offers: List<OfferDto> = emptyList(),
    val meetups: List<MeetupDto> = emptyList(),
    val iRated: Boolean = false,
    val theyRated: Boolean = false,
    val lastActivityAt: String = "",
)

@Serializable data class PeerStateResponse(val peer: SellerCardDto, val blockedByMe: Boolean = false, val threads: List<PeerThreadDto> = emptyList())
@Serializable data class RepliedRequest(val peerNexconnUserId: String)
@Serializable data class LocationRelayRequest(val peerNexconnUserId: String, val latitude: Double, val longitude: Double, val poi: String, val thumbnailBase64: String)
@Serializable data class MessageIdResponse(val messageId: String? = null)
@Serializable data class LookupRequest(val nexconnUserIds: List<String>)
@Serializable data class UsersResponse(val users: List<UserDto>)

@Serializable
data class RatingDto(
    val id: String,
    val stars: Int,
    val comment: String = "",
    val rateeRole: String,
    val createdAt: String = "",
    val rater: UserDto? = null,
)

@Serializable
data class ProfileResponse(
    val user: SellerCardDto,
    val blocked: Boolean = false,
    val listings: List<ListingDto> = emptyList(),
    val ratings: List<RatingDto> = emptyList(),
)

@Serializable
data class WantedDto(
    val id: String,
    val campusId: String,
    val userId: String,
    val title: String,
    val description: String = "",
    val category: String,
    val budget: Int? = null,
    val status: String,
    val responseCount: Int = 0,
    val createdAt: String = "",
    val author: UserDto? = null,
    val respondedByMe: Boolean = false,
)

@Serializable data class WantedPageResponse(val wanted: List<WantedDto>, val nextCursor: String? = null)
@Serializable data class WantedCreateRequest(val title: String, val description: String, val category: String, val budget: Int? = null)
@Serializable data class WantedResponse(val wanted: WantedDto)
@Serializable data class WantedStatusRequest(val status: String)
@Serializable data class WantedRespondRequest(val listingId: String? = null, val note: String = "")

@Serializable
data class WantedRespondResponse(
    val author: UserDto,
    val listing: ListingDto? = null,
    val threadId: String? = null,
    val firstResponse: Boolean = false,
)

@Serializable data class BlockRequest(val userId: String)
@Serializable data class BlocksResponse(val blocked: List<UserDto>)
@Serializable data class ReportRequest(val userId: String? = null, val listingId: String? = null, val reason: String, val details: String = "")
@Serializable data class OkResponse(val ok: Boolean = true)
