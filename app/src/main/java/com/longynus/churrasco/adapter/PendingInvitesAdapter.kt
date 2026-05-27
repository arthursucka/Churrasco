package com.longynus.churrasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.ChurrascoDateUtils
import com.longynus.churrasco.R
import com.longynus.churrasco.model.Churrasco

class PendingInvitesAdapter(
    private val invites: MutableList<Churrasco>,
    private val onDetailsClick: (Churrasco) -> Unit
) : RecyclerView.Adapter<PendingInvitesAdapter.InviteVH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InviteVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pending_invite, parent, false)
        return InviteVH(view)
    }

    override fun onBindViewHolder(holder: InviteVH, position: Int) {
        holder.bind(invites[position])
    }

    override fun getItemCount(): Int = invites.size

    /** Atualiza toda a lista de convites e notifica o RecyclerView */
    fun updateData(newInvites: List<Churrasco>) {
        invites.clear()
        invites.addAll(newInvites)
        notifyDataSetChanged()
    }

    inner class InviteVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCreator: TextView    = itemView.findViewById(R.id.tvCreator)
        private val tvDateTime: TextView   = itemView.findViewById(R.id.tvDateTime)
        private val tvLocal: TextView      = itemView.findViewById(R.id.tvLocal)
        private val btnDetails: Button     = itemView.findViewById(R.id.btnDetails)

        fun bind(churrasco: Churrasco) {
            tvCreator.text  = "Convite de ${churrasco.createdBy}"
            tvDateTime.text = ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)
            tvLocal.text = "Local: ${churrasco.local}"
            btnDetails.setOnClickListener { onDetailsClick(churrasco) }
        }
    }
}
