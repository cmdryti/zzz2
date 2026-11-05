package com.ryti.dancebot

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Tela de preparação: 3 passos (sobreposição, acessibilidade, captura) e pronto. */
class MainActivity : Activity() {

    companion object {
        private const val PEDIDO_CAPTURA = 100
    }

    private lateinit var passo1: TextView
    private lateinit var passo2: TextView
    private lateinit var passo3: TextView
    private lateinit var botaoIniciar: Button

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun titulo(t: String) = TextView(this).apply {
        text = t
        textSize = 22f
        setTextColor(Color.parseColor("#222222"))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun texto(t: String) = TextView(this).apply {
        text = t
        textSize = 14f
        setTextColor(Color.parseColor("#444444"))
        setPadding(0, dp(2), 0, dp(6))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        raiz.addView(titulo("DanceBot 2.0"))
        raiz.addView(texto("Joga a Febre de Dança sozinho, de dentro do emulador. Faça os 3 passos uma vez; depois é só iniciar."))

        passo1 = texto("")
        raiz.addView(passo1)
        raiz.addView(Button(this).apply {
            text = "1) Permitir painel sobre o jogo"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        passo2 = texto("")
        raiz.addView(passo2)
        raiz.addView(Button(this).apply {
            text = "2) Ativar o serviço de Acessibilidade"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        raiz.addView(texto("Na lista, abra \"DanceBot\" e ligue. É ele que faz os toques na tela."))

        passo3 = texto("")
        raiz.addView(passo3)
        botaoIniciar = Button(this).apply {
            text = "3) Iniciar o robô"
            setOnClickListener { iniciar() }
        }
        raiz.addView(botaoIniciar)

        raiz.addView(Button(this).apply {
            text = "Parar o robô"
            setOnClickListener {
                startService(Intent(this@MainActivity, BotService::class.java).setAction(BotService.ACAO_PARAR))
            }
        })
        raiz.addView(texto("Depois de iniciar, abra o jogo. O painel ▶/⏸ fica por cima; use – para virar uma bolinha."))

        val rolagem = ScrollView(this)
        rolagem.addView(raiz)
        setContentView(rolagem)
    }

    override fun onResume() {
        super.onResume()
        val sobreposicao = Settings.canDrawOverlays(this)
        val acessibilidade = DanceAccessibilityService.instancia != null
        passo1.text = if (sobreposicao) "✔ Painel sobre o jogo: permitido" else "✘ Painel sobre o jogo: falta permitir"
        passo2.text = if (acessibilidade) "✔ Acessibilidade: ligada" else "✘ Acessibilidade: desligada"
        passo3.text = if (BotService.rodando) "✔ Robô: rodando" else "• Robô: parado"
        botaoIniciar.isEnabled = sobreposicao && !BotService.rodando
    }

    private fun iniciar() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), PEDIDO_CAPTURA)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PEDIDO_CAPTURA && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, BotService::class.java)
                .putExtra(BotService.EXTRA_CODIGO, resultCode)
                .putExtra(BotService.EXTRA_DADOS, data)
            startForegroundService(i)
            moveTaskToBack(true)       // volta para o jogo
        }
    }
}
