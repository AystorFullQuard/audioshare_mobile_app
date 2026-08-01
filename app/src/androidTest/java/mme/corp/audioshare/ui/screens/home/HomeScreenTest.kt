package mme.corp.audioshare.ui.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mme.corp.audioshare.ui.screens.home.uiBlocks.HOME_ROOMS_BUTTON_TAG
import mme.corp.audioshare.ui.screens.home.uiBlocks.RoomsEntryButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun roomsEntryInvokesNavigationOnce() {
        var navigationCalls = 0

        composeRule.setContent {
            RoomsEntryButton {
                navigationCalls += 1
            }
        }

        composeRule
            .onNodeWithTag(HOME_ROOMS_BUTTON_TAG)
            .assertExists()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, navigationCalls)
        }
    }
}
