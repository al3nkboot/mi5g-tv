package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateVersionTest {
    @Test fun stableVersionsCompareNumericallyRatherThanLexically() {
        assertTrue(SemanticVersion.isNewer("v1.10.0", "1.9.9"))
        assertTrue(SemanticVersion.isNewer("1.2.11", "1.2.9"))
        assertFalse(SemanticVersion.isNewer("1.4.9", "1.5.0"))
        assertFalse(SemanticVersion.isNewer("v1.5.0", "1.5.0"))
    }

    @Test fun semverPrereleasesSortBeforeStableAndByIdentifier() {
        assertTrue(SemanticVersion.isNewer("1.5.0", "1.5.0-rc.2"))
        assertTrue(SemanticVersion.isNewer("1.5.0-rc.3", "1.5.0-rc.2"))
        assertTrue(SemanticVersion.isNewer("1.5.0-rc.10", "1.5.0-rc.2"))
        assertTrue(SemanticVersion.isNewer("1.5.0-rc.99999999999999999999", "1.5.0-rc.10000000000000000000"))
        assertTrue(SemanticVersion.isNewer("1.5.0-rc.2", "1.5.0-rc.1"))
        assertFalse(SemanticVersion.isNewer("1.5.0-rc.1", "1.5.0"))
    }

    @Test fun buildMetadataDoesNotChangeVersionPrecedence() {
        assertFalse(SemanticVersion.isNewer("1.5.0+build.2", "1.5.0+build.1"))
    }

    @Test fun malformedOrOverflowingTagsNeverBecomeAnUpdate() {
        assertNull(SemanticVersion.parse("1.5"))
        assertNull(SemanticVersion.parse("01.5.0"))
        assertNull(SemanticVersion.parse("1.5.0-01"))
        assertFalse(SemanticVersion.isNewer("not-a-version", "1.0.0"))
        assertFalse(SemanticVersion.isNewer("999999999999999999999.0.0", "1.0.0"))
    }
}
