package com.longynus.churrasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.ChurrascoDateUtils
import com.longynus.churrasco.R
import com.longynus.churrasco.data.model.Event

/** Lista de churrascos (ativos, histórico ou convites), com o mesmo cartão para todos. */
class EventAdapter(
    @LayoutRes private val layout: Int,
    private val onClick: (Event) -> Unit,
) : ListAdapter<Event, EventAdapter.Holder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<Event>() {
        override fun areItemsTheSame(a: Event, b: Event) = a.id == b.id
        override fun areContentsTheSame(a: Event, b: Event) = a == b
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateTime: TextView = view.findViewById(R.id.tvDateTime)
        private val local: TextView = view.findViewById(R.id.tvLocal)
        private val creator: TextView = view.findViewById(R.id.tvCreator)
        private val details: Button? = view.findViewById(R.id.btnDetails)

        fun bind(event: Event) {
            val context = itemView.context
            dateTime.text = ChurrascoDateUtils.eventDateTime(event.churrascoDate, event.hora)
            local.text = context.getString(R.string.list_local, event.local)
            creator.text = when {
                event.isOrganizer -> context.getString(R.string.list_you_organize)
                event.myStatus == "invited" -> context.getString(R.string.list_invited_by, event.organizer.displayName)
                else -> context.getString(R.string.list_organized_by, event.organizer.displayName, 1 + event.confirmed.size)
            }
            itemView.setOnClickListener { onClick(event) }
            details?.setOnClickListener { onClick(event) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(layout, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
}
