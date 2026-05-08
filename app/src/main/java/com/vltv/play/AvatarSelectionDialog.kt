package com.vltv.play.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import com.vltv.play.R

class AvatarSelectionDialog(
    context: Context,
    private val apiKey: String,
    private val onAvatarSelected: (String) -> Unit
) : Dialog(context) {

    data class AvatarItem(val nome: String, val url: String, val categoria: String)

    private val todosAvatares = listOf(
        // ── Marvel ──────────────────────────────────────────────────────────
        AvatarItem("Iron Man",       "https://image.tmdb.org/t/p/w185/78lPtwv72eTNqFW9COBV0l9L7QZ.jpg", "Marvel"),
        AvatarItem("Spider-Man",     "https://image.tmdb.org/t/p/w185/or06FN3Dka5tukK1e9sl16pB3iy.jpg", "Marvel"),
        AvatarItem("Thor",           "https://image.tmdb.org/t/p/w185/bKmMX9WWqsnoJfVPq4QaVWUaNOs.jpg", "Marvel"),
        AvatarItem("Hulk",           "https://image.tmdb.org/t/p/w185/of9gOraGNFALfJdFLxJagBXpTxe.jpg", "Marvel"),
        AvatarItem("Black Widow",    "https://image.tmdb.org/t/p/w185/qAZ0pzat24kLdO3o8ejmbLxyOac.jpg", "Marvel"),
        AvatarItem("Cap. America",   "https://image.tmdb.org/t/p/w185/e1mjopzAS2KNsvpbpahQ1a6SkSn.jpg", "Marvel"),
        AvatarItem("Dr. Strange",    "https://image.tmdb.org/t/p/w185/uGBVAWjjA55V6UJng842Objhbkm.jpg", "Marvel"),
        AvatarItem("Wanda",          "https://image.tmdb.org/t/p/w185/oCbCHYLtaWMCKKRHhGVOAigFOvH.jpg", "Marvel"),
        AvatarItem("Black Panther",  "https://image.tmdb.org/t/p/w185/uxzzxijgPIY7slzFvMotPv8wjKA.jpg", "Marvel"),
        AvatarItem("Rocket",         "https://image.tmdb.org/t/p/w185/r2J02Z2OpNTectGMFo1QIm6p9pr.jpg", "Marvel"),
        AvatarItem("Groot",          "https://image.tmdb.org/t/p/w185/kSBXou5Ac7vEqKd97wotJumyJvU.jpg", "Marvel"),
        AvatarItem("Thanos",         "https://image.tmdb.org/t/p/w185/2bXbqYdUdNVa8VIWXVfclP2ICtT.jpg", "Marvel"),
        // ── DC ──────────────────────────────────────────────────────────────
        AvatarItem("Batman",         "https://image.tmdb.org/t/p/w185/b0PlSFdDwbyK0cf5RxwDpaOJQvQ.jpg", "DC"),
        AvatarItem("Superman",       "https://image.tmdb.org/t/p/w185/3a3pd7AHs65UVD7SPGbQxbYVOqQ.jpg", "DC"),
        AvatarItem("Wonder Woman",   "https://image.tmdb.org/t/p/w185/imekS7f1OuHyUP2LAiTEM0zBzUz.jpg", "DC"),
        AvatarItem("The Flash",      "https://image.tmdb.org/t/p/w185/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg", "DC"),
        AvatarItem("Aquaman",        "https://image.tmdb.org/t/p/w185/2E7UxTkFnSWb1iT5v31LOf5BYXB.jpg", "DC"),
        AvatarItem("Joker",          "https://image.tmdb.org/t/p/w185/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg", "DC"),
        // ── Disney / Pixar ───────────────────────────────────────────────────
        AvatarItem("Simba",          "https://image.tmdb.org/t/p/w185/zMyfkB2bOd7yFxkMKk4FLiTpPqD.jpg", "Disney"),
        AvatarItem("Moana",          "https://image.tmdb.org/t/p/w185/inVq3FRqcYIRl2la8iZikYYxFNR.jpg", "Disney"),
        AvatarItem("Elsa",           "https://image.tmdb.org/t/p/w185/kgwjIb2JDHRhNk13lmSxiClFjVk.jpg", "Disney"),
        AvatarItem("Woody",          "https://image.tmdb.org/t/p/w185/uXDfjJbdP4ijW5hWSBrPu9LDG1U.jpg", "Disney"),
        AvatarItem("WALL-E",         "https://image.tmdb.org/t/p/w185/hbhFnRzzg6ZDmm8YAmxBnQpQIPh.jpg", "Disney"),
        AvatarItem("Nemo",           "https://image.tmdb.org/t/p/w185/eHuGQ10FUzK1mdOY69wF5pGgEf5.jpg", "Disney"),
        AvatarItem("Rapunzel",       "https://image.tmdb.org/t/p/w185/qMjQulFzJxnBDuoxDd1bTCEGpXi.jpg", "Disney"),
        AvatarItem("Stitch",         "https://image.tmdb.org/t/p/w185/7bD9GBH48mwPjVfpXpFTb5T5BKP.jpg", "Disney"),
        // ── Star Wars ────────────────────────────────────────────────────────
        AvatarItem("Darth Vader",    "https://image.tmdb.org/t/p/w185/6FfCtAuVAW8XJjZ7eWeLibRLWTw.jpg", "Star Wars"),
        AvatarItem("Yoda",           "https://image.tmdb.org/t/p/w185/dvjqlp8BPGTOc7FQUV8t3X1GNEF.jpg", "Star Wars"),
        AvatarItem("Rey",            "https://image.tmdb.org/t/p/w185/dXNAPwY7VrqMAo51EKhhCJRJ5YO.jpg", "Star Wars"),
        AvatarItem("Mandalorian",    "https://image.tmdb.org/t/p/w185/sQn8aPGXMCG4FaWMZCXtJXxVSqr.jpg", "Star Wars"),
        AvatarItem("Grogu",          "https://image.tmdb.org/t/p/w185/kijFzLFZomBJkdtbPUfEhxfaUPX.jpg", "Star Wars"),
        AvatarItem("Obi-Wan",        "https://image.tmdb.org/t/p/w185/l7GBnsr3FyEgRtPlnYxJpH3ey9a.jpg", "Star Wars"),
        // ── Séries ───────────────────────────────────────────────────────────
        AvatarItem("Jon Snow",       "https://image.tmdb.org/t/p/w185/1XS1oqL89opfnbLl8WnZY1O1uJx.jpg", "Séries"),
        AvatarItem("Daenerys",       "https://image.tmdb.org/t/p/w185/xuAIuYSmsUzKlUMBFGVZaHravPZ.jpg", "Séries"),
        AvatarItem("Walter White",   "https://image.tmdb.org/t/p/w185/eFcgrQorqFqMMMYMBNHfagRkFnE.jpg", "Séries"),
        AvatarItem("Eleven",         "https://image.tmdb.org/t/p/w185/49WJfeN0moxb9IPfGn8AIqMGskD.jpg", "Séries"),
        AvatarItem("Wednesday",      "https://image.tmdb.org/t/p/w185/9PFonBhy4cQy7Jz20NpMygczOkv.jpg", "Séries"),
        AvatarItem("Loki",           "https://image.tmdb.org/t/p/w185/kEl2t3OhXc3Zb9FBh1AuYzRTykH.jpg", "Séries"),
        // ── Ação ─────────────────────────────────────────────────────────────
        AvatarItem("James Bond",     "https://image.tmdb.org/t/p/w185/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg", "Ação"),
        AvatarItem("John Wick",      "https://image.tmdb.org/t/p/w185/fZPSd91yGE9fCcCe6OoQr6E3Bev.jpg", "Ação"),
        AvatarItem("Indiana Jones",  "https://image.tmdb.org/t/p/w185/ceG9VzoRAVGwivFU403Wc3AHRys.jpg", "Ação"),
        AvatarItem("Jack Sparrow",   "https://image.tmdb.org/t/p/w185/z8onk7LV9Mmw6zKz4hT6pzzvmvl.jpg", "Ação"),
        AvatarItem("Ethan Hunt",     "https://image.tmdb.org/t/p/w185/AkJg3renhrLDVgGLLQLDaRUPy3a.jpg", "Ação"),
        // ── Anime ─────────────────────────────────────────────────────────────
        AvatarItem("Naruto",         "https://image.tmdb.org/t/p/w185/xppeysfvDKVx775MFuH8Z9BlpMk.jpg", "Anime"),
        AvatarItem("Goku",           "https://image.tmdb.org/t/p/w185/kMSMPFJiGcvOw6cmgK3vHSwkEfL.jpg", "Anime"),
        AvatarItem("Luffy",          "https://image.tmdb.org/t/p/w185/e3NBGiAifW9Xt8xD5tpARskjccO.jpg", "Anime"),
        AvatarItem("Saitama",        "https://image.tmdb.org/t/p/w185/iE3s0lG5QVdEHOEZnoAxjLUiG4E.jpg", "Anime"),
        AvatarItem("Mikasa",         "https://image.tmdb.org/t/p/w185/rkB4LyZHo1NHXFEDHl9vSD9r1lI.jpg", "Anime"),
        AvatarItem("Nezuko",         "https://image.tmdb.org/t/p/w185/wrsFBfBEAEMaFVCXPMPx5o5hFnV.jpg", "Anime")
    )

    private val categorias = listOf("Todos", "Marvel", "DC", "Disney", "Star Wars", "Séries", "Ação", "Anime")
    private var categoriaAtual = "Todos"
    private var urlSelecionada: String? = null

    // ✅ Views declaradas como var nullable — sem lateinit
    private var btnConfirmar: TextView? = null
    private var recyclerView: RecyclerView? = null
    private var gridAdapter: AvatarGridAdapter? = null
    private val chipViews = mutableMapOf<String, TextView>()

    private fun filtrados() =
        if (categoriaAtual == "Todos") todosAvatares
        else todosAvatares.filter { it.categoria == categoriaAtual }

    // ✅ Atualiza o botão sem depender de lateinit
    private fun atualizarBotao() {
        val ativo = urlSelecionada != null
        btnConfirmar?.apply {
            isEnabled = ativo
            val bg = background as? android.graphics.drawable.GradientDrawable
            if (ativo) {
                bg?.setColor(Color.WHITE)
                bg?.setStroke(0, Color.TRANSPARENT)
                setTextColor(Color.BLACK)
            } else {
                bg?.setColor(Color.parseColor("#1A1A1A"))
                bg?.setStroke(1.dp, Color.parseColor("#2A2A2A"))
                setTextColor(Color.parseColor("#444444"))
            }
        }
    }

    private fun atualizarChips(selecionada: String) {
        chipViews.forEach { (cat, chip) ->
            val bg = chip.background as? android.graphics.drawable.GradientDrawable
            if (cat == selecionada) {
                bg?.setStroke(2.dp, Color.parseColor("#FFD700"))
                chip.setTextColor(Color.WHITE)
                chip.typeface = Typeface.DEFAULT_BOLD
            } else {
                bg?.setStroke(1.dp, Color.parseColor("#333333"))
                chip.setTextColor(Color.parseColor("#777777"))
                chip.typeface = Typeface.DEFAULT
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ── Root ─────────────────────────────────────────────────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fun divider() = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 0, 0, 0) }
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        // ── Header ───────────────────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 18.dp, 20.dp, 14.dp)
        }
        header.addView(TextView(context).apply {
            text = "Escolher Avatar"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#888888"))
            setPadding(16.dp, 8.dp, 4.dp, 8.dp)
            setOnClickListener { dismiss() }
        })

        // ── Chips ────────────────────────────────────────────────────────────
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        }
        categorias.forEach { cat ->
            val chip = TextView(context).apply {
                text = cat
                textSize = 12f
                setPadding(16.dp, 7.dp, 16.dp, 7.dp)
                setTextColor(Color.parseColor("#777777"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = 20.dp.toFloat()
                    setStroke(1.dp, Color.parseColor("#333333"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4.dp, 0, 4.dp, 0) }
                setOnClickListener {
                    categoriaAtual = cat
                    atualizarChips(cat)
                    urlSelecionada = null
                    atualizarBotao()
                    gridAdapter?.updateList(filtrados())
                }
            }
            chipViews[cat] = chip
            row.addView(chip)
        }
        atualizarChips("Todos")
        scroll.addView(row)

        // ── RecyclerView ──────────────────────────────────────────────────────
        // ✅ Adapter criado com callback simples — sem animação, sem atraso
        gridAdapter = AvatarGridAdapter(filtrados()) { url ->
            // ✅ Atualiza a variável e o botão IMEDIATAMENTE no clique
            urlSelecionada = url
            atualizarBotao()
        }

        recyclerView = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            layoutManager = GridLayoutManager(context, 4)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            clipToPadding = false
            adapter = gridAdapter
            setHasFixedSize(false)
            setItemViewCacheSize(30)
            // ✅ Pré-carrega imagens antes de mostrar
            setRecycledViewPool(RecyclerView.RecycledViewPool().also { it.setMaxRecycledViews(0, 20) })
        }

        // ── Footer / Botão Confirmar ──────────────────────────────────────────
        // ✅ btnConfirmar criado AQUI, atribuído à var nullable antes do setContentView
        val btn = TextView(context).apply {
            text = "Confirmar Avatar"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            isEnabled = false
            setTextColor(Color.parseColor("#444444"))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor("#2A2A2A"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 52.dp
            )
            setOnClickListener {
                val url = urlSelecionada ?: return@setOnClickListener
                onAvatarSelected(url)
                dismiss()
            }
        }
        // ✅ Atribui à var ANTES de qualquer notifyDataSetChanged
        btnConfirmar = btn

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(16.dp, 8.dp, 16.dp, 16.dp)
        }
        footer.addView(divider())
        footer.addView(btn)

        // ── Montar ────────────────────────────────────────────────────────────
        root.addView(header)
        root.addView(divider())
        root.addView(scroll)
        root.addView(divider())
        root.addView(recyclerView)
        root.addView(footer)

        setContentView(root)

        // Dimensões da janela
        window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D0D"))
                cornerRadius = 16.dp.toFloat()
            })
            val p = attributes
            p.width  = (context.resources.displayMetrics.widthPixels  * 0.92).toInt()
            p.height = (context.resources.displayMetrics.heightPixels * 0.82).toInt()
            attributes = p
        }

        // ✅ Pré-carrega as primeiras imagens visíveis para aparecerem mais rápido
        preCarregarImagens()
    }

    // ✅ Dispara Glide.preload nas primeiras 12 imagens — sem esperar scroll
    private fun preCarregarImagens() {
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .override(185, 185)

        filtrados().take(16).forEach { item ->
            try {
                Glide.with(context)
                    .load(item.url)
                    .apply(options)
                    .preload(185, 185)
            } catch (e: Exception) {}
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    // ── Adapter ───────────────────────────────────────────────────────────────
    inner class AvatarGridAdapter(
        private var list: List<AvatarItem>,
        private val onClick: (String) -> Unit   // ← callback simples, sem animação
    ) : RecyclerView.Adapter<AvatarGridAdapter.VH>() {

        private var selectedPos = -1

        fun updateList(nova: List<AvatarItem>) {
            selectedPos = -1
            list = nova
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val img: ImageView  = v.findViewById(android.R.id.icon)
            val txt: TextView   = v.findViewById(android.R.id.text1)
            val ring: View      = v.findViewById(android.R.id.background)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4.dp, 8.dp, 4.dp, 8.dp) }
                isClickable = true
                isFocusable = true
            }

            val frame = FrameLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(68.dp, 68.dp)
            }

            val ring = View(parent.context).apply {
                id = android.R.id.background
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(0, Color.TRANSPARENT)
                }
                visibility = View.INVISIBLE
            }

            val img = ImageView(parent.context).apply {
                id = android.R.id.icon
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { val m = 3.dp; setMargins(m, m, m, m) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.parseColor("#222222"))
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }

            frame.addView(ring)
            frame.addView(img)

            val txt = TextView(parent.context).apply {
                id = android.R.id.text1
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dp }
                textSize = 9f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            container.addView(frame)
            container.addView(txt)
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.txt.text = item.nome

            // ✅ Glide sem animação (dontAnimate) = aparece mais rápido
            Glide.with(holder.itemView.context)
                .load(item.url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(185, 185)
                .dontAnimate()          // ← sem fade = carrega instantâneo do cache
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(holder.img)

            // Anel dourado de seleção
            val sel = selectedPos == position
            val ringBg = holder.ring.background as? android.graphics.drawable.GradientDrawable
            ringBg?.setStroke(if (sel) 3.dp else 0, if (sel) Color.parseColor("#FFD700") else Color.TRANSPARENT)
            holder.ring.visibility = if (sel) View.VISIBLE else View.INVISIBLE
            holder.txt.setTextColor(if (sel) Color.WHITE else Color.parseColor("#888888"))
            holder.txt.typeface = if (sel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            // ✅ onClick sem withEndAction — chama callback imediatamente
            holder.itemView.setOnClickListener {
                val prev = selectedPos
                selectedPos = holder.adapterPosition
                if (prev >= 0) notifyItemChanged(prev)
                notifyItemChanged(selectedPos)
                onClick(item.url)   // ← imediato, sem delay de animação
            }

            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.scaleX = if (hasFocus) 1.1f else 1f
                v.scaleY = if (hasFocus) 1.1f else 1f
            }
        }

        override fun getItemCount() = list.size
    }
}
