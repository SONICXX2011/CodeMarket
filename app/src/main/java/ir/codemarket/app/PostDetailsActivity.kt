package ir.codemarket.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

class PostDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostDetailsBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    private var postId: Int = -1

    private val commentsList = mutableListOf<PostCommentItem>()
    private lateinit var commentsAdapter: PostCommentsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = Markwon.create(this)

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityPostDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getIntExtra("post_id", -1)
        if (postId == -1) { finish(); return }

        binding.btnBack.setOnClickListener { finish() }

        // فعال کردن منوی مارک‌داون روی ورودی کامنت
        MarkdownUtils.applyMarkdownShortcuts(binding.etComment)

        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString()
            if (text.isNotEmpty()) {
                sendComment(text)
            } else {
                Toast.makeText(this, "متن کامنت خالی است", Toast.LENGTH_SHORT).show()
            }
        }

        setupCommentsRecyclerView()
        loadPostDetails()
    }

    private fun setupCommentsRecyclerView() {
        commentsAdapter = PostCommentsAdapter(commentsList, markwon, sessionManager.getTextSize())
        binding.recyclerComments.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false // رفع باگ نمایش 1 آیتم
        }
        binding.recyclerComments.adapter = commentsAdapter
    }

    private fun loadPostDetails() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed/$postId/comments", token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val post = json.getJSONObject("post")
                    
                    binding.tvUsername.text = post.optString("username", "کاربر")
                    
                    binding.tvPostText.textSize = sessionManager.getTextSize()
                    val text = post.optString("text", "")
                    markwon.setMarkdown(binding.tvPostText, text)
                    
                    binding.tvLikeCount.text = post.optInt("like_count", 0).toString()
                    binding.tvViewsCount.text = post.optInt("views", 0).toString()

                    val isLiked = post.optBoolean("is_liked", false)
                    if (isLiked) {
                        binding.imgLike.setColorFilter(ContextCompat.getColor(this@PostDetailsActivity, R.color.purple))
                        binding.tvLikeCount.setTextColor(ContextCompat.getColor(this@PostDetailsActivity, R.color.purple))
                    } else {
                        binding.imgLike.setColorFilter(Color.parseColor("#80FFFFFF"))
                        binding.tvLikeCount.setTextColor(Color.parseColor("#80FFFFFF"))
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
            commentsList.add(PostCommentItem(c.optInt("id", 0), c.optString("username", "کاربر"), c.optString("user_pic", ""), c.optString("text", ""), c.optString("date", "")))
        }
        commentsAdapter.notifyDataSetChanged()
    }

    private fun sendComment(text: String) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().apply { put("post_id", postId); put("text", text) }.toString()

        binding.btnSendComment.isEnabled = false
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/feed/comment", payload, token) }
            binding.btnSendComment.isEnabled = true
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    binding.etComment.setText("") // رفع باگ clear()
                    loadPostDetails() 
                }
            } else {
                Toast.makeText(this@PostDetailsActivity, "خطا در ارسال کامنت", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class PostCommentItem(val id: Int, val username: String, val userPic: String, val text: String, val date: String)

class PostCommentsAdapter(private val items: List<PostCommentItem>, private val markwon: Markwon, private val size: Float) : RecyclerView.Adapter<PostCommentsAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvCommentUsername.text = item.username
        holder.b.tvCommentDate.text = TimeUtils.getTimeAgo(item.date)
        holder.b.tvCommentRating.visibility = View.GONE 
        
        holder.b.tvCommentText.textSize = size
        markwon.setMarkdown(holder.b.tvCommentText, item.text)

        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) {
            val fullUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context).load(fullUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgCommentUser)
        } else {
            holder.b.imgCommentUser.setImageResource(R.drawable.ic_sun)
        }
    }
    override fun getItemCount(): Int = items.size
}