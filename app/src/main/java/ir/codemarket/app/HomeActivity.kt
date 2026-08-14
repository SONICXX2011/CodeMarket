package ir.codemarket.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ir.codemarket.app.databinding.ActivityHomeBinding
import ir.codemarket.app.databinding.ItemShopBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private val shopItems = mutableListOf<ShopItem>()
    private val filteredItems = mutableListOf<ShopItem>()
    private lateinit var shopAdapter: ShopAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark) else setTheme(R.style.Theme_CodeMarket_Light)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupShopView()
        setupUploadView()
        setupProfileView()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> showView(binding.shopContainer)
                R.id.nav_shop -> showView(binding.shopContainer)
                R.id.nav_profile -> showView(binding.profileContainer)
                R.id.nav_submit -> showView(binding.uploadContainer)
            }
            true
        }
        loadShopData()
    }

    private fun showView(view: View) {
        binding.shopContainer.visibility = if (view == binding.shopContainer) View.VISIBLE else View.GONE
        binding.profileContainer.visibility = if (view == binding.profileContainer) View.VISIBLE else View.GONE
        binding.uploadContainer.visibility = if (view == binding.uploadContainer) View.VISIBLE else View.GONE
    }

    private fun setupShopView() {
        shopAdapter = ShopAdapter(filteredItems) { position ->
            val intent = Intent(this, SourceDetailsActivity::class.java)
            intent.putExtra("source_id", filteredItems[position].id)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = shopAdapter

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

    private fun setupUploadView() {
        binding.btnUpload.setOnClickListener {
            val name = binding.etSourceName.text.toString()
            val desc = binding.etSourceDesc.text.toString()
            val link = binding.etSourceLink.text.toString()
            if (name.isNotEmpty() && desc.isNotEmpty() && link.isNotEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = JSONObject().apply {
                        put("name", name)
                        put("description", desc)
                        put("link", link)
                        put("logo", "")
                    }.toString()
                    val token = sessionManager.fetchAuthToken() ?: ""
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/upload", payload, token) }
                    if (res != null && JSONObject(res).getBoolean("success")) {
                        Toast.makeText(this@HomeActivity, "سورس برای تایید ادمین ارسال شد", Toast.LENGTH_SHORT).show()
                        binding.etSourceName.text?.clear()
                        binding.etSourceDesc.text?.clear()
                        binding.etSourceLink.text?.clear()
                    } else {
                        Toast.makeText(this@HomeActivity, "خطا در ارسال", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun setupProfileView() {
        binding.btnUpdateProfile.setOnClickListener {
            val fullName = binding.etFullName.text.toString()
            CoroutineScope(Dispatchers.Main).launch {
                val payload = JSONObject().apply {
                    put("full_name", fullName)
                    put("profile_pic", "")
                }.toString()
                val token = sessionManager.fetchAuthToken() ?: ""
                val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/profile/update", payload, token) }
                if (res != null) {
                    Toast.makeText(this@HomeActivity, "پروفایل آپدیت شد", Toast.LENGTH_SHORT).show()
                }
            }
        }
        CoroutineScope(Dispatchers.Main).launch {
            val token = sessionManager.fetchAuthToken() ?: ""
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/profile", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    binding.etFullName.setText(json.getString("full_name"))
                    binding.tvUsernameProfile.text = "@" + json.getString("username")
                }
            }
        }
    }
}

data class ShopItem(val id: Int, val name: String, val desc: String, val logo: String)

class ShopAdapter(private val items: List<ShopItem>, private val onClick: (Int) -> Unit) : RecyclerView.Adapter<ShopAdapter.ViewHolder>() {
    class ViewHolder(val b: ItemShopBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemShopBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.b.tvSourceName.text = item.name
        holder.b.tvSourceDesc.text = item.desc
        holder.b.root.setOnClickListener { onClick(position) }
    }

    override fun getItemCount() = items.size
}