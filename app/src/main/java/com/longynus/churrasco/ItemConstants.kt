import com.longynus.churrasco.R

object ItemConstants {
    data class Item(val checkboxId: Int, val label: String)

    val listaCompletaDeItens = listOf(
        Item(R.id.chkCarvao, "Carvão"),
        Item(R.id.chkGelo, "Gelo"),
        Item(R.id.chkRefrigerante, "Refrigerante"),
        Item(R.id.chkCerveja, "Cerveja"),
        Item(R.id.chkDestilados, "Destilados"),
        Item(R.id.chkLimao, "Limão"),
        Item(R.id.chkVinagrete, "Vinagrete"),
        Item(R.id.chkPao, "Pão"),
        Item(R.id.chkSobremesa, "Sobremesa"),
        Item(R.id.chkCarneBovina, "Carne Bovina"),
        Item(R.id.chkCarneSuina, "Carne Suína"),
        Item(R.id.chkLinguica, "Linguiça"),
        Item(R.id.chkFrango, "Frango"),
        Item(R.id.chkCoracao, "Coração"),
        Item(R.id.chkPaoDeAlho, "Pão de Alho"),
        Item(R.id.chkQueijoCoalho, "Queijo Coalho"),
        Item(R.id.chkQueijoProvolone, "Queijo Provolone")
    )
}
