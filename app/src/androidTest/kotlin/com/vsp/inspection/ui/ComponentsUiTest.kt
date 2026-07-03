package com.vsp.inspection.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vsp.core.ui.components.EmptyState
import com.vsp.core.ui.components.PrimaryButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented UI test covering shared accessible components used across every screen.
 * Runs on device/emulator or CI with a connected device.
 */
class ComponentsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyState_showsTitleAndSubtitle() {
        composeRule.setContent {
            EmptyState(title = "No inspections yet", subtitle = "Start a new one")
        }
        composeRule.onNodeWithText("No inspections yet").assertIsDisplayed()
        composeRule.onNodeWithText("Start a new one").assertIsDisplayed()
    }

    @Test
    fun primaryButton_invokesClick() {
        var clicked = false
        composeRule.setContent {
            PrimaryButton(text = "Sign in", onClick = { clicked = true })
        }
        composeRule.onNodeWithText("Sign in").performClick()
        assertTrue(clicked)
    }
}
