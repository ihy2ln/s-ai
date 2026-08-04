package com.sai.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "S.Ai"
            textSize = 32f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.WHITE)
        })
    }
}
