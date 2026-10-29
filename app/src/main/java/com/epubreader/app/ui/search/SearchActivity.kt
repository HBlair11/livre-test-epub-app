package com.epubreader.app.ui

import com.epubreader.app.util.SystemBarController

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.epubreader.app.databinding.ActivitySearchBinding

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var searchViewController: SearchViewController

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarController.apply(this)
        setSupportActionBar(binding.searchToolbar)
        binding.searchToolbar.setNavigationOnClickListener {
            searchViewController.dismissSearchKeyboard()
            finish()
        }

        searchViewController = SearchViewController(this, binding)
        searchViewController.setup()
    }

    override fun onResume() {
        super.onResume()
        searchViewController.onResume()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (searchViewController.dispatchTouchEvent(ev)) return true
        return super.dispatchTouchEvent(ev)
    }
}
