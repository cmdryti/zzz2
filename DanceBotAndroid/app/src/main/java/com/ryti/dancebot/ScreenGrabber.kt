package com.ryti.dancebot

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import com.ryti.dancebot.core.Frame
import com.ryti.dancebot.core.Quadros
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Captura a tela do emulador (MediaProjection) em tamanho reduzido (~540 px de altura) e entrega o
 * último quadro. Se a tela girar / mudar de tamanho, remonta a captura sozinho.
 */
class ScreenGrabber(
    private val ctx: Context,
    private val projection: MediaProjection,
    private val aoParar: () -> Unit,
) : Quadros {

    companion object {
        private const val ALTURA_CAPTURA = 540
        private const val INTERVALO_MS = 40L       // no máx. ~25 quadros/s processados
    }

    private val thread = HandlerThread("DanceBot-captura").also { it.start() }
    private val handler = Handler(thread.looper)
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var ultimoMs = 0L
    private var linha = ByteArray(0)

    @Volatile private var quadro: Frame? = null
    @Volatile var realW = 0; private set
    @Volatile var realH = 0; private set
    @Volatile var capW = 1; private set
    @Volatile var capH = 1; private set

    val escalaX: Double get() = realW.toDouble() / capW
    val escalaY: Double get() = realH.toDouble() / capH

    override fun ultimo(): Frame? = quadro

    fun iniciar() {
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                aoParar()
            }
        }, handler)
        montar()
    }

    @Suppress("DEPRECATION")
    private fun tamanhoReal(): Pair<Int, Int> {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(dm)
        return Pair(dm.widthPixels, dm.heightPixels)
    }

    @Synchronized
    private fun montar() {
        val (rw, rh) = tamanhoReal()
        realW = rw
        realH = rh
        val esc = ALTURA_CAPTURA.toDouble() / min(rw, rh)
        capW = max(2, (rw * esc / 2).roundToInt() * 2)
        capH = max(2, (rh * esc / 2).roundToInt() * 2)
        val leitor = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        leitor.setOnImageAvailableListener({ r -> converter(r) }, handler)
        reader = leitor
        display = projection.createVirtualDisplay(
            "DanceBot", capW, capH, ctx.resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, leitor.surface, null, handler,
        )
    }

    @Synchronized
    private fun desmontar() {
        try {
            reader?.setOnImageAvailableListener(null, null)
            display?.release()
            reader?.close()
        } catch (_: Exception) {
        }
        display = null
        reader = null
    }

    /** Chame de vez em quando: se a tela girou ou mudou de tamanho, refaz a captura. */
    fun verificarTamanho() {
        val (w, h) = tamanhoReal()
        if (w != realW || h != realH) {
            desmontar()
            quadro = null
            montar()
        }
    }

    private fun converter(r: ImageReader) {
        val img = try {
            r.acquireLatestImage()
        } catch (_: Exception) {
            null
        } ?: return
        try {
            val agora = SystemClock.elapsedRealtime()
            if (agora - ultimoMs < INTERVALO_MS) return
            ultimoMs = agora
            val plano = img.planes[0]
            val buf = plano.buffer
            val stride = plano.rowStride
            val passo = plano.pixelStride
            val w = img.width
            val h = img.height
            if (linha.size < stride) linha = ByteArray(stride)
            val px = IntArray(w * h)
            var o = 0
            for (y in 0 until h) {
                val ini = y * stride
                val n = min(stride, buf.limit() - ini)
                if (n <= 0) break
                buf.position(ini)
                buf.get(linha, 0, n)
                var p = 0
                for (x in 0 until w) {
                    if (p + 2 >= n) break
                    val rr = linha[p].toInt() and 255
                    val gg = linha[p + 1].toInt() and 255
                    val bb = linha[p + 2].toInt() and 255
                    px[o++] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
                    p += passo
                }
            }
            quadro = Frame(w, h, px)
        } catch (_: Exception) {
            // quadro ruim: ignora e espera o próximo
        } finally {
            img.close()
        }
    }

    fun parar() {
        desmontar()
        try {
            projection.stop()
        } catch (_: Exception) {
        }
        thread.quitSafely()
    }
}
