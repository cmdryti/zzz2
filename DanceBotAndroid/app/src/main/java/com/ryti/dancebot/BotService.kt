package com.ryti.dancebot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.ryti.dancebot.core.Acao
import com.ryti.dancebot.core.Cerebro
import com.ryti.dancebot.core.Config
import com.ryti.dancebot.core.Jogador
import com.ryti.dancebot.core.Toque
import com.ryti.dancebot.core.texto

/**
 * Serviço em primeiro plano: mantém a captura de tela, o painel flutuante e o laço do robô rodando
 * mesmo com o jogo minimizado ou com outro app na frente.
 */
class BotService : Service(), Painel.Callbacks {

    companion object {
        const val ACAO_PARAR = "com.ryti.dancebot.PARAR"
        const val EXTRA_CODIGO = "codigo"
        const val EXTRA_DADOS = "dados"
        @Volatile var rodando = false
        private const val CANAL = "dancebot"
        private const val ID_NOTIF = 1
        private const val TAG = "DanceBot"
    }

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var grabber: ScreenGrabber? = null
    private var painel: Painel? = null
    private var thread: Thread? = null
    private val cfg = Config()

    @Volatile private var ativoLaco = false
    @Volatile private var jogando = true
    @Volatile private var delayMs = 120L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            encerrar()
            return START_NOT_STICKY
        }
        if (rodando) return START_NOT_STICKY
        val codigo = intent?.getIntExtra(EXTRA_CODIGO, 0) ?: 0
        @Suppress("DEPRECATION")
        val dados: Intent? = intent?.getParcelableExtra(EXTRA_DADOS)
        if (dados == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        prefs = getSharedPreferences("dancebot", Context.MODE_PRIVATE)
        delayMs = prefs.getLong("delay", 120L).coerceIn(20L, 800L)
        cfg.menusAutomaticos = prefs.getBoolean("menus", true)

        // 1) notificação de primeiro plano (obrigatória antes de capturar a tela)
        val notif = criarNotificacao()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID_NOTIF, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(ID_NOTIF, notif)
        }

        // 2) captura de tela
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projecao = try {
            mpm.getMediaProjection(codigo, dados)
        } catch (e: Exception) {
            Log.e(TAG, "sem permissão de captura", e)
            null
        }
        if (projecao == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        val g = ScreenGrabber(this, projecao) { main.post { encerrar() } }
        g.iniciar()
        grabber = g

        // 3) painel flutuante
        val p = Painel(this, this)
        painel = p
        p.mostrar(delayMs, jogando, cfg.menusAutomaticos)

        // 4) laço do robô
        rodando = true
        ativoLaco = true
        thread = Thread({ laco(g, p) }, "DanceBot-robo").also { it.start() }
        return START_NOT_STICKY
    }

    private fun laco(g: ScreenGrabber, p: Painel) {
        val cerebro = Cerebro(cfg)
        val toque = object : Toque {
            override fun tocar(x: Double, y: Double): Boolean {
                val acc = DanceAccessibilityService.instancia ?: return false
                return acc.tocar((x * g.escalaX).toFloat(), (y * g.escalaY).toFloat())
            }
        }
        val jogador = Jogador(g, toque, { delayMs }, { !jogando || !ativoLaco })
        var ultimoTexto = ""
        var ultimaVerificacao = 0L

        while (ativoLaco) {
            val agora = SystemClock.elapsedRealtime()
            if (agora - ultimaVerificacao > 1000) {
                ultimaVerificacao = agora
                try {
                    g.verificarTamanho()
                } catch (e: Exception) {
                    Log.w(TAG, "verificarTamanho", e)
                }
            }
            val f = g.ultimo()
            if (!jogando) {
                mostrarStatus(p, "Pausado", ultimoTexto).also { ultimoTexto = it }
                sleep(100)
                continue
            }
            if (f == null) {
                mostrarStatus(p, "Aguardando imagem da tela...", ultimoTexto).also { ultimoTexto = it }
                sleep(150)
                continue
            }
            if (DanceAccessibilityService.instancia == null) {
                mostrarStatus(p, "Ative o serviço de Acessibilidade do DanceBot", ultimoTexto).also { ultimoTexto = it }
                sleep(300)
                continue
            }

            try {
                val acao = cerebro.passo(f, agora)
                for (m in cerebro.msgs) Log.i(TAG, m)
                cerebro.msgs.clear()
                when (acao) {
                    is Acao.Jogar -> {
                        mostrarStatus(p, "Jogando ${acao.seq.texto()}", ultimoTexto).also { ultimoTexto = it }
                        val r = jogador.jogar(acao.seq, acao.botoes)
                        cerebro.jogou(acao.seq, r.ok)
                        Log.i(TAG, if (r.ok) "JOGOU ${acao.seq.texto()}" else "INTERROMPIDO ${acao.seq.texto()} -> ${r.motivo}")
                        mostrarStatus(p, if (r.ok) "Jogada feita" else "Interrompido: ${r.motivo}", ultimoTexto).also { ultimoTexto = it }
                    }
                    is Acao.ToqueMenu -> {
                        mostrarStatus(p, "Menu: ${acao.menu.nome}", ultimoTexto).also { ultimoTexto = it }
                        Log.i(TAG, "MENU ${acao.menu.nome} (nota ${"%.2f".format(acao.nota)})")
                        toque.tocar(acao.x, acao.y)
                    }
                    null -> mostrarStatus(p, cerebro.status, ultimoTexto).also { ultimoTexto = it }
                }
            } catch (e: Exception) {
                Log.e(TAG, "erro no laço", e)
                sleep(200)
            }
            sleep(if (cerebro.ativo) 30 else 120)
        }
    }

    private fun mostrarStatus(p: Painel, texto: String, anterior: String): String {
        if (texto != anterior) p.status(texto)
        return texto
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    // ---- botões do painel ----
    override fun alternarJogando(): Boolean {
        jogando = !jogando
        return jogando
    }

    override fun mudarDelay(deltaMs: Int): Long {
        delayMs = (delayMs + deltaMs).coerceIn(20L, 800L)
        prefs.edit().putLong("delay", delayMs).apply()
        return delayMs
    }

    override fun alternarMenus(): Boolean {
        cfg.menusAutomaticos = !cfg.menusAutomaticos
        prefs.edit().putBoolean("menus", cfg.menusAutomaticos).apply()
        return cfg.menusAutomaticos
    }

    override fun fechar() {
        encerrar()
    }

    // ---- ciclo de vida ----
    private fun encerrar() {
        ativoLaco = false
        rodando = false
        thread?.interrupt()
        painel?.remover()
        grabber?.parar()
        grabber = null
        painel = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        if (rodando) {
            ativoLaco = false
            rodando = false
            painel?.remover()
            grabber?.parar()
        }
        super.onDestroy()
    }

    private fun criarNotificacao(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CANAL, "DanceBot", NotificationManager.IMPORTANCE_LOW))
        }
        val parar = PendingIntent.getService(
            this, 0, Intent(this, BotService::class.java).setAction(ACAO_PARAR),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val abrir = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CANAL)
            .setContentTitle("DanceBot rodando")
            .setContentText("Painel flutuante ativo. Toque para abrir.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(abrir)
            .addAction(Notification.Action.Builder(null, "Parar", parar).build())
            .setOngoing(true)
            .build()
    }
}
