package com.example.myapp // 1. Must match your folder structure

import android.os.Bundle // 2. Library imports go here
import androidx.appcompat.app.AppCompatActivity

// 3. Class definition (inherits from a base class if needed)
class MainActivity : AppCompatActivity() {

    // 4. Variables / Properties
    private var username: String = "Guest"
    private val maxAttempts = 3

    // 5. Lifecycle or core functions
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Calling a local helper function
        val dynamicMessage = generateWelcomeMessage(username)
        println(dynamicMessage)
    }

    /**
     * 6. Helper Function Example
     * Takes a [name] parameter and returns a greeting string.
     */
    private fun generateWelcomeMessage(name: String): String {
        return "Welcome back, $name! You have $maxAttempts attempts remaining."
    }
}
