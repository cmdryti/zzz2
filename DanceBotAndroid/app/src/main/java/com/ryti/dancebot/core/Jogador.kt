package com.ryti.dancebot.core

/** De onde vêm os quadros (a captura de tela). */
interface Quadros {
    fun ultimo(): Frame?
}

/** Como tocar na tela. Coordenadas no espaço do quadro (a implementação converte para pixels reais). Bloqueia até o toque terminar. */
interface Toque {
    fun tocar(x: Double, y: Double): Boolean
}

class Resultado(val ok: Boolean, val motivo: String)

/**
 * Toca a sequência. Antes de CADA toque confere a tela: botões visíveis e a barra ainda mostrando a sequência
 * (se ela sumiu ou virou outra, para na hora em vez de apertar no vazio).
 */
class Jogador(
    private val quadros: Quadros,
    private val toque: Toque,
    private val delayMs: () -> Long,
    private val parar: () -> Boolean,
) {
    fun jogar(seq: List<Dir>, botoes: Botoes): Resultado {
        var prox = System.currentTimeMillis()
        for ((i, d) in seq.withIndex()) {
            if (parar()) return Resultado(false, "robô pausado")

            var motivo: String? = null
            for (tentativa in 0 until 2) {                  // 2 leituras seguidas ruins para abortar
                val f = quadros.ultimo()
                motivo = if (f == null) null
                else if (Vision.botoesPresentes(f, botoes) < 2) "botões sumiram antes do toque ${i + 1}/${seq.size}"
                else {
                    val l = Vision.lerBarra(f, botoes)
                    if (!l.confiavel || Vision.compativel(l.seq, seq, i)) null
                    else "a barra mudou antes do toque ${i + 1}/${seq.size} (viu: ${if (l.seq.isEmpty()) "vazia" else l.seq.texto()})"
                }
                if (motivo == null) break
                Thread.sleep(40)
            }
            if (motivo != null) return Resultado(false, motivo)

            // mantém o ritmo: um toque a cada 'delay' ms (a conferência acima já conta nesse tempo)
            val espera = prox - System.currentTimeMillis()
            if (espera > 0) Thread.sleep(espera)
            val inicio = System.currentTimeMillis()
            val p = botoes[d]
            toque.tocar(p.x, p.y)
            prox = inicio + delayMs()
        }
        return Resultado(true, "ok")
    }
}
