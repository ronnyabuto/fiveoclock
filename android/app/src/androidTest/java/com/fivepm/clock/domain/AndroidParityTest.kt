package com.fivepm.clock.domain

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidParityTest {
    @Test fun matchesTypeScriptOnDeviceTimezoneDatabase() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val zones = instrumentation.targetContext.assets.open("zones.json").bufferedReader().use { it.readText() }
        val fixtures = instrumentation.context.assets.open("parity-fixtures.json").bufferedReader().use { it.readText() }
        ParityChecks.verify(zones, fixtures)
    }
}
