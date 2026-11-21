package app.campusbazaar.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.campusbazaar.MainActivity
import app.campusbazaar.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.max

object ImageCompressor {
    private const val MAX_EDGE = 1600
    private const val MAX_BYTES = 1_500_000

    /** Decodes with sampling, applies EXIF rotation, scales to 1600 px and re-encodes as JPEG under ~1.5 MB. */
    fun compress(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "not an image" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("decode failed")
        val rotation = runCatching {
            resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        }.getOrDefault(0)
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val bitmap = if (!matrix.isIdentity) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true) else decoded
        var quality = 85
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                stream.toByteArray()
            }
            quality -= 10
        } while (out.size > MAX_BYTES && quality >= 45)
        if (bitmap !== decoded) decoded.recycle()
        bitmap.recycle()
        return out
    }

    /** Writes a compressed copy into the cache so the Nexconn SDK can upload it as an image message. */
    fun compressToCacheFile(context: Context, uri: Uri): File {
        val file = File(File(context.cacheDir, "chat-images").apply { mkdirs() }, "img_${System.currentTimeMillis()}.jpg")
        file.writeBytes(compress(context, uri))
        return file
    }
}

class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val isOnline: Flow<Boolean> = callbackFlow {
        fun current(): Boolean = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(current())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(current()) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

object Permissions {
    fun granted(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 || granted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun hasLocation(context: Context) =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) || granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
}

object LocationHelper {
    /** Current position without Play services: fresh fix if possible, else the last known one. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!Permissions.hasLocation(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (Build.VERSION.SDK_INT >= 30) {
            for (provider in providers) {
                val fix = withTimeoutOrNull(8000) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                    }
                }
                if (fix != null) return fix
            }
        }
        return (providers + LocationManager.PASSIVE_PROVIDER).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }
}

/** System notifications while the app process is alive (no FCM push: shared Nexconn console). */
object Notifier {
    const val CHANNEL_MESSAGES = "messages"
    const val CHANNEL_ACTIVITY = "activity"
    const val EXTRA_PEER = "open_peer"
    const val EXTRA_ACTIVITY = "open_activity"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, context.getString(R.string.notification_channel_messages), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.notification_channel_messages_desc) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVITY, context.getString(R.string.notification_channel_activity), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notification_channel_activity_desc) },
        )
    }

    @SuppressLint("MissingPermission")
    fun show(context: Context, id: Int, channel: String, title: String, text: String, peerUserId: String?) {
        if (!Permissions.canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (peerUserId != null) putExtra(EXTRA_PEER, peerUserId) else putExtra(EXTRA_ACTIVITY, true)
        }
        val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
