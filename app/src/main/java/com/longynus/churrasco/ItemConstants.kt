package com.longynus.churrasco

/**
 * Lista centralizada de todos os itens possíveis de um churrasco.
 * Usada para criar dinamicamente os CheckBox em CreateChurrascoActivity1
 * e para filtrar quais ainda faltam em EscolherItensActivity.
 */
object ItemConstants {
    val listaCompletaDeItens: List<String> = listOf(
        "Carvão",
        "Gelo",
        "Refrigerante",
        "Cerveja",
        "Destilados",
        "Limão",
        "Vinagrete",
        "Pão",
        "Sobremesa",
        "Carne Bovina",
        "Carne Suína",
        "Linguiça",
        "Frango",
        "Coração",
        "Pão de Alho",
        "Queijo Coalho",
        "Queijo Provolone"
    )
}
