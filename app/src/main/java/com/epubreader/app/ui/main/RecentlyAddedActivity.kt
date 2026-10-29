package com.epubreader.app.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.databinding.ViewRecentlyAddedBinding
import com.epubreader.app.util.SystemBarController

class RecentlyAddedActivity : AppCompatActivity() {

    private lateinit var binding: ViewRecentlyAddedBinding
    private lateinit var viewController: RecentlyAddedViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ViewRecentlyAddedBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        binding.toolbar.visibility = View.VISIBLE
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        viewController = RecentlyAddedViewController(this, binding.root)
    }
}
