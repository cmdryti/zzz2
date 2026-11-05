package com.ryti.dancebot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Serviço de Acessibilidade: é ele que "encosta o dedo" na tela (toques por gesto).
 * Não lê nem guarda nada da tela; só executa os toques que o robô pede.
 */
class DanceAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instancia: DanceAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instancia = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    /** Toca em (x, y) em pixels reais da tela e espera o toque terminar. Não chamar da thread principal. */
    fun tocar(x: Float, y: Float, duracaoMs: Long = 30): Boolean {
        val caminho = Path().apply { moveTo(x, y) }
        val gesto = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(caminho, 0, duracaoMs))
            .build()
        val trava = CountDownLatch(1)
        var ok = false
        val aceito = dispatchGesture(gesto, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                ok = true
                trava.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                trava.countDown()
            }
        }, null)
        if (!aceito) return false
        trava.await(600, TimeUnit.MILLISECONDS)
        return ok
    }
}
