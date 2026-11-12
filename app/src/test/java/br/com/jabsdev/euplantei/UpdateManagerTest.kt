package br.com.jabsdev.euplantei

import br.com.jabsdev.euplantei.data.repo.UpdateInfo
import br.com.jabsdev.euplantei.data.repo.isNewerThan
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun `le o update json publicado na release`() {
        val json = """
            {
              "versionCode": 3,
              "versionName": "1.1.1",
              "apkUrl": "https://github.com/JabsDev/EuPlantei/releases/download/v1.1.1/EuPlantei-1.1.1.apk",
              "sha256": "abc123",
              "notes": "Correções no jardim."
            }
        """.trimIndent()

        val info = Json { ignoreUnknownKeys = true }.decodeFromString<UpdateInfo>(json)

        assertEquals(3, info.versionCode)
        assertEquals("1.1.1", info.versionName)
        assertTrue(info.apkUrl.startsWith("https://github.com/"))
        assertEquals("abc123", info.sha256)
        assertEquals("Correções no jardim.", info.notes)
    }

    @Test
    fun `so considera atualizacao quando version code e maior`() {
        val newer = UpdateInfo(versionCode = 3, versionName = "1.1.1")
        val same = UpdateInfo(versionCode = 2, versionName = "1.1.0")
        val older = UpdateInfo(versionCode = 1, versionName = "1.0.0")

        assertTrue(newer.isNewerThan(2))
        assertFalse(same.isNewerThan(2))
        assertFalse(older.isNewerThan(2))
    }
}
