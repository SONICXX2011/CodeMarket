package ir.codemarket.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private var postId: Int = 0
    private val comments = mutableListOf<CommentData>()
    private lateinit var commentAdapter: CommentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
            window.decorView.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
            window.decorView.setBackgroundResource(R.drawable.bg_gradient_light)
        }
        
        binding = ActivityPostDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getIntExtra("post_id", 0)
        
        commentAdapter = CommentAdapter(comments, { }, { })
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentAdapter
        binding.recyclerComments.isNestedScrollingEnabled = false

        binding.btnBack.setOnClickListener { 
            finish() 
        }

        loadPostDetails()

        binding.btnSendComment.setOnClickListener {
            val text = binding.etComment.text.toString()
            if (text.isNotEmpty()) {
                sendComment(text)
            }
        }
    }

    private fun loadPostDetails() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed/$postId/comments", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    val post = json.getJSONObject("post")
                    
                    binding.tvUsername.text = post.getString("username")
                    binding.tvPostText.text = post.getString("text")
                    binding.tvLikeCount.text = post.getInt("like_count").toString()
                    binding.tvViewsCount.text = post.getInt("views").toString()
                    
                    val baseUrl = NativeLib.getBaseUrl()
                    val picUrl = post.optString("user_pic")
                    if (picUrl.isNotEmpty()) {
                        val fullPicUrl = if (picUrl.startsWith("http")) picUrl else baseUrl + picUrl
                        Glide.with(this@PostDetailsActivity)
                            .load(fullPicUrl)
                            .placeholder(R.drawable.ic_sun)
                            .into(binding.imgUserPic)
                    }

                    val mediaUrl = post.optString("media")
                    if (mediaUrl.isNotEmpty() && post.optString("media_type") == "image") {
                        val fullMediaUrl = if (mediaUrl.startsWith("http")) mediaUrl else baseUrl + mediaUrl
                        Glide.with(this@PostDetailsActivity)
                            .load(fullMediaUrl)
                            .into(binding.imgPostMedia)
                        binding.imgPostMedia.visibility = View.VISIBLE
                    } else {
                        binding.imgPostMedia.visibility = View.GONE
                    }

                    val arr = json.optJSONArray("comments") ?: JSONArray()
                    comments.clear()
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        comments.add(CommentData(
                            c.optInt("id", 0),
                            c.getString("username"),
                            c.getString("text"),
                            c.optString("user_pic"),
                            0,
                            c.optBoolean("is_edited", false),
                            false // isOwner برای توییت‌ها فعلا غیرفعال است
                        ))
                    }
                    commentAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun sendComment(text: String) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().put("post_id", postId).put("text", text).toString()
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/feed/comment", payload, token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    binding.etComment.text?.clear()
                    Toast.makeText(this@PostDetailsActivity, "کامنت ثبت شد", Toast.LENGTH_SHORT).show()
                    loadPostDetails()
                }
            }
        }
    }
}