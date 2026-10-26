package tv.cleanyour.app

import android.app.Application
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UpdateNotificationIntentTest {
    @Test fun reusedActivityReceivesEveryUpdateNotificationRequest() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val activity = controller.get()
        assertEquals(0, activity.updateOpenRequest)
        controller.newIntent(Intent().putExtra(AndroidAppUpdateController.EXTRA_OPEN_UPDATES, true))
        assertEquals(1, activity.updateOpenRequest)
        controller.newIntent(Intent().putExtra(AndroidAppUpdateController.EXTRA_OPEN_UPDATES, true))
        assertEquals(2, activity.updateOpenRequest)
        controller.newIntent(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/untrusted")))
        assertEquals(2, activity.updateOpenRequest)
    }
}
