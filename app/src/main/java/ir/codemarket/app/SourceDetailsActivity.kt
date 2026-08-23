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
import io.noties.markwon.Markwon
import ir.codemarket.app.databinding.ActivitySourceDetailsBinding
import ir.codemarket.app.databinding.ItemCommentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class SourceDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySourceDetailsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    private var sourceId: Int = -1
    private var zipUrl: String = ""

    private val commentsList = mutableListOf<CommentItem>()
    private lateinit var commentsAdapter: CommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = Markwon.create(this)

        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }

        binding = ActivitySourceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sourceId = intent.getIntExtra("source_id", -1)
        if (sourceId == -1) {
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnDownload.setOnClickListener {
            if (zipUrl.isNotEmpty()) {
                downloadFile(zipUrl)
            } else {
                Toast.makeText(this, "لینک دانلود نامعتبر است", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnAddComment.setOnClickListener {
            showAddCommentDialog()
        }

        setupCommentsRecyclerView()
        loadSourceDetails()
    }

    private fun setupCommentsRecyclerView() {
        commentsAdapter = CommentsAdapter(commentsList, markwon)
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
                    
                    val desc = source.optString("description", "بدون توضیحات")
                    markwon.setMarkdown(binding.tvDetailsDesc, desc)

                    val logoUrl = source.optString("logo", "")
                    if (logoUrl.isNotEmpty()) {
                        val fullLogoUrl = if (logoUrl.startsWith("http")) logoUrl else NativeLib.getBaseUrl() + logoUrl
                        Glide.with(this@SourceDetailsActivity)
                            .load(fullLogoUrl)
                            .placeholder(R.drawable.ic_sun)
                            .into(binding.imgDetailsLogo)
                    } else {
                        binding.imgDetailsLogo.setImageResource(R.drawable.ic_sun)
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
            commentsList.add(
                CommentItem(
                    c.optInt("id", 0),
                    c.optString("username", "کاربر"),
                    c.optString("user_pic", ""),
                    c.optString("text", ""),
                    c.optInt("rating", 0),
                    c.optString("date", "")
                )
            )
        }
        commentsAdapter.notifyDataSetChanged()
    }

    private fun calculateMyketRatings(comments: JSONArray) {
        val total = comments.length()
        if (total == 0) {
            binding.tvAverageRating.text = "0.0"
            binding.pbRating5.progress = 0
            binding.pbRating4.progress = 0
            binding.pbRating3.progress = 0
            binding.pbRating2.progress = 0
            binding.pbRating1.progress = 0
            return
        }

        var sum = 0.0
        var r5 = 0; var r4 = 0; var r3 = 0; var r2 = 0; var r1 = 0

        for (i in 0 until total) {
            val c = comments.getJSONObject(i)
            val rating = c.optDouble("rating", 0.0)
            sum += rating

            when {
                rating >= 9 -> r5++
                rating >= 7 -> r4++
                rating >= 5 -> r3++
                rating >= 3 -> r2++
                else -> r1++
            }
        }

        val avg = sum / total
        binding.tvAverageRating.text = String.format(java.util.Locale.US, "%.1f", avg)

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

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("ارسال") { _, _ ->
                val ratingStr = etRating.text.toString()
                val text = etCommentText.text.toString()
                val rating = ratingStr.toIntOrNull() ?: 0

                if (text.isNotEmpty() && rating in 1..10) {
                    sendComment(text, rating)
                } else {
                    Toast.makeText(this, "امتیاز بین 1 تا 10 و متن الزامی است", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun sendComment(text: String, rating: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().apply {
            put("text", text)
            put("rating", rating)
        }.toString()

        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment", payload, token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    Toast.makeText(this@SourceDetailsActivity, "نظر شما ثبت شد", Toast.LENGTH_SHORT).show()
                    loadSourceDetails() 
                }
            }
        }
    }

    private fun downloadFile(url: String) {
        try {
            binding.tvDownloadPercent.visibility = View.VISIBLE
            binding.progressDownload.visibility = View.VISIBLE
            binding.tvDownloadPercent.text = "در حال شروع دانلود..."

            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(binding.tvDetailsName.text.toString())
                .setDescription("در حال دانلود فایل...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "source_${System.currentTimeMillis()}.zip")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            
            Toast.makeText(this, "دانلود در پس‌زمینه شروع شد", Toast.LENGTH_SHORT).show()
            binding.tvDownloadPercent.text = "دانلود در حال انجام..."
            binding.progressDownload.isIndeterminate = true
            
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در شروع دانلود", Toast.LENGTH_SHORT).show()
            binding.tvDownloadPercent.visibility = View.GONE
            binding.progressDownload.visibility = View.GONE
        }
    }
}

data class CommentItem(
    val id: Int,
    val username: String,
    val userPic: String,
    val text: String,
    val rating: Int,
    val date: String
)

class CommentsAdapter(
    private val items: List<CommentItem>,
    private val markwon: Markwon
) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.b.tvCommentUsername.text = item.username
        holder.b.tvCommentRating.text = item.rating.toString()
        holder.b.tvCommentDate.text = TimeUtils.getTimeAgo(item.date)
        
        markwon.setMarkdown(holder.b.tvCommentText, item.text)

        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) {
            val fullUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context)
                .load(fullUrl)
                .placeholder(R.drawable.ic_sun)
                .into(holder.b.imgCommentUser)
        } else {
            holder.b.imgCommentUser.setImageResource(R.drawable.ic_sun)
        }
    }

    override fun getItemCount(): Int = items.size
}