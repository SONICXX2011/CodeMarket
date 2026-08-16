package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ir.codemarket.app.databinding.ActivityRegisterBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }
        
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateThemeIcon()
        
        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.btnRegister.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()
            
            if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty()) {
                
                binding.btnRegister.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = NativeLib.buildRegisterPayload(username, email, password)
                    val (response, code) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/auth/register", payload) }
                    
                    binding.btnRegister.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    
                    if (response != null) {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            sessionManager.saveAuthToken(json.getString("token"))
                            sessionManager.saveUsername(json.getString("username"))
                            startActivity(Intent(this@RegisterActivity, HomeActivity::class.java))
                            finish()
                        } else {
                            binding.tvError.text = json.optString("error", "خطای ناشناخته")
                            binding.tvError.visibility = View.VISIBLE
                        }
                    } else {
                        binding.tvError.text = "ارتباط با سرور برقرار نشد"
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun updateThemeIcon() {
        if (sessionManager.isDarkMode()) {
            binding.btnThemeToggle.setImageResource(R.drawable.ic_sun)
            binding.btnThemeToggle.setColorFilter(ContextCompat.getColor(this, R.color.yellow))
        } else {
            binding.btnThemeToggle.setImageResource(R.drawable.ic_moon)
            binding.btnThemeToggle.setColorFilter(ContextCompat.getColor(this, R.color.purple))
        }
    }
}