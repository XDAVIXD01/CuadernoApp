package com.cuaderno.app

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Editor de portada: permite cambiar título, textura, tipografía e imagen de portada.
 */
class CoverEditorActivity : AppCompatActivity() {

    private lateinit var repository: NotebookRepository
    private lateinit var notebook: Notebook
    private lateinit var editingCover: CoverData

    private val textureSwatches = linkedMapOf<String, View>()
    private val fontButtons = linkedMapOf<String, MaterialButton>()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val savedPath = repository.saveImage(it)
            if (savedPath != null) {
                editingCover.imagePath = savedPath
                updatePreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cover_editor)

        repository = NotebookRepository(applicationContext)
        notebook = repository.load()
        editingCover = notebook.cover.copy()

        setupUI()
    }

    private fun setupUI() {
        val titleInput = findViewById<EditText>(R.id.coverTitleInput)

        // Título
        titleInput.setText(editingCover.title)

        // Grid de texturas
        val textureGrid = findViewById<LinearLayout>(R.id.textureGrid)
        textureGrid.removeAllViews()
        textureSwatches.clear()
        for (tex in Textures.entries) {
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(66), dp(66)).apply {
                    marginEnd = dp(10)
                }
                background = createTextureSwatch(tex, tex.key == editingCover.texture)
                contentDescription = tex.label
                setOnClickListener {
                    editingCover.texture = tex.key
                    syncTextureSelection()
                    updatePreview()
                }
            }
            textureSwatches[tex.key] = swatch
            textureGrid.addView(swatch)
        }
        syncTextureSelection()

        // Grid de fuentes
        val fontGrid = findViewById<LinearLayout>(R.id.fontGrid)
        fontGrid.removeAllViews()
        fontButtons.clear()
        for (font in CoverFonts.entries) {
            val btn = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = font.label.substringBefore(" (")
                isAllCaps = false
                minimumHeight = dp(44)
                minHeight = dp(44)
                minWidth = dp(130)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(10) }
                setOnClickListener {
                    editingCover.font = font.key
                    syncFontSelection()
                    updatePreview()
                }
            }
            fontButtons[font.key] = btn
            fontGrid.addView(btn)
        }
        syncFontSelection()

        // Input de título
        titleInput.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    editingCover.title = s?.toString() ?: ""
                    updatePreview()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )

        // Botón seleccionar imagen
        findViewById<MaterialButton>(R.id.selectCoverImageBtn).setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Botón quitar imagen
        findViewById<MaterialButton>(R.id.removeCoverImageBtn).setOnClickListener {
            editingCover.imagePath = null
            updatePreview()
        }

        // Guardar
        findViewById<MaterialButton>(R.id.saveCoverBtn).setOnClickListener {
            if (editingCover.title.isBlank()) editingCover.title = "Mi Cuaderno"
            notebook.cover = editingCover
            repository.save(notebook)
            setResult(RESULT_OK)
            finish()
        }

        // Cancelar
        findViewById<MaterialButton>(R.id.cancelCoverBtn).setOnClickListener {
            finish()
        }

        updatePreview()
    }

    private fun updatePreview() {
        val previewCard = findViewById<View>(R.id.coverPreviewCard)
        val previewTitle = findViewById<TextView>(R.id.previewTitleText)
        val previewBgImg = findViewById<ImageView>(R.id.previewBgImg)

        previewTitle.text = editingCover.title.ifBlank { "Mi Cuaderno" }
        previewTitle.typeface = fontTypeFace(editingCover.font)
        previewTitle.gravity = if (CoverFonts.byKey(editingCover.font).fontFamily == "monospace") {
            Gravity.START
        } else {
            Gravity.CENTER
        }

        val imageUri = editingCover.imagePath?.let { resolveImageUri(it) }

        if (imageUri != null && canUseImageUri(imageUri)) {
            previewBgImg.setImageURI(imageUri)
            previewBgImg.visibility = ImageView.VISIBLE
            previewCard.setBackgroundColor(0x00000000)
        } else {
            previewBgImg.visibility = ImageView.GONE
            val tex = Textures.byKey(editingCover.texture)
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tex.colors[0], tex.colors[1])
            )
            drawable.cornerRadius = resources.getDimension(R.dimen.book_corner_radius)
            previewCard.background = drawable
        }
    }

    private fun syncTextureSelection() {
        for ((key, swatch) in textureSwatches) {
            val tex = Textures.byKey(key)
            val selected = key == editingCover.texture
            swatch.background = createTextureSwatch(tex, selected)
            swatch.alpha = if (selected) 1f else 0.78f
        }
    }

    private fun syncFontSelection() {
        for ((key, btn) in fontButtons) {
            val selected = key == editingCover.font
            val bgColor = if (selected) 0xFFB8945F.toInt() else 0x28000000
            btn.backgroundTintList = ColorStateList.valueOf(bgColor)
            btn.strokeWidth = if (selected) dp(2) else dp(1)
            btn.strokeColor = ColorStateList.valueOf(if (selected) 0xFFFFE0A8.toInt() else 0x55FFFFFF)
            btn.setTextColor(if (selected) 0xFFF9EFDF.toInt() else 0xFFDCCCB0.toInt())
            btn.typeface = fontTypeFace(key)
        }
    }

    private fun createTextureSwatch(texture: TextureEntry, selected: Boolean): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(texture.colors[0], texture.colors[1])
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setStroke(if (selected) dp(3) else dp(1), if (selected) 0xFFFFE0A8.toInt() else 0x44FFFFFF)
        }
    }

    private fun fontTypeFace(fontKey: String): Typeface {
        return when (CoverFonts.byKey(fontKey).fontFamily) {
            "serif" -> Typeface.create("serif", Typeface.BOLD)
            "monospace" -> Typeface.create("monospace", Typeface.BOLD)
            else -> Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
    }

    private fun resolveImageUri(pathOrUri: String): Uri? {
        val parsed = Uri.parse(pathOrUri)
        if (parsed.scheme.isNullOrBlank()) {
            val file = File(pathOrUri)
            if (file.exists()) return Uri.fromFile(file)
        }
        return parsed
    }

    private fun canUseImageUri(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path
                !path.isNullOrBlank() && File(path).exists()
            }
            "content" -> {
                try {
                    contentResolver.openInputStream(uri)?.close()
                    true
                } catch (_: Exception) {
                    false
                }
            }
            null -> {
                val raw = uri.toString()
                raw.isNotBlank() && File(raw).exists()
            }
            else -> false
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
