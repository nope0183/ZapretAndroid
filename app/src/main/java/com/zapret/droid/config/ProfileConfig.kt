package com.zapret.droid.config

import com.zapret.droid.strategies.Strategy
import com.zapret.droid.strategies.StrategyPresets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

data class ProfileCheckResult(
    val strategy: Strategy?,
    val errors: List<String>
) {
    val isValid: Boolean
        get() = strategy != null && errors.isEmpty()
}

object ProfileConfig {
    fun generate(strategy: Strategy): String = toJson(strategy).toString(2)

    fun validate(contents: String): ProfileCheckResult {
        val actual = try {
            JSONObject(contents)
        } catch (error: JSONException) {
            return ProfileCheckResult(null, listOf("Некорректный JSON: ${error.message}"))
        }

        val strategyId = try {
            actual.getString("strategy_id")
        } catch (error: JSONException) {
            return ProfileCheckResult(null, listOf("В профиле отсутствует строковый strategy_id."))
        }
        val strategy = StrategyPresets.ALL.firstOrNull { it.id == strategyId }
            ?: return ProfileCheckResult(null, listOf("Неизвестная стратегия: $strategyId"))

        val expected = toJson(strategy)
        val expectedKeys = expected.keys().asSequence().toSet()
        val actualKeys = actual.keys().asSequence().toSet()
        val errors = mutableListOf<String>()

        (expectedKeys - actualKeys).sorted().forEach { errors.add("Отсутствует поле: $it") }
        (actualKeys - expectedKeys).sorted().forEach { errors.add("Неизвестное поле: $it") }
        (expectedKeys intersect actualKeys).sorted().forEach { key ->
            if (!jsonValuesEqual(actual.get(key), expected.get(key))) {
                errors.add("Значение поля «$key» не соответствует стратегии $strategyId.")
            }
        }

        return ProfileCheckResult(strategy, errors)
    }

    private fun toJson(strategy: Strategy): JSONObject = JSONObject()
        .put("format", "zapret-android-profile")
        .put("schema_version", 1)
        .put("strategy_id", strategy.id)
        .put("name", strategy.name)
        .put("tcp_ports", JSONArray(strategy.tcpPorts))
        .put("udp_ports", JSONArray(strategy.udpPorts))
        .put("dpi_desync", JSONArray(strategy.dpiDesync.map { it.name.lowercase(Locale.ROOT) }))
        .put("repeats", strategy.dpiDesyncRepeats)
        .put("split_pos", strategy.splitPos.name.lowercase(Locale.ROOT))
        .put("seqovl", strategy.seqovl)
        .put("seqovl_pattern", strategy.seqovlPattern.name.lowercase(Locale.ROOT))
        .put("fooling", JSONArray(strategy.fooling.map { it.name.lowercase(Locale.ROOT) }))
        .put("badseq_increment", strategy.badseqIncrement)
        .put("fake_ttl", strategy.fakeTtl)
        .put("fake_data", strategy.fakeData.name.lowercase(Locale.ROOT))
        .put("fake_tls_mod", strategy.fakeTlsMod.name.lowercase(Locale.ROOT))
        .put("fake_tls_sni", strategy.fakeTlsSni)
        .put("any_protocol", strategy.anyProtocol)
        .put("desync_cutoff", strategy.desyncCutoff.name.lowercase(Locale.ROOT))
        .put("ip_id_zero", strategy.ipIdZero)
        .put("filter_l7", JSONArray(strategy.filterL7.map { it.name.lowercase(Locale.ROOT) }))
        .put("services", JSONArray(strategy.services.map { it.name.lowercase(Locale.ROOT) }))
        .put("telegram_enabled", strategy.telegramEnabled)

    private fun jsonValuesEqual(left: Any, right: Any): Boolean = when {
        left is JSONArray && right is JSONArray -> {
            if (left.length() != right.length()) {
                false
            } else {
                (0 until left.length()).all { index ->
                    jsonValuesEqual(left.get(index), right.get(index))
                }
            }
        }
        left is Number && right is Number ->
            left.javaClass == right.javaClass && left.toString() == right.toString()
        else -> left == right
    }
}
