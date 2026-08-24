package ir.codemarket.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import ir.codemarket.app.databinding.ActivityPostDetailsBinding
import ir.codemarket.app.databinding.ItemCommentBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PostDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    private var postId: Int = -1

    private val commentsList = mutableListOf<PostCommentItem>()
    private lateinit var commentsAdapter: PostCommentsAdapter

    private var replyToId: Int? = null
    private var selectedMediaUri: Uri? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedMediaUri = it
            binding.layoutMediaPreview.visibility = View.VISIBLE
            Glide.with(this).load(it).into(binding.imgMediaPreview)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = MarkdownUtils.createMarkwon(this) 

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityPostDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_light)
        }

        postId = intent.getIntExtra("post_id", -1)
        if (postId == -1) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }

        MarkdownUtils.applyMarkdownShortcuts(binding.etComment)

        binding.btnAttach.setOnClickListener { pickMedia.launch(arrayOf("image/*", "video/*")) }

        binding.btnCancelMedia.setOnClickListener {
            selectedMediaUri = null
            binding.layoutMediaPreview.visibility = View.GONE
            binding.imgMediaPreview.setImageDrawable(null)
        }

        binding.btnCancelReply.setOnClickListener {
            replyToId = null
            binding.layoutReplyPreview.visibility = View.GONE
        }

        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString()
            if (text.isNotEmpty() || selectedMediaUri != null) {
                uploadCommentWithMedia(text, selectedMediaUri)
            } else {
                Toast.makeText(this, "متن یا فایل الزامی است", Toast.LENGTH_SHORT).show()
            }
        }

        setupCommentsRecyclerView()
        loadPostDetails()
    }

    private fun setupCommentsRecyclerView() {
        commentsAdapter = PostCommentsAdapter(commentsList, markwon, sessionManager.getTextSize(),
            onUserClick = { userId -> openUserProfile(userId) },
            onReplyClick = { comment ->
                replyToId = comment.id
                binding.layoutReplyPreview.visibility = View.VISIBLE
                binding.tvReplyPreviewName.text = comment.username
                binding.tvReplyPreviewText.text = comment.text
                binding.etComment.requestFocus()
            }
        )
        binding.recyclerComments.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false
        }
        binding.recyclerComments.adapter = commentsAdapter
    }

    private fun openUserProfile(userId: Int) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
    }

    private fun loadPostDetails() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed/$postId/comments", token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val post = json.getJSONObject("post")
                    
                    val postUserId = post.optInt("user_id", -1)
                    binding.tvUsername.text = post.optString("username", "کاربر")
                    
                    binding.imgUserPic.setOnClickListener { openUserProfile(postUserId) }
                    binding.tvUsername.setOnClickListener { openUserProfile(postUserId) }
                    
                    binding.tvPostText.textSize = sessionManager.getTextSize()
                    MarkdownUtils.setMarkdownText(markwon, binding.tvPostText, post.optString("text", ""))
                    
                    binding.tvLikeCount.text = post.optInt("like_count", 0).toString()
                    binding.tvViewsCount.text = post.optInt("views", 0).toString()

                    val isLiked = post.optBoolean("is_liked", false)
                    if (isLiked) {
                        binding.imgLike.setColorFilter(ContextCompat.getColor(this@PostDetailsActivity, R.color.purple))
                        binding.tvLikeCount.setTextColor(ContextCompat.getColor(this@PostDetailsActivity, R.color.purple))
                    } else {
                        binding.imgLike.setColorFilter(Color.parseColor("#80888888"))
                        binding.tvLikeCount.setTextColor(Color.parseColor("#80888888"))
                    }

                    val baseUrl = NativeLib.getBaseUrl()
                    val userPic = post.optString("user_pic", "")
                    if (userPic.isNotEmpty()) {
                        val fullPicUrl = if (userPic.startsWith("http")) userPic else baseUrl + userPic
                        Glide.with(this@PostDetailsActivity).load(fullPicUrl).placeholder(R.drawable.ic_sun).into(binding.imgUserPic)
                    } else {
                        binding.imgUserPic.setImageResource(R.drawable.ic_sun)
                    }

                    val media = post.optString("media", "")
                    val mediaType = post.optString("media_type", "")
                    if (media.isNotEmpty() && mediaType == "image") {
                        val fullMediaUrl = if (media.startsWith("http")) media else baseUrl + media
                        Glide.with(this@PostDetailsActivity).load(fullMediaUrl).into(binding.imgPostMedia)
                        binding.imgPostMedia.visibility = View.VISIBLE
                        
                        binding.imgPostMedia.setOnClickListener {
                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.setDataAndType(Uri.parse(fullMediaUrl), "image/*")
                            startActivity(intent)
                        }
                    } else {
                        binding.imgPostMedia.visibility = View.GONE
                    }

                    val commentsArray = json.optJSONArray("comments") ?: JSONArray()
                    parseComments(commentsArray)
                }
            }
        }
    }

    private fun parseComments(array: JSONArray) {
        commentsList.clear()
        for (i in 0 until array.length()) {
            val c = array.getJSONObject(i)
            commentsList.add(PostCommentItem(
                c.optInt("id", 0), 
                c.optInt("user_id", -1), 
                c.optString("username", "کاربر"), 
                c.optString("user_pic", ""), 
                c.optString("text", ""), 
                c.optString("date", ""),
                c.optString("media", ""),
                c.optString("media_type", ""),
                c.optInt("reply_to_id", -1),
                c.optString("reply_to_username", ""),
                c.optString("reply_to_text", ""),
                c.optBoolean("is_vip", false),
                c.optString("badge_url", ""),
                c.optString("custom_bg", "")
            ))
        }
        commentsAdapter.notifyDataSetChanged()
    }

    private fun uploadCommentWithMedia(text: String, mediaUri: Uri?) {
        binding.btnSendComment.isEnabled = false
        val token = sessionManager.fetchAuthToken() ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(NativeLib.getBaseUrl() + "/api/feed/comment")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.doOutput = true

                if (mediaUri == null) {
                    connection.setRequestProperty("Content-Type", "application/json")
                    val payload = JSONObject().apply {
                        put("post_id", postId)
                        put("text", text)
                        if (replyToId != null) put("reply_to_id", replyToId)
                    }.toString()
                    connection.outputStream.write(payload.toByteArray())
                } else {
                    val boundary = "Boundary-" + System.currentTimeMillis()
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    val outputStream = DataOutputStream(connection.outputStream)

                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"post_id\"\r\n\r\n$postId\r\n")
                    
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"text\"\r\n\r\n$text\r\n")
                    
                    if (replyToId != null) {
                        outputStream.writeBytes("--$boundary\r\n")
                        outputStream.writeBytes("Content-Disposition: form-data; name=\"reply_to_id\"\r\n\r\n$replyToId\r\n")
                    }

                    val mimeType = contentResolver.getType(mediaUri) ?: "application/octet-stream"
                    val ext = if (mimeType.contains("video")) "mp4" else "jpg"
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"media\"; filename=\"upload.$ext\"\r\n")
                    outputStream.writeBytes("Content-Type: $mimeType\r\n\r\n")
                    
                    contentResolver.openInputStream(mediaUri)?.use { input -> input.copyTo(outputStream) }
                    outputStream.writeBytes("\r\n--$boundary--\r\n")
                    outputStream.flush()
                }

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    binding.btnSendComment.isEnabled = true
                    if (responseCode in 200..299) {
                        binding.etComment.setText("")
                        binding.btnCancelMedia.performClick()
                        binding.btnCancelReply.performClick()
                        loadPostDetails()
                    } else if (responseCode == 429) {
                        Toast.makeText(this@PostDetailsActivity, "بیش از حد مجاز! یک دقیقه صبر کنید.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@PostDetailsActivity, "خطا در ارسال کامنت", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSendComment.isEnabled = true
                    Toast.makeText(this@PostDetailsActivity, "خطای شبکه", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

data class PostCommentItem(
    val id: Int, val userId: Int, val username: String, val userPic: String, val text: String, val date: String,
    val media: String, val mediaType: String, val replyToId: Int, val replyToUsername: String, val replyToText: String,
    val isVip: Boolean, val badgeUrl: String, val customBg: String
)

class PostCommentsAdapter(
    private val items: List<PostCommentItem>, 
    private val markwon: Markwon, 
    private val size: Float,
    private val onUserClick: (Int) -> Unit,
    private val onReplyClick: (PostCommentItem) -> Unit
) : RecyclerView.Adapter<PostCommentsAdapter.ViewHolder>() {
    
    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val baseUrl = NativeLib.getBaseUrl()
        
        holder.b.tvCommentUsername.text = item.username
        holder.b.tvCommentDate.text = TimeUtils.getTimeAgo(item.date)
        holder.b.tvCommentRating.visibility = View.GONE 
        holder.b.btnCommentOptions.visibility = View.GONE
        
        holder.b.tvCommentText.textSize = size
        MarkdownUtils.setMarkdownText(markwon, holder.b.tvCommentText, item.text)

        // ارورهای tvReplyInfo اینجا با جایگزینی با layoutReplyQuote برطرف شد
        if (item.replyToId != -1 && item.replyToUsername.isNotEmpty()) {
            holder.b.layoutReplyQuote.visibility = View.VISIBLE
            holder.b.tvReplyUsernameQuote.text = item.replyToUsername
            holder.b.tvReplyTextQuote.text = item.replyToText
        } else {
            holder.b.layoutReplyQuote.visibility = View.GONE
        }

        if (item.media.isNotEmpty()) {
            holder.b.layoutMedia.visibility = View.VISIBLE
            val fullMediaUrl = if (item.media.startsWith("http")) item.media else baseUrl + item.media
            Glide.with(holder.b.root.context).load(fullMediaUrl).into(holder.b.imgCommentMedia)
            
            if (item.mediaType == "video") {
                holder.b.imgPlayVideo.visibility = View.VISIBLE
                holder.b.tvMediaInfo.visibility = View.VISIBLE
                holder.b.tvMediaInfo.text = "ویدیو" 
            } else {
                holder.b.imgPlayVideo.visibility = View.GONE
                holder.b.tvMediaInfo.visibility = View.GONE
            }

            holder.b.imgCommentMedia.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setDataAndType(Uri.parse(fullMediaUrl), if (item.mediaType == "video") "video/*" else "image/*")
                holder.b.root.context.startActivity(intent)
            }
        } else {
            holder.b.layoutMedia.visibility = View.GONE
        }

        holder.b.btnReply.setOnClickListener { onReplyClick(item) }

        holder.b.imgCommentUser.setOnClickListener { onUserClick(item.userId) }
        holder.b.tvCommentUsername.setOnClickListener { onUserClick(item.userId) }

        if (item.userPic.isNotEmpty()) {
            val fullUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context).load(fullUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgCommentUser)
        } else holder.b.imgCommentUser.setImageResource(R.drawable.ic_sun)

        if (item.isVip && item.badgeUrl.isNotEmpty()) {
            holder.b.imgBadge.visibility = View.VISIBLE
            Glide.with(holder.b.root.context).load(if (item.badgeUrl.startsWith("http")) item.badgeUrl else baseUrl + item.badgeUrl).into(holder.b.imgBadge)
        } else holder.b.imgBadge.visibility = View.GONE

        if (item.isVip && item.customBg.isNotEmpty()) {
            try { holder.b.cardComment.setCardBackgroundColor(Color.parseColor(item.customBg)) } catch(e: Exception) {}
        }
    }
    override fun getItemCount(): Int = items.size
}