package app.campusbazaar.ui.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.SUGGESTED_SPOTS
import app.campusbazaar.ui.components.Field
import app.campusbazaar.ui.components.PriceTag
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.SelectChip
import app.campusbazaar.ui.components.TagSize
import app.campusbazaar.ui.theme.CB
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
private fun SheetFrame(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = CB.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            content()
        }
    }
}

/** Offer amount entry shared by "Make offer" and "Counter". */
@Composable
fun OfferSheet(
    title: String,
    listingTitle: String,
    askingPrice: Int,
    negotiable: Boolean,
    reference: Int?,
    busy: Boolean,
    onSubmit: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(if (!negotiable) askingPrice.toString() else "") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val amount = Money.parse(text)
    val error = if (submitted) DealRules.validateOffer(amount, askingPrice, negotiable) else null
    SheetFrame(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(listingTitle, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Asking", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            Spacer(Modifier.size(8.dp))
            PriceTag(askingPrice, size = TagSize.Small)
            if (reference != null) {
                Spacer(Modifier.size(14.dp))
                Text("Their offer", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
                Spacer(Modifier.size(8.dp))
                PriceTag(reference, size = TagSize.Small)
            }
        }
        Field(
            text, { text = it.filter(Char::isDigit).take(6) }, "Your offer",
            leading = { Text("₹", style = MaterialTheme.typography.titleMedium) },
            keyboardType = KeyboardType.Number, error = error,
            supporting = amount?.let { DealRules.offerHint(it, askingPrice) } ?: if (!negotiable) "The seller set a fixed price." else null,
            enabled = negotiable,
        )
        if (negotiable) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.95, 0.9, 0.85, 0.8).map { (askingPrice * it / 10).toInt() * 10 }.distinct().filter { it > 0 }.forEach { v ->
                    SelectChip(Money.rupees(v), amount == v, { text = v.toString() })
                }
            }
        }
        PrimaryButton("Send offer", {
            submitted = true
            if (DealRules.validateOffer(amount, askingPrice, negotiable) == null) onSubmit(amount!!)
        }, Modifier.fillMaxWidth(), loading = busy)
    }
}

private val utc = ZoneOffset.UTC

@Composable
fun MeetupSheet(listingTitle: String, defaultSpot: String, busy: Boolean, onSubmit: (spot: String, at: Instant, note: String, shareLocation: Boolean) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val tomorrow = LocalDate.now(zone).plusDays(1)
    var spot by rememberSaveable { mutableStateOf(defaultSpot) }
    var note by rememberSaveable { mutableStateOf("") }
    var dateEpochDay by rememberSaveable { mutableStateOf(tomorrow.toEpochDay()) }
    var hour by rememberSaveable { mutableIntStateOf(17) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var share by rememberSaveable { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> share = result.values.any { it } }
    val toggleShare: (Boolean) -> Unit = { on ->
        if (on && !app.campusbazaar.util.Permissions.hasLocation(context)) {
            locationPermission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
        } else share = on
    }

    val at = LocalDateTime.of(LocalDate.ofEpochDay(dateEpochDay), LocalTime.of(hour, minute)).atZone(zone).toInstant()
    val timeError = if (submitted && at.isBefore(Instant.now())) "Pick a time in the future." else null
    val spotError = if (submitted && spot.trim().length < 2) "Pick a meetup spot." else null

    SheetFrame(onDismiss) {
        Text("Propose a meetup", style = MaterialTheme.typography.headlineSmall)
        Text("For $listingTitle. Pick a busy, public spot on campus.", style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
        Field(spot, { spot = it }, "Spot", placeholder = "e.g. Central library entrance", error = spotError, maxLength = 80)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SUGGESTED_SPOTS.forEach { s -> SelectChip(s, spot == s, { spot = s }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerButton(Icons.Outlined.CalendarMonth, LocalDate.ofEpochDay(dateEpochDay).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)), Modifier.weight(1f)) { pickDate = true }
            PickerButton(Icons.Outlined.Schedule, LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)), Modifier.weight(1f)) { pickTime = true }
        }
        timeError?.let { Text(it, color = CB.Stamp, style = MaterialTheme.typography.bodySmall) }
        Field(note, { note = it }, "Note (optional)", placeholder = "e.g. I'll be in a blue hoodie", maxLength = 200)
        Row(Modifier.fillMaxWidth().clickable { toggleShare(!share) }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(share, { toggleShare(it) }, colors = CheckboxDefaults.colors(checkedColor = CB.Ink))
            Column {
                Text("Also share my current location", style = MaterialTheme.typography.bodyMedium)
                Text("Sends a map pin so they can find the spot.", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
        }
        PrimaryButton("Send proposal", {
            submitted = true
            if (spot.trim().length >= 2 && !at.isBefore(Instant.now())) onSubmit(spot.trim(), at, note.trim(), share)
        }, Modifier.fillMaxWidth(), loading = busy)
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(dateEpochDay).atStartOfDay(utc).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = Instant.ofEpochMilli(utcTimeMillis).atZone(utc).toLocalDate()
                    val today = LocalDate.now(zone)
                    return !d.isBefore(today) && !d.isAfter(today.plusDays(60))
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { QuietButton("Done", { state.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(utc).toLocalDate().toEpochDay() }; pickDate = false }) },
            dismissButton = { QuietButton("Cancel", { pickDate = false }) },
        ) { DatePicker(state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            containerColor = CB.Surface,
            confirmButton = { QuietButton("Done", { hour = state.hour; minute = state.minute; pickTime = false }) },
            dismissButton = { QuietButton("Cancel", { pickTime = false }) },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun PickerButton(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    app.campusbazaar.ui.components.SecondaryButton(text, onClick, modifier, icon = icon)
}

@Composable
fun RateSheet(peerName: String, peerRole: String, listingTitle: String, busy: Boolean, onSubmit: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var stars by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    SheetFrame(onDismiss) {
        Text("Rate $peerName", style = MaterialTheme.typography.headlineSmall)
        Text(if (peerRole == "buyer") "How was selling $listingTitle to them? Ratings help students trade safely." else "How was buying $listingTitle? Ratings help students trade safely.", style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { i ->
                IconButton(onClick = { stars = i }, modifier = Modifier.size(52.dp)) {
                    Icon(if (i <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = "$i star${if (i > 1) "s" else ""}",
                        tint = if (i <= stars) CB.Marigold else CB.InkFaint, modifier = Modifier.size(38.dp))
                }
            }
        }
        Text(
            when (stars) { 1 -> "Bad experience"; 2 -> "Not great"; 3 -> "Okay"; 4 -> "Good deal"; 5 -> "Excellent, would trade again"; else -> "Tap a star" },
            style = MaterialTheme.typography.labelMedium, color = CB.InkSoft,
        )
        Field(comment, { comment = it }, "Comment (optional)", placeholder = "On time, item as described…", singleLine = false, minLines = 2, maxLength = 400)
        Spacer(Modifier.height(4.dp))
        PrimaryButton("Submit rating", { if (stars > 0) onSubmit(stars, comment.trim()) }, Modifier.fillMaxWidth(), enabled = stars > 0, loading = busy)
    }
}
