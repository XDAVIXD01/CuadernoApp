package com.cuaderno.app

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.io.File

/**
 * Pantalla de portada 3D del cuaderno.
 * Muestra el libro con textura, título y hojas decorativas.
 */
class CoverActivity : AppCompatActivity() {

    private lateinit var repository: NotebookRepository
    private lateinit var notebook: Notebook
    private var openingAnimationRunning = false
    private var returningFromNotebook = false
    private var skipInitialResumeRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cover)

        repository = NotebookRepository(applicationContext)
        notebook = repository.load()

        setupUI()
        skipInitialResumeRefresh = true
    }

    private fun setupUI(playCloseAnimation: Boolean = false) {
        val cover = notebook.cover
        val titleView = findViewById<TextView>(R.id.coverTitleText)
        val pageCountView = findViewById<TextView>(R.id.coverPageCount)
        val bookZone = findViewById<View>(R.id.bookInteractiveZone)
        val bookCard = findViewById<View>(R.id.bookCard)
        val openBookButton = findViewById<MaterialButton>(R.id.openBookBtn)
        val hintView = findViewById<TextView>(R.id.tapHintText)

        // Título
        titleView.text = cover.title.ifBlank { "Mi Cuaderno" }

        val font = CoverFonts.byKey(cover.font)
        titleView.typeface = TypefaceCache.forCover(font)
        titleView.gravity = if (font.fontFamily == "monospace") Gravity.START else Gravity.CENTER

        // Contador de páginas
        pageCountView.text = "${notebook.pages.size} paginas"

        // Aplicar textura de portada
        applyTexture(cover.texture, cover.imagePath)

        if (playCloseAnimation) {
            animateClosingBook(bookZone, hintView)
        } else {
            animateIdleBook(bookZone, hintView)
        }

        // Botón abrir cuaderno
        openBookButton.setOnClickListener {
            openNotebookWithAnimation(bookZone)
        }

        // Toque sobre el libro abre el cuaderno
        bookCard.setOnClickListener {
            openNotebookWithAnimation(bookZone)
        }

        // Botón editar portada
        findViewById<ImageView>(R.id.editCoverBtn).setOnClickListener {
            val intent = Intent(this, CoverEditorActivity::class.java)
            startActivityForResult(intent, REQUEST_EDIT_COVER)
        }

        // Botón exportar
        findViewById<ImageView>(R.id.exportFromCoverBtn).setOnClickListener {
            // Exportar la página actual
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("export", true)
            startActivity(intent)
        }
    }

    private fun applyTexture(textureKey: String, imagePath: String?) {
        val coverView = findViewById<View>(R.id.coverSurface)
        val textureLayer = findViewById<View>(R.id.coverTextureLayer)
        val imageScrim = findViewById<View>(R.id.coverImageScrim)
        val bgImg = findViewById<ImageView>(R.id.coverBgImg)

        val imageUri = imagePath?.let { resolveImageUri(it) }

        if (imageUri != null && canUseImageUri(imageUri)) {
            // Cargar imagen de portada seleccionada por el usuario.
            bgImg.setImageURI(imageUri)
            bgImg.visibility = View.VISIBLE
            textureLayer.visibility = View.GONE
            imageScrim.alpha = 0.55f
            coverView.background = null
        } else {
            bgImg.visibility = ImageView.GONE
            textureLayer.visibility = View.VISIBLE
            imageScrim.alpha = 1f
            val tex = Textures.byKey(textureKey)
            val drawable = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(tex.colors[0], tex.colors[1])
            )
            drawable.cornerRadius = resources.getDimension(R.dimen.book_corner_radius)
            coverView.background = drawable
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
                !path.isNullOrBlank() && java.io.File(path).exists()
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

    private fun animateIdleBook(bookZone: View, hintView: View) {
        bookZone.animate().cancel()
        hintView.animate().cancel()

        resetBookTransform(bookZone)
        bookZone.scaleX = 0.96f
        bookZone.scaleY = 0.96f
        bookZone.alpha = 0f
        bookZone.translationY = 34f
        hintView.alpha = 0f
        hintView.translationY = 10f

        bookZone.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .translationY(0f)
            .setDuration(520)
            .withEndAction {
                bookZone.animate()
                    .translationYBy(-5f)
                    .setDuration(1400)
                    .withEndAction {
                        bookZone.animate().translationYBy(5f).setDuration(1400).start()
                    }
                    .start()
            }
            .start()

        hintView.animate()
            .alpha(0.9f)
            .translationY(0f)
            .setStartDelay(180)
            .setDuration(420)
            .start()
    }

    private fun animateClosingBook(bookZone: View, hintView: View) {
        bookZone.animate().cancel()
        hintView.animate().cancel()

        hintView.alpha = 0f
        hintView.translationY = 10f
        bookZone.rotationX = 0f
        bookZone.rotation = 0f
        bookZone.rotationY = -78f
        bookZone.scaleX = 1.04f
        bookZone.scaleY = 1.02f
        bookZone.alpha = 0f
        bookZone.translationX = -120f
        bookZone.translationY = 0f
        bookZone.translationZ = 0f

        bookZone.animate()
            .rotationY(-24f)
            .alpha(1f)
            .translationX(-20f)
            .setDuration(220)
            .withEndAction {
                bookZone.animate()
                    .rotationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .setDuration(260)
                    .withEndAction {
                        animateIdleFloat(bookZone)
                    }
                    .start()
            }
            .start()

        hintView.animate()
            .alpha(0.9f)
            .translationY(0f)
            .setStartDelay(260)
            .setDuration(360)
            .start()
    }

    private fun animateIdleFloat(bookZone: View) {
        bookZone.animate()
            .translationYBy(-5f)
            .setDuration(1400)
            .withEndAction {
                bookZone.animate().translationYBy(5f).setDuration(1400).start()
            }
            .start()
    }

    private fun resetBookTransform(bookZone: View) {
        bookZone.rotationX = 0f
        bookZone.rotationY = 0f
        bookZone.rotation = 0f
        bookZone.translationX = 0f
        bookZone.translationY = 0f
        bookZone.translationZ = 0f
        bookZone.alpha = 1f
    }

    private fun openNotebookWithAnimation(bookZone: View) {
        if (openingAnimationRunning) return
        openingAnimationRunning = true

        bookZone.animate().cancel()
        bookZone.animate()
            .rotationY(-24f)
            .scaleX(1.04f)
            .scaleY(1.02f)
            .translationX(-20f)
            .setDuration(240)
            .withEndAction {
                bookZone.animate()
                    .rotationY(-78f)
                    .alpha(0f)
                    .translationX(-120f)
                    .setDuration(220)
                    .withEndAction {
                        try {
                            returningFromNotebook = true
                            startActivity(Intent(this, MainActivity::class.java))
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        } catch (e: Exception) {
                            returningFromNotebook = false
                            openingAnimationRunning = false
                            Toast.makeText(this, "No se pudo abrir el cuaderno", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .start()
            }
            .start()
    }

    override fun onResume() {
        super.onResume()
        openingAnimationRunning = false
        if (skipInitialResumeRefresh) {
            skipInitialResumeRefresh = false
            return
        }

        // Recargar datos por si se editó la portada
        notebook = repository.load()
        setupUI(returningFromNotebook)
        returningFromNotebook = false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_COVER && resultCode == RESULT_OK) {
            notebook = repository.load()
            setupUI()
        }
    }

    companion object {
        private const val REQUEST_EDIT_COVER = 1001
    }
}

private object TypefaceCache {
    fun forCover(font: FontEntry) = when (font.fontFamily) {
        "serif" -> Typeface.create("serif", Typeface.BOLD)
        "monospace" -> Typeface.create("monospace", Typeface.BOLD)
        else -> Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
}
