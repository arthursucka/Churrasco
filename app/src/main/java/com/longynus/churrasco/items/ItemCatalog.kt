package com.longynus.churrasco.items

import com.longynus.churrasco.data.model.Contribution
import java.text.NumberFormat
import java.util.Locale

/** Um campo do cartão de uma categoria. Os valores viram `details` da contribuição (texto). */
sealed class ItemField(val key: String, val label: String) {
    /** Sugestões em chips; a pessoa também pode digitar outra coisa quando [allowCustom]. */
    class Choice(key: String, label: String, val options: List<String>, val allowCustom: Boolean = true) : ItemField(key, label)

    /** Contador com unidade já definida e atalhos comuns. */
    class Quantity(
        key: String,
        label: String,
        val unit: String,
        val step: Double,
        val presets: List<Double>,
        val pluralUnit: String = unit,
    ) : ItemField(key, label) {
        fun format(value: Double): String {
            val number = NumberFormat.getNumberInstance(PT_BR).apply { maximumFractionDigits = 1 }.format(value)
            return "$number ${if (value > 1.0 && pluralUnit != unit) pluralUnit else unit}"
        }
    }
}

data class ItemCategory(
    val name: String,
    val emoji: String,
    val fields: List<ItemField>,
    /** Nomes usados pela v1 que caem nesta categoria (só para exibir eventos antigos). */
    val aliases: List<String> = emptyList(),
) {
    val isOther get() = name == ItemCatalog.OTHER
}

private val PT_BR = Locale("pt", "BR")

private val TEMPERO = ItemField.Choice("tempero", "Tempero", listOf("Temperada", "Sem tempero", "Só sal grosso"))
private fun kg(presets: List<Double> = listOf(0.5, 1.0, 1.5, 2.0, 3.0)) =
    ItemField.Quantity("quantidade", "Quantidade", "kg", 0.5, presets)
private fun units(unit: String, plural: String, presets: List<Double>, step: Double = 1.0) =
    ItemField.Quantity("quantidade", "Quantidade", unit, step, presets, plural)

object ItemCatalog {
    const val OTHER = "outro"

    val categories: List<ItemCategory> = listOf(
        ItemCategory("Carne bovina", "🥩", listOf(
            ItemField.Choice("corte", "Corte", listOf("Picanha", "Fraldinha", "Contrafilé", "Maminha", "Alcatra", "Costela", "Cupim", "Ancho", "Chorizo")),
            kg(), TEMPERO,
        ), aliases = listOf("Carne Bovina")),
        ItemCategory("Carne suína", "🐖", listOf(
            ItemField.Choice("corte", "Corte", listOf("Costelinha", "Lombo", "Panceta", "Bisteca", "Pernil", "Copa lombo")),
            kg(), TEMPERO,
        ), aliases = listOf("Carne Suína")),
        ItemCategory("Frango", "🍗", listOf(
            ItemField.Choice("corte", "Corte", listOf("Coxinha da asa", "Asa", "Sobrecoxa", "Tulipa", "Peito")),
            kg(), TEMPERO,
        )),
        ItemCategory("Linguiça", "🌭", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Toscana", "Cuiabana", "Apimentada", "De frango", "Calabresa")),
            kg(),
        )),
        ItemCategory("Coração", "❤️", listOf(kg(listOf(0.5, 1.0, 2.0)), TEMPERO)),
        ItemCategory("Queijo", "🧀", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Coalho", "Provolone", "Muçarela de bola")),
            units("pacote", "pacotes", listOf(1.0, 2.0, 3.0)),
        ), aliases = listOf("Queijo Coalho", "Queijo Provolone")),
        ItemCategory("Pão de alho", "🧄", listOf(
            ItemField.Choice("sabor", "Sabor", listOf("Tradicional", "Com queijo", "Apimentado")),
            units("pacote", "pacotes", listOf(1.0, 2.0, 3.0)),
        ), aliases = listOf("Pão de Alho")),
        ItemCategory("Pão", "🍞", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Francês", "Baguete", "De queijo")),
            units("unidade", "unidades", listOf(10.0, 20.0, 30.0), step = 5.0),
        )),
        ItemCategory("Farofa", "🥣", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Pronta", "Caseira", "Com bacon")),
            units("pacote", "pacotes", listOf(1.0, 2.0)),
        )),
        ItemCategory("Vinagrete", "🥗", listOf(units("pote", "potes", listOf(1.0, 2.0)))),
        ItemCategory("Carvão", "🔥", listOf(kg(listOf(3.0, 5.0, 10.0)).let { ItemField.Quantity(it.key, it.label, "kg", 1.0, it.presets) })),
        ItemCategory("Gelo", "🧊", listOf(units("saco de 5 kg", "sacos de 5 kg", listOf(1.0, 2.0, 4.0)))),
        ItemCategory("Cerveja", "🍺", listOf(
            ItemField.Choice("marca", "Marca", listOf("Heineken", "Brahma", "Skol", "Original", "Amstel", "Spaten")),
            ItemField.Choice("embalagem", "Embalagem", listOf("Lata 350 ml", "Latão 473 ml", "Long neck", "Garrafa 600 ml")),
            units("unidade", "unidades", listOf(6.0, 12.0, 24.0), step = 6.0),
        )),
        ItemCategory("Refrigerante", "🥤", listOf(
            ItemField.Choice("sabor", "Sabor", listOf("Cola", "Cola zero", "Guaraná", "Laranja", "Limão")),
            ItemField.Choice("tamanho", "Tamanho", listOf("2 L", "1,5 L", "Lata 350 ml")),
            units("unidade", "unidades", listOf(1.0, 2.0, 4.0, 6.0)),
        )),
        ItemCategory("Água", "💧", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Sem gás", "Com gás"), allowCustom = false),
            units("garrafa", "garrafas", listOf(2.0, 6.0, 12.0)),
        )),
        ItemCategory("Destilados", "🥃", listOf(
            ItemField.Choice("tipo", "Tipo", listOf("Cachaça", "Vodka", "Whisky", "Gin")),
            units("garrafa", "garrafas", listOf(1.0, 2.0)),
        )),
        ItemCategory("Limão", "🍋", listOf(units("unidade", "unidades", listOf(10.0, 20.0), step = 5.0))),
        ItemCategory("Sobremesa", "🍰", listOf(
            ItemField.Choice("tipo", "O quê", listOf("Pudim", "Brownie", "Pavê", "Sorvete", "Frutas")),
        )),
        ItemCategory("Descartáveis", "🍽️", listOf(
            ItemField.Choice("tipo", "O quê", listOf("Pratos", "Copos", "Talheres", "Guardanapos", "Sacos de lixo")),
        )),
        ItemCategory(OTHER, "✍️", emptyList()),
    )

    fun find(name: String): ItemCategory? =
        categories.firstOrNull { it.name.equals(name, ignoreCase = true) || name in it.aliases }

    fun emoji(name: String) = find(name)?.emoji ?: "🍽️"

    fun label(name: String) = if (name == OTHER) "Outro" else (find(name)?.name ?: name)

    /** Ex.: "Picanha · 1,5 kg · Sem tempero" ou o texto livre de "Outro". */
    fun summary(contribution: Contribution): String {
        val category = find(contribution.category)
        val ordered = category?.fields?.mapNotNull { contribution.details[it.key] }.orEmpty()
        val extra = contribution.details.filterKeys { key -> category?.fields?.none { it.key == key } ?: true }.values
        return (ordered + extra + listOfNotNull(contribution.note.takeIf { it.isNotBlank() }))
            .joinToString(" · ")
    }
}
