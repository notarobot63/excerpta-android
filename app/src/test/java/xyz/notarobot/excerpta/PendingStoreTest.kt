package xyz.notarobot.excerpta

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun link(n: Int) = PendingLink(
        url = "https://example.org/$n", title = "t$n", tags = listOf("a", "b"),
        note = "", folderId = if (n % 2 == 0) n else null, isPublic = n % 2 == 0,
    )

    private val ok = ApiClient.Result(true, R.string.link_saved)
    private val offline = ApiClient.Result(false, R.string.network_error, "", isNetworkError = true)
    private val refused = ApiClient.Result(false, R.string.invalid_url_rejected)

    private fun store(max: Int = 200) = PendingStore(File(tmp.root, "q.json"), max)

    @Test
    fun `aller-retour fidele sur disque`() {
        val s = store()
        s.enqueue(link(1)); s.enqueue(link(2))
        assertEquals(listOf(link(1), link(2)), PendingStore(File(tmp.root, "q.json")).load())
    }

    @Test
    fun `refuse au-dela du plafond`() {
        val s = store(max = 2)
        s.enqueue(link(1)); s.enqueue(link(2))
        assertEquals(PendingStore.EnqueueResult.FULL, s.enqueue(link(3)))
        assertEquals(2, s.load().size)
    }

    @Test
    fun `un echec reseau garde le lien et arrete l'envoi`() = runBlocking {
        val s = store()
        (1..3).forEach { s.enqueue(link(it)) }
        var calls = 0
        val report = s.flush { calls++; offline }!!
        assertEquals(1, calls)
        assertEquals(0, report.sent)
        assertEquals(3, s.load().size)
    }

    @Test
    fun `succes et refus sortent de la file, le refus est compte`() = runBlocking {
        val s = store()
        (1..3).forEach { s.enqueue(link(it)) }
        val report = s.flush { if (it == link(2)) refused else ok }!!
        assertEquals(PendingStore.FlushReport(sent = 2, rejected = 1), report)
        assertTrue(s.isEmpty())
    }

    @Test
    fun `un lien ajoute pendant l'envoi n'est pas perdu`() = runBlocking {
        val s = store()
        s.enqueue(link(1))
        s.flush { s.enqueue(link(9)); ok }
        assertEquals(listOf(link(9)), s.load())
    }

    @Test
    fun `deux envois simultanes ne partent pas deux fois`() = runBlocking {
        val s = store()
        s.enqueue(link(1))
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val first = async { s.flush { calls++; gate.await(); ok } }
        while (calls == 0) kotlinx.coroutines.yield()
        assertNull(s.flush { calls++; ok })
        gate.complete(Unit)
        first.await()
        assertEquals(1, calls)
    }

    @Test
    fun `un fichier corrompu est mis de cote, pas ecrase`() {
        val f = File(tmp.root, "q.json").apply { writeText("[{\"url\": tronqué") }
        val s = PendingStore(f)
        assertTrue(s.load().isEmpty())
        s.enqueue(link(1))
        val quarantined = tmp.root.listFiles()!!.filter { it.name.startsWith("q.json.corrupt-") }
        assertEquals(1, quarantined.size)
        assertEquals("[{\"url\": tronqué", quarantined[0].readText())
        assertEquals(listOf(link(1)), s.load())
    }
}
