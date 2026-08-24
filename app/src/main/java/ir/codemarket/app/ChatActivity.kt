package ir.codemarket.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import java.util.regex.Pattern

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

        if (sessionManager.isDarkMode()) {
            setTheme(R.style.Theme_CodeMarket_Dark)
        } else {
            setTheme(R.style.Theme_CodeMarket_Light)
        }

        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (sessionManager.isDarkMode()) {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_dark)
        } else {
            binding.root.setBackgroundResource(R.drawable.bg_gradient_light)
        }

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

        binding.imgChatTargetPic.setOnClickListener { 
            if (targetUserId != currentUserId) {
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("user_id", targetUserId)
                startActivity(intent)
            }
        }
        binding.tvChatTargetName.setOnClickListener { binding.imgChatTargetPic.performClick() }

        setupMessageInputFormatting()

        binding.btnAttach.setOnClickListener { pickMedia.launch(arrayOf("image/*", "video/*", "audio/*")) }
        
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

    // --- افزودن منوی فرمت‌دهی (اسپویل، برجسته، لینک) با نگه‌داشتن انگشت روی متن قبل از ارسال ---
    private fun setupMessageInputFormatting() {
        binding.etMessage.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                menu.add(0, 1, 0, "اسپویل (سانسور)").setIcon(android.R.drawable.ic_menu_view)
                menu.add(0, 2, 0, "برجسته (Bold)").setIcon(android.R.drawable.ic_menu_edit)
                menu.add(0, 3, 0, "خط‌خورده (Strike)").setIcon(android.R.drawable.ic_menu_delete)
                menu.add(0, 4, 0, "لینک دادن").setIcon(android.R.drawable.ic_menu_share)
                return true
            }
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                val start = binding.etMessage.selectionStart
                val end = binding.etMessage.selectionEnd
                if (start < 0 || end < 0) return false
                val text = binding.etMessage.text
                when (item.itemId) {
                    1 -> { text.insert(end, "||"); text.insert(start, "||") }
                    2 -> { text.insert(end, "**"); text.insert(start, "**") }
                    3 -> { text.insert(end, "~~"); text.insert(start, "~~") }
                    4 -> { text.insert(end, "]()"); text.insert(start, "[") }
                }
                mode.finish()
                return true
            }
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
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
        chatAdapter = ChatMessagesAdapter(
            messagesList, 
            markwon, 
            sessionManager.getTextSize(), 
            currentUserId,
            onReplyClick = { msg -> startReply(msg) },
            onMessageLongClick = { msg, position -> showMessageOptions(msg, position) }
        )
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.recyclerChat.layoutManager = layoutManager
        binding.recyclerChat.adapter = chatAdapter
    }
    
    private fun startReply(msg: ChatMessageItem) {
        replyToId = msg.id
        binding.layoutReplyPreview.visibility = View.VISIBLE
        binding.tvReplyPreviewName.text = if(msg.senderId == currentUserId) "شما" else targetUsername
        binding.tvReplyPreviewText.text = if(msg.text.isNotEmpty()) msg.text else "رسانه"
        binding.etMessage.requestFocus()
    }

    // --- منوی شیشه‌ای، فوق گرافیکی و گوشه‌گرد از پایین (BottomSheet) ---
    private fun showMessageOptions(msg: ChatMessageItem, position: Int) {
        val bottomSheet = BottomSheetDialog(this)
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 50)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(if (sessionManager.isDarkMode()) "#1A1A2E" else "#F3F4F6"))
                cornerRadii = floatArrayOf(80f, 80f, 80f, 80f, 0f, 0f, 0f, 0f) // گوشه‌های کاملاً گرد و نرم
            }
        }

        val title = android.widget.TextView(this).apply {
            text = "گزینه‌های پیام"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        container.addView(title)

        fun addOption(titleStr: String, iconRes: Int, onClick: () -> Unit) {
            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 30, 20, 30)
                gravity = Gravity.CENTER_VERTICAL
                background = ContextCompat.getDrawable(this@ChatActivity, android.R.attr.selectableItemBackground)
                setOnClickListener { bottomSheet.dismiss(); onClick() }
            }
            val icon = ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(Color.parseColor("#5288C1"))
                layoutParams = LinearLayout.LayoutParams(64, 64)
            }
            val textV = android.widget.TextView(this).apply {
                text = titleStr
                textSize = 16f
                setTextColor(Color.parseColor(if (sessionManager.isDarkMode()) "#FFFFFF" else "#000000"))
                setPadding(40, 0, 40, 0)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            itemLayout.addView(icon)
            itemLayout.addView(textV)
            container.addView(itemLayout)
        }

        addOption("پاسخ دادن (نقل قول)", android.R.drawable.ic_menu_revert) { startReply(msg) }
        
        if (msg.text.isNotEmpty()) {
            addOption("کپی کردن متن", android.R.drawable.ic_menu_edit) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Message", msg.text))
                Toast.makeText(this, "متن کپی شد", Toast.LENGTH_SHORT).show()
            }
        }
        
        if (msg.senderId == currentUserId) {
            if (msg.text.isNotEmpty()) addOption("ویرایش پیام", android.R.drawable.ic_menu_edit) { editMessage(msg, position) }
            addOption("حذف پیام", android.R.drawable.ic_menu_delete) { deleteMessage(msg.id, position) }
        }

        bottomSheet.setContentView(container)
        (container.parent as View).setBackgroundColor(Color.TRANSPARENT)
        bottomSheet.show()
    }

    private fun deleteMessage(msgId: Int, position: Int) {
        val token = sessionManager.fetchAuthToken() ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/chats/message/$msgId/delete", "{}", token) }
            if (res != null && JSONObject(res).getBoolean("success")) {
                messagesList.removeAt(position)
                chatAdapter.notifyItemRemoved(position)
            }
        }
    }

    private fun editMessage(msg: ChatMessageItem, position: Int) {
        val editText = EditText(this).apply {
            setText(msg.text)
            setPadding(40, 40, 40, 40)
        }
        MarkdownUtils.applyMarkdownShortcuts(editText)

        AlertDialog.Builder(this)
            .setTitle("ویرایش پیام")
            .setView(editText)
            .setPositiveButton("ثبت") { _, _ ->
                val newText = editText.text.toString()
                val token = sessionManager.fetchAuthToken() ?: return@setPositiveButton
                CoroutineScope(Dispatchers.Main).launch {
                    val payload = JSONObject().put("text", newText).toString()
                    val (res, _) = withContext(Dispatchers.IO) { ApiClient.postRequest("/api/chats/message/${msg.id}/edit", payload, token) }
                    if (res != null && JSONObject(res).getBoolean("success")) {
                        messagesList[position] = messagesList[position].copy(text = newText, isEdited = true)
                        chatAdapter.notifyItemChanged(position)
                    }
                }
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun loadMessages() {
        val token = sessionManager.fetchAuthToken() ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val (res, _) = withContext(Dispatchers.IO) { ApiClient.getRequest("/api/chats/$targetUserId", token) }
            if (res != null) {
                val json = JSONObject(res)
                if (json.getBoolean("success")) {
                    val arr = json.getJSONArray("messages")
                    messagesList.clear()
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        messagesList.add(ChatMessageItem(
                            c.optInt("id", 0), c.optInt("sender_id", -1), c.optString("text", ""), 
                            c.optString("media", ""), c.optString("media_type", ""), c.optInt("reply_to_id", -1),
                            c.optString("reply_to_username", ""), c.optString("reply_to_text", ""),
                            c.optString("date", ""), c.optBoolean("is_read", false), c.optBoolean("is_edited", false)
                        ))
                    }
                    chatAdapter.notifyDataSetChanged()
                    binding.recyclerChat.scrollToPosition(messagesList.size - 1)
                }
            }
        }
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
                    val ext = if (mimeType.contains("video")) "mp4" else if (mimeType.contains("audio")) "mp3" else "jpg"
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

// --- کلاس کاستوم برای اسپویل (سانسور) کردن متن‌ها ---
class SpoilerSpan : ClickableSpan() {
    private var isHidden = true
    override fun updateDrawState(ds: TextPaint) {
        super.updateDrawState(ds)
        if (isHidden) {
            ds.color = Color.parseColor("#80888888") // رنگ خاکستری تیره برای سانسور
            ds.bgColor = Color.parseColor("#80888888")
        } else {
            ds.bgColor = Color.TRANSPARENT // برداشتن سانسور
        }
        ds.isUnderlineText = false
    }
    override fun onClick(widget: View) {
        if (isHidden) {
            isHidden = false
            widget.invalidate()
        }
    }
}

data class ChatMessageItem(
    val id: Int, val senderId: Int, val text: String, val media: String, val mediaType: String,
    val replyToId: Int, val replyToUsername: String, val replyToText: String, val date: String, val isRead: Boolean, val isEdited: Boolean
)

class ChatMessagesAdapter(
    private val items: List<ChatMessageItem>, 
    private val markwon: Markwon, 
    private val size: Float, 
    private val currentUserId: Int,
    private val onReplyClick: (ChatMessageItem) -> Unit,
    private val onMessageLongClick: (ChatMessageItem, Int) -> Unit
) : RecyclerView.Adapter<ChatMessagesAdapter.ViewHolder>() {
    
    class ViewHolder(val b: ItemChatMessageBinding) : RecyclerView.ViewHolder(b.root)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isMe = item.senderId == currentUserId

        holder.b.layoutMessageRoot.gravity = if (isMe) Gravity.END else Gravity.START
        
        if (isMe) {
            holder.b.cardMessageBubble.setCardBackgroundColor(Color.parseColor("#225288C1"))
            holder.b.imgMessageStatus.visibility = View.VISIBLE
            holder.b.imgMessageStatus.setImageResource(android.R.drawable.checkbox_on_background)
        } else {
            holder.b.cardMessageBubble.setCardBackgroundColor(Color.parseColor("#1A888888"))
            holder.b.imgMessageStatus.visibility = View.GONE
        }

        if (item.text.isNotEmpty()) {
            holder.b.tvMessageText.visibility = View.VISIBLE
            holder.b.tvMessageText.textSize = size
            
            // پردازش متن برای مارک‌داون و اسپویلر
            val spanned = markwon.toMarkdown(item.text) as SpannableStringBuilder
            val matcher = Pattern.compile("\\|\\|(.*?)\\|\\|").matcher(spanned.toString())
            var offset = 0
            while (matcher.find()) {
                val start = matcher.start() - offset
                val end = matcher.end() - offset
                val spoilText = matcher.group(1)
                spanned.replace(start, end, spoilText)
                spanned.setSpan(SpoilerSpan(), start, start + spoilText!!.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                offset += 4
            }
            holder.b.tvMessageText.text = spanned
            holder.b.tvMessageText.movementMethod = LinkMovementMethod.getInstance()
            Linkify.addLinks(holder.b.tvMessageText, Linkify.ALL)
        } else {
            holder.b.tvMessageText.visibility = View.GONE
        }

        val editMark = if (item.isEdited) " (ویرایش شده) " else " "
        holder.b.tvMessageTime.text = TimeUtils.getTimeAgo(item.date) + editMark

        if (item.replyToId != -1 && item.replyToUsername.isNotEmpty()) {
            holder.b.layoutReplyQuote.visibility = View.VISIBLE
            holder.b.tvReplyUsernameQuote.text = item.replyToUsername
            holder.b.tvReplyTextQuote.text = item.replyToText
        } else {
            holder.b.layoutReplyQuote.visibility = View.GONE
        }

        if (item.media.isNotEmpty()) {
            val fullMediaUrl = if (item.media.startsWith("http")) item.media else NativeLib.getBaseUrl() + item.media
            if (item.mediaType == "voice") {
                holder.b.layoutVoice.visibility = View.VISIBLE
                holder.b.layoutMedia.visibility = View.GONE
                holder.b.btnPlayVoice.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(fullMediaUrl), "audio/*")
                    holder.b.root.context.startActivity(intent)
                }
            } else {
                holder.b.layoutMedia.visibility = View.VISIBLE
                holder.b.layoutVoice.visibility = View.GONE
                Glide.with(holder.b.root.context).load(fullMediaUrl).into(holder.b.imgMessageMedia)
                
                if (item.mediaType == "video") {
                    holder.b.viewMediaOverlay.visibility = View.VISIBLE
                    holder.b.imgPlayVideo.visibility = View.VISIBLE
                    holder.b.tvMediaInfo.visibility = View.VISIBLE
                    holder.b.tvMediaInfo.text = "ویدیو" 
                } else {
                    holder.b.viewMediaOverlay.visibility = View.GONE
                    holder.b.imgPlayVideo.visibility = View.GONE
                    holder.b.tvMediaInfo.visibility = View.GONE
                }

                holder.b.imgMessageMedia.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW)
                    val type = if (item.mediaType == "video") "video/*" else "image/*"
                    intent.setDataAndType(Uri.parse(fullMediaUrl), type)
                    holder.b.root.context.startActivity(intent)
                }
            }
        } else {
            holder.b.layoutMedia.visibility = View.GONE
            holder.b.layoutVoice.visibility = View.GONE
        }

        // کلیک برای ریپلای تلگرامی و لانگ‌کلیک برای منوی گوشه‌گرد
        holder.b.cardMessageBubble.setOnClickListener { onReplyClick(item) }
        holder.b.cardMessageBubble.setOnLongClickListener { onMessageLongClick(item, position); true }
    }
    
    override fun getItemCount(): Int = items.size
}