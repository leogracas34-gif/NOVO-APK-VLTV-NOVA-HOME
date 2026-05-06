package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.vltv.play.databinding.ActivityHomeBinding
import com.vltv.play.DownloadHelper
import com.vltv.play.data.AppDatabase
import com.vltv.play.data.LiveStreamEntity
import com.vltv.play.data.VodEntity
import com.vltv.play.data.SeriesEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val TMDB_API_KEY = "9b73f5dd15b8165b1b57419be2f29128"

    private var currentProfile: String = "Padrao"
    private var currentProfileIcon: String? = null

    private val database by lazy { AppDatabase.getDatabase(this) }

    private var listaCompletaParaSorteio: List<Any> = emptyList()
    private lateinit var bannerAdapter: BannerAdapter
    private val REQUEST_CODE_CAST_PERMISSIONS = 1001

    private var bannerItemAtual: Any? = null

    // ✅ Controla se já está sincronizando para não disparar duas vezes
    private var isSyncing = false

    // ✅ Timestamp da última sincronização completa
    private var ultimaSincronizacao: Long = 0L

    // Intervalo mínimo entre sincronizações ao voltar pro app: 5 minutos
    private val SYNC_INTERVAL_MS = 5 * 60 * 1000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            configurarOrientacaoAutomatica()

            binding = ActivityHomeBinding.inflate(layoutInflater)
            setContentView(binding.root)

            try {
                CastContext.getSharedInstance(this)
                binding.mediaRouteButton?.let { btn ->
                    CastButtonFactory.setUpMediaRouteButton(applicationContext, btn)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            verificarPermissoesCast()

            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            val savedName = prefs.getString("last_profile_name", null)
            val savedIcon = prefs.getString("last_profile_icon", null)

            currentProfile = intent.getStringExtra("PROFILE_NAME") ?: savedName ?: "Padrao"
            currentProfileIcon = intent.getStringExtra("PROFILE_ICON") ?: savedIcon

            // ✅ CIRURGIA 1: Sincroniza as prefs com o perfil recebido pelo Intent.
            // Isso garante que onResume e setupBottomNavigation sempre leiam o perfil correto,
            // inclusive quando a Home é aberta por SettingsActivity após troca de perfil.
            prefs.edit().apply {
                putString("last_profile_name", currentProfile)
                if (currentProfileIcon != null) putString("last_profile_icon", currentProfileIcon)
                apply()
            }

            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = false

            DownloadHelper.registerReceiver(this)

            try {
                CastContext.getSharedInstance(this)
                binding.mediaRouteButton?.let { btn ->
                    CastButtonFactory.setUpMediaRouteButton(applicationContext, btn)
                    btn.visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            setupSingleBanner()
            setupBottomNavigation()
            setupClicks()
            setupFirebaseRemoteConfig()

            carregarDadosLocaisImediato()
            sincronizarConteudoSilenciosamente()

            val isKidsMode = intent.getBooleanExtra("IS_KIDS_MODE", false)
            if (isKidsMode) {
                currentProfile = "Kids"
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        binding.cardKids.performClick()
                        Toast.makeText(this, "Modo Kids Ativado", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {}
                }, 500)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun configurarOrientacaoAutomatica() {
        if (isTVDevice()) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun isTVDevice(): Boolean {
        return try {
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            packageManager.hasSystemFeature("android.hardware.type.television") ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION ||
            isRealTVSize()
        } catch (e: Exception) {
            false
        }
    }

    private fun isRealTVSize(): Boolean {
        return try {
            val displayMetrics = resources.displayMetrics
            val widthDp = displayMetrics.widthPixels / displayMetrics.density
            val isLargeWidth = widthDp > 600
            val isLowDensity = displayMetrics.densityDpi < DisplayMetrics.DENSITY_XHIGH
            isLargeWidth && isLowDensity
        } catch (e: Exception) {
            false
        }
    }

    private fun setupSingleBanner() {
        bannerAdapter = BannerAdapter(emptyList())
        binding.bannerViewPager?.adapter = bannerAdapter
        binding.bannerViewPager?.isUserInputEnabled = false
    }

    // ✅ CIRURGIA 2: Substituídas as variáveis locais finalName/finalIcon
    // (que reliam as prefs e podiam estar desatualizadas) pelas variáveis de
    // instância currentProfile e currentProfileIcon, que já foram atualizadas
    // antes desta chamada — tanto no onCreate quanto no onResume.
    private fun setupBottomNavigation() {
        binding.bottomNavigation?.let { nav ->
            val profileItem = nav.menu.findItem(R.id.nav_profile)
            profileItem?.title = currentProfile

            if (!currentProfileIcon.isNullOrEmpty()) {
                Glide.with(this)
                    .asBitmap()
                    .load(currentProfileIcon)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(object : CustomTarget<Bitmap>() {
                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                            if (!isFinishing && !isDestroyed) {
                                profileItem?.icon = BitmapDrawable(resources, resource)
                            }
                        }
                        override fun onLoadCleared(placeholder: Drawable?) {}
                    })
            }
        }

        binding.bottomNavigation?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_search -> {
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra("PROFILE_NAME", currentProfile)
                    intent.putExtra("PROFILE_ICON", currentProfileIcon)
                    startActivity(intent)
                    false
                }
                R.id.nav_novidades -> {
                    val intent = Intent(this, NovidadesActivity::class.java)
                    intent.putExtra("PROFILE_NAME", currentProfile)
                    intent.putExtra("PROFILE_ICON", currentProfileIcon)
                    startActivity(intent)
                    false
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.putExtra("PROFILE_NAME", currentProfile)
                    intent.putExtra("PROFILE_ICON", currentProfileIcon)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
    }

    private fun carregarDadosLocaisImediato() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ✅ Busca mais itens para alimentar todas as seções
                val localMovies = database.streamDao().getRecentVods(60)
                val movieItems = localMovies.map { VodItem(it.stream_id.toString(), limparNomeExibicao(it.name), it.stream_icon ?: "") }

                val localSeries = database.streamDao().getRecentSeries(60)
                val seriesItems = localSeries.map { VodItem(it.series_id.toString(), limparNomeExibicao(it.name), it.cover ?: "") }

                withContext(Dispatchers.Main) {

                    // ── FILMES PARA VOCÊ (primeiros 20) ──────────────────
                    if (movieItems.isNotEmpty()) {
                        binding.rvRecentlyAdded.setHasFixedSize(true)
                        binding.rvRecentlyAdded.setItemViewCacheSize(20)
                        binding.rvRecentlyAdded.adapter = HomeRowAdapter(movieItems.take(20)) { selectedItem ->
                            val intent = Intent(this@HomeActivity, DetailsActivity::class.java)
                            intent.putExtra("stream_id", selectedItem.id.toIntOrNull() ?: 0)
                            intent.putExtra("name", selectedItem.name)
                            intent.putExtra("icon", selectedItem.streamIcon)
                            intent.putExtra("PROFILE_NAME", currentProfile)
                            intent.putExtra("is_series", false)
                            startActivity(intent)
                        }
                    }

                    // ── SÉRIES PARA VOCÊ (primeiros 20) ──────────────────
                    if (seriesItems.isNotEmpty()) {
                        binding.rvRecentSeries.setHasFixedSize(true)
                        binding.rvRecentSeries.setItemViewCacheSize(20)
                        binding.rvRecentSeries.adapter = HomeRowAdapter(seriesItems.take(20)) { selectedItem ->
                            val intent = Intent(this@HomeActivity, SeriesDetailsActivity::class.java)
                            intent.putExtra("series_id", selectedItem.id.toIntOrNull() ?: 0)
                            intent.putExtra("name", selectedItem.name)
                            intent.putExtra("icon", selectedItem.streamIcon)
                            intent.putExtra("PROFILE_NAME", currentProfile)
                            intent.putExtra("is_series", true)
                            startActivity(intent)
                        }
                    }

                    // ── TOP 10 FILMES (itens 20-30 — diversifica o conteúdo) ──
                    try {
                        val top10Movies = if (movieItems.size > 20) movieItems.subList(20, minOf(30, movieItems.size))
                                          else movieItems.take(10)
                        if (top10Movies.isNotEmpty()) {
                            binding.rvTop10Movies?.setHasFixedSize(true)
                            binding.rvTop10Movies?.adapter = Top10Adapter(top10Movies) { selectedItem ->
                                val intent = Intent(this@HomeActivity, DetailsActivity::class.java)
                                intent.putExtra("stream_id", selectedItem.id.toIntOrNull() ?: 0)
                                intent.putExtra("name", selectedItem.name)
                                intent.putExtra("icon", selectedItem.streamIcon)
                                intent.putExtra("PROFILE_NAME", currentProfile)
                                intent.putExtra("is_series", false)
                                startActivity(intent)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    // ── TOP 10 SÉRIES (itens 20-30) ──────────────────────
                    try {
                        val top10Series = if (seriesItems.size > 20) seriesItems.subList(20, minOf(30, seriesItems.size))
                                          else seriesItems.take(10)
                        if (top10Series.isNotEmpty()) {
                            binding.rvTop10Series?.setHasFixedSize(true)
                            binding.rvTop10Series?.adapter = Top10Adapter(top10Series) { selectedItem ->
                                val intent = Intent(this@HomeActivity, SeriesDetailsActivity::class.java)
                                intent.putExtra("series_id", selectedItem.id.toIntOrNull() ?: 0)
                                intent.putExtra("name", selectedItem.name)
                                intent.putExtra("icon", selectedItem.streamIcon)
                                intent.putExtra("PROFILE_NAME", currentProfile)
                                intent.putExtra("is_series", true)
                                startActivity(intent)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    // ── NOVIDADES (mistura filmes + séries recentes 30-50) ──
                    try {
                        val novidadesFilmes = if (movieItems.size > 30) movieItems.subList(30, minOf(40, movieItems.size)) else emptyList()
                        val novidadesSeries = if (seriesItems.size > 30) seriesItems.subList(30, minOf(40, seriesItems.size)) else emptyList()
                        val novidades = (novidadesFilmes + novidadesSeries).shuffled().take(20)

                        if (novidades.isNotEmpty()) {
                            binding.rvNovidades?.setHasFixedSize(true)
                            binding.rvNovidades?.adapter = HomeRowAdapter(novidades) { selectedItem ->
                                val isSeries = novidadesSeries.any { it.id == selectedItem.id }
                                val intent = if (isSeries)
                                    Intent(this@HomeActivity, SeriesDetailsActivity::class.java).apply { putExtra("series_id", selectedItem.id.toIntOrNull() ?: 0) }
                                else
                                    Intent(this@HomeActivity, DetailsActivity::class.java).apply { putExtra("stream_id", selectedItem.id.toIntOrNull() ?: 0) }
                                intent.putExtra("name", selectedItem.name)
                                intent.putExtra("icon", selectedItem.streamIcon)
                                intent.putExtra("PROFILE_NAME", currentProfile)
                                startActivity(intent)
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    listaCompletaParaSorteio = (localMovies + localSeries)
                    sortearBannerUnico()
                    ativarModoSupersonico(movieItems, seriesItems)
                    carregarContinuarAssistindoLocal()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun ativarModoSupersonico(filmes: List<VodItem>, series: List<VodItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            val preloadList = filmes.take(20) + series.take(20)
            for (item in preloadList) {
                try {
                    if (!item.streamIcon.isNullOrEmpty()) {
                        Glide.with(applicationContext)
                            .load(item.streamIcon)
                            .format(DecodeFormat.PREFER_RGB_565)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .preload(180, 270)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    private fun sortearBannerUnico() {
        if (listaCompletaParaSorteio.isNotEmpty()) {
            val lista = listaCompletaParaSorteio
            var itemSorteado = lista.random()
            if (lista.size > 1) {
                var tentativas = 0
                while (itemSorteado === bannerItemAtual && tentativas < 5) {
                    itemSorteado = lista.random()
                    tentativas++
                }
            }
            bannerItemAtual = itemSorteado
            bannerAdapter.updateList(listOf(itemSorteado))
        } else {
            carregarBannerAlternado()
        }
    }

    private fun limparNomeExibicao(nome: String): String {
        return nome
            .replace(Regex("(?i)\\b(4K|FULL\\.?HD|HD|SD|720P|1080P|2160P|DUBLADO|LEGENDADO|DUAL|AUDIO|LATINO|PT[-.]?BR|PTBR|WEB[-.]?DL|BLURAY|MKV|MP4|AVI|REPACK|H\\.?264|H\\.?265|HEVC|WEB|HDR|REMUX|UHD|FHD)\\b"), "")
            .replace(Regex("\\(\\d{4}\\)|\\[.*?\\]|\\{.*?\\}"), "")
            .replace(Regex("\\d{4}"), "")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("[-|•·]+\\s*$"), "")
            .trim()
    }

    private fun limparNomeParaTMDB(nome: String): String {
        return nome
            .replace(Regex("(?i)\\b(4K|FULL HD|HD|SD|720P|1080P|2160P|DUBLADO|LEGENDADO|DUAL|AUDIO|LATINO|PT-BR|PTBR|WEB-DL|BLURAY|MKV|MP4|AVI|REPACK|H264|H265|HEVC|WEB|S\\d+E\\d+|SEASON|TEMPORADA)\\b"), "")
            .replace(Regex("\\(\\d{4}\\)|\\[.*?\\]|\\{.*?\\}|\\(.*\\d{4}.*\\)"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(50)
    }

    private fun buscarImagemBackgroundTMDB(nome: String, isSeries: Boolean, fallback: String, internalId: Int, targetImg: ImageView, targetLogo: ImageView, targetTitle: TextView, suave: Boolean = false) {
        try {
            targetImg.scaleType = ImageView.ScaleType.CENTER_CROP
            val glideRequest = Glide.with(this@HomeActivity)
                .load(fallback)
                .centerCrop()
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.ALL)

            if (suave) {
                glideRequest.transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade(400))
                    .into(targetImg)
            } else {
                glideRequest.dontAnimate().into(targetImg)
            }
        } catch (e: Exception) {}

        val tipo = if (isSeries) "tv" else "movie"
        val nomeLimpo = limparNomeParaTMDB(nome)
        val query = URLEncoder.encode(nomeLimpo, "UTF-8")
        val url = "https://api.themoviedb.org/3/search/$tipo?api_key=$TMDB_API_KEY&query=$query&language=pt-BR&region=BR"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = URL(url).readText()
                val results = JSONObject(response).getJSONArray("results")
                if (results.length() > 0) {
                    val backdropPath = results.getJSONObject(0).optString("backdrop_path")
                    val tmdbId = results.getJSONObject(0).optString("id")

                    withContext(Dispatchers.Main) {
                        try {
                            if (backdropPath != "null" && backdropPath.isNotEmpty()) {
                                Glide.with(this@HomeActivity)
                                    .load("https://image.tmdb.org/t/p/original$backdropPath")
                                    .centerCrop()
                                    .dontAnimate()
                                    .format(DecodeFormat.PREFER_RGB_565)
                                    .placeholder(targetImg.drawable)
                                    .into(targetImg)
                            }
                        } catch (e: Exception) {}
                    }

                    buscarLogoOverlayHome(tmdbId, tipo, internalId, isSeries, targetLogo, targetTitle)
                }
            } catch (e: Exception) {}
        }
    }

    private fun buscarLogoOverlayHome(tmdbId: String, tipo: String, internalId: Int, isSeries: Boolean, targetLogo: ImageView, targetTitle: TextView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val imagesUrl = "https://api.themoviedb.org/3/$tipo/$tmdbId/images?api_key=$TMDB_API_KEY&include_image_language=pt,null"
                val imagesJson = URL(imagesUrl).readText()
                val imagesObj = JSONObject(imagesJson)

                if (imagesObj.has("logos") && imagesObj.getJSONArray("logos").length() > 0) {
                    val logos = imagesObj.getJSONArray("logos")
                    var bestPath: String? = null

                    for (i in 0 until logos.length()) {
                        val logo = logos.getJSONObject(i)
                        if (logo.optString("iso_639_1") == "pt") {
                            bestPath = logo.getString("file_path")
                            break
                        }
                    }

                    if (bestPath == null) {
                        for (i in 0 until logos.length()) {
                            val logo = logos.getJSONObject(i)
                            val lang = logo.optString("iso_639_1")
                            if (lang == "null" || lang == "xx" || lang.isEmpty()) {
                                bestPath = logo.getString("file_path")
                                break
                            }
                        }
                    }

                    if (bestPath == null && logos.length() > 0) {
                        bestPath = logos.getJSONObject(0).getString("file_path")
                    }

                    if (bestPath != null) {
                        val fullLogoUrl = "https://image.tmdb.org/t/p/w500$bestPath"

                        try {
                            if (isSeries) {
                                database.streamDao().updateSeriesLogo(internalId, fullLogoUrl)
                            } else {
                                database.streamDao().updateVodLogo(internalId, fullLogoUrl)
                            }
                        } catch (e: Exception) {}

                        withContext(Dispatchers.Main) {
                            targetTitle.visibility = View.GONE
                            targetLogo.visibility = View.VISIBLE
                            try {
                                Glide.with(this@HomeActivity)
                                    .load(fullLogoUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(targetLogo)
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ SINCRONIZAÇÃO SILENCIOSA — chamada no onCreate E no onResume com controle de intervalo
    private fun sincronizarConteudoSilenciosamente() {
        val agora = System.currentTimeMillis()

        // Evita sincronizar se já está rodando ou se sincronizou há menos de 5 minutos
        if (isSyncing) return
        if (agora - ultimaSincronizacao < SYNC_INTERVAL_MS && ultimaSincronizacao > 0) return

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val dns = prefs.getString("dns", "") ?: ""
        val user = prefs.getString("username", "") ?: ""
        val pass = prefs.getString("password", "") ?: ""

        if (dns.isEmpty() || user.isEmpty()) return

        isSyncing = true

        lifecycleScope.launch(Dispatchers.IO) {
            // ✅ Sem delay no onResume: começa imediatamente
            // (delay só no primeiro carregamento do onCreate já foi removido)
            delay(1500) // Pequeno delay para não competir com o carregamento local inicial

            try {
                val vodUrl = "$dns/player_api.php?username=$user&password=$pass&action=get_vod_streams"
                val vodResponse = URL(vodUrl).readText()
                val vodArray = org.json.JSONArray(vodResponse)
                val vodBatch = mutableListOf<VodEntity>()
                val palavrasProibidas = listOf("XXX", "PORN", "ADULTO", "SEXO", "EROTICO", "🔞", "PORNÔ")
                var firstVodBatchLoaded = false

                for (i in 0 until vodArray.length()) {
                    val obj = vodArray.getJSONObject(i)
                    val nome = obj.optString("name")
                    if (!palavrasProibidas.any { nome.uppercase().contains(it) }) {
                        vodBatch.add(VodEntity(
                            stream_id = obj.optInt("stream_id"),
                            name = nome,
                            title = obj.optString("name"),
                            stream_icon = obj.optString("stream_icon"),
                            container_extension = obj.optString("container_extension"),
                            rating = obj.optString("rating"),
                            category_id = obj.optString("category_id"),
                            added = obj.optLong("added")
                        ))
                    }

                    if (vodBatch.size >= 50) {
                        database.streamDao().insertVodStreams(vodBatch)
                        vodBatch.clear()

                        if (!firstVodBatchLoaded) {
                            withContext(Dispatchers.Main) { carregarDadosLocaisImediato() }
                            firstVodBatchLoaded = true
                        }
                    }
                }
                if (vodBatch.isNotEmpty()) {
                    database.streamDao().insertVodStreams(vodBatch)
                }
                withContext(Dispatchers.Main) { carregarDadosLocaisImediato() }

                val seriesUrl = "$dns/player_api.php?username=$user&password=$pass&action=get_series"
                val seriesResponse = URL(seriesUrl).readText()
                val seriesArray = org.json.JSONArray(seriesResponse)
                val seriesBatch = mutableListOf<SeriesEntity>()
                var firstSeriesBatchLoaded = false

                for (i in 0 until seriesArray.length()) {
                    val obj = seriesArray.getJSONObject(i)
                    val nome = obj.optString("name")
                    if (!palavrasProibidas.any { nome.uppercase().contains(it) }) {
                        seriesBatch.add(SeriesEntity(
                            series_id = obj.optInt("series_id"),
                            name = nome,
                            cover = obj.optString("cover"),
                            rating = obj.optString("rating"),
                            category_id = obj.optString("category_id"),
                            last_modified = obj.optLong("last_modified")
                        ))
                    }

                    if (seriesBatch.size >= 50) {
                        database.streamDao().insertSeriesStreams(seriesBatch)
                        seriesBatch.clear()

                        if (!firstSeriesBatchLoaded) {
                            withContext(Dispatchers.Main) { carregarDadosLocaisImediato() }
                            firstSeriesBatchLoaded = true
                        }
                    }
                }
                if (seriesBatch.isNotEmpty()) {
                    database.streamDao().insertSeriesStreams(seriesBatch)
                }
                withContext(Dispatchers.Main) { carregarDadosLocaisImediato() }

                val liveUrl = "$dns/player_api.php?username=$user&password=$pass&action=get_live_streams"
                val liveResponse = URL(liveUrl).readText()
                val liveArray = org.json.JSONArray(liveResponse)
                val liveBatch = mutableListOf<LiveStreamEntity>()

                for (i in 0 until liveArray.length()) {
                    val obj = liveArray.getJSONObject(i)
                    liveBatch.add(LiveStreamEntity(
                        stream_id = obj.optInt("stream_id"),
                        name = obj.optString("name"),
                        stream_icon = obj.optString("stream_icon"),
                        epg_channel_id = obj.optString("epg_channel_id"),
                        category_id = obj.optString("category_id")
                    ))

                    if (liveBatch.size >= 100) {
                        database.streamDao().insertLiveStreams(liveBatch)
                        liveBatch.clear()
                    }
                }
                if (liveBatch.isNotEmpty()) {
                    database.streamDao().insertLiveStreams(liveBatch)
                }

                withContext(Dispatchers.Main) {
                    carregarDadosLocaisImediato()
                    // ✅ Registra o timestamp da última sincronização bem-sucedida
                    ultimaSincronizacao = System.currentTimeMillis()
                    isSyncing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSyncing = false
                }
            }
        }
    }

    private fun setupFirebaseRemoteConfig() {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 60
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // Configuração remota carregada
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            currentProfile = prefs.getString("last_profile_name", currentProfile) ?: "Padrao"
            currentProfileIcon = prefs.getString("last_profile_icon", currentProfileIcon)

            // ✅ setupBottomNavigation() chamado APÓS atualizar currentProfile e
            // currentProfileIcon acima. No original estava na mesma ordem mas
            // setupBottomNavigation relía as prefs internamente (finalName/finalIcon)
            // em vez de usar as variáveis já atualizadas — isso foi corrigido na cirurgia 2.
            sortearBannerUnico()
            carregarContinuarAssistindoLocal()
            atualizarNotificacaoDownload()
            setupBottomNavigation()

            // ✅ SINCRONIZAÇÃO AUTOMÁTICA AO VOLTAR PRO APP
            // Só sincroniza se passou o intervalo mínimo (5 minutos)
            sincronizarConteudoSilenciosamente()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun atualizarNotificacaoDownload() {
        // Função desativada pois o botão de Downloads foi removido do rodapé
    }

    private fun setupClicks() {
        fun isTelevisionDevice(): Boolean {
            return packageManager.hasSystemFeature("android.hardware.type.television") ||
                   packageManager.hasSystemFeature("android.software.leanback") ||
                   (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        }

        val cards = listOf(binding.cardLiveTv, binding.cardMovies, binding.cardSeries, binding.cardKids)

        cards.forEach { card ->
            card.isFocusable = true
            card.isClickable = true

            card.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    card.animate().scaleX(1.08f).scaleY(1.08f).translationZ(10f).setDuration(200).start()
                } else {
                    card.animate().scaleX(1f).scaleY(1f).translationZ(0f).setDuration(200).start()
                }
            }

            card.setOnClickListener {
                when (card.id) {
                    R.id.cardLiveTv -> {
                        val intent = Intent(this, LiveTvActivity::class.java)
                        intent.putExtra("SHOW_PREVIEW", true)
                        intent.putExtra("PROFILE_NAME", currentProfile)
                        intent.putExtra("PROFILE_ICON", currentProfileIcon)
                        startActivity(intent)
                    }
                    R.id.cardMovies -> {
                        val intent = Intent(this, VodActivity::class.java)
                        intent.putExtra("SHOW_PREVIEW", false)
                        intent.putExtra("PROFILE_NAME", currentProfile)
                        intent.putExtra("PROFILE_ICON", currentProfileIcon)
                        startActivity(intent)
                    }
                    R.id.cardSeries -> {
                        val intent = Intent(this, SeriesActivity::class.java)
                        intent.putExtra("SHOW_PREVIEW", false)
                        intent.putExtra("PROFILE_NAME", currentProfile)
                        intent.putExtra("PROFILE_ICON", currentProfileIcon)
                        startActivity(intent)
                    }
                    R.id.cardKids -> {
                        val intent = Intent(this, KidsActivity::class.java)
                        intent.putExtra("SHOW_PREVIEW", false)
                        intent.putExtra("PROFILE_NAME", "Kids")
                        intent.putExtra("PROFILE_ICON", currentProfileIcon)
                        startActivity(intent)
                    }
                }
            }
        }

        if (isTelevisionDevice()) {
            binding.cardLiveTv.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    binding.bannerViewPager?.requestFocus(); true
                } else false
            }
            binding.cardMovies.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardLiveTv.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardSeries.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    binding.bannerViewPager?.requestFocus(); true
                } else false
            }
            binding.cardSeries.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardMovies.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardKids.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    binding.bannerViewPager?.requestFocus(); true
                } else false
            }
            binding.cardKids.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                    binding.cardSeries.requestFocus(); true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.action == KeyEvent.ACTION_DOWN) {
                    binding.bannerViewPager?.requestFocus(); true
                } else false
            }
        }
    }

    private fun mostrarDialogoSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair e desconectar?")
            .setPositiveButton("Sim") { _, _ ->

                // ✅ Limpa TODAS as SharedPreferences do app
                // Isso garante que last_profile_name não sobrevive ao logout
                getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vltv_home_prefs", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vltv_favoritos", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("vltv_text_cache", Context.MODE_PRIVATE).edit().clear().apply()

                // ✅ Vai direto para o Login limpando toda a pilha de telas
                // Com as prefs limpas, LoginActivity não vai fazer auto-login
                // e ProfilesActivity não vai fazer auto-navegação para a Home
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun carregarBannerAlternado() {
        val prefs = getSharedPreferences("vltv_home_prefs", Context.MODE_PRIVATE)
        val ultimoTipo = prefs.getString("ultimo_tipo_banner", "tv") ?: "tv"
        val tipoAtual = if (ultimoTipo == "tv") "movie" else "tv"
        prefs.edit().putString("ultimo_tipo_banner", tipoAtual).apply()

        val urlString = "https://api.themoviedb.org/3/trending/$tipoAtual/day?api_key=$TMDB_API_KEY&language=pt-BR&region=BR"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonTxt = URL(urlString).readText()
                val json = JSONObject(jsonTxt)
                val results = json.getJSONArray("results")

                if (results.length() > 0) {
                    val randomIndex = Random.nextInt(results.length())
                    val item = results.getJSONObject(randomIndex)
                    val backdropPath = item.getString("backdrop_path")

                    if (backdropPath != "null" && backdropPath.isNotBlank()) {
                        val imageUrl = "https://image.tmdb.org/t/p/original$backdropPath"
                        withContext(Dispatchers.Main) {
                            try {
                                val imgBannerView = binding.root.findViewById<ImageView>(R.id.imgBanner)
                                if (imgBannerView != null) {
                                    Glide.with(this@HomeActivity)
                                        .load(imageUrl)
                                        .centerCrop()
                                        .dontAnimate()
                                        .format(DecodeFormat.PREFER_RGB_565)
                                        .into(imgBannerView)
                                    imgBannerView.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mostrarDialogoSair()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun carregarContinuarAssistindoLocal() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val historyList = database.streamDao().getWatchHistory(currentProfile, 20)

                val vodItems = mutableListOf<VodItem>()
                val seriesMap = mutableMapOf<String, Boolean>()
                val seriesJaAdicionadas = mutableSetOf<String>()

                for (item in historyList) {
                    var finalId = item.stream_id.toString()
                    var finalName = limparNomeExibicao(item.name)
                    var finalIcon = item.icon ?: ""
                    val isSeries = item.is_series

                    if (isSeries) {
                        try {
                            var cleanName = item.name.replace(Regex("(?i)^(S\\d+E\\d+|T\\d+E\\d+|\\d+x\\d+|E\\d+)\\s*(-|:)?\\s*"), "")
                            if (cleanName.contains(":")) cleanName = cleanName.substringBefore(":")
                            cleanName = cleanName.replace(Regex("(?i)\\s+(S\\d+|T\\d+|E\\d+|Ep\\d+|Temporada|Season|Episode|Capitulo|\\d+x\\d+).*"), "")
                            if (cleanName.contains(" - ")) cleanName = cleanName.substringBefore(" - ")
                            cleanName = cleanName.trim()

                            val cursor = database.openHelper.writableDatabase.query(
                                "SELECT series_id, name, cover FROM series_streams WHERE name LIKE ? LIMIT 1",
                                arrayOf("%$cleanName%")
                            )

                            if (cursor.moveToFirst()) {
                                val realSeriesId = cursor.getInt(0).toString()
                                val realName = cursor.getString(1)
                                val realCover = cursor.getString(2)

                                if (seriesJaAdicionadas.contains(realSeriesId)) {
                                    cursor.close()
                                    continue
                                }

                                finalId = realSeriesId
                                finalName = limparNomeExibicao(realName)
                                finalIcon = realCover
                                seriesJaAdicionadas.add(realSeriesId)
                            }
                            cursor.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    vodItems.add(VodItem(finalId, finalName, finalIcon))
                    seriesMap[finalId] = isSeries
                }

                withContext(Dispatchers.Main) {
                    val tvTitle = binding.root.findViewById<TextView>(R.id.tvContinueWatching)
                    if (vodItems.isNotEmpty()) {
                        tvTitle?.visibility = View.VISIBLE
                        binding.rvContinueWatching.visibility = View.VISIBLE

                        binding.rvContinueWatching.adapter = HomeRowAdapter(vodItems) { selected ->
                            val isSeries = seriesMap[selected.id] ?: false

                            val intent = if (isSeries) {
                                Intent(this@HomeActivity, SeriesDetailsActivity::class.java).apply {
                                    putExtra("series_id", selected.id.toIntOrNull() ?: 0)
                                }
                            } else {
                                Intent(this@HomeActivity, DetailsActivity::class.java).apply {
                                    putExtra("stream_id", selected.id.toIntOrNull() ?: 0)
                                }
                            }

                            intent.putExtra("name", selected.name)
                            intent.putExtra("icon", selected.streamIcon)
                            intent.putExtra("PROFILE_NAME", currentProfile)
                            startActivity(intent)
                        }
                    } else {
                        tvTitle?.visibility = View.GONE
                        binding.rvContinueWatching.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class BannerAdapter(private var items: List<Any>) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {
        fun updateList(newItems: List<Any>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_banner_home, parent, false)
            return BannerViewHolder(view)
        }

        override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
            if (items.isNotEmpty()) {
                holder.bind(items[0])
            }
        }

        override fun getItemCount(): Int = items.size

        inner class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imgBanner: ImageView = itemView.findViewById(R.id.imgBanner)
            private val tvTitle: TextView = itemView.findViewById(R.id.tvBannerTitle)
            private val imgLogo: ImageView = itemView.findViewById(R.id.imgBannerLogo)
            private val btnPlay: View = itemView.findViewById(R.id.btnBannerPlay)
            // ✅ Novo botão Detalhes do banner premium
            private val btnInfo: View? = try { itemView.findViewById(R.id.btnBannerInfo) } catch (e: Exception) { null }

            fun bind(item: Any) {
                var title = ""
                var icon = ""
                var id = 0
                var isSeries = false
                var logoSalva: String? = null

                if (item is VodEntity) {
                    title = item.name; icon = item.stream_icon ?: ""; id = item.stream_id; isSeries = false; logoSalva = item.logo_url
                } else if (item is SeriesEntity) {
                    title = item.name; icon = item.cover ?: ""; id = item.series_id; isSeries = true; logoSalva = item.logo_url
                }

                val cleanTitle = limparNomeExibicao(title)
                tvTitle.text = cleanTitle

                if (!logoSalva.isNullOrEmpty()) {
                    tvTitle.visibility = View.GONE
                    imgLogo.visibility = View.VISIBLE
                    try {
                        Glide.with(itemView.context)
                            .load(logoSalva)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(imgLogo)
                    } catch (e: Exception) {}
                } else {
                    tvTitle.visibility = View.VISIBLE
                    imgLogo.visibility = View.GONE
                }

                buscarImagemBackgroundTMDB(limparNomeParaTMDB(title), isSeries, icon, id, imgBanner, imgLogo, tvTitle, suave = true)

                // ✅ Abre o player diretamente
                btnPlay.setOnClickListener {
                    val intent = if (isSeries) Intent(this@HomeActivity, SeriesDetailsActivity::class.java).apply { putExtra("series_id", id) }
                                 else Intent(this@HomeActivity, DetailsActivity::class.java).apply { putExtra("stream_id", id) }
                    intent.putExtra("name", title)
                    intent.putExtra("icon", icon)
                    intent.putExtra("PROFILE_NAME", currentProfile)
                    intent.putExtra("is_series", isSeries)
                    startActivity(intent)
                }

                // ✅ Botão Detalhes — abre a tela de detalhes (sem iniciar o player)
                btnInfo?.setOnClickListener {
                    val intent = if (isSeries) Intent(this@HomeActivity, SeriesDetailsActivity::class.java).apply { putExtra("series_id", id) }
                                 else Intent(this@HomeActivity, DetailsActivity::class.java).apply { putExtra("stream_id", id) }
                    intent.putExtra("name", title)
                    intent.putExtra("icon", icon)
                    intent.putExtra("PROFILE_NAME", currentProfile)
                    intent.putExtra("is_series", isSeries)
                    startActivity(intent)
                }

                itemView.setOnClickListener { btnPlay.performClick() }
            }
        }
    }

    private fun verificarPermissoesCast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissoes = arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
            val faltamPermissoes = permissoes.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (faltamPermissoes) {
                ActivityCompat.requestPermissions(this, permissoes, REQUEST_CODE_CAST_PERMISSIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_CAST_PERMISSIONS)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAST_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Busca de dispositivos ativada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ✅ TOP 10 ADAPTER — card com número enorme estilo Netflix
    inner class Top10Adapter(
        private val list: List<VodItem>,
        private val onItemClick: (VodItem) -> Unit
    ) : RecyclerView.Adapter<Top10Adapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPoster: ImageView = view.findViewById(R.id.ivPoster)
            val tvRank: TextView = view.findViewById(R.id.tvRankNumber)
            val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_top10_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvRank.text = (position + 1).toString()
            holder.tvTitle.text = item.name

            Glide.with(holder.itemView.context)
                .asBitmap()
                .load(item.streamIcon)
                .override(160, 240)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .placeholder(R.drawable.ic_launcher)
                .into(holder.ivPoster)

            holder.itemView.setOnClickListener { onItemClick(item) }

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.08f else 1.0f
                v.scaleY = if (hasFocus) 1.08f else 1.0f
                v.elevation = if (hasFocus) 12f else 0f
            }
        }

        override fun getItemCount() = list.size
    }
}
