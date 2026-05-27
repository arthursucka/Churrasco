package com.longynus.churrasco.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.model.Message
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatVH>() {

    private val items = mutableListOf<Message>()
    private val dateFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun addMessage(msg: Message) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val tv = TextView(parent.context).apply {
            textSize = 14f
            setPadding(8, 4, 8, 4)
        }
        return ChatVH(tv)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        val m = items[position]
        val time = dateFmt.format(Date(m.timestamp))
        (holder.itemView as TextView).text = "[${time}] ${m.sender}: ${m.text}"
    }

    class ChatVH(view: TextView) : RecyclerView.ViewHolder(view)
}
