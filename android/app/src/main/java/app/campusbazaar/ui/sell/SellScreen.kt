package app.campusbazaar.ui.sell

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.campusbazaar.domain.Category
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.SUGGESTED_SPOTS
import app.campusbazaar.ui.CameraRoute
import app.campusbazaar.ui.ListingRoute
import app.campusbazaar.ui.SellRoute
import app.campusbazaar.ui.appViewModel
import app.campusbazaar.ui.components.ConfirmDialog
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.LoadingBlock
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.theme.CB
import coil3.compose.AsyncImage

const val CAMERA_RESULT_KEY = "captured_photo"

@Composable
fun SellScreen(nav: NavHostController, editId: String?) {
    val vm = appViewModel(key = "sell-${editId ?: "new"}") { c, h -> SellViewModel(c.market, h, editId) }
    val draft by vm.draft.collectAsState()
    val ai by vm.ai.collectAsState()
    val publishing by vm.publishing.collectAsState()
    val loadingEdit by vm.loadingEdit.collectAsState()
    val submitted by vm.submitted.collectAsState()
    val context = LocalContext.current
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val errors = if (submitted) vm.errorsFor(draft) else emptyMap()
    val room = SellViewModel.MAX_PHOTOS - draft.photos.size

    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(SellViewModel.MAX_PHOTOS)) { uris -> if (uris.isNotEmpty()) vm.addPhotos(uris) }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.addPhotos(listOf(uri)) }

    // Photos captured by the in-app camera come back through the saved state handle.
    val entry = nav.currentBackStackEntry
    LaunchedEffect(entry) {
        entry?.savedStateHandle?.getStateFlow<String?>(CAMERA_RESULT_KEY, null)?.collect { uri ->
            if (uri != null) {
                vm.addPhotos(listOf(Uri.parse(uri)))
                entry.savedStateHandle[CAMERA_RESULT_KEY] = null
            }
        }
    }
    LaunchedEffect(Unit) { vm.errors.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
    LaunchedEffect(Unit) {
        vm.published.collect { id ->
            Toast.makeText(context, if (vm.isEdit) "Changes saved" else "Your item is live on campus", Toast.LENGTH_SHORT).show()
            if (vm.isEdit) nav.popBackStack() else nav.navigate(ListingRoute(id)) { popUpTo<SellRoute> { inclusive = true } }
        }
    }

    val dirty = draft.photos.isNotEmpty() || draft.title.isNotBlank()
    androidx.activity.compose.BackHandler(enabled = dirty && !vm.isEdit) { confirmLeave = true }

    Column(Modifier.fillMaxSize().background(CB.Paper).imePadding()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (dirty && !vm.isEdit) confirmLeave = true else nav.popBackStack() }) { Icon(Icons.Outlined.Close, "Close") }
            Text(if (vm.isEdit) "Edit listing" else "Sell an item", style = MaterialTheme.typography.titleLarge)
        }
        if (loadingEdit) {
            LoadingBlock()
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Photos
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Photos", style = MaterialTheme.typography.titleMedium)
                Text("Up to 5. The first photo is the cover. Good light sells faster.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    draft.photos.forEachIndexed { i, p -> PhotoThumb(p, cover = i == 0, onRemove = { vm.removePhoto(p.id) }, onMakeCover = { vm.movePhotoFirst(p.id) }) }
                    if (room > 0) {
                        AddTile(Icons.Outlined.PhotoCamera, "Camera") { nav.navigate(CameraRoute) }
                        AddTile(Icons.Outlined.AddPhotoAlternate, "Gallery") {
                            val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            if (room >= 2) pickMany.launch(req) else pickOne.launch(req)
                        }
                    }
                }
                errors["photos"]?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodySmall) }
            }

            AiPanel(ai, hasPhotos = draft.photos.any { it.remoteUrl != null }, onSuggest = vm::suggest, onApply = vm::applySuggestion)

            Field(draft.title, vm::setTitle, "Title", placeholder = "e.g. Casio fx-991EX scientific calculator", error = errors["title"], maxLength = 80)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c -> SelectChip(c.label, draft.category == c.id, { vm.setCategory(c.id) }) }
                }
                errors["category"]?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodySmall) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Condition", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Condition.entries.forEach { c -> SelectChip(c.label, draft.condition == c.id, { vm.setCondition(c.id) }) }
                }
                draft.condition?.let { Text(Condition.of(it).hint, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft) }
                errors["condition"]?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodySmall) }
            }

            val priceValue = Money.parse(draft.price)
            Field(
                draft.price, vm::setPrice, "Price",
                leading = { Text("₹", style = MaterialTheme.typography.titleMedium, color = CB.Ink) },
                keyboardType = KeyboardType.Number,
                error = errors["price"],
                supporting = when {
                    draft.aiPriceMin != null && draft.aiPriceMax != null ->
                        (DealRules.priceVersusRange(priceValue ?: 0, draft.aiPriceMin, draft.aiPriceMax)?.let { "$it " } ?: "") +
                            "AI suggests ${Money.rupees(draft.aiPriceMin!!)} to ${Money.rupees(draft.aiPriceMax!!)} for used items like this."
                    else -> null
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Open to offers", style = MaterialTheme.typography.titleSmall)
                    Text(if (draft.negotiable) "Buyers can send offer cards in chat." else "Buyers can only offer your asking price.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                }
                Switch(draft.negotiable, vm::setNegotiable, colors = SwitchDefaults.colors(checkedTrackColor = CB.Ink, checkedThumbColor = Color.White))
            }

            Field(draft.description, vm::setDescription, "Description", placeholder = "Edition, what's included, any marks or faults", singleLine = false, minLines = 3, maxLength = 1500)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(draft.meetupSpot, vm::setSpot, "Preferred meetup spot", placeholder = "Somewhere public on campus", maxLength = 80)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SUGGESTED_SPOTS.forEach { s -> SelectChip(s, draft.meetupSpot == s, { vm.setSpot(s) }) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.fillMaxWidth().background(CB.Surface).navigationBarsPadding().padding(16.dp)) {
            PrimaryButton(if (vm.isEdit) "Save changes" else "Publish to campus", vm::publish, Modifier.fillMaxWidth(), loading = publishing, accent = !vm.isEdit)
        }
    }

    if (confirmLeave) {
        ConfirmDialog("Discard this listing?", "Your photos and details will be lost.", "Discard", { confirmLeave = false; nav.popBackStack() }, { confirmLeave = false }, danger = true)
    }
}

@Composable
private fun PhotoThumb(p: PhotoSlot, cover: Boolean, onRemove: () -> Unit, onMakeCover: () -> Unit) {
    Box(Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(CB.InkWash).clickable(enabled = !cover && p.remoteUrl != null, onClick = onMakeCover)) {
        AsyncImage(model = p.remoteUrl ?: p.preview, contentDescription = if (cover) "Cover photo" else "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (p.uploading) {
            Box(Modifier.fillMaxSize().background(Color(0x99FFFFFF)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), color = CB.Ink, strokeWidth = 2.dp)
            }
        }
        if (p.error != null) {
            Box(Modifier.fillMaxSize().background(Color(0xE6FFFFFF)).padding(6.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = CB.Stamp, modifier = Modifier.size(20.dp))
                    Text("Upload failed", style = MaterialTheme.typography.labelSmall, color = CB.Stamp)
                }
            }
        }
        if (cover) {
            Text("Cover", style = MaterialTheme.typography.labelSmall, color = CB.Ink, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).background(CB.Marigold, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp).background(CB.Surface, CircleShape).border(1.dp, CB.Hairline, CircleShape).clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Close, "Remove photo", Modifier.size(15.dp)) }
    }
}

@Composable
private fun AddTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(104.dp).clip(RoundedCornerShape(8.dp)).background(CB.Surface).border(1.dp, CB.Hairline, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = CB.Ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AiPanel(ai: AiState, hasPhotos: Boolean, onSuggest: () -> Unit, onApply: (app.campusbazaar.data.api.SuggestionDto) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(CB.Surface, RoundedCornerShape(8.dp)).border(1.dp, CB.Hairline, RoundedCornerShape(8.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = CB.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("AI listing helper", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (ai is AiState.Working) CircularProgressIndicator(Modifier.size(18.dp), color = CB.Ink, strokeWidth = 2.dp)
        }
        when (ai) {
            AiState.Idle -> {
                Text(
                    if (hasPhotos) "Let AI look at your photos and draft the title, category, condition, description and a fair used price."
                    else "Add a photo and AI drafts the title, category, condition, description and a fair used price. You can edit everything.",
                    style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
                )
                if (hasPhotos) SecondaryButton("Draft from photos", onSuggest)
            }
            AiState.Working -> Text("Looking at your photos…", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            is AiState.Suggested -> {
                Text(
                    "Drafted \"${ai.s.title}\" with a price range of ${Money.rupees(ai.s.priceMin)} to ${Money.rupees(ai.s.priceMax)}" +
                        (if (ai.s.confidence == "low") ". It wasn't sure what the item is, so check the details." else ". Edit anything below."),
                    style = MaterialTheme.typography.bodySmall, color = CB.InkSoft,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuietButton("Use full draft", { onApply(ai.s) })
                    QuietButton("Try again", onSuggest)
                }
            }
            is AiState.Failed -> {
                Text(ai.message, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                QuietButton("Try again", onSuggest)
            }
        }
    }
}
