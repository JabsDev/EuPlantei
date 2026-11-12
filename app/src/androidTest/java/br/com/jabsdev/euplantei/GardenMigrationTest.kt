package br.com.jabsdev.euplantei

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Abre um banco v1 "legado" e deixa o Room real aplicar a migração, sem o
 * MigrationTestHelper. Assim o teste exercita o mesmo caminho de quem instala
 * a versão nova por cima de um histórico já existente.
 */
@RunWith(AndroidJUnit4::class)
class GardenMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun cleanDatabase() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migracao1Para2PreservaConsultaECriaTabelasDoJardim() = runBlocking {
        createLegacyDatabase()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val consultations = database.consultationDao().getAllOnce()
            assertEquals(1, consultations.size)
            assertEquals("Rosa", consultations.first().species)
            assertEquals("Conselho antigo", consultations.first().advice)

            val gardenDao = database.gardenDao()
            val plantId = gardenDao.insertPlant(
                GardenPlantEntity(
                    name = "Lírio",
                    species = "Lírio",
                    speciesId = "lirio",
                    coverImagePath = "/tmp/capa.jpg",
                    addedAt = 1_700_000_000_000,
                    wateringIntervalDays = 7,
                    nextWateringAt = 1_700_604_800_000,
                ),
            )
            assertNotNull(gardenDao.getPlant(plantId))
            assertEquals(1, gardenDao.observePlants().first().size)
        } finally {
            database.close()
        }
    }

    private fun createLegacyDatabase() {
        val file = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `consultations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, " +
                    "`imagePath` TEXT NOT NULL, " +
                    "`species` TEXT NOT NULL, " +
                    "`condition` TEXT NOT NULL, " +
                    "`confidencePercent` INTEGER NOT NULL, " +
                    "`manual` INTEGER NOT NULL, " +
                    "`symptom` TEXT NOT NULL, " +
                    "`question` TEXT NOT NULL, " +
                    "`city` TEXT, " +
                    "`weatherJson` TEXT, " +
                    "`advice` TEXT NOT NULL)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_consultations_createdAt` " +
                    "ON `consultations` (`createdAt`)",
            )
            legacy.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_consultations_species` " +
                    "ON `consultations` (`species`)",
            )
            legacy.execSQL(
                "INSERT INTO consultations " +
                    "(createdAt, imagePath, species, condition, confidencePercent, manual, " +
                    "symptom, question, city, weatherJson, advice) " +
                    "VALUES (1700000000000, '/tmp/foto.jpg', 'Rosa', 'Mancha foliar', 91, 0, " +
                    "'Manchas', '', 'Campinas', NULL, 'Conselho antigo')",
            )
            legacy.version = 1
        } finally {
            legacy.close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-garden-test.db"
    }
}
