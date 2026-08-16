package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ir.codemarket.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        
        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }
        
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateThemeIcon()
        
        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString()
            val password = binding.etPassword.text.toString()
            
            if (username.isNotEmpty() && password.isNotEmpty()) {
                
                binding.btnLogin.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
                
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = NativeLib.buildLoginPayload(username, password)
                    val (response, code) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/auth/login", payload) }
                    
                    binding.btnLogin.visibility = View.VISIBLE
                    binding.progressBar.visibility = View.GONE
                    
                    if (response != null) {
                        val json = JSONObject(response)
                        if (json.getBoolean("success")) {
                            sessionManager.saveAuthToken(json.getString("token"))
                            sessionManager.saveUsername(json.getString("username"))
                            startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
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
        
        binding.tvGoRegister.setOnClickListener { 
            startActivity(Intent(this, RegisterActivity::class.java)) 
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