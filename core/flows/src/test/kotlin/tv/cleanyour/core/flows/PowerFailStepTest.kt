package tv.cleanyour.core.flows

import kotlin.test.Test
import kotlin.test.assertEquals

class PowerFailStepTest {
    @Test fun failingStepIsNamed() {
        assertEquals(PowerFailStep.NO_APK, PowerFailStep.of(apkFound = false, installed = false))
        assertEquals(PowerFailStep.INSTALL, PowerFailStep.of(apkFound = true, installed = false))
        assertEquals(PowerFailStep.LAUNCH, PowerFailStep.of(apkFound = true, installed = true))
    }
}
