package ir.codemarket.app

import android.content.ContentValues
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ir.codemarket.app.databinding.ActivitySourceDetailsBinding
import ir.codemarket.app.databinding.ItemCommentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class SourceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySourceDetailsBinding
    private lateinit var sessionManager: SessionManager
    private var sourceId: Int = 0
    private var sourceZipUrl: String = ""
    private val comments = mutableListOf<CommentData>()
    private lateinit var commentAdapter: CommentAdapter
    private var currentUserId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        
        // اعمال تم روی کل صفحه
        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
            window.decorView.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
            window.decorView.setBackgroundResource(R.drawable.bg_gradient_light)
        }
        
        binding = ActivitySourceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceId = intent.getIntExtra("source_id", 0)
        
        commentAdapter = CommentAdapter(comments, currentUserId, { position -> showEditDialog(position) }, { position -> deleteComment(position) })
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnDownload.setOnClickListener {
            if (sourceZipUrl.isNotEmpty()) downloadFile(sourceZipUrl)
            else Toast.makeText(this, "لینک دانلود موجود نیست", Toast.LENGTH_SHORT).show()
        }

        binding.btnAddComment.setOnClickListener {
            showAddCommentDialog()
        }

        loadSourceDetails()
    }

    private fun showAddCommentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val etRating = dialogView.findViewById<EditText>(R.id.etRating)
        val etText = dialogView.findViewById<EditText>(R.id.etCommentText)

        MaterialAlertDialogBuilder(this)
            .setTitle("ثبت نظر و امتیاز")
            .setView(dialogView)
            .setPositiveButton("ارسال") { _, _ ->
                val rating = etRating.text.toString().toIntOrNull() ?: 0
                val text = etText.text.toString()
                if (text.isNotEmpty() && rating in 1..10) {
                    sendComment(text, rating)
                } else {
                    Toast.makeText(this, "امتیاز باید بین ۱ تا ۱۰ باشد و متن خالی نباشد", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun showEditDialog(position: Int) {
        val comment = comments[position]
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val etRating = dialogView.findViewById<EditText>(R.id.etRating)
        val etText = dialogView.findViewById<EditText>(R.id.etCommentText)

        etRating.setText(comment.rating.toString())
        etText.setText(comment.text)

        MaterialAlertDialogBuilder(this)
            .setTitle("ویرایش نظر")
            .setView(dialogView)
            .setPositiveButton("به‌روزرسانی") { _, _ ->
                val rating = etRating.text.toString().toIntOrNull() ?: 0
                val text = etText.text.toString()
                if (text.isNotEmpty() && rating in 1..10) {
                    editComment(comment.id, text, rating, position)
                } else {
                    Toast.makeText(this, "اطلاعات نامعتبر است", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun sendComment(text: String, rating: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().put("text", text).put("rating", rating).toString()
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment", payload, token) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                Toast.makeText(this@SourceDetailsActivity, "نظر شما ثبت شد", Toast.LENGTH_SHORT).show()
                loadSourceDetails()
            }
        }
    }

    private fun editComment(commentId: Int, text: String, rating: Int, position: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().put("text", text).put("rating", rating).toString()
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment/$commentId/edit", payload, token) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                Toast.makeText(this@SourceDetailsActivity, "نظر ویرایش شد", Toast.LENGTH_SHORT).show()
                loadSourceDetails()
            }
        }
    }

    private fun deleteComment(position: Int) {
        val comment = comments[position]
        val token = sessionManager.fetchAuthToken() ?: ""
        MaterialAlertDialogBuilder(this)
            .setTitle("حذف نظر")
            .setMessage("آیا از حذف این نظر مطمئن هستید؟")
            .setPositiveButton("بله، حذف کن") { _, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment/${comment.id}/delete", "{}", token) }
                    if (res != null && JSONObject(res).getBoolean("success")) {
                        Toast.makeText(this@SourceDetailsActivity, "نظر حذف شد", Toast.LENGTH_SHORT).show()
                        loadSourceDetails()
                    }
                }
            }
            .setNegativeButton("خیر", null)
            .show()
    }

    private fun downloadFile(relativeUrl: String) {
        val baseUrl = NativeLib.getBaseUrl()
        val fullUrl = if (relativeUrl.startsWith("http")) relativeUrl else baseUrl + relativeUrl
        binding.btnDownload.visibility = View.GONE
        binding.progressDownload.visibility = View.VISIBLE
        binding.tvDownloadPercent.visibility = View.VISIBLE
        binding.progressDownload.progress = 0

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val request = Request.Builder().url(fullUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed")
                    val body = response.body ?: throw IOException("Null response body")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L
                    val inputStream = body.byteStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int

                    val resolver = contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, "source_$sourceId.zip")
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/CodeMarketDownload")
                        }
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: throw IOException("Failed")
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    binding.progressDownload.progress = progress
                                    binding.tvDownloadPercent.text = "در حال دانلود... $progress%"
                                }
                            }
                        }
                        outputStream.flush()
                    } ?: throw IOException("Stream error")

                    withContext(Dispatchers.Main) {
                        binding.btnDownload.visibility = View.VISIBLE
                        binding.progressDownload.visibility = View.GONE
                        binding.tvDownloadPercent.visibility = View.GONE
                        Toast.makeText(this@SourceDetailsActivity, "دانلود با موفقیت انجام شد!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SourceDetailsActivity, "خطا در دانلود: ${e.message}", Toast.LENGTH_SHORT).show()
                    binding.btnDownload.visibility = View.VISIBLE
                    binding.progressDownload.visibility = View.GONE
                    binding.tvDownloadPercent.visibility = View.GONE
                }
            }
        }
    }

    private fun loadSourceDetails() {
        comments.clear()
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/source/$sourceId", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    val source = json.getJSONObject("source")
                    binding.tvDetailsName.text = source.getString("name")
                    binding.tvDetailsDesc.text = source.getString("description")
                    sourceZipUrl = source.optString("zip_file", "")
                    
                    val logoUrl = source.optString("logo", "")
                    if (logoUrl.isNotEmpty()) {
                        val baseUrl = NativeLib.getBaseUrl()
                        val fullLogoUrl = if (logoUrl.startsWith("http")) logoUrl else baseUrl + logoUrl
                        Glide.with(this@SourceDetailsActivity)
                            .load(fullLogoUrl)
                            .placeholder(R.drawable.ic_sun)
                            .into(binding.imgDetailsLogo)
                    }
                    
                    val arr = source.optJSONArray("comments") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        comments.add(CommentData(
                            c.getInt("id"),
                            c.getInt("user_id"),
                            c.getString("username"),
                            c.getString("text"),
                            c.optString("user_pic"),
                            c.optInt("rating", 0),
                            c.optBoolean("is_edited", false)
                        ))
                    }
                    commentAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}

data class CommentData(
    val id: Int,
    val userId: Int,
    val username: String,
    val text: String,
    val userPic: String,
    val rating: Int,
    val isEdited: Boolean
)

class CommentAdapter(
    private val items: List<CommentData>,
    private val currentUserId: Int,
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvCommentUser.text = item.username
        holder.b.tvCommentText.text = item.text
        holder.b.tvCommentRating.text = "${item.rating}/10"
        
        if (item.isEdited) {
            holder.b.tvEditedTag.visibility = View.VISIBLE
        } else {
            holder.b.tvEditedTag.visibility = View.GONE
        }

        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) {
            val picUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context)
                .load(picUrl)
                .placeholder(R.drawable.ic_sun)
                .into(holder.b.imgCommentUserPic)
        }

        if (item.userId == currentUserId) {
            holder.b.btnEditComment.visibility = View.VISIBLE
            holder.b.btnDeleteComment.visibility = View.VISIBLE
            holder.b.btnEditComment.setOnClickListener { onEditClick(position) }
            holder.b.btnDeleteComment.setOnClickListener { onDeleteClick(position) }
        } else {
            holder.b.btnEditComment.visibility = View.GONE
            holder.b.btnDeleteComment.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}