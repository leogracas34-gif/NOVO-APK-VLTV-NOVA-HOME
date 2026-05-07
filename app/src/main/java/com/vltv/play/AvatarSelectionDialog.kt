package com.vltv.play.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
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

    // ✅ Lista curada de avatares premium estilo Disney+
    // Usa imagens diretas do TMDB — sem busca, sem lixo, sempre bonito
    private val avatarsCurados = listOf(

        // ── Marvel ────────────────────────────────────────────────────────
        AvatarItem("Iron Man",       "https://image.tmdb.org/t/p/w300/5qHNjhtjMD4YWH3UP0rm4tKwxCL.jpg"),
        AvatarItem("Spider-Man",     "https://image.tmdb.org/t/p/w300/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg"),
        AvatarItem("Thor",           "https://image.tmdb.org/t/p/w300/bKmMX9WWqsnoJfVPq4QaVWUaNOs.jpg"),
        AvatarItem("Hulk",           "https://image.tmdb.org/t/p/w300/gKkl37BQuKTanygYQG1pyYgLVgf.jpg"),
        AvatarItem("Black Widow",    "https://image.tmdb.org/t/p/w300/6OEBp0Gqv6DsOgi8diPUslT2kbA.jpg"),
        AvatarItem("Captain America","https://image.tmdb.org/t/p/w300/neFaP3SaoNRSBzE1PjaS8KRJENE.jpg"),
        AvatarItem("Dr. Strange",    "https://image.tmdb.org/t/p/w300/2H1TmgdfNtsKlU9jKdeNyYL5y8T.jpg"),
        AvatarItem("Wanda",          "https://image.tmdb.org/t/p/w300/1YXSe6fJeNW0DFQ5fsMG7J2Sfoo.jpg"),
        AvatarItem("Black Panther",  "https://image.tmdb.org/t/p/w300/uxzzxijgPIY7slzFvMotPv8wjKA.jpg"),
        AvatarItem("Rocket",         "https://image.tmdb.org/t/p/w300/oENY593ntzOeABBH94YnR2UBSKA.jpg"),
        AvatarItem("Groot",          "https://image.tmdb.org/t/p/w300/vNsVSJKGHCi4VNGBF3B1eXBdRKP.jpg"),
        AvatarItem("Thanos",         "https://image.tmdb.org/t/p/w300/jihlBpCVF57WHwxPxmHJNyJIuqe.jpg"),

        // ── DC ────────────────────────────────────────────────────────────
        AvatarItem("Batman",         "https://image.tmdb.org/t/p/w300/74xTEgt7R36Fpooo50r9T25onhq.jpg"),
        AvatarItem("Superman",       "https://image.tmdb.org/t/p/w300/3a3pd7AHs65UVD7SPGbQxbYVOqQ.jpg"),
        AvatarItem("Wonder Woman",   "https://image.tmdb.org/t/p/w300/gGFjVQqcPR2aTVjJVhXbdS5aM6W.jpg"),
        AvatarItem("The Flash",      "https://image.tmdb.org/t/p/w300/rktDFPbfHfUbArZ6OOOKsXcv0Bm.jpg"),
        AvatarItem("Aquaman",        "https://image.tmdb.org/t/p/w300/2E7UxTkFnSWb1iT5v31LOf5BYXB.jpg"),
        AvatarItem("Joker",          "https://image.tmdb.org/t/p/w300/udDclJoHjfjb8Ekgsd4FDteOkCU.jpg"),

        // ── Disney / Pixar ────────────────────────────────────────────────
        AvatarItem("Mickey",         "https://image.tmdb.org/t/p/w300/6nbwTMfXkrQLFHgSNRJXqtA7tva.jpg"),
        AvatarItem("Simba",          "https://image.tmdb.org/t/p/w300/zMyfkB2bOd7yFxkMKk4FLiTpPqD.jpg"),
        AvatarItem("Moana",          "https://image.tmdb.org/t/p/w300/inVq3FRqcYIRl2la8iZikYYxFNR.jpg"),
        AvatarItem("Elsa",           "https://image.tmdb.org/t/p/w300/2gKDoujFmQndqMfHXotPl1Duwry.jpg"),
        AvatarItem("Woody",          "https://image.tmdb.org/t/p/w300/kMSMPFJiGcvOw6cmgK3vHSwkEfL.jpg"),
        AvatarItem("WALL-E",         "https://image.tmdb.org/t/p/w300/hbhFnRzzg6ZDmm8YAmxBnQpQIPh.jpg"),
        AvatarItem("Nemo",           "https://image.tmdb.org/t/p/w300/kgwjIb2JDHRhNk13lmSxiClFjVk.jpg"),
        AvatarItem("Rapunzel",       "https://image.tmdb.org/t/p/w300/qMjQulFzJxnBDuoxDd1bTCEGpXi.jpg"),

        // ── Star Wars ─────────────────────────────────────────────────────
        AvatarItem("Darth Vader",    "https://image.tmdb.org/t/p/w300/3KM0j8mJfDfQdDFLq6EoV6swkBG.jpg"),
        AvatarItem("Yoda",           "https://image.tmdb.org/t/p/w300/dvjqlp8BPGTOc7FQUV8t3X1GNEF.jpg"),
        AvatarItem("Rey",            "https://image.tmdb.org/t/p/w300/dXNAPwY7VrqMAo51EKhhCJRJ5YO.jpg"),
        AvatarItem("Mandalorian",    "https://image.tmdb.org/t/p/w300/sQn8aPGXMCG4FaWMZCXtJXxVSqr.jpg"),
        AvatarItem("Grogu",          "https://image.tmdb.org/t/p/w300/kijFzLFZomBJkdtbPUfEhxfaUPX.jpg"),
        AvatarItem("Obi-Wan",        "https://image.tmdb.org/t/p/w300/l7GBnsr3FyEgRtPlnYxJpH3ey9a.jpg"),

        // ── Séries populares ──────────────────────────────────────────────
        AvatarItem("Jon Snow",       "https://image.tmdb.org/t/p/w300/c7ATN5mNrBW0vA5BkXNmu4mkrDo.jpg"),
        AvatarItem("Daenerys",       "https://image.tmdb.org/t/p/w300/xuAIuYSmsUzKlUMBFGVZaHravPZ.jpg"),
        AvatarItem("Walter White",   "https://image.tmdb.org/t/p/w300/uGy4DCmgU4N7ufKA3JoepxRaGKn.jpg"),
        AvatarItem("Eleven",         "https://image.tmdb.org/t/p/w300/4Gq6RgZT7BfO3MNOdJN0S5bEsKC.jpg"),
        AvatarItem("Wednesday",      "https://image.tmdb.org/t/p/w300/jeGtaMwGxPmQN5xM4ClnwPQcKdF.jpg"),
        AvatarItem("Loki",           "https://image.tmdb.org/t/p/w300/9uGHEgsiUXjCNq8wdq4r49YL8A1.jpg"),

        // ── Ação / Aventura ───────────────────────────────────────────────
        AvatarItem("James Bond",     "https://image.tmdb.org/t/p/w300/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg"),
        AvatarItem("John Wick",      "https://image.tmdb.org/t/p/w300/fZPSd91yGE9fCcCe6OoQr6E3Bev.jpg"),
        AvatarItem("Ethan Hunt",     "https://image.tmdb.org/t/p/w300/8qBylBsQf4llkGrWR3qAsOtOU8O.jpg"),
        AvatarItem("Indiana Jones",  "https://image.tmdb.org/t/p/w300/ceG9VzoRAVGwivFU403Wc3AHRys.jpg"),
        AvatarItem("Jack Sparrow",   "https://image.tmdb.org/t/p/w300/rOyzFaIkWQqlk2hMm2CVrVgLk4Q.jpg"),

        // ── Anime ─────────────────────────────────────────────────────────
        AvatarItem("Naruto",         "https://image.tmdb.org/t/p/w300/xppeysfvDKVx775MFuH8Z9BlpMk.jpg"),
        AvatarItem("Goku",           "https://image.tmdb.org/t/p/w300/3n3bSY6bpOPRLfMWVAUPr3M5lSG.jpg"),
        AvatarItem("Luffy",          "https://image.tmdb.org/t/p/w300/e3NBGiAifW9Xt8xD5tpARskjccO.jpg"),
        AvatarItem("Saitama",        "https://image.tmdb.org/t/p/w300/iE3s0lG5QVdEHOEZnoAxjLUiG4E.jpg"),
        AvatarItem("Mikasa",         "https://image.tmdb.org/t/p/w300/rkB4LyZHo1NHXFEDHl9vSD9r1lI.jpg"),
        AvatarItem("Nezuko",         "https://image.tmdb.org/t/p/w300/wrsFBfBEAEMaFVCXPMPx5o5hFnV.jpg")
    )

    data class AvatarItem(val nome: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // Layout programático — sem depender de XML externo
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(0, 0, 0, 0)
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(24.dp, 20.dp, 24.dp, 16.dp)
        }
        val tvHeader = TextView(context).apply {
            text = "Escolher Avatar"
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnFechar = TextView(context).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.parseColor("#888888"))
            setPadding(16.dp, 8.dp, 8.dp, 8.dp)
            setOnClickListener { dismiss() }
        }
        header.addView(tvHeader)
        header.addView(btnFechar)

        // Divisor
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            )
            setBackgroundColor(Color.parseColor("#222222"))
        }

        // Grid de avatares
        val recycler = RecyclerView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutManager = GridLayoutManager(context, 4)
            setPadding(12.dp, 12.dp, 12.dp, 24.dp)
            clipToPadding = false
            adapter = AvatarGridAdapter(avatarsCurados) { url ->
                onAvatarSelected(url)
                dismiss()
            }
        }

        root.addView(header)
        root.addView(divider)
        root.addView(recycler)

        setContentView(root)

        // Fundo escuro com bordas arredondadas
        window?.apply {
            setBackgroundDrawable(android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#111111"))
                cornerRadius = 20.dp.toFloat()
            })
            val params = attributes
            params.width = (context.resources.displayMetrics.widthPixels * 0.92).toInt()
            params.height = (context.resources.displayMetrics.heightPixels * 0.75).toInt()
            attributes = params
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    // ── Adapter do grid de avatares ──────────────────────────────────────────
    inner class AvatarGridAdapter(
        private val list: List<AvatarItem>,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<AvatarGridAdapter.VH>() {

        private var selectedPosition = -1

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val imgAvatar: ImageView = v.findViewById(android.R.id.icon)
            val tvNome: TextView     = v.findViewById(android.R.id.text1)
            val ringView: View       = v.findViewById(android.R.id.background)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            // Layout programático para cada card de avatar
            val container = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(6.dp, 8.dp, 6.dp, 8.dp) }
                isClickable = true
                isFocusable = true
            }

            // Anel de seleção (fundo)
            val ring = View(parent.context).apply {
                id = android.R.id.background
                layoutParams = LinearLayout.LayoutParams(72.dp, 72.dp)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(Color.TRANSPARENT)
                    setStroke(3.dp, Color.TRANSPARENT)
                }
                visibility = View.INVISIBLE
            }

            // Wrapper para sobrepor imagem + anel
            val frame = android.widget.FrameLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(72.dp, 72.dp)
            }

            val img = ImageView(parent.context).apply {
                id = android.R.id.icon
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    val margin = 3.dp
                    setMargins(margin, margin, margin, margin)
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
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
                setTextColor(Color.parseColor("#AAAAAA"))
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

            // ✅ HD: avatar em qualidade máxima com fade suave
            Glide.with(holder.itemView.context)
                .load(item.url)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .override(300, 300)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transition(DrawableTransitionOptions.withCrossFade(150))
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
                holder.ringView.visibility = View.INVISIBLE
                holder.tvNome.setTextColor(Color.parseColor("#888888"))
            }

            holder.itemView.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = holder.adapterPosition
                if (prev >= 0) notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
                // Pequena animação de toque
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                        onClick(item.url)
                    }.start()
            }

            // Foco para TV
            holder.itemView.setOnFocusChangeListener { v, hasFocus ->
                v.animate().scaleX(if (hasFocus) 1.12f else 1f)
                           .scaleY(if (hasFocus) 1.12f else 1f)
                           .setDuration(150).start()
            }
        }

        override fun getItemCount() = list.size
    }
}
