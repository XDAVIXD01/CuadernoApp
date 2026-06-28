package com.cuaderno.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import java.io.File

/**
 * Actividad principal del cuaderno: vista de trabajo con dos páginas (spread).
 * Dibujo, imágenes, navegación entre páginas.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: NotebookRepository
    private lateinit var notebook: Notebook

    // Vistas de dibujo (izquierda y derecha)
    private lateinit var drawingViewLeft: DrawingView
    private lateinit var drawingViewRight: DrawingView

    // Herramientas
    private var currentTool = "pen"
    private var currentColor: Int = 0xFF2B2622.toInt()
    private var currentSize: Float = 3f
    private var lastActiveSide = "left" // "left" o "right"

    // Selector de imágenes
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { addImageToCurrentPage(it) }
    }

    // Cámara
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraImageUri != null) {
            addImageToCurrentPage(cameraImageUri!!)
        }
    }
    private var cameraImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        repository = NotebookRepository(applicationContext)
        notebook = repository.load()

        initViews()
        renderSpread()
        setupBackNavigation()

        // Si viene con flag de exportar
        if (intent?.getBooleanExtra("export", false) == true) {
            exportCurrentPage()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackToCover()
            }
        })
    }

    private fun initViews() {
        drawingViewLeft = findViewById(R.id.drawingViewLeft)
        drawingViewRight = findViewById(R.id.drawingViewRight)

        // Callbacks de guardado
        drawingViewLeft.onPageChanged = { saveNotebook() }
        drawingViewRight.onPageChanged = { saveNotebook() }

        // Herramientas
        findViewById<MaterialButton>(R.id.penBtn).setOnClickListener { setTool("pen") }
        findViewById<MaterialButton>(R.id.markerBtn).setOnClickListener { setTool("marker") }
        findViewById<MaterialButton>(R.id.eraserBtn).setOnClickListener { setTool("eraser") }

        // Colores
        findViewById<View>(R.id.colorBlack).setOnClickListener { setColor(0xFF2B2622.toInt()) }
        findViewById<View>(R.id.colorRed).setOnClickListener { setColor(0xFF8B3A3A.toInt()) }
        findViewById<View>(R.id.colorBlue).setOnClickListener { setColor(0xFF2C4A6E.toInt()) }
        findViewById<View>(R.id.colorGreen).setOnClickListener { setColor(0xFF2D5A4A.toInt()) }
        findViewById<View>(R.id.colorYellow).setOnClickListener { setColor(0xFFB8945F.toInt()) }

        // Slider de tamaño
        findViewById<Slider>(R.id.sizeSlider).addOnChangeListener { _, value, _ ->
            currentSize = value
            updateToolState()
        }

        // Navegación
        findViewById<ImageButton>(R.id.prevBtn).setOnClickListener { goPrev() }
        findViewById<ImageButton>(R.id.nextBtn).setOnClickListener { goNext() }

        // Acciones
        findViewById<ImageButton>(R.id.undoBtn).setOnClickListener { undo() }
        findViewById<ImageButton>(R.id.clearBtn).setOnClickListener { clearPage() }
        findViewById<ImageButton>(R.id.addPageBtn).setOnClickListener { addPage() }
        findViewById<ImageButton>(R.id.deletePageBtn).setOnClickListener { deletePage() }
        findViewById<ImageButton>(R.id.exportBtn).setOnClickListener { exportCurrentPage() }
        findViewById<ImageButton>(R.id.pasteBtn).setOnClickListener { pickImage() }
        findViewById<ImageButton>(R.id.cameraBtn).setOnClickListener { takePhoto() }
        findViewById<ImageButton>(R.id.backToCoverBtn).setOnClickListener { goBackToCover() }

        // Detectar lado activo
        findViewById<View>(R.id.pageLeft).setOnTouchListener { _, _ ->
            lastActiveSide = "left"; false
        }
        findViewById<View>(R.id.pageRight).setOnTouchListener { _, _ ->
            lastActiveSide = "right"; false
        }
    }

    private fun setTool(tool: String) {
        currentTool = tool
        updateToolState()
        // Actualizar UI de botones
        listOf(R.id.penBtn, R.id.markerBtn, R.id.eraserBtn).forEach { id ->
            findViewById<MaterialButton>(id).isChecked = id == when (tool) {
                "pen" -> R.id.penBtn
                "marker" -> R.id.markerBtn
                "eraser" -> R.id.eraserBtn
                else -> R.id.penBtn
            }
        }
    }

    private fun setColor(color: Int) {
        currentColor = color
        updateToolState()
    }

    private fun updateToolState() {
        drawingViewLeft.currentTool = currentTool
        drawingViewLeft.currentColor = currentColor
        drawingViewLeft.currentSize = currentSize
        drawingViewRight.currentTool = currentTool
        drawingViewRight.currentColor = currentColor
        drawingViewRight.currentSize = currentSize
    }

    private fun renderSpread() {
        val leftPage = notebook.pages.getOrNull(notebook.currentSpread)
        val rightPage = notebook.pages.getOrNull(notebook.currentSpread + 1)

        drawingViewLeft.pageData = leftPage
        drawingViewRight.pageData = rightPage

        // Indicador de página
        findViewById<TextView>(R.id.pageIndicator).text =
            "Páginas ${notebook.currentSpread + 1}–${notebook.currentSpread + 2} de ${notebook.pages.size}"

        // Botones de navegación
        findViewById<ImageButton>(R.id.prevBtn).isEnabled = notebook.currentSpread > 0
        findViewById<ImageButton>(R.id.nextBtn).isEnabled =
            notebook.currentSpread + 2 < notebook.pages.size
    }

    private fun goNext() {
        if (notebook.currentSpread + 2 < notebook.pages.size) {
            notebook.currentSpread += 2
            renderSpread()
        }
    }

    private fun goPrev() {
        if (notebook.currentSpread > 0) {
            notebook.currentSpread -= 2
            renderSpread()
        }
    }

    private fun undo() {
        val activeView = if (lastActiveSide == "left") drawingViewLeft else drawingViewRight
        activeView.undo()
    }

    private fun clearPage() {
        AlertDialog.Builder(this)
            .setTitle("Borrar dibujo")
            .setMessage("¿Borrar todo el dibujo de esta página? Las imágenes no se eliminan.")
            .setPositiveButton("Borrar") { _, _ ->
                val activeView = if (lastActiveSide == "left") drawingViewLeft else drawingViewRight
                activeView.clearStrokes()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addPage() {
        notebook.pages.add(PageData())
        notebook.pages.add(PageData())
        notebook.currentSpread = notebook.pages.size - 2
        renderSpread()
        saveNotebook()
    }

    private fun deletePage() {
        if (notebook.pages.size <= 2) {
            Toast.makeText(this, "El cuaderno necesita al menos una hoja", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Borrar hoja")
            .setMessage("¿Borrar esta hoja (ambas páginas)? Esta acción no se puede deshacer.")
            .setPositiveButton("Borrar") { _, _ ->
                notebook.pages.removeAt(notebook.currentSpread)
                notebook.pages.removeAt(notebook.currentSpread) // remove the second one
                if (notebook.currentSpread >= notebook.pages.size) {
                    notebook.currentSpread = (notebook.pages.size - 2).coerceAtLeast(0)
                }
                renderSpread()
                saveNotebook()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }

    private fun takePhoto() {
        val photoFile = File(filesDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraImageUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(cameraImageUri!!)
    }

    private fun addImageToCurrentPage(uri: Uri) {
        val pageIndex = notebook.currentSpread + if (lastActiveSide == "left") 0 else 1
        val pageData = notebook.pages.getOrNull(pageIndex) ?: return

        // Guardar imagen a almacenamiento interno
        val savedPath = repository.saveImage(uri)
        if (savedPath == null) {
            Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
            return
        }

        // Obtener dimensiones
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(savedPath, options)
        val imgW = options.outWidth
        val imgH = options.outHeight

        var w = imgW.toFloat()
        var h = imgH.toFloat()
        val maxDim = 260f
        if (w > maxDim || h > maxDim) {
            val scale = maxDim / maxOf(w, h)
            w *= scale
            h *= scale
        }

        val x = (DrawingView.PAGE_W - w) / 2f
        val y = (DrawingView.PAGE_H - h) / 2f

        val item = PastedItemData(
            imageUri = savedPath,
            x = x,
            y = y,
            w = w,
            h = h
        )
        pageData.items.add(item)
        saveNotebook()

        // Forzar redibujo
        renderSpread()
    }

    private fun exportCurrentPage() {
        // Exportar ambas páginas del spread actual
        val exportBmp = Bitmap.createBitmap(
            (DrawingView.PAGE_W * 4).toInt(),
            (DrawingView.PAGE_H * 2).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(exportBmp)
        canvas.drawColor(0xFFF0EBE0.toInt())

        // Página izquierda
        val leftBmp = drawingViewLeft.exportBitmap()
        canvas.drawBitmap(leftBmp, 0f, 0f, null)
        leftBmp.recycle()

        // Página derecha
        val rightBmp = drawingViewRight.exportBitmap()
        canvas.drawBitmap(rightBmp, DrawingView.PAGE_W * 2, 0f, null)
        rightBmp.recycle()

        // Guardar y compartir
        val file = File(cacheDir, "cuaderno_export_${System.currentTimeMillis()}.png")
        file.outputStream().use { out ->
            exportBmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        exportBmp.recycle()

        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir página"))
    }

    private fun goBackToCover() {
        saveNotebook()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun saveNotebook() {
        repository.save(notebook)
    }

    override fun onPause() {
        super.onPause()
        saveNotebook()
    }
}
