package com.marcmayol.dracapps

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Comprobación de que se puede pintar Compose de verdad sin emulador. */
@RunWith(RobolectricTestRunner::class)
class HumoTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `compose se pinta sin emulador`() {
        compose.setContent { Text("hola") }
        compose.onNodeWithText("hola").assertIsDisplayed()
    }
}
