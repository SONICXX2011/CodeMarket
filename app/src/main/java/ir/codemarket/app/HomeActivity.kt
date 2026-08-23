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
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
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
    private lateinit var markwon: Markwon

    private val shopItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()
    private lateinit var shopAdapter: ShopAdapter

    private val feedItems = mutableListOf<FeedItem>()
    private lateinit var feedAdapter: FeedAdapter
    
    // متغیرهای صفحه‌بندی
    private var currentPage = 1
    private var isLoadingFeed = false
    private var hasMoreFeed = true

    // اطلاعات کاربر لاگین شده برای بررسی مالکیت پست
    private var currentUserId = -1

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
        markwon = Markwon.create(this)

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        else binding.root.setBackgroundResource(R.drawable.bg_gradient_light)

        setupShopView()
        setupFeedView()
        setupUploadView()
        setupProfileView()

        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.btnRefresh.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(500).start()
            binding.swipeRefresh.isRefreshing = true
            currentPage = 1
            loadFeedData()
        }

        binding.btnAddPost.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            currentPage = 1
            loadFeedData()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showSection("home")
                R.id.nav_shop -> showSection("shop")
                R.id.nav_profile -> showSection("profile")
                R.id.nav_submit -> showSection("submit")
            }
            true
        }

        loadProfile() // گرفتن آیدی کاربر لاگین شده
        loadShopData()
        loadFeedData()
    }

    private fun showSection(section: String) {
        binding.swipeRefresh.visibility = if (section == "home") View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (section == "shop") View.VISIBLE else View.GONE
        binding.profileContainer.visibility = if (section == "profile") View.VISIBLE else View.GONE
        binding.uploadContainer.visibility = if (section == "submit") View.VISIBLE else View.GONE
        
        binding.btnSearchIcon.visibility = if (section == "shop") View.VISIBLE else View.GONE
        binding.spacer.visibility = View.VISIBLE
        binding.btnRefresh.visibility = if (section == "home") View.VISIBLE else View.GONE
        binding.btnAddPost.visibility = if (section == "home") View.VISIBLE else View.GONE
        binding.etSearch.visibility = View.GONE
        binding.topBar.visibility = if (section == "profile") View.GONE else View.VISIBLE
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
            binding.spacer.visibility = if (binding.etSearch.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterShop(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun filterShop(query: String) {
        filteredItems.clear()
        if (query.isEmpty()) filteredItems.addAll(shopItems)
        else {
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
        val layoutManager = LinearLayoutManager(this)
        feedAdapter = FeedAdapter(feedItems, currentUserId, markwon, 
            onLikeClick = { postId, pos -> toggleLike(postId, pos) }, 
            onPostClick = { postId -> openPostDetails(postId) },
            onOptionsClick = { view, post, pos -> showPostOptions(view, post, pos) }
        )
        binding.recyclerFeed.layoutManager = layoutManager
        binding.recyclerFeed.adapter = feedAdapter

        binding.recyclerFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()
                    if (!isLoadingFeed && hasMoreFeed) {
                        if ((visibleItemCount + pastVisibleItems) >= totalItemCount - 5) {
                            currentPage++
                            loadFeedData()
                        }
                    }
                }
            }
        })
    }

    private fun loadFeedData() {
        isLoadingFeed = true
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (response, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed?page=$currentPage&limit=25", token) }
            binding.swipeRefresh.isRefreshing = false
            isLoadingFeed = false
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    hasMoreFeed = json.getBoolean("has_more")
                    val arr = json.getJSONArray("posts")
                    
                    if (currentPage == 1) feedItems.clear()
                    
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        val newItem = FeedItem(
                            p.getInt("id"), p.getInt("user_id"), p.optString("username"),
                            p.optString("user_pic"), p.optString("text"), p.optString("media"),
                            p.optString("media_type"), p.optBoolean("is_liked"),
                            p.optInt("like_count"), p.optInt("comment_count"),
                            p.optInt("views"), p.optString("created_at"), p.optBoolean("is_edited")
                        )
                        // جلوگیری از تکرار در سینک
                        if (feedItems.none { it.id == newItem.id }) feedItems.add(newItem)
                    }
                    feedAdapter.setCurrentUserId(currentUserId)
                    feedAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun showPostOptions(view: View, post: FeedItem, position: Int) {
        val popup = PopupMenu(this, view)
        popup.menu.add("ویرایش")
        popup.menu.add("حذف")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "حذف" -> deletePost(post.id, position)
                "ویرایش" -> editPost(post.id, post.text, position)
            }
            true
        }
        popup.show()
    }

    private fun deletePost(postId: Int, position: Int) {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/feed/post/$postId/delete", "{}", token) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                feedItems.removeAt(position)
                feedAdapter.notifyItemRemoved(position)
                Toast.makeText(this@HomeActivity, "پست حذف شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun editPost(postId: Int, currentText: String, position: Int) {
        val editText = EditText(this).apply {
            setText(currentText)
            setPadding(40, 40, 40, 40)
        }
        AlertDialog.Builder(this)
            .setTitle("ویرایش پست (پشتیبانی از مارک‌داون)")
            .setView(editText)
            .setPositiveButton("ثبت") { _, _ ->
                val newText = editText.text.toString()
                val token = sessionManager.fetchAuthToken() ?: ""
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = JSONObject().put("text", newText).toString()
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/feed/post/$postId/edit", payload, token) }
                    if (res != null && JSONObject(res).getBoolean("success")) {
                        feedItems[position] = feedItems[position].copy(text = newText, isEdited = true)
                        feedAdapter.notifyItemChanged(position)
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
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
                        val fields = mapOf("name" to name, "description" to desc)
                        val token = sessionManager.fetchAuthToken() ?: ""
                        val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadFile("/api/upload", token, zipFile, logoFile, fields) }
                        if (res != null && JSONObject(res).getBoolean("success")) {
                            Toast.makeText(this@HomeActivity, "سورس برای تایید ادمین ارسال شد", Toast.LENGTH_SHORT).show()
                            binding.etSourceName.text.clear()
                            binding.etSourceDesc.text.clear()
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
    }

    private fun loadProfile() {
        val token = sessionManager.fetchAuthToken() ?: ""
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/profile", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    currentUserId = verifyTokenExtractId(token) // اگر نیاز بود اینجا محاسبه میشه
                    binding.etFullName.setText(json.getString("full_name"))
                    binding.tvUsernameProfile.text = "@" + json.getString("username")
                    binding.tvEmailProfile.text = json.getString("email")
                    val picUrl = json.optString("profile_pic", "")
                    if (picUrl.isNotEmpty()) {
                        val fullPicUrl = if (picUrl.startsWith("http")) picUrl else NativeLib.getBaseUrl() + picUrl
                        Glide.with(this@HomeActivity).load(fullPicUrl).placeholder(R.drawable.ic_sun).into(binding.imgProfile)
                    }
                }
            }
        }
    }

    // شبیه‌سازی دیکود توکن برای گرفتن آی‌دی
    private fun verifyTokenExtractId(token: String): Int {
        try {
            val parts = token.split(".")
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            return JSONObject(payload).getInt("user_id")
        } catch (e: Exception) { return -1 }
    }

    private fun uploadProfilePic(uri: Uri) {
        val file = File(cacheDir, "temp_profile.png").apply { copyFromUri(uri) }
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadProfilePic("/api/profile/upload_pic", token, file) }
            if (res != null && JSONObject(res).getBoolean("success")) recreate()
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
                    val item = feedItems[position]
                    feedItems[position] = item.copy(isLiked = json.getBoolean("liked"), likeCount = json.getInt("like_count"))
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
    val id: Int, val userId: Int, val username: String, val userPic: String,
    val text: String, val media: String, val mediaType: String,
    val isLiked: Boolean, val likeCount: Int, val commentCount: Int,
    val views: Int, val createdAt: String, val isEdited: Boolean
)

class ShopAdapter(private val items: List<ShopItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ShopAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemShopBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvSourceName.text = item.name
        holder.b.tvSourceDesc.text = item.desc
        holder.b.root.setOnClickListener { onClick(position) }
        Glide.with(holder.b.root.context).load(NativeLib.getBaseUrl() + item.logo).placeholder(R.drawable.ic_sun).into(holder.b.imgSourceLogo)
    }
    override fun getItemCount(): Int = items.size
}

class FeedAdapter(
    private val items: List<FeedItem>,
    private var currentUserId: Int,
    private val markwon: Markwon,
    private val onLikeClick: (Int, Int) -> Unit,
    private val onPostClick: (Int) -> Unit,
    private val onOptionsClick: (View, FeedItem, Int) -> Unit
) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemFeedPostBinding) : RecyclerView.ViewHolder(b.root)

    fun setCurrentUserId(id: Int) { currentUserId = id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemFeedPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvUsername.text = item.username
        
        // رندر کردن مارک‌داون
        markwon.setMarkdown(holder.b.tvPostText, item.text)
        
        holder.b.tvLikeCount.text = item.likeCount.toString()
        holder.b.tvCommentCount.text = item.commentCount.toString()
        holder.b.tvViewsCount.text = item.views.toString()
        
        val editStatus = if (item.isEdited) " (ویرایش شده)" else ""
        holder.b.tvPostDate.text = TimeUtils.getTimeAgo(item.createdAt) + editStatus

        // فقط صاحب پست می‌تواند پست را ویرایش/حذف کند
        if (item.userId == currentUserId && currentUserId != -1) {
            holder.b.tvUsername.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_more, 0)
            holder.b.tvUsername.setOnClickListener { onOptionsClick(it, item, position) }
        } else {
            holder.b.tvUsername.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            holder.b.tvUsername.setOnClickListener(null)
        }

        val baseUrl = NativeLib.getBaseUrl()
        if (item.userPic.isNotEmpty()) Glide.with(holder.b.root.context).load(baseUrl + item.userPic).placeholder(R.drawable.ic_sun).into(holder.b.imgUserPic)
        else holder.b.imgUserPic.setImageResource(R.drawable.ic_sun)
        
        if (item.media.isNotEmpty() && item.mediaType == "image") {
            Glide.with(holder.b.root.context).load(baseUrl + item.media).into(holder.b.imgPostMedia)
            holder.b.imgPostMedia.visibility = View.VISIBLE
        } else holder.b.imgPostMedia.visibility = View.GONE

        if (item.isLiked) {
            holder.b.imgLike.setColorFilter(ContextCompat.getColor(holder.b.root.context, R.color.purple))
            holder.b.tvLikeCount.setTextColor(ContextCompat.getColor(holder.b.root.context, R.color.purple))
        } else {
            holder.b.imgLike.setColorFilter(Color.parseColor("#B3FFFFFF"))
            holder.b.tvLikeCount.setTextColor(Color.parseColor("#B3FFFFFF"))
        }

        holder.b.btnLike.setOnClickListener { onLikeClick(item.id, position) }
        holder.b.btnComment.setOnClickListener { onPostClick(item.id) }
        holder.b.tvPostText.setOnClickListener { onPostClick(item.id) }
    }
    override fun getItemCount(): Int = items.size
}