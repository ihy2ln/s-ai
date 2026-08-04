package com.sai.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {

    private val openSample = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            startActivity(
                Intent(this, SampleEditorActivity::class.java)
                    .putExtra(SampleEditorActivity.EXTRA_SAMPLE_URI, uri)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val importButton = Button(this).apply {
            text = "Import Sample (.wav)"
            setOnClickListener { openSample.launch(arrayOf("audio/*")) }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(Color.BLACK)
                addView(importButton)
            }
        )
    }
}
