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
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.vltv.play.R

class AvatarSelectionDialog(
    context: Context,
    private val apiKey: String,
    private val onAvatarSelected: (String) -> Unit
) : Dialog(context) {

    data class AvatarItem(val nome: String, val url: String, val categoria: String)

    // URLs usando w185 (menor = mais rápido) e todas verificadas
    private val todosAvatares = listOf(

        // ── Marvel ──────────────────────────────────────────────────────────
        AvatarItem("Iron Man",        "https://image.tmdb.org/t/p/w185/5qHNjhtjMD4YWH3UP0rm4tKwxCL.jpg",  "Marvel"),
        AvatarItem("Spider-Man",      "https://image.tmdb.org/t/p/w185/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg",  "Marvel"),
        AvatarItem("Thor",            "https://image.tmdb.org/t/p/w185/bKmMX9WWqsnoJfVPq4QaVWUaNOs.jpg",  "Marvel"),
        AvatarItem("Hulk",            "https://image.tmdb.org/t/p/w185/gKkl37BQuKTanygYQG1pyYgLVgf.jpg",  "Marvel"),
        AvatarItem("Black Widow",     "https://image.tmdb.org/t/p/w185/6OEBp0Gqv6DsOgi8diPUslT2kbA.jpg",  "Marvel"),
        AvatarItem("Cap. America",    "https://image.tmdb.org/t/p/w185/neFaP3SaoNRSBzE1PjaS8KRJENE.jpg",  "Marvel"),
        AvatarItem("Dr. Strange",     "https://image.tmdb.org/t/p/w185/2H1TmgdfNtsKlU9jKdeNyYL5y8T.jpg",  "Marvel"),
        AvatarItem("Wanda",           "https://image.tmdb.org/t/p/w185/1YXSe6fJeNW0DFQ5fsMG7J2Sfoo.jpg",  "Marvel"),
        AvatarItem("Black Panther",   "https://image.tmdb.org/t/p/w185/uxzzxijgPIY7slzFvMotPv8wjKA.jpg",  "Marvel"),
        AvatarItem("Rocket",          "https://image.tmdb.org/t/p/w185/oENY593ntzOeABBH94YnR2UBSKA.jpg",  "Marvel"),
        AvatarItem("Groot",           "https://image.tmdb.org/t/p/w185/vNsVSJKGHCi4VNGBF3B1eXBdRKP.jpg",  "Marvel"),
        AvatarItem("Thanos",          "https://image.tmdb.org/t/p/w185/jihlBpCVF57WHwxPxmHJNyJIuqe.jpg",  "Marvel"),

        // ── DC ──────────────────────────────────────────────────────────────
        AvatarItem("Batman",          "https://image.tmdb.org/t/p/w185/74xTEgt7R36Fpooo50r9T25onhq.jpg",  "DC"),
        AvatarItem("Superman",        "https://image.tmdb.org/t/p/w185/3a3pd7AHs65UVD7SPGbQxbYVOqQ.jpg",  "DC"),
        AvatarItem("Wonder Woman",    "https://image.tmdb.org/t/p/w185/gGFjVQqcPR2aTVjJVhXbdS5aM6W.jpg",  "DC"),
        AvatarItem("The Flash",       "https://image.tmdb.org/t/p/w185/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg",  "DC"),
        AvatarItem("Aquaman",         "https://image.tmdb.org/t/p/w185/2E7UxTkFnSWb1iT5v31LOf5BYXB.jpg",  "DC"),
        AvatarItem("Joker",           "https://image.tmdb.org/t/p/w185/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg",  "DC"),

        // ── Disney / Pixar ───────────────────────────────────────────────────
        AvatarItem("Simba",           "https://image.tmdb.org/t/p/w185/zMyfkB2bOd7yFxkMKk4FLiTpPqD.jpg",  "Disney"),
        AvatarItem("Moana",           "https://image.tmdb.org/t/p/w185/inVq3FRqcYIRl2la8iZikYYxFNR.jpg",  "Disney"),
        AvatarItem("Elsa",            "https://image.tmdb.org/t/p/w185/2gKDoujFmQndqMfHXotPl1Duwry.jpg",  "Disney"),
        AvatarItem("Woody",           "https://image.tmdb.org/t/p/w185/kMSMPFJiGcvOw6cmgK3vHSwkEfL.jpg",  "Disney"),
        AvatarItem("WALL-E",          "https://image.tmdb.org/t/p/w185/hbhFnRzzg6ZDmm8YAmxBnQpQIPh.jpg",  "Disney"),
        AvatarItem("Nemo",            "https://image.tmdb.org/t/p/w185/kgwjIb2JDHRhNk13lmSxiClFjVk.jpg",  "Disney"),
        AvatarItem("Rapunzel",        "https://image.tmdb.org/t/p/w185/qMjQulFzJxnBDuoxDd1bTCEGpXi.jpg",  "Disney"),

        // ── Star Wars ────────────────────────────────────────────────────────
        AvatarItem("Darth Vader",     "https://image.tmdb.org/t/p/w185/3KM0j8mJfDfQdDFLq6EoV6swkBG.jpg",  "Star Wars"),
        AvatarItem("Yoda",            "https://image.tmdb.org/t/p/w185/dvjqlp8BPGTOc7FQUV8t3X1GNEF.jpg",  "Star Wars"),
        AvatarItem("Rey",             "https://image.tmdb.org/t/p/w185/dXNAPwY7VrqMAo51EKhhCJRJ5YO.jpg",  "Star Wars"),
        AvatarItem("Mandalorian",     "https://image.tmdb.org/t/p/w185/sQn8aPGXMCG4FaWMZCXtJXxVSqr.jpg",  "Star Wars"),
        AvatarItem("Grogu",           "https://image.tmdb.org/t/p/w185/kijFzLFZomBJkdtbPUfEhxfaUPX.jpg",  "Star Wars"),
        AvatarItem("Obi-Wan",         "https://image.tmdb.org/t/p/w185/l7GBnsr3FyEgRtPlnYxJpH3ey9a.jpg",  "Star Wars"),

        // ── Séries ───────────────────────────────────────────────────────────
        AvatarItem("Jon Snow",        "https://image.tmdb.org/t/p/w185/c7ATN5mNrBW0vA5BkXNmu4mkrDo.jpg",  "Séries"),
        AvatarItem("Daenerys",        "https://image.tmdb.org/t/p/w185/xuAIuYSmsUzKlUMBFGVZaHravPZ.jpg",  "Séries"),
        AvatarItem("Walter White",    "https://image.tmdb.org/t/p/w185/uGy4DCmgU4N7ufKA3JoepxRaGKn.jpg",  "Séries"),
        AvatarItem("Eleven",          "https://image.tmdb.org/t/p/w185/4Gq6RgZT7BfO3MNOdJN0S5bEsKC.jpg",  "Séries"),
        AvatarItem("Wednesday",       "https://image.tmdb.org/t/p/w185/jeGtaMwGxPmQN5xM4ClnwPQcKdF.jpg",  "Séries"),
        AvatarItem("Loki",            "https://image.tmdb.org/t/p/w185/9uGHEgsiUXjCNq8wdq4r49YL8A1.jpg",  "Séries"),

        // ── Ação ─────────────────────────────────────────────────────────────
        AvatarItem("James Bond",      "https://image.tmdb.org/t/p/w185/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",  "Ação"),
        AvatarItem("John Wick",       "https://image.tmdb.org/t/p/w185/fZPSd91yGE9fCcCe6OoQr6E3Bev.jpg",  "Ação"),
        AvatarItem("Ethan Hunt",      "https://image.tmdb.org/t/p/w185/8qBylBsQf4llkGrWR3qAsOtOU8O.jpg",  "Ação"),
        AvatarItem("Indiana Jones",   "https://image.tmdb.org/t/p/w185/ceG9VzoRAVGwivFU403Wc3AHRys.jpg",  "Ação"),
        AvatarItem("Jack Sparrow",    "https://image.tmdb.org/t/p/w185/rOyzFaIkWQqlk2hMm2CVrVgLk4Q.jpg",  "Ação"),

        // ── Anime ─────────────────────────────────────────────────────────────
        AvatarItem("Naruto",          "https://image.tmdb.org/t/p/w185/xppeysfvDKVx775MFuH8Z9BlpMk.jpg",  "Anime"),
        AvatarItem("Goku",            "https://image.tmdb.org/t/p/w185/3n3bSY6bpOPRLfMWVAUPr3M5lSG.jpg",  "Anime"),
        AvatarItem("Luffy",           "https://image.tmdb.org/t/p/w185/e3NBGiAifW9Xt8xD5tpARskjccO.jpg",  "Anime"),
        AvatarItem("Saitama",         "https://image.tmdb.org/t/p/w185/iE3s0lG5QVdEHOEZnoAxjLUiG4E.jpg",  "Anime"),
        AvatarItem("Mikasa",          "https://image.tmdb.org/t/p/w185/rkB4LyZHo1NHXFEDHl9vSD9r1lI.jpg",  "Anime"),
        AvatarItem("Nezuko",          "https://image.tmdb.org/t/p/w185/wrsFBfBEAEMaFVCXPMPx5o5hFnV.jpg",  "Anime")
    )

    private val categorias = listOf("Todos", "Marvel", "DC", "Disney", "Star Wars", "Séries", "Ação", "Anime")

    private var categoriaAtual = "Todos"
    private var avatarSelecionadoUrl: String? = null
    private lateinit var adapter: AvatarGridAdapter
    private lateinit var btnConfirmar: TextView

    private fun avataresFiltrados() =
        if (categoriaAtual == "Todos") todosAvatares
        else todosAvatares.filter { it.categoria == categoriaAtual }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // ── Root ─────────────────────────────────────────────────────────────
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }

        // ── Header ───────────────────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 18.dp, 20.dp, 14.dp)
        }
        val tvHeader = TextView(context).apply {
            text = "Escolher Avatar"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnFechar = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.parseColor("#888888"))
            setPadding(12.dp, 8.dp, 4.dp, 8.dp)
            setOnClickListener { dismiss() }
        }
        header.addView(tvHeader)
        header.addView(btnFechar)

        // ── Divisor ──────────────────────────────────────────────────────────
        fun divider() = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        // ── Categorias (scroll horizontal estilo Disney+) ─────────────────────
        val categoriasScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val categoriasRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        }

        val chipViews = mutableMapOf<String, TextView>()

        fun atualizarChips(selecionada: String) {
            chipViews.forEach { (cat, chip) ->
                if (cat == selecionada) {
                    chip.setBackgroundColor(Color.WHITE)
                    chip.setTextColor(Color.BLACK)
                } else {
                    chip.setBackgroundColor(Color.parseColor("#222222"))
                    chip.setTextColor(Color.parseColor("#AAAAAA"))
                }
            }
        }

        categorias.forEach { cat ->
            val chip = TextView(context).apply {
                text = cat
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16.dp, 7.dp, 16.dp, 7.dp)
                setTextColor(Color.parseColor("#AAAAAA"))
                setBackgroundColor(Color.parseColor("#222222"))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#222222"))
                    cornerRadius = 20.dp.toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4.dp, 0, 4.dp, 0) }
                setOnClickListener {
                    categoriaAtual = cat
                    atualizarChips(cat)
                    adapter.updateList(avataresFiltrados())
                    avatarSelecionadoUrl = null
                    btnConfirmar.alpha = 0.4f
                    btnConfirmar.isEnabled = false
                }
            }
            chipViews[cat] = chip
            categoriasRow.addView(chip)
        }

        // Selecionar "Todos" por padrão
        atualizarChips("Todos")
        categoriasScroll.addView(categoriasRow)

        // ── Grid de avatares ──────────────────────────────────────────────────
        adapter = AvatarGridAdapter(avataresFiltrados()) { url ->
            avatarSelecionadoUrl = url
            btnConfirmar.alpha = 1f
            btnConfirmar.isEnabled = true
        }

        val recycler = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            layoutManager = GridLayoutManager(context, 4)
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            clipToPadding = false
            this.adapter = this@AvatarSelectionDialog.adapter
            setHasFixedSize(true)
            // Pré-cache para carregamento mais rápido
            setItemViewCacheSize(20)
        }

        // ── Rodapé com botão Confirmar ────────────────────────────────────────
        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 10.dp, 16.dp, 16.dp)
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }

        val footerDivider = divider()

        btnConfirmar = TextView(context).apply {
            text = "Confirmar Avatar"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            alpha = 0.4f
            isEnabled = false
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 8.dp.toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                48.dp
            ).apply { topMargin = 10.dp }
            setOnClickListener {
                avatarSelecionadoUrl?.let { url ->
                    onAvatarSelected(url)
                    dismiss()
                }
            }
        }

        footer.addView(footerDivider)
        footer.addView(btnConfirmar)

        // ── Montar layout ─────────────────────────────────────────────────────
        root.addView(header)
        root.addView(divider())
        root.addView(categoriasScroll)
        root.addView(divider())
        root.addView(recycler)
        root.addView(footer)

        setContentView(root)

        // ── Janela arredondada ────────────────────────────────────────────────
        window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D0D"))
                cornerRadius = 16.dp.toFloat()
            })
            val params = attributes
            params.width  = (context.resources.displayMetrics.widthPixels * 0.92).toInt()
            params.height = (context.resources.displayMetrics.heightPixels * 0.80).toInt()
            attributes = params
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    // ── Adapter ───────────────────────────────────────────────────────────────
    inner class AvatarGridAdapter(
        private var list: List<AvatarItem>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<AvatarGridAdapter.VH>() {

        private var selectedPosition = -1

        fun updateList(novaLista: List<AvatarItem>) {
            selectedPosition = -1
            list = novaLista
            notifyDataSetChanged()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val imgAvatar: ImageView = v.findViewById(android.R.id.icon)
            val tvNome: TextView     = v.findViewById(android.R.id.text1)
            val ringView: View       = v.findViewById(android.R.id.background)
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

            val frame = android.widget.FrameLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(68.dp, 68.dp)
            }

            // Anel dourado de seleção
            val ring = View(parent.context).apply {
                id = android.R.id.background
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(3.dp, Color.TRANSPARENT)
                }
                visibility = View.INVISIBLE
            }

            val img = ImageView(parent.context).apply {
                id = android.R.id.icon
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    val m = 3.dp
                    setMargins(m, m, m, m)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.parseColor("#1A1A1A"))
                }
                clipToOutline = true
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }

            frame.addView(ring)
            frame.addView(img)

            val tv = TextView(parent.context).apply {
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
            container.addView(tv)
            return VH(container)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = list[position]
            holder.tvNome.text = item.nome

            // Carregamento rápido: w185, sem ARGB_8888, sem override grande
            Glide.with(holder.itemView.context)
                .load(item.url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(100))
                .circleCrop()
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(holder.imgAvatar)

            // Anel dourado quando selecionado
            val isSelected = selectedPosition == position
            val ringDrawable = holder.ringView.background as? android.graphics.drawable.GradientDrawable
            if (isSelected) {
                ringDrawable?.setStroke(3.dp, Color.parseColor("#FFD700"))
                holder.ringView.visibility = View.VISIBLE
                holder.tvNome.setTextColor(Color.WHITE)
            } else {
                ringDrawable?.setStroke(3.dp, Color.TRANSPARENT)
                holder.ringView.visibility = View.INVISIBLE
                holder.tvNome.setTextColor(Color.parseColor("#888888"))
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.adapterPosition
                if (prev >= 0) notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)

                it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        onClick(item.url)
                    }.start()
            }

            // Foco para TV
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.animate()
                    .scaleX(if (hasFocus) 1.12f else 1f)
                    .scaleY(if (hasFocus) 1.12f else 1f)
                    .setDuration(150).start()
            }
        }

        override fun getItemCount() = list.size
    }
}
