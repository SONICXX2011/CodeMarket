package ir.codemarket.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private var sourceId: Int = 0
    private val comments = mutableListOf<Pair<String, String>>()
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
                    }
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
                    val arr = source.optJSONArray("comments") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        comments.add(Pair(c.getString("username"), c.getString("text")))
                    }
                    commentAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}

class CommentAdapter(private val items: List<Pair<String, String>>) : RecyclerView.Adapter<CommentAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.b.tvCommentUser.text = items[position].first
        holder.b.tvCommentText.text = items[position].second
    }

    override fun getItemCount() = items.size
}