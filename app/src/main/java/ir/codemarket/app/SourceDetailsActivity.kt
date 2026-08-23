package ir.codemarket.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import com.google.android.material.button.MaterialButton
import io.noties.markwon.Markwon
import ir.codemarket.app.databinding.ActivitySourceDetailsBinding
import ir.codemarket.app.databinding.ItemCommentBinding
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class SourceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySourceDetailsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    private var sourceId: Int = -1
    private var zipUrl: String = ""

    private var downloadId: Long = -1
    private var isDownloading = false
    private var progressJob: Job? = null

    private val commentsList = mutableListOf<CommentItem>()
    private lateinit var commentsAdapter: CommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = Markwon.create(this)

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivitySourceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceId = intent.getIntExtra("source_id", -1)
        if (sourceId == -1) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnDownload.setOnClickListener {
            if (zipUrl.isNotEmpty() && !isDownloading) {
                downloadFile(zipUrl)
            } else if (zipUrl.isEmpty()) {
                Toast.makeText(this, "لینک دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCancelDownload.setOnClickListener {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.remove(downloadId)
            isDownloading = false
            progressJob?.cancel()
            binding.tvDownloadPercent.text = "دانلود متوقف شد."
            binding.btnCancelDownload.visibility = View.GONE
            binding.progressDownload.progress = 0
        }

        binding.btnAddComment.setOnClickListener { showAddCommentDialog() }

        setupCommentsRecyclerView()
        loadSourceDetails()
    }

    private fun setupCommentsRecyclerView() {
        commentsAdapter = CommentsAdapter(commentsList, markwon, sessionManager.getTextSize())
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentsAdapter
    }

    private fun loadSourceDetails() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/source/$sourceId", token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val source = json.getJSONObject("source")
                    binding.tvDetailsName.text = source.optString("name", "نامشخص")
                    
                    binding.tvDetailsDesc.textSize = sessionManager.getTextSize()
                    markwon.setMarkdown(binding.tvDetailsDesc, source.optString("description", "بدون توضیحات"))

                    val logoUrl = source.optString("logo", "")
                    if (logoUrl.isNotEmpty()) {
                        val fullLogoUrl = if (logoUrl.startsWith("http")) logoUrl else NativeLib.getBaseUrl() + logoUrl
                        Glide.with(this@SourceDetailsActivity).load(fullLogoUrl).placeholder(R.drawable.ic_sun).into(binding.imgDetailsLogo)
                    }

                    val rawZipUrl = source.optString("zip_file", "")
                    zipUrl = if (rawZipUrl.startsWith("http")) rawZipUrl else NativeLib.getBaseUrl() + rawZipUrl

                    val commentsArray = source.optJSONArray("comments") ?: JSONArray()
                    parseComments(commentsArray)
                    calculateMyketRatings(commentsArray)
                }
            }
        }
    }

    private fun parseComments(array: JSONArray) {
        commentsList.clear()
        for (i in 0 until array.length()) {
            val c = array.getJSONObject(i)
            commentsList.add(CommentItem(c.optInt("id", 0), c.optString("username", "کاربر"), c.optString("user_pic", ""), c.optString("text", ""), c.optInt("rating", 0), c.optString("date", "")))
        }
        commentsAdapter.notifyDataSetChanged()
    }

    private fun calculateMyketRatings(comments: JSONArray) {
        val total = comments.length()
        if (total == 0) {
            binding.tvAverageRating.text = "0.0"
            binding.pbRating5.progress = 0; binding.pbRating4.progress = 0; binding.pbRating3.progress = 0; binding.pbRating2.progress = 0; binding.pbRating1.progress = 0
            return
        }

        var sum = 0.0; var r5 = 0; var r4 = 0; var r3 = 0; var r2 = 0; var r1 = 0
        for (i in 0 until total) {
            val rating = comments.getJSONObject(i).optDouble("rating", 0.0)
            sum += rating
            when {
                rating >= 9 -> r5++; rating >= 7 -> r4++; rating >= 5 -> r3++; rating >= 3 -> r2++; else -> r1++
            }
        }
        binding.tvAverageRating.text = String.format(java.util.Locale.US, "%.1f", sum / total)
        binding.pbRating5.progress = (r5 * 100) / total
        binding.pbRating4.progress = (r4 * 100) / total
        binding.pbRating3.progress = (r3 * 100) / total
        binding.pbRating2.progress = (r2 * 100) / total
        binding.pbRating1.progress = (r1 * 100) / total
    }

    private fun showAddCommentDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val etRating = dialogView.findViewById<EditText>(R.id.etRating)
        val etCommentText = dialogView.findViewById<EditText>(R.id.etCommentText)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmitDialog)

        MarkdownUtils.applyMarkdownShortcuts(etCommentText)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Device_Default_Dialog_NoActionBar_MinWidth)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSubmit.setOnClickListener {
            val rating = etRating.text.toString().toIntOrNull() ?: 0
            val text = etCommentText.text.toString()

            if (text.isNotEmpty() && rating in 1..10) {
                dialog.dismiss()
                sendComment(text, rating)
            } else {
                Toast.makeText(this, "امتیاز بین 1 تا 10 و متن الزامی است", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun sendComment(text: String, rating: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().apply { put("text", text); put("rating", rating) }.toString()

        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment", payload, token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    Toast.makeText(this@SourceDetailsActivity, "نظر شما ثبت شد", Toast.LENGTH_SHORT).show()
                    loadSourceDetails() 
                } else {
                    Toast.makeText(this@SourceDetailsActivity, json.optString("error", "خطا در ثبت"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun downloadFile(url: String) {
        try {
            binding.tvDownloadPercent.visibility = View.VISIBLE
            binding.progressDownload.visibility = View.VISIBLE
            binding.btnCancelDownload.visibility = View.VISIBLE
            binding.progressDownload.progress = 0
            binding.tvDownloadPercent.text = "در حال اتصال..."

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(binding.tvDetailsName.text.toString())
                .setDescription("در حال دانلود فایل...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "source_${System.currentTimeMillis()}.zip")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = manager.enqueue(request)
            isDownloading = true
            
            progressJob = CoroutineScope(Dispatchers.Main).launch {
                while(isDownloading) {
                    val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            isDownloading = false
                            binding.tvDownloadPercent.text = "دانلود با موفقیت تمام شد!"
                            binding.progressDownload.progress = 100
                            binding.btnCancelDownload.visibility = View.GONE
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            isDownloading = false
                            binding.tvDownloadPercent.text = "خطا در دانلود"
                            binding.btnCancelDownload.visibility = View.GONE
                        } else {
                            val d = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val t = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (t > 0) {
                                val p = (d * 100L) / t
                                binding.tvDownloadPercent.text = "در حال دانلود... $p%"
                                binding.progressDownload.isIndeterminate = false
                                binding.progressDownload.progress = p.toInt()
                            }
                        }
                        cursor.close()
                    }
                    delay(500)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در شروع دانلود", Toast.LENGTH_SHORT).show()
        }
    }
}

data class CommentItem(val id: Int, val username: String, val userPic: String, val text: String, val rating: Int, val date: String)

class CommentsAdapter(private val items: List<CommentItem>, private val markwon: Markwon, private val size: Float) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvCommentUsername.text = item.username
        holder.b.tvCommentRating.text = item.rating.toString()
        holder.b.tvCommentDate.text = TimeUtils.getTimeAgo(item.date)
        
        holder.b.tvCommentText.textSize = size
        markwon.setMarkdown(holder.b.tvCommentText, item.text)

        if (item.userPic.isNotEmpty()) {
            val fullUrl = if (item.userPic.startsWith("http")) item.userPic else NativeLib.getBaseUrl() + item.userPic
            Glide.with(holder.b.root.context).load(fullUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgCommentUser)
        } else holder.b.imgCommentUser.setImageResource(R.drawable.ic_sun)
    }
    override fun getItemCount(): Int = items.size
}