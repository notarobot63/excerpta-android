package xyz.notarobot.excerpta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `accepte http et https, retire espaces et slash final`() {
        assertEquals("https://links.example.org", ServerUrl.normalize("  https://links.example.org/ "))
        assertEquals("http://excerpta.lan:8070", ServerUrl.normalize("http://excerpta.lan:8070"))
        assertEquals("https://example.org/excerpta", ServerUrl.normalize("https://example.org/excerpta//"))
    }

    @Test
    fun `refuse une adresse sans schema`() {
        assertNull(ServerUrl.normalize("links.example.org"))
    }

    @Test
    fun `refuse les schemas non HTTP, l'hote vide et le texte libre`() {
        assertNull(ServerUrl.normalize("ftp://example.org"))
        assertNull(ServerUrl.normalize("https://"))
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("pas une url"))
    }
}
