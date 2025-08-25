package com.example.dlm.profile
import com.example.dlm.R
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.dlm.profile.GeneralFragment
import com.example.dlm.profile.SenasFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import de.hdodenhof.circleimageview.CircleImageView

class ProfileActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var profileImage: CircleImageView
    private lateinit var btnChangePhoto: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initViews()
        setupToolbar()
        setupViewPager()
        setupListeners()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)
        profileImage = findViewById(R.id.profileImage)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupViewPager() {
        val adapter = ProfilePagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                0 -> tab.text = "General"
                1 -> tab.text = "SEÑAS"
            }
        }.attach()
    }

    private fun setupListeners() {
        btnChangePhoto.setOnClickListener {
            Toast.makeText(this, "Cambiar foto de perfil", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class ProfilePagerAdapter(activity: AppCompatActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) = when (position) {
            0 -> GeneralFragment()
            1 -> SenasFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}