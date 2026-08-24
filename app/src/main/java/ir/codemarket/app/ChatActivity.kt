package ir.codemarket.app

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import io.noties.markwon.Markwon
import ir.codemarket.app.databinding.ActivityChatBinding
import ir.codemarket.app.databinding.ItemChatMessageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var markwon: Markwon
    
    private var targetUserId: Int = -1
    private var targetUsername: String = ""
    private var currentUserId: Int = -1

    private val messagesList = mutableListOf<ChatMessageItem>()
    private lateinit var chatAdapter: ChatMessagesAdapter

    private var replyToId: Int? = null
    private var selectedMediaUri: Uri? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedMediaUri = it
            binding.layoutMediaPreview.visibility = View.VISIBLE
            Glide.with(this).load(it).into(binding.imgMediaPreview)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        sessionManager = SessionManager(this)
        markwon = MarkdownUtils.createMarkwon(this)
        extractCurrentUserId()

        if (sessionManager.isDarkMode()) setTheme(R.style.Theme_CodeMarket_Dark)
        else setTheme(R.style.Theme_CodeMarket_Light)

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        else binding.root.setBackgroundResource(R.drawable.bg_gradient_light)

        targetUserId = intent.getIntExtra("target_id", -1)
        targetUsername = intent.getStringExtra("target_username") ?: "کاربر"
        
        if (targetUserId == -1) { finish(); return }

        binding.tvChatTargetName.text = targetUsername
        if (targetUserId == currentUserId) {
            binding.tvChatTargetName.text = "پیام‌های ذخیره شده"
            binding.imgChatTargetPic.setImageResource(android.R.drawable.ic_menu_save)
            binding.imgChatTargetPic.setColorFilter(Color.parseColor("#5288C1"))
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRefresh.setOnClickListener { 
            it.animate().rotationBy(360f).setDuration(500).start()
            loadMessages() 
        }

        binding.imgChatTargetPic.setOnClickListener { 
            if (targetUserId != currentUserId) {
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("user_id", targetUserId)
                startActivity(intent)
            }
        }

        binding.tvChatTargetName.setOnClickListener { binding.imgChatTargetPic.performClick() }

        MarkdownUtils.applyMarkdownShortcuts(binding.etMessage)

        binding.btnAttach.setOnClickListener { pickMedia.launch(arrayOf("image/*", "video/*")) }
        
        binding.btnCancelMedia.setOnClickListener {
            selectedMediaUri = null
            binding.layoutMediaPreview.visibility = View.GONE
            binding.imgMediaPreview.setImageDrawable(null)
        }

        binding.btnCancelReply.setOnClickListener {
            replyToId = null
            binding.layoutReplyPreview.visibility = View.GONE
        }

        binding.btnSendMessage.setOnClickListener {
            val text = binding.etMessage.text.toString()
            if (text.isNotEmpty() || selectedMediaUri != null) {
                sendMessage(text, selectedMediaUri)
            }
        }

        setupRecyclerView()
        loadMessages()
    }

    private fun extractCurrentUserId() {
        val token = sessionManager.fetchAuthToken() ?: return
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                currentUserId = JSONObject(payload).getInt("user_id")
            }
        } catch (e: Exception) { }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatMessagesAdapter(messagesList, markwon, sessionManager.getTextSize(), currentUserId) { msg ->
            replyToId = msg.id
            binding.layoutReplyPreview.visibility = View.VISIBLE
            binding.tvReplyPreviewName.text = if(msg.senderId == currentUserId) "شما" else targetUsername
            binding.tvReplyPreviewText.text = if(msg.text.isNotEmpty()) msg.text else "رسانه"
            binding.etMessage.requestFocus()
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.recyclerChat.layoutManager = layoutManager
        binding.recyclerChat.adapter = chatAdapter
    }

    private fun loadMessages() {
        val token = sessionManager.fetchAuthToken() ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/chats/$targetUserId", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    val arr = json.getJSONArray("messages")
                    parseMessages(arr)
                    binding.recyclerChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }
    }

    private fun parseMessages(array: JSONArray) {
        messagesList.clear()
        for (i in 0 until array.length()) {
            val c = array.getJSONObject(i)
            messagesList.add(ChatMessageItem(
                c.optInt("id", 0), 
                c.optInt("sender_id", -1), 
                c.optString("text", ""), 
                c.optString("media", ""),
                c.optString("media_type", ""),
                c.optInt("reply_to_id", -1),
                c.optString("reply_to_username", ""),
                c.optString("reply_to_text", ""),
                c.optString("date", ""),
                c.optBoolean("is_read", false)
            ))
        }
        chatAdapter.notifyDataSetChanged()
    }

    private fun sendMessage(text: String, mediaUri: Uri?) {
        binding.btnSendMessage.isEnabled = false
        val token = sessionManager.fetchAuthToken() ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(NativeLib.getBaseUrl() + "/api/chats/send")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.doOutput = true

                if (mediaUri == null) {
                    connection.setRequestProperty("Content-Type", "application/json")
                    val payload = JSONObject().apply {
                        put("target_id", targetUserId)
                        put("text", text)
                        if (replyToId != null) put("reply_to_id", replyToId)
                    }.toString()
                    connection.outputStream.write(payload.toByteArray())
                } else {
                    val boundary = "Boundary-" + System.currentTimeMillis()
                    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    val outputStream = DataOutputStream(connection.outputStream)

                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"target_id\"\r\n\r\n$targetUserId\r\n")
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"text\"\r\n\r\n$text\r\n")
                    if (replyToId != null) {
                        outputStream.writeBytes("--$boundary\r\n")
                        outputStream.writeBytes("Content-Disposition: form-data; name=\"reply_to_id\"\r\n\r\n$replyToId\r\n")
                    }

                    val mimeType = contentResolver.getType(mediaUri) ?: "application/octet-stream"
                    val ext = if (mimeType.contains("video")) "mp4" else "jpg"
                    outputStream.writeBytes("--$boundary\r\n")
                    outputStream.writeBytes("Content-Disposition: form-data; name=\"media\"; filename=\"upload.$ext\"\r\n")
                    outputStream.writeBytes("Content-Type: $mimeType\r\n\r\n")
                    
                    contentResolver.openInputStream(mediaUri)?.use { input -> input.copyTo(outputStream) }
                    outputStream.writeBytes("\r\n--$boundary--\r\n")
                    outputStream.flush()
                }

                val responseCode = connection.responseCode
                withContext(Dispatchers.Main) {
                    binding.btnSendMessage.isEnabled = true
                    if (responseCode in 200..299) {
                        binding.etMessage.setText("")
                        binding.btnCancelMedia.performClick()
                        binding.btnCancelReply.performClick()
                        loadMessages()
                    } else if (responseCode == 429) {
                        Toast.makeText(this@ChatActivity, "بیش از حد مجاز! یک دقیقه صبر کنید.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.btnSendMessage.isEnabled = true
                    Toast.makeText(this@ChatActivity, "خطای شبکه", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

data class ChatMessageItem(
    val id: Int, val senderId: Int, val text: String, val media: String, val mediaType: String,
    val replyToId: Int, val replyToUsername: String, val replyToText: String, val date: String, val isRead: Boolean
)

class ChatMessagesAdapter(
    private val items: List<ChatMessageItem>, private val markwon: Markwon, private val size: Float, private val currentUserId: Int,
    private val onReplyClick: (ChatMessageItem) -> Unit
) : RecyclerView.Adapter<ChatMessagesAdapter.ViewHolder>() {
    
    class ViewHolder(val b: ItemChatMessageBinding) : RecyclerView.ViewHolder(b.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isMe = item.senderId == currentUserId

        holder.b.layoutMessageRoot.gravity = if (isMe) Gravity.END else Gravity.START
        
        if (isMe) {
            holder.b.cardMessageBubble.setCardBackgroundColor(Color.parseColor("#225288C1")) // آبی شیشه‌ای برای خودم
            holder.b.imgMessageStatus.visibility = View.VISIBLE
            holder.b.imgMessageStatus.setImageResource(if (item.isRead) R.drawable.ic_tick else R.drawable.ic_tick) // در آینده می‌توان دو تیک کرد
        } else {
            holder.b.cardMessageBubble.setCardBackgroundColor(Color.parseColor("#1A888888")) // طوسی شیشه‌ای برای طرف مقابل
            holder.b.imgMessageStatus.visibility = View.GONE
        }

        if (item.text.isNotEmpty()) {
            holder.b.tvMessageText.visibility = View.VISIBLE
            holder.b.tvMessageText.textSize = size
            MarkdownUtils.setMarkdownText(markwon, holder.b.tvMessageText, item.text)
        } else {
            holder.b.tvMessageText.visibility = View.GONE
        }

        holder.b.tvMessageTime.text = TimeUtils.getTimeAgo(item.date)

        if (item.replyToId != -1 && item.replyToUsername.isNotEmpty()) {
            holder.b.layoutReplyQuote.visibility = View.VISIBLE
            holder.b.tvReplyUsernameQuote.text = item.replyToUsername
            holder.b.tvReplyTextQuote.text = item.replyToText
        } else {
            holder.b.layoutReplyQuote.visibility = View.GONE
        }

        if (item.media.isNotEmpty()) {
            holder.b.layoutMedia.visibility = View.VISIBLE
            val fullMediaUrl = if (item.media.startsWith("http")) item.media else NativeLib.getBaseUrl() + item.media
            Glide.with(holder.b.root.context).load(fullMediaUrl).into(holder.b.imgMessageMedia)
            
            if (item.mediaType == "video" || item.mediaType == "voice") {
                holder.b.imgPlayVideo.visibility = View.VISIBLE
                holder.b.tvMediaInfo.visibility = View.VISIBLE
                holder.b.tvMediaInfo.text = if (item.mediaType == "voice") "ویس" else "ویدیو"
            } else {
                holder.b.imgPlayVideo.visibility = View.GONE
                holder.b.tvMediaInfo.visibility = View.GONE
            }

            holder.b.imgMessageMedia.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW)
                val type = if (item.mediaType == "video") "video/*" else if (item.mediaType == "voice") "audio/*" else "image/*"
                intent.setDataAndType(Uri.parse(fullMediaUrl), type)
                holder.b.root.context.startActivity(intent)
            }
        } else {
            holder.b.layoutMedia.visibility = View.GONE
        }

        holder.b.cardMessageBubble.setOnClickListener { onReplyClick(item) }
    }
    
    override fun getItemCount(): Int = items.size
}