package xyz.notarobot.excerpta

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket

class ApiClientTest {

    /** Un port local fermé : la connexion est refusée, comme sans réseau. */
    private fun closedPortUrl(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port"
    }

    @Test
    fun `addLink sans serveur joignable signale une erreur reseau`() = runBlocking {
        val result = ApiClient.addLink(closedPortUrl(), "key", "https://example.com", "", emptyList())
        assertFalse(result.success)
        assertTrue("un échec réseau doit pouvoir partir en file", result.isNetworkError)
    }

    @Test
    fun `une URL serveur sans schema ne fait pas planter l'appel`() = runBlocking {
        val result = ApiClient.addLink("example.com", "key", "https://example.com", "", emptyList())
        assertFalse(result.success)
        assertFalse("une URL invalide n'est pas un échec réseau", result.isNetworkError)
    }

    @Test
    fun `fetchLinks avec une URL serveur invalide renvoie null au lieu de planter`() = runBlocking {
        assertTrue(ApiClient.fetchLinks("ftp://example.com", "key") == null)
        assertTrue(ApiClient.fetchLinks("pas une url", "key") == null)
    }

    @Test
    fun `seuls les echecs de transport sont retentables`() {
        assertTrue(ApiClient.isNetworkFailure(java.net.ConnectException()))
        assertTrue(ApiClient.isNetworkFailure(java.net.SocketTimeoutException()))
        assertTrue(ApiClient.isNetworkFailure(java.net.UnknownHostException()))
        // HTTP en clair refusé par network_security_config : retenter n'y changera rien.
        assertFalse(ApiClient.isNetworkFailure(java.net.UnknownServiceException("CLEARTEXT not permitted")))
        assertFalse(ApiClient.isNetworkFailure(java.net.MalformedURLException()))
        assertFalse(ApiClient.isNetworkFailure(org.json.JSONException("x")))
    }

    @Test
    fun `ping avec une URL serveur invalide renvoie un echec`() = runBlocking {
        assertFalse(ApiClient.ping("example.com", "key").success)
    }
}
