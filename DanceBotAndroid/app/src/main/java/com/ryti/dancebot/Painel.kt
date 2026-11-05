package com.ryti.dancebot

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Painel flutuante por cima do jogo: ▶/⏸, velocidade (− / +), menus automáticos (M), minimizar (–) e fechar (✕).
 * Arraste pela barra "⠿". Minimizado vira uma bolinha: toque nela para abrir de novo.
 */
class Painel(private val ctx: Context, private val cb: Callbacks) {

    interface Callbacks {
        fun alternarJogando(): Boolean          // devolve o novo estado (true = jogando)
        fun mudarDelay(deltaMs: Int): Long      // devolve o novo delay em ms
        fun alternarMenus(): Boolean            // devolve o novo estado (true = menus automáticos ligados)
        fun fechar()
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val main = Handler(Looper.getMainLooper())
    private val dens = ctx.resources.displayMetrics.density

    private fun dp(v: Int) = (v * dens).toInt()

    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(8)
        y = dp(56)
    }

    private lateinit var painel: LinearLayout
    private lateinit var bolha: TextView
    private lateinit var botaoPlay: TextView
    private lateinit var textoDelay: TextView
    private lateinit var botaoMenus: TextView
    private lateinit var textoStatus: TextView
    private var visivelPainel = false
    private var visivelBolha = false
    private var jogando = true

    private fun fundo(cor: Int, raio: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(cor)
            cornerRadius = dp(raio).toFloat()
        }

    private fun botao(texto: String, aoClicar: () -> Unit): TextView =
        TextView(ctx).apply {
            this.text = texto
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            minWidth = dp(40)
            minHeight = dp(36)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = fundo(Color.parseColor("#44FFFFFF"), 8)
            setOnClickListener { aoClicar() }
        }

    private fun arrastavel(gatilho: View, alvo: () -> View, aoToque: (() -> Unit)? = null) {
        var iniX = 0
        var iniY = 0
        var tocX = 0f
        var tocY = 0f
        var moveu = false
        gatilho.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    iniX = lp.x
                    iniY = lp.y
                    tocX = e.rawX
                    tocY = e.rawY
                    moveu = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tocX).toInt()
                    val dy = (e.rawY - tocY).toInt()
                    if (abs(dx) > dp(4) || abs(dy) > dp(4)) moveu = true
                    if (moveu) {
                        lp.x = iniX + dx
                        lp.y = iniY + dy
                        wm.updateViewLayout(alvo(), lp)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moveu) {
                        v.performClick()
                        aoToque?.invoke()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun montar() {
        val alca = TextView(ctx).apply {
            text = "⠿"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
        }
        botaoPlay = botao("⏸") {
            jogando = cb.alternarJogando()
            atualizarPlay()
        }
        val menos = botao("−") { textoDelay.text = formatarDelay(cb.mudarDelay(+10)) }   // + delay = mais devagar
        val mais = botao("+") { textoDelay.text = formatarDelay(cb.mudarDelay(-10)) }    // - delay = mais rápido
        textoDelay = TextView(ctx).apply {
            text = "0.12s"
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            minWidth = dp(44)
        }
        botaoMenus = botao("M") {
            val ligado = cb.alternarMenus()
            atualizarMenus(ligado)
        }
        val minimizar = botao("–") { mostrarBolha() }
        val fechar = botao("✕") { cb.fechar() }

        val linha1 = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(alca)
            addView(botaoPlay)
            addView(space())
            addView(menos)
            addView(textoDelay)
            addView(mais)
            addView(space())
            addView(botaoMenus)
            addView(space())
            addView(minimizar)
            addView(fechar)
        }
        textoStatus = TextView(ctx).apply {
            text = "Iniciando..."
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 11f
            setPadding(dp(8), dp(2), dp(8), 0)
            maxWidth = dp(300)
            maxLines = 2
        }
        painel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(6))
            background = fundo(Color.parseColor("#D91B1B26"), 12)
            addView(linha1)
            addView(textoStatus)
        }
        arrastavel(alca, { painel })

        bolha = TextView(ctx).apply {
            text = "▶"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            minWidth = dp(44)
            minHeight = dp(44)
            background = fundo(Color.parseColor("#D92ECC71"), 22)
        }
        arrastavel(bolha, { bolha }, aoToque = { mostrarPainel() })
        atualizarPlay()
    }

    private fun space(): View = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 1) }

    private fun formatarDelay(ms: Long): String = "%.2fs".format(ms / 1000.0)

    private fun atualizarPlay() {
        botaoPlay.text = if (jogando) "⏸" else "▶"
        botaoPlay.background = fundo(if (jogando) Color.parseColor("#CC2ECC71") else Color.parseColor("#CCF39C12"), 8)
        bolha.text = if (jogando) "▶" else "⏸"
        bolha.background = fundo(if (jogando) Color.parseColor("#D92ECC71") else Color.parseColor("#D9F39C12"), 22)
    }

    private fun atualizarMenus(ligado: Boolean) {
        botaoMenus.background = fundo(if (ligado) Color.parseColor("#CC3498DB") else Color.parseColor("#44FFFFFF"), 8)
    }

    private fun mostrarPainel() {
        if (visivelBolha) {
            wm.removeView(bolha)
            visivelBolha = false
        }
        if (!visivelPainel) {
            wm.addView(painel, lp)
            visivelPainel = true
        }
    }

    private fun mostrarBolha() {
        if (visivelPainel) {
            wm.removeView(painel)
            visivelPainel = false
        }
        if (!visivelBolha) {
            wm.addView(bolha, lp)
            visivelBolha = true
        }
    }

    /** Mostra o painel. Chamar na thread principal. */
    fun mostrar(delayMs: Long, jogandoInicial: Boolean, menusLigados: Boolean) {
        montar()
        jogando = jogandoInicial
        textoDelay.text = formatarDelay(delayMs)
        atualizarPlay()
        atualizarMenus(menusLigados)
        mostrarPainel()
    }

    /** Atualiza o texto de estado (pode ser chamado de qualquer thread). */
    fun status(texto: String) {
        main.post {
            if (::textoStatus.isInitialized) textoStatus.text = texto
        }
    }

    fun remover() {
        main.post {
            try {
                if (visivelPainel) wm.removeView(painel)
                if (visivelBolha) wm.removeView(bolha)
            } catch (_: Exception) {
            }
            visivelPainel = false
            visivelBolha = false
        }
    }
}
