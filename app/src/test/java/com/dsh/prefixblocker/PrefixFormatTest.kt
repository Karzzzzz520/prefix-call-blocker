package com.dsh.prefixblocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证「同一个号码的不同投递格式下，规则到底该怎么写」。
 *
 * 国内来电号码可能以这几种形式到达，取决于运营商和是否国际呼入：
 *
 *   18700138000         最常见：运营商直接给 11 位
 *   +8618700138000      带国际前缀
 *   8618700138000       只有国家码、没有 +
 *   008618700138000     00 开头的国际前缀写法
 *
 * 这里逐条验证 `187` / `+86187` / `86187` 三种写法的实际覆盖范围，
 * 结论是：**只写 `187` 才能全覆盖**。
 */
class PrefixFormatTest {

    private fun isBlocked(pattern: String, number: String): Boolean {
        val engine = RuleEngine(
            listOf(
                CallRule(
                    id = 1L,
                    pattern = pattern,
                    matchType = MatchType.PREFIX,
                    action = RuleAction.BLOCK,
                    enabled = true
                )
            )
        )
        return engine.decide(number) is ScreeningDecision.Deny
    }

    @Test
    fun ruleWithoutCountryCode_coversAllDeliveryFormats() {
        // 写 "187" 是唯一能覆盖全部四种投递格式的写法
        assertTrue("11 位国内格式", isBlocked("187", "18700138000"))
        assertTrue("带 +86", isBlocked("187", "+8618700138000"))
        assertTrue("裸 86 前缀", isBlocked("187", "8618700138000"))
        assertTrue("0086 前缀", isBlocked("187", "008618700138000"))
    }

    @Test
    fun ruleWithPlusCountryCode_missesTheCommonDomesticFormat() {
        // "+86187" 只在国际格式下命中；国内最常见的 11 位格式会漏掉
        assertTrue("带 +86 时命中", isBlocked("+86187", "+8618700138000"))
        assertFalse("11 位国内格式漏掉", isBlocked("+86187", "18700138000"))
        assertFalse("裸 86 前缀也漏掉", isBlocked("+86187", "8618700138000"))
    }

    @Test
    fun ruleWithBareCountryCode_alsoMissesTheDomesticFormat() {
        // "86187" 能命中裸 86 前缀（以及通过去掉 + 命中 +86 格式），但同样漏掉 11 位格式
        assertTrue("裸 86 前缀命中", isBlocked("86187", "8618700138000"))
        assertTrue("带 +86 通过去 + 形式命中", isBlocked("86187", "+8618700138000"))
        assertFalse("11 位国内格式漏掉", isBlocked("86187", "18700138000"))
    }

    @Test
    fun ruleDoesNotOverreachIntoOtherNumberRanges() {
        assertFalse("138 号段", isBlocked("187", "13800138000"))
        assertFalse("北京座机", isBlocked("187", "01012345678"))
        assertFalse("银行短号", isBlocked("187", "95588"))
        assertFalse("400 客服号", isBlocked("187", "4001234567"))
    }

    @Test
    fun knownTradeOff_usNumbersBeginningWith187AreAlsoBlocked() {
        // 已知副作用：规则 "187" 会顺带命中 +1 870 这类美国号码
        // （去掉 + 后是 18705551234，以 187 开头）。
        // 如果你会接到美国 870 区号的电话，需要再加一条白名单放行。
        assertTrue(isBlocked("187", "+18705551234"))
    }

    @Test
    fun patternIsCleanedTheSameWayTheUiDoesIt() {
        // 界面上不管你怎么输入，保存前都会被清理成纯号码
        assertEquals("187", PhoneNumberUtils.cleanPattern("187", MatchType.PREFIX))
        assertEquals("187", PhoneNumberUtils.cleanPattern(" 187 ", MatchType.PREFIX))
        assertEquals("187", PhoneNumberUtils.cleanPattern("1-8-7", MatchType.PREFIX))
        assertEquals("+86187", PhoneNumberUtils.cleanPattern("+86 187", MatchType.PREFIX))
        assertEquals("86187", PhoneNumberUtils.cleanPattern("86 187", MatchType.PREFIX))
    }
}
