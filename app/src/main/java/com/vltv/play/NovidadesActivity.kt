package com.vltv.play

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.vltv.play.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovidadesActivity : AppCompatActivity() {

    private lateinit var tabEmBreve: TextView
    private lateinit var tabTodoMundo: TextView
    private lateinit var tabTopSeries: TextView
    private lateinit var tabTopFilmes: TextView
    private lateinit var recyclerNovidades: RecyclerView
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var adapter: NovidadesAdapter

    private val listaEmBreve    = mutableListOf<NovidadeItem>()
    private val listaTodoMundo  = mutableListOf<NovidadeItem>()
    private val listaTopSeries  = mutableListOf<NovidadeItem>()
    private val listaTopFilmes  = mutableListOf<NovidadeItem>()

    private val apiKey   = "9b73f5dd15b8165b1b57419be2f29128"
    private val client   = OkHttpClient()
    private var currentProfile = "Padrao"
    private val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_novidades)

        currentProfile = intent.getStringExtra("PROFILE_NAME") ?: "Padrao"

        tabEmBreve       = findViewById(R.id.tabEmBreve)
        tabTodoMundo     = findViewById(R.id.tabBombando)
        tabTopSeries     = findViewById(R.id.tabTopSeries)
        tabTopFilmes     = findViewById(R.id.tabTopFilmes)
        recyclerNovidades = findViewById(R.id.recyclerNovidades)
        bottomNavigation  = findViewById(R.id.bottomNavigation)

        adapter = NovidadesAdapter(emptyList(), currentProfile, database)
        recyclerNovidades.layoutManager = LinearLayoutManager(this)
        recyclerNovidades.adapter = adapter

        configurarAbas()
        configurarRodape()
        carregarTudo()
    }

    private fun configurarRodape() {
        bottomNavigation.selectedItemId = R.id.nav_novidades
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { finish(); true }
                R.id.nav_search -> {
                    startActivity(Intent(this, SearchActivity::class.java).apply {
                        putExtra("PROFILE_NAME", currentProfile)
                    })
                    finish(); true
                }
                R.id.nav_novidades -> true
                R.id.nav_profile -> {
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        putExtra("PROFILE_NAME", currentProfile)
                    })
                    finish(); true
                }
                else -> false
            }
        }
    }

    private fun configurarAbas() {
        // Ativa "Em Breve" por padrão
        ativarAba(tabEmBreve)

        tabEmBreve.setOnClickListener {
            ativarAba(tabEmBreve)
            adapter.atualizarLista(listaEmBreve)
            recyclerNovidades.scrollToPosition(0)
        }
        tabTodoMundo.setOnClickListener {
            ativarAba(tabTodoMundo)
            adapter.atualizarLista(listaTodoMundo)
            recyclerNovidades.scrollToPosition(0)
        }
        tabTopSeries.setOnClickListener {
            ativarAba(tabTopSeries)
            adapter.atualizarLista(listaTopSeries)
            recyclerNovidades.scrollToPosition(0)
        }
        tabTopFilmes.setOnClickListener {
            ativarAba(tabTopFilmes)
            adapter.atualizarLista(listaTopFilmes)
            recyclerNovidades.scrollToPosition(0)
        }
    }

    private fun ativarAba(aba: TextView) {
        listOf(tabEmBreve, tabTodoMundo, tabTopSeries, tabTopFilmes).forEach {
            if (it == aba) {
                it.setBackgroundResource(R.drawable.bg_aba_selecionada)
                it.setTextColor(Color.BLACK)
            } else {
                it.setBackgroundResource(R.drawable.bg_aba_inativa)
                it.setTextColor(Color.WHITE)
            }
        }
    }

    private fun carregarTudo() {
        val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val daqui3meses = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000))

        // 1. Em Breve — filmes que vão estrear (TMDB puro, sem filtro de banco)
        buscarTMDB(
            url = "https://api.themoviedb.org/3/discover/movie?api_key=$apiKey&language=pt-BR&region=BR" +
                  "&with_release_type=2|3&primary_release_date.gte=$hoje" +
                  "&primary_release_date.lte=$daqui3meses&sort_by=primary_release_date.asc",
            destino       = listaEmBreve,
            isTop10       = false,
            isEmBreve     = true,
            isSerie       = false,
            tagFixa       = "Estreia em Breve",
            usarPoster    = true,
            limite        = 20
        ) {
            runOnUiThread { adapter.atualizarLista(listaEmBreve) }
        }

        // 2. Bombando — trending da semana (filmes + séries)
        buscarTMDB(
            url = "https://api.themoviedb.org/3/trending/all/week?api_key=$apiKey&language=pt-BR",
            destino       = listaTodoMundo,
            isTop10       = false,
            isEmBreve     = false,
            isSerie       = false, // será detectado pelo campo "media_type"
            tagFixa       = "Bombando no Mundo",
            usarPoster    = false,
            limite        = 20,
            detectarTipo  = true
        ) {}

        // 3. Top 10 Séries
        buscarTMDB(
            url = "https://api.themoviedb.org/3/tv/popular?api_key=$apiKey&language=pt-BR&page=1",
            destino       = listaTopSeries,
            isTop10       = true,
            isEmBreve     = false,
            isSerie       = true,
            tagFixa       = "Top 10 Séries",
            usarPoster    = false,
            limite        = 10
        ) {}

        // 4. Top 10 Filmes
        buscarTMDB(
            url = "https://api.themoviedb.org/3/movie/popular?api_key=$apiKey&language=pt-BR&page=1",
            destino       = listaTopFilmes,
            isTop10       = true,
            isEmBreve     = false,
            isSerie       = false,
            tagFixa       = "Top 10 Filmes",
            usarPoster    = false,
            limite        = 10
        ) {}
    }

    private fun buscarTMDB(
        url: String,
        destino: MutableList<NovidadeItem>,
        isTop10: Boolean,
        isEmBreve: Boolean,
        isSerie: Boolean,
        tagFixa: String,
        usarPoster: Boolean,
        limite: Int,
        detectarTipo: Boolean = false,
        onSucesso: () -> Unit = {}
    ) {
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                try {
                    val results = JSONObject(body).optJSONArray("results") ?: return
                    val temp = mutableListOf<NovidadeItem>()
                    var posicao = 1

                    for (i in 0 until results.length()) {
                        if (temp.size >= limite) break
                        val obj = results.getJSONObject(i)

                        // Detecta tipo automaticamente para Trending All
                        val tipoDetectado = if (detectarTipo)
                            obj.optString("media_type") == "tv"
                        else isSerie

                        val titulo = obj.optString("title", obj.optString("name", ""))
                        if (titulo.isEmpty()) continue

                        val pathImagem = if (usarPoster)
                            obj.optString("poster_path", "")
                        else
                            obj.optString("backdrop_path", obj.optString("poster_path", ""))
                        if (pathImagem.isEmpty()) continue

                        val sinopse = obj.optString("overview", "Descrição indisponível.")
                        val releaseDate = obj.optString("release_date", obj.optString("first_air_date", ""))
                        val tagFinal = if (isEmBreve && releaseDate.isNotEmpty())
                            formatarData(releaseDate) else tagFixa

                        // ✅ TMDB puro — disponibilidade verificada no adapter em paralelo
                        temp.add(NovidadeItem(
                            idTMDB        = obj.optInt("id"),
                            stream_id     = 0,
                            series_id     = 0,
                            titulo        = titulo,
                            sinopse       = sinopse,
                            imagemFundoUrl = "https://image.tmdb.org/t/p/w780$pathImagem",
                            tagline       = tagFinal,
                            isSerie       = tipoDetectado,
                            isEmBreve     = isEmBreve,
                            isTop10       = isTop10,
                            posicaoTop10  = posicao++
                        ))
                    }

                    withContext(Dispatchers.Main) {
                        destino.clear()
                        destino.addAll(temp)
                        onSucesso()
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        })
    }

    // Precisa ser suspend para usar withContext dentro do enqueue callback
    private suspend fun <T> withContext(
        context: kotlin.coroutines.CoroutineContext,
        block: suspend () -> T
    ): T = kotlinx.coroutines.withContext(context) { block() }

    private fun formatarData(dataIngles: String): String {
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dataIngles)
            if (date != null)
                SimpleDateFormat("'Estreia' dd 'de' MMM", Locale("pt", "BR")).format(date)
            else "Estreia em breve"
        } catch (e: Exception) { "Estreia em breve" }
    }
}
