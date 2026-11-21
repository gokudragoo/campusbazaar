package app.campusbazaar.ui.sell

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.campusbazaar.data.api.ListingCreateRequest
import app.campusbazaar.data.api.ListingUpdateRequest
import app.campusbazaar.data.api.SuggestionDto
import app.campusbazaar.data.api.appJson
import app.campusbazaar.data.repo.MarketRepository
import app.campusbazaar.domain.InputRules
import app.campusbazaar.domain.Money
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class PhotoSlot(val id: String, val preview: String, val remoteUrl: String? = null, val uploading: Boolean = false, val error: String? = null)

@Serializable
data class SellDraft(
    val photos: List<PhotoSlot> = emptyList(),
    val title: String = "",
    val description: String = "",
    val category: String? = null,
    val condition: String? = null,
    val price: String = "",
    val negotiable: Boolean = true,
    val meetupSpot: String = "",
    val aiUsed: Boolean = false,
    val aiPriceMin: Int? = null,
    val aiPriceMax: Int? = null,
)

sealed interface AiState {
    data object Idle : AiState
    data object Working : AiState
    data class Suggested(val s: SuggestionDto) : AiState
    data class Failed(val message: String) : AiState
}

/**
 * Sell form state. The whole draft is mirrored into SavedStateHandle so a half-filled listing
 * (including already-uploaded photo URLs) survives process death while the camera is open.
 */
class SellViewModel(private val market: MarketRepository, private val saved: SavedStateHandle, private val editId: String?) : ViewModel() {
    private val _draft = MutableStateFlow(
        saved.get<String>(KEY)?.let { runCatching { appJson.decodeFromString(SellDraft.serializer(), it) }.getOrNull() }
            ?.let { d -> d.copy(photos = d.photos.map { if (it.uploading) it.copy(uploading = false, error = if (it.remoteUrl == null) "Upload interrupted. Remove and add again." else null) else it }) }
            ?: SellDraft(),
    )
    val draft = _draft.asStateFlow()
    private val _ai = MutableStateFlow<AiState>(AiState.Idle)
    val ai = _ai.asStateFlow()
    private val _publishing = MutableStateFlow(false)
    val publishing = _publishing.asStateFlow()
    private val _loadingEdit = MutableStateFlow(editId != null && saved.get<String>(KEY) == null)
    val loadingEdit = _loadingEdit.asStateFlow()
    private val _submitted = MutableStateFlow(false)
    val submitted = _submitted.asStateFlow()
    private val _published = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val published = _published.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors = _errors.asSharedFlow()

    val isEdit get() = editId != null
    private var counter = System.currentTimeMillis()

    init {
        if (editId != null && saved.get<String>(KEY) == null) loadExisting(editId)
    }

    private fun edit(transform: (SellDraft) -> SellDraft) {
        val next = transform(_draft.value)
        _draft.value = next
        saved[KEY] = appJson.encodeToString(SellDraft.serializer(), next)
    }

    private fun loadExisting(id: String) {
        viewModelScope.launch {
            market.listing(id).onSuccess { r ->
                val l = r.listing
                edit {
                    SellDraft(
                        photos = l.photos.mapIndexed { i, url -> PhotoSlot("r$i", url, url) },
                        title = l.title, description = l.description, category = l.category, condition = l.condition,
                        price = l.price.toString(), negotiable = l.negotiable, meetupSpot = l.meetupSpot,
                        aiUsed = l.aiAssisted, aiPriceMin = l.aiPriceMin, aiPriceMax = l.aiPriceMax,
                    )
                }
            }.onFailure { _errors.tryEmit(it.message ?: "Couldn't load the listing") }
            _loadingEdit.value = false
        }
    }

    fun addPhotos(uris: List<Uri>) {
        val room = MAX_PHOTOS - _draft.value.photos.size
        if (room <= 0) {
            _errors.tryEmit("You can add up to $MAX_PHOTOS photos.")
            return
        }
        uris.take(room).forEach { uri ->
            val slot = PhotoSlot(id = "p${counter++}", preview = uri.toString(), uploading = true)
            edit { it.copy(photos = it.photos + slot) }
            viewModelScope.launch {
                market.uploadPhoto(uri).onSuccess { url ->
                    edit { d -> d.copy(photos = d.photos.map { if (it.id == slot.id) it.copy(remoteUrl = url, uploading = false, error = null) else it }) }
                    maybeAutoSuggest()
                }.onFailure { e ->
                    edit { d -> d.copy(photos = d.photos.map { if (it.id == slot.id) it.copy(uploading = false, error = e.message) else it }) }
                }
            }
        }
    }

    fun removePhoto(id: String) = edit { d -> d.copy(photos = d.photos.filterNot { it.id == id }) }

    fun movePhotoFirst(id: String) = edit { d ->
        val p = d.photos.firstOrNull { it.id == id } ?: return@edit d
        d.copy(photos = listOf(p) + d.photos.filterNot { it.id == id })
    }

    private fun maybeAutoSuggest() {
        val d = _draft.value
        // Draft automatically once, when the first photo lands and the form is still empty.
        if (!isEdit && d.title.isBlank() && _ai.value == AiState.Idle && d.photos.none { it.uploading }) suggest()
    }

    fun suggest() {
        val urls = _draft.value.photos.mapNotNull { it.remoteUrl }
        if (urls.isEmpty()) {
            _errors.tryEmit("Add a photo first, then AI can draft the details.")
            return
        }
        if (_ai.value == AiState.Working) return
        _ai.value = AiState.Working
        viewModelScope.launch {
            market.suggest(urls.take(3)).onSuccess { s ->
                _ai.value = AiState.Suggested(s)
                // A low-confidence guess stays a suggestion; the seller can still apply it explicitly.
                if (s.confidence == "low" || s.priceMax <= 0) return@onSuccess
                edit { d ->
                    d.copy(
                        title = if (d.title.isBlank()) s.title else d.title,
                        description = if (d.description.isBlank()) s.description else d.description,
                        category = d.category ?: s.category,
                        condition = d.condition ?: s.condition,
                        price = d.price.ifBlank { (((s.priceMin + s.priceMax) / 2) / 10 * 10).toString() },
                        aiUsed = true, aiPriceMin = s.priceMin, aiPriceMax = s.priceMax,
                    )
                }
            }.onFailure { e -> _ai.value = AiState.Failed(e.message ?: "The AI helper is busy. Fill in the details yourself.") }
        }
    }

    /** Replaces the fields with the AI draft (the seller asked for it explicitly). */
    fun applySuggestion(s: SuggestionDto) = edit {
        it.copy(title = s.title, description = s.description, category = s.category, condition = s.condition,
            price = if (s.priceMax > 0) (((s.priceMin + s.priceMax) / 2) / 10 * 10).toString() else it.price,
            aiUsed = true, aiPriceMin = s.priceMin.takeIf { s.priceMax > 0 }, aiPriceMax = s.priceMax.takeIf { it > 0 })
    }

    fun setTitle(v: String) = edit { it.copy(title = v) }
    fun setDescription(v: String) = edit { it.copy(description = v) }
    fun setCategory(v: String) = edit { it.copy(category = v) }
    fun setCondition(v: String) = edit { it.copy(condition = v) }
    fun setPrice(v: String) = edit { it.copy(price = v.filter(Char::isDigit).take(6)) }
    fun setNegotiable(v: Boolean) = edit { it.copy(negotiable = v) }
    fun setSpot(v: String) = edit { it.copy(meetupSpot = v) }

    fun errorsFor(d: SellDraft): Map<String, String> = buildMap {
        if (d.photos.none { it.remoteUrl != null }) put("photos", "Add at least one photo.")
        if (d.photos.any { it.uploading }) put("photos", "Wait for photos to finish uploading.")
        InputRules.titleError(d.title)?.let { put("title", it) }
        if (d.category == null) put("category", "Pick a category.")
        if (d.condition == null) put("condition", "Pick the condition.")
        val price = Money.parse(d.price)
        if (price == null) put("price", "Enter a price in rupees.") else if (price > 500_000) put("price", "That's above ₹5,00,000.")
    }

    fun publish() {
        _submitted.value = true
        val d = _draft.value
        if (errorsFor(d).isNotEmpty() || _publishing.value) return
        _publishing.value = true
        viewModelScope.launch {
            val photos = d.photos.mapNotNull { it.remoteUrl }
            val result = if (editId == null) {
                market.create(
                    ListingCreateRequest(
                        title = d.title.trim(), description = d.description.trim(), category = d.category!!, condition = d.condition!!,
                        price = Money.parse(d.price)!!, negotiable = d.negotiable, meetupSpot = d.meetupSpot.trim(), photos = photos,
                        aiAssisted = d.aiUsed, aiPriceMin = d.aiPriceMin, aiPriceMax = d.aiPriceMax,
                    ),
                )
            } else {
                market.update(
                    editId,
                    ListingUpdateRequest(
                        title = d.title.trim(), description = d.description.trim(), category = d.category, condition = d.condition,
                        price = Money.parse(d.price), negotiable = d.negotiable, meetupSpot = d.meetupSpot.trim(), photos = photos,
                    ),
                )
            }
            _publishing.value = false
            result.onSuccess { listing ->
                saved.remove<String>(KEY)
                _published.tryEmit(listing.id)
            }.onFailure { _errors.tryEmit(it.message ?: "Couldn't publish. Try again.") }
        }
    }

    companion object {
        const val MAX_PHOTOS = 5
        private const val KEY = "sell_draft"
    }
}

