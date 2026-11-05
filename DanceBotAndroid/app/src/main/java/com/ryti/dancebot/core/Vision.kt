package com.ryti.dancebot.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Faixas de cor HSV (OpenCV: H 0..179) de cada seta/botão: {H,S,V mínimos}, {H,S,V máximos}. */
object Cores {
    val faixa: Map<Dir, Pair<IntArray, IntArray>> = mapOf(
        Dir.UP to Pair(intArrayOf(10, 110, 170), intArrayOf(38, 255, 255)),      // amarelo/laranja
        Dir.LEFT to Pair(intArrayOf(40, 90, 90), intArrayOf(85, 255, 255)),      // verde
        Dir.RIGHT to Pair(intArrayOf(95, 90, 110), intArrayOf(125, 255, 255)),   // azul
        Dir.DOWN to Pair(intArrayOf(126, 70, 110), intArrayOf(165, 255, 255)),   // roxo
    )
}

class Pt(val x: Double, val y: Double)

/** Posição dos 4 botões da direita (em pixels do quadro) e o espaçamento entre ESQUERDA e DIREITA. */
class Botoes(val pos: Map<Dir, Pt>, val s: Double) {
    operator fun get(d: Dir): Pt = pos.getValue(d)
}

/** Leitura da barra de sequência. */
class Leitura(val seq: List<Dir>, val xs: List<Double>, val confiavel: Boolean)

object Vision {

    /** Janela do fechamento morfológico: 5 em 1080p, 3 em quadros menores. */
    fun kFecho(h: Int): Int = if (h >= 900) 5 else 3

    private class Cand(val d: Dir, val cx: Double, val cy: Double, val w: Int, val h: Int, val area: Int)

    private fun candidatosBotao(hs: Hsv, d: Dir, W: Int, H: Int, ox: Int, oy: Int): List<Cand> {
        val (lo, hi) = Cores.faixa.getValue(d)
        val mask = Imaging.close(Imaging.inRange(hs, lo, hi), hs.w, hs.h, kFecho(H))
        val aMin = 0.0004 * W * H
        val aMax = 0.006 * W * H
        val res = ArrayList<Cand>()
        for (c in Imaging.componentes(mask, hs.w, hs.h)) {
            if (c.area < aMin || c.area > aMax) continue
            val mx = max(c.w, c.h)
            val mn = min(c.w, c.h)
            if (mx < 0.05 * H || mx > 0.16 * H || mn < 0.025 * H) continue
            res.add(Cand(d, ox + c.cx, oy + c.cy, c.w, c.h, c.area))
        }
        res.sortByDescending { it.area }
        return res.take(4)
    }

    /** Os 4 botões formam o losango: cima em cima, baixo embaixo, esquerda e direita nos lados. */
    private fun geometriaOk(u: Cand, l: Cand, r: Cand, d: Cand, H: Int): Boolean {
        val s = r.cx - l.cx
        if (s < 0.15 * H || s > 0.50 * H) return false
        val meio = (l.cx + r.cx) / 2
        if (abs(u.cx - meio) > 0.25 * s || abs(d.cx - meio) > 0.25 * s) return false
        if (abs(l.cy - r.cy) > 0.15 * s) return false
        val my = (l.cy + r.cy) / 2
        if (my - u.cy < 0.25 * s || my - u.cy > 0.85 * s) return false
        if (d.cy - my < 0.25 * s || d.cy - my > 0.85 * s) return false
        val tam = listOf(u, l, r, d).map { max(it.w, it.h) }
        return tam.max() <= 1.7 * tam.min()
    }

    /** Procura os 4 botões da direita. null = não estão na tela (lobby, menu, carregando...). */
    fun acharBotoes(f: Frame): Botoes? {
        val W = f.w
        val H = f.h
        val ox = (0.40 * W).toInt()
        val oy = (0.30 * H).toInt()
        val hs = Imaging.hsv(f, ox, oy, W, H)
        val cand = HashMap<Dir, List<Cand>>()
        for (d in Dir.values()) {
            val l = candidatosBotao(hs, d, W, H, ox, oy)
            if (l.isEmpty()) return null
            cand[d] = l
        }
        var melhor: List<Cand>? = null
        var melhorArea = -1
        for (u in cand.getValue(Dir.UP)) for (l in cand.getValue(Dir.LEFT))
            for (r in cand.getValue(Dir.RIGHT)) for (d in cand.getValue(Dir.DOWN)) {
                if (geometriaOk(u, l, r, d, H)) {
                    val tot = u.area + l.area + r.area + d.area
                    if (tot > melhorArea) {
                        melhor = listOf(u, l, r, d)
                        melhorArea = tot
                    }
                }
            }
        val m = melhor ?: return null
        val (u, l, r, d) = m
        return Botoes(
            mapOf(Dir.UP to Pt(u.cx, u.cy), Dir.LEFT to Pt(l.cx, l.cy), Dir.RIGHT to Pt(r.cx, r.cy), Dir.DOWN to Pt(d.cx, d.cy)),
            r.cx - l.cx,
        )
    }

    /** Faixa dos ícones da sequência: na altura do botão de baixo, à esquerda dos botões. [x0, y0, x1, y1] */
    fun retanguloBarra(b: Botoes, W: Int, H: Int): IntArray {
        val dy = b[Dir.DOWN].y
        val top = max(0.0, dy - 0.060 * H).toInt()
        val bot = min(H.toDouble(), dy + 0.018 * H).toInt()
        val x0 = (0.18 * W).toInt()
        val x1 = min(W.toDouble(), b[Dir.LEFT].x - 0.40 * b.s).toInt()
        return intArrayOf(x0, top, x1, bot)
    }

    private class Achado(val cx: Double, val d: Dir, val area: Int)

    /** Lê a sequência da barra, da esquerda para a direita. 'confiavel' = espaçamento regular, nenhum ícone faltando. */
    fun lerBarra(f: Frame, b: Botoes): Leitura {
        val r = retanguloBarra(b, f.w, f.h)
        val (x0, y0, x1, y1) = r
        if (x1 - x0 < 50 || y1 - y0 < 20) return Leitura(emptyList(), emptyList(), false)
        val hb = y1 - y0
        val s = b.s
        val aMin = 0.05 * hb * hb
        val aMax = 0.60 * hb * hb
        val hs = Imaging.hsv(f, x0, y0, x1, y1)
        val achados = ArrayList<Achado>()
        for (d in Dir.values()) {
            val (lo, hi) = Cores.faixa.getValue(d)
            val mask = Imaging.close(Imaging.inRange(hs, lo, hi), hs.w, hs.h, kFecho(f.h))
            for (c in Imaging.componentes(mask, hs.w, hs.h)) {
                if (c.area < aMin || c.area > aMax) continue
                val mx = max(c.w, c.h)
                if (mx > 0.85 * hb || mx < 0.20 * hb) continue
                achados.add(Achado(x0 + c.cx, d, c.area))
            }
        }
        achados.sortBy { it.cx }

        // junta blobs de cores diferentes na mesma posição (o losango apagado de dentro do ícone)
        val gap = 0.18 * s
        val finais = ArrayList<Achado>()
        for (a in achados) {
            if (finais.isNotEmpty() && a.cx - finais.last().cx < gap) {
                if (a.area > finais.last().area) finais[finais.size - 1] = a
            } else finais.add(a)
        }

        var confiavel = true
        if (finais.size >= 2) {
            val difs = (1 until finais.size).map { finais[it].cx - finais[it - 1].cx }
            val ord = difs.sorted()
            val med = if (ord.size % 2 == 1) ord[ord.size / 2] else (ord[ord.size / 2 - 1] + ord[ord.size / 2]) / 2
            if (med < 0.28 * s || med > 0.50 * s) confiavel = false
            else if (difs.any { it < 0.8 * med || it > 1.2 * med }) confiavel = false
        } else if (finais.size == 1) confiavel = false
        return Leitura(finais.map { it.d }, finais.map { it.cx }, confiavel)
    }

    /** Quantos dos 4 botões ainda têm a cor visível (checagem rápida durante os toques). */
    fun botoesPresentes(f: Frame, b: Botoes): Int {
        val r = (0.075 * f.h).toInt()
        val minimo = 0.0003 * f.w * f.h
        var cont = 0
        for (d in Dir.values()) {
            val p = b[d]
            val x0 = max(0, (p.x - r).toInt())
            val y0 = max(0, (p.y - r).toInt())
            val x1 = min(f.w, (p.x + r).toInt())
            val y1 = min(f.h, (p.y + r).toInt())
            val hs = Imaging.hsv(f, x0, y0, x1, y1)
            val (lo, hi) = Cores.faixa.getValue(d)
            val m = Imaging.inRange(hs, lo, hi)
            var n = 0
            for (v in m) if (v) n++
            if (n >= minimo) cont++
        }
        return cont
    }

    /** A barra ainda mostra a sequência inteira, ou só o que sobrou depois de i toques? Tolera 1 ícone com cor diferente. */
    fun compativel(atual: List<Dir>, seq: List<Dir>, i: Int): Boolean {
        for (k in 0..i) {
            if (k > seq.size) break
            val esperado = seq.subList(k, seq.size)
            if (atual.size != esperado.size) continue
            var erros = 0
            for (j in atual.indices) if (atual[j] != esperado[j]) erros++
            if (erros <= (if (esperado.size >= 4) 1 else 0)) return true
        }
        return false
    }

    /** true se 'seq' é só um pedaço (restinho) da última sequência jogada. */
    fun eSobra(seq: List<Dir>, ultima: List<Dir>?): Boolean {
        if (ultima == null || seq.size >= ultima.size) return false
        val n = seq.size
        for (i in 0..ultima.size - n) if (ultima.subList(i, i + n) == seq) return true
        return false
    }

    @Suppress("unused")
    private fun arred(x: Double) = x.roundToInt()
}
