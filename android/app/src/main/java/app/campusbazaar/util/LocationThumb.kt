package app.campusbazaar.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Nexconn location messages carry a small preview image (base64 JPEG). We render a simple,
 * offline "pin card" in the app palette (no map SDK or API key needed) and keep it a few KB so it
 * fits comfortably inside the message size limit.
 */
object LocationThumb {
    private const val W = 240
    private const val H = 140

    fun render(lat: Double, lng: Double, label: String): String {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF3F4F7.toInt())
        val grid = Paint().apply { color = 0xFFDCDFE6.toInt(); strokeWidth = 1f }
        for (x in 0..W step 24) c.drawLine(x.toFloat(), 0f, x.toFloat(), H.toFloat(), grid)
        for (y in 0..H step 24) c.drawLine(0f, y.toFloat(), W.toFloat(), y.toFloat(), grid)
        val road = Paint().apply { color = 0xFFFFFFFF.toInt(); strokeWidth = 10f }
        c.drawLine(0f, 92f, W.toFloat(), 64f, road)
        c.drawLine(150f, 0f, 120f, H.toFloat(), road)

        val cx = W / 2f
        val pin = Path().apply {
            addCircle(cx, 48f, 16f, Path.Direction.CW)
            moveTo(cx - 12f, 58f); lineTo(cx, 82f); lineTo(cx + 12f, 58f); close()
        }
        c.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF5B800.toInt() })
        c.drawPath(pin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16213E.toInt(); style = Paint.Style.STROKE; strokeWidth = 2.5f })
        c.drawCircle(cx, 48f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16213E.toInt() })

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF16213E.toInt(); textSize = 13f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        c.drawText(label.take(30), cx, 108f, text)
        text.apply { typeface = Typeface.DEFAULT; textSize = 11f; color = 0xFF545C70.toInt() }
        c.drawText(String.format(Locale.US, "%.5f, %.5f", lat, lng), cx, 126f, text)

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
