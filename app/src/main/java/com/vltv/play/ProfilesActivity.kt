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

    private var isCreating = false
    private val mutex = Mutex()

    private val tmdbApiKey = "9b73f5dd15b8165b1b57419be2f29128"

    // URLs padrão para os 4 perfis iniciais
    private val defaultAvatarUrl1 = "https://image.tmdb.org/t/p/original/ywe9S1cOyIhR5yWzK7511NuQ2YX.jpg"
    private val defaultAvatarUrl2 = "https://image.tmdb.org/t/p/original/4fLZUr1e65hKPPVw0R3PmKFKxj1.jpg"
    private val defaultAvatarUrl3 = "https://image.tmdb.org/t/p/original/53iAkBnBhqJh2ZmhCug4lSCSUq9.jpg"
    private val defaultAvatarUrl4 = "https://image.tmdb.org/t/p/original/8I37NtDffNV7AZlDa7uDvvqhovU.jpg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        verificarPerfilSalvo()
        setupRecyclerView()
        showShimmer(true)
        loadProfilesFromDb()

        binding.tvEditProfiles.setOnClickListener {
            isEditMode = !isEditMode
            binding.tvEditProfiles.text = if (isEditMode) "CONCLUÍDO" else "EDITAR PERFIS"
            adapter.setEditMode(isEditMode)
        }

        binding.layoutAddProfile.setOnClickListener { addNewProfile() }

        iniciarPreCarregamentoEmBackground()
    }

    private fun iniciarPreCarregamentoEmBackground() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val dns  = prefs.getString("dns", null) ?: return
        val user = prefs.getString("username", null) ?: return
        val pass = prefs.getString("password", null) ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (db.streamDao().getVodCount() > 0) return@launch

                var base = dns.trim()
                if (base.contains("player_api.php")) base = base.substringBefore("player_api.php")
                if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
                if (!base.endsWith("/")) base += "/"

                try {
                    val vodUrl = "${base}player_api.php?username=$user&password=$pass&action=get_vod_streams"
                    val arr = JSONArray(URL(vodUrl).readText())
                    val batch = mutableListOf<VodEntity>()
                    for (i in 0 until minOf(50, arr.length())) {
                        val o = arr.getJSONObject(i)
                        batch.add(VodEntity(
                            stream_id = o.optInt("stream_id"), name = o.optString("name"),
                            title = o.optString("name"), stream_icon = o.optString("stream_icon"),
                            container_extension = o.optString("container_extension"),
                            rating = o.optString("rating"), category_id = o.optString("category_id"),
                            added = o.optLong("added")
                        ))
                    }
                    if (batch.isNotEmpty()) db.streamDao().insertVodStreams(batch)
                } catch (e: Exception) { e.printStackTrace() }

                try {
                    val seriesUrl = "${base}player_api.php?username=$user&password=$pass&action=get_series"
                    val arr = JSONArray(URL(seriesUrl).readText())
                    val batch = mutableListOf<SeriesEntity>()
                    for (i in 0 until minOf(50, arr.length())) {
                        val o = arr.getJSONObject(i)
                        batch.add(SeriesEntity(
                            series_id = o.optInt("series_id"), name = o.optString("name"),
                            cover = o.optString("cover"), rating = o.optString("rating"),
                            category_id = o.optString("category_id"),
                            last_modified = o.optLong("last_modified")
                        ))
                    }
                    if (batch.isNotEmpty()) db.streamDao().insertSeriesStreams(batch)
                } catch (e: Exception) { e.printStackTrace() }

            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun verificarPerfilSalvo() {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        val salvoNome = prefs.getString("last_profile_name", null)
        val salvoIcon = prefs.getString("last_profile_icon", null)
        val forcarSelecao = intent.getBooleanExtra("FORCE_SELECTION", false)

        if (!forcarSelecao && salvoNome != null) {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                putExtra("PROFILE_NAME", salvoNome)
                putExtra("PROFILE_ICON", salvoIcon)
            })
            finish()
        }
    }

    private fun showShimmer(show: Boolean) {
        if (show) {
            binding.rvProfiles.alpha = 0f
        } else {
            binding.rvProfiles.animate()
                .alpha(1f).setDuration(350)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter(listaPerfis)
        binding.rvProfiles.layoutManager = GridLayoutManager(this, 2)
        binding.rvProfiles.adapter = adapter
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
                        animateCardsIn()
                    }
                }
            }
        }
    }

    private fun animateCardsIn() {
        binding.rvProfiles.post {
            val lm = binding.rvProfiles.layoutManager as GridLayoutManager
            for (i in lm.findFirstVisibleItemPosition()..lm.findLastVisibleItemPosition()) {
                val view = lm.findViewByPosition(i) ?: continue
                view.translationY = 80f; view.alpha = 0f
                view.animate().translationY(0f).alpha(1f)
                    .setStartDelay((i * 60L).coerceAtMost(300L))
                    .setDuration(400).setInterpolator(OvershootInterpolator(1.1f)).start()
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
            if (db.streamDao().getAllProfiles().isEmpty()) {
                padrao.forEach { db.streamDao().insertProfile(it) }
            }
        }
        val criados = withContext(Dispatchers.IO) { db.streamDao().getAllProfiles() }
        withContext(Dispatchers.Main) {
            listaPerfis.clear(); listaPerfis.addAll(criados)
            adapter.notifyDataSetChanged(); showShimmer(false); animateCardsIn()
            isCreating = false
        }
    }

    private fun addNewProfile() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Novo Perfil").setView(input)
            .setPositiveButton("Adicionar") { _, _ ->
                val nome = input.text.toString()
                if (nome.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.streamDao().insertProfile(ProfileEntity(name = nome, imageUrl = defaultAvatarUrl1))
                        withContext(Dispatchers.Main) { loadProfilesFromDb() }
                    }
                }
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun showEditOptions(perfil: ProfileEntity) {
        AlertDialog.Builder(this)
            .setTitle("O que deseja fazer?")
            .setItems(arrayOf("Editar Nome", "Trocar Avatar", "Excluir Perfil")) { _, which ->
                when (which) {
                    0 -> editProfileName(perfil)
                    1 -> openAvatarSelection(perfil)
                    2 -> deleteProfile(perfil)
                }
            }.show()
    }

    private fun editProfileName(perfil: ProfileEntity) {
        val input = EditText(this).apply { setText(perfil.name) }
        AlertDialog.Builder(this)
            .setTitle("Editar Nome").setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                updateProfileInDb(perfil.copy(name = input.text.toString()))
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun openAvatarSelection(perfil: ProfileEntity) {
        AvatarSelectionDialog(this, tmdbApiKey) { imageUrl ->
            updateProfileInDb(perfil.copy(imageUrl = imageUrl))
        }.show()
    }

    private fun updateProfileInDb(perfil: ProfileEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.streamDao().updateProfile(perfil)

            // ✅ FIX: compara por ID, não por nome
            // Antes comparava perfil.name com last_profile_name — sempre falso após renomear
            val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
            val nomeAtivo = prefs.getString("last_profile_name", null)

            // Atualiza as prefs se este perfil é o ativo (mesmo que tenha mudado o nome)
            // Usa o imageUrl como sinal secundário caso o nome tenha mudado
            if (nomeAtivo != null) {
                val perfilAtivoNoBanco = db.streamDao().getAllProfiles()
                    .find { it.id == perfil.id }

                if (perfilAtivoNoBanco != null) {
                    // ✅ Sempre atualiza icon nas prefs quando o perfil ativo muda de avatar
                    val iconAtivo = prefs.getString("last_profile_icon", null)
                    val esteEhOAtivo = nomeAtivo == perfil.name ||
                                       (iconAtivo != null && iconAtivo == perfilAtivoNoBanco.imageUrl)

                    if (esteEhOAtivo || nomeAtivo == perfil.name) {
                        prefs.edit().apply {
                            putString("last_profile_name", perfil.name)
                            // ✅ FIX PRINCIPAL: salva o imageUrl atualizado nas prefs
                            // Antes: só atualizava se nomeAtivo == perfil.name (sempre falso após troca de avatar)
                            putString("last_profile_icon", perfil.imageUrl ?: "")
                            apply()
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) { loadProfilesFromDb() }
        }
    }

    private fun deleteProfile(perfil: ProfileEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            db.streamDao().deleteProfile(perfil)
            withContext(Dispatchers.Main) { loadProfilesFromDb() }
        }
    }

    // ─── ADAPTER ─────────────────────────────────────────────────────────────
    inner class ProfileAdapter(private val perfis: List<ProfileEntity>) :
        RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

        private var editMode = false

        fun setEditMode(enabled: Boolean) { editMode = enabled; notifyDataSetChanged() }

        inner class ProfileViewHolder(val itemBinding: ItemProfileCircleBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ProfileViewHolder(ItemProfileCircleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
            val perfil = perfis[position]
            holder.itemBinding.tvProfileName.text = perfil.name

            // ✅ HD: avatar em qualidade máxima com fade suave
            // Usa imageUrl do banco — se for null, usa placeholder
            val avatarUrl = perfil.imageUrl?.takeIf { it.isNotEmpty() }

            Glide.with(this@ProfilesActivity)
                .load(avatarUrl ?: R.drawable.ic_profile_placeholder)
                .override(400, 400)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(holder.itemBinding.ivProfileAvatar)

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
                    it.animate().scaleX(0.92f).scaleY(0.92f).setDuration(100)
                        .withEndAction {
                            it.animate().scaleX(1f).scaleY(1f).setDuration(100).start()

                            // ✅ FIX PRINCIPAL: salva nome E imageUrl nas prefs
                            // Antes: imageUrl podia ser null ou a URL antiga se o avatar foi trocado
                            val iconParaSalvar = perfil.imageUrl ?: ""
                            getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit().apply {
                                putString("last_profile_name", perfil.name)
                                putString("last_profile_icon", iconParaSalvar)
                                apply()
                            }

                            Toast.makeText(this@ProfilesActivity, "Entrando como: ${perfil.name}", Toast.LENGTH_SHORT).show()

                            startActivity(Intent(this@ProfilesActivity, HomeActivity::class.java).apply {
                                putExtra("PROFILE_NAME", perfil.name)
                                // ✅ FIX: passa o icon como PROFILE_ICON para a HomeActivity
                                // A HomeActivity lê: intent.getStringExtra("PROFILE_ICON") ?: savedIcon
                                // Se não passar aqui, ela usa o savedIcon das prefs (que pode estar desatualizado)
                                putExtra("PROFILE_ICON", iconParaSalvar)
                            })
                            finish()
                        }.start()
                }
            }
        }

        override fun getItemCount() = perfis.size

        private fun startWobbleAnimation(view: View) {
            val rotateLeft  = ObjectAnimator.ofFloat(view, "rotation", 0f, -2.5f).apply { duration = 100 }
            val rotateRight = ObjectAnimator.ofFloat(view, "rotation", -2.5f, 2.5f).apply { duration = 200 }
            val rotateBack  = ObjectAnimator.ofFloat(view, "rotation", 2.5f, 0f).apply { duration = 100 }
            AnimatorSet().apply {
                playSequentially(rotateLeft, rotateRight, rotateBack)
                startDelay = (Math.random() * 120).toLong()
                start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfilesFromDb()
    }
}
