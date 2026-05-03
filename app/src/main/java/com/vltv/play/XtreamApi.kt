package com.vltv.play

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.dnsoverhttps.DnsOverHttps
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.InetAddress
import java.util.concurrent.TimeUnit

// ---------------------
// Modelos de Dados
// ---------------------
data class XtreamLoginResponse(val user_info: UserInfo?, val server_info: ServerInfo?)
data class UserInfo(val username: String?, val status: String?, val exp_date: String?)
data class ServerInfo(val url: String?, val port: String?, val server_protocol: String?)

data class LiveCategory(val category_id: String, val category_name: String) {
    val id: String get() = category_id
    val name: String get() = category_name
}

data class LiveStream(val stream_id: Int, val name: String, val stream_icon: String?, val epg_channel_id: String?) {
    val id: Int get() = stream_id
    val icon: String? get() = stream_icon
}

data class VodStream(val stream_id: Int, val name: String, val title: String?, val stream_icon: String?, val container_extension: String?, val rating: String?) {
    val id: Int get() = stream_id
    val icon: String? get() = stream_icon
    val extension: String? get() = container_extension
}

data class SeriesStream(val series_id: Int, val name: String, val cover: String?, val rating: String?) {
    val id: Int get() = series_id
    val icon: String? get() = cover
}

data class EpgWrapper(val epg_listings: List<EpgResponseItem>?)
data class EpgResponseItem(val id: String?, val epg_id: String?, val title: String?, val lang: String?, val start: String?, val end: String?, val stop: String?, val description: String?, val channel_id: String?, val start_timestamp: String?, val stop_timestamp: String?)
data class SeriesInfoResponse(val episodes: Map<String, List<EpisodeStream>>?)
data class EpisodeStream(val id: String, val title: String, val container_extension: String?, val season: Int, val episode_num: Int, val info: EpisodeInfo?)
data class EpisodeInfo(val plot: String?, val duration: String?, val movie_image: String?)
data class VodInfoResponse(val info: VodInfoData?)
data class VodInfoData(val plot: String?, val genre: String?, val director: String?, val cast: String?, val releasedate: String?, val rating: String?, val movie_image: String?)

// ---------------------
// Interface Retrofit
// ---------------------
interface XtreamService {

    @GET("player_api.php")
    fun login(@Query("username") user: String, @Query("password") pass: String): Call<XtreamLoginResponse>

    @GET("player_api.php")
    fun getLiveCategories(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_live_categories"): Call<ResponseBody>

    @GET("player_api.php")
    fun getLiveStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_live_streams", @Query("category_id") categoryId: String): Call<List<LiveStream>>

    @GET("player_api.php")
    fun getVodCategories(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_categories"): Call<ResponseBody>

    @GET("player_api.php")
    fun getVodStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_streams", @Query("category_id") categoryId: String): Call<List<VodStream>>

    @GET("player_api.php")
    fun getAllVodStreams(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_streams"): Call<List<VodStream>>

    @GET("player_api.php")
    fun getVodInfo(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_vod_info", @Query("vod_id") vodId: Int): Call<VodInfoResponse>

    @GET("player_api.php")
    fun getSeriesCategories(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series_categories"): Call<ResponseBody>

    @GET("player_api.php")
    fun getSeries(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series", @Query("category_id") categoryId: String): Call<List<SeriesStream>>

    @GET("player_api.php")
    fun getAllSeries(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series"): Call<List<SeriesStream>>

    @GET("player_api.php")
    fun getSeriesInfoV2(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_series_info", @Query("series_id") seriesId: Int): Call<SeriesInfoResponse>

    @GET("player_api.php")
    fun getShortEpg(@Query("username") user: String, @Query("password") pass: String, @Query("action") action: String = "get_short_epg", @Query("stream_id") streamId: String, @Query("limit") limit: Int = 2): Call<EpgWrapper>
}

// ---------------------
// Interceptor
// ---------------------
class VpnInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "*/*")
            .header("Cache-Control", "no-cache")
            .build()
        return chain.proceed(request)
    }
}

// ---------------------
// XtreamApi — corrigido
// ---------------------
object XtreamApi {

    private const val PREFS_NAME = "vltv_prefs"
    private const val PREF_DNS_KEY = "dns"

    // ✅ FIX: lock explícito para proteger baseUrl, retrofit e service
    private val lock = Any()
    private var baseUrl: String = ""

    // ✅ FIX: _service cacheado — não recria Retrofit a cada chamada
    // Antes: retrofit = null + service recriado causava race condition em troca rápida de categoria
    // Agora: _service só é recriado quando baseUrl muda de fato
    private var _service: XtreamService? = null

    // ✅ FIX: OkHttpClient singleton reutilizável com ConnectionPool generoso
    // Antes: client era recriado junto com retrofit — fechava conexões ativas
    // Agora: mesmo client para todas as requisições, conexões são reutilizadas
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(buildSafeDns())
            .addInterceptor(VpnInterceptor())
            // ✅ Pool maior: suporta troca rápida de categorias sem fechar conexões
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .build()
    }

    private fun buildSafeDns(): Dns {
        return try {
            val bootstrapClient = OkHttpClient.Builder().build()
            DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    listOf(
                        InetAddress.getByName("8.8.8.8"),
                        InetAddress.getByName("1.1.1.1")
                    )
                )
                .build()
        } catch (e: Exception) {
            Dns.SYSTEM
        }
    }

    init {
        carregarDnsSalvo()
    }

    private fun getAppContext(): Context? {
        return try {
            Class.forName("android.app.ActivityThread")
                .getMethod("currentApplication")
                .invoke(null) as? Context
        } catch (e: Exception) { null }
    }

    private fun carregarDnsSalvo() {
        val context = getAppContext() ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDns = prefs.getString(PREF_DNS_KEY, null)
        if (!savedDns.isNullOrBlank()) setBaseUrl(savedDns)
    }

    fun salvarDns(context: Context, dns: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_DNS_KEY, dns).apply()
        setBaseUrl(dns)
    }

    fun setBaseUrl(newUrl: String) {
        if (newUrl.isBlank()) return

        var urlClean = newUrl.trim()
        if (urlClean.contains("player_api.php")) urlClean = urlClean.substringBefore("player_api.php")
        if (!urlClean.startsWith("http://") && !urlClean.startsWith("https://")) urlClean = "http://$urlClean"
        if (!urlClean.endsWith("/")) urlClean += "/"

        synchronized(lock) {
            if (baseUrl != urlClean) {
                baseUrl = urlClean
                // ✅ FIX: invalida APENAS o service, não o okHttpClient
                // O client mantém o ConnectionPool ativo — conexões abertas são reaproveitadas
                _service = null
            }
        }
    }

    // ✅ FIX: service é criado uma única vez por URL e reutilizado
    // synchronized garante que apenas 1 thread cria o Retrofit, as demais aguardam
    // e pegam o mesmo objeto já criado — sem race condition
    val service: XtreamService
        get() = synchronized(lock) {
            _service ?: run {
                val url = baseUrl.ifBlank { "http://localhost/" }
                val newService = Retrofit.Builder()
                    .baseUrl(url)
                    .client(okHttpClient) // ✅ mesmo client reutilizado
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(XtreamService::class.java)
                _service = newService
                newService
            }
        }

    fun <T> parseCategoryList(responseBody: ResponseBody?, clazz: Class<T>): List<T>? {
        return try {
            val json = responseBody?.string() ?: return null
            Gson().fromJson<List<T>>(json, object : TypeToken<List<T>>() {}.type)
        } catch (e: Exception) { null }
    }
}
