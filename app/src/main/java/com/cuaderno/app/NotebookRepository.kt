package com.cuaderno.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Maneja la persistencia del cuaderno en disco (JSON en filesDir).
 * Similar a localStorage en la web.
 */
class NotebookRepository(private val context: Context) {

    companion object {
        private const val NOTEBOOK_FILE = "cuaderno_notebook.json"
        private const val IMAGES_DIR = "cuaderno_images"
    }

    private val notebookFile = File(context.filesDir, NOTEBOOK_FILE)
    private val imagesDir = File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }

    /**
     * Guarda el notebook completo a JSON
     */
    fun save(notebook: Notebook) {
        try {
            val json = notebookToJson(notebook)
            notebookFile.writeText(json.toString(2))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Carga el notebook desde JSON, o crea uno nuevo si no existe
     */
    fun load(): Notebook {
        return try {
            if (notebookFile.exists()) {
                val json = JSONObject(notebookFile.readText())
                jsonToNotebook(json)
            } else {
                Notebook()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Notebook()
        }
    }

    /**
     * Guarda una imagen desde un Uri a almacenamiento interno y devuelve la ruta
     */
    fun saveImage(uri: Uri): String? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return null

            val filename = "img_${System.currentTimeMillis()}.png"
            val file = File(imagesDir, filename)
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
            fos.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Guarda una imagen desde un Bitmap y devuelve la ruta
     */
    fun saveBitmap(bitmap: Bitmap): String {
        val filename = "img_${System.currentTimeMillis()}.png"
        val file = File(imagesDir, filename)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
        fos.close()
        return file.absolutePath
    }

    // =========== Serialización JSON ===========

    private fun notebookToJson(notebook: Notebook): JSONObject {
        return JSONObject().apply {
            put("currentSpread", notebook.currentSpread)
            put("cover", coverToJson(notebook.cover))
            put("pages", JSONArray().apply {
                for (page in notebook.pages) {
                    put(pageToJson(page))
                }
            })
        }
    }

    private fun coverToJson(cover: CoverData): JSONObject {
        return JSONObject().apply {
            put("title", cover.title)
            put("texture", cover.texture)
            put("font", cover.font)
            put("imagePath", cover.imagePath ?: JSONObject.NULL)
        }
    }

    private fun pageToJson(page: PageData): JSONObject {
        return JSONObject().apply {
            put("id", page.id)
            put("strokes", JSONArray().apply {
                for (stroke in page.strokes) {
                    put(strokeToJson(stroke))
                }
            })
            put("items", JSONArray().apply {
                for (item in page.items) {
                    put(itemToJson(item))
                }
            })
        }
    }

    private fun strokeToJson(stroke: StrokeData): JSONObject {
        return JSONObject().apply {
            put("tool", stroke.tool)
            put("color", stroke.color)
            put("size", stroke.size.toDouble())
            put("points", JSONArray().apply {
                for (p in stroke.points) {
                    put(JSONObject().apply {
                        put("x", p.x.toDouble())
                        put("y", p.y.toDouble())
                    })
                }
            })
        }
    }

    private fun itemToJson(item: PastedItemData): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("type", item.type)
            put("imageUri", item.imageUri)
            put("x", item.x.toDouble())
            put("y", item.y.toDouble())
            put("w", item.w.toDouble())
            put("h", item.h.toDouble())
        }
    }

    // =========== Deserialización JSON ===========

    private fun jsonToNotebook(json: JSONObject): Notebook {
        val cover = jsonToCover(json.optJSONObject("cover") ?: JSONObject())
        val pages = mutableListOf<PageData>()
        val pagesArr = json.optJSONArray("pages")
        if (pagesArr != null) {
            for (i in 0 until pagesArr.length()) {
                pages.add(jsonToPage(pagesArr.getJSONObject(i)))
            }
        }
        if (pages.isEmpty()) {
            pages.add(PageData())
            pages.add(PageData())
        }
        // Asegurar cantidad par
        if (pages.size % 2 != 0) {
            pages.add(PageData())
        }
        val maxSpread = (pages.size - 2).coerceAtLeast(0)
        val requestedSpread = json.optInt("currentSpread", 0).coerceIn(0, maxSpread)
        val safeSpread = if (requestedSpread % 2 == 0) requestedSpread else requestedSpread - 1

        return Notebook(
            pages = pages,
            currentSpread = safeSpread,
            cover = cover
        )
    }

    private fun jsonToCover(json: JSONObject): CoverData {
        val rawImagePath = if (json.has("imagePath") && !json.isNull("imagePath")) {
            json.optString("imagePath", null)
        } else {
            null
        }
        val imagePath = rawImagePath?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

        return CoverData(
            title = json.optString("title", "Mi Cuaderno"),
            texture = json.optString("texture", "cuero-marron"),
            font = json.optString("font", "fraunces"),
            imagePath = imagePath
        )
    }

    private fun jsonToPage(json: JSONObject): PageData {
        val strokes = mutableListOf<StrokeData>()
        val strokesArr = json.optJSONArray("strokes")
        if (strokesArr != null) {
            for (i in 0 until strokesArr.length()) {
                strokes.add(jsonToStroke(strokesArr.getJSONObject(i)))
            }
        }
        val items = mutableListOf<PastedItemData>()
        val itemsArr = json.optJSONArray("items")
        if (itemsArr != null) {
            for (i in 0 until itemsArr.length()) {
                items.add(jsonToItem(itemsArr.getJSONObject(i)))
            }
        }
        return PageData(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            strokes = strokes,
            items = items
        )
    }

    private fun jsonToStroke(json: JSONObject): StrokeData {
        val points = mutableListOf<PointF>()
        val pointsArr = json.optJSONArray("points")
        if (pointsArr != null) {
            for (i in 0 until pointsArr.length()) {
                val p = pointsArr.getJSONObject(i)
                points.add(PointF(p.optDouble("x", 0.0).toFloat(), p.optDouble("y", 0.0).toFloat()))
            }
        }
        return StrokeData(
            tool = json.optString("tool", "pen"),
            color = json.optInt("color", 0xFF2B2622.toInt()),
            size = json.optDouble("size", 3.0).toFloat(),
            points = points
        )
    }

    private fun jsonToItem(json: JSONObject): PastedItemData {
        return PastedItemData(
            id = json.optString("id", java.util.UUID.randomUUID().toString()),
            type = json.optString("type", "image"),
            imageUri = json.optString("imageUri", ""),
            x = json.optDouble("x", 0.0).toFloat(),
            y = json.optDouble("y", 0.0).toFloat(),
            w = json.optDouble("w", 260.0).toFloat(),
            h = json.optDouble("h", 200.0).toFloat()
        )
    }
}