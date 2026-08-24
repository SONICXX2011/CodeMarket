package ir.codemarket.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import io.noties.markwon.Markwon
import ir.codemarket.app.databinding.ActivityUserProfileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    
    private var targetUserId: Int = -1
    private var targetUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = MarkdownUtils.createMarkwon(this) // پردازش هوشمند منشن و لینک

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // اینجا بررسی می‌کنیم که کاربر با ID اومده یا روی یه متن آیدی (@username) کلیک کرده
        targetUserId = intent.getIntExtra("user_id", -1)
        val passedUsername = intent.getStringExtra("target_username")
        if (passedUsername != null) targetUsername = passedUsername

        if (targetUserId == -1 && targetUsername.isEmpty()) { finish(); return }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.collapsingToolbar.setExpandedTitleColor(Color.TRANSPARENT)
        binding.collapsingToolbar.setCollapsedTitleTextColor(if (sessionManager.isDarkMode()) Color.WHITE else Color.BLACK)

        binding.btnCopyId.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UserID", "@$targetUsername")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "آیدی کپی شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnSendMessage.setOnClickListener {
            Toast.makeText(this, "در حال ورود به چت...", Toast.LENGTH_SHORT).show()
        }

        loadUserData()
    }

    private fun loadUserData() {
        val token = sessionManager.fetchAuthToken() ?: return
        
        // اگر username داریم به جای id با username ریکوئست می‌دیم
        val apiParam = if (targetUsername.isNotEmpty()) targetUsername else targetUserId.toString()

        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/users/$apiParam", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    targetUserId = json.optInt("id", -1)
                    targetUsername = json.getString("username")
                    val isVip = json.optBoolean("is_vip", false)
                    
                    binding.collapsingToolbar.title = targetUsername
                    binding.tvUsernameDisplay.text = targetUsername
                    binding.tvUserIdDisplay.text = "@$targetUsername"
                    
                    val bio = json.optString("bio", "")
                    if (bio.isNotEmpty()) {
                        // تفسیر هوشمند مارک‌داون و آبی کردن لینک‌ها و آیدی‌های داخل بیوگرافی
                        MarkdownUtils.setMarkdownText(markwon, binding.tvUserBioDisplay, bio)
                    }

                    val baseUrl = NativeLib.getBaseUrl()
                    val badgeUrl = json.optString("badge_url", "")
                    if (isVip && badgeUrl.isNotEmpty()) {
                        binding.imgVipBadge.visibility = View.VISIBLE
                        Glide.with(this@UserProfileActivity).load(if (badgeUrl.startsWith("http")) badgeUrl else baseUrl + badgeUrl).into(binding.imgVipBadge)
                    } else {
                        binding.imgVipBadge.visibility = View.GONE
                    }

                    val pic = json.optString("profile_pic", "")
                    if (pic.isNotEmpty()) {
                        val fullPicUrl = if (pic.startsWith("http")) pic else baseUrl + pic
                        Glide.with(this@UserProfileActivity)
                            .load(fullPicUrl)
                            .placeholder(R.drawable.ic_sun)
                            .into(binding.imgProfileCover)
                    } else {
                        binding.imgProfileCover.setImageResource(R.drawable.ic_sun)
                    }
                }
            }
        }
    }
}