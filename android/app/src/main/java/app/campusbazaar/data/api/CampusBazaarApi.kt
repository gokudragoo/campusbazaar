package app.campusbazaar.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit description of the CampusBazaar backend (server/src/app/api). */
interface CampusBazaarApi {
    @GET("api/config") suspend fun config(): ConfigResponse

    @POST("api/auth/signup") suspend fun signup(@Body body: SignupRequest): SessionResponse
    @POST("api/auth/login") suspend fun login(@Body body: LoginRequest): SessionResponse

    @GET("api/me") suspend fun me(): MeResponse
    @PATCH("api/me") suspend fun updateMe(@Body body: ProfileUpdateRequest): UserResponse
    @POST("api/me/nexconn-token") suspend fun refreshNexconnToken(): NexconnTokenResponse
    @GET("api/me/listings") suspend fun myListings(): ListingsResponse
    @GET("api/me/saved") suspend fun saved(): ListingsResponse

    @GET("api/campuses") suspend fun campuses(@Query("q") q: String?): CampusListResponse
    @POST("api/campuses") suspend fun createCampus(@Body body: CampusCreateRequest): CampusResponse
    @POST("api/campuses/{id}/join") suspend fun joinCampus(@Path("id") id: String): CampusResponse
    @GET("api/campus/square") suspend fun square(): SquareResponse

    @Multipart
    @POST("api/uploads")
    suspend fun upload(@Part file: MultipartBody.Part, @Part("kind") kind: RequestBody): UploadResponse

    @POST("api/ai/listing-suggest") suspend fun suggest(@Body body: SuggestRequest): SuggestResponse

    @GET("api/listings")
    suspend fun listings(
        @Query("q") q: String?,
        @Query("category") category: String?,
        @Query("condition") condition: String?,
        @Query("minPrice") minPrice: Int?,
        @Query("maxPrice") maxPrice: Int?,
        @Query("sort") sort: String?,
        @Query("cursor") cursor: String?,
    ): ListingPageResponse

    @POST("api/listings") suspend fun createListing(@Body body: ListingCreateRequest): ListingResponse
    @GET("api/listings/{id}") suspend fun listing(@Path("id") id: String): ListingDetailResponse
    @PATCH("api/listings/{id}") suspend fun updateListing(@Path("id") id: String, @Body body: ListingUpdateRequest): ListingResponse
    @DELETE("api/listings/{id}") suspend fun deleteListing(@Path("id") id: String): OkResponse
    @POST("api/listings/{id}/save") suspend fun save(@Path("id") id: String): OkResponse
    @DELETE("api/listings/{id}/save") suspend fun unsave(@Path("id") id: String): OkResponse
    @POST("api/listings/{id}/hide") suspend fun hide(@Path("id") id: String): OkResponse
    @POST("api/listings/{id}/sold") suspend fun markSold(@Path("id") id: String, @Body body: SoldRequest): ListingResponse

    @POST("api/chats/start") suspend fun startChat(@Body body: ChatStartRequest): ChatStartResponse
    @GET("api/chats/threads") suspend fun threads(): ThreadsResponse
    @GET("api/chats/peer/{nexconnUserId}") suspend fun peerState(@Path("nexconnUserId") nexconnUserId: String): PeerStateResponse
    @POST("api/chats/replied") suspend fun replied(@Body body: RepliedRequest): OkResponse
    @POST("api/chats/location") suspend fun relayLocation(@Body body: LocationRelayRequest): MessageIdResponse

    @POST("api/offers") suspend fun createOffer(@Body body: OfferCreateRequest): OfferCreateResponse
    @POST("api/offers/{id}/accept") suspend fun acceptOffer(@Path("id") id: String): OfferActionResponse
    @POST("api/offers/{id}/decline") suspend fun declineOffer(@Path("id") id: String): OfferActionResponse
    @POST("api/offers/{id}/counter") suspend fun counterOffer(@Path("id") id: String, @Body body: AmountRequest): OfferActionResponse
    @POST("api/offers/{id}/message") suspend fun attachOfferMessage(@Path("id") id: String, @Body body: MessageIdRequest): OfferActionResponse

    @POST("api/meetups") suspend fun createMeetup(@Body body: MeetupCreateRequest): MeetupResponse
    @POST("api/meetups/{id}/{action}") suspend fun meetupAction(@Path("id") id: String, @Path("action") action: String): MeetupResponse
    @POST("api/meetups/{id}/message") suspend fun attachMeetupMessage(@Path("id") id: String, @Body body: MessageIdRequest): MeetupResponse

    @POST("api/ratings") suspend fun rate(@Body body: RatingRequest): OkResponse

    @GET("api/users/{id}") suspend fun profile(@Path("id") id: String): ProfileResponse
    @POST("api/users/lookup") suspend fun lookup(@Body body: LookupRequest): UsersResponse

    @GET("api/wanted") suspend fun wanted(@Query("category") category: String?, @Query("mine") mine: String?, @Query("cursor") cursor: String?): WantedPageResponse
    @POST("api/wanted") suspend fun createWanted(@Body body: WantedCreateRequest): WantedResponse
    @PATCH("api/wanted/{id}") suspend fun setWantedStatus(@Path("id") id: String, @Body body: WantedStatusRequest): WantedResponse
    @DELETE("api/wanted/{id}") suspend fun deleteWanted(@Path("id") id: String): OkResponse
    @POST("api/wanted/{id}/respond") suspend fun respondWanted(@Path("id") id: String, @Body body: WantedRespondRequest): WantedRespondResponse

    @GET("api/blocks") suspend fun blocks(): BlocksResponse
    @POST("api/blocks") suspend fun block(@Body body: BlockRequest): OkResponse
    @DELETE("api/blocks/{userId}") suspend fun unblock(@Path("userId") userId: String): OkResponse
    @POST("api/reports") suspend fun report(@Body body: ReportRequest): OkResponse
}
