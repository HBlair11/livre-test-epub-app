package com.epubreader.app.ui

import com.epubreader.app.util.SystemBarController

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.databinding.ActivityMetadataRefreshBinding

class MetadataRefreshActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMetadataRefreshBinding
    private lateinit var metadataRefreshViewController: MetadataRefreshViewController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMetadataRefreshBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        metadataRefreshViewController = MetadataRefreshViewController(this, binding)
        metadataRefreshViewController.setup()
    }
}
