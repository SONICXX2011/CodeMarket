package ir.codemarket.app

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark) else setTheme(R.style.Theme_CodeMarket_Light)
        binding = ActivitySourceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceId = intent.getIntExtra("source_id", 0)
        commentAdapter = CommentAdapter(comments)
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentAdapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnDownload.setOnClickListener {
            if (sourceZipUrl.isNotEmpty()) downloadFile(sourceZipUrl)
            else Toast.makeText(this, "لینک دانلود موجود نیست", Toast.LENGTH_SHORT).show()
        }

        loadSourceDetails()

        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString()
            if (text.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = JSONObject().put("text", text).toString()
                    val token = sessionManager.fetchAuthToken() ?: ""
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment", payload, token) }
                    if (res != null && JSONObject(res).getBoolean("success")) {
                        binding.etComment.text?.clear()
                        loadSourceDetails()
                        Toast.makeText(this@SourceDetailsActivity, "کامنت ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
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
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
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
                        Glide.with(this@SourceDetailsActivity).load(fullLogoUrl).placeholder(R.drawable.ic_sun).into(binding.imgDetailsLogo)
                    }
                    
                    val arr = source.optJSONArray("comments") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        comments.add(CommentData(c.getString("username"), c.getString("text"), c.optString("user_pic")))
                    }
                    commentAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}

data class CommentData(val username: String, val text: String, val userPic: String)

class CommentAdapter(private val items: List<CommentData>) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
    
    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvCommentUser.text = item.username
        holder.b.tvCommentText.text = item.text
        
        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) {
            val picUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context).load(picUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgCommentUserPic)
        }
    }

    override fun getItemCount(): Int = items.size
}