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
        "http://cmdbr.life",
        "http://blackdeluxe.shop"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
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
        val savedDns = prefs.getString("dns", null)

        if (!savedUser.isNullOrBlank() && !savedPass.isNullOrBlank() && !savedDns.isNullOrBlank()) {
            // âœ… AUTO-LOGIN: vai imediatamente, sem esperar carregamento
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
        binding.etUsername.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus()
                true
            } else false
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                binding.btnLogin.performClick()
                true
            } else false
        }

        binding.btnLogin.setOnClickListener {
            val user = binding.etUsername.text.toString().trim()
            val pass = binding.etPassword.text.toString().trim()

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Preencha usuÃ¡rio e senha!", Toast.LENGTH_SHORT).show()
            } else {
                iniciarLoginTurbo(user, pass)
            }
        }

        binding.etUsername.requestFocus()
    }

    // âœ… AUTO-LOGIN RÃPIDO: navega imediatamente se jÃ¡ tem dados no banco.
    // O conteÃºdo novo do servidor Ã© sincronizado pelo HomeActivity em background.
    private fun verificarEIniciarRapido(dns: String, user: String, pass: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val temConteudo = db.streamDao().getVodCount() > 0

            withContext(Dispatchers.Main) {
                if (temConteudo) {
                    // âœ… TEM CONTEÃšDO: vai direto sem mostrar loading nem esperar API
                    decidirProximaTela()
                } else {
                    // Primeiro uso: precisa carregar pelo menos um lote inicial
                    binding = ActivityLoginBinding.inflate(layoutInflater)
                    setContentView(binding.root)
                    aplicarModoImersivo()
                    binding.progressBar.visibility = View.VISIBLE
                    Toast.makeText(
                        this@LoginActivity,
                        "Primeiro acesso, carregando...",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Carrega um lote mÃ­nimo e navega logo apÃ³s
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
            try {
                val deferreds = SERVERS.map { url ->
                    async { testarConexaoIndividual(url, user, pass) }
                }

                var dnsVencedor: String? = null
                val startTime = System.currentTimeMillis()

                while (System.currentTimeMillis() - startTime < 10000) {
                    val completed = deferreds.filter { it.isCompleted }
                    for (job in completed) {
                        val result = job.getCompleted()
                        if (result != null) {
                            dnsVencedor = result
                            break
                        }
                    }
                    if (dnsVencedor != null) break
                    delay(100)
                }

                deferreds.forEach { if (it.isActive) it.cancel() }

                if (dnsVencedor != null) {
                    // âœ… SEPARAÃ‡ÃƒO DE USUÃRIOS: Detecta se o usuÃ¡rio mudou e limpa o banco
                    val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                    val usuarioAnterior = prefs.getString("username", null)

                    if (usuarioAnterior != null && usuarioAnterior != user) {
                        // UsuÃ¡rio diferente: limpa todo o conteÃºdo do banco
                        limparBancoPorTrocaDeUsuario()
                    }

                    salvarCredenciais(dnsVencedor, user, pass)

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginActivity, "Conectando Ã  VLTV+", Toast.LENGTH_LONG).show()
                    }

                    // âœ… RÃPIDO: carrega sÃ³ um lote mÃ­nimo e navega logo
                    preCarregarLoteMinimo(dnsVencedor, user, pass)
                    withContext(Dispatchers.Main) { decidirProximaTela() }

                } else {
                    withContext(Dispatchers.Main) {
                        mostrarErro("Nenhum servidor respondeu. Verifique dados.")
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mostrarErro("Erro: ${e.message}")
                }
            }
        }
    }

    // âœ… LOTE MÃNIMO: carrega apenas 20 itens de cada para abrir a tela rÃ¡pido.
    // O restante Ã© sincronizado silenciosamente pelo HomeActivity.
    private suspend fun preCarregarLoteMinimo(dns: String, user: String, pass: String) {
        try {
            val db = AppDatabase.getDatabase(this)
            val base = normalizarBaseUrl(dns)

            // FILMES â€” apenas 20
            try {
                val vodUrl = "${base}player_api.php?username=$user&password=$pass&action=get_vod_streams"
                val response = URL(vodUrl).readText()
                val jsonArray = JSONArray(response)
                val batch = mutableListOf<VodEntity>()
                val limit = minOf(20, jsonArray.length())

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

            // SÃ‰RIES â€” apenas 20
            try {
                val seriesUrl = "${base}player_api.php?username=$user&password=$pass&action=get_series"
                val response = URL(seriesUrl).readText()
                val jsonArray = JSONArray(response)
                val batch = mutableListOf<SeriesEntity>()
                val limit = minOf(20, jsonArray.length())

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

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // âœ… SEPARAÃ‡ÃƒO DE USUÃRIOS: limpa filmes, sÃ©ries e canais ao trocar de conta
    private suspend fun limparBancoPorTrocaDeUsuario() {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            withContext(Dispatchers.IO) {
                db.streamDao().clearLive()
                // Limpa VODs e SÃ©ries tambÃ©m via queries diretas
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

    private fun testarConexaoIndividual(baseUrl: String, user: String, pass: String): String? {
        val urlLimpa = normalizarBaseUrl(baseUrl).removeSuffix("/")
        val apiLogin = "$urlLimpa/player_api.php?username=$user&password=$pass"

        return try {
            val request = Request.Builder().url(apiLogin).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.contains("user_info") && body.contains("server_info")) {
                        return urlLimpa
                    }
                }
                null
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
