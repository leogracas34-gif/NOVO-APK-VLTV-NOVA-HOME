package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class KidsActivity : AppCompatActivity() {

    private lateinit var rvHubChannels: RecyclerView
    private lateinit var rvRecentKids: RecyclerView
    private lateinit var rvMoviesKids: RecyclerView
    private lateinit var rvSeriesKids: RecyclerView
    private lateinit var tvTitleRecent: TextView
    private lateinit var etSearchKids: EditText
    private lateinit var prefs: SharedPreferences

    private var user = ""
    private var pass = ""

    private val termosProibidos = listOf(
        "adulto", "xxx", "sexo", "sexy", "porn", "18+", "erótico",
        "violência", "007", "terror", "horror", "assassinato", "guerra", "pânico", "morte"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_kids)

        prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        user  = prefs.getString("username", "") ?: ""
        pass  = prefs.getString("password", "") ?: ""

        tvTitleRecent  = findViewById(R.id.tvTitleRecent)
        rvHubChannels  = findViewById(R.id.rvHubChannels)
        rvRecentKids   = findViewById(R.id.rvRecentKids)
        rvMoviesKids   = findViewById(R.id.rvMoviesKids)
        rvSeriesKids   = findViewById(R.id.rvSeriesKids)
        etSearchKids   = findViewById(R.id.etSearchKids)

        findViewById<TextView>(R.id.btnBackKids).let {
            configurarFoco(it)
            it.setOnClickListener { finish() }
        }

        configurarFoco(etSearchKids)
        etSearchKids.isFocusableInTouchMode = true
        etSearchKids.setOnClickListener {
            etSearchKids.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearchKids, InputMethodManager.SHOW_FORCED)
        }
        etSearchKids.setOnEditorActionListener { v, actionId, _ ->
            val ehAcao = actionId == EditorInfo.IME_ACTION_SEARCH ||
                         actionId == EditorInfo.IME_ACTION_DONE ||
                         actionId == EditorInfo.IME_ACTION_GO
            if (ehAcao) {
                val query = v.text.toString().trim()
                val bloqueada = termosProibidos.any { query.contains(it, ignoreCase = true) }
                if (query.isNotEmpty() && !bloqueada) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                    etSearchKids.clearFocus()
                    startActivity(Intent(this, SearchActivity::class.java).apply {
                        putExtra("query", query)
                        putExtra("search_text", query)
                        putExtra("initial_query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } else if (bloqueada) {
                    Toast.makeText(this, "Busca bloqueada na Área Kids 🛡️", Toast.LENGTH_LONG).show()
                    etSearchKids.setText("")
                }
                true
            } else false
        }

        setupLayouts()
        setupHubChannels()
        carregarConteudoKids()
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        nav?.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home      -> { finish(); true }
                R.id.nav_search    -> { startActivity(Intent(this, SearchActivity::class.java)); false }
                R.id.nav_novidades -> { startActivity(Intent(this, NovidadesActivity::class.java)); false }
                R.id.nav_profile   -> { startActivity(Intent(this, SettingsActivity::class.java)); false }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        etSearchKids.setText("")
        etSearchKids.clearFocus()
        atualizarRecentesVisual()
    }

    private fun configurarFoco(view: View) {
        view.isFocusable = true
        view.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                v.setBackgroundResource(R.drawable.bg_selector_kids)
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                v.background = null
            }
        }
    }

    private fun setupLayouts() {
        rvHubChannels.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvRecentKids.layoutManager  = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvMoviesKids.layoutManager  = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        rvSeriesKids.layoutManager  = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
    }

    private fun setupHubChannels() {
        val nomesDesejados = listOf("Cartoon Network", "Discovery Kids", "Gloob", "Cartoonito", "Nickelodeon")
        XtreamApi.service.getLiveStreams(user, pass, categoryId = "0")
            .enqueue(object : Callback<List<LiveStream>> {
                override fun onResponse(call: Call<List<LiveStream>>, response: Response<List<LiveStream>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val todos = response.body()!!
                        val hub = nomesDesejados.mapNotNull { nome ->
                            todos.firstOrNull { it.name.contains(nome, ignoreCase = true) }
                        }
                        rvHubChannels.adapter = HubAdapter(hub) { canal ->
                            startActivity(Intent(this@KidsActivity, PlayerActivity::class.java).apply {
                                putExtra("stream_id", canal.id)
                                putExtra("name", canal.name)
                                putExtra("title", canal.name)
                                putExtra("type", "live")
                                putExtra("epg_channel_id", canal.epg_channel_id)
                            })
                        }
                    }
                }
                override fun onFailure(call: Call<List<LiveStream>>, t: Throwable) {}
            })
    }

    private fun carregarConteudoKids() {
        // Filmes kids
        XtreamApi.service.getVodCategories(user, pass)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (!response.isSuccessful || response.body() == null) return
                    try {
                        val cats = parseCategorias(response.body()!!.string())
                        val kidsCats = cats.filter {
                            val n = it.name.lowercase()
                            n.contains("kids") || n.contains("infantil") ||
                            n.contains("desenho") || n.contains("disney")
                        }
                        kidsCats.forEach { cat ->
                            XtreamApi.service.getVodStreams(user, pass, categoryId = cat.id)
                                .enqueue(object : Callback<List<VodStream>> {
                                    override fun onResponse(call: Call<List<VodStream>>, res: Response<List<VodStream>>) {
                                        if (!res.isSuccessful || res.body() == null) return
                                        val nova = res.body()!!
                                        val atual = (rvMoviesKids.adapter as? KidsVodAdapter)?.list?.toMutableList() ?: mutableListOf()
                                        atual.addAll(nova)
                                        rvMoviesKids.adapter = KidsVodAdapter(atual.distinctBy { it.id }) { filme ->
                                            salvarNosRecentes(filme.id.toString(), "movie")
                                            abrirDetalhesFilme(filme)
                                        }
                                    }
                                    override fun onFailure(call: Call<List<VodStream>>, t: Throwable) {}
                                })
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
            })

        // Séries kids
        XtreamApi.service.getSeriesCategories(user, pass)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    if (!response.isSuccessful || response.body() == null) return
                    try {
                        val cats = parseCategorias(response.body()!!.string())
                        val kidsCats = cats.filter {
                            val n = it.name.lowercase()
                            n.contains("kids") || n.contains("infantil") ||
                            n.contains("desenho") || n.contains("disney")
                        }
                        kidsCats.forEach { cat ->
                            XtreamApi.service.getSeries(user, pass, categoryId = cat.id)
                                .enqueue(object : Callback<List<SeriesStream>> {
                                    override fun onResponse(call: Call<List<SeriesStream>>, res: Response<List<SeriesStream>>) {
                                        if (!res.isSuccessful || res.body() == null) return
                                        val nova = res.body()!!
                                        val atual = (rvSeriesKids.adapter as? KidsSeriesAdapter)?.list?.toMutableList() ?: mutableListOf()
                                        atual.addAll(nova)
                                        rvSeriesKids.adapter = KidsSeriesAdapter(atual.distinctBy { it.id }) { serie ->
                                            salvarNosRecentes(serie.id.toString(), "series")
                                            abrirDetalhesSerie(serie)
                                        }
                                    }
                                    override fun onFailure(call: Call<List<SeriesStream>>, t: Throwable) {}
                                })
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {}
            })
    }

    private fun parseCategorias(rawJson: String): List<LiveCategory> {
        val lista = mutableListOf<LiveCategory>()
        val gson = Gson()
        try {
            if (rawJson.trim().startsWith("[")) {
                val tipo = object : TypeToken<List<LiveCategory>>() {}.type
                lista.addAll(gson.fromJson(rawJson, tipo))
            } else if (rawJson.trim().startsWith("{")) {
                val obj = JSONObject(rawJson)
                obj.keys().forEach { key ->
                    lista.add(gson.fromJson(obj.getJSONObject(key).toString(), LiveCategory::class.java))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return lista
    }

    private fun salvarNosRecentes(id: String, tipo: String) {
        val key = if (tipo == "movie") "kids_recent_vod" else "kids_recent_series"
        val atuais = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        atuais.add(id)
        prefs.edit().putStringSet(key, atuais.take(10).toSet()).apply()
    }

    private fun atualizarRecentesVisual() {
        val recentVodIds    = prefs.getStringSet("kids_recent_vod", emptySet()) ?: emptySet()
        val recentSeriesIds = prefs.getStringSet("kids_recent_series", emptySet()) ?: emptySet()
        if (recentVodIds.isEmpty() && recentSeriesIds.isEmpty()) return

        val unificada = mutableListOf<KidsRecentItem>()

        if (recentVodIds.isNotEmpty()) {
            XtreamApi.service.getAllVodStreams(user, pass)
                .enqueue(object : Callback<List<VodStream>> {
                    override fun onResponse(call: Call<List<VodStream>>, response: Response<List<VodStream>>) {
                        if (!response.isSuccessful || response.body() == null) return
                        response.body()!!
                            .filter { recentVodIds.contains(it.id.toString()) }
                            .forEach { unificada.add(KidsRecentItem(it.id.toString(), it.name, it.icon ?: "", "movie", it, null)) }
                        exibirRecentes(unificada)
                    }
                    override fun onFailure(call: Call<List<VodStream>>, t: Throwable) {}
                })
        }

        if (recentSeriesIds.isNotEmpty()) {
            XtreamApi.service.getAllSeries(user, pass)
                .enqueue(object : Callback<List<SeriesStream>> {
                    override fun onResponse(call: Call<List<SeriesStream>>, response: Response<List<SeriesStream>>) {
                        if (!response.isSuccessful || response.body() == null) return
                        response.body()!!
                            .filter { recentSeriesIds.contains(it.id.toString()) }
                            .forEach { unificada.add(KidsRecentItem(it.id.toString(), it.name, it.icon ?: "", "series", null, it)) }
                        exibirRecentes(unificada)
                    }
                    override fun onFailure(call: Call<List<SeriesStream>>, t: Throwable) {}
                })
        }
    }

    private fun exibirRecentes(itens: List<KidsRecentItem>) {
        val final = itens.distinctBy { it.id }.reversed()
        if (final.isNotEmpty()) {
            tvTitleRecent.visibility = View.VISIBLE
            rvRecentKids.visibility  = View.VISIBLE
            rvRecentKids.adapter = KidsUnifiedAdapter(final) { item ->
                if (item.tipo == "movie" && item.filmeObj != null) abrirDetalhesFilme(item.filmeObj)
                else if (item.tipo == "series" && item.serieObj != null) abrirDetalhesSerie(item.serieObj)
            }
        }
    }

    private fun abrirDetalhesFilme(filme: VodStream) {
        startActivity(Intent(this, DetailsActivity::class.java).apply {
            putExtra("stream_id", filme.id)
            putExtra("stream_ext", filme.extension ?: "mp4")
            putExtra("name", filme.name)
            putExtra("icon", filme.icon)
            putExtra("rating", filme.rating ?: "0.0")
        })
    }

    private fun abrirDetalhesSerie(serie: SeriesStream) {
        startActivity(Intent(this, SeriesDetailsActivity::class.java).apply {
            putExtra("series_id", serie.id)
            putExtra("name", serie.name)
            putExtra("icon", serie.icon)
        })
    }

    // ── Modelos internos ────────────────────────────────────────────────────
    data class KidsRecentItem(
        val id: String, val nome: String, val capa: String, val tipo: String,
        val filmeObj: VodStream?, val serieObj: SeriesStream?
    )

    // ── Adapter Hub (canais ao vivo kids) ───────────────────────────────────
    inner class HubAdapter(val list: List<LiveStream>, val onClick: (LiveStream) -> Unit) :
        RecyclerView.Adapter<HubAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView       = v.findViewById(R.id.imgLogoHub)
            val txt: TextView        = v.findViewById(R.id.tvNameHub)
            val container: LinearLayout = v.findViewById(R.id.containerHub)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_hub_kids, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.txt.text = item.name.uppercase()

            // ✅ HD: logo do canal em qualidade máxima
            Glide.with(holder.itemView.context)
                .load(item.icon)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .fitCenter()
                .into(holder.img)

            val nomeUpper = item.name.uppercase()
            holder.container.setBackgroundColor(Color.parseColor(when {
                nomeUpper.contains("CARTOON")   -> "#000000"
                nomeUpper.contains("DISCOVERY") -> "#00AEEF"
                nomeUpper.contains("NICK")      -> "#FF6600"
                nomeUpper.contains("GLOOB")     -> "#E30613"
                nomeUpper.contains("DISNEY")    -> "#FF007F"
                else                            -> "#4A148C"
            }))

            configurarFoco(holder.itemView)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }

    // ── Adapter Recentes unificado ──────────────────────────────────────────
    inner class KidsUnifiedAdapter(val list: List<KidsRecentItem>, val onClick: (KidsRecentItem) -> Unit) :
        RecyclerView.Adapter<KidsUnifiedAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgPoster)
            val txt: TextView  = v.findViewById(R.id.tvName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome
            // ✅ HD: poster dos recentes em qualidade máxima
            Glide.with(holder.itemView.context)
                .load(item.capa)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(240, 360)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .centerCrop()
                .into(holder.img)
            configurarFoco(holder.itemView)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }

    // ── Adapter Filmes kids ─────────────────────────────────────────────────
    inner class KidsVodAdapter(val list: List<VodStream>, val onClick: (VodStream) -> Unit) :
        RecyclerView.Adapter<KidsVodAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgPoster)
            val txt: TextView  = v.findViewById(R.id.tvName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.txt.text = item.name
            // ✅ HD: poster dos filmes kids em qualidade máxima
            Glide.with(holder.itemView.context)
                .load(item.icon)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(240, 360)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .centerCrop()
                .into(holder.img)
            configurarFoco(holder.itemView)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }

    // ── Adapter Séries kids ─────────────────────────────────────────────────
    inner class KidsSeriesAdapter(val list: List<SeriesStream>, val onClick: (SeriesStream) -> Unit) :
        RecyclerView.Adapter<KidsSeriesAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgPoster)
            val txt: TextView  = v.findViewById(R.id.tvName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_vod, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.txt.text = item.name
            // ✅ HD: poster das séries kids em qualidade máxima
            Glide.with(holder.itemView.context)
                .load(item.icon)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(240, 360)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .centerCrop()
                .into(holder.img)
            configurarFoco(holder.itemView)
            holder.itemView.setOnClickListener { onClick(item) }
        }

        override fun getItemCount() = list.size
    }
}
