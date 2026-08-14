package ir.codemarket.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ir.codemarket.app.databinding.ItemFeedPostBinding

class PostAdapter(private val posts: List<Pair<String, String>>) : RecyclerView.Adapter<PostAdapter.ViewHolder>() {
    inner class ViewHolder(val b: ItemFeedPostBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemFeedPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = posts[position]
        holder.b.tvUsername.text = post.first
        holder.b.tvContent.text = post.second
    }

    override fun getItemCount() = posts.size
}