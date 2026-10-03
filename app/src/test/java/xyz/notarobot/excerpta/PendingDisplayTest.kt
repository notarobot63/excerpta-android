package xyz.notarobot.excerpta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDisplayTest {

    private val recette = PendingLink(
        url = "https://cuisine.example/tarte", title = "Tarte aux Pommes", tags = listOf("recettes"),
        note = "pour dimanche", folderId = 3, isPublic = false,
    )
    private val article = PendingLink(
        url = "https://news.example/a", title = "Un article", tags = emptyList(),
        note = "", folderId = null, isPublic = true,
    )
    private val both = listOf(recette, article)

    @Test
    fun `sans filtre, tout s'affiche dans l'ordre, marque en attente`() {
        val items = PendingDisplay.items(both)
        assertEquals(listOf(recette.url, article.url), items.map { it.url })
        assertTrue(items.all { it.isPending })
    }

    @Test
    fun `ids negatifs, distincts et reversibles`() {
        val ids = PendingDisplay.items(both).map { it.id }
        assertTrue(ids.all { it < 0 })
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf(0, 1), ids.map { PendingDisplay.indexOf(it) })
        assertNull(PendingDisplay.indexOf(42))
    }

    @Test
    fun `l'index survit au filtrage`() {
        val item = PendingDisplay.items(both, query = "article").single()
        assertEquals(1, PendingDisplay.indexOf(item.id))
    }

    @Test
    fun `filtre par tag, sans casse`() {
        assertEquals(listOf(recette.url), PendingDisplay.items(both, tag = "Recettes").map { it.url })
        assertTrue(PendingDisplay.items(both, tag = "autre").isEmpty())
    }

    @Test
    fun `filtre par dossier`() {
        assertEquals(listOf(recette.url), PendingDisplay.items(both, groupId = 3).map { it.url })
        assertTrue(PendingDisplay.items(both, groupId = 4).isEmpty())
    }

    @Test
    fun `recherche, chaque mot sans casse, dans titre url note et tags`() {
        assertEquals(listOf(recette.url), PendingDisplay.items(both, query = "tarte POMMES").map { it.url })
        assertEquals(listOf(recette.url), PendingDisplay.items(both, query = "dimanche").map { it.url })
        assertEquals(listOf(recette.url), PendingDisplay.items(both, query = "cuisine.example").map { it.url })
        assertEquals(listOf(recette.url), PendingDisplay.items(both, query = "recettes").map { it.url })
        assertTrue(PendingDisplay.items(both, query = "tarte absent").isEmpty())
    }
}
