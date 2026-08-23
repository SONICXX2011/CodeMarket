package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ir.codemarket.app.databinding.ActivitySettingsBinding

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
                val newSize = 12f + progress
                binding.tvTextSizePreview.textSize = newSize
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnSaveSettings.setOnClickListener {
            val isDark = binding.rbDark.isChecked
            sessionManager.saveThemeMode(isDark)
            
            val finalSize = 12f + binding.seekBarTextSize.progress
            sessionManager.saveTextSize(finalSize)

            Toast.makeText(this, "تنظیمات ذخیره شد", Toast.LENGTH_SHORT).show()
            
            // ری‌استارت اپ برای اعمال کامل تم
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}