package com.longynus.churrasco.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.longynus.churrasco.ChurrascoDateUtils
import com.longynus.churrasco.R
import com.longynus.churrasco.model.Churrasco

/**
 * Adapter para exibir a lista de churrascos ativos.
 * Mantém um buffer mutável de itens e expõe updateData() para trocar a lista.
 */
class ActiveChurrascoAdapter(
    private val items: MutableList<Churrasco>,
    private val onClick: (Churrasco) -> Unit
) : RecyclerView.Adapter<ActiveChurrascoAdapter.ViewHolder>() {

    /** ViewHolder mantém as referências às views de cada item */
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDateTime: TextView = view.findViewById(R.id.tvDateTime)
        private val tvLocal: TextView    = view.findViewById(R.id.tvLocal)
        private val tvCreator: TextView  = view.findViewById(R.id.tvCreator)

        /** Liga os dados do churrasco às views */
        fun bind(churrasco: Churrasco) {
            tvDateTime.text = ChurrascoDateUtils.eventDateTime(churrasco.churrascoDate, churrasco.hora)
            tvLocal.text    = "Local: ${churrasco.local}"
            tvCreator.text  = "Criado por ${churrasco.createdBy}"
            itemView.setOnClickListener { onClick(churrasco) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_churrasco, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    /**
     * Atualiza completamente a lista de itens e notifica o RecyclerView.
     */
    fun updateData(newItems: List<Churrasco>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}


