package com.longynus.churrasco.invites

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.longynus.churrasco.R
import java.io.File

/**
 * A imagem do convite que vai para o WhatsApp (manual: Aplicações → Convite compartilhado):
 * fundo carvão, o símbolo, o nome do churrasco em Bricolage, data e local. A arte CONVITE-02
 * entra no lugar das barras de grelha quando for gerada.
 */
object InviteCard {
    private const val W = 1080
    private const val H = 1350

    fun render(context: Context, place: String, whenText: String, address: String, host: String): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun color(id: Int) = ContextCompat.getColor(context, id)
        val carvao = 0xFF2A1E19.toInt()
        val cream = 0xFFFFF8F3.toInt()
        val brasa = 0xFFC9471A.toInt()
        val ouro = 0xFFDFA632.toInt()
        c.drawColor(carvao)

        // Grelha: quatro barras em brasa saindo pela direita, como na capa do manual.
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = brasa }
        for (i in 0 until 4) c.drawRoundRect(RectF(620f, 980f + i * 72f, 1140f, 1012f + i * 72f), 16f, 16f, bar)
        c.drawCircle(980f, 900f, 36f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = ouro })

        // Símbolo e palavra no topo.
        ContextCompat.getDrawable(context, R.drawable.brand_symbol_light)?.apply { setBounds(96, 96, 96 + 104, 96 + 128); draw(c) }
        val display = ResourcesCompat.getFont(context, R.font.bricolage_extrabold) ?: Typeface.DEFAULT_BOLD
        val text = ResourcesCompat.getFont(context, R.font.jakarta_semibold) ?: Typeface.DEFAULT
        val body = ResourcesCompat.getFont(context, R.font.jakarta_regular) ?: Typeface.DEFAULT
        c.drawText(context.getString(R.string.brand_wordmark), 232f, 190f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 56f; this.color = cream })

        fun block(value: String, face: Typeface, size: Float, ink: Int, top: Float, width: Int = W - 192, lines: Int = 3): Float {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = ink }
            val layout = StaticLayout.Builder.obtain(value, 0, value.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(lines).setEllipsize(android.text.TextUtils.TruncateAt.END).setLineSpacing(0f, 1.0f).build()
            c.save(); c.translate(96f, top); layout.draw(c); c.restore()
            return top + layout.height
        }
        var y = block(context.getString(R.string.invite_card_overline, host).uppercase(), text, 36f, ouro, 380f)
        y = block(place, display, 128f, cream, y + 24f)
        y = block(whenText, text, 48f, cream, y + 40f, lines = 2)
        if (address.isNotBlank()) block(address, body, 40f, 0xCCFFF8F3.toInt(), y + 16f, lines = 2)
        block(context.getString(R.string.invite_card_footer), text, 36f, cream, H - 180f, width = 520)
        return bmp
    }

    /** Salva a imagem no cache compartilhável e devolve o endereço para o Intent. */
    fun save(context: Context, bitmap: Bitmap): android.net.Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "convite.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    }
}
