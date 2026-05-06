package com.vltv.play

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.vltv.play.data.AppDatabase
import com.vltv.play.data.ProfileEntity
import com.vltv.play.data.SeriesEntity
import com.vltv.play.data.VodEntity
import com.vltv.play.databinding.ActivityProfileSelectionBinding
import com.vltv.play.databinding.ItemProfileCircleBinding
import com.vltv.play.ui.AvatarSelectionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.net.URL

class ProfilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSelectionBinding
    private var isEditMode = false
    private lateinit var adapter: ProfileAdapter
    private val db by lazy { AppDatabase.getDatabase(this) }
    private val listaPerfis = mutableListOf<ProfileEntity>()

    // TRAVA DE SEGURANÇA (SEMÁFORO)
    private var isCreating = false
    private val mutex = Mutex()

    private val tmdbApiKey = "9b73f5dd15b8165b1b57419be2f29128"

    // URLs Padrão para os 4 perfis iniciais
    private val defaultAvatarUrl1 = "https://image.tmdb.org/t/p/original/ywe9S1cOyIhR5yWzK7511NuQ2YX.jpg"
    private val defaultAvatarUrl2 = "https://image.tmdb.org/t/p/original/4fLZUr1e65hKPPVw0R3PmKFKxj1.jpg"
    private val defaultAvatarUrl3 = "https://image.tmdb.org/t/p/original/53iAkBnBhqJh2ZmhCug4lSCSUq9.jpg"
    private val defaultAvatarUrl4 = "https://image.tmdb.org/t/p/original/8I37NtDffNV7AZlDa7uDvvqhovU.jpg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ LÓGICA DE AUTO-LOGIN — mantida intacta
        verificarPerfilSalvo()

        setupRecyclerView()

        // Mostra shimmer enquanto carrega do banco
        showShimmer(true)
        loadProfilesFromDb()

        binding.tvEditProfiles.setOnClickListener {
            isEditMode = !isEditMode
            binding.tvEditProfiles.text = if (isEditMode) "CONCLUÍDO" else "EDITAR PERFIS"
            adapter.setEditMode(isEditMode)
        }

        binding.layoutAddProfile.setOnClickListener {
            addNewProfile()
        }

        // ✅ INICIA O DOWNLOAD INVISÍVEL NO FUNDO
        // Isso NÃO trava a tela. Roda em paralelo enquanto o usuário escolhe o perfil.
        iniciarPreCarregamentoEmBackground()
    }

    // ✅ FUNÇÃO NOVA: Baixa os primeiros conteúdos em silêncio para a Home não abrir vazia
    private fun iniciarPreCarregamentoEmBackground() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val dns = prefs.getString("dns", null) ?: return
        val user = prefs.getString("username", null) ?: return
        val pass = prefs.getString("password", null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Se já tem conteúdo no banco, não precisa baixar de novo aqui
                val temConteudo = db.streamDao().getVodCount() > 0
                if (temConteudo) return@launch

                var base = dns.trim()
                if (base.contains("player_api.php")) base = base.substringBefore("player_api.php")
                if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
                if (!base.endsWith("/")) base += "/"

                // Baixa um lote rápido de Filmes (50 itens)
                try {
                    val vodUrl = "${base}player_api.php?username=$user&password=$pass&action=get_vod_streams"
                    val response = URL(vodUrl).readText()
                    val jsonArray = JSONArray(response)
                    val batch = mutableListOf<VodEntity>()
                    val limit = minOf(50, jsonArray.length())

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
                } catch (e: Exception) { e.printStackTrace() }

                // Baixa um lote rápido de Séries (50 itens)
                try {
                    val seriesUrl = "${base}player_api.php?username=$user&password=$pass&action=get_series"
                    val response = URL(seriesUrl).readText()
                    val jsonArray = JSONArray(response)
                    val batch = mutableListOf<SeriesEntity>()
                    val limit = minOf(50, jsonArray.length())

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
                } catch (e: Exception) { e.printStackTrace() }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ✅ FUNÇÃO QUE VERIFICA A "CADERNETA" (SHARED PREFERENCES) — mantida intacta
    private fun verificarPerfilSalvo() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val salvoNome = prefs.getString("last_profile_name", null)
        val salvoIcon = prefs.getString("last_profile_icon", null)

        val forcarSelecao = intent.getBooleanExtra("FORCE_SELECTION", false)

        if (!forcarSelecao && salvoNome != null) {
            val intent = Intent(this, HomeActivity::class.java)
            intent.putExtra("PROFILE_NAME", salvoNome)
            intent.putExtra("PROFILE_ICON", salvoIcon)
            startActivity(intent)
            finish()
        }
    }

    // ─── Controla visibilidade do Shimmer e do RecyclerView ───────────────────
    private fun showShimmer(show: Boolean) {
        if (show) {
            binding.rvProfiles.alpha = 0f
        } else {
            binding.rvProfiles.animate()
                .alpha(1f)
                .setDuration(350)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter(listaPerfis)
        binding.rvProfiles.layoutManager = GridLayoutManager(this, 2)
        binding.rvProfiles.adapter = adapter

        // Desativa o piscar padrão ao atualizar itens
        binding.rvProfiles.itemAnimator = null
    }

    private fun loadProfilesFromDb() {
        if (isCreating) return

        lifecycleScope.launch {
            mutex.withLock {
                val perfis = withContext(Dispatchers.IO) { db.streamDao().getAllProfiles() }

                if (perfis.isEmpty()) {
                    createDefaultProfiles()
                } else {
                    listaPerfis.clear()
                    listaPerfis.addAll(perfis)
                    withContext(Dispatchers.Main) {
                        adapter.notifyDataSetChanged()
                        showShimmer(false)
                        // Dispara animação escalonada nos cards após carregar
                        animateCardsIn()
                    }
                }
            }
        }
    }

    // ─── Animação escalonada dos cards ao entrar na tela ──────────────────────
    private fun animateCardsIn() {
        binding.rvProfiles.post {
            val layoutManager = binding.rvProfiles.layoutManager as GridLayoutManager
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()

            for (i in firstVisible..lastVisible) {
                val view = layoutManager.findViewByPosition(i) ?: continue
                val delay = (i * 60L).coerceAtMost(300L)

                view.translationY = 80f
                view.alpha = 0f

                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setStartDelay(delay)
                    .setDuration(400)
                    .setInterpolator(OvershootInterpolator(1.1f))
                    .start()
            }
        }
    }

    private suspend fun createDefaultProfiles() {
        isCreating = true

        val padrao = listOf(
            ProfileEntity(name = "Meu Perfil 1", imageUrl = defaultAvatarUrl1),
            ProfileEntity(name = "Meu Perfil 2", imageUrl = defaultAvatarUrl2),
            ProfileEntity(name = "Meu Perfil 3", imageUrl = defaultAvatarUrl3),
            ProfileEntity(name = "Meu Perfil 4", imageUrl = defaultAvatarUrl4)
        )

        withContext(Dispatchers.IO) {
            val checagem = db.streamDao().getAllProfiles()
            if (checagem.isEmpty()) {
                padrao.forEach { db.streamDao().insertProfile(it) }
            }
        }

        val perfisCriados = withContext(Dispatchers.IO) { db.streamDao().getAllProfiles() }

        withContext(Dispatchers.Main) {
            listaPerfis.clear()
            listaPerfis.addAll(perfisCriados)
            adapter.notifyDataSetChanged()
            showShimmer(false)
            animateCardsIn()
            isCreating = false
        }
    }

    private fun addNewProfile() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Novo Perfil")
            .setView(input)
            .setPositiveButton("Adicionar") { _, _ ->
                val nome = input.text.toString()
                if (nome.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.streamDao().insertProfile(ProfileEntity(name = nome, imageUrl = defaultAvatarUrl1))
                        withContext(Dispatchers.Main) {
                            loadProfilesFromDb()
                        }
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ─── Edição de perfil ─────────────────────────────────────────────────────
    private fun showEditOptions(perfil: ProfileEntity) {
        val options = arrayOf("Editar Nome", "Trocar Avatar (Personagens)", "Excluir Perfil")
        AlertDialog.Builder(this)
            .setTitle("O que deseja fazer?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> editProfileName(perfil)
                    1 -> openAvatarSelection(perfil)
                    2 -> deleteProfile(perfil)
                }
            }
            .show()
    }

    // ✅ CORREÇÃO CIRÚRGICA 1: usa .copy() em vez de mutar o objeto direto.
    // O Room identifica o registro pelo ID. Mutando perfil.name diretamente,
    // o data class pode perder a referência de ID e o UPDATE vira INSERT duplicado.
    private fun editProfileName(perfil: ProfileEntity) {
        val input = EditText(this)
        input.setText(perfil.name)
        AlertDialog.Builder(this)
            .setTitle("Editar Nome")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val perfilAtualizado = perfil.copy(name = input.text.toString())
                updateProfileInDb(perfilAtualizado)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ✅ CORREÇÃO CIRÚRGICA 2: idem — usa .copy() para garantir que o Room
    // faça UPDATE no ID correto, sem duplicar o perfil no banco.
    private fun openAvatarSelection(perfil: ProfileEntity) {
        val dialog = AvatarSelectionDialog(this, tmdbApiKey) { imageUrl ->
            val perfilAtualizado = perfil.copy(imageUrl = imageUrl)
            updateProfileInDb(perfilAtualizado)
        }
        dialog.show()
    }

    // ✅ CORREÇÃO CIRÚRGICA 3: após salvar no banco, atualiza as SharedPreferences
    // se o perfil editado for o perfil atualmente ativo.
    // Sem isso, a Home e as outras telas continuam exibindo nome/avatar antigos.
    private fun updateProfileInDb(perfil: ProfileEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.streamDao().updateProfile(perfil)

            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            val nomeAtivo = prefs.getString("last_profile_name", null)
            if (nomeAtivo == perfil.name) {
                prefs.edit().apply {
                    putString("last_profile_name", perfil.name)
                    putString("last_profile_icon", perfil.imageUrl)
                    apply()
                }
            }

            withContext(Dispatchers.Main) {
                loadProfilesFromDb()
            }
        }
    }

    private fun deleteProfile(perfil: ProfileEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.streamDao().deleteProfile(perfil)
            withContext(Dispatchers.Main) {
                loadProfilesFromDb()
            }
        }
    }

    // ─── ADAPTER ──────────────────────────────────────────────────────────────
    inner class ProfileAdapter(private val perfis: List<ProfileEntity>) :
        RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

        private var editMode = false

        fun setEditMode(enabled: Boolean) {
            editMode = enabled
            notifyDataSetChanged()
        }

        inner class ProfileViewHolder(val itemBinding: ItemProfileCircleBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
            val itemBinding = ItemProfileCircleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ProfileViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val perfil = perfis[position]
            holder.itemBinding.tvProfileName.text = perfil.name

            // ✅ Glide em alta definição com transição suave e cache completo
            Glide.with(this@ProfilesActivity)
                .load(perfil.imageUrl ?: R.drawable.ic_profile_placeholder)
                .override(400, 400)                              // Força resolução HD
                .diskCacheStrategy(DiskCacheStrategy.ALL)        // Cache disco + memória
                .transition(DrawableTransitionOptions.withCrossFade(200)) // Fade suave
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(holder.itemBinding.ivProfileAvatar)

            // ─── Modo edição: mostra ícone de lápis e aplica animação de tremor ───
            if (editMode) {
                holder.itemBinding.ivEditOverlay?.visibility = View.VISIBLE
                startWobbleAnimation(holder.itemBinding.root)
            } else {
                holder.itemBinding.ivEditOverlay?.visibility = View.GONE
                holder.itemBinding.root.clearAnimation()
                holder.itemBinding.root.rotation = 0f
            }

            holder.itemBinding.root.setOnClickListener {
                if (editMode) {
                    showEditOptions(perfil)
                } else {
                    // ✅ Animação de toque antes de navegar
                    it.animate()
                        .scaleX(0.92f).scaleY(0.92f)
                        .setDuration(100)
                        .withEndAction {
                            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                            // Salva perfil selecionado
                            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("last_profile_name", perfil.name)
                                putString("last_profile_icon", perfil.imageUrl)
                                apply()
                            }

                            Toast.makeText(
                                this@ProfilesActivity,
                                "Entrando como: ${perfil.name}",
                                Toast.LENGTH_SHORT
                            ).show()

                            val intent = Intent(this@ProfilesActivity, HomeActivity::class.java)
                            intent.putExtra("PROFILE_NAME", perfil.name)
                            intent.putExtra("PROFILE_ICON", perfil.imageUrl)
                            startActivity(intent)
                            finish()
                        }.start()
                }
            }
        }

        override fun getItemCount(): Int = perfis.size

        // ─── Animação de tremor no modo edição (estilo iOS) ───────────────────
        private fun startWobbleAnimation(view: View) {
            val rotateLeft  = ObjectAnimator.ofFloat(view, "rotation", 0f, -2.5f)
            val rotateRight = ObjectAnimator.ofFloat(view, "rotation", -2.5f, 2.5f)
            val rotateBack  = ObjectAnimator.ofFloat(view, "rotation", 2.5f, 0f)

            rotateLeft.duration  = 100
            rotateRight.duration = 200
            rotateBack.duration  = 100

            val set = AnimatorSet()
            set.playSequentially(rotateLeft, rotateRight, rotateBack)
            set.startDelay = (Math.random() * 120).toLong() // offset aleatório por card
            set.start()
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfilesFromDb()
    }
}
