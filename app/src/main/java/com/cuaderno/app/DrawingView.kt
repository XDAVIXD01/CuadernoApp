package com.cuaderno.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom View que renderiza una página del cuaderno.
 * Soporta dibujo a mano alzada (pen, marker, eraser) y muestra imágenes pegadas.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Página que estamos editando
    var pageData: PageData? = null
        set(value) {
            field = value
            invalidate()
        }

    // Herramienta activa
    var currentTool: String = "pen"
    var currentColor: Int = 0xFF2B2622.toInt()
    var currentSize: Float = 3f

    // Estado de dibujo
    private var isDrawing = false
    private val currentPoints = mutableListOf<PointF>()

    // Bitmap de respaldo para no redibujar todos los trazos en cada frame
    private var bitmapCache: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private var cacheDirty = true

    // Callback para notificar cambios (guardar)
    var onPageChanged: (() -> Unit)? = null

    // Tamaño lógico de página (como en la web)
    companion object {
        const val PAGE_W = 480f
        const val PAGE_H = 640f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildCache()
    }

    private fun rebuildCache() {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        bitmapCache?.recycle()
        bitmapCache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmapCache!!)
        cacheDirty = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Fondo de página
        canvas.drawColor(0xFFF0EBE0.toInt())

        val page = pageData ?: return

        // Redibujar cache si es necesario
        if (cacheDirty) {
            redrawCache(page)
            cacheDirty = false
        }

        // Dibujar cache
        bitmapCache?.let { canvas.drawBitmap(it, 0f, 0f, null) }

        // Dibujar trazo actual (en progreso)
        if (currentPoints.size > 1) {
            drawStrokeOnCanvas(canvas, currentTool, currentColor, currentSize, currentPoints)
        }
    }

    private fun redrawCache(page: PageData) {
        val bc = bitmapCanvas ?: return
        bc.drawColor(0xFFF0EBE0.toInt())

        // Dibujar todos los trazos guardados
        for (stroke in page.strokes) {
            drawStrokeOnCanvas(bc, stroke.tool, stroke.color, stroke.size, stroke.points)
        }
    }

    private fun drawStrokeOnCanvas(
        canvas: Canvas,
        tool: String,
        color: Int,
        size: Float,
        points: List<PointF>
    ) {
        if (points.size < 1) return

        val scaleX = width / PAGE_W
        val scaleY = height / PAGE_H

        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
            strokeWidth = size * ((scaleX + scaleY) / 2f)
        }

        if (tool == "eraser") {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            paint.color = 0x00000000.toInt()
        } else {
            paint.color = color
            paint.alpha = if (tool == "marker") 115 else 255 // 0.45 * 255
        }

        val path = Path()
        path.moveTo(points[0].x * scaleX, points[0].y * scaleY)

        for (i in 1 until points.size) {
            path.lineTo(points[i].x * scaleX, points[i].y * scaleY)
        }

        if (points.size == 1) {
            path.lineTo(points[0].x * scaleX + 0.1f, points[0].y * scaleY + 0.1f)
        }

        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val page = pageData ?: return false

        if (width == 0 || height == 0) return false

        val scaleX = PAGE_W / width
        val scaleY = PAGE_H / height
        val logicalX = event.x * scaleX
        val logicalY = event.y * scaleY

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                currentPoints.clear()
                currentPoints.add(PointF(logicalX, logicalY))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDrawing) {
                    currentPoints.add(PointF(logicalX, logicalY))
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDrawing && currentPoints.isNotEmpty()) {
                    page.strokes.add(
                        StrokeData(
                            tool = currentTool,
                            color = currentColor,
                            size = currentSize,
                            points = currentPoints.toMutableList()
                        )
                    )
                    currentPoints.clear()
                    cacheDirty = true
                    invalidate()
                    onPageChanged?.invoke()
                }
                isDrawing = false
                return true
            }
        }
        return false
    }

    /**
     * Deshace el último trazo
     */
    fun undo() {
        val page = pageData ?: return
        if (page.strokes.isNotEmpty()) {
            page.strokes.removeAt(page.strokes.lastIndex)
            cacheDirty = true
            invalidate()
            onPageChanged?.invoke()
        }
    }

    /**
     * Limpia todos los trazos de la página
     */
    fun clearStrokes() {
        val page = pageData ?: return
        page.strokes.clear()
        cacheDirty = true
        invalidate()
        onPageChanged?.invoke()
    }

    /**
     * Exporta la página actual como Bitmap
     */
    fun exportBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap((PAGE_W * 2).toInt(), (PAGE_H * 2).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF0EBE0.toInt())
        c.scale(2f, 2f)

        val page = pageData
        if (page != null) {
            for (stroke in page.strokes) {
                drawStrokeOnCanvas(c, stroke.tool, stroke.color, stroke.size, stroke.points)
            }
        }
        return bmp
    }
}