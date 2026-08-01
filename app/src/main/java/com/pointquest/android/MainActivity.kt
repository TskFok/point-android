package com.pointquest.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pointquest.android.app.PointQuestApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PointQuestApplication).container
        setContent { PointQuestApp(container) }
    }
}
