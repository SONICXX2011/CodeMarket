package ir.codemarket.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import ir.codemarket.app.databinding.ActivityHomeBinding
import ir.codemarket.app.databinding.ItemShopBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private val shopItems = mutableListOf<ShopItem>()
    private lateinit var shopAdapter: ShopAdapter
    private var selectedZipUri: Uri? = null
    private var selectedLogoUri: Uri? = null

    private val pickZip = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedZipUri = it; binding.btnSelectZip.text = "فایل ZIP انتخاب شد" }
    }

    private val pickLogo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedLogoUri = it; binding.btnSelectLogo.text = "لوگو انتخاب شد" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark) else setTheme(R.style.Theme_CodeMarket_Light)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupShopView()
        setupUploadView()
        setupProfileView()

        binding.btnThemeToggle.setOnClickListener {
            sessionManager.saveThemeMode(!sessionManager.isDarkMode())
            recreate()
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_home -> { binding.shopContainer.visibility = View.VISIBLE; binding.uploadContainer.visibility = View.GONE; binding.profileContainer.visibility = View.GONE }
                R.id.nav_shop -> { binding.shopContainer.visibility = View.VISIBLE; binding.uploadContainer.visibility = View.GONE; binding.profileContainer.visibility = View.GONE }
                R.id.nav_profile -> { binding.shopContainer.visibility = View.GONE; binding.uploadContainer.visibility = View.GONE; binding.profileContainer.visibility = View.VISIBLE }
                R.id.nav_submit -> { binding.shopContainer.visibility = View.GONE; binding.uploadContainer.visibility = View.VISIBLE; binding.profileContainer.visibility = View.GONE }
            }
            true
        }
        loadShopData()
    }

    private fun setupShopView() {
        shopAdapter = ShopAdapter(shopItems) { position ->
            val intent = Intent(this, SourceDetailsActivity::class.java)
            intent.putExtra("source_id", shopItems[position].id)
            startActivity(intent)
        }
        binding.recyclerView.layoutManager = GridLayoutManager(this, 2) // گرید ۲ ستونی
        binding.recyclerView.adapter = shopAdapter

        binding.btnSearchIcon.setOnClickListener {
            if (binding.etSearch.visibility == View.GONE) {
                binding.etSearch.visibility = View.VISIBLE
            } else {
                binding.etSearch.visibility = View.GONE
            }
        }
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
                    shopAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun setupUploadView() {
        binding.btnSelectLogo.setOnClickListener { pickLogo.launch("image/*") }
        binding.btnSelectZip.setOnClickListener { pickZip.launch("application/zip") }

        binding.btnUpload.setOnClickListener {
            if (selectedZipUri != null && selectedLogoUri != null) {
                val name = binding.etSourceName.text.toString()
                val desc = binding.etSourceDesc.text.toString()
                if (name.isNotEmpty() && desc.isNotEmpty()) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val zipFile = File(cacheDir, "temp_zip.zip").apply { copyFromUri(selectedZipUri!!) }
                        val logoFile = File(cacheDir, "temp_logo.png").apply { copyFromUri(selectedLogoUri!!) }
                        val fields = mapOf("name" to name, "description" to desc, "link" to binding.etSourceLink.text.toString())
                        val token = sessionManager.fetchAuthToken() ?: ""
                        val (res, _) = withContext(Dispatchers.IO) { ApiClient.uploadFile("/api/upload", token, zipFile, logoFile, fields) }
                        if (res != null && JSONObject(res).getBoolean("success")) {
                            Toast.makeText(this@HomeActivity, "سورس برای تایید ادمین ارسال شد", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@HomeActivity, "خطا در ارسال", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupProfileView() {
        binding.btnChangePic.setOnClickListener { /* می‌توانید اینجا نیز FilePicker راه بیندازید */ }
        binding.btnUpdateProfile.setOnClickListener {
            val fullName = binding.etFullName.text.toString()
            CoroutineScope(Dispatchers.Main).launch {
                val payload = JSONObject().put("full_name", fullName).toString()
                val token = sessionManager.fetchAuthToken() ?: ""
                withContext(Dispatchers.IO) { ApiClient.postRequest("/api/profile/update", payload, token) }
                Toast.makeText(this@HomeActivity, "پروفایل آپدیت شد", Toast.LENGTH_SHORT).show()
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
                    binding.tvEmailProfile.text = json.getString("email")
                }
            }
        }
    }

    private fun File.copyFromUri(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { input ->
            this.outputStream().use { output -> input.copyTo(output) }
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