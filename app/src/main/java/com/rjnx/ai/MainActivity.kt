package com.rjnx.ai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch the new Glass Orbit Mio home screen.
        setContentView(R.layout.mio_glass_orbit)
    }
}
