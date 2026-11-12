package br.com.jabsdev.euplantei.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConsultationEntity::class,
        GardenPlantEntity::class,
        GardenPhotoEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun consultationDao(): ConsultationDao
    abstract fun gardenDao(): GardenDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * Preserva o histórico de consultas já existente no aparelho. As tabelas
         * do jardim são apenas criadas; nada do usuário é apagado.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `garden_plants` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`species` TEXT NOT NULL, " +
                        "`speciesId` TEXT NOT NULL, " +
                        "`coverImagePath` TEXT NOT NULL, " +
                        "`addedAt` INTEGER NOT NULL, " +
                        "`wateringIntervalDays` INTEGER NOT NULL, " +
                        "`lastWateredAt` INTEGER, " +
                        "`nextWateringAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_garden_plants_nextWateringAt` " +
                        "ON `garden_plants` (`nextWateringAt`)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `garden_photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`plantId` INTEGER NOT NULL, " +
                        "`imagePath` TEXT NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "FOREIGN KEY(`plantId`) REFERENCES `garden_plants`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_garden_photos_plantId` " +
                        "ON `garden_photos` (`plantId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_garden_photos_capturedAt` " +
                        "ON `garden_photos` (`capturedAt`)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "euplantei.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
