package tv.mi5g.adb.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Spake2Test {
    @Test fun matchingPasswordsDeriveTheSameKey() {
        val password = "123456\u0000exported".toByteArray()
        val alice = Spake2(true, password)
        val bob = Spake2(false, password)
        assertArrayEquals(alice.process(bob.message), bob.process(alice.message))
    }

    @Test fun wrongPasswordDoesNotDeriveTheSameKey() {
        val alice = Spake2(true, "123456".toByteArray())
        val bob = Spake2(false, "654321".toByteArray())
        val a = alice.process(bob.message)!!
        val b = bob.process(alice.message)!!
        org.junit.Assert.assertFalse(a.contentEquals(b))
    }
}
