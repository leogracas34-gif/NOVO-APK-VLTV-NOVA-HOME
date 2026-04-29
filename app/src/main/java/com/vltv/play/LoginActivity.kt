package com.vltv.play

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val dotsHandler = Handler(Looper.getMainLooper())
    private var dotsJob: Runnable? = null
    private var dotsCount = 0

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
        binding.btnLogin.isFocusableInTouchMode = false
        binding.btnLogin.isFocusable = false

        binding.etUsername.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                binding.etPassword.requestFocus(); true
            } else false
        }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
                binding.btnLogin.callOnClick(); true
            } else false
        }

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

    // ✅ Animação dos pontinhos "." ".." "..."
    private fun iniciarAnimacaoPontinhos() {
        dotsJob = object : Runnable {
            override fun run() {
                dotsCount = (dotsCount + 1) % 4
                try { binding.tvLoadingDots?.text = ".".repeat(dotsCount) } catch (e: Exception) {}
                dotsHandler.postDelayed(this, 400)
            }
        }
        dotsHandler.post(dotsJob!!)
    }

    private fun pararAnimacaoPontinhos() {
        dotsJob?.let { dotsHandler.removeCallbacks(it) }
        dotsJob = null
    }

    private fun mostrarLoading() {
        binding.btnLogin.isEnabled = false
        binding.etUsername.isEnabled = false
        binding.etPassword.isEnabled = false
        try { binding.layoutLoading?.visibility = View.VISIBLE } catch (e: Exception) {
            // fallback para XML antigo
            binding.progressBar.visibility = View.VISIBLE
        }
        iniciarAnimacaoPontinhos()
    }

    private fun esconderLoading() {
        pararAnimacaoPontinhos()
        try { binding.layoutLoading?.visibility = View.GONE } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
        }
        binding.btnLogin.isEnabled = true
        binding.etUsername.isEnabled = true
        binding.etPassword.isEnabled = true
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
                    mostrarLoading()

                    launch(Dispatchers.IO) {
                        preCarregarLoteMinimo(dns, user, pass)
                        withContext(Dispatchers.Main) { decidirProximaTela() }
                    }
                }
            }
        }
    }

    private fun iniciarLoginTurbo(user: String, pass: String) {
        mostrarLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            var dnsVencedor: String? = null

            // ── Fase 1: todos em paralelo, timeout 20s ────────────────────
            try {
                dnsVencedor = withTimeoutOrNull(20_000L) {
                    val canal = kotlinx.coroutines.channels.Channel<String>(
                        kotlinx.coroutines.channels.Channel.UNLIMITED
                    )
                    val jobs = SERVERS.map { url ->
                        launch(Dispatchers.IO) {
                            val r = testarConexaoIndividual(url, user, pass)
                            if (r != null) canal.trySend(r)
                        }
                    }
                    // Aguarda o primeiro resultado
                    val vencedor = withTimeoutOrNull(19_000L) { canal.receive() }
                    jobs.forEach { it.cancel() }
                    canal.close()
                    vencedor
                }
            } catch (e: Exception) { e.printStackTrace() }

            // ── Fase 2: fallback sequencial nos principais com timeout 25s ─
            if (dnsVencedor == null) {
                val principais = listOf(
                    "http://fibercdn.sbs",
                    "http://cmdtv.top",
                    "http://cmdbr.life",
                    "http://blackdns.shop",
                    "http://ranos.sbs",
                    "http://cmdtv.sbs",
                    "http://tvblack.shop"
                )
                for (servidor in principais) {
                    val r = testarConexaoIndividualLento(servidor, user, pass)
                    if (r != null) { dnsVencedor = r; break }
                }
            }

            if (dnsVencedor != null) {
                val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                val usuarioAnterior = prefs.getString("username", null)
                if (usuarioAnterior != null && usuarioAnterior != user) {
                    limparBancoPorTrocaDeUsuario()
                }
                salvarCredenciais(dnsVencedor, user, pass)
                preCarregarLoteMinimo(dnsVencedor, user, pass)
                withContext(Dispatchers.Main) {
                    pararAnimacaoPontinhos()
                    decidirProximaTela()
                }
            } else {
                withContext(Dispatchers.Main) {
                    esconderLoading()
                    mostrarErro("Servidor não encontrado. Verifique login e senha.")
                }
            }
        }
    }

    private fun testarConexaoIndividual(baseUrl: String, user: String, pass: String): String? {
        val urlLimpa = normalizarBaseUrl(baseUrl).removeSuffix("/")
        return try {
            val request = Request.Builder()
                .url("$urlLimpa/player_api.php?username=$user&password=$pass")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val valido = body.contains("user_info") &&
                            body.contains("server_info") &&
                            !body.contains("\"auth\":0") &&
                            !body.contains("\"auth\": 0")
                    if (valido) urlLimpa else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    private fun testarConexaoIndividualLento(baseUrl: String, user: String, pass: String): String? {
        val clientLento = OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        val urlLimpa = normalizarBaseUrl(baseUrl).removeSuffix("/")
        return try {
            val request = Request.Builder()
                .url("$urlLimpa/player_api.php?username=$user&password=$pass")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            clientLento.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val valido = body.contains("user_info") &&
                            body.contains("server_info") &&
                            !body.contains("\"auth\":0") &&
                            !body.contains("\"auth\": 0")
                    if (valido) urlLimpa else null
                } else null
            }
        } catch (e: Exception) { null }
    }

    private suspend fun preCarregarLoteMinimo(dns: String, user: String, pass: String) {
        try {
            val db = AppDatabase.getDatabase(this)
            val base = normalizarBaseUrl(dns)
            coroutineScope {
                val jF = async(Dispatchers.IO) {
                    try {
                        val r = URL("${base}player_api.php?username=$user&password=$pass&action=get_vod_streams").readText()
                        val arr = JSONArray(r)
                        val batch = mutableListOf<VodEntity>()
                        for (i in 0 until minOf(10, arr.length())) {
                            val o = arr.getJSONObject(i)
                            batch.add(VodEntity(o.optInt("stream_id"), o.optString("name"), o.optString("name"), o.optString("stream_icon"), o.optString("container_extension"), o.optString("rating"), o.optString("category_id"), o.optLong("added")))
                        }
                        if (batch.isNotEmpty()) db.streamDao().insertVodStreams(batch)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                val jS = async(Dispatchers.IO) {
                    try {
                        val r = URL("${base}player_api.php?username=$user&password=$pass&action=get_series").readText()
                        val arr = JSONArray(r)
                        val batch = mutableListOf<SeriesEntity>()
                        for (i in 0 until minOf(10, arr.length())) {
                            val o = arr.getJSONObject(i)
                            batch.add(SeriesEntity(o.optInt("series_id"), o.optString("name"), o.optString("cover"), o.optString("rating"), o.optString("category_id"), o.optLong("last_modified")))
                        }
                        if (batch.isNotEmpty()) db.streamDao().insertSeriesStreams(batch)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                jF.await(); jS.await()
            }
        } catch (e: Exception) { e.printStackTrace() }
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun normalizarBaseUrl(dns: String): String {
        var url = dns.trim()
        if (url.contains("player_api.php")) url = url.substringBefore("player_api.php")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        if (!url.endsWith("/")) url += "/"
        return url
    }

    private fun salvarCredenciais(dns: String, user: String, pass: String) {
        getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().apply {
            putString("dns", dns); putString("username", user); putString("password", pass); apply()
        }
        XtreamApi.salvarDns(this, dns)
    }

    private fun decidirProximaTela() {
        val intent = if (isTv()) Intent(this, HomeActivity::class.java).apply { putExtra("PROFILE_NAME", "TV_Box") }
                     else Intent(this, ProfilesActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun mostrarErro(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        pararAnimacaoPontinhos()
        super.onDestroy()
    }
}
