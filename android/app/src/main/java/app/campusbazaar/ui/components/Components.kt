package app.campusbazaar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.campusbazaar.domain.Money
import app.campusbazaar.ui.theme.AnekCondensed
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.ui.theme.PriceText
import coil3.compose.AsyncImage

/** A price sticker: angled left edge with a punched hole, like second-hand bookshop tags. */
object TagShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notch = size.height * 0.42f
        val r = with(density) { 3.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, size.height / 2f)
            lineTo(notch, 0f)
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(notch, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

enum class TagSize { Small, Medium, Large }

@Composable
fun PriceTag(amount: Int, modifier: Modifier = Modifier, size: TagSize = TagSize.Medium, struck: Boolean = false) {
    val style: TextStyle = when (size) {
        TagSize.Small -> PriceText.Small
        TagSize.Medium -> PriceText.Medium
        TagSize.Large -> PriceText.Large
    }
    val height = when (size) { TagSize.Small -> 24.dp; TagSize.Medium -> 32.dp; TagSize.Large -> 46.dp }
    val hole = when (size) { TagSize.Small -> 4.dp; TagSize.Medium -> 5.dp; TagSize.Large -> 7.dp }
    Row(
        modifier = modifier
            .height(height)
            .background(CB.Marigold, TagShape)
            .padding(start = height * 0.5f, end = if (size == TagSize.Large) 14.dp else 9.dp)
            .semantics { contentDescription = "Price ${Money.rupees(amount)}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(hole).background(CB.Paper, CircleShape))
        Spacer(Modifier.width(if (size == TagSize.Large) 8.dp else 5.dp))
        Text(
            Money.rupees(amount),
            style = style.copy(textDecoration = if (struck) androidx.compose.ui.text.style.TextDecoration.LineThrough else null),
            maxLines = 1,
        )
    }
}

/** Rubber-stamp overlay for SOLD / RESERVED. */
@Composable
fun StatusStamp(text: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(
        modifier = modifier
            .rotate(-8f)
            .background(CB.Surface, RoundedCornerShape(4.dp))
            .border(BorderStroke(if (large) 2.5.dp else 1.5.dp, CB.Stamp), RoundedCornerShape(4.dp))
            .padding(horizontal = if (large) 12.dp else 7.dp, vertical = if (large) 4.dp else 1.dp),
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = AnekCondensed,
                fontWeight = FontWeight(800),
                fontSize = if (large) 22.sp else 13.sp,
                letterSpacing = 0.08.em,
                color = CB.Stamp,
            ),
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    accent: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 50.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) CB.Marigold else CB.Ink,
            contentColor = if (accent) CB.Ink else Color.White,
            disabledContainerColor = CB.InkWash,
            disabledContentColor = CB.InkFaint,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = if (accent) CB.Ink else Color.White)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (danger) CB.Stamp else CB.Ink.copy(alpha = if (enabled) 0.85f else 0.25f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (danger) CB.Stamp else CB.Ink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
    TextButton(onClick = onClick, modifier = modifier, enabled = enabled, colors = ButtonDefaults.textButtonColors(contentColor = if (danger) CB.Stamp else CB.Ink)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    maxLength: Int? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = CB.InkSoft, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { if (maxLength == null || it.length <= maxLength) onValueChange(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder?.let { { Text(it, color = CB.InkFaint) } },
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            leadingIcon = leading,
            trailingIcon = trailing,
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CB.Ink,
                unfocusedBorderColor = CB.Hairline,
                focusedContainerColor = CB.Surface,
                unfocusedContainerColor = CB.Surface,
                errorBorderColor = CB.Stamp,
                cursorColor = CB.Ink,
            ),
        )
        val note = error ?: supporting
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) CB.Stamp else CB.InkSoft,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

/** Filter/category chip: ink fill when selected, hairline outline otherwise. */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) CB.Ink else CB.Surface,
        contentColor = if (selected) Color.White else CB.Ink,
        border = if (selected) null else BorderStroke(1.dp, CB.Hairline),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun Avatar(name: String, url: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(CB.MarigoldWash)
            .border(1.dp, CB.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.titleSmall.copy(fontSize = (size.value * 0.36f).sp), color = CB.Ink)
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun RatingLine(avg: Double?, count: Int, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Star, contentDescription = null, tint = CB.Ink, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            if (avg == null || count == 0) "No ratings yet" else "${"%.1f".format(avg)} from $count ${if (count == 1) "rating" else "ratings"}",
            style = MaterialTheme.typography.bodySmall,
            color = CB.InkSoft,
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(64.dp).background(CB.MarigoldWash, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = CB.Ink, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = if (message.contains("offline", ignoreCase = true)) Icons.Outlined.CloudOff else Icons.Outlined.ErrorOutline,
        title = "Couldn't load this",
        body = message,
        modifier = modifier,
        action = { SecondaryButton("Try again", onRetry) },
    )
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = CB.Ink, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier, text: String = "You're offline. Showing what was loaded last.") {
    Row(
        modifier.fillMaxWidth().background(CB.Ink).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** Square listing photo with optional stamp; falls back to a calm placeholder. */
@Composable
fun ListingPhoto(url: String?, status: String?, modifier: Modifier = Modifier, stampLarge: Boolean = false) {
    Box(modifier.background(CB.InkWash), contentAlignment = Alignment.Center) {
        if (!url.isNullOrBlank()) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when (status) {
            "sold" -> StatusStamp("SOLD", Modifier.align(Alignment.Center), large = stampLarge)
            "reserved" -> StatusStamp("RESERVED", Modifier.align(Alignment.TopEnd).padding(8.dp), large = stampLarge)
        }
    }
}

@Composable
fun ListingTile(
    title: String,
    price: Int,
    photo: String?,
    status: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(CB.Surface)
            .border(1.dp, CB.Hairline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Box {
            ListingPhoto(photo, status, Modifier.fillMaxWidth().aspectRatio(1f))
            PriceTag(price, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp), size = TagSize.Small)
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 38.dp))
            Text(meta, style = MaterialTheme.typography.bodySmall, color = CB.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(CB.Hairline))
}

/** A titled white block with a hairline border, used for grouped settings and detail sections. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CB.Surface)
            .border(1.dp, CB.Hairline, RoundedCornerShape(8.dp)),
    ) { content() }
}
