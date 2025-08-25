package com.example.dlm.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.dlm.R

class SenasFragment : Fragment() {

    private lateinit var star1: ImageView
    private lateinit var star2: ImageView
    private lateinit var star3: ImageView
    private lateinit var star4: ImageView
    private lateinit var star5: ImageView
    private lateinit var tvSenasCount: TextView

    private var totalSenas = 250 // Ejemplo: el usuario ha aportado 250 señas

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_senas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        updateStarsAndCount()
    }

    private fun initViews(view: View) {
        star1 = view.findViewById(R.id.star1)
        star2 = view.findViewById(R.id.star2)
        star3 = view.findViewById(R.id.star3)
        star4 = view.findViewById(R.id.star4)
        star5 = view.findViewById(R.id.star5)
        tvSenasCount = view.findViewById(R.id.tvSenasCount)
    }

    private fun updateStarsAndCount() {
        tvSenasCount.text = "Total: $totalSenas señas"

        val level = totalSenas / 100

        val stars = listOf(star1, star2, star3, star4, star5)

        stars.forEachIndexed { index, star ->
            if (index < level) {
                star.setImageResource(R.drawable.ic_star_filled)
            } else {
                star.setImageResource(R.drawable.ic_star_outline)
            }
        }

        if (level >= 5) {
            stars.forEach { it.setImageResource(R.drawable.ic_star_filled) }
        }
    }
}