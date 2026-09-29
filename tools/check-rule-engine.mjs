/**
 * 开发辅助脚本：在没有 Android SDK / Kotlin 编译器的机器上，验证 RuleEngine 的算法行为。
 *
 * 它按 1:1 复刻了 app/src/main/java/com/dsh/prefixblocker/PhoneNumberUtils.kt 与 RuleEngine.kt
 * 的逻辑，并跑同一组用例（与 RuleEngineTest.kt 对应）。
 *
 * 用法： node tools/check-rule-engine.mjs
 *
 * 这个脚本只是算法的可执行说明书，App 真正的实现仍然是 Kotlin 那一份。
 */

const MatchType = { PREFIX: 'PREFIX', CONTAINS: 'CONTAINS', REGEX: 'REGEX' }
const RuleAction = { BLOCK: 'BLOCK', SILENCE: 'SILENCE', ALLOW: 'ALLOW' }

const COUNTRY_CODES = [
  '852', '853', '855', '856', '880', '886', '850',
  '86', '81', '82', '84', '91', '92', '93', '94', '95', '98', '90',
  '60', '61', '62', '63', '64', '65', '66',
  '20', '27', '30', '31', '32', '33', '34', '36', '39',
  '40', '41', '43', '44', '45', '46', '47', '48', '49',
  '51', '52', '53', '54', '55', '56', '57', '58',
  '1', '7'
].sort((a, b) => b.length - a.length)

function canonical(raw) {
  if (raw === null || raw === undefined) return ''
  const text = String(raw).trim()
  const digits = text.replace(/\D/g, '')
  if (digits.length === 0) return ''

  let international = text.startsWith('+')
  let body = digits
  if (body.startsWith('00')) {
    body = body.substring(2)
    international = true
  }
  if (body.length === 0) return ''
  return international ? `+${body}` : body
}

function variants(raw) {
  const c = canonical(raw)
  if (c.length === 0) return []
  const result = [{ value: c, strippedCountryCode: false }]

  const withoutPlus = c.startsWith('+') ? c.substring(1) : c
  if (withoutPlus !== c) result.push({ value: withoutPlus, strippedCountryCode: false })

  if (c.startsWith('+')) {
    const code = COUNTRY_CODES.find((it) => withoutPlus.startsWith(it) && withoutPlus.length > it.length)
    if (code) result.push({ value: withoutPlus.substring(code.length), strippedCountryCode: true })
  } else if (withoutPlus.length > 11) {
    result.push({ value: withoutPlus.slice(-11), strippedCountryCode: true })
  }
  return result
}

function cleanPattern(raw, matchType) {
  const text = raw === null || raw === undefined ? '' : String(raw).trim()
  if (text.length === 0) return ''
  if (matchType === MatchType.REGEX) return text

  const international = text.startsWith('+')
  const digits = text.replace(/\D/g, '')
  if (digits.length === 0) return ''
  if (international) return `+${digits}`
  if (digits.startsWith('00')) return `+${digits.substring(2)}`
  return digits
}

const HIDDEN_NUMBER_RULE = {
  id: -1, pattern: '', matchType: MatchType.PREFIX, action: RuleAction.BLOCK, enabled: true, note: '隐藏号码'
}

class RuleEngine {
  constructor(rules, blockHiddenNumbers = false) {
    this.rules = rules
    this.blockHiddenNumbers = blockHiddenNumbers
    this.regexCache = new Map()
  }

  decide(rawNumber) {
    const candidates = variants(rawNumber)
    const display = rawNumber === null || rawNumber === undefined ? '' : String(rawNumber).trim()

    if (candidates.length === 0) {
      return this.blockHiddenNumbers
        ? { kind: 'deny', rule: HIDDEN_NUMBER_RULE, action: RuleAction.BLOCK, number: '未知号码' }
        : { kind: 'allow' }
    }

    for (const rule of this.rules) {
      if (!rule.enabled || rule.action !== RuleAction.ALLOW) continue
      if (this.matches(rule, candidates)) return { kind: 'allow' }
    }

    let best = null
    for (const rule of this.rules) {
      if (!rule.enabled || rule.action === RuleAction.ALLOW) continue
      if (!this.matches(rule, candidates)) continue
      if (best === null || rule.pattern.length > best.pattern.length) best = rule
    }

    if (best === null) return { kind: 'allow' }
    return { kind: 'deny', rule: best, action: best.action, number: display || '未知号码' }
  }

  matches(rule, candidates) {
    const pattern = rule.pattern
    if (!pattern || pattern.trim().length === 0) return false

    for (const candidate of candidates) {
      if (candidate.strippedCountryCode && pattern.length < 2) continue
      if (rule.matchType === MatchType.PREFIX && candidate.value.startsWith(pattern)) return true
      if (rule.matchType === MatchType.CONTAINS && candidate.value.includes(pattern)) return true
      if (rule.matchType === MatchType.REGEX && this.compile(pattern)?.test(candidate.value)) return true
    }
    return false
  }

  compile(pattern) {
    if (this.regexCache.has(pattern)) return this.regexCache.get(pattern)
    let compiled = null
    try {
      compiled = new RegExp(pattern)
    } catch (t) {
      compiled = null
    }
    this.regexCache.set(pattern, compiled)
    return compiled
  }
}

// ------------------------------------------------------------------ 测试向量

function rule(pattern, matchType, action, id = 1) {
  return { id, pattern, matchType, action, enabled: true, note: '' }
}

const cases = [
  ['归一化：+86 带空格', canonical('+86 138 0013 8000'), '+8613800138000'],
  ['归一化：00 国际前缀', canonical('008613800138000'), '+8613800138000'],
  ['归一化：座机带横线', canonical('010-1234 5678'), '01012345678'],
  ['归一化：空号码', canonical(null), ''],
  ['归一化：文字号码', canonical('未知号码'), ''],
  ['候选形式包含国内号码', variants('+8613800138000').map((v) => v.value).includes('13800138000'), true],
  ['候选形式包含去 + 形式', variants('+8613800138000').map((v) => v.value).includes('8613800138000'), true],

  ['前缀 400 拦截 4001234567', new RuleEngine([rule('400', MatchType.PREFIX, RuleAction.BLOCK)]).decide('4001234567').kind, 'deny'],
  ['前缀 400 拦截 400-123-4567', new RuleEngine([rule('400', MatchType.PREFIX, RuleAction.BLOCK)]).decide('400-123-4567').kind, 'deny'],
  ['前缀 400 放行 13900139000', new RuleEngine([rule('400', MatchType.PREFIX, RuleAction.BLOCK)]).decide('13900139000').kind, 'allow'],

  ['前缀 138 拦截 +8613800138000（去国家码）', new RuleEngine([rule('138', MatchType.PREFIX, RuleAction.BLOCK)]).decide('+8613800138000').kind, 'deny'],
  ['前缀 138 拦截 13800138000', new RuleEngine([rule('138', MatchType.PREFIX, RuleAction.BLOCK)]).decide('13800138000').kind, 'deny'],
  ['前缀 138 拦截 008613800138000', new RuleEngine([rule('138', MatchType.PREFIX, RuleAction.BLOCK)]).decide('008613800138000').kind, 'deny'],
  ['前缀 +86 拦截 +8613800138000', new RuleEngine([rule('+86', MatchType.PREFIX, RuleAction.BLOCK)]).decide('+8613800138000').kind, 'deny'],

  ['单字符规则 1 拦截 +12025550123', new RuleEngine([rule('1', MatchType.PREFIX, RuleAction.BLOCK)]).decide('+12025550123').kind, 'deny'],
  ['单字符规则 1 不能误伤 +8613123456789', new RuleEngine([rule('1', MatchType.PREFIX, RuleAction.BLOCK)]).decide('+8613123456789').kind, 'allow'],

  ['停用的规则不生效', new RuleEngine([{ ...rule('400', MatchType.PREFIX, RuleAction.BLOCK), enabled: false }]).decide('4001234567').kind, 'allow'],

  ['包含 5588 拦截 95588', new RuleEngine([rule('5588', MatchType.CONTAINS, RuleAction.BLOCK)]).decide('95588').kind, 'deny'],
  ['包含 5588 放行 95533', new RuleEngine([rule('5588', MatchType.CONTAINS, RuleAction.BLOCK)]).decide('95533').kind, 'allow'],

  ['正则手机号拦截 +8613800138000', new RuleEngine([rule('^1[3-9]\\d{9}$', MatchType.REGEX, RuleAction.BLOCK)]).decide('+8613800138000').kind, 'deny'],
  ['正则手机号放行 01012345678', new RuleEngine([rule('^1[3-9]\\d{9}$', MatchType.REGEX, RuleAction.BLOCK)]).decide('01012345678').kind, 'allow'],
  ['非法正则不会崩溃', new RuleEngine([rule('([unclosed', MatchType.REGEX, RuleAction.BLOCK)]).decide('4001234567').kind, 'allow'],

  ['白名单优先于拦截', new RuleEngine([
    rule('138', MatchType.PREFIX, RuleAction.BLOCK),
    rule('13800138000', MatchType.PREFIX, RuleAction.ALLOW, 2)
  ]).decide('+8613800138000').kind, 'allow'],

  ['更长的模式优先', new RuleEngine([
    rule('138', MatchType.PREFIX, RuleAction.BLOCK),
    rule('1380013', MatchType.PREFIX, RuleAction.SILENCE, 2)
  ]).decide('13800138000').action, RuleAction.SILENCE],

  ['隐藏号码默认放行', new RuleEngine([], false).decide(null).kind, 'allow'],
  ['开启开关后隐藏号码被拦截', new RuleEngine([], true).decide(null).kind, 'deny'],

  ['输入清理：+86 138 → +86138', cleanPattern('+86 138', MatchType.PREFIX), '+86138'],
  ['输入清理：0086 → +86', cleanPattern('0086', MatchType.PREFIX), '+86'],
  ['输入清理：正则原样保留', cleanPattern('^1[3-9]\\d{9}$', MatchType.REGEX), '^1[3-9]\\d{9}$']
]

let failed = 0
for (const [name, actual, expected] of cases) {
  const ok = actual === expected
  if (!ok) failed++
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${ok ? '' : `  →  期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`}`)
}
console.log(`\n${cases.length - failed}/${cases.length} 通过`)
process.exit(failed === 0 ? 0 : 1)
