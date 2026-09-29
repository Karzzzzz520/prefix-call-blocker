package com.dsh.prefixblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 匹配引擎的单元测试（纯 JVM，不需要设备）。
 *
 * 命令行运行： `gradlew :app:testDebugUnitTest`
 * Android Studio 里直接右键本文件 → Run。
 *
 * 注意：tools/check-rule-engine.mjs 里有一份等价的实现和同一组用例，
 * 用于在没有 Android SDK 的机器上快速验证算法行为。
 */
class RuleEngineTest {

    // ------------------------------------------------------------ 号码归一化

    @Test
    fun canonical_keepsInternationalPrefix() {
        assertEquals("+8613800138000", PhoneNumberUtils.canonical("+86 138 0013 8000"))
        assertEquals("+8613800138000", PhoneNumberUtils.canonical("008613800138000"))
        assertEquals("01012345678", PhoneNumberUtils.canonical("010-1234 5678"))
        assertEquals("", PhoneNumberUtils.canonical(null))
        assertEquals("", PhoneNumberUtils.canonical("未知号码"))
    }

    @Test
    fun variants_exposeNationalForm() {
        val variants = PhoneNumberUtils.variants("+8613800138000").map { it.value }
        assertTrue(variants.contains("+8613800138000"))
        assertTrue(variants.contains("8613800138000"))
        assertTrue(variants.contains("13800138000"))
    }

    // ------------------------------------------------------------ 前缀拦截

    @Test
    fun prefixRule_matchesVariousCallerIdFormats() {
        val rules = listOf(rule("400", MatchType.PREFIX, RuleAction.BLOCK))
        assertDenied(RuleEngine(rules).decide("4001234567"))
        assertDenied(RuleEngine(rules).decide("400-123-4567"))
        assertAllowed(RuleEngine(rules).decide("13900139000"))
    }

    @Test
    fun prefixRule_matchesMobilePrefixBehindCountryCode() {
        val rule = rule("138", MatchType.PREFIX, RuleAction.BLOCK)
        assertDenied(RuleEngine(listOf(rule)).decide("+8613800138000"))
        assertDenied(RuleEngine(listOf(rule)).decide("13800138000"))
        assertDenied(RuleEngine(listOf(rule)).decide("008613800138000"))
    }

    @Test
    fun prefixRule_matchesFullCountryCode() {
        val rule = rule("+86", MatchType.PREFIX, RuleAction.BLOCK)
        assertDenied(RuleEngine(listOf(rule)).decide("+8613800138000"))
    }

    @Test
    fun singleDigitRule_doesNotMatchStrippedNationalNumber() {
        // 规则 "1" 是给 +1 号码用的，绝不能误伤 +86 的国内号码
        val rule = rule("1", MatchType.PREFIX, RuleAction.BLOCK)
        val engine = RuleEngine(listOf(rule))
        assertDenied(engine.decide("+12025550123"))
        assertAllowed(engine.decide("+8613123456789"))
    }

    @Test
    fun disabledRule_isIgnored() {
        val rule = rule("400", MatchType.PREFIX, RuleAction.BLOCK).copy(enabled = false)
        assertAllowed(RuleEngine(listOf(rule)).decide("4001234567"))
    }

    // ------------------------------------------------------------ 其他匹配方式

    @Test
    fun containsRule_matchesInnerDigits() {
        val rule = rule("5588", MatchType.CONTAINS, RuleAction.BLOCK)
        assertDenied(RuleEngine(listOf(rule)).decide("95588"))
        assertAllowed(RuleEngine(listOf(rule)).decide("95533"))
    }

    @Test
    fun regexRule_matchesMobileNumbers() {
        val rule = rule("^1[3-9]\\d{9}$", MatchType.REGEX, RuleAction.BLOCK)
        val engine = RuleEngine(listOf(rule))
        assertDenied(engine.decide("+8613800138000"))
        assertDenied(engine.decide("13900139000"))
        assertAllowed(engine.decide("01012345678"))
    }

    @Test
    fun brokenRegex_neverMatchesAndDoesNotCrash() {
        val rule = rule("([unclosed", MatchType.REGEX, RuleAction.BLOCK)
        assertAllowed(RuleEngine(listOf(rule)).decide("4001234567"))
    }

    // ------------------------------------------------------------ 优先级

    @Test
    fun whitelistWinsOverBlock() {
        val rules = listOf(
            rule("138", MatchType.PREFIX, RuleAction.BLOCK),
            rule("13800138000", MatchType.PREFIX, RuleAction.ALLOW, id = 2L)
        )
        assertAllowed(RuleEngine(rules).decide("+8613800138000"))
    }

    @Test
    fun longestPatternWins() {
        val rules = listOf(
            rule("138", MatchType.PREFIX, RuleAction.BLOCK),
            rule("1380013", MatchType.PREFIX, RuleAction.SILENCE, id = 2L)
        )
        val decision = RuleEngine(rules).decide("13800138000")
        assertTrue(decision is ScreeningDecision.Deny)
        assertEquals(RuleAction.SILENCE, (decision as ScreeningDecision.Deny).action)
    }

    // ------------------------------------------------------------ 隐藏号码

    @Test
    fun hiddenNumberFollowsSetting() {
        assertAllowed(RuleEngine(emptyList(), blockHiddenNumbers = false).decide(null))
        assertAllowed(RuleEngine(emptyList(), blockHiddenNumbers = false).decide(""))
        assertDenied(RuleEngine(emptyList(), blockHiddenNumbers = true).decide(null))
    }

    // ------------------------------------------------------------ 辅助

    private fun rule(
        pattern: String,
        matchType: MatchType,
        action: RuleAction,
        id: Long = 1L
    ) = CallRule(id = id, pattern = pattern, matchType = matchType, action = action, enabled = true)

    private fun assertDenied(decision: ScreeningDecision) {
        assertTrue("期望被拦截，实际放行", decision is ScreeningDecision.Deny)
    }

    private fun assertAllowed(decision: ScreeningDecision) {
        assertTrue("期望放行，实际被拦截", decision is ScreeningDecision.Allow)
    }
}
