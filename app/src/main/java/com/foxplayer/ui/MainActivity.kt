package com.foxplayer.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.foxplayer.FoxApp
import com.foxplayer.R
import com.foxplayer.databinding.ActivityMainBinding
import com.foxplayer.viewmodel.HomeViewModel
import com.foxplayer.viewmodel.SearchViewModel
import com.google.android.material.navigation.NavigationBarView
import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val homeVm: HomeViewModel by viewModels()
    private val searchVm: SearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 注入全局SourceManager到ViewModels
        val app = application as FoxApp
        homeVm.sourceManager = app.sourceManager
        searchVm.sourceManager = app.sourceManager

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)

        // Hide bottom nav on player
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.bottomNav.visibility = when (dest.id) {
                R.id.playerFragment -> View.GONE
                else -> View.VISIBLE
            }
        }
    }
}
