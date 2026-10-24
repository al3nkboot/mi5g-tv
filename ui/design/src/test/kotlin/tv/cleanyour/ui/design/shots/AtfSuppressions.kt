package tv.cleanyour.ui.design.shots

import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import org.hamcrest.Matcher
import org.hamcrest.Matchers

// Nothing is suppressed. A suppression here needs a reason next to it.
object AtfSuppressions {
    val matcher: Matcher<in AccessibilityViewCheckResult> = Matchers.not(Matchers.anything())
}
