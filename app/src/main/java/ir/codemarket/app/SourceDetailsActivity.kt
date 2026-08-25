package ir.codemarket.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

    private var currentUserId = -1
    private val commentsList = mutableListOf<CommentItem>()
    private lateinit var commentsAdapter: CommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = MarkdownUtils.createMarkwon(this)

        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }

        binding = ActivitySourceDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_light)
        }

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

        binding.btnAddComment.setOnClickListener { showAddCommentDialog(null) }

        extractUserId()
        setupCommentsRecyclerView()
        loadSourceDetails()
    }

    private fun extractUserId() {
        val token = sessionManager.fetchAuthToken() ?: return
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                currentUserId = JSONObject(payload).getInt("user_id")
            }
        } catch (e: Exception) { }
    }

    private fun openUserProfile(userId: Int) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
    }

    private fun setupCommentsRecyclerView() {
        commentsAdapter = CommentsAdapter(commentsList, markwon, sessionManager.getTextSize(), currentUserId,
            onOptionsClick = { view, comment, pos -> showCommentOptions(view, comment, pos) },
            onReplyClick = { comment -> showAddCommentDialog(comment) },
            onUserClick = { userId -> openUserProfile(userId) }
        )
        binding.recyclerComments.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false
        }
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
                    MarkdownUtils.setMarkdownText(markwon, binding.tvDetailsDesc, source.optString("description", "بدون توضیحات"))

                    val logoUrl = source.optString("logo", "")
                    if (logoUrl.isNotEmpty()) {
                        val fullLogoUrl = if (logoUrl.startsWith("http")) logoUrl else NativeLib.getBaseUrl() + logoUrl
                        Glide.with(this@SourceDetailsActivity).load(fullLogoUrl).placeholder(R.drawable.ic_sun).into(binding.imgDetailsLogo)
                    }

                    val rawZipUrl = source.optString("zip_file", "")
                    zipUrl = if (rawZipUrl.startsWith("http")) rawZipUrl else NativeLib.getBaseUrl() + rawZipUrl

                    val screenshotsArray = source.optJSONArray("screenshots") ?: JSONArray()
                    val screenshotsList = mutableListOf<String>()
                    for (i in 0 until screenshotsArray.length()) {
                        screenshotsList.add(screenshotsArray.getString(i))
                    }

                    if (screenshotsList.isNotEmpty()) {
                        binding.tvScreenshotsTitle.visibility = View.VISIBLE
                        binding.recyclerScreenshots.visibility = View.VISIBLE
                        binding.recyclerScreenshots.layoutManager = LinearLayoutManager(this@SourceDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
                        binding.recyclerScreenshots.adapter = ScreenshotsAdapter(screenshotsList) { position ->
                            showFullScreenImages(screenshotsList, position)
                        }
                    } else {
                        binding.tvScreenshotsTitle.visibility = View.GONE
                        binding.recyclerScreenshots.visibility = View.GONE
                    }

                    val commentsArray = source.optJSONArray("comments") ?: JSONArray()
                    parseComments(commentsArray)
                    calculateMyketRatings(commentsArray)
                }
            }
        }
    }

    private fun showFullScreenImages(imageUrls: List<String>, initialPosition: Int) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        
        val recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(this@SourceDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
        }
        
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)

        recyclerView.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val img = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                return object : RecyclerView.ViewHolder(img) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val iv = holder.itemView as ImageView
                val fullUrl = if (imageUrls[position].startsWith("http")) imageUrls[position] else NativeLib.getBaseUrl() + imageUrls[position]
                Glide.with(iv.context).load(fullUrl).into(iv)
            }
            override fun getItemCount() = imageUrls.size
        }

        val closeBtn = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(Color.WHITE)
            setBackgroundResource(R.drawable.bg_circle_glass)
            setPadding(30, 30, 30, 30)
            setOnClickListener { dialog.dismiss() }
        }
        
        val frameLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(recyclerView)
            
            val params = FrameLayout.LayoutParams(120, 120)
            params.gravity = Gravity.TOP or Gravity.START
            params.setMargins(48, 48, 48, 0)
            addView(closeBtn, params)
        }

        dialog.setContentView(frameLayout)
        dialog.show()
        recyclerView.scrollToPosition(initialPosition)
    }

    private fun parseComments(array: JSONArray) {
        commentsList.clear()
        for (i in 0 until array.length()) {
            val c = array.getJSONObject(i)
            commentsList.add(
                CommentItem(
                    c.optInt("id", 0), c.optInt("user_id", -1), c.optString("username", "کاربر"),
                    c.optString("user_pic", ""), c.optString("text", ""), c.optInt("rating", 0),
                    c.optString("date", ""), c.optBoolean("is_vip", false), c.optString("badge_url", ""),
                    c.optString("custom_bg", ""), c.optInt("reply_to_id", -1),
                    c.optString("reply_to_username", ""), c.optString("reply_to_text", "")
                )
            )
        }
        commentsAdapter.notifyDataSetChanged()
    }

    private fun calculateMyketRatings(comments: JSONArray) {
        val total = comments.length()
        binding.tvTotalRatings.text = "از $total نظر"
        if (total == 0) {
            binding.tvAverageRating.text = "0.0"
            binding.ratingBarStars.rating = 0f
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
        val avg = sum / total
        binding.tvAverageRating.text = String.format(java.util.Locale.US, "%.1f", avg)
        binding.ratingBarStars.rating = (avg / 2).toFloat()
        binding.pbRating5.progress = (r5 * 100) / total; binding.pbRating4.progress = (r4 * 100) / total; binding.pbRating3.progress = (r3 * 100) / total; binding.pbRating2.progress = (r2 * 100) / total; binding.pbRating1.progress = (r1 * 100) / total
    }

    private fun showAddCommentDialog(replyTo: CommentItem?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val etRating = dialogView.findViewById<EditText>(R.id.etRating)
        val etCommentText = dialogView.findViewById<EditText>(R.id.etCommentText)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmitDialog)
        val tvHeader = dialogView.findViewById<TextView>(R.id.tvReplyHeader)

        MarkdownUtils.applyMarkdownShortcuts(etCommentText)

        if (replyTo != null) {
            tvHeader.text = "پاسخ به ${replyTo.username}"
            etRating.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSubmit.setOnClickListener {
            val rating = if (replyTo != null) 0 else (etRating.text.toString().toIntOrNull() ?: 0)
            val text = etCommentText.text.toString()

            if (text.isNotEmpty() && (replyTo != null || rating in 1..10)) {
                dialog.dismiss()
                sendComment(text, rating, replyTo?.id)
            } else {
                Toast.makeText(this, "امتیاز و متن الزامی است", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun sendComment(text: String, rating: Int, replyToId: Int?) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().apply {
            put("text", text)
            put("rating", rating)
            if (replyToId != null) put("reply_to_id", replyToId)
        }.toString()

        CoroutineScope(Dispatchers.Main).launch {
            val (res, code) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment", payload, token) }
            if (code == 429) {
                Toast.makeText(this@SourceDetailsActivity, "بیش از حد مجاز! یک دقیقه صبر کنید.", Toast.LENGTH_LONG).show()
                return@launch
            }
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

    private fun showCommentOptions(view: View, comment: CommentItem, position: Int) {
        val popup = PopupMenu(this, view)
        popup.menu.add("ویرایش")
        popup.menu.add("حذف")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "حذف" -> deleteComment(comment.id)
                "ویرایش" -> editCommentDialog(comment)
            }
            true
        }
        popup.show()
    }

    private fun deleteComment(commentId: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment/$commentId/delete", "{}", token) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                Toast.makeText(this@SourceDetailsActivity, "کامنت حذف شد", Toast.LENGTH_SHORT).show()
                loadSourceDetails()
            }
        }
    }

    private fun editCommentDialog(comment: CommentItem) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_comment, null)
        val etRating = dialogView.findViewById<EditText>(R.id.etRating)
        val etCommentText = dialogView.findViewById<EditText>(R.id.etCommentText)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmitDialog)

        etRating.setText(comment.rating.toString())
        etCommentText.setText(comment.text)
        MarkdownUtils.applyMarkdownShortcuts(etCommentText)

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnSubmit.setOnClickListener {
            val rating = etRating.text.toString().toIntOrNull() ?: 0
            val text = etCommentText.text.toString()

            if (text.isNotEmpty() && rating in 1..10) {
                dialog.dismiss()
                val token = sessionManager.fetchAuthToken() ?: ""
                val payload = JSONObject().apply { put("text", text); put("rating", rating) }.toString()
                CoroutineScope(Dispatchers.Main).launch {
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/source/$sourceId/comment/${comment.id}/edit", payload, token) }
                    if (res != null && JSONObject(res).getBoolean("success")) loadSourceDetails()
                }
            } else { Toast.makeText(this, "مشخصات نامعتبر", Toast.LENGTH_SHORT).show() }
        }
        dialog.show()
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
        } catch (e: Exception) { Toast.makeText(this, "خطا در شروع دانلود", Toast.LENGTH_SHORT).show() }
    }
}

class ScreenshotsAdapter(
    private val items: List<String>, 
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<ScreenshotsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgScreenshot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_screenshot, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fullUrl = if (items[position].startsWith("http")) items[position] else NativeLib.getBaseUrl() + items[position]
        Glide.with(holder.itemView.context).load(fullUrl).placeholder(R.drawable.ic_sun).into(holder.img)
        holder.itemView.setOnClickListener { onClick(position) }
    }

    override fun getItemCount(): Int = items.size
}

data class CommentItem(
    val id: Int, val userId: Int, val username: String, val userPic: String, val text: String,
    val rating: Int, val date: String, val isVip: Boolean, val badgeUrl: String, val customBg: String,
    val replyToId: Int, val replyToUsername: String, val replyToText: String
)

class CommentsAdapter(
    private val items: List<CommentItem>, 
    private val markwon: Markwon, 
    private val size: Float, 
    private val currentUserId: Int,
    private val onOptionsClick: (View, CommentItem, Int) -> Unit,
    private val onReplyClick: (CommentItem) -> Unit,
    private val onUserClick: (Int) -> Unit
) : RecyclerView.Adapter<CommentsAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvCommentUsername.text = item.username
        if (item.rating > 0) {
            holder.b.tvCommentRating.visibility = View.VISIBLE
            holder.b.tvCommentRating.text = item.rating.toString()
        } else {
            holder.b.tvCommentRating.visibility = View.GONE
        }

        holder.b.tvCommentDate.text = TimeUtils.getTimeAgo(item.date)
        
        holder.b.tvCommentText.textSize = size
        MarkdownUtils.setMarkdownText(markwon, holder.b.tvCommentText, item.text)

        if (item.replyToId != -1 && item.replyToUsername.isNotEmpty()) {
            holder.b.layoutReplyQuote.visibility = View.VISIBLE
            holder.b.tvReplyUsernameQuote.text = item.replyToUsername
            holder.b.tvReplyTextQuote.text = item.replyToText
        } else {
            holder.b.layoutReplyQuote.visibility = View.GONE
        }

        if (item.userId == currentUserId && currentUserId != -1) {
            holder.b.btnCommentOptions.visibility = View.VISIBLE
            holder.b.btnCommentOptions.setOnClickListener { onOptionsClick(it, item, position) }
        } else {
            holder.b.btnCommentOptions.visibility = View.GONE
        }
        
        holder.b.btnReply.setOnClickListener { onReplyClick(item) }

        holder.b.imgCommentUser.setOnClickListener { onUserClick(item.userId) }
        holder.b.tvCommentUsername.setOnClickListener { onUserClick(item.userId) }

        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) {
            val fullUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context).load(fullUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgCommentUser)
        } else {
            holder.b.imgCommentUser.setImageResource(R.drawable.ic_sun)
        }

        if (item.isVip && item.badgeUrl.isNotEmpty()) {
            holder.b.imgBadge.visibility = View.VISIBLE
            Glide.with(holder.b.root.context).load(if (item.badgeUrl.startsWith("http")) item.badgeUrl else baseUrl + item.badgeUrl).into(holder.b.imgBadge)
        } else {
            holder.b.imgBadge.visibility = View.GONE
        }

        val typedValue = TypedValue()
        holder.b.root.context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        
        if (item.isVip && item.customBg.isNotEmpty()) {
            try { 
                holder.b.cardComment.setCardBackgroundColor(Color.parseColor(item.customBg))
                holder.b.cardComment.cardElevation = 0f
                if (holder.b.cardComment is MaterialCardView) {
                    (holder.b.cardComment as MaterialCardView).strokeWidth = 0 
                }
            } catch(e: Exception) {
                holder.b.cardComment.setCardBackgroundColor(typedValue.data)
                holder.b.cardComment.cardElevation = 4f
                if (holder.b.cardComment is MaterialCardView) {
                    (holder.b.cardComment as MaterialCardView).strokeWidth = 2
                }
            }
        } else {
            holder.b.cardComment.setCardBackgroundColor(typedValue.data)
            holder.b.cardComment.cardElevation = 4f
            if (holder.b.cardComment is MaterialCardView) {
                (holder.b.cardComment as MaterialCardView).strokeWidth = 2
            }
        }
    }
    
    override fun getItemCount(): Int = items.size
}