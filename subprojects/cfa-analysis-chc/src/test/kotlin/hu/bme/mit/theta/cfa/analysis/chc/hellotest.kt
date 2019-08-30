package hu.bme.mit.theta.cfa.analysis.chc

import org.junit.Assert
import org.junit.Test

class HelloTest {

    @Test
    fun helloTest() {
        Assert.assertEquals("Hello does not match", "Helló", hello())
    }
}