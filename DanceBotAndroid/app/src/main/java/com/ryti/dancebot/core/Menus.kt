package com.ryti.dancebot.core

import java.util.Base64
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Menu(val nome: String, val esperaMs: Long, val limiar: Double) {
    SAIR("Sair", 4000, 0.80),
    JOGAR("Jogar Agora", 8000, 0.78),
    CONF("Confirmar", 4000, 0.85),
    IR("Ir", 4000, 0.85),
}

class MenuNota(val nota: Double, val x: Double, val y: Double)

/**
 * Botões de menu fora da dança:
 *  - Sair / Confirmar / Ir: retângulos dourados cheios (achados pela cor e forma);
 *  - Jogar Agora: botão só com borda, achado por comparação com um recorte (correlação normalizada).
 */
object Menus {
    private const val LIMIAR_JANELA = 0.85

    private val DOURADO = Pair(intArrayOf(14, 110, 150), intArrayOf(34, 255, 255))
    private val VERDE = Pair(intArrayOf(35, 40, 110), intArrayOf(80, 200, 255))

    // Recorte (cinza) do botão "Jogar Agora" numa tela de 540 px de altura: largura, altura, ponto do toque.
    private const val TPL_W = 90
    private const val TPL_H = 28
    private const val TPL_OX = 44.5
    private const val TPL_OY = 14.0
    private const val TPL_B64 = "v3pzbWpqaWpqaWlpaWlpaWlpaWpqampqa2tra2tra2trampqampqampqampqampqampqaWlpamlpaWppaWlpaWpqampqampqaWlqamppaWloaGhoaGhoaG3EolZRTk1NTU1NTU1NTU1NTExMTU5OTk5PT09PT09PT09PT09OTk5OTk5OTk5OTk5OTk5OTk1OTk5NTk5OTk5OTk5OTk5OTk1NTk1NTU1NTUxMTExMTExMTUy3mlJQTk5OTk5OTk5OTk5OTk5OT09PUFBQUFBRUVFRUVFRUVFRUVBQUFBQUE9PT09PT09PTk5PT09PT09PT09PT09PT09OTk5OTk5OTk5OTU1NTU1NTU1NTU61k1FQT09PT09PUE9PT09OTk9PUFBPUVFRUVFRUVFRUFBQUFBQT09QUE9PT09PT09QT09PTk5PUE9PT09PT09PT09PT09PT09PT05PTk5OT05OTk5OTk5OTk64jlFQUFBQUFBQUFBQUE9PT09PT09PUE9PUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQT09QUE9PT09PUFBQUFBQUFBPT09PT09QT09PT09PT09OTk5OTk68jFJRUFBQUFBQUVFRUVBQUFBQUFBQUVFQUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFRUVFQUFBRUVFRUVFRUVFRUVFRUVFRUVFRUVFQUFBQUFBQUFBQUFBQUFC9jFNSUlFRTkpJTFBSUVFRUVBRUVFRUVFRUlJSUlJSUlJSUlJSUlJSUlJSUVFSUlJST0tKTFBRUlJSUlJSUlJRUVJSUlJSUlJSUVFRUVFRUVFRUVBQUFBRUVG9kVVUU1NQSD08RE5TU1NSUlJSUlJSUlJTU1NTU1NTU1NTU1NTU1NTU1NTU1NTU1NRSkA8QkxSU1NTU1NTU1NTU1NTU1NTU1NTU1JSUlJSUlJSUlJSUlJTVFTDkVVVVFRPQ47LPUxTU1NSUlJSU1NTU1JTU1JSU1NTU1JTVFRTU1JSUlJTU1NTVFRPQ3jlUkZQVFRUVFNTU1NSUlNTU1NSU1RUU1JSUlJSU1NSUlFSUlRVVljRjlZVVVVQQo7LPEtRTkxLTE9SU1JPTEtLS0tMUVNQTUtLTlFSTktLS0tNUVRVVVRMPbrhlUBOVFVTUExLTExLTVFSUE1LS09SUU1KSkpKTFBQTEpLTVFVWl/kj1dXV1dSQo7LPElJQj08PkVNUExDPTw8PTxAS01GPzw9QUpMRDw7PDxBTFVXV1RIUeCB1z9KVFRNRD48PT08QUtNRT48PEFJS0M8Ozs8QEZFPjs8QUlSWF3mj1lZWVhSQ47LO0M/kNThu11BR0BsxN+/WtF2Q0NWtNzXlkBEO+VwwOBySFVYV1FCkao323NFUU5CbMTfv1rRdkJBXLrg1ZJAQjvlcMDgcDxVtNzXlkBLVVndkVpaWllTRI7LOTun0XRgnuBaPGTjkV57zt91QEHRmVtz3Is+OeXWg2JHTVZZV0w+0mcxm7g+S0Zm45Jee87fdT1Y36Bhc9GqOjjl1oNiQz7RmVtz3ItEUlnSk1xbXFpURY7LOF3lWz9COrG1NbinO0I+bOV1QD1ALy8vm8A6OOWgO0VNVVhaVUdq1jQyVeVTRD64pzxEQG3ldDm0szxEQFvlXjbloDtFRjxALy8vm8A/UFjHlV1bWVdTRI7LNobUOktPQ33eM9t6Q09JQOV1QDxwxOHZ08s6OOV6RFNYWVpZUUCrxoqLit2WPTrce0VUTULldTjefkhXUDzUhjXlekRRTT1wxOHZ08s+T1nAmFxWTUxNQo/KN4bUO0xQQ33eM9t7RFBJQOV1PmLlkV1grcs6OOV1SFdcXFxYTEnhs7GxsL/XPjnce0ZWUEPldTvef01gWD/UhzfldkpWS2TlkV1grcs+UFq8m1pLU1c/O6K8OmDlXEBEO7K1NrinO0Q/bOV1PYrSMjQznss6OOV2SVhdXFxWRYTGNz9BPkPhczm4pz1IRG7ld0C3tERSTF7lYTrld09aS4vSMjQynss+UFq+nVlHpsVkcd2IQT6q0XRhn+BbPWTjkl56zuV1PmnkhF2K3cs6OOV2SVldXVxRP8aKQ1BUTkCuuTlk45JffM7leEhg36JldtGsQj3leFJgUWzkhF2K3cs/UVvKn15PSqzc2JdDTUxCktThvF9FSj5oxODBW+VzREGF0eCzics8O+V3S1pfX1tOX+VSTVteWUpw5VM6aMTgwVvldU1MYbvh1ZRITUHleVJhWUWF0eCzictAU13coWNbTkM/P0ZQWllQRj8+QkpSTj4yMDIxRuVkS09HQD4/Pj9FRT9EUl1gYF1TRD9IVl9gXlVGPj88MzE0MkblZlNbUUZCQ0pWWUxCR1ZhX1RIQT4+PkBKWGPnpWViXVdUVFhdYGBcWFRTVVlZS3inNjMymdhEUltYVVNTU1RWVlRWW19hYWBbVVRYXmBhYV5ZUkh4pzc0M5nYR1hkYVxZWV1iYVtXWF5hYV5ZVVNTUlNYX2ndrWdkY2BgYGBhYWFhYF9fX19cTlnT1LTK3GxJWWBgYF9gYGBgYGBgYWFhYWFhYGBhYWJiY2NkYFJc1NS0ytxuTV9nZ2dmZWVmZWNiYmJiYWFgYF9fXl9hZmTPsWllZGJiYmJiYmJiYWFhYWFfWEpFc4t+TEhWX2FhYWJiYmJiYWJiYmJiYmJiYmJjY2RkZWZnZ2BQSHWLf09MXGZpaGhoZ2dmZWVkY2NiYmJhYWFgYGFkY2LItG5oZWRkY2NjY2JiYmJiYWFhX1pSTEpMUVlgYmJiYmJiYmJiYmNjY2NjZGRkZWVmZmdoaWlqamdhWFFOUFhgaGpra2pqaWloZ2ZlZGNjYmJiYmJiY2RjYmLCtnZvaWhoaWhnZmVlZGRjY2NjY2JgXl1eYGJjY2NjY2NjZGRlZGVlZmdoaGhoaGhoaGhoaWlpaWloZ2VkZmlrbW1tbW1sbGtqamhoZmVmZWVlZmZnZmRjYmPDtnl2bWprbGxtbW1ramhnZmVlZWRkZGRkZGRkZGRlZWVmZmZoaWpqa2loaGdnZ2dnZ2doaGlpamprbWxtbm5ubm9wcHFxcHBwb29ubm5ub29ubGpoZWRkZGPJvXl4cm5sbGxtbm5vcHBwcG9ta2loZ2dnZmZmZWVmZ2doaGpsbWxramlpaWhoaGhoaGhpaWprbGxtbm9ub3BwcXN0dnh6e319fHx6d3NwbmtqaGZlZGRkZGjS7+vr6ufh397e3d7e3t/g4uPl6Onp6Obl5OPh39za2tnZ2tvZ2NfX1tbW1tfX2NnZ29ze4OHj5OXm5+jp6+zu8PLy8vLx8fDt6OTf29bSz8vHwsC9uLe8xM3J"
    private val tpl: IntArray by lazy {
        val b = Base64.getDecoder().decode(TPL_B64)
        IntArray(b.size) { b[it].toInt() and 255 }
    }

    private class Ret(val cx: Double, val cy: Double, val w: Int, val h: Int)

    private fun retangulos(hs: Hsv, faixa: Pair<IntArray, IntArray>, H: Int): List<Ret> {
        val k = if (H >= 900) 9 else 5
        val mask = Imaging.close(Imaging.inRange(hs, faixa.first, faixa.second), hs.w, hs.h, k)
        val res = ArrayList<Ret>()
        for (c in Imaging.componentes(mask, hs.w, hs.h)) {
            val razao = c.w.toDouble() / c.h
            if (c.w > 0.15 * H && c.h > 0.035 * H && c.h < 0.12 * H && razao > 2.5 && razao < 7.0 &&
                c.area.toDouble() / (c.w * c.h) >= 0.6
            ) res.add(Ret(c.cx, c.cy, c.w, c.h))
        }
        return res
    }

    private fun retangulosRegiao(f: Frame, x0: Int, y0: Int, x1: Int, y1: Int, faixa: Pair<IntArray, IntArray>): List<Ret> {
        val hs = Imaging.hsv(f, x0, y0, x1, y1)
        return retangulos(hs, faixa, f.h).map { Ret(it.cx + x0, it.cy + y0, it.w, it.h) }
    }

    private fun porCor(f: Frame): Map<Menu, MenuNota> {
        val W = f.w
        val H = f.h
        val res = HashMap<Menu, MenuNota>()
        // só olha onde cada botão pode aparecer (bem mais leve que varrer a tela inteira)
        val ax0 = (0.20 * W).toInt(); val ay0 = (0.50 * H).toInt(); val ax1 = (0.85 * W).toInt(); val ay1 = (0.90 * H).toInt()
        val ouroCentro = retangulosRegiao(f, ax0, ay0, ax1, ay1, DOURADO)
        if (ouroCentro.isNotEmpty()) {
            val verde = retangulosRegiao(f, ax0, ay0, ax1, ay1, VERDE)
            for (r in ouroCentro) {
                if (r.cy > 0.50 * H && r.cy < 0.88 * H && r.cx > 0.3 * W && r.cx < 0.8 * W) {
                    // Confirmar tem o Cancelar (verde) ao lado esquerdo, na mesma altura; Ir fica sozinho no meio
                    val irmao = verde.any { it.cx < r.cx - 0.5 * r.w && abs(it.cy - r.cy) < 0.5 * r.h }
                    if (irmao) res[Menu.CONF] = MenuNota(1.0, r.cx, r.cy)
                    else if (r.cx > 0.4 * W && r.cx < 0.6 * W) res[Menu.IR] = MenuNota(1.0, r.cx, r.cy)
                }
            }
        }
        val bx0 = (0.55 * W).toInt(); val by0 = (0.80 * H).toInt()
        for (r in retangulosRegiao(f, bx0, by0, W, H, DOURADO)) {
            if (r.cx > 0.65 * W && r.cy > 0.85 * H && r.w > 0.30 * H) res[Menu.SAIR] = MenuNota(1.0, r.cx, r.cy)
        }
        return res
    }

    /** Jogar Agora: procura o recorte na região esquerda/central (0..40% da largura, 45..78% da altura). */
    private fun jogarAgora(f: Frame): MenuNota {
        val W = f.w
        val H = f.h
        val x1 = (0.40 * W).toInt()
        val y0 = (0.45 * H).toInt()
        val y1 = (0.78 * H).toInt()
        val regW = x1
        val regH = y1 - y0
        if (regW < 20 || regH < 20) return MenuNota(0.0, 0.0, 0.0)
        // trabalha em meia resolução da referência (270 px de altura) para ser leve
        val ref = 270.0 / H
        val dw = max(8, (regW * ref).toInt())
        val dh = max(8, (regH * ref).toInt())
        val reg = Imaging.redimensionar(Imaging.cinza(f, 0, y0, x1, y1), regW, regH, dw, dh)
        val t0 = Imaging.redimensionar(tpl, TPL_W, TPL_H, TPL_W / 2, TPL_H / 2)
        var melhor = MenuNota(0.0, 0.0, 0.0)
        for (esc in doubleArrayOf(1.0, 0.94, 1.06)) {
            val tw = max(4, (TPL_W / 2 * esc).toInt())
            val th = max(4, (TPL_H / 2 * esc).toInt())
            val t = if (esc == 1.0) t0 else Imaging.redimensionar(t0, TPL_W / 2, TPL_H / 2, tw, th)
            val (nota, px, py) = Imaging.casar(reg, dw, dh, t, tw, th)
            if (nota > melhor.nota) {
                val cx = (px + TPL_OX / 2 * esc) / ref
                val cy = y0 + (py + TPL_OY / 2 * esc) / ref
                melhor = MenuNota(nota, cx, cy)
            }
            if (melhor.nota >= 0.90) break      // já achou com folga: não precisa testar outras escalas
        }
        return melhor
    }

    /** Nota e posição de cada botão de menu na tela. */
    fun achar(f: Frame): Map<Menu, MenuNota> {
        val res = HashMap<Menu, MenuNota>()
        for (m in Menu.values()) res[m] = MenuNota(0.0, 0.0, 0.0)
        val cor = porCor(f)
        for ((m, n) in cor) res[m] = n
        // com uma janela aberta (Confirmar/Ir) o Jogar Agora é só o fundo borrado: nem procura
        if (!cor.containsKey(Menu.CONF) && !cor.containsKey(Menu.IR)) res[Menu.JOGAR] = jogarAgora(f)
        return res
    }

    /**
     * Escolhe qual botão tocar. Janelas (Confirmar/Ir) têm prioridade: o fundo borrado atrás delas ainda
     * parece o lobby, então Jogar Agora/Sair são ignorados enquanto uma janela estiver aberta.
     * 'livre' = instante (ms) em que cada botão pode ser tocado de novo.
     */
    fun escolher(notas: Map<Menu, MenuNota>, livre: Map<Menu, Long>, agora: Long): Pair<Menu, MenuNota>? {
        val janela = max(notas.getValue(Menu.CONF).nota, notas.getValue(Menu.IR).nota) >= LIMIAR_JANELA
        var melhor: Pair<Menu, MenuNota>? = null
        for ((m, n) in notas) {
            if (n.nota < m.limiar) continue
            if (janela && (m == Menu.JOGAR || m == Menu.SAIR)) continue
            if (agora < (livre[m] ?: 0L)) continue
            if (melhor == null || n.nota > melhor.second.nota) melhor = Pair(m, n)
        }
        return melhor
    }

    @Suppress("unused")
    private fun naoUsado() = min(1, 2)
}
