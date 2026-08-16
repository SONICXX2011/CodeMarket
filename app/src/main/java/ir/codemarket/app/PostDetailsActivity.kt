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
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }
        
        binding = ActivityPostDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        postId = intent.getIntExtra("post_id", 0)
        
        commentAdapter = CommentAdapter(comments)
        binding.recyclerComments.layoutManager = LinearLayoutManager(this)
        binding.recyclerComments.adapter = commentAdapter

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
                            c.getString("username"), 
                            c.getString("text"), 
                            c.optString("user_pic")
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
            Glide.with(holder.b.root.context)
                .load(picUrl)
                .placeholder(R.drawable.ic_sun)
                .into(holder.b.imgCommentUserPic)
        }
    }

    override fun getItemCount(): Int = items.size
}