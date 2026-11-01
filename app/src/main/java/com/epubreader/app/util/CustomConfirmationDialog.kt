package com.epubreader.app.util

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.epubreader.app.R
import com.google.android.material.button.MaterialButton

object CustomConfirmationDialog {

    fun show(
        context: Context,
        title: String,
        message: String,
        positiveText: String = context.getString(R.string.ok),
        negativeText: String = context.getString(R.string.cancel),
        onPositive: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_confirmation, null, false)
        val tvTitle = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val btnNegative = dialogView.findViewById<MaterialButton>(R.id.btnNegative)
        val btnPositive = dialogView.findViewById<MaterialButton>(R.id.btnPositive)

        tvTitle.text = title
        tvMessage.text = message
        btnNegative.text = negativeText
        btnPositive.text = positiveText

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        btnNegative.setOnClickListener {
            dialog.dismiss()
        }
        btnPositive.setOnClickListener {
            dialog.dismiss()
            onPositive()
        }

        dialog.show()
    }
}
