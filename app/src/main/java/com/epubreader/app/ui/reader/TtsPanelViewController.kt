package com.epubreader.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.epubreader.app.ReaderActivity
import com.epubreader.app.R
import com.epubreader.app.epub.ReaderTtsController

class TtsPanelViewController(
    private val activity: ReaderActivity,
    private val ttsOverlayView: View,
    private val onPlayPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrev: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onClose: () -> Unit
) {

    private val btnClose: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsClose)
    private val btnSettings: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsSettings)
    private val tvTitle: TextView = ttsOverlayView.findViewById(R.id.tvTtsBookTitle)
    private val tvSection: TextView = ttsOverlayView.findViewById(R.id.tvTtsSection)
    private val tvStatus: TextView = ttsOverlayView.findViewById(R.id.tvTtsStatus)
    private val btnPrev: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsPrev)
    private val btnPlayPause: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsPlayPause)
    private val btnNext: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsNext)
    private val btnStop: ImageButton = ttsOverlayView.findViewById(R.id.btnTtsStop)

    init {
        btnClose.setOnClickListener { onClose() }
        btnSettings.setOnClickListener { onOpenSettings() }
        btnPrev.setOnClickListener { onPrev() }
        btnPlayPause.setOnClickListener { onPlayPause() }
        btnNext.setOnClickListener { onNext() }
        btnStop.setOnClickListener { onStop() }
    }

    fun show(bookTitle: String?, sectionLabel: String, playing: Boolean) {
        ttsOverlayView.visibility = View.VISIBLE
        tvTitle.text = bookTitle
        tvSection.text = sectionLabel
        updateControls(playing, ReaderTtsController.State.PLAYING)
    }

    fun hide() {
        ttsOverlayView.visibility = View.GONE
    }

    fun updateControls(playing: Boolean, state: ReaderTtsController.State?) {
        btnPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        btnPlayPause.contentDescription = activity.getString(if (playing) R.string.tts_pause else R.string.tts_play)
        tvStatus.text = when (state) {
            ReaderTtsController.State.PLAYING -> activity.getString(R.string.tts_status_reading)
            ReaderTtsController.State.PAUSED -> activity.getString(R.string.tts_status_paused)
            else -> activity.getString(R.string.tts_status_reading)
        }
    }
}
