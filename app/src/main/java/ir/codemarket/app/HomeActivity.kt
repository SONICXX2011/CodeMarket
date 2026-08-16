package ir.codemarket.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.codemarket.app.databinding.ActivityHomeBinding
import ir.codemarket.app.databinding.ItemFeedPostBinding
import ir.codemarket.app.databinding.ItemShopBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager

    private val shopItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()
    private lateinit var shopAdapter: ShopAdapter

    private val feedItems = mutableListOf<FeedItem>()
    private lateinit var feedAdapter: FeedAdapter

    private var selectedZipUri: Uri? = null
    private var selectedLogoUri: Uri? = null

    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedZipUri = it
            binding.btnSelectZip.text = "فایل ZIP انتخاب شد"
        }
    }

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedLogoUri = it
            binding.btnSelectLogo.text = "لوگو انتخاب شد"
        }
    }

    private val pickProfilePic = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadProfilePic(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_light)
        }

        setupShopView()
        setupFeedView()
        setupUploadView()
        setupProfileView()

        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.btnAddPost.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    binding.homeContainer.visibility = View.VISIBLE
                    binding.recyclerView.visibility = View.GONE
                    binding.uploadContainer.visibility = View.GONE
                    binding.profileContainer.visibility = View.GONE
                    binding.btnSearchIcon.visibility = View.GONE
                    binding.etSearch.visibility = View.GONE
                    binding.btnAddPost.visibility = View.VISIBLE
                }
                R.id.nav_shop -> {
                    binding.homeContainer.visibility = View.GONE
                    binding.recyclerView.visibility = View.VISIBLE
                    binding.uploadContainer.visibility = View.GONE
                    binding.profileContainer.visibility = View.GONE
                    binding.btnSearchIcon.visibility = View.VISIBLE
                    binding.etSearch.visibility = View.GONE
                    binding.btnAddPost.visibility = View.GONE
                }
                R.id.nav_profile -> {
                    binding.homeContainer.visibility = View.GONE
                    binding.recyclerView.visibility = View.GONE
                    binding.uploadContainer.visibility = View.GONE
                    binding.profileContainer.visibility = View.VISIBLE
                    binding.btnSearchIcon.visibility = View.GONE
                    binding.etSearch.visibility = View.GONE
                    binding.btnAddPost.visibility = View.GONE
                }
                R.id.nav_submit -> {
                    binding.homeContainer.visibility = View.GONE
                    binding.recyclerView.visibility = View.GONE
                    binding.uploadContainer.visibility = View.VISIBLE
                    binding.profileContainer.visibility = View.GONE
                    binding.btnSearchIcon.visibility = View.GONE
                    binding.etSearch.visibility = View.GONE
                    binding.btnAddPost.visibility = View.GONE
                }
            }
            true
        }

        loadShopData()
        loadFeedData()
    }

    private fun setupShopView() {
        shopAdapter = ShopAdapter(filteredItems) { position ->
            val intent = Intent(this, SourceDetailsActivity::class.java)
            intent.putExtra("source_id", filteredItems[position].id)
            startActivity(intent)
        }

        binding.recyclerView.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerView.adapter = shopAdapter

        binding.btnSearchIcon.setOnClickListener {
            binding.etSearch.visibility = if (binding.etSearch.visibility == View.GONE) View.VISIBLE else View.GONE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterShop(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterShop(query: String) {
        filteredItems.clear()
        if (query.isEmpty()) {
            filteredItems.addAll(shopItems)
        } else {
            shopItems.forEach {
                if (it.name.contains(query, ignoreCase = true) || it.desc.contains(query, ignoreCase = true)) {
                    filteredItems.add(it)
                }
            }
        }
        shopAdapter.notifyDataSetChanged()
    }

    private fun loadShopData() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/shop", token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val arr = json.getJSONArray("sources")
                    shopItems.clear()
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        shopItems.add(ShopItem(s.getInt("id"), s.getString("name"), s.getString("description"), s.optString("logo")))
                    }
                    filterShop("")
                }
            }
        }
    }

    private fun setupFeedView() {
        feedAdapter = FeedAdapter(feedItems, { postId, position -> toggleLike(postId, position) }, { postId -> openPostDetails(postId) })
        binding.recyclerFeed.layoutManager = LinearLayoutManager(this)
        binding.recyclerFeed.adapter = feedAdapter
    }

    private fun loadFeedData() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed", token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val arr = json.getJSONArray("posts")
                    feedItems.clear()
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        feedItems.add(FeedItem(
                            p.getInt("id"),
                            p.optString("username"),
                            p.optString("user_pic"),
                            p.optString("text"),
                            p.optString("media"),
                            p.optString("media_type"),
                            p.optBoolean("is_liked"),
                            p.optInt("like_count"),
                            p.optInt("comment_count"),
                            p.optInt("views")
                        ))
                    }
                    feedAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupUploadView() {
        binding.btnSelectLogo.setOnClickListener { pickLogo.launch("image/*") }
        binding.btnSelectZip.setOnClickListener { pickZip.launch("application/zip") }

        binding.btnUpload.setOnClickListener {
            if (selectedZipUri != null && selectedLogoUri != null) {
                val name = binding.etSourceName.text.toString()
                val desc = binding.etSourceDesc.text.toString()
                if (name.isNotEmpty() && desc.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val zipFile = File(cacheDir, "temp_zip.zip").apply { copyFromUri(selectedZipUri!!) }
                        val logoFile = File(cacheDir, "temp_logo.png").apply { copyFromUri(selectedLogoUri!!) }
                        val fields = mapOf("name" to name, "description" to desc, "link" to binding.etSourceLink.text.toString())
                        val token = sessionManager.fetchAuthToken() ?: ""
                        val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadFile("/api/upload", token, zipFile, logoFile, fields) }
                        if (res != null && JSONObject(res).getBoolean("success")) {
                            Toast.makeText(this@HomeActivity, "سورس برای تایید ادمین ارسال شد", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@HomeActivity, "خطا در ارسال", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupProfileView() {
        binding.btnChangePic.setOnClickListener { pickProfilePic.launch("image/*") }

        binding.btnUpdateProfile.setOnClickListener {
            val fullName = binding.etFullName.text.toString()
            CoroutineScope(Dispatchers.Main).launch {
                val payload = JSONObject().put("full_name", fullName).toString()
                val token = sessionManager.fetchAuthToken() ?: ""
                withContext(Dispatchers.IO) { ApiClient.postRequest("/api/profile/update", payload, token) }
                Toast.makeText(this@HomeActivity, "پروفایل آپدیت شد", Toast.LENGTH_SHORT).show()
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/profile", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    binding.etFullName.setText(json.getString("full_name"))
                    binding.tvUsernameProfile.text = "@" + json.getString("username")
                    binding.tvEmailProfile.text = json.getString("email")

                    val picUrl = json.optString("profile_pic", "")
                    if (picUrl.isNotEmpty()) {
                        val baseUrl = NativeLib.getBaseUrl()
                        val fullPicUrl = if (picUrl.startsWith("http")) picUrl else baseUrl + picUrl
                        Glide.with(this@HomeActivity)
                            .load(fullPicUrl)
                            .placeholder(R.drawable.ic_sun)
                            .into(binding.imgProfile)
                    }
                }
            }
        }
    }

    private fun uploadProfilePic(uri: Uri) {
        val file = File(cacheDir, "temp_profile.png").apply { copyFromUri(uri) }
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadProfilePic("/api/profile/upload_pic", token, file) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                Toast.makeText(this@HomeActivity, "عکس پروفایل آپدیت شد", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
    }

    private fun File.copyFromUri(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { input ->
            this.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun toggleLike(postId: Int, position: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        val payload = JSONObject().put("post_id", postId).toString()
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/feed/like", payload, token) }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val liked = json.getBoolean("liked")
                    val count = json.getInt("like_count")
                    val item = feedItems[position]
                    feedItems[position] = item.copy(isLiked = liked, likeCount = count)
                    feedAdapter.notifyItemChanged(position)
                }
            }
        }
    }

    private fun openPostDetails(postId: Int) {
        val intent = Intent(this, PostDetailsActivity::class.java)
        intent.putExtra("post_id", postId)
        startActivity(intent)
    }
}

data class ShopItem(val id: Int, val name: String, val desc: String, val logo: String)

data class FeedItem(
    val id: Int,
    val username: String,
    val userPic: String,
    val text: String,
    val media: String,
    val mediaType: String,
    val isLiked: Boolean,
    val likeCount: Int,
    val commentCount: Int,
    val views: Int
)

class ShopAdapter(private val items: List<ShopItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ShopAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemShopBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvSourceName.text = item.name
        holder.b.tvSourceDesc.text = item.desc
        holder.b.root.setOnClickListener { onClick(position) }
        
        val baseUrl = NativeLib.getBaseUrl()
        val fullLogoUrl = if (item.logo.startsWith("http")) item.logo else baseUrl + item.logo
        
        Glide.with(holder.b.root.context)
            .load(fullLogoUrl)
            .placeholder(R.drawable.ic_sun)
            .into(holder.b.imgSourceLogo)
    }

    override fun getItemCount(): Int = items.size
}

class FeedAdapter(
    private val items: List<FeedItem>,
    private val onLikeClick: (Int, Int) -> Unit,
    private val onPostClick: (Int) -> Unit
) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemFeedPostBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemFeedPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvUsername.text = item.username
        holder.b.tvPostText.text = item.text
        holder.b.tvLikeCount.text = item.likeCount.toString()
        holder.b.tvCommentCount.text = item.commentCount.toString()
        holder.b.tvViewsCount.text = item.views.toString()

        val baseUrl = NativeLib.getBaseUrl()
        
        if (item.userPic.isNotEmpty()) {
            val picUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context)
                .load(picUrl)
                .placeholder(R.drawable.ic_sun)
                .into(holder.b.imgUserPic)
        }
        
        if (item.media.isNotEmpty() && item.mediaType == "image") {
            val mediaUrl = if (item.media.startsWith("http")) item.media else baseUrl + item.media
            Glide.with(holder.b.root.context)
                .load(mediaUrl)
                .into(holder.b.imgPostMedia)
            holder.b.imgPostMedia.visibility = View.VISIBLE
        } else {
            holder.b.imgPostMedia.visibility = View.GONE
        }

        if (item.isLiked) {
            holder.b.imgLike.setColorFilter(ContextCompat.getColor(holder.b.root.context, R.color.purple))
            holder.b.tvLikeCount.setTextColor(ContextCompat.getColor(holder.b.root.context, R.color.purple))
        } else {
            holder.b.imgLike.setColorFilter(Color.parseColor("#80FFFFFF"))
            holder.b.tvLikeCount.setTextColor(Color.parseColor("#80FFFFFF"))
        }

        holder.b.btnLike.setOnClickListener { onLikeClick(item.id, position) }
        holder.b.btnComment.setOnClickListener { onPostClick(item.id) }
        holder.b.root.setOnClickListener { onPostClick(item.id) }
    }

    override fun getItemCount(): Int = items.size
}