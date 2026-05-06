package com.vltv.play

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.vltv.play.data.AppDatabase
import com.vltv.play.data.SeriesEntity
import com.vltv.play.data.VodEntity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

class NovidadesAdapter(
    private var lista: List<NovidadeItem>,
    private val currentProfile: String,
    private val database: AppDatabase
) : RecyclerView.Adapter<NovidadesAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val imgFundo: ImageView     = view.findViewById(R.id.imgFundoNovidade)
        val imgLogo: ImageView      = view.findViewById(R.id.imgLogoNovidade)
        val tvTitulo: TextView      = view.findViewById(R.id.tvTituloNovidade)
        val tvTagline: TextView     = view.findViewById(R.id.tvTagline)
        val tvSinopse: TextView     = view.findViewById(R.id.tvSinopseNovidade)
        val tvMensagem: TextView?   = try { view.findViewById(R.id.tvMensagemDisponibilidade) } catch (e: Exception) { null }
        val containerBotoes: LinearLayout = view.findViewById(R.id.containerBotoesAtivos)
        val btnAssistir: LinearLayout     = view.findViewById(R.id.btnAssistirNovidade)
        val btnDetalhes: LinearLayout     = view.findViewById(R.id.btnMinhaListaNovidade)
        var job: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_novidade, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = lista[position]
        val context = holder.itemView.context
        val logoPrefs = context.getSharedPreferences("vltv_logos_cache", Context.MODE_PRIVATE)

        // Cancela coroutine anterior do mesmo holder
        holder.job?.cancel()

        // ── Textos básicos ──────────────────────────────────────────────────
        holder.tvTitulo.text  = item.titulo
        holder.tvSinopse.text = item.sinopse
        holder.tvSinopse.visibility = View.VISIBLE
        holder.tvTagline.text = if (item.isTop10) "🏆 Top ${item.posicaoTop10}" else item.tagline

        // ── Imagem de fundo HD ──────────────────────────────────────────────
        Glide.with(context)
            .load(item.imagemFundoUrl)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .override(780, 440)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .transition(DrawableTransitionOptions.withCrossFade(300))
            .centerCrop()
            .into(holder.imgFundo)

        // ── Logo do título (cache → TMDB) ───────────────────────────────────
        val cachedLogo = logoPrefs.getString("novidade_logo_${item.idTMDB}", null)
        if (cachedLogo != null) {
            holder.tvTitulo.visibility = View.GONE
            holder.imgLogo.visibility  = View.VISIBLE
            Glide.with(context)
                .load(cachedLogo)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(holder.imgLogo)
        } else {
            holder.tvTitulo.visibility = View.VISIBLE
            holder.imgLogo.visibility  = View.GONE
        }

        // ── Estado inicial dos botões (reseta para evitar reciclagem suja) ──
        holder.btnAssistir.visibility = View.GONE
        holder.tvMensagem?.visibility = View.GONE
        holder.containerBotoes.visibility = View.VISIBLE

        // ── Lógica de disponibilidade ───────────────────────────────────────
        holder.job = CoroutineScope(Dispatchers.IO).launch {

            // Busca logo em paralelo se não estava em cache
            if (cachedLogo == null) {
                val logoUrl = buscarLogoTMDB(item.idTMDB, item.isSerie, logoPrefs)
                if (logoUrl != null) {
                    withContext(Dispatchers.Main) {
                        if (holder.adapterPosition == position) {
                            holder.tvTitulo.visibility = View.GONE
                            holder.imgLogo.visibility  = View.VISIBLE
                            Glide.with(context)
                                .load(logoUrl)
                                .format(DecodeFormat.PREFER_ARGB_8888)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .transition(DrawableTransitionOptions.withCrossFade(200))
                                .into(holder.imgLogo)
                        }
                    }
                }
            }

            // Em Breve → nunca mostra Assistir
            if (item.isEmBreve) {
                withContext(Dispatchers.Main) {
                    holder.btnAssistir.visibility = View.GONE
                    holder.tvMensagem?.text = "🗓 Disponível no aplicativo após o lançamento"
                    holder.tvMensagem?.visibility = View.VISIBLE
                    configurarBotaoDetalhes(holder, item, context, null, null)
                }
                return@launch
            }

            // Bombando / Top 10 → busca no banco local
            val nomeParaBusca = item.titulo.lowercase().trim()

            val serieLocal: SeriesEntity? = if (item.isSerie) {
                database.streamDao().getAllSeries().find { serie ->
                    val nomeLocal = limparNome(serie.name)
                    nomeLocal.contains(nomeParaBusca) || nomeParaBusca.contains(nomeLocal)
                }
            } else null

            val filmeLocal: VodEntity? = if (!item.isSerie) {
                database.streamDao().getAllVods().find { vod ->
                    val nomeLocal = limparNome(vod.name)
                    nomeLocal.contains(nomeParaBusca) || nomeParaBusca.contains(nomeLocal)
                }
            } else null

            withContext(Dispatchers.Main) {
                if (holder.adapterPosition != position) return@withContext

                if (serieLocal != null || filmeLocal != null) {
                    // ✅ Disponível no servidor → mostra ASSISTIR + DETALHES
                    holder.btnAssistir.visibility = View.VISIBLE
                    holder.tvMensagem?.visibility = View.GONE

                    holder.btnAssistir.setOnClickListener {
                        val intent = if (item.isSerie && serieLocal != null) {
                            Intent(context, SeriesDetailsActivity::class.java).apply {
                                putExtra("series_id", serieLocal.series_id)
                                putExtra("name", serieLocal.name)
                                putExtra("icon", serieLocal.cover)
                                putExtra("rating", serieLocal.rating ?: "0.0")
                                putExtra("PROFILE_NAME", currentProfile)
                            }
                        } else if (filmeLocal != null) {
                            Intent(context, DetailsActivity::class.java).apply {
                                putExtra("stream_id", filmeLocal.stream_id)
                                putExtra("name", filmeLocal.name)
                                putExtra("icon", filmeLocal.stream_icon)
                                putExtra("poster", filmeLocal.stream_icon)
                                putExtra("rating", filmeLocal.rating ?: "0.0")
                                putExtra("container_extension", filmeLocal.container_extension)
                                putExtra("PROFILE_NAME", currentProfile)
                            }
                        } else null
                        intent?.let { context.startActivity(it) }
                    }

                    configurarBotaoDetalhes(holder, item, context, serieLocal, filmeLocal)

                } else {
                    // ❌ Não está no servidor → só DETALHES + mensagem
                    holder.btnAssistir.visibility = View.GONE
                    holder.tvMensagem?.text = "Em breve disponível no aplicativo"
                    holder.tvMensagem?.visibility = View.VISIBLE

                    configurarBotaoDetalhes(holder, item, context, null, null)
                }
            }
        }
    }

    // ── Botão DETALHES → abre tela de detalhes TMDB ─────────────────────────
    private fun configurarBotaoDetalhes(
        holder: VH,
        item: NovidadeItem,
        context: Context,
        serieLocal: SeriesEntity?,
        filmeLocal: VodEntity?
    ) {
        holder.btnDetalhes.setOnClickListener {
            when {
                // Se tem no servidor → vai para a tela de detalhes real
                item.isSerie && serieLocal != null -> {
                    context.startActivity(Intent(context, SeriesDetailsActivity::class.java).apply {
                        putExtra("series_id", serieLocal.series_id)
                        putExtra("name", serieLocal.name)
                        putExtra("icon", serieLocal.cover)
                        putExtra("rating", serieLocal.rating ?: "0.0")
                        putExtra("PROFILE_NAME", currentProfile)
                    })
                }
                !item.isSerie && filmeLocal != null -> {
                    context.startActivity(Intent(context, DetailsActivity::class.java).apply {
                        putExtra("stream_id", filmeLocal.stream_id)
                        putExtra("name", filmeLocal.name)
                        putExtra("icon", filmeLocal.stream_icon)
                        putExtra("rating", filmeLocal.rating ?: "0.0")
                        putExtra("container_extension", filmeLocal.container_extension)
                        putExtra("PROFILE_NAME", currentProfile)
                    })
                }
                // Se NÃO tem no servidor → abre TmdbDetailsActivity com dados do TMDB
                else -> {
                    context.startActivity(Intent(context, TmdbDetailsActivity::class.java).apply {
                        putExtra("tmdb_id", item.idTMDB)
                        putExtra("titulo", item.titulo)
                        putExtra("sinopse", item.sinopse)
                        putExtra("imagem_url", item.imagemFundoUrl)
                        putExtra("is_serie", item.isSerie)
                        putExtra("is_em_breve", item.isEmBreve)
                        putExtra("tagline", item.tagline)
                        putExtra("PROFILE_NAME", currentProfile)
                    })
                }
            }
        }
    }

    // ── Remove qualificadores do nome para comparação ────────────────────────
    private fun limparNome(nome: String): String {
        var n = nome.lowercase()
        listOf("fhd", "hd", "sd", "4k", "8k", "h265", "leg", "dublado", "dub",
               "nacional", "legendado", "|", "-", "_", ".", "(", ")")
            .forEach { n = n.replace(it, " ") }
        return n.trim().replace(Regex("\\s+"), " ")
    }

    // ── Busca logo no TMDB e salva em cache ──────────────────────────────────
    private suspend fun buscarLogoTMDB(
        tmdbId: Int,
        isSerie: Boolean,
        prefs: SharedPreferences
    ): String? {
        val tipo = if (isSerie) "tv" else "movie"
        return try {
            val url = "https://api.themoviedb.org/3/$tipo/$tmdbId/images" +
                      "?api_key=9b73f5dd15b8165b1b57419be2f29128&include_image_language=pt,en,null"
            val resp = URL(url).readText()
            val logos = JSONObject(resp).optJSONArray("logos") ?: return null
            if (logos.length() == 0) return null

            // Prioridade: PT > EN > qualquer
            var path: String? = null
            for (i in 0 until logos.length()) {
                val logo = logos.getJSONObject(i)
                val lang = logo.optString("iso_639_1", "")
                if (lang == "pt") { path = logo.optString("file_path"); break }
            }
            if (path == null) {
                for (i in 0 until logos.length()) {
                    val logo = logos.getJSONObject(i)
                    if (logo.optString("iso_639_1") == "en") { path = logo.optString("file_path"); break }
                }
            }
            if (path == null) path = logos.getJSONObject(0).optString("file_path")

            val finalUrl = "https://image.tmdb.org/t/p/w500$path"
            prefs.edit().putString("novidade_logo_$tmdbId", finalUrl).apply()
            finalUrl
        } catch (e: Exception) { null }
    }

    override fun getItemCount() = lista.size

    fun atualizarLista(novaLista: List<NovidadeItem>) {
        lista = novaLista.toList()
        notifyDataSetChanged()
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        holder.job?.cancel()
    }
}
