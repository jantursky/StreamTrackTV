package com.jantursky.streamtracktv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jantursky.streamtracktv.ui.StreamTrackRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as StreamTrackApplication).graph.ui
        setContent { StreamTrackRoot(graph) }
    }
}
