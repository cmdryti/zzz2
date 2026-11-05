package com.ryti.dancebot.core

/** Um quadro da tela: pixels ARGB (0xAARRGGBB), linha por linha. */
class Frame(val w: Int, val h: Int, val px: IntArray) {
    init {
        require(px.size == w * h) { "tamanho do quadro inválido" }
    }
}

enum class Dir(val simbolo: String) {
    UP("↑"), LEFT("←"), RIGHT("→"), DOWN("↓")
}

fun List<Dir>.texto(): String = joinToString(" ") { it.simbolo }
