package com.ryti.dancebot.core

/** O que o cérebro pede para fazer. */
sealed class Acao {
    class Jogar(val seq: List<Dir>, val botoes: Botoes) : Acao()
    class ToqueMenu(val menu: Menu, val x: Double, val y: Double, val nota: Double) : Acao()
}

class Config {
    @Volatile var menusAutomaticos = true
    var apareceuMs = 200L        // botões visíveis por esse tempo -> ativa
    var sumiuMs = 400L           // botões sumidos por esse tempo -> pausa
    var estavelMs = 400L         // mesma sequência, ícones parados, por esse tempo -> joga
    var limpoMs = 150L           // barra vazia por esse tempo = rodada acabou
    var minSetas = 3
    var intervaloMenuMs = 250L
}

/**
 * Decide o que fazer a cada quadro. Não mexe em tela nem em toque (por isso dá para testar no PC).
 * É a mesma lógica do dance_bot.py.
 */
class Cerebro(val cfg: Config = Config()) {
    var ativo = false
        private set
    var botoes: Botoes? = null
        private set
    val msgs = ArrayList<String>()
    /** Texto curto para o painel. */
    @Volatile var status: String = "Esperando o jogo"

    private var visDesde: Long? = null
    private var sumiuDesde: Long? = null
    private var candidato: List<Dir>? = null
    private var candDesde = 0L
    private var candPos: List<Double>? = null
    private var ultima: List<Dir>? = null
    private var limpoDesde: Long? = null
    private var ultimoMenu = 0L
    private val menuLivre = HashMap<Menu, Long>()

    private fun zerarRodada() {
        candidato = null
        candPos = null
        ultima = null
        limpoDesde = null
    }

    fun passo(f: Frame, agora: Long): Acao? {
        val b = Vision.acharBotoes(f)

        // ---- botões visíveis? (liga / pausa sozinho) ----
        if (b != null) {
            sumiuDesde = null
            botoes = b
            if (visDesde == null) visDesde = agora
            if (!ativo && agora - visDesde!! >= cfg.apareceuMs) {
                ativo = true
                zerarRodada()
                msgs.add("BOTÕES DETECTADOS - ATIVO")
            }
        } else {
            visDesde = null
            if (sumiuDesde == null) sumiuDesde = agora
            if (ativo && agora - sumiuDesde!! >= cfg.sumiuMs) {
                ativo = false
                zerarRodada()
                msgs.add("BOTÕES SUMIRAM - PAUSADO (volta sozinho)")
            }
        }

        // ---- fora da dança: navega os menus (Sair -> Jogar Agora -> Confirmar -> Ir) ----
        if (!ativo && visDesde == null) {
            status = "Fora da dança"
            if (cfg.menusAutomaticos && agora - ultimoMenu >= cfg.intervaloMenuMs) {
                ultimoMenu = agora
                val m = Menus.escolher(Menus.achar(f), menuLivre, agora)
                if (m != null) {
                    menuLivre[m.first] = agora + m.first.esperaMs
                    status = "Menu: ${m.first.nome}"
                    return Acao.ToqueMenu(m.first, m.second.x, m.second.y, m.second.nota)
                }
            }
            return null
        }
        if (!ativo || b == null) {
            status = "Detectando botões..."
            return null
        }

        // ---- lê a barra ----
        val l = Vision.lerBarra(f, b)
        if (l.confiavel && l.seq.size < cfg.minSetas) {
            // barra vazia / quase vazia -> rodada acabou
            candidato = null
            if (limpoDesde == null) limpoDesde = agora
            if (agora - limpoDesde!! >= cfg.limpoMs) ultima = null
            status = "Dança: esperando sequência"
            return null
        }
        limpoDesde = null
        if (!l.confiavel) {
            candidato = null            // leitura duvidosa: espera estabilizar, não arrisca
            return null
        }

        // os ícones também precisam estar PARADOS (não deslizando/aparecendo)
        val tol = 0.03 * b.s
        val cp = candPos
        val mexeu = cp == null || cp.size != l.xs.size || l.xs.indices.any { Math.abs(l.xs[it] - cp[it]) > tol }
        if (l.seq != candidato || mexeu) {
            candidato = l.seq
            candDesde = agora
            candPos = l.xs
            return null
        }
        if (agora - candDesde < cfg.estavelMs) return null
        if (l.seq == ultima || Vision.eSobra(l.seq, ultima)) {
            status = "Dança: jogada feita"
            return null
        }
        status = "Jogando ${l.seq.texto()}"
        return Acao.Jogar(l.seq, b)
    }

    fun jogou(seq: List<Dir>, ok: Boolean) {
        ultima = if (ok) seq else null
        candidato = null
    }
}
