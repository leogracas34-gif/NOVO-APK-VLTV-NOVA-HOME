package com.vltv.play

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.vltv.play.data.AppDatabase
import com.vltv.play.data.ProfileEntity
import com.vltv.play.ui.AvatarSelectionDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var rvProfiles: RecyclerView
    private val database by lazy { AppDatabase.getDatabase(this) }
    private var currentProfileName: String = "Padrao"
    private var currentProfileIcon: String? = null
    private val tmdbApiKey = "9b73f5dd15b8165b1b57419be2f29128"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        currentProfileName = intent.getStringExtra("PROFILE_NAME")
            ?: prefs.getString("last_profile_name", "Padrao") ?: "Padrao"
        currentProfileIcon = intent.getStringExtra("PROFILE_ICON")
            ?: prefs.getString("last_profile_icon", null)

        // Views
        val switchParental = findViewById<Switch>(R.id.switchParental)
        val etPin          = findViewById<EditText>(R.id.etPin)
        val btnSavePin     = findViewById<Button>(R.id.btnSavePin)
        val layoutPin      = findViewById<LinearLayout>(R.id.layoutPin)
        val tvVersion      = findViewById<TextView?>(R.id.tvVersion)
        val cardClearCache = findViewById<LinearLayout?>(R.id.cardClearCache)
        val cardAbout      = findViewById<LinearLayout?>(R.id.cardAbout)
        val cardLogout     = findViewById<LinearLayout?>(R.id.cardLogout)
        val btnBack        = findViewById<TextView?>(R.id.btnBackSettings)
        rvProfiles = findViewById(R.id.rvProfilesSettings)

        btnBack?.setOnClickListener { finish() }

        // ── Versão ───────────────────────────────────────────────────────────
        tvVersion?.text = "Versão 1.0.0"

        // ── Controle Parental ─────────────────────────────────────────────────
        val parentalAtivo = ParentalControlManager.isEnabled(this)
        switchParental.isChecked = parentalAtivo
        layoutPin.visibility = if (parentalAtivo) View.VISIBLE else View.GONE
        etPin.setText(ParentalControlManager.getPin(this))

        switchParental.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                mostrarDialogConfirmacao(
                    titulo   = "Ativar Controle Parental",
                    mensagem = "O controle parental bloqueará conteúdo adulto em todas as telas. Defina um PIN de 4 dígitos para proteger as configurações.",
                    btnPositivo = "Ativar",
                    corPositivo = "#FFFFFF"
                ) {
                    ParentalControlManager.setEnabled(this, true)
                    layoutPin.visibility = View.VISIBLE
                    mostrarToastPremium("Controle parental ativado ✓")
                }
            } else {
                verificarPinParaAcao("Desativar controle parental?") {
                    ParentalControlManager.setEnabled(this, false)
                    layoutPin.visibility = View.GONE
                    mostrarToastPremium("Controle parental desativado")
                }
            }
        }

        btnSavePin.setOnClickListener {
            val pin = etPin.text.toString().trim()
            if (pin.length != 4) {
                mostrarToastPremium("O PIN precisa ter exatamente 4 dígitos")
            } else {
                ParentalControlManager.setPin(this, pin)
                mostrarToastPremium("PIN salvo com sucesso ✓")
            }
        }

        // ── Perfis ────────────────────────────────────────────────────────────
        setupProfilesSection()

        // ── Limpar Cache ──────────────────────────────────────────────────────
        cardClearCache?.setOnClickListener {
            mostrarDialogConfirmacao(
                titulo      = "Limpar Cache",
                mensagem    = "Isso remove imagens e dados temporários. O app pode ficar mais lento na próxima abertura enquanto recarrega.",
                btnPositivo = "Limpar",
                corPositivo = "#FFFFFF"
            ) {
                Thread { Glide.get(this).clearDiskCache() }.start()
                Glide.get(this).clearMemory()
                mostrarToastPremium("Cache limpo com sucesso ✓")
            }
        }

        // ── Sobre ─────────────────────────────────────────────────────────────
        cardAbout?.setOnClickListener {
            mostrarDialogInfo(
                titulo  = "VLTV Play",
                mensagem = "Versão 1.0.0\n\nSeu entretenimento premium em um só lugar.\nFilmes, séries, canais ao vivo e muito mais."
            )
        }

        // ── Sair ──────────────────────────────────────────────────────────────
        cardLogout?.setOnClickListener {
            mostrarDialogConfirmacao(
                titulo      = "Sair da Conta",
                mensagem    = "Você será desconectado e redirecionado para a tela de login. Seus favoritos e histórico serão mantidos.",
                btnPositivo = "Sair",
                corPositivo = "#FF5252"
            ) {
                getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit()
                    .remove("username").remove("password").remove("dns")
                    .remove("last_profile_name").remove("last_profile_icon")
                    .apply()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }
    }

    // ─── Seção de perfis ──────────────────────────────────────────────────────
    private fun setupProfilesSection() {
        rvProfiles.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        carregarPerfis()
    }

    private fun carregarPerfis() {
        lifecycleScope.launch(Dispatchers.IO) {
            val perfis = database.streamDao().getAllProfiles()
            withContext(Dispatchers.Main) {
                rvProfiles.adapter = SettingsProfileAdapter(perfis) { perfil ->
                    mostrarOpcoesEdicao(perfil)
                }
            }
        }
    }

    // ─── Opções ao clicar num perfil ─────────────────────────────────────────
    private fun mostrarOpcoesEdicao(perfil: ProfileEntity) {
        val isAtivo = perfil.name == currentProfileName

        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(0, 0, 0, 0)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp, 24.dp, 24.dp, 16.dp)
        }
        val imgHeader = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(72.dp, 72.dp)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#222222"))
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        Glide.with(this).load(perfil.imageUrl)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .override(200, 200)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .placeholder(R.drawable.ic_profile_placeholder)
            .into(imgHeader)

        val tvNomeHeader = TextView(this).apply {
            text = perfil.name
            textSize = 16f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp }
        }
        val tvSubtitulo = TextView(this).apply {
            text = if (isAtivo) "Perfil ativo" else "Toque em uma opção"
            textSize = 12f
            setTextColor(if (isAtivo) Color.parseColor("#E50914") else Color.parseColor("#777777"))
            gravity = Gravity.CENTER
        }
        header.addView(imgHeader)
        header.addView(tvNomeHeader)
        header.addView(tvSubtitulo)

        fun divider() = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#222222"))
        }

        fun opcao(icone: String, texto: String, cor: Int = Color.WHITE, onClick: () -> Unit): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24.dp, 16.dp, 24.dp, 16.dp)
                isClickable = true
                isFocusable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#22FFFFFF")),
                    null, null
                )
                addView(TextView(context).apply {
                    text = icone
                    textSize = 18f
                    setTextColor(cor)
                    layoutParams = LinearLayout.LayoutParams(36.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
                    gravity = Gravity.CENTER
                })
                addView(TextView(context).apply {
                    text = texto
                    textSize = 14f
                    setTextColor(cor)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = 12.dp
                    }
                })
                setOnClickListener { dialog.dismiss(); onClick() }
            }
        }

        root.addView(header)
        root.addView(divider())

        if (!isAtivo) {
            root.addView(opcao("👤", "Usar este perfil") {
                trocarPerfilAtivo(perfil)
            })
            root.addView(divider())
        }

        root.addView(opcao("✏️", "Editar nome") {
            editarNomePerfil(perfil)
        })
        root.addView(divider())

        root.addView(opcao("🖼️", "Trocar avatar") {
            trocarAvatarPerfil(perfil)
        })

        if (!isAtivo) {
            root.addView(divider())
            root.addView(opcao("🗑️", "Excluir perfil", Color.parseColor("#FF5252")) {
                confirmarExcluirPerfil(perfil)
            })
        }

        root.addView(divider())
        root.addView(TextView(this).apply {
            text = "Cancelar"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(24.dp, 16.dp, 24.dp, 16.dp)
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        })

        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            attributes = p
        }
        dialog.show()
    }

    // ─── Trocar perfil ativo ─────────────────────────────────────────────────
    private fun trocarPerfilAtivo(perfil: ProfileEntity) {
        val prefs = getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("last_profile_name", perfil.name)
            putString("last_profile_icon", perfil.imageUrl ?: "")
            apply()
        }
        currentProfileName = perfil.name
        currentProfileIcon = perfil.imageUrl
        mostrarToastPremium("Perfil alterado para ${perfil.name}")
        startActivity(Intent(this, HomeActivity::class.java).apply {
            putExtra("PROFILE_NAME", perfil.name)
            putExtra("PROFILE_ICON", perfil.imageUrl ?: "")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ─── Editar nome do perfil ───────────────────────────────────────────────
    private fun editarNomePerfil(perfil: ProfileEntity) {
        mostrarDialogInput(
            titulo       = "Editar Nome",
            hint         = "Nome do perfil",
            valorInicial = perfil.name,
            btnPositivo  = "Salvar"
        ) { novoNome ->
            if (novoNome.isBlank()) {
                mostrarToastPremium("O nome não pode ficar em branco")
                return@mostrarDialogInput
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val perfilAtualizado = perfil.copy(name = novoNome)
                database.streamDao().updateProfile(perfilAtualizado)

                // Se era o perfil ativo, atualiza as prefs (ainda no IO)
                if (perfil.name == currentProfileName) {
                    getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit()
                        .putString("last_profile_name", novoNome).apply()
                }

                withContext(Dispatchers.Main) {
                    // ✅ CORREÇÃO: atribuição movida para a thread principal
                    if (perfil.name == currentProfileName) {
                        currentProfileName = novoNome
                    }
                    mostrarToastPremium("Nome atualizado ✓")
                    carregarPerfis()
                }
            }
        }
    }

    // ─── Trocar avatar ───────────────────────────────────────────────────────
    private fun trocarAvatarPerfil(perfil: ProfileEntity) {
        AvatarSelectionDialog(this, tmdbApiKey) { novaUrl ->
            lifecycleScope.launch(Dispatchers.IO) {
                val perfilAtualizado = perfil.copy(imageUrl = novaUrl)
                database.streamDao().updateProfile(perfilAtualizado)

                // Se era o perfil ativo, atualiza as prefs (ainda no IO)
                if (perfil.name == currentProfileName) {
                    getSharedPreferences("vltv_prefs", Context.MODE_PRIVATE).edit()
                        .putString("last_profile_icon", novaUrl).apply()
                }

                withContext(Dispatchers.Main) {
                    // ✅ CORREÇÃO: atribuição movida para a thread principal
                    if (perfil.name == currentProfileName) {
                        currentProfileIcon = novaUrl
                    }
                    mostrarToastPremium("Avatar atualizado ✓")
                    carregarPerfis()
                }
            }
        }.show()
    }

    // ─── Confirmar exclusão ──────────────────────────────────────────────────
    private fun confirmarExcluirPerfil(perfil: ProfileEntity) {
        mostrarDialogConfirmacao(
            titulo      = "Excluir Perfil",
            mensagem    = "Tem certeza que deseja excluir o perfil \"${perfil.name}\"? O histórico e favoritos deste perfil serão perdidos.",
            btnPositivo = "Excluir",
            corPositivo = "#FF5252"
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                database.streamDao().deleteProfile(perfil)
                withContext(Dispatchers.Main) {
                    mostrarToastPremium("Perfil excluído")
                    carregarPerfis()
                }
            }
        }
    }

    // ─── Verificar PIN antes de executar ação ────────────────────────────────
    private fun verificarPinParaAcao(descricao: String, onSucesso: () -> Unit) {
        val pinSalvo = ParentalControlManager.getPin(this)

        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24.dp, 24.dp, 24.dp, 20.dp)
        }

        root.addView(TextView(this).apply {
            text = "🔒 PIN Necessário"
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 6.dp }
        })
        root.addView(TextView(this).apply {
            text = descricao
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp }
        })

        val etPinVerifica = EditText(this).apply {
            hint = "••••"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            maxLines = 1
            textSize = 22f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER
            letterSpacing = 0.5f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#333333"))
            }
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
        }
        root.addView(etPinVerifica)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16.dp }
            weightSum = 2f
        }

        btnRow.addView(TextView(this).apply {
            text = "Cancelar"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginEnd = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = 8.dp.toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val sw = findViewById<Switch>(R.id.switchParental)
                sw?.isChecked = ParentalControlManager.isEnabled(this@SettingsActivity)
                dialog.dismiss()
            }
        })

        btnRow.addView(TextView(this).apply {
            text = "Confirmar"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginStart = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 8.dp.toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val digitado = etPinVerifica.text.toString()
                if (digitado == pinSalvo) {
                    dialog.dismiss()
                    onSucesso()
                } else {
                    etPinVerifica.setText("")
                    etPinVerifica.setHintTextColor(Color.parseColor("#FF5252"))
                    etPinVerifica.hint = "PIN incorreto"
                }
            }
        })

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width = (resources.displayMetrics.widthPixels * 0.82).toInt()
            attributes = p
        }
        dialog.show()
        etPinVerifica.requestFocus()
    }

    // ─── Dialog de confirmação premium ──────────────────────────────────────
    private fun mostrarDialogConfirmacao(
        titulo: String,
        mensagem: String,
        btnPositivo: String,
        corPositivo: String = "#FFFFFF",
        onConfirmar: () -> Unit
    ) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24.dp, 24.dp, 24.dp, 20.dp)
        }

        root.addView(TextView(this).apply {
            text = titulo
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp }
        })

        root.addView(TextView(this).apply {
            text = mensagem
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setLineSpacing(0f, 1.4f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dp }
        })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }

        val corBtnPos = try { Color.parseColor(corPositivo) } catch (e: Exception) { Color.WHITE }
        val isDestructive = corPositivo == "#FF5252"

        btnRow.addView(TextView(this).apply {
            text = "Cancelar"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginEnd = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#2A2A2A"))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss() }
        })

        btnRow.addView(TextView(this).apply {
            text = btnPositivo
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDestructive) Color.WHITE else Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginStart = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(corBtnPos)
                cornerRadius = 8.dp.toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { dialog.dismiss(); onConfirmar() }
        })

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#141414"))
                cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            attributes = p
        }
        dialog.show()
    }

    // ─── Dialog de informação ────────────────────────────────────────────────
    private fun mostrarDialogInfo(titulo: String, mensagem: String) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24.dp, 24.dp, 24.dp, 20.dp)
        }
        root.addView(TextView(this).apply {
            text = titulo
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp }
        })
        root.addView(TextView(this).apply {
            text = mensagem
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setLineSpacing(0f, 1.5f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20.dp }
        })
        root.addView(TextView(this).apply {
            text = "OK"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 48.dp)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 8.dp.toFloat()
            }
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#141414")); cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width = (resources.displayMetrics.widthPixels * 0.82).toInt()
            attributes = p
        }
        dialog.show()
    }

    // ─── Dialog de input de texto ────────────────────────────────────────────
    private fun mostrarDialogInput(
        titulo: String,
        hint: String,
        valorInicial: String = "",
        btnPositivo: String = "Confirmar",
        onConfirmar: (String) -> Unit
    ) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141414"))
            setPadding(24.dp, 24.dp, 24.dp, 20.dp)
        }
        root.addView(TextView(this).apply {
            text = titulo; textSize = 17f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14.dp }
        })
        val input = EditText(this).apply {
            this.hint = hint; setText(valorInicial)
            setTextColor(Color.WHITE); setHintTextColor(Color.parseColor("#555555"))
            textSize = 15f; setSingleLine(true)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A")); cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#333333"))
            }
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp }
        }
        root.addView(input)
        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 2f }
        btnRow.addView(TextView(this).apply {
            text = "Cancelar"; textSize = 14f; setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginEnd = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A")); cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#2A2A2A"))
            }
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(TextView(this).apply {
            text = btnPositivo; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, 48.dp, 1f).apply { marginStart = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE); cornerRadius = 8.dp.toFloat()
            }
            isClickable = true; isFocusable = true
            setOnClickListener { dialog.dismiss(); onConfirmar(input.text.toString().trim()) }
        })
        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#141414")); cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width = (resources.displayMetrics.widthPixels * 0.85).toInt()
            attributes = p
            setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        dialog.show()
        input.requestFocus()
    }

    // ─── Toast premium ───────────────────────────────────────────────────────
    private fun mostrarToastPremium(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    // ─── Adapter de perfis ───────────────────────────────────────────────────
    inner class SettingsProfileAdapter(
        private val list: List<ProfileEntity>,
        private val onClick: (ProfileEntity) -> Unit
    ) : RecyclerView.Adapter<SettingsProfileAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView = v.findViewById(R.id.imgProfileItem)
            val tvName: TextView = v.findViewById(R.id.tvProfileNameItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_profile_settings, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val perfil = list[position]
            holder.tvName.text = perfil.name

            Glide.with(this@SettingsActivity)
                .load(perfil.imageUrl)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(200, 200)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(holder.img)

            val isAtivo = perfil.name == currentProfileName
            holder.itemView.alpha = if (isAtivo) 1.0f else 0.55f
            holder.img.setBackgroundResource(
                if (isAtivo) R.drawable.bg_profile_border else 0
            )
            holder.tvName.setTextColor(
                if (isAtivo) Color.WHITE else Color.parseColor("#888888")
            )
            holder.tvName.typeface =
                if (isAtivo) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            holder.itemView.setOnClickListener { onClick(perfil) }
        }

        override fun getItemCount() = list.size
    }
}
