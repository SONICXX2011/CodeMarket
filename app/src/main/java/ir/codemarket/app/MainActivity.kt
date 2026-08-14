package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        val token = sessionManager.fetchAuthToken()
        val intent = if (token != null) Intent(this, HomeActivity::class.java) else Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}