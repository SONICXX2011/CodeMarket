package ir.codemarket.app

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import ir.codemarket.app.databinding.ActivityCreatePostBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding
    private lateinit var sessionManager: SessionManager
    private var selectedMediaUri: Uri? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedMediaUri = it
            binding.cardPreview.visibility = View.VISIBLE
            Glide.with(this).load(it).into(binding.imgPreview)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)
        
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        else binding.root.setBackgroundResource(R.drawable.bg_gradient_light)

        // اینجا مارک‌داون اعمال شده
        MarkdownUtils.applyMarkdownShortcuts(binding.etPostText)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPickMedia.setOnClickListener { pickMedia.launch("image/*") }

        binding.btnRemoveImage.setOnClickListener {
            selectedMediaUri = null
            binding.imgPreview.setImageDrawable(null)
            binding.cardPreview.visibility = View.GONE
        }

        binding.btnPost.setOnClickListener {
            val text = binding.etPostText.text.toString()
            if (text.isNotEmpty() || selectedMediaUri != null) {
                uploadPost(text, selectedMediaUri)
            } else {
                Toast.makeText(this, "پست خالی است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadPost(text: String, mediaUri: Uri?) {
        var mediaFile: File? = null
        if (mediaUri != null) {
            val mimeType = contentResolver.getType(mediaUri)
            val ext = if (mimeType?.contains("video") == true) "mp4" else "jpg"
            mediaFile = File(cacheDir, "temp_post.$ext").apply { copyFromUri(mediaUri) }
        }

        binding.btnPost.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadPost("/api/feed/post", token, text, mediaFile) }
            binding.btnPost.isEnabled = true
            if (res != null && JSONObject(res).getBoolean("success")) {
                Toast.makeText(this@CreatePostActivity, "پست منتشر شد", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this@CreatePostActivity, "خطا در انتشار پست", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun File.copyFromUri(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { input ->
            this.outputStream().use { output -> input.copyTo(output) }
        }
    }
}