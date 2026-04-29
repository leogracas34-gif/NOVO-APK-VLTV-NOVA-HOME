package com.vltv.play

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.vltv.play.data.AppDatabase
import com.vltv.play.data.SeriesEntity
import com.vltv.play.data.VodEntity
import com.vltv.play.databinding.ActivityLoginBinding
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URL
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    private val SERVERS = listOf(
        "http://fibercdn.sbs",
        "http://tvblack.shop",
        "http://blackzz.shop",
        "http://playchannels.shop",
        "http://dnsblackz.brstore.live",
        "http://xppv.shop",
        "http://redeinternadestiny.top",
        "http://blackstartv.shop",
        "http://blackdns.shop",
        "http://ranos.sbs",
        "http://cmdtv.casa",
        "http://cmdtv.pro",
        "http://cmdtv.sbs",
        "http://cmdtv.top",
        "http://tlfp.fun:80",
        "http://telefunplay.xyz:80",
        "http://ouropreto.top",
        "http://cmdbr.life",
        "http://blackdeluxe.shop"
    )

    // ✅ FIX #1: Timeout aumentado — 5s era curto demais para servidores lentos
    // connectTimeout: tempo para estabelecer a conexão TCP
    // readTimeout: tempo para receber a resposta depois de conectado
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)   // ✅ FIX: tenta reconectar automaticamente
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = if (isTv()) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val savedUser = prefs.getString("username", null)
        val savedPass = prefs.getString("password", null)
        val savedDns  = prefs.getString("dns", null)

        if (!savedUser.isNullOrBlank() && !savedPass.isNullOrBlank() && !savedDns.isNullOrBlank()) {
            verificarEIniciarRapido(savedDns, savedUser, savedPass)
        } else {
            binding = ActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
            aplicarModoImersivo()
            setupUI()
        }
    }

    private fun isTv(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)) return true
        return false
    }

    private fun aplicarModoImersivo() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setupUI() {
        // ✅ FIX DUPLO CLIQUE: focusableInTouchMode no botão fazia o 1º toque
        // apenas ganhar foco e o 2º disparar o clique. Forçamos false aqui por
        // código para garantir, independente do que estiver no XML.
        binding.btnLogin.isFocusableInTouchMode = false
        binding.btnLogin.isFocusable = false

        binding.etUsername.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus()
                true
            } else false
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                binding.btnLogin.callOnClick()
                true
            } else false
        }

        // ✅ Debounce 800ms: evita duplo disparo mesmo que o usuário toque rápido
        var ultimoClique = 0L
        binding.btnLogin.setOnClickListener {
            val agora = System.currentTimeMillis()
            if (agora - ultimoClique < 800L) return@setOnClickListener
            ultimoClique = agora

            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuário e senha!", Toast.LENGTH_SHORT).show()
            } else {
                iniciarLoginTurbo(user, pass)
            }
        }

        binding.etUsername.requestFocus()
    }

    private fun verificarEIniciarRapido(dns: String, user: String, pass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val temConteudo = db.streamDao().getVodCount() > 0

            withContext(Dispatchers.Main) {
                if (temConteudo) {
                    decidirProximaTela()
                } else {
                    binding = ActivityLoginBinding.inflate(layoutInflater)
                    setContentView(binding.root)
                    aplicarModoImersivo()
                    binding.progressBar.visibility = View.VISIBLE
                    Toast.makeText(this@LoginActivity, "Primeiro acesso, carregando...", Toast.LENGTH_SHORT).show()

                    launch(Dispatchers.IO) {
                        preCarregarLoteMinimo(dns, user, pass)
                        withContext(Dispatchers.Main) { decidirProximaTela() }
                    }
                }
            }
        }
    }

    private fun iniciarLoginTurbo(user: String, pass: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false
        binding.etUsername.isEnabled = false
        binding.etPassword.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {

            // ✅ FIX #2: Usar select{} para pegar o PRIMEIRO servidor que responder
            // Antes: loop de polling a cada 100ms com race condition
            // Agora: cada servidor corre em paralelo e o vencedor cancela os demais imediatamente
            var dnsVencedor: String? = null

            try {
                dnsVencedor = withTimeoutOrNull(15_000L) {
                    // Canal de capacidade 1: só o primeiro resultado importa
                    val canal = kotlinx.coroutines.channels.Channel<String>(1)

                    val jobs = SERVERS.map { url ->
                        launch(Dispatchers.IO) {
                            val resultado = testarConexaoIndividual(url, user, pass)
                            if (resultado != null) {
                                canal.trySend(resultado)
                            }
                        }
                    }

                    // Aguarda o primeiro resultado ou timeout
                    val vencedor = canal.receive()
                    jobs.forEach { it.cancel() }  // cancela os demais imediatamente
                    canal.close()
                    vencedor
                }
            } catch (e: Exception) {
                // timeout ou falha geral
            }

            if (dnsVencedor != null) {
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                val usuarioAnterior = prefs.getString("username", null)

                if (usuarioAnterior != null && usuarioAnterior != user) {
                    limparBancoPorTrocaDeUsuario()
                }

                salvarCredenciais(dnsVencedor, user, pass)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Conectando à VLTV+", Toast.LENGTH_SHORT).show()
                }

                // ✅ FIX #3: Pré-carregamento paralelo (filmes + séries ao mesmo tempo)
                // Antes: sequencial — filmes primeiro, depois séries (~2x mais lento)
                // Agora: ambos em paralelo com coroutineScope
                preCarregarLoteMinimo(dnsVencedor, user, pass)
                withContext(Dispatchers.Main) { decidirProximaTela() }

            } else {
                withContext(Dispatchers.Main) {
                    mostrarErro("Nenhum servidor respondeu. Verifique login e senha.")
                }
            }
        }
    }

    // ✅ FIX #4: Pré-carrega filmes E séries em paralelo (coroutineScope = aguarda ambos)
    // Reduzido de 20 para 10 itens cada — suficiente para a tela não aparecer vazia,
    // mas 2x mais rápido. O restante é sincronizado pelo HomeActivity em background.
    private suspend fun preCarregarLoteMinimo(dns: String, user: String, pass: String) {
        try {
            val db = AppDatabase.getDatabase(this)
            val base = normalizarBaseUrl(dns)

            coroutineScope {
                // Filmes e séries baixam ao mesmo tempo
                val jobFilmes = async(Dispatchers.IO) {
                    try {
                        val vodUrl = "${base}player_api.php?username=$user&password=$pass&action=get_vod_streams"
                        val response = URL(vodUrl).readText()
                        val jsonArray = JSONArray(response)
                        val batch = mutableListOf<VodEntity>()
                        val limit = minOf(10, jsonArray.length()) // ✅ 10 itens (era 20)

                        for (i in 0 until limit) {
                            val obj = jsonArray.getJSONObject(i)
                            batch.add(VodEntity(
                                stream_id = obj.optInt("stream_id"),
                                name = obj.optString("name"),
                                title = obj.optString("name"),
                                stream_icon = obj.optString("stream_icon"),
                                container_extension = obj.optString("container_extension"),
                                rating = obj.optString("rating"),
                                category_id = obj.optString("category_id"),
                                added = obj.optLong("added")
                            ))
                        }
                        if (batch.isNotEmpty()) db.streamDao().insertVodStreams(batch)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val jobSeries = async(Dispatchers.IO) {
                    try {
                        val seriesUrl = "${base}player_api.php?username=$user&password=$pass&action=get_series"
                        val response = URL(seriesUrl).readText()
                        val jsonArray = JSONArray(response)
                        val batch = mutableListOf<SeriesEntity>()
                        val limit = minOf(10, jsonArray.length()) // ✅ 10 itens (era 20)

                        for (i in 0 until limit) {
                            val obj = jsonArray.getJSONObject(i)
                            batch.add(SeriesEntity(
                                series_id = obj.optInt("series_id"),
                                name = obj.optString("name"),
                                cover = obj.optString("cover"),
                                rating = obj.optString("rating"),
                                category_id = obj.optString("category_id"),
                                last_modified = obj.optLong("last_modified")
                            ))
                        }
                        if (batch.isNotEmpty()) db.streamDao().insertSeriesStreams(batch)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Aguarda os dois juntos — tempo total = o mais lento dos dois (não a soma)
                jobFilmes.await()
                jobSeries.await()
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun limparBancoPorTrocaDeUsuario() {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            withContext(Dispatchers.IO) {
                db.streamDao().clearLive()
                db.openHelper.writableDatabase.execSQL("DELETE FROM vod_streams")
                db.openHelper.writableDatabase.execSQL("DELETE FROM series_streams")
                db.openHelper.writableDatabase.execSQL("DELETE FROM watch_history")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizarBaseUrl(dns: String): String {
        var url = dns.trim()
        if (url.contains("player_api.php")) {
            url = url.substringBefore("player_api.php")
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url += "/"
        }
        return url
    }

    // ✅ FIX #5: Validação mais robusta — verifica "auth" além de "user_info"
    // Alguns servidores respondem com auth=0 (credenciais erradas) mas body válido
    private fun testarConexaoIndividual(baseUrl: String, user: String, pass: String): String? {
        val urlLimpa = normalizarBaseUrl(baseUrl).removeSuffix("/")
        val apiLogin = "$urlLimpa/player_api.php?username=$user&password=$pass"

        return try {
            val request = Request.Builder()
                .url(apiLogin)
                .header("User-Agent", "Mozilla/5.0")  // ✅ FIX: alguns servidores bloqueiam sem User-Agent
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    // ✅ Verifica se tem user_info E se auth != 0 (credenciais inválidas)
                    val credenciaisValidas = body.contains("user_info") &&
                            body.contains("server_info") &&
                            !body.contains("\"auth\":0") &&
                            !body.contains("\"auth\": 0")
                    if (credenciaisValidas) urlLimpa else null
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun salvarCredenciais(dns: String, user: String, pass: String) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("dns", dns)
            putString("username", user)
            putString("password", pass)
            apply()
        }
        XtreamApi.salvarDns(this, dns)
    }

    private fun decidirProximaTela() {
        if (isTv()) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("PROFILE_NAME", "TV_Box")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        } else {
            val intent = Intent(this, ProfilesActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
        finish()
    }

    private fun mostrarErro(msg: String) {
        binding.progressBar.visibility = View.GONE
        binding.btnLogin.isEnabled = true
        binding.etUsername.isEnabled = true
        binding.etPassword.isEnabled = true
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
