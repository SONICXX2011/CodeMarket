package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ir.codemarket.app.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        if (sessionManager.isDarkMode()) binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        else binding.root.setBackgroundResource(R.drawable.bg_gradient_light)

        binding.btnBack.setOnClickListener { finish() }

        if (sessionManager.isDarkMode()) binding.rbDark.isChecked = true
        else binding.rbLight.isChecked = true

        val currentSize = sessionManager.getTextSize()
        binding.seekBarTextSize.progress = (currentSize - 12f).toInt()
        binding.tvTextSizePreview.textSize = currentSize

        binding.seekBarTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvTextSizePreview.textSize = 12f + progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        loadVipSettings()

        binding.btnSaveSettings.setOnClickListener {
            val isDark = binding.rbDark.isChecked
            sessionManager.saveThemeMode(isDark)
            sessionManager.saveTextSize(12f + binding.seekBarTextSize.progress)

            if (binding.layoutVipSettings.visibility == View.VISIBLE) {
                saveVipSettings(binding.etCustomBg.text.toString())
            } else {
                restartApp()
            }
        }
    }

    private fun loadVipSettings() {
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: return@launch
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/profile", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success") && json.optBoolean("is_vip", false)) {
                    binding.layoutVipSettings.visibility = View.VISIBLE
                    binding.etCustomBg.setText(json.optString("custom_bg", ""))
                }
            }
        }
    }

    private fun saveVipSettings(bg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: return@launch
            val payload = JSONObject().put("custom_bg", bg).toString()
            withContext(Dispatchers.IO) { ApiClient.postRequest("/api/profile/update", payload, token) }
            restartApp()
        }
    }

    private fun restartApp() {
        Toast.makeText(this, "تنظیمات اعمال شد", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}