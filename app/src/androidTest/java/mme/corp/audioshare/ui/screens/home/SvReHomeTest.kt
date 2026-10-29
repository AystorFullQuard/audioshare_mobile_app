package mme.corp.audioshare.ui.screens.home

import mme.corp.audioshare.testing.AwakeUnlockedDeviceRule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mme.corp.audioshare.ui.screens.home.uiBlocks.HOME_ROOMS_BUTTON_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SvReHomeTest {
    private val rule = createComposeRule()

    @get:Rule
    val uiTestRules: TestRule = RuleChain.outerRule(AwakeUnlockedDeviceRule()).around(rule)

    @Test
    fun guestAvatarOnlyTriggersLoginWhenClicked() {
        var loginCalls = 0
        rule.setContent {
            SvReHome(
                signedIn = false, rooms = emptyList(),
                onNavigateRooms = {}, onAvatarClick = { loginCalls++ })
        }
        rule.onNodeWithTag("home_profile_avatar").performClick()
        rule.runOnIdle { assertEquals(1, loginCalls) }
    }

    @Test
    fun roomsSectionUsesRealNavigationAndNeverDisplaysFakeHistory() {
        var navigations = 0
        rule.setContent {
            SvReHome(signedIn = true, rooms = emptyList(), onNavigateRooms = { navigations++ })
        }
        rule.onNodeWithText("SvRe").assertExists()
        rule.onNodeWithText("My Rooms").assertExists()
        rule.onNodeWithText("Good evening!").assertDoesNotExist()
        rule.onNodeWithText("Your recent sessions will appear here.").assertExists()
        rule.onNodeWithTag(HOME_ROOMS_BUTTON_TAG).performClick()
        rule.runOnIdle { assertEquals(1, navigations) }
    }
}

