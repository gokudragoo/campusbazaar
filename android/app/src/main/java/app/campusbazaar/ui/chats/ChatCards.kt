package app.campusbazaar.ui.chats

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.campusbazaar.chat.ChatBody
import app.campusbazaar.chat.ChatItem
import app.campusbazaar.chat.SendState
import app.campusbazaar.chat.messages.DealUpdateMessage
import app.campusbazaar.domain.Condition
import app.campusbazaar.domain.DealRules
import app.campusbazaar.domain.Money
import app.campusbazaar.domain.TimeText
import app.campusbazaar.ui.components.PriceTag
import app.campusbazaar.ui.components.QuietButton
import app.campusbazaar.ui.components.StatusStamp
import app.campusbazaar.ui.components.TagSize
import app.campusbazaar.ui.theme.CB
import coil3.compose.AsyncImage

private val bubbleShapeMine = RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)
private val bubbleShapeTheirs = RoundedCornerShape(14.dp, 14.dp, 14.dp, 4.dp)

@Composable
fun MessageMeta(item: ChatItem, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)) {
        if (item.state == SendState.Failed) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = CB.Stamp, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("Not sent. Tap to retry", style = MaterialTheme.typography.labelSmall, color = CB.Stamp, modifier = Modifier.clickable(onClick = onRetry))
            return@Row
        }
        Text(TimeText.clock(item.sentTime), style = MaterialTheme.typography.labelSmall, color = CB.InkFaint)
        if (item.mine) {
            Spacer(Modifier.width(4.dp))
            when (item.state) {
                SendState.Sending -> Icon(Icons.Outlined.AccessTime, "Sending", tint = CB.InkFaint, modifier = Modifier.size(13.dp))
                SendState.Sent -> Icon(Icons.Outlined.Done, "Sent", tint = CB.InkFaint, modifier = Modifier.size(14.dp))
                SendState.Read -> Icon(Icons.Outlined.DoneAll, "Seen", tint = CB.Ink, modifier = Modifier.size(14.dp))
                SendState.Failed -> Unit
            }
        }
    }
}

@Composable
fun TextBubble(text: String, mine: Boolean) {
    Box(
        Modifier.widthIn(max = 300.dp)
            .background(if (mine) CB.Ink else CB.Surface, if (mine) bubbleShapeMine else bubbleShapeTheirs)
            .then(if (mine) Modifier else Modifier.border(1.dp, CB.Hairline, bubbleShapeTheirs))
            .padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (mine) Color.White else CB.Ink)
    }
}

@Composable
fun ImageBubble(body: ChatBody.Image, mine: Boolean, onOpen: (String) -> Unit) {
    val thumb = remember(body.thumbnailBase64) {
        body.thumbnailBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
            runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
        }
    }
    Box(
        Modifier.size(width = 220.dp, height = 220.dp).clip(if (mine) bubbleShapeMine else bubbleShapeTheirs).background(CB.InkWash)
            .clickable(enabled = body.url != null) { body.url?.let(onOpen) },
    ) {
        if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (body.url != null) AsyncImage(model = body.url, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        body.progress?.let { p ->
            Text("Uploading $p%", style = MaterialTheme.typography.labelSmall, color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(CB.Ink, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun LocationBubble(body: ChatBody.Location) {
    val context = LocalContext.current
    Column(
        Modifier.widthIn(max = 280.dp).background(CB.Surface, RoundedCornerShape(10.dp)).border(1.dp, CB.Hairline, RoundedCornerShape(10.dp))
            .clickable {
                val label = Uri.encode(body.label.ifBlank { "Meetup" })
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${body.lat},${body.lng}?q=${body.lat},${body.lng}($label)")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .padding(12.dp),
    ) {
        val thumb = remember(body.thumbnailBase64) {
            body.thumbnailBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
                runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
            }
        }
        if (thumb != null) {
            Image(thumb, contentDescription = "Map pin", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Place, null, tint = CB.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(body.label.ifBlank { "Shared location" }, style = MaterialTheme.typography.titleSmall)
        }
        Text("%.5f, %.5f".format(body.lat, body.lng), style = MaterialTheme.typography.bodySmall, color = CB.InkSoft, modifier = Modifier.padding(top = 2.dp, start = 28.dp))
        Row(Modifier.padding(top = 6.dp, start = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Map, null, tint = CB.Ink, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(4.dp))
            Text("Open in Maps", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun ListingCardBubble(body: ChatBody.ListingCard, onOpen: () -> Unit) {
    Row(
        Modifier.widthIn(max = 310.dp).fillMaxWidth().background(CB.Surface, RoundedCornerShape(10.dp)).border(1.dp, CB.Hairline, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(CB.InkWash)) {
            if (body.photo.isNotBlank()) AsyncImage(model = body.photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("About this item", style = MaterialTheme.typography.labelSmall, color = CB.InkSoft)
            Text(body.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriceTag(body.price, size = TagSize.Small)
                Spacer(Modifier.width(8.dp))
                Text(Condition.of(body.condition).label, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
        }
    }
}

private fun offerStateLabel(state: String, mine: Boolean): String = when (state) {
    DealRules.PENDING -> if (mine) "Waiting for a reply" else "Waiting for your reply"
    DealRules.ACCEPTED -> "Accepted"
    DealRules.DECLINED -> "Declined"
    DealRules.COUNTERED -> "Countered"
    DealRules.WITHDRAWN -> "Replaced by a newer offer"
    else -> state
}

@Composable
fun OfferCardBubble(
    body: ChatBody.OfferCard,
    mine: Boolean,
    state: String,
    canRespond: Boolean,
    busy: Boolean,
    onAccept: () -> Unit,
    onCounter: () -> Unit,
    onDecline: () -> Unit,
) {
    Column(
        Modifier.widthIn(max = 300.dp).fillMaxWidth().background(CB.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, if (state == DealRules.PENDING) CB.Ink else CB.Hairline, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sell, null, tint = CB.InkSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (body.parentAmount > 0) "Counter offer" else "Offer", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
            Spacer(Modifier.weight(1f))
            when (state) {
                DealRules.ACCEPTED -> StatusStamp("ACCEPTED")
                DealRules.DECLINED -> StatusStamp("DECLINED")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PriceTag(body.amount, size = TagSize.Large, struck = state == DealRules.COUNTERED || state == DealRules.WITHDRAWN)
            if (body.askingPrice > 0 && body.askingPrice != body.amount) {
                Spacer(Modifier.width(10.dp))
                Text("asking ${Money.rupees(body.askingPrice)}", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
            }
        }
        Text(body.listingTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (body.parentAmount > 0) Text("In reply to ${Money.rupees(body.parentAmount)}", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
        Text(offerStateLabel(state, mine), style = MaterialTheme.typography.labelMedium, color = if (state == DealRules.DECLINED) CB.Stamp else CB.Ink)
        if (canRespond) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) CB.InkWash else CB.Ink).clickable(enabled = !busy, onClick = onAccept).padding(horizontal = 16.dp, vertical = 10.dp),
                ) { Text("Accept", style = MaterialTheme.typography.labelLarge, color = Color.White) }
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, CB.Ink, RoundedCornerShape(8.dp)).clickable(enabled = !busy, onClick = onCounter).padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text("Counter", style = MaterialTheme.typography.labelLarge, color = CB.Ink) }
                QuietButton("Decline", onDecline, danger = true, enabled = !busy)
            }
        }
    }
}

@Composable
fun MeetupCardBubble(body: ChatBody.MeetupCard, mine: Boolean, state: String, busy: Boolean, onConfirm: () -> Unit, onDecline: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.widthIn(max = 300.dp).fillMaxWidth().background(CB.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, if (state == "proposed") CB.Ink else CB.Hairline, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Event, null, tint = CB.InkSoft, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Meetup", style = MaterialTheme.typography.labelMedium, color = CB.InkSoft)
            Spacer(Modifier.weight(1f))
            when (state) {
                "confirmed" -> StatusStamp("CONFIRMED")
                "declined" -> StatusStamp("DECLINED")
                "cancelled" -> StatusStamp("CANCELLED")
            }
        }
        Text(body.spot, style = MaterialTheme.typography.titleMedium)
        Text(TimeText.meetup(body.scheduledAt), style = MaterialTheme.typography.bodyMedium)
        Text(body.listingTitle, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (body.note.isNotBlank()) Text("“${body.note}”", style = MaterialTheme.typography.bodySmall, color = CB.InkSoft)
        if (body.lat != null && body.lng != null) {
            Text("Open pin in Maps", style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${body.lat},${body.lng}?q=${body.lat},${body.lng}(${Uri.encode(body.spot)})")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
        if (state == "proposed") {
            if (mine) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Waiting for a reply", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    QuietButton("Cancel", onCancel, enabled = !busy)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(if (busy) CB.InkWash else CB.Ink).clickable(enabled = !busy, onClick = onConfirm).padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("Confirm", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    }
                    QuietButton("Can't make it", onDecline, danger = true, enabled = !busy)
                }
            }
        }
    }
}

@Composable
fun DealPill(body: ChatBody.Deal, mine: Boolean) {
    val text = when (body.kind) {
        DealUpdateMessage.KIND_SOLD -> if (mine) "You marked ${body.listingTitle} as sold${if (body.amount > 0) " for ${Money.rupees(body.amount)}" else ""}. Rate each other below."
        else "${body.listingTitle} is yours${if (body.amount > 0) " for ${Money.rupees(body.amount)}" else ""}. Rate the seller below."
        DealUpdateMessage.KIND_RESERVED -> "Deal agreed at ${Money.rupees(body.amount)}. ${body.listingTitle} is reserved. Pick a meetup spot."
        else -> "${body.listingTitle} is available again."
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            text, style = MaterialTheme.typography.bodySmall, color = CB.Ink, textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp).background(CB.MarigoldWash, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun DaySeparator(epochMs: Long) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Text(TimeText.short(epochMs).let { if (it.contains(":")) "Today" else it }, style = MaterialTheme.typography.labelSmall, color = CB.InkSoft,
            modifier = Modifier.background(CB.InkWash, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

