package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.Priority
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.nio.charset.Charset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONObject

class LiveTvActivity : AppCompatActivity() {

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvChannels: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCategoryTitle: TextView

    private var username = ""
    private var password = ""

    private var cachedCategories: List<LiveCategory>? = null
    private val channelsCache = mutableMapOf<String, List<LiveStream>>()

    private var categoryAdapter: CategoryAdapter? = null
    private var channelAdapter: ChannelAdapter? = null

    // ✅ Detecta se é TV para ajustar zoom e colunas
    private fun isTvDevice(): Boolean {
        return packageManager.hasSystemFeature("android.software.leanback") ||
               packageManager.hasSystemFeature("android.hardware.type.television") ||
               (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
               android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_tv)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        rvCategories = findViewById(R.id.rvCategories)
        rvChannels = findViewById(R.id.rvChannels)
        progressBar = findViewById(R.id.progressBar)
        tvCategoryTitle = findViewById(R.id.tvCategoryTitle)

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        username = prefs.getString("username", "") ?: ""
        password = prefs.getString("password", "") ?: ""

        setupRecyclerFocus()

        rvCategories.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
        rvCategories.setHasFixedSize(true)
        rvCategories.setItemViewCacheSize(50)
        rvCategories.overScrollMode = View.OVER_SCROLL_NEVER
        rvCategories.isFocusable = true
        rvCategories.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

        // ✅ Colunas adaptadas: 5 na TV, 4 no celular
        val colunas = if (isTvDevice()) 5 else 4
        rvChannels.layoutManager = GridLayoutManager(this, colunas)
        rvChannels.isFocusable = true
        rvChannels.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        rvChannels.setHasFixedSize(true)
        rvChannels.setItemViewCacheSize(100)

        rvCategories.requestFocus()
        carregarCategorias()
    }

    private fun preLoadChannelLogos(canais: List<LiveStream>) {
        CoroutineScope(Dispatchers.IO).launch {
            val limit = minOf(canais.size, 40)
            for (i in 0 until limit) {
                val url = canais[i].icon
                if (!url.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Glide.with(this@LiveTvActivity)
                            .load(url)
                            .format(DecodeFormat.PREFER_ARGB_8888) // ✅ HD
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .priority(Priority.LOW)
                            .preload()
                    }
                }
            }
        }
    }

    private fun setupRecyclerFocus() {
        rvCategories.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) rvCategories.smoothScrollToPosition(0)
        }
        rvChannels.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) rvChannels.smoothScrollToPosition(0)
        }
    }

    private fun isAdultName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.lowercase()
        return n.contains("+18") || n.contains("adult") || n.contains("xxx") ||
               n.contains("hot") || n.contains("sexo")
    }

    private fun carregarCategorias() {
        cachedCategories?.let { aplicarCategorias(it); return }

        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getLiveCategories(username, password)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        try {
                            val rawJson = response.body()!!.string()
                            val lista = mutableListOf<LiveCategory>()
                            val gson = Gson()

                            if (rawJson.trim().startsWith("[")) {
                                val listType = object : TypeToken<List<LiveCategory>>() {}.type
                                lista.addAll(gson.fromJson(rawJson, listType))
                            } else if (rawJson.trim().startsWith("{")) {
                                val obj = JSONObject(rawJson)
                                val keys = obj.keys()
                                while (keys.hasNext()) {
                                    lista.add(gson.fromJson(obj.getJSONObject(keys.next()).toString(), LiveCategory::class.java))
                                }
                            }

                            cachedCategories = lista
                            val filtradas = if (ParentalControlManager.isEnabled(this@LiveTvActivity))
                                lista.filterNot { isAdultName(it.name) } else lista

                            aplicarCategorias(filtradas)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@LiveTvActivity, "Erro no formato dos dados", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@LiveTvActivity, "Erro ao carregar categorias", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun aplicarCategorias(categorias: List<LiveCategory>) {
        if (categorias.isEmpty()) {
            Toast.makeText(this, "Nenhuma categoria disponível.", Toast.LENGTH_SHORT).show()
            rvCategories.adapter = CategoryAdapter(emptyList()) {}
            rvChannels.adapter = ChannelAdapter(emptyList(), username, password) {}
            return
        }
        categoryAdapter = CategoryAdapter(categorias) { carregarCanais(it) }
        rvCategories.adapter = categoryAdapter
        carregarCanais(categorias[0])
    }

    private fun carregarCanais(categoria: LiveCategory) {
        tvCategoryTitle.text = categoria.name
        val catIdStr = categoria.id.toString()

        channelsCache[catIdStr]?.let {
            aplicarCanais(categoria, it)
            preLoadChannelLogos(it)
            return
        }

        progressBar.visibility = View.VISIBLE

        XtreamApi.service.getLiveStreams(username, password, categoryId = catIdStr)
            .enqueue(object : Callback<List<LiveStream>> {
                override fun onResponse(call: Call<List<LiveStream>>, response: Response<List<LiveStream>>) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        var canais = response.body()!!
                        channelsCache[catIdStr] = canais
                        if (ParentalControlManager.isEnabled(this@LiveTvActivity)) {
                            canais = canais.filterNot { isAdultName(it.name) }
                        }
                        aplicarCanais(categoria, canais)
                        preLoadChannelLogos(canais)
                    } else {
                        Toast.makeText(this@LiveTvActivity, "Erro ao carregar canais", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<List<LiveStream>>, t: Throwable) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LiveTvActivity, "Falha de conexão", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun aplicarCanais(categoria: LiveCategory, canais: List<LiveStream>) {
        tvCategoryTitle.text = categoria.name
        channelAdapter = ChannelAdapter(canais, username, password) { canal ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putExtra("stream_id", canal.id)
            intent.putExtra("stream_ext", "ts")
            intent.putExtra("stream_type", "live")
            intent.putExtra("channel_name", canal.name)
            startActivity(intent)
        }
        rvChannels.adapter = channelAdapter
    }

    // ─── ADAPTER CATEGORIAS ──────────────────────────────────────────────────
    inner class CategoryAdapter(
        private val list: List<LiveCategory>,
        private val onClick: (LiveCategory) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        private var selectedPos = 0

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val viewIndicator: View? = try { v.findViewById(R.id.viewIndicator) } catch (e: Exception) { null }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name
            atualizarEstilo(holder, position == selectedPos, false)

            holder.itemView.isFocusable = true
            holder.itemView.isClickable = true
            // ✅ FIX DUPLO CLIQUE: false no celular, true só na TV
            holder.itemView.isFocusableInTouchMode = isTvDevice()

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                atualizarEstilo(holder, selectedPos == position, hasFocus)
            }

            holder.itemView.setOnClickListener {
                notifyItemChanged(selectedPos)
                selectedPos = holder.adapterPosition
                notifyItemChanged(selectedPos)
                onClick(item)
            }
        }

        private fun atualizarEstilo(holder: VH, isSelected: Boolean, hasFocus: Boolean) {
            when {
                hasFocus -> {
                    holder.tvName.setTextColor(Color.YELLOW)
                    holder.tvName.textSize = 13f
                    holder.itemView.setBackgroundResource(R.drawable.bg_focus_neon)
                    holder.itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(150).start()
                    holder.viewIndicator?.visibility = View.VISIBLE
                }
                isSelected -> {
                    holder.tvName.setTextColor(Color.WHITE)
                    holder.tvName.textSize = 12f
                    holder.itemView.setBackgroundColor(0x22FFFFFF)
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    holder.viewIndicator?.visibility = View.VISIBLE
                }
                else -> {
                    holder.tvName.setTextColor(0x88FFFFFF.toInt())
                    holder.tvName.textSize = 12f
                    holder.itemView.setBackgroundColor(0x00000000)
                    holder.itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    holder.viewIndicator?.visibility = View.INVISIBLE
                }
            }
        }

        override fun getItemCount() = list.size
    }

    // ─── ADAPTER CANAIS ──────────────────────────────────────────────────────
    inner class ChannelAdapter(
        private val list: List<LiveStream>,
        private val username: String,
        private val password: String,
        private val onClick: (LiveStream) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        private val epgCache = mutableMapOf<Int, List<EpgResponseItem>>()

        // ✅ Zoom adaptado: menor no celular, maior na TV
        private val zoomFocus = if (isTvDevice()) 1.12f else 1.04f

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvNow: TextView = v.findViewById(R.id.tvNow)
            val tvNext: TextView = v.findViewById(R.id.tvNext)
            val imgLogo: ImageView = v.findViewById(R.id.imgLogo)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvName.text = item.name

            // ✅ HD: logo em qualidade máxima com fade suave
            Glide.with(holder.itemView.context)
                .load(item.icon)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .priority(Priority.HIGH)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .placeholder(R.drawable.bg_logo_placeholder)
                .error(R.drawable.bg_logo_placeholder)
                .fitCenter()
                .into(holder.imgLogo)

            carregarEpg(holder, item)

            holder.itemView.isFocusable = true
            holder.itemView.isClickable = true
            // ✅ FIX DUPLO CLIQUE: desativa focusableInTouchMode no celular
            holder.itemView.isFocusableInTouchMode = isTvDevice()

            holder.itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    holder.tvName.setTextColor(Color.YELLOW)
                    view.setBackgroundResource(R.drawable.bg_focus_neon)
                    view.animate().scaleX(zoomFocus).scaleY(zoomFocus).setDuration(160).start()
                    view.elevation = 16f
                } else {
                    holder.tvName.setTextColor(Color.WHITE)
                    view.setBackgroundResource(0)
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).start()
                    view.elevation = 4f
                }
            }

            holder.itemView.setOnClickListener { onClick(item) }
        }

        private fun decodeBase64(text: String?): String {
            return try {
                if (text.isNullOrEmpty()) ""
                else String(Base64.decode(text, Base64.DEFAULT), Charset.forName("UTF-8"))
            } catch (e: Exception) { text ?: "" }
        }

        private fun carregarEpg(holder: VH, canal: LiveStream) {
            epgCache[canal.id]?.let { mostrarEpg(holder, it); return }

            XtreamApi.service.getShortEpg(
                user = username,
                pass = password,
                streamId = canal.id.toString(),
                limit = 2
            ).enqueue(object : Callback<EpgWrapper> {
                override fun onResponse(call: Call<EpgWrapper>, response: Response<EpgWrapper>) {
                    if (response.isSuccessful && response.body()?.epg_listings != null) {
                        val epg = response.body()!!.epg_listings!!
                        epgCache[canal.id] = epg
                        mostrarEpg(holder, epg)
                    } else {
                        holder.tvNow.text = ""
                        holder.tvNext.text = ""
                    }
                }
                override fun onFailure(call: Call<EpgWrapper>, t: Throwable) {
                    holder.tvNow.text = ""
                    holder.tvNext.text = ""
                }
            })
        }

        private fun mostrarEpg(holder: VH, epg: List<EpgResponseItem>) {
            if (epg.isNotEmpty()) {
                holder.tvNow.text = decodeBase64(epg[0].title)
                holder.tvNext.text = if (epg.size > 1) decodeBase64(epg[1].title) else ""
            } else {
                holder.tvNow.text = ""
                holder.tvNext.text = ""
            }
        }

        override fun getItemCount() = list.size
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
