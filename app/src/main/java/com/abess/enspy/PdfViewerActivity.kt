package com.abess.enspy

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class PdfViewerActivity : AppCompatActivity() {
    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null
    private var pageIndex = 0
    private lateinit var image: ImageView
    private lateinit var pageLabel: TextView
    private var scaleFactor = 1f
    private lateinit var scaleDetector: ScaleGestureDetector

    private var plainFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(35, 31, 28)) }
        val bar = LinearLayout(this).apply { setPadding(12, 12, 12, 12); gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = intent.getStringExtra("title") ?: "Document ENSPY"
            setTextColor(Color.WHITE); setTextSize(16f)
        }
        bar.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        pageLabel = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(13f) }
        bar.addView(pageLabel)
        root.addView(bar)
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.DKGRAY)
        }
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1f, 4f)
                image.scaleX = scaleFactor
                image.scaleY = scaleFactor
                return true
            }
        })
        image.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && scaleFactor > 1f) {
                image.performClick()
            }
            true
        }
        root.addView(ScrollView(this).apply { addView(image) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(8, 8, 8, 8) }
        val previous = Button(this).apply { text = "‹"; setTextColor(Color.WHITE) }
        val next = Button(this).apply { text = "›"; setTextColor(Color.WHITE) }
        controls.addView(previous, LinearLayout.LayoutParams(76, 52))
        controls.addView(next, LinearLayout.LayoutParams(76, 52))
        root.addView(controls)
        setContentView(root)
        val encrypted = File(intent.getStringExtra("path").orEmpty())
        runCatching {
            val plain = SecureStore(this).decryptToCache(encrypted, intent.getIntExtra("documentId", 0))
            plainFile = plain
            descriptor = ParcelFileDescriptor.open(plain, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            render()
        }.onFailure { Toast.makeText(this, "Impossible d'ouvrir le PDF.", Toast.LENGTH_LONG).show(); finish() }
        previous.setOnClickListener { if (pageIndex > 0) { pageIndex--; render() } }
        next.setOnClickListener { if (renderer != null && pageIndex < renderer!!.pageCount - 1) { pageIndex++; render() } }
        title.setOnClickListener { searchPage() }
    }

    private var currentBitmap: Bitmap? = null

    private fun render() {
        val pdf = renderer ?: return
        val page = pdf.openPage(pageIndex)
        val width = (resources.displayMetrics.widthPixels * 1.8).toInt()
        val height = width * page.height / page.width
        
        currentBitmap?.recycle()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        image.setImageBitmap(bitmap)
        currentBitmap = bitmap
        
        pageLabel.text = "${pageIndex + 1} / ${pdf.pageCount}"
    }

    private fun searchPage() {
        val input = EditText(this).apply {
            hint = "Numéro de page"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialogBuilder(this, "Rechercher une page", input) { value ->
            val requested = value.toIntOrNull()?.minus(1) ?: return@AlertDialogBuilder
            if (renderer != null && requested in 0 until renderer!!.pageCount) {
                pageIndex = requested
                render()
            } else Toast.makeText(this, "Page introuvable.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun AlertDialogBuilder(
        activity: PdfViewerActivity,
        title: String,
        input: EditText,
        onConfirm: (String) -> Unit
    ) {
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Ouvrir") { _, _ -> onConfirm(input.text.toString()) }
            .show()
    }

    override fun onDestroy() {
        currentBitmap?.recycle()
        renderer?.close()
        descriptor?.close()
        plainFile?.delete()
        super.onDestroy()
    }
}