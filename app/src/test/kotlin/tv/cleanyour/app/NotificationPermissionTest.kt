package tv.cleanyour.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.cleanyour.core.flows.SetupActions
import tv.cleanyour.core.flows.SetupIntent
import tv.cleanyour.core.flows.Sheet

class NotificationPermissionTest {

    @Test fun asksOnlyOnAndroid13WhenNotGranted() {
        assertTrue(shouldAskPostNotifications(33, granted = false))
        assertTrue(shouldAskPostNotifications(35, granted = false))
        assertFalse(shouldAskPostNotifications(33, granted = true))
        assertFalse(shouldAskPostNotifications(32, granted = false))
    }

    @Test fun denialCallbackStillStartsTheClean() {
        val sent = mutableListOf<SetupIntent>()
        val inner = object : SetupActions {
            override fun intent(intent: SetupIntent) { sent += intent }
            override fun back(): Boolean = false
            override fun dismissCard() {}
        }
        val gated = NotificationGatedSetupActions(inner) {
            inner.intent(SetupIntent.NotificationPermissionSettled)
            inner.intent(SetupIntent.MakeItClean)
        }
        gated.intent(SetupIntent.MakeItClean)
        assertEquals(listOf(SetupIntent.NotificationPermissionSettled, SetupIntent.MakeItClean), sent)
    }

    @Test fun otherIntentsPassThrough() {
        val sent = mutableListOf<SetupIntent>()
        val inner = object : SetupActions {
            override fun intent(intent: SetupIntent) { sent += intent }
            override fun back(): Boolean = false
            override fun dismissCard() {}
        }
        val gated = NotificationGatedSetupActions(inner) { error("must not ask") }
        gated.intent(SetupIntent.Open(Sheet.CUSTOMISE))
        assertEquals(listOf(SetupIntent.Open(Sheet.CUSTOMISE)), sent)
    }
}
