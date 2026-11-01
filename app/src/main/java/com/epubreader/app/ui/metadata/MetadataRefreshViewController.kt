package com.epubreader.app.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager

import com.epubreader.app.R
import com.epubreader.app.databinding.ActivityMetadataRefreshBinding
import com.epubreader.app.epub.MetadataRefreshReportStore

class MetadataRefreshViewController(private val activity: AppCompatActivity, private val binding: ActivityMetadataRefreshBinding) {

    fun setup() {
        val headerContainer = binding.headerContainer
        headerContainer.removeAllViews()
        val inflated = android.view.LayoutInflater.from(activity).inflate(R.layout.header_secondary_category_1, headerContainer, true)
        val toolbar = inflated.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        activity.setSupportActionBar(toolbar)
        toolbar.title = activity.getString(R.string.header_metadata_refresh)
        toolbar.setNavigationOnClickListener {
            activity.finish()
        }

        val report = MetadataRefreshReportStore.latest
        if (report == null) {
            activity.finish()
            return
        }

        binding.summary.text = activity.getString(
            R.string.metadata_refresh_report_summary,
            report.items.size,
        )
        binding.counts.text = activity.getString(
            R.string.metadata_refresh_report_counts,
            report.updated, report.unchanged, report.skipped, report.failed,
        )

        binding.recycler.layoutManager = LinearLayoutManager(activity)
        binding.recycler.adapter = MetadataRefreshAdapter(
            context = activity,
            onBookClick = { bookId ->
                activity.startActivity(
                    Intent(activity, BookDetailsActivity::class.java).putExtra(BookDetailsActivity.EXTRA_BOOK_ID, bookId)
                )
            }
        ).also {
            it.submitReport(report)
        }
    }
}
