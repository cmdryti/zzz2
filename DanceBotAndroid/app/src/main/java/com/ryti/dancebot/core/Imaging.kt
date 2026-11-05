package com.ryti.dancebot.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** HSV no padrão do OpenCV (H 0..179, S 0..255, V 0..255) de um retângulo do quadro. */
class Hsv(val w: Int, val h: Int, val hh: ByteArray, val ss: ByteArray, val vv: ByteArray)

class Comp(val x: Int, val y: Int, val w: Int, val h: Int, val area: Int) {
    val cx: Double get() = x + w / 2.0
    val cy: Double get() = y + h / 2.0
}

object Imaging {

    fun hsv(f: Frame, x0: Int, y0: Int, x1: Int, y1: Int): Hsv {
        val w = max(0, x1 - x0)
        val h = max(0, y1 - y0)
        val hArr = ByteArray(w * h)
        val sArr = ByteArray(w * h)
        val vArr = ByteArray(w * h)
        var i = 0
        for (y in y0 until y0 + h) {
            var p = y * f.w + x0
            for (x in 0 until w) {
                val c = f.px[p++]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                val d = mx - mn
                vArr[i] = mx.toByte()
                if (mx == 0 || d == 0) {
                    sArr[i] = 0
                    hArr[i] = 0
                } else {
                    sArr[i] = ((255 * d + mx / 2) / mx).toByte()
                    var hd = when (mx) {
                        r -> 60f * (g - b) / d
                        g -> 120f + 60f * (b - r) / d
                        else -> 240f + 60f * (r - g) / d
                    }
                    if (hd < 0f) hd += 360f
                    hArr[i] = (((hd / 2f) + 0.5f).toInt() % 180).toByte()
                }
                i++
            }
        }
        return Hsv(w, h, hArr, sArr, vArr)
    }

    fun inRange(hs: Hsv, lo: IntArray, hi: IntArray): BooleanArray {
        val n = hs.w * hs.h
        val out = BooleanArray(n)
        for (i in 0 until n) {
            val h = hs.hh[i].toInt() and 255
            val s = hs.ss[i].toInt() and 255
            val v = hs.vv[i].toInt() and 255
            out[i] = h >= lo[0] && h <= hi[0] && s >= lo[1] && s <= hi[1] && v >= lo[2] && v <= hi[2]
        }
        return out
    }

    /** Fechamento morfológico (dilata e depois erode) com janela quadrada k x k. */
    fun close(m: BooleanArray, w: Int, h: Int, k: Int): BooleanArray {
        val r = k / 2
        val d = pass(pass(m, w, h, r, false, true), w, h, r, false, false)
        return pass(pass(d, w, h, r, true, true), w, h, r, true, false)
    }

    /** Passada separável: horizontal (horiz=true) ou vertical; erode = "todos" na janela, senão "algum". */
    private fun pass(src: BooleanArray, w: Int, h: Int, r: Int, erode: Boolean, horiz: Boolean): BooleanArray {
        val out = BooleanArray(w * h)
        if (horiz) {
            val pre = IntArray(w + 1)
            for (y in 0 until h) {
                val o = y * w
                for (x in 0 until w) pre[x + 1] = pre[x] + (if (src[o + x]) 1 else 0)
                for (x in 0 until w) {
                    val a = max(0, x - r)
                    val b = min(w - 1, x + r)
                    val c = pre[b + 1] - pre[a]
                    out[o + x] = if (erode) c == (b - a + 1) else c > 0
                }
            }
        } else {
            val pre = IntArray(h + 1)
            for (x in 0 until w) {
                for (y in 0 until h) pre[y + 1] = pre[y] + (if (src[y * w + x]) 1 else 0)
                for (y in 0 until h) {
                    val a = max(0, y - r)
                    val b = min(h - 1, y + r)
                    val c = pre[b + 1] - pre[a]
                    out[y * w + x] = if (erode) c == (b - a + 1) else c > 0
                }
            }
        }
        return out
    }

    /** Componentes conectados (8 vizinhos) com caixa e área em pixels. */
    fun componentes(m: BooleanArray, w: Int, h: Int): List<Comp> {
        val vis = BooleanArray(w * h)
        var stack = IntArray(2048)
        val res = ArrayList<Comp>()
        for (start in 0 until w * h) {
            if (!m[start] || vis[start]) continue
            var sp = 0
            stack[sp++] = start
            vis[start] = true
            var minX = Int.MAX_VALUE
            var maxX = -1
            var minY = Int.MAX_VALUE
            var maxY = -1
            var area = 0
            while (sp > 0) {
                val p = stack[--sp]
                val py = p / w
                val px = p - py * w
                area++
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                for (dy in -1..1) {
                    val ny = py + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = px + dx
                        if (nx < 0 || nx >= w) continue
                        val q = ny * w + nx
                        if (m[q] && !vis[q]) {
                            vis[q] = true
                            if (sp == stack.size) stack = stack.copyOf(stack.size * 2)
                            stack[sp++] = q
                        }
                    }
                }
            }
            res.add(Comp(minX, minY, maxX - minX + 1, maxY - minY + 1, area))
        }
        return res
    }

    /** Tons de cinza (0.299 R + 0.587 G + 0.114 B) de um retângulo. */
    fun cinza(f: Frame, x0: Int, y0: Int, x1: Int, y1: Int): IntArray {
        val w = max(0, x1 - x0)
        val h = max(0, y1 - y0)
        val out = IntArray(w * h)
        var i = 0
        for (y in y0 until y0 + h) {
            var p = y * f.w + x0
            for (x in 0 until w) {
                val c = f.px[p++]
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                out[i++] = (r * 299 + g * 587 + b * 114 + 500) / 1000
            }
        }
        return out
    }

    /** Redimensiona uma imagem em cinza (bilinear). */
    fun redimensionar(src: IntArray, sw: Int, sh: Int, dw: Int, dh: Int): IntArray {
        val out = IntArray(dw * dh)
        val fx = sw.toDouble() / dw
        val fy = sh.toDouble() / dh
        for (y in 0 until dh) {
            val sy = ((y + 0.5) * fy - 0.5).coerceIn(0.0, (sh - 1).toDouble())
            val y0 = sy.toInt()
            val y1 = min(sh - 1, y0 + 1)
            val wy = sy - y0
            for (x in 0 until dw) {
                val sx = ((x + 0.5) * fx - 0.5).coerceIn(0.0, (sw - 1).toDouble())
                val x0 = sx.toInt()
                val x1 = min(sw - 1, x0 + 1)
                val wx = sx - x0
                val a = src[y0 * sw + x0] * (1 - wx) + src[y0 * sw + x1] * wx
                val b = src[y1 * sw + x0] * (1 - wx) + src[y1 * sw + x1] * wx
                out[y * dw + x] = (a * (1 - wy) + b * wy + 0.5).toInt()
            }
        }
        return out
    }

    /** Correlação normalizada (como TM_CCOEFF_NORMED do OpenCV). Devolve (nota, x, y) da melhor posição. */
    fun casar(reg: IntArray, rw: Int, rh: Int, t: IntArray, tw: Int, th: Int): Triple<Double, Int, Int> {
        if (rw < tw || rh < th) return Triple(0.0, 0, 0)
        val n = tw * th
        var mt = 0.0
        for (v in t) mt += v
        mt /= n
        val tp = FloatArray(n) { (t[it] - mt).toFloat() }
        var tt = 0.0
        for (v in tp) tt += v * v
        if (tt < 1e-6) return Triple(0.0, 0, 0)

        // imagens integrais de soma e soma dos quadrados
        val iw = rw + 1
        val s1 = LongArray(iw * (rh + 1))
        val s2 = LongArray(iw * (rh + 1))
        for (y in 0 until rh) {
            var r1 = 0L
            var r2 = 0L
            for (x in 0 until rw) {
                val v = reg[y * rw + x].toLong()
                r1 += v
                r2 += v * v
                s1[(y + 1) * iw + x + 1] = s1[y * iw + x + 1] + r1
                s2[(y + 1) * iw + x + 1] = s2[y * iw + x + 1] + r2
            }
        }
        fun nota(x: Int, y: Int): Double {
            val a = (y + th) * iw + x + tw
            val sum = s1[a] - s1[y * iw + x + tw] - s1[(y + th) * iw + x] + s1[y * iw + x]
            val sq = s2[a] - s2[y * iw + x + tw] - s2[(y + th) * iw + x] + s2[y * iw + x]
            val varI = sq - sum.toDouble() * sum / n
            if (varI < 1e-6) return -1.0
            var cross = 0f
            var k = 0
            for (ty in 0 until th) {
                var p = (y + ty) * rw + x
                for (tx in 0 until tw) {
                    cross += reg[p++] * tp[k++]
                }
            }
            return cross / sqrt(varI * tt)
        }

        // pré-filtro barato: correlação aproximada com 1/4 dos pontos do recorte em todas as posições;
        // depois a nota exata só nas melhores posições (e vizinhas)
        val sub = ArrayList<Int>()
        for (ty in 0 until th step 2) for (tx in 0 until tw step 2) sub.add(ty * tw + tx)
        val subIdx = sub.toIntArray()
        val k = 12
        val topNota = DoubleArray(k) { -1e18 }
        val topX = IntArray(k)
        val topY = IntArray(k)
        for (y in 0..rh - th) {
            for (x in 0..rw - tw) {
                val a = (y + th) * iw + x + tw
                val sum = s1[a] - s1[y * iw + x + tw] - s1[(y + th) * iw + x] + s1[y * iw + x]
                val sq = s2[a] - s2[y * iw + x + tw] - s2[(y + th) * iw + x] + s2[y * iw + x]
                val varI = sq - sum.toDouble() * sum / n
                if (varI < 1e-6) continue
                var cross = 0f
                for (q in subIdx) {
                    val ty = q / tw
                    val tx = q - ty * tw
                    cross += reg[(y + ty) * rw + x + tx] * tp[q]
                }
                val aprox = cross / sqrt(varI * tt)
                // insere se for melhor que o pior dos k guardados
                var pior = 0
                for (i in 1 until k) if (topNota[i] < topNota[pior]) pior = i
                if (aprox > topNota[pior]) {
                    topNota[pior] = aprox
                    topX[pior] = x
                    topY[pior] = y
                }
            }
        }
        var best = -1.0
        var bx = 0
        var by = 0
        for (i in 0 until k) {
            if (topNota[i] < -1e17) continue
            for (yy in max(0, topY[i] - 1)..min(rh - th, topY[i] + 1)) {
                for (xx in max(0, topX[i] - 1)..min(rw - tw, topX[i] + 1)) {
                    val sc = nota(xx, yy)
                    if (sc > best) {
                        best = sc
                        bx = xx
                        by = yy
                    }
                }
            }
        }
        return Triple(best, bx, by)
    }
}
