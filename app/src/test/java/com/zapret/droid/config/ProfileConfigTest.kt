package com.zapret.droid.config

import com.zapret.droid.strategies.StrategyPresets
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileConfigTest {
    @Test
    fun generatedProfilesValidateForEveryBundledStrategy() {
        StrategyPresets.ALL.forEach { strategy ->
            val result = ProfileConfig.validate(ProfileConfig.generate(strategy))

            assertTrue("${strategy.id}: ${result.errors}", result.isValid)
            assertTrue(result.strategy === strategy)
        }
    }

    @Test
    fun rejectsMalformedJsonAndUnknownStrategies() {
        assertFalse(ProfileConfig.validate("{").isValid)

        val unknownStrategy = JSONObject(ProfileConfig.generate(StrategyPresets.GENERAL))
            .put("strategy_id", "not-a-bundled-profile")

        assertFalse(ProfileConfig.validate(unknownStrategy.toString()).isValid)
    }

    @Test
    fun rejectsChangedStrategyValuesAndUnknownFields() {
        val changedRepeats = JSONObject(ProfileConfig.generate(StrategyPresets.GENERAL))
            .put("repeats", 99)
        val withExtraField = JSONObject(ProfileConfig.generate(StrategyPresets.GENERAL))
            .put("shell_command", "ignored")

        assertFalse(ProfileConfig.validate(changedRepeats.toString()).isValid)
        assertFalse(ProfileConfig.validate(withExtraField.toString()).isValid)
    }
}
