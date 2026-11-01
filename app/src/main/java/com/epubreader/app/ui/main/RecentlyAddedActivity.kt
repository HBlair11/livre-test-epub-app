package com.epubreader.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.R
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

        val ids = intent.getLongArrayExtra("recently_added_ids")?.toList() ?: emptyList()
        val sourceResId = intent.getIntExtra("recently_added_source_res", R.string.nav_home)

        viewController = RecentlyAddedViewController(this, binding.root, ids, sourceResId)
    }
}
