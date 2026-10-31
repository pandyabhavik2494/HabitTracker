package com.pandyabhavik.habittracker.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.pandyabhavik.habittracker.R
import com.pandyabhavik.habittracker.art.CatArt
import com.pandyabhavik.habittracker.core.Cat
import com.pandyabhavik.habittracker.core.Catalog
import com.pandyabhavik.habittracker.data.HomeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Sends a sticker as an image: to WhatsApp, or as a 9:16 card for a story. */
object Share {
    /** Main-thread scope for the share intents; the image itself is drawn and encoded on [Dispatchers.Default]. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** "Perfect Day #N" for the share, or null when there's no number worth showing (never "#0"). */
    private fun dayNumber(s: HomeState, number: Int?): Int? = (number ?: s.perfectDays).takeIf { it > 0 }

    /** [number] is the Perfect Day the sticker came from; without one, the count of Perfect Days is used. */
    fun whatsapp(context: Context, cat: Cat, s: HomeState, number: Int? = null) {
        scope.launch {
            val uri = withContext(Dispatchers.Default) { save(context, card(context, cat, s, 1080, 1080, number), "sticker-${cat.id}.png") }
            sendWhatsapp(context, cat, s, uri, number)
        }
    }

    private fun sendWhatsapp(context: Context, cat: Cat, s: HomeState, uri: Uri, number: Int?) {
        val day = dayNumber(s, number)
        val text = (if (day != null) "Perfect Day #$day 🔥 " else "") +
            "Just pulled ${if (cat.breed == cat.name) "a ${cat.name}" else "${cat.name} the ${cat.breed}"}. ${s.owned.size} of ${Catalog.SIZE} collected."
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("sticker", uri)
        try {
            context.startActivity(Intent(intent).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent.createChooser(intent, "Send sticker").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun story(context: Context, cat: Cat, s: HomeState, number: Int? = null) {
        scope.launch {
            val uri = withContext(Dispatchers.Default) { save(context, card(context, cat, s, 1080, 1920, number), "story-${cat.id}.png") }
            sendStory(context, uri)
        }
    }

    private fun sendStory(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newRawUri("story", uri)
        context.startActivity(Intent.createChooser(intent, "Share to your story").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun save(context: Context, bmp: Bitmap, name: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, name)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }

    /** The share image: the sticker big on a dark glow, its name and breed, and the streak. */
    fun card(context: Context, cat: Cat, s: HomeState, w: Int, h: Int, number: Int? = null): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val bg = Paint()
        bg.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF1B1630.toInt(), 0xFF050608.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val stickerSize = w * 0.62f
        val cx = w / 2f
        val cy = h * if (h > w) 0.40f else 0.42f
        val glow = Paint()
        glow.shader = RadialGradient(cx, cy, stickerSize * 0.75f, 0x66A98BFF, 0x00000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, stickerSize * 0.75f, glow)
        CatArt.get(context, cat.id)?.let { art ->
            c.save()
            c.translate(cx - stickerSize / 2, cy - stickerSize / 2)
            c.rotate(-6f, stickerSize / 2, stickerSize / 2)
            CatArt.draw(c, art, stickerSize, 0.8f)
            c.restore()
        }
        val display = runCatching { ResourcesCompat.getFont(context, R.font.bricolage) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val body = runCatching { ResourcesCompat.getFont(context, R.font.figtree) }.getOrNull() ?: Typeface.DEFAULT
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = display
            fontVariationSettings = "'wght' 800"
            textSize = w * 0.1f
            color = 0xFFF3F4F8.toInt()
            textAlign = Paint.Align.CENTER
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = body
            fontVariationSettings = "'wght' 700"
            textSize = w * 0.035f
            color = 0xFF8D93A6.toInt()
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.18f
        }
        val ny = cy + stickerSize / 2 + w * 0.14f
        c.drawText(cat.name, cx, ny, name)
        if (cat.breed != cat.name) c.drawText(cat.breed.uppercase(), cx, ny + w * 0.06f, small)
        val streak = Paint(small).apply { color = 0xFFFF6B57.toInt(); textSize = w * 0.04f }
        val day = dayNumber(s, number)
        c.drawText(if (day != null) "PERFECT DAY #$day  ·  HABIT TRACKER" else "HABIT TRACKER", cx, ny + w * 0.13f, streak)
        return bmp
    }
}
