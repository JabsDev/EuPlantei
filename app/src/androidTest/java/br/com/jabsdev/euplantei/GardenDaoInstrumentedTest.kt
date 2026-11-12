package br.com.jabsdev.euplantei

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.db.GardenPhotoEntity
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.DAY_MILLIS
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GardenDaoInstrumentedTest {
    private lateinit var database: AppDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun plantaRegaFotoEExclusaoEmCascata() = runBlocking {
        val dao = database.gardenDao()
        val now = System.currentTimeMillis()
        val id = dao.insertPlant(
            GardenPlantEntity(
                name = "Lírio da varanda",
                species = "Lírio",
                speciesId = "lirio",
                coverImagePath = "/tmp/capa.jpg",
                addedAt = now,
                wateringIntervalDays = 5,
                nextWateringAt = now + 5 * DAY_MILLIS,
            ),
        )

        assertEquals(1, dao.getAllPlants().size)
        assertEquals(id, dao.getPlant(id)?.id)
        assertEquals(1, dao.observePlants().first().size)

        val due = dao.getDuePlants(now + 5 * DAY_MILLIS)
        assertEquals(1, due.size)

        val plant = dao.getPlant(id)!!
        dao.updatePlant(
            plant.copy(
                lastWateredAt = now,
                nextWateringAt = now + 5 * DAY_MILLIS,
            ),
        )
        assertEquals(now, dao.getPlant(id)?.lastWateredAt)

        dao.insertPhoto(
            GardenPhotoEntity(
                plantId = id,
                imagePath = "/tmp/foto1.jpg",
                capturedAt = now,
            ),
        )
        assertEquals(1, dao.observePhotos(id).first().size)

        dao.deletePlant(id)
        assertNull(dao.getPlant(id))
        assertTrue(dao.getPhotos(id).isEmpty())
    }
}
