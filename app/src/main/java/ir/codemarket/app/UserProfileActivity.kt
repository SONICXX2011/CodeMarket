package ir.codemarket.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import ir.codemarket.app.databinding.ActivityUserProfileBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class UserProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserProfileBinding
    private lateinit var sessionManager: SessionManager
    private var targetUserId: Int = -1
    private var targetUsername: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetUserId = intent.getIntExtra("user_id", -1)
        if (targetUserId == -1) { finish(); return }

        // تولبار رو با انیمیشن تلگرامی هماهنگ میکنیم
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
            // تا زمان ساخت صفحه چت (ChatActivity) در مرحله بعد، پیام میده تا کرش نکنه
            Toast.makeText(this, "در حال ورود به چت...", Toast.LENGTH_SHORT).show()
        }

        loadUserData()
    }

    private fun loadUserData() {
        val token = sessionManager.fetchAuthToken() ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/users/$targetUserId", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    targetUsername = json.getString("username")
                    val isVip = json.optBoolean("is_vip", false)
                    
                    binding.collapsingToolbar.title = targetUsername
                    binding.tvUsernameDisplay.text = targetUsername
                    binding.tvUserIdDisplay.text = "@$targetUsername"
                    
                    val bio = json.optString("bio", "")
                    if (bio.isNotEmpty()) {
                        binding.tvUserBioDisplay.text = bio
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