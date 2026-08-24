package ir.codemarket.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.btnCopyId.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("UserID", "@$targetUsername")
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "آیدی کپی شد", Toast.LENGTH_SHORT).show()
        }

        binding.btnSendMessage.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("target_id", targetUserId)
            intent.putExtra("target_username", targetUsername)
            startActivity(intent)
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
                    val title = if (json.optBoolean("is_vip")) "🌟 $targetUsername" else targetUsername
                    binding.toolbar.title = title
                    
                    binding.tvUserId.text = "@$targetUsername"
                    val bio = json.optString("bio", "")
                    if (bio.isNotEmpty()) binding.tvUserBio.text = bio

                    val pic = json.optString("profile_pic", "")
                    if (pic.isNotEmpty()) {
                        Glide.with(this@UserProfileActivity).load(if (pic.startsWith("http")) pic else NativeLib.getBaseUrl() + pic).into(binding.imgProfileCover)
                    }
                }
            }
        }
    }
}