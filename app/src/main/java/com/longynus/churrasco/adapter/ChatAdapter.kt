package com.longynus.churrasco.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.R
import com.longynus.churrasco.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter(
    private val currentUser: String
) : RecyclerView.Adapter<ChatAdapter.ChatVH>() {

    private val items = mutableListOf<Message>()
    private val dateFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    val isEmpty: Boolean
        get() = items.isEmpty()

    fun addMessage(msg: Message) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatVH(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        holder.bind(items[position], currentUser, dateFmt)
    }

    class ChatVH(view: View) : RecyclerView.ViewHolder(view) {
        private val row: FrameLayout = view.findViewById(R.id.messageRow)
        private val bubble: LinearLayout = view.findViewById(R.id.messageBubble)
        private val txtSender: TextView = view.findViewById(R.id.txtSender)
        private val txtMessage: TextView = view.findViewById(R.id.txtMessage)
        private val txtTime: TextView = view.findViewById(R.id.txtTime)

        fun bind(message: Message, currentUser: String, dateFmt: SimpleDateFormat) {
            val isMine = message.sender == currentUser
            val context = itemView.context

            (bubble.layoutParams as FrameLayout.LayoutParams).gravity =
                if (isMine) Gravity.END else Gravity.START

            bubble.background = context.getDrawable(
                if (isMine) R.drawable.bg_chat_mine else R.drawable.bg_chat_other
            )

            txtSender.visibility = if (isMine) View.GONE else View.VISIBLE
            txtSender.text = message.sender
            txtMessage.text = message.text
            txtTime.text = dateFmt.format(Date(message.timestamp))

            txtMessage.setTextColor(
                context.getColor(if (isMine) R.color.md_on_primary else R.color.mainInk)
            )
            txtTime.setTextColor(
                context.getColor(if (isMine) R.color.md_primary_container else R.color.mainMuted)
            )
            txtSender.setTextColor(context.getColor(R.color.mainMuted))

            row.setPadding(
                if (isMine) 54.dp() else 0,
                4.dp(),
                if (isMine) 0 else 54.dp(),
                4.dp()
            )
        }

        private fun Int.dp(): Int = (this * itemView.resources.displayMetrics.density).toInt()
    }
}
