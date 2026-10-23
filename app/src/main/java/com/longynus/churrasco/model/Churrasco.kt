package com.longynus.churrasco.model

/**
 * Representa um convidado que confirmou presença,
 * com seu nome e os itens que ele vai levar.
 */
data class Guest(
    val name: String,
    val items: List<String> = emptyList()
)

/**
 * Modelo principal de um churrasco, conforme retornado pelo backend.
 */
data class Churrasco(
    val id: String,

    /** Data do churrasco no formato dd/MM/yyyy */
    val churrascoDate: String,

    /** Hora do churrasco no formato HH:mm */
    val hora: String,

    /** Local do evento */
    val local: String,

    /** Nome do criador do churrasco */
    val createdBy: String,

    /** Usuarios convidados para este churrasco. */
    val invitedUsers: List<String> = emptyList(),

    /**
     * Lista de todos os itens que já foram fornecidos:
     * tanto pelo criador quanto pelos convidados.
     */
    val fornecidosAgregados: List<String> = emptyList(),

    /**
     * Lista de convidados que confirmaram presença, com os itens escolhidos.
     */
    val guestsConfirmed: List<Guest> = emptyList(),

    /**
     * Lista com os nomes dos convidados que recusaram o convite.
     */
    val guestsDeclined: List<String> = emptyList(),

    /** Início do evento em epoch ms, calculado pelo servidor no horário de São Paulo. */
    val eventStartsAt: Long? = null,

    /** Itens que o organizador garantiu (null em eventos antigos). */
    val organizerItems: List<String>? = null
) {
    fun itemsReservedByOthers(username: String): Set<String> {
        val others = guestsConfirmed.filter { it.name != username }.flatMap { it.items }
        val organizer = organizerItems ?: fornecidosAgregados.filterNot {
            it in guestsConfirmed.flatMap { guest -> guest.items }
        }
        return (organizer + others).toSet()
    }

    fun ownItems(username: String): List<String> =
        guestsConfirmed.firstOrNull { it.name == username }?.items.orEmpty()
}
