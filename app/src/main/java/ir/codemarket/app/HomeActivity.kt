package ir.codemarket.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import ir.codemarket.app.databinding.ActivityHomeBinding
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private val posts = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark) else setTheme(R.style.Theme_CodeMarket_Light)
        
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = PostAdapter(posts)

        loadFeed()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> { Logger.logEvent("Nav", "Home clicked"); true }
                R.id.nav_shop -> { Logger.logEvent("Nav", "Shop clicked"); true }
                R.id.nav_profile -> { Logger.logEvent("Nav", "Profile clicked"); true }
                R.id.nav_submit -> { Logger.logEvent("Nav", "Submit clicked"); true }
                else -> false
            }
        }
    }

    private fun loadFeed() {
        val token = SessionManager(this).fetchAuthToken()
        CoroutineScope(Dispatchers.Main).launch {
            val (response, code) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/feed", token ?: "") }
            if (response != null) {
                val json = JSONObject(response)
                if (json.getBoolean("success")) {
                    val arr = json.getJSONArray("posts")
                    for (i in 0 until arr.length()) {
                        val post = arr.getJSONObject(i)
                        posts.add(Pair(post.getString("username"), post.getString("content")))
                    }
                    binding.recyclerView.adapter?.notifyDataSetChanged()
                }
            }
        }
    }
}