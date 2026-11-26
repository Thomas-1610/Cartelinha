package com.example.cartelinha.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.cartelinha.data.model.RecordStatus
import com.example.cartelinha.data.model.SchemeType

class Converters {
    @TypeConverter fun schemeToString(v: SchemeType) = v.name
    @TypeConverter fun stringToScheme(v: String) = SchemeType.valueOf(v)
    @TypeConverter fun statusToString(v: RecordStatus) = v.name
    @TypeConverter fun stringToStatus(v: String) = RecordStatus.valueOf(v)
}

@Database(
    entities = [ContraceptiveConfigEntity::class, DailyRecordEntity::class, RecordEditEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CartelinhaDatabase : RoomDatabase() {
    abstract fun configDao(): ContraceptiveConfigDao
    abstract fun recordDao(): DailyRecordDao
    abstract fun editDao(): RecordEditDao

    companion object {
        @Volatile private var instance: CartelinhaDatabase? = null

        fun get(context: Context): CartelinhaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CartelinhaDatabase::class.java,
                    "cartelinha.db"
                ).build().also { instance = it }
            }
    }
}
