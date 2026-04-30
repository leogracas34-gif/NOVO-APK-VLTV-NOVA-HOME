package com.vltv.play.data

import androidx.room.*
import android.content.Context

// ==========================================
// ENTITIES (inalteradas — sem quebrar nada)
// ==========================================

@Entity(tableName = "user_profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    var name: String,
    var imageUrl: String? = null,
    val isKids: Boolean = false
)

@Entity(
    tableName = "live_streams",
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["name"]),
        // ✅ NOVO índice composto: cobre a query mais comum (filtrar + ordenar por categoria)
        Index(value = ["category_id", "name"])
    ]
)
data class LiveStreamEntity(
    @PrimaryKey val stream_id: Int,
    val name: String,
    val stream_icon: String?,
    val epg_channel_id: String?,
    val category_id: String
)

@Entity(
    tableName = "vod_streams",
    indices = [
        Index(value = ["added"]),
        Index(value = ["category_id"]),
        Index(value = ["name"]),
        // ✅ NOVO índice composto: cobre filtro por categoria + ordenação por data
        Index(value = ["category_id", "added"]),
        // ✅ NOVO índice composto: cobre busca por nome dentro de categoria
        Index(value = ["category_id", "name"])
    ]
)
data class VodEntity(
    @PrimaryKey val stream_id: Int,
    val name: String,
    val title: String?,
    val stream_icon: String?,
    val container_extension: String?,
    val rating: String?,
    val category_id: String,
    val added: Long,
    val logo_url: String? = null
)

@Entity(
    tableName = "series_streams",
    indices = [
        Index(value = ["last_modified"]),
        Index(value = ["category_id"]),
        Index(value = ["name"]),
        // ✅ NOVO índice composto: cobre filtro por categoria + ordenação por data
        Index(value = ["category_id", "last_modified"]),
        // ✅ NOVO índice composto: cobre busca por nome dentro de categoria
        Index(value = ["category_id", "name"])
    ]
)
data class SeriesEntity(
    @PrimaryKey val series_id: Int,
    val name: String,
    val cover: String?,
    val rating: String?,
    val category_id: String,
    val last_modified: Long,
    val logo_url: String? = null
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["stream_id", "profile_name"],
    indices = [
        Index(value = ["timestamp"]),
        // ✅ NOVO: busca por perfil é a query mais comum no histórico
        Index(value = ["profile_name", "timestamp"])
    ]
)
data class WatchHistoryEntity(
    val stream_id: Int,
    val profile_name: String,
    val name: String,
    val icon: String?,
    val last_position: Long,
    val duration: Long,
    val is_series: Boolean,
    val timestamp: Long
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val category_id: String,
    val category_name: String,
    val type: String
)

@Entity(tableName = "epg_cache", indices = [Index(value = ["stream_id"])])
data class EpgEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val stream_id: String,
    val title: String?,
    val start: String?,
    val stop: String?,
    val description: String?
)

@Entity(tableName = "downloads", indices = [Index(value = ["status"])])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val android_download_id: Long,
    val stream_id: Int,
    val name: String,
    val episode_name: String?,
    val image_url: String?,
    val file_path: String,
    val type: String,
    val status: String,
    val progress: Int = 0,
    val total_size: String = "0MB"
)

// ==========================================
// DAO — OTIMIZADO
// ==========================================

@Dao
interface StreamDao {

    // --- PERFIS ---
    @Query("SELECT * FROM user_profiles")
    suspend fun getAllProfiles(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    // --- LIVE ---
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLiveStreams(streams: List<LiveStreamEntity>)

    @Query("SELECT * FROM live_streams WHERE name LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchLive(query: String): List<LiveStreamEntity>

    @Query("DELETE FROM live_streams")
    suspend fun clearLive()

    // ✅ NOVO: busca live por categoria — usa o índice composto category_id+name
    @Query("SELECT * FROM live_streams WHERE category_id = :categoryId ORDER BY name ASC")
    suspend fun getLiveByCategory(categoryId: String): List<LiveStreamEntity>

    // --- VOD ---
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVodStreams(streams: List<VodEntity>)

    @Query("SELECT COUNT(*) FROM vod_streams")
    suspend fun getVodCount(): Int

    @Query("SELECT * FROM vod_streams")
    suspend fun getAllVods(): List<VodEntity>

    // ✅ OTIMIZADO: usa índice composto category_id+added
    @Query("SELECT * FROM vod_streams WHERE category_id = :categoryId ORDER BY added DESC")
    suspend fun getVodsByCategory(categoryId: String): List<VodEntity>

    @Transaction
    @Query("SELECT * FROM vod_streams ORDER BY added DESC LIMIT :limit")
    suspend fun getRecentVods(limit: Int): List<VodEntity>

    @Query("SELECT * FROM vod_streams WHERE name LIKE '%' || :query || '%' LIMIT 100")
    suspend fun searchVod(query: String): List<VodEntity>

    // ✅ NOVO: busca por nome dentro de categoria — usa índice category_id+name
    @Query("SELECT * FROM vod_streams WHERE category_id = :categoryId AND name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchVodInCategory(categoryId: String, query: String): List<VodEntity>

    @Query("UPDATE vod_streams SET logo_url = :logoUrl WHERE stream_id = :id")
    suspend fun updateVodLogo(id: Int, logoUrl: String)

    @Query("SELECT * FROM vod_streams ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomVods(limit: Int): List<VodEntity>

    // ✅ NOVO: bulk update de logos em uma transação só (muito mais rápido que N updates)
    @Transaction
    suspend fun updateVodLogos(updates: Map<Int, String>) {
        updates.forEach { (id, url) -> updateVodLogo(id, url) }
    }

    // --- SÉRIES ---
    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesStreams(series: List<SeriesEntity>)

    @Query("SELECT * FROM series_streams")
    suspend fun getAllSeries(): List<SeriesEntity>

    // ✅ OTIMIZADO: usa índice composto category_id+last_modified
    @Query("SELECT * FROM series_streams WHERE category_id = :categoryId ORDER BY last_modified DESC")
    suspend fun getSeriesByCategory(categoryId: String): List<SeriesEntity>

    @Transaction
    @Query("SELECT * FROM series_streams ORDER BY last_modified DESC LIMIT :limit")
    suspend fun getRecentSeries(limit: Int): List<SeriesEntity>

    @Query("UPDATE series_streams SET logo_url = :logoUrl WHERE series_id = :id")
    suspend fun updateSeriesLogo(id: Int, logoUrl: String)

    @Query("SELECT * FROM series_streams ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSeries(limit: Int): List<SeriesEntity>

    // --- CATEGORIAS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    // ✅ NOVO: busca categorias por tipo (live / vod / series)
    @Query("SELECT * FROM categories WHERE type = :type ORDER BY category_name ASC")
    suspend fun getCategoriesByType(type: String): List<CategoryEntity>

    // --- HISTÓRICO ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveWatchHistory(history: WatchHistoryEntity)

    // ✅ OTIMIZADO: usa índice composto profile_name+timestamp
    @Query("SELECT * FROM watch_history WHERE profile_name = :profile ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getWatchHistory(profile: String, limit: Int = 20): List<WatchHistoryEntity>

    // --- DOWNLOADS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity)

    @Query("SELECT * FROM downloads ORDER BY id DESC")
    fun getAllDownloads(): androidx.lifecycle.LiveData<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE stream_id = :streamId AND type = :type LIMIT 1")
    suspend fun getDownloadByStreamId(streamId: Int, type: String): DownloadEntity?

    @Query("UPDATE downloads SET status = :status, progress = :progress WHERE android_download_id = :downloadId")
    suspend fun updateDownloadProgress(downloadId: Long, status: String, progress: Int)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownload(id: Int)

    @Query("DELETE FROM downloads WHERE android_download_id = :downloadId")
    suspend fun deleteDownloadByAndroidId(downloadId: Long)

    @Query("SELECT * FROM downloads WHERE status = :status")
    suspend fun getDownloadsByStatus(status: String): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'BAIXANDO'")
    fun getCountDownloadsAtivos(): androidx.lifecycle.LiveData<Int>
}

// ==========================================
// DATABASE — OTIMIZADO
// ==========================================

@Database(
    entities = [
        LiveStreamEntity::class,
        VodEntity::class,
        SeriesEntity::class,
        CategoryEntity::class,
        EpgEntity::class,
        WatchHistoryEntity::class,
        DownloadEntity::class,
        ProfileEntity::class
    ],
    version = 7, // ✅ Incrementado para forçar recriação dos índices compostos
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun streamDao(): StreamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // ✅ Double-checked locking — padrão correto para singleton thread-safe
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vltv_play_db"
            )
            .fallbackToDestructiveMigration()
            // ✅ WAL = Write-Ahead Logging: leituras e escritas simultâneas sem bloquear
            // Antes: modo journal padrão bloqueava leituras durante sincronização do servidor
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            // ✅ Query executor com 4 threads — permite 4 queries paralelas simultâneas
            // Útil quando Home, Canais e Filmes carregam ao mesmo tempo
            .setQueryExecutor(
                java.util.concurrent.Executors.newFixedThreadPool(4)
            )
            .build()
        }
    }
}
