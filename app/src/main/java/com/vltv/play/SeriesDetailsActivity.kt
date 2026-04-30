package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.Priority
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vltv.play.CastAdapter
import com.vltv.play.CastMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class SeriesDetailsActivity : AppCompatActivity() {
    private var seriesId: Int = 0
    private var seriesName: String = ""
    private var seriesIcon: String? = null
    private var seriesRating: String = "0.0"

    private var currentProfile: String = "Padrao"

    private lateinit var imgPoster: ImageView
    private lateinit var imgBackground: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var imgTitleLogo: ImageView
    private lateinit var tvRating: TextView
    private lateinit var tvGenre: TextView
    private lateinit var tvCast: TextView
    private lateinit var recyclerCast: RecyclerView
    private lateinit var tvPlot: TextView
    private lateinit var btnSeasonSelector: TextView
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var btnFavoriteSeries: ImageButton
    private lateinit var btnPlaySeries: Button
    private lateinit var btnDownloadEpisodeArea: LinearLayout
    private lateinit var imgDownloadEpisodeState: ImageView
    private lateinit var tvDownloadEpisodeState: TextView
    private lateinit var btnDownloadSeason: Button
    private lateinit var btnResume: Button
    private var appBarLayout: AppBarLayout? = null
    private var tabLayout: TabLayout? = null

    private var layoutProgress: LinearLayout? = null
    private var progressBarSeries: ProgressBar? = null
    private var tvTimeRemaining: TextView? = null
    private var btnRestartAction: LinearLayout? = null

    private lateinit var bottomNavigation: BottomNavigationView

    private lateinit var recyclerSuggestions: RecyclerView
    private lateinit var llTechBadges: LinearLayout
    private lateinit var tvBadge4k: TextView
    private lateinit var tvBadgeHdr: TextView
    private lateinit var tvBadgeDolby: TextView
    private lateinit var tvBadge51: TextView
    private lateinit var tvReleaseDate: TextView
    private lateinit var tvCreatedBy: TextView

    private var episodesBySeason: Map<String, List<EpisodeStream>> = emptyMap()
    private var sortedSeasons: List<String> = emptyList()
    private var currentSeason: String = ""
    private var currentEpisode: EpisodeStream? = null

    private enum class DownloadState { BAIXAR, BAIXANDO, BAIXADO }
    private var downloadState: DownloadState = DownloadState.BAIXAR
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series_details)

        currentProfile = intent.getStringExtra("PROFILE_NAME") ?: "Padrao"

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())

        seriesId = intent.getIntExtra("series_id", 0)
        seriesName = intent.getStringExtra("name") ?: ""
        seriesIcon = intent.getStringExtra("icon")
        seriesRating = intent.getStringExtra("rating") ?: "0.0"

        inicializarViews()
        verificarTecnologias(seriesName)

        appBarLayout?.addOnOffsetChangedListener { appBar, verticalOffset ->
            val percentage = Math.abs(verticalOffset).toFloat() / appBar.totalScrollRange
            val alphaValue = if (percentage > 0.6f) 0f else 1f - (percentage * 1.5f).coerceAtMost(1f)
            tvTitle.alpha = alphaValue
            imgTitleLogo.alpha = alphaValue
            btnPlaySeries.alpha = alphaValue
            btnResume.alpha = alphaValue
            layoutProgress?.alpha = alphaValue
            btnFavoriteSeries.alpha = alphaValue
            tvRating.alpha = alphaValue
            tvGenre.alpha = alphaValue
        }

        if (isTelevisionDevice()) {
            btnDownloadEpisodeArea.visibility = View.GONE
            btnDownloadSeason.visibility = View.GONE
        }

        tvTitle.text = seriesName
        tvRating.text = "Nota: $seriesRating"
        tvGenre.text = "Gênero: Buscando..."
        tvCast.text = "Elenco:"
        tvPlot.text = "Carregando sinopse..."

        btnSeasonSelector.setBackgroundColor(Color.parseColor("#333333"))
        btnSeasonSelector.setTextColor(Color.WHITE)

        // ✅ HD: poster com ARGB_8888 + fade suave
        Glide.with(this)
            .load(seriesIcon)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .override(400, 600)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.mipmap.ic_launcher)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .centerCrop()
            .into(imgPoster)

        // ✅ HD: background com qualidade máxima desde o início
        Glide.with(this)
            .load(seriesIcon)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .override(800, 600)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .centerCrop()
            .into(imgBackground)

        rvEpisodes.isFocusable = true
        rvEpisodes.isFocusableInTouchMode = true
        rvEpisodes.setHasFixedSize(true)
        rvEpisodes.layoutManager = LinearLayoutManager(this)

        rvEpisodes.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                val holder = rvEpisodes.findContainingViewHolder(view) as? EpisodeAdapter.VH
                holder?.let {
                    val position = holder.adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        currentEpisode = (rvEpisodes.adapter as? EpisodeAdapter)?.list?.getOrNull(position)
                        restaurarEstadoDownload()
                    }
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {}
        })

        recyclerSuggestions.layoutManager = GridLayoutManager(this, 3)
        recyclerSuggestions.setHasFixedSize(true)

        val isFavInicial = getFavSeries(this).contains(seriesId)
        atualizarIconeFavoritoSerie(isFavInicial)

        btnFavoriteSeries.setOnClickListener {
            val favs = getFavSeries(this)
            if (favs.contains(seriesId)) favs.remove(seriesId) else favs.add(seriesId)
            saveFavSeries(this, favs)
            atualizarIconeFavoritoSerie(favs.contains(seriesId))
        }

        btnSeasonSelector.setOnClickListener { mostrarSeletorDeTemporada() }

        btnPlaySeries.setOnClickListener {
            val epParaContinuar = encontrarEpisodioParaContinuar()
            if (epParaContinuar != null) {
                abrirPlayer(epParaContinuar, true)
            } else {
                val epEncontrado = encontrarEpisodioParaAssistir()
                if (epEncontrado != null) abrirPlayer(epEncontrado, false)
                else Toast.makeText(this, "Nenhum episódio encontrado.", Toast.LENGTH_SHORT).show()
            }
        }

        btnResume.setOnClickListener {
            encontrarEpisodioParaContinuar()?.let { abrirPlayer(it, true) }
        }

        btnRestartAction?.setOnClickListener {
            val ep = encontrarEpisodioParaContinuar() ?: encontrarEpisodioParaAssistir()
            if (ep != null) {
                AlertDialog.Builder(this)
                    .setTitle("Reiniciar Episódio")
                    .setMessage("Deseja assistir desde o início?")
                    .setPositiveButton("Sim") { _, _ -> abrirPlayer(ep, false) }
                    .setNegativeButton("Não", null)
                    .show()
            }
        }

        restaurarEstadoDownload()
        tentarCarregarLogoCache()
        carregarSeriesInfo()
        sincronizarDadosTMDB()

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { finish(); true }
                R.id.nav_search -> {
                    startActivity(Intent(this, SearchActivity::class.java).apply { putExtra("PROFILE_NAME", currentProfile) })
                    true
                }
                R.id.nav_novidades -> {
                    startActivity(Intent(this, NovidadesActivity::class.java).apply { putExtra("PROFILE_NAME", currentProfile) })
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, SettingsActivity::class.java).apply { putExtra("PROFILE_NAME", currentProfile) })
                    true
                }
                else -> false
            }
        }

        val commonFocus = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_focus_neon)
                if (v is Button) v.setTextColor(Color.YELLOW)
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
            } else {
                if (v is Button) {
                    v.setBackgroundResource(android.R.drawable.btn_default)
                    v.setTextColor(Color.WHITE)
                } else v.setBackgroundResource(0)
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }
        btnPlaySeries.onFocusChangeListener = commonFocus
        btnResume.onFocusChangeListener = commonFocus

        btnSeasonSelector.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundResource(R.drawable.bg_focus_neon)
                (v as TextView).setTextColor(Color.YELLOW)
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
            } else {
                v.setBackgroundColor(Color.parseColor("#333333"))
                (v as TextView).setTextColor(Color.WHITE)
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
            }
        }

        tabLayout?.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        rvEpisodes.visibility = View.VISIBLE
                        tvPlot.visibility = View.GONE
                        tvCast.visibility = View.GONE
                        recyclerCast.visibility = View.GONE
                        tvReleaseDate.visibility = View.GONE
                        tvCreatedBy.visibility = View.GONE
                        recyclerSuggestions.visibility = View.GONE
                    }
                    1 -> {
                        rvEpisodes.visibility = View.GONE
                        tvPlot.visibility = View.GONE
                        tvCast.visibility = View.GONE
                        recyclerCast.visibility = View.GONE
                        tvReleaseDate.visibility = View.GONE
                        tvCreatedBy.visibility = View.GONE
                        recyclerSuggestions.visibility = View.VISIBLE
                    }
                    2 -> {
                        rvEpisodes.visibility = View.GONE
                        recyclerSuggestions.visibility = View.GONE
                        tvPlot.visibility = View.VISIBLE
                        tvCast.visibility = View.VISIBLE
                        recyclerCast.visibility = View.VISIBLE
                        tvReleaseDate.visibility = View.VISIBLE
                        tvCreatedBy.visibility = View.VISIBLE
                        tvPlot.setTextColor(Color.WHITE)
                        tvCast.setTextColor(Color.WHITE)
                        tvReleaseDate.setTextColor(Color.WHITE)
                        tvCreatedBy.setTextColor(Color.WHITE)
                        tabLayout?.requestFocus()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun inicializarViews() {
        appBarLayout = findViewById(R.id.appBar)
        tabLayout = findViewById(R.id.tabLayout)
        if (tabLayout?.tabCount == 0) {
            tabLayout?.addTab(tabLayout!!.newTab().setText("EPISÓDIOS"))
            tabLayout?.addTab(tabLayout!!.newTab().setText("SUGESTÕES"))
            tabLayout?.addTab(tabLayout!!.newTab().setText("DETALHES"))
        }

        bottomNavigation = findViewById(R.id.bottomNavigation)
        imgPoster = findViewById(R.id.imgPoster)
        imgBackground = try { findViewById(R.id.imgBackground) } catch (e: Exception) { imgPoster }
        tvTitle = findViewById(R.id.tvTitle)
        tvTitle.visibility = View.INVISIBLE
        imgTitleLogo = findViewById(R.id.imgTitleLogo)
        tvRating = findViewById(R.id.tvRating)
        tvGenre = findViewById(R.id.tvGenre)
        llTechBadges = findViewById(R.id.llTechBadges)
        tvBadge4k = findViewById(R.id.tvBadge4k)
        tvBadgeHdr = findViewById(R.id.tvBadgeHdr)
        tvBadgeDolby = findViewById(R.id.tvBadgeDolby)
        tvBadge51 = findViewById(R.id.tvBadge51)
        tvPlot = findViewById(R.id.tvPlot)
        tvReleaseDate = findViewById(R.id.tvReleaseDate)
        tvCreatedBy = findViewById(R.id.tvCreatedBy)
        tvCast = findViewById(R.id.tvCast)
        recyclerCast = findViewById(R.id.recyclerCast)
        recyclerCast.visibility = View.GONE
        recyclerSuggestions = findViewById(R.id.recyclerSuggestions)
        btnSeasonSelector = findViewById(R.id.btnSeasonSelector)
        rvEpisodes = findViewById(R.id.recyclerEpisodes)
        btnPlaySeries = findViewById(R.id.btnPlay)
        btnFavoriteSeries = findViewById(R.id.btnFavorite)
        btnResume = findViewById(R.id.btnResume)
        btnDownloadEpisodeArea = findViewById(R.id.btnDownloadArea)
        imgDownloadEpisodeState = findViewById(R.id.imgDownloadState)
        tvDownloadEpisodeState = findViewById(R.id.tvDownloadState)
        btnDownloadSeason = findViewById(R.id.btnDownloadSeason)
        btnDownloadEpisodeArea.visibility = View.GONE
        btnDownloadSeason.visibility = View.GONE
        layoutProgress = findViewById(R.id.layoutProgress)
        progressBarSeries = findViewById(R.id.progressBarMovie)
        tvTimeRemaining = findViewById(R.id.tvTimeRemaining)
        btnRestartAction = findViewById(R.id.btnRestartAction)
    }

    private fun verificarTecnologias(nome: String) {
        val n = nome.uppercase()
        var tem = false
        if (n.contains("4K") || n.contains("UHD")) { tvBadge4k.visibility = View.VISIBLE; tem = true }
        if (n.contains("HDR")) { tvBadgeHdr.visibility = View.VISIBLE; tem = true }
        if (n.contains("DOLBY") || n.contains("VISION")) { tvBadgeDolby.visibility = View.VISIBLE; tem = true }
        if (n.contains("5.1")) { tvBadge51.visibility = View.VISIBLE; tem = true }
        llTechBadges.visibility = if (tem) View.VISIBLE else View.GONE
    }

    private fun sincronizarDadosTMDB() {
        val apiKey = "9b73f5dd15b8165b1b57419be2f29128"
        var cleanName = seriesName
        cleanName = cleanName.replace(Regex("[\\(\\[\\{].*?[\\)\\]\\}]"), "")
        cleanName = cleanName.replace(Regex("\\b\\d{4}\\b"), "")
        listOf("FHD", "HD", "SD", "4K", "8K", "H265", "LEG", "DUBLADO", "DUB", "|", "-", "_", ".")
            .forEach { cleanName = cleanName.replace(it, "", ignoreCase = true) }
        cleanName = cleanName.trim().replace(Regex("\\s+"), " ")
        val encodedName = try { URLEncoder.encode(cleanName, "UTF-8") } catch (e: Exception) { cleanName }
        val url = "https://api.themoviedb.org/3/search/tv?api_key=$apiKey&query=$encodedName&language=pt-BR&region=BR"

        client.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { tvTitle.visibility = View.VISIBLE; tvTitle.text = cleanName }
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val results = JSONObject(body).optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val show = results.getJSONObject(0)
                        val tmdbId = show.getInt("id")
                        buscarLogoSerieTraduzida(tmdbId, apiKey, cleanName)
                        buscarDetalhesTMDB(tmdbId, apiKey)
                        runOnUiThread {
                            val sinopse = show.optString("overview")
                            tvPlot.text = if (sinopse.isNotEmpty()) sinopse else "Sinopse indisponível."
                            val vote = show.optDouble("vote_average", 0.0)
                            if (vote > 0) tvRating.text = "Nota: ${String.format("%.1f", vote)}"

                            val backdropPath = show.optString("backdrop_path")
                            if (backdropPath.isNotEmpty() && imgBackground != imgPoster) {
                                // ✅ HD: backdrop em resolução original com fade suave
                                Glide.with(this@SeriesDetailsActivity)
                                    .load("https://image.tmdb.org/t/p/original$backdropPath")
                                    .format(DecodeFormat.PREFER_ARGB_8888)
                                    .override(1280, 720)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .transition(DrawableTransitionOptions.withCrossFade(400))
                                    .centerCrop()
                                    .into(imgBackground)
                            }

                            // ✅ HD: poster também em qualidade máxima
                            Glide.with(this@SeriesDetailsActivity)
                                .load(seriesIcon)
                                .format(DecodeFormat.PREFER_ARGB_8888)
                                .override(400, 600)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .transition(DrawableTransitionOptions.withCrossFade(300))
                                .placeholder(R.mipmap.ic_launcher)
                                .centerCrop()
                                .into(imgPoster)
                        }
                    } else {
                        runOnUiThread { tvTitle.visibility = View.VISIBLE; tvTitle.text = cleanName }
                    }
                } catch (e: Exception) {
                    runOnUiThread { tvTitle.visibility = View.VISIBLE; tvTitle.text = cleanName }
                }
            }
        })
    }

    private fun buscarLogoSerieTraduzida(id: Int, key: String, nomeLimpo: String) {
        val imagesUrl = "https://api.themoviedb.org/3/tv/$id/images?api_key=$key&include_image_language=pt,null"
        client.newCall(Request.Builder().url(imagesUrl).build()).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: run {
                    runOnUiThread { imgTitleLogo.visibility = View.GONE; tvTitle.visibility = View.VISIBLE; tvTitle.text = nomeLimpo }
                    return
                }
                try {
                    val logos = JSONObject(body).optJSONArray("logos")
                    if (logos != null && logos.length() > 0) {
                        var logoPath: String? = null
                        for (i in 0 until logos.length()) {
                            val logo = logos.getJSONObject(i)
                            if (logo.optString("iso_639_1", "").equals("pt", ignoreCase = true)) {
                                val fp = logo.optString("file_path", "")
                                if (fp.isNotEmpty()) { logoPath = fp; break }
                            }
                        }
                        if (!logoPath.isNullOrEmpty()) {
                            val finalUrl = "https://image.tmdb.org/t/p/w500$logoPath"
                            getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)
                                .edit().putString("series_logo_$seriesId", finalUrl).apply()
                            runOnUiThread {
                                tvTitle.visibility = View.GONE
                                imgTitleLogo.visibility = View.VISIBLE
                                // ✅ HD: logo em qualidade máxima com fade
                                Glide.with(this@SeriesDetailsActivity)
                                    .load(finalUrl)
                                    .format(DecodeFormat.PREFER_ARGB_8888)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .transition(DrawableTransitionOptions.withCrossFade(250))
                                    .into(imgTitleLogo)
                            }
                        } else {
                            runOnUiThread { imgTitleLogo.visibility = View.GONE; tvTitle.visibility = View.VISIBLE; tvTitle.text = nomeLimpo }
                        }
                    } else {
                        runOnUiThread { imgTitleLogo.visibility = View.GONE; tvTitle.visibility = View.VISIBLE; tvTitle.text = nomeLimpo }
                    }
                } catch (e: Exception) {
                    runOnUiThread { imgTitleLogo.visibility = View.GONE; tvTitle.visibility = View.VISIBLE; tvTitle.text = nomeLimpo }
                }
            }
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                runOnUiThread { imgTitleLogo.visibility = View.GONE; tvTitle.visibility = View.VISIBLE; tvTitle.text = nomeLimpo }
            }
        })
    }

    private fun buscarDetalhesTMDB(id: Int, key: String) {
        val url = "https://api.themoviedb.org/3/tv/$id?api_key=$key&append_to_response=credits,recommendations&language=pt-BR"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {}
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: return
                try {
                    val d = JSONObject(body)
                    val gs = d.optJSONArray("genres")
                    val genresList = mutableListOf<String>()
                    if (gs != null) for (i in 0 until gs.length()) genresList.add(gs.getJSONObject(i).getString("name"))

                    val castArray = d.optJSONObject("credits")?.optJSONArray("cast")
                    val castNames = mutableListOf<String>()
                    if (castArray != null) {
                        val limit = minOf(castArray.length(), 10)
                        for (i in 0 until limit) castNames.add(castArray.getJSONObject(i).getString("name"))
                    }

                    val firstAirDate = d.optString("first_air_date", "")
                    val createdByArray = d.optJSONArray("created_by")
                    val creatorsList = mutableListOf<String>()
                    if (createdByArray != null) for (i in 0 until createdByArray.length()) creatorsList.add(createdByArray.getJSONObject(i).getString("name"))

                    val similarResults = d.optJSONObject("recommendations")?.optJSONArray("results")
                    val sugestoesList = ArrayList<JSONObject>()
                    if (similarResults != null) for (i in 0 until similarResults.length()) sugestoesList.add(similarResults.getJSONObject(i))

                    runOnUiThread {
                        tvGenre.text = "Gênero: ${if (genresList.isEmpty()) "Variados" else genresList.joinToString(", ")}"
                        tvCast.text = "Elenco: ${castNames.joinToString(", ")}"
                        if (firstAirDate.isNotEmpty()) {
                            tvReleaseDate.text = "Lançamento: ${firstAirDate.split("-")[0]}"
                            tvReleaseDate.visibility = View.VISIBLE
                        }
                        if (creatorsList.isNotEmpty()) {
                            tvCreatedBy.text = "Criado por: ${creatorsList.joinToString(", ")}"
                            tvCreatedBy.visibility = View.VISIBLE
                        }
                        if (sugestoesList.isNotEmpty()) recyclerSuggestions.adapter = SuggestionsAdapter(sugestoesList)
                    }
                } catch (e: Exception) {}
            }
        })
    }

    private fun tentarCarregarLogoCache() {
        val prefs = getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)
        val cachedUrl = prefs.getString("series_logo_$seriesId", null)
        if (cachedUrl != null) {
            tvTitle.visibility = View.GONE
            imgTitleLogo.visibility = View.VISIBLE
            // ✅ HD: logo do cache também em qualidade máxima
            Glide.with(this)
                .load(cachedUrl)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(imgTitleLogo)
        }
    }

    override fun onResume() {
        super.onResume()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
        restaurarEstadoDownload()
        verificarResume()
    }

    private fun isTelevisionDevice() = packageManager.hasSystemFeature("android.software.leanback") ||
            packageManager.hasSystemFeature("android.hardware.type.television")

    private fun getFavSeries(context: Context): MutableSet<Int> {
        val prefs = context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val set = prefs.getStringSet("${currentProfile}_fav_series", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toIntOrNull() }.toMutableSet()
    }

    private fun saveFavSeries(context: Context, ids: Set<Int>) {
        context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit()
            .putStringSet("${currentProfile}_fav_series", ids.map { it.toString() }.toSet()).apply()
    }

    private fun atualizarIconeFavoritoSerie(isFav: Boolean) {
        if (isFav) {
            btnFavoriteSeries.setImageResource(android.R.drawable.btn_star_big_on)
            btnFavoriteSeries.setColorFilter(Color.parseColor("#FFD700"))
        } else {
            btnFavoriteSeries.setImageResource(android.R.drawable.btn_star_big_off)
            btnFavoriteSeries.clearColorFilter()
        }
    }

    private fun carregarSeriesInfo() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("username", "") ?: ""
        val password = prefs.getString("password", "") ?: ""
        XtreamApi.service.getSeriesInfoV2(username, password, seriesId = seriesId)
            .enqueue(object : Callback<SeriesInfoResponse> {
                override fun onResponse(call: Call<SeriesInfoResponse>, response: Response<SeriesInfoResponse>) {
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        episodesBySeason = body.episodes ?: emptyMap()
                        sortedSeasons = episodesBySeason.keys.sortedBy { it.toIntOrNull() ?: 0 }
                        if (sortedSeasons.isNotEmpty()) {
                            mudarTemporada(sortedSeasons.first())
                            verificarResume()
                        } else {
                            btnSeasonSelector.text = "Indisponível"
                            btnSeasonSelector.setTextColor(Color.WHITE)
                        }
                    }
                }
                override fun onFailure(call: Call<SeriesInfoResponse>, t: Throwable) {
                    Toast.makeText(this@SeriesDetailsActivity, "Erro de conexão", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun mostrarSeletorDeTemporada() {
        if (sortedSeasons.isEmpty()) return
        val dialog = BottomSheetDialog(this, R.style.DialogTemporadaTransparente)
        val root = RelativeLayout(this)
        root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 500.toPx())
        root.setBackgroundColor(Color.TRANSPARENT)
        val btnClose = ImageButton(this)
        btnClose.id = View.generateViewId()
        val closeParams = RelativeLayout.LayoutParams(65.toPx(), 65.toPx())
        closeParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        closeParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
        closeParams.setMargins(0, 0, 0, 30.toPx())
        btnClose.layoutParams = closeParams
        btnClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        btnClose.setColorFilter(Color.WHITE)
        btnClose.background = null
        btnClose.scaleType = ImageView.ScaleType.FIT_CENTER
        btnClose.setPadding(10.toPx(), 10.toPx(), 10.toPx(), 10.toPx())
        btnClose.isFocusable = true
        btnClose.isClickable = true
        btnClose.setOnClickListener { dialog.dismiss() }
        btnClose.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) { v.setBackgroundResource(R.drawable.bg_focus_neon); v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).start() }
            else { v.setBackgroundResource(0); v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start() }
        }
        val rvSeasons = RecyclerView(this)
        val rvParams = RelativeLayout.LayoutParams(250.toPx(), ViewGroup.LayoutParams.WRAP_CONTENT)
        rvParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        rvParams.addRule(RelativeLayout.ABOVE, btnClose.id)
        rvParams.addRule(RelativeLayout.CENTER_HORIZONTAL)
        rvParams.setMargins(0, 10.toPx(), 0, 10.toPx())
        rvSeasons.layoutParams = rvParams
        rvSeasons.layoutManager = LinearLayoutManager(this)
        rvSeasons.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val tv = TextView(parent.context)
                tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                tv.setPadding(20, 35, 20, 35); tv.gravity = Gravity.CENTER; tv.textSize = 22f
                tv.setTextColor(Color.WHITE); tv.isFocusable = true; tv.isClickable = true
                return object : RecyclerView.ViewHolder(tv) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val season = sortedSeasons[position]
                val tv = holder.itemView as TextView
                tv.text = "Temporada $season"
                tv.setOnFocusChangeListener { v, hasFocus ->
                    if (hasFocus) { v.setBackgroundResource(R.drawable.bg_focus_neon); (v as TextView).setTextColor(Color.YELLOW); v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start() }
                    else { v.setBackgroundColor(Color.TRANSPARENT); (v as TextView).setTextColor(Color.WHITE); v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start() }
                }
                tv.setOnClickListener { mudarTemporada(season); dialog.dismiss() }
            }
            override fun getItemCount() = sortedSeasons.size
        }
        root.addView(btnClose); root.addView(rvSeasons)
        dialog.setContentView(root)
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let {
            com.google.android.material.bottomsheet.BottomSheetBehavior.from(it).peekHeight = 500.toPx()
            it.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
        rvSeasons.postDelayed({ rvSeasons.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }, 150)
    }

    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    private fun mudarTemporada(seasonKey: String) {
        currentSeason = seasonKey
        btnSeasonSelector.text = "Temporada $seasonKey ▼"
        btnSeasonSelector.setTextColor(Color.WHITE)
        val lista = episodesBySeason[seasonKey] ?: emptyList()
        if (lista.isNotEmpty()) { currentEpisode = lista.first(); restaurarEstadoDownload(); verificarResume() }
        rvEpisodes.adapter = EpisodeAdapter(this, lista, currentProfile) { ep, _ ->
            currentEpisode = ep; restaurarEstadoDownload(); verificarResume(); abrirPlayer(ep, true)
        }
    }

    private fun abrirPlayer(ep: EpisodeStream, usarResume: Boolean) {
        val streamId = ep.id.toIntOrNull() ?: 0
        val lista = episodesBySeason[currentSeason] ?: emptyList()
        val posInList = lista.indexOfFirst { it.id == ep.id }
        val nextEp = if (posInList + 1 < lista.size) lista[posInList + 1] else null
        val mochilaIds = ArrayList<Int>()
        lista.forEach { item -> val idInt = item.id.toIntOrNull() ?: 0; if (idInt != 0) mochilaIds.add(idInt) }
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val pos = prefs.getLong("${currentProfile}_series_resume_${streamId}_pos", 0L)
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra("stream_id", streamId)
        intent.putExtra("stream_ext", ep.container_extension ?: "mp4")
        intent.putExtra("stream_type", "series")
        intent.putExtra("channel_name", "T${currentSeason}E${ep.episode_num} - $seriesName")
        intent.putExtra("PROFILE_NAME", currentProfile)
        if (mochilaIds.isNotEmpty()) intent.putIntegerArrayListExtra("episode_list", mochilaIds)
        if (usarResume && pos > 30000L) intent.putExtra("start_position_ms", pos)
        if (nextEp != null) {
            intent.putExtra("next_stream_id", nextEp.id.toIntOrNull() ?: 0)
            intent.putExtra("next_channel_name", "T${currentSeason}E${nextEp.episode_num} - $seriesName")
        }
        startActivity(intent)
    }

    private fun encontrarEpisodioParaAssistir(): EpisodeStream? {
        if (sortedSeasons.isNotEmpty()) {
            val eps = episodesBySeason[sortedSeasons.first()]
            if (!eps.isNullOrEmpty()) { currentSeason = sortedSeasons.first(); currentEpisode = eps.first(); return eps.first() }
        }
        return null
    }

    private fun encontrarEpisodioParaContinuar(): EpisodeStream? {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        var ultimoEp: EpisodeStream? = null
        for (season in sortedSeasons) {
            val eps = episodesBySeason[season] ?: continue
            for (ep in eps) {
                val sid = ep.id.toIntOrNull() ?: 0
                if (prefs.getLong("${currentProfile}_series_resume_${sid}_pos", 0L) > 30000L) {
                    currentSeason = season; ultimoEp = ep
                }
            }
        }
        return ultimoEp
    }

    private fun verificarResume() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        var epRecente: EpisodeStream? = null
        var maxPos = 0L; var maxDur = 0L; var seasonAchada = ""
        for (season in sortedSeasons) {
            val eps = episodesBySeason[season] ?: continue
            for (ep in eps) {
                val sid = ep.id.toIntOrNull() ?: 0
                val pos = prefs.getLong("${currentProfile}_series_resume_${sid}_pos", 0L)
                val dur = prefs.getLong("${currentProfile}_series_resume_${sid}_dur", 0L)
                if (pos > 30000L) { epRecente = ep; maxPos = pos; maxDur = dur; seasonAchada = season }
            }
        }
        runOnUiThread {
            if (epRecente != null && maxDur > 0) {
                btnPlaySeries.text = "▶ CONTINUAR T${seasonAchada}:E${epRecente.episode_num}"
                btnResume.visibility = View.VISIBLE
                btnRestartAction?.visibility = View.VISIBLE
                layoutProgress?.visibility = View.VISIBLE
                progressBarSeries?.progress = ((maxPos.toFloat() / maxDur.toFloat()) * 100).toInt()
                val restMs = maxDur - maxPos
                tvTimeRemaining?.text = "Restam ${TimeUnit.MILLISECONDS.toHours(restMs)}h${TimeUnit.MILLISECONDS.toMinutes(restMs) % 60}min"
            } else {
                btnPlaySeries.text = "▶ ASSISTIR"
                btnResume.visibility = View.GONE
                btnRestartAction?.visibility = View.GONE
                layoutProgress?.visibility = View.GONE
            }
        }
    }

    private fun restaurarEstadoDownload() { /* botão removido */ }

    override fun onDestroy() { client.dispatcher.cancelAll(); super.onDestroy() }

    class EpisodeAdapter(
        private val activity: SeriesDetailsActivity,
        val list: List<EpisodeStream>,
        private val profile: String,
        private val onClick: (EpisodeStream, Int) -> Unit
    ) : RecyclerView.Adapter<EpisodeAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvEpisodeTitle)
            val imgThumb: ImageView = v.findViewById(R.id.imgEpisodeThumb)
            val tvPlotEp: TextView = v.findViewById(R.id.tvEpisodePlot)
            val btnDownloadItem: LinearLayout = v.findViewById(R.id.btnDownloadEpisode)
            val pbEpisodeProgress: ProgressBar? = v.findViewById(R.id.pbEpisodeProgress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ep = list[position]
            holder.tvTitle.text = "E${ep.episode_num.toString().padStart(2, '0')} - ${ep.title}"
            holder.tvPlotEp.text = ep.info?.plot ?: "Sem descrição disponível."

            // ✅ HD: thumbnail do episódio em qualidade máxima com fade
            Glide.with(holder.itemView.context)
                .load(ep.info?.movie_image ?: "")
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(400, 225)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .placeholder(android.R.color.darker_gray)
                .error(android.R.color.black)
                .centerCrop()
                .into(holder.imgThumb)

            val epId = ep.id.toIntOrNull() ?: 0
            val prefs = holder.itemView.context.getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            val pos = prefs.getLong("${profile}_series_resume_${epId}_pos", 0)
            val dur = prefs.getLong("${profile}_series_resume_${epId}_dur", 0)
            if (pos > 0 && dur > 0) {
                val pct = ((pos.toFloat() / dur.toFloat()) * 100).toInt()
                holder.pbEpisodeProgress?.visibility = View.VISIBLE
                holder.pbEpisodeProgress?.progress = pct
            } else {
                holder.pbEpisodeProgress?.visibility = View.GONE
            }

            holder.btnDownloadItem.visibility = View.GONE
            holder.itemView.setOnClickListener { onClick(ep, position) }
            holder.itemView.setOnFocusChangeListener { view, hasFocus ->
                holder.tvTitle.setTextColor(if (hasFocus) Color.YELLOW else Color.WHITE)
                if (hasFocus) { view.setBackgroundResource(R.drawable.bg_focus_neon); view.animate().scaleX(1.05f).scaleY(1.05f).setDuration(200).start() }
                else { view.setBackgroundResource(0); view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start() }
            }
        }

        override fun getItemCount() = list.size
    }

    inner class SuggestionsAdapter(val items: List<JSONObject>) : RecyclerView.Adapter<SuggestionsAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(android.R.id.icon)
            val tvName: TextView = v.findViewById(android.R.id.text1)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(12, 12, 12, 12) }
                gravity = Gravity.CENTER_HORIZONTAL; isFocusable = true; isClickable = true
            }
            val card = androidx.cardview.widget.CardView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(130.toPx(), 200.toPx()); radius = 12f; cardElevation = 4f
            }
            val img = ImageView(parent.context).apply { id = android.R.id.icon; scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(img)
            val tv = TextView(parent.context).apply {
                id = android.R.id.text1
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
                setTextColor(Color.WHITE); textSize = 12f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER
            }
            container.addView(card); container.addView(tv)
            return ViewHolder(container)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val posterPath = item.optString("poster_path")
            // ✅ HD: sugestões também em qualidade máxima
            Glide.with(holder.itemView.context)
                .load("https://image.tmdb.org/t/p/w342$posterPath")
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(holder.img)
            holder.tvName.text = item.optString("name")
            holder.itemView.setOnClickListener {
                startActivity(Intent(holder.itemView.context, SeriesDetailsActivity::class.java).apply {
                    putExtra("series_id", item.optInt("id"))
                    putExtra("name", item.optString("name"))
                    putExtra("icon", "https://image.tmdb.org/t/p/w342$posterPath")
                    putExtra("rating", item.optDouble("vote_average", 0.0).toString())
                    putExtra("PROFILE_NAME", currentProfile)
                })
            }
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) { v.animate().scaleX(1.05f).scaleY(1.05f).start(); v.setBackgroundResource(R.drawable.bg_focus_neon) }
                else { v.animate().scaleX(1.0f).scaleY(1.0f).start(); v.setBackgroundResource(0) }
            }
        }
        override fun getItemCount() = items.size
    }
}
