package com.cuaderno.app

import android.graphics.Path
import java.util.UUID

// ============================================================
// Data models — replica de la estructura del Cuaderno web
// ============================================================

data class Notebook(
    var pages: MutableList<PageData> = mutableListOf(PageData(), PageData()),
    var currentSpread: Int = 0,
    var cover: CoverData = CoverData()
)

data class CoverData(
    var title: String = "Mi Cuaderno",
    var texture: String = "cuero-marron",
    var font: String = "fraunces",
    var imagePath: String? = null  // URI de imagen seleccionada
)

data class PageData(
    val id: String = UUID.randomUUID().toString(),
    val strokes: MutableList<StrokeData> = mutableListOf(),
    val items: MutableList<PastedItemData> = mutableListOf()
)

data class StrokeData(
    val tool: String = "pen",       // "pen", "marker", "eraser"
    val color: Int = 0xFF2B2622.toInt(),
    val size: Float = 3f,
    val points: MutableList<PointF> = mutableListOf()
)

data class PointF(
    val x: Float,
    val y: Float
)

data class PastedItemData(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "image",
    var imageUri: String = "",
    var x: Float = 0f,
    var y: Float = 0f,
    var w: Float = 260f,
    var h: Float = 200f
)

// Texturas y fuentes disponibles
object Textures {
    val entries = listOf(
        TextureEntry("cuero-marron", "Cuero marrón", intArrayOf(0xFF5A3E2B.toInt(), 0xFF3D2A1C.toInt())),
        TextureEntry("cuero-negro", "Cuero negro", intArrayOf(0xFF2A2624.toInt(), 0xFF15120F.toInt())),
        TextureEntry("verde-botella", "Verde botella", intArrayOf(0xFF2D5A4A.toInt(), 0xFF1A3A2E.toInt())),
        TextureEntry("azul-marino", "Azul marino", intArrayOf(0xFF2C4A6E.toInt(), 0xFF192E47.toInt())),
        TextureEntry("vino", "Vino", intArrayOf(0xFF6B2737.toInt(), 0xFF3F1620.toInt())),
        TextureEntry("crema-lino", "Crema lino", intArrayOf(0xFFE3DCCB.toInt(), 0xFFC9BFA8.toInt())),
        TextureEntry("mostaza", "Mostaza", intArrayOf(0xFFB8945F.toInt(), 0xFF8A6C3F.toInt())),
        TextureEntry("lavanda", "Lavanda", intArrayOf(0xFF6B5B95.toInt(), 0xFF473F63.toInt()))
    )
    fun byKey(key: String) = entries.find { it.key == key } ?: entries[0]
}

data class TextureEntry(val key: String, val label: String, val colors: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextureEntry) return false
        return key == other.key
    }
    override fun hashCode() = key.hashCode()
}

object CoverFonts {
    val entries = listOf(
        FontEntry("fraunces", "Fraunces (serif)", "serif"),
        FontEntry("inter", "Inter (moderna)", "sans-serif"),
        FontEntry("georgia", "Georgia (clásica)", "serif"),
        FontEntry("mono", "Monoespaciada", "monospace")
    )
    fun byKey(key: String) = entries.find { it.key == key } ?: entries[0]
}

data class FontEntry(val key: String, val label: String, val fontFamily: String)