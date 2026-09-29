package com.dsh.prefixblocker

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * 来电筛选服务：Android 7.0 (API 24) 起系统会在来电响铃前把号码交给本服务，
 * 由本服务决定放行、拒接或静音。
 *
 * 需要用户把本应用授权为系统的“来电显示和骚扰拦截 / Caller ID & spam”应用，
 * 否则系统根本不会调用这里。
 */
class BlockerScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        // 无论内部发生什么，都必须且只能调用一次 respondToCall，否则系统会一直等待。
        val response = try {
            buildResponse(details)
        } catch (t: Throwable) {
            allowResponse()
        }
        respondToCall(details, response)
    }

    private fun buildResponse(details: Call.Details): CallScreeningService.CallResponse {
        // 只筛选来电；去电、已接通的通话不处理。
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return allowResponse()

        val settings = SettingsStore(this)
        if (!settings.screeningEnabled) return allowResponse()

        val rawNumber = details.handle?.schemeSpecificPart
        val engine = RuleEngine(RuleStore(this).load(), settings.blockHiddenNumbers)
        val decision = engine.decide(rawNumber)
        if (decision !is ScreeningDecision.Deny) return allowResponse()

        BlockLogStore(this).append(
            BlockLogEntry(
                number = decision.number,
                pattern = decision.rule.pattern.ifEmpty { "(隐藏号码)" },
                action = decision.action.name,
                timeMillis = System.currentTimeMillis()
            )
        )

        // 测试模式：只记录，不真正拦截。
        if (settings.logOnlyMode) return allowResponse()

        return if (decision.action == RuleAction.SILENCE) silenceResponse() else blockResponse()
    }

    /** 放行。注意：disallowCall 为 false 时，其余四项必须都是 false，否则系统会抛异常。 */
    private fun allowResponse(): CallScreeningService.CallResponse = CallScreeningService.CallResponse.Builder()
        .setDisallowCall(false)
        .setRejectCall(false)
        .setSilenceCall(false)
        .setSkipCallLog(false)
        .setSkipNotification(false)
        .build()

    /** 直接拒接：对方听到忙音，本机通话记录里会留下一条已拦截记录。 */
    private fun blockResponse(): CallScreeningService.CallResponse = CallScreeningService.CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(true)
        .setSilenceCall(false)
        .setSkipCallLog(false)
        .setSkipNotification(false)
        .build()

    /** 静音拦截：不响铃、不弹通知，但仍然记入通话记录。 */
    private fun silenceResponse(): CallScreeningService.CallResponse = CallScreeningService.CallResponse.Builder()
        .setDisallowCall(true)
        .setRejectCall(false)
        .setSilenceCall(true)
        .setSkipCallLog(false)
        .setSkipNotification(true)
        .build()
}
