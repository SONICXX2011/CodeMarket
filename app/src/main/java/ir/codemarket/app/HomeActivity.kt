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
import ir.codemarket.app.databinding.ItemChatListBinding
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

    // لیست چت‌ها
    private val chatItems = mutableListOf<ChatListItem>()
    private lateinit var chatListAdapter: ChatListAdapter
    
    private var currentPage = 1
    private var isLoadingFeed = false
    private var hasMoreFeed = true
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
        setupChatsView()
        setupUploadView()
        setupProfileView()

        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.btnRefresh.setOnClickListener {
            it.animate().rotationBy(360f).setDuration(500).start()
            if (binding.recyclerFeed.visibility == View.VISIBLE) {
                binding.swipeRefresh.isRefreshing = true
                currentPage = 1
                loadFeedData()
            } else {
                loadChatsData() // آپدیت لیست چت‌ها
            }
        }

        binding.btnAddPost.setOnClickListener {
            startActivity(Intent(this, CreatePostActivity::class.java))
        }
        
        binding.swipeRefresh.setOnRefreshListener {
            if (binding.recyclerFeed.visibility == View.VISIBLE) {
                currentPage = 1
                loadFeedData()
            } else {
                binding.swipeRefresh.isRefreshing = false
                loadChatsData()
            }
        }

        // هندل کردن تب‌های بالای بخش خانه (عمومی / شخصی)
        binding.tabPublic.setOnClickListener {
            binding.tabPublic.setTextColor(Color.parseColor("#5288C1"))
            binding.tabPublic.textStyle = android.graphics.Typeface.BOLD
            binding.tabPublic.setBackgroundResource(R.drawable.bg_glass_input)
            
            binding.tabPrivate.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.tabPrivate.textStyle = android.graphics.Typeface.NORMAL
            binding.tabPrivate.setBackgroundColor(Color.TRANSPARENT)

            binding.recyclerFeed.visibility = View.VISIBLE
            binding.recyclerChats.visibility = View.GONE
            binding.btnAddPost.visibility = View.VISIBLE
        }

        binding.tabPrivate.setOnClickListener {
            binding.tabPrivate.setTextColor(Color.parseColor("#5288C1"))
            binding.tabPrivate.textStyle = android.graphics.Typeface.BOLD
            binding.tabPrivate.setBackgroundResource(R.drawable.bg_glass_input)
            
            binding.tabPublic.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            binding.tabPublic.textStyle = android.graphics.Typeface.NORMAL
            binding.tabPublic.setBackgroundColor(Color.TRANSPARENT)

            binding.recyclerFeed.visibility = View.GONE
            binding.recyclerChats.visibility = View.VISIBLE
            binding.btnAddPost.visibility = View.GONE
            loadChatsData()
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

        loadProfile()
        loadShopData()
        loadFeedData()
    }

    private fun showSection(section: String) {
        binding.swipeRefresh.visibility = if (section == "home") View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (section == "shop") View.VISIBLE else View.GONE
        binding.profileContainer.visibility = if (section == "profile") View.VISIBLE else View.GONE
        binding.uploadContainer.visibility = if (section == "submit") View.VISIBLE else View.GONE
        
        binding.layoutHomeTabs.visibility = if (section == "home") View.VISIBLE else View.GONE
        binding.btnSearchIcon.visibility = if (section == "shop") View.VISIBLE else View.GONE
        binding.spacer.visibility = View.VISIBLE
        binding.btnRefresh.visibility = if (section == "home") View.VISIBLE else View.GONE
        
        binding.etSearch.visibility = View.GONE
        binding.topBar.visibility = if (section == "profile") View.GONE else View.VISIBLE

        if (section == "home") {
            if (binding.recyclerFeed.visibility == View.VISIBLE) binding.btnAddPost.visibility = View.VISIBLE
        } else {
            binding.btnAddPost.visibility = View.GONE
        }
    }

    private fun setupChatsView() {
        chatListAdapter = ChatListAdapter(chatItems) { chat ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("target_id", chat.targetUserId)
            intent.putExtra("target_username", chat.targetUsername)
            startActivity(intent)
        }
        binding.recyclerChats.layoutManager = LinearLayoutManager(this)
        binding.recyclerChats.adapter = chatListAdapter
    }

    private fun loadChatsData() {
        // فعلاً دمو برای نمایش UI بدون کرش تا وصل شدن به بک‌اند اصلی
        chatItems.clear()
        chatItems.add(ChatListItem(1, 2, "ali_dev", "", "سلام خوبی؟ پروژه چطور پیش میره؟", "12:30", 2))
        chatItems.add(ChatListItem(2, 3, "sara_coder", "", "فایل‌ها رو برات فرستادم.", "دیروز", 0))
        chatListAdapter.notifyDataSetChanged()
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
            if (binding.etSearch.visibility == View.GONE) {
                binding.etSearch.visibility = View.VISIBLE
                binding.spacer.visibility = View.GONE
            } else {
                binding.etSearch.visibility = View.GONE
                binding.spacer.visibility = View.VISIBLE
            }
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
        val layoutManager = LinearLayoutManager(this)
        feedAdapter = FeedAdapter(feedItems, currentUserId, markwon, sessionManager.getTextSize(),
            onLikeClick = { postId, pos -> toggleLike(postId, pos) }, 
            onPostClick = { postId -> openPostDetails(postId) },
            onOptionsClick = { view, post, pos -> showPostOptions(view, post, pos) },
            onUserClick = { userId -> openUserProfile(userId) }
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
                            p.optInt("views"), p.optString("created_at"), p.optBoolean("is_edited"),
                            p.optBoolean("is_vip", false), p.optString("badge_url", ""), p.optString("custom_bg", "")
                        )
                        
                        val existingIndex = feedItems.indexOfFirst { it.id == newItem.id }
                        if (existingIndex != -1) feedItems[existingIndex] = newItem
                        else feedItems.add(newItem)
                    }
                    feedAdapter.setCurrentUserId(currentUserId)
                    feedAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun openUserProfile(userId: Int) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("user_id", userId)
        startActivity(intent)
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
            hint = "ویرایش پست (پشتیبانی از مارک‌داون)..."
        }
        
        MarkdownUtils.applyMarkdownShortcuts(editText)

        AlertDialog.Builder(this)
            .setTitle("ویرایش پست")
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
                        Toast.makeText(this@HomeActivity, "پست ویرایش شد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun setupUploadView() {
        binding.btnSelectLogo.setOnClickListener { pickLogo.launch("image/*") }
        binding.btnSelectZip.setOnClickListener { pickZip.launch("application/zip") }
        
        MarkdownUtils.applyMarkdownShortcuts(binding.etSourceDesc)
        
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
                            Toast.makeText(this@HomeActivity, "سورس ارسال شد", Toast.LENGTH_SHORT).show()
                            binding.etSourceName.setText("")
                            binding.etSourceDesc.setText("")
                            selectedZipUri = null; selectedLogoUri = null
                        } else {
                            Toast.makeText(this@HomeActivity, "خطا در ارسال", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "لطفاً نام و توضیحات را وارد کنید", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "لوگو و فایل ZIP الزامی است", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupProfileView() {
        binding.btnChangePic.setOnClickListener { pickProfilePic.launch("image/*") }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnUpdateProfile.setOnClickListener {
            val fullName = binding.etFullName.text.toString()
            val bio = binding.etBio.text.toString()
            CoroutineScope(Dispatchers.Main).launch {
                val payload = JSONObject().put("full_name", fullName).put("bio", bio).toString()
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
                    currentUserId = json.optInt("id", -1)
                    binding.etFullName.setText(json.getString("full_name"))
                    binding.etBio.setText(json.optString("bio", ""))
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

// دیتامدل‌ها
data class ShopItem(val id: Int, val name: String, val desc: String, val logo: String)

data class ChatListItem(val id: Int, val targetUserId: Int, val targetUsername: String, val targetPic: String, val lastMessage: String, val date: String, val unreadCount: Int)

data class FeedItem(
    val id: Int, val userId: Int, val username: String, val userPic: String,
    val text: String, val media: String, val mediaType: String,
    val isLiked: Boolean, val likeCount: Int, val commentCount: Int,
    val views: Int, val createdAt: String, val isEdited: Boolean,
    val isVip: Boolean, val badgeUrl: String, val customBg: String
)

// آداپترها
class ChatListAdapter(private val items: List<ChatListItem>, private val onClick: (ChatListItem) -> Unit) : RecyclerView.Adapter<ChatListAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemChatListBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemChatListBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvChatUsername.text = item.targetUsername
        holder.b.tvChatLastMessage.text = item.lastMessage
        holder.b.tvChatDate.text = item.date
        if (item.unreadCount > 0) {
            holder.b.tvUnreadCount.visibility = View.VISIBLE
            holder.b.tvUnreadCount.text = item.unreadCount.toString()
        } else holder.b.tvUnreadCount.visibility = View.GONE
        
        val baseUrl = NativeLib.getBaseUrl()
        if (item.targetPic.isNotEmpty()) {
            Glide.with(holder.b.root.context).load(if (item.targetPic.startsWith("http")) item.targetPic else baseUrl + item.targetPic).placeholder(R.drawable.ic_sun).into(holder.b.imgChatUser)
        } else holder.b.imgChatUser.setImageResource(R.drawable.ic_sun)
        
        holder.b.root.setOnClickListener { onClick(item) }
    }
    override fun getItemCount(): Int = items.size
}

class ShopAdapter(private val items: List<ShopItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ShopAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemShopBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvSourceName.text = item.name
        holder.b.tvSourceDesc.text = item.desc
        holder.b.root.setOnClickListener { onClick(position) }
        holder.b.btnShowDetails.setOnClickListener { onClick(position) }
        
        val fullLogoUrl = if (item.logo.startsWith("http")) item.logo else NativeLib.getBaseUrl() + item.logo
        Glide.with(holder.b.root.context).load(fullLogoUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgSourceLogo)
    }
    override fun getItemCount(): Int = items.size
}

class FeedAdapter(
    private val items: List<FeedItem>,
    private var currentUserId: Int,
    private val markwon: Markwon,
    private val size: Float,
    private val onLikeClick: (Int, Int) -> Unit,
    private val onPostClick: (Int) -> Unit,
    private val onOptionsClick: (View, FeedItem, Int) -> Unit,
    private val onUserClick: (Int) -> Unit
) : RecyclerView.Adapter<FeedAdapter.ViewHolder>() {

    class ViewHolder(val b: ItemFeedPostBinding) : RecyclerView.ViewHolder(b.root)

    fun setCurrentUserId(id: Int) { currentUserId = id }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemFeedPostBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvUsername.text = item.username
        
        holder.b.tvPostText.textSize = size
        markwon.setMarkdown(holder.b.tvPostText, item.text)
        
        holder.b.tvLikeCount.text = item.likeCount.toString()
        holder.b.tvCommentCount.text = item.commentCount.toString()
        holder.b.tvViewsCount.text = item.views.toString()
        
        val editStatus = if (item.isEdited) " (ویرایش شده)" else ""
        holder.b.tvPostDate.text = TimeUtils.getTimeAgo(item.createdAt) + editStatus

        // اعمال بک‌گراند کاستوم و تیک آبی VIP
        val baseUrl = NativeLib.getBaseUrl()
        if (item.isVip && item.badgeUrl.isNotEmpty()) {
            holder.b.imgBadge.visibility = View.VISIBLE
            Glide.with(holder.b.root.context).load(if (item.badgeUrl.startsWith("http")) item.badgeUrl else baseUrl + item.badgeUrl).into(holder.b.imgBadge)
        } else holder.b.imgBadge.visibility = View.GONE

        if (item.isVip && item.customBg.isNotEmpty()) {
            try { holder.b.cardPost.setCardBackgroundColor(Color.parseColor(item.customBg)) } catch(e: Exception) {}
        }

        if (item.userId == currentUserId && currentUserId != -1) {
            holder.b.tvUsername.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_more, 0)
            holder.b.tvUsername.setOnClickListener { onOptionsClick(it, item, position) }
        } else {
            holder.b.tvUsername.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            holder.b.tvUsername.setOnClickListener { onUserClick(item.userId) }
            holder.b.imgUserPic.setOnClickListener { onUserClick(item.userId) }
        }

        if (item.userPic.isNotEmpty()) {
            val picUrl = if (item.userPic.startsWith("http")) item.userPic else baseUrl + item.userPic
            Glide.with(holder.b.root.context).load(picUrl).placeholder(R.drawable.ic_sun).into(holder.b.imgUserPic)
        } else holder.b.imgUserPic.setImageResource(R.drawable.ic_sun)
        
        if (item.media.isNotEmpty() && item.mediaType == "image") {
            val mediaUrl = if (item.media.startsWith("http")) item.media else baseUrl + item.media
            Glide.with(holder.b.root.context).load(mediaUrl).into(holder.b.imgPostMedia)
            holder.b.imgPostMedia.visibility = View.VISIBLE
        } else holder.b.imgPostMedia.visibility = View.GONE

        if (item.isLiked) {
            holder.b.imgLike.setColorFilter(ContextCompat.getColor(holder.b.root.context, R.color.purple))
            holder.b.tvLikeCount.setTextColor(ContextCompat.getColor(holder.b.root.context, R.color.purple))
        } else {
            holder.b.imgLike.setColorFilter(Color.parseColor("#B3888888"))
            holder.b.tvLikeCount.setTextColor(Color.parseColor("#B3888888"))
        }

        holder.b.btnLike.setOnClickListener { onLikeClick(item.id, position) }
        holder.b.btnComment.setOnClickListener { onPostClick(item.id) }
        holder.b.tvPostText.setOnClickListener { onPostClick(item.id) }
        holder.b.root.setOnClickListener { onPostClick(item.id) }
    }
    override fun getItemCount(): Int = items.size
}