package oolong.client.components.automask

/** A kind of private detail that hiding everything at once looks for. */
enum class PrivateKind { Phone, IdCard, BankCard, Email, Plate, QQ, WeChat, Code }

/**
 * A line of text as read: its [text], and for each of its characters the span it covers across
 * the line, left to right; [top] and [bottom] bound the line.
 */
class ReadLine(
    val text: String,
    val spans: List<ClosedFloatingPointRange<Float>>,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** The characters as read, one for each of [spans]: some take two places in [text], as a rare character or an emoji. */
    val chars: List<String> = text.codePoints().toArray().map { String(Character.toChars(it)) },
) {
    val height: Float get() = bottom - top

    /** The span of the character at [offset] in [text]. */
    fun spanAt(offset: Int): ClosedFloatingPointRange<Float> {
        var at = 0
        for ((i, char) in chars.withIndex()) {
            at += char.length
            if (offset < at) return spans[i]
        }
        return spans.last()
    }
}

/** A private detail found: its [kind], and the part of [line] it takes, characters [start] until [end]. */
class PrivateMatch(val kind: PrivateKind, val line: ReadLine, val start: Int, val end: Int) {
    val text: String get() = line.text.substring(start, end)
}

/**
 * Finds private details in lines of text read off a picture: what its form gives away (mobile
 * numbers, landlines with their area code set off, numbers from abroad, ID card and bank card
 * numbers that check out, email addresses, number plates), and what only its label does (a
 * landline without its area code, QQ and WeChat numbers, verification codes), the label before
 * it on the same line, or its own line to the right or below.
 */
object PrivacyRules {
    private val Forms: List<Pair<PrivateKind, Regex>> = listOf(
        PrivateKind.Email to Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}"""),
        PrivateKind.IdCard to Regex("""(?<![\dXx])\d{6}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?![\dXx])"""),
        PrivateKind.BankCard to Regex("""(?<!\d)(?:\d{4}[ -]?){3}\d{4}(?:[ -]?\d{1,3})?(?![\dXx])"""),
        // A mobile number, split anywhere, the country before it or not
        PrivateKind.Phone to Regex("""(?<!\d)(?:(?:\+|00)86[- ]?|\(\+?86\)[- ]?)?1[3-9]\d(?:[- ]?\d){8}(?!\d)"""),
        // A landline with its area code set off, an extension after it or not
        PrivateKind.Phone to Regex(
            """(?<!\d)(?:(?:\+|00)86[- ]?)?(?:\((?:010|02\d|0[3-9]\d{2})\)[- ]?|(?:010|02\d|0[3-9]\d{2})[- ])[2-9]\d{2,3}[- ]?\d{4}(?:\s*(?:转|分机|ext\.?|-)\s*\d{1,5})?(?!\d)""",
        ),
        // A number from abroad, its country after a plus
        PrivateKind.Phone to Regex("""\+[1-9]\d{0,2}(?:[- .]?\(?\d+\)?)+"""),
        PrivateKind.Plate to Regex(
            """(?<![A-Za-z0-9])(?:[$Provinces][A-HJ-NP-Z][·•.\s]?(?:[A-HJ-NP-Z0-9]{5,6}|[A-HJ-NP-Z0-9]{4}[挂学警港澳领试超])|使\d{6}|\d{6}使|WJ[$Provinces]?\d{4}[\dA-Z])(?![A-Za-z0-9])""",
        ),
    )

    /** A kind found by its label, and what the detail after the label looks like. */
    private class Labelled(val kind: PrivateKind, label: String, value: String) {
        /** The label with its detail after it on the same line, the detail as group 1. */
        val inLine = Regex("""(?:$label)\s*[:：]?\s*($value)(?![-_A-Za-z0-9])""")

        /** The label ending its line, its detail elsewhere. */
        val ending = Regex("""(?:$label)\s*[:：]?\s*$""")

        /** The detail alone. */
        val alone = Regex("""^\s*($value)\s*$""")
    }

    private val Labels = listOf(
        Labelled(
            PrivateKind.Phone,
            """(?:电话|手机|座机|固话|联系方式|Tel|TEL|tel|Phone|phone|Mobile|mobile)\s*(?:号码?)?""",
            """0\d{9,11}|[2-9]\d{6,7}""",
        ),
        Labelled(PrivateKind.QQ, """(?:QQ|qq|Qq|扣扣|Q号)\s*(?:号码?|账号)?""", """[1-9]\d{4,10}"""),
        Labelled(PrivateKind.WeChat, """(?:微信|wx|WX|vx|VX|V信|v信|weixin|wechat|WeChat)\s*(?:号|ID|id)?""", """[A-Za-z][-_A-Za-z0-9]{5,19}|1[3-9]\d{9}"""),
        Labelled(PrivateKind.Code, """(?:验证码|校验码|动态码|动态密码)\s*(?:是|为)?""", """\d{4,8}"""),
    )

    fun find(lines: List<ReadLine>): List<PrivateMatch> {
        val found = mutableListOf<PrivateMatch>()
        for (line in lines) {
            val text = normalised(line.text)
            val taken = mutableListOf<IntRange>()
            fun take(kind: PrivateKind, start: Int, end: Int) {
                if (taken.any { start < it.last + 1 && it.first < end }) return
                taken += start until end
                found += PrivateMatch(kind, line, start, end)
            }
            for ((kind, form) in Forms) {
                for (m in form.findAll(text)) {
                    val digits = m.value.filter { it.isDigit() || it == 'X' || it == 'x' }
                    val ok = when (kind) {
                        PrivateKind.IdCard -> idChecks(digits)
                        PrivateKind.BankCard -> luhn(digits) || CardLabel.containsMatchIn(text.substring(0, m.range.first).takeLast(8))
                        PrivateKind.Phone -> m.value[0] != '+' || digits.length in 8..15
                        else -> true
                    }
                    if (ok) take(kind, m.range.first, m.range.last + 1)
                }
            }
            // A label with its detail after it on the same line
            for (labelled in Labels) {
                for (m in labelled.inLine.findAll(text)) {
                    val detail = m.groups[1] ?: continue
                    take(labelled.kind, detail.range.first, detail.range.last + 1)
                }
            }
        }
        // A label ending its line, its detail on a line of its own beside or below it
        for (line in lines) {
            for (labelled in Labels) {
                if (!labelled.ending.containsMatchIn(normalised(line.text))) continue
                val detail = valueLine(line, lines) ?: continue
                val m = labelled.alone.find(normalised(detail.text))?.groups?.get(1) ?: continue
                if (found.none { it.line === detail }) found += PrivateMatch(labelled.kind, detail, m.range.first, m.range.last + 1)
            }
        }
        return found
    }

    /** The line a label ending [line] names: to its right on the same row, else just below its start. */
    private fun valueLine(line: ReadLine, lines: List<ReadLine>): ReadLine? {
        val right = lines.filter { it !== line && it.left >= line.right - line.height * 0.2f && overlap(it, line) >= 0.5f * minOf(it.height, line.height) }
            .minByOrNull { it.left }
        if (right != null) return right
        return lines.filter {
            it !== line && it.top >= line.bottom - line.height * 0.2f && it.top - line.bottom <= line.height &&
                kotlin.math.abs(it.left - line.left) <= line.height * 1.5f
        }.minByOrNull { it.top }
    }

    private fun overlap(a: ReadLine, b: ReadLine) = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)

    private val CardLabel = Regex("""(?:卡号|银行卡|储蓄卡|信用卡|借记卡|账号)\s*[:：]?\s*$""")

    /**
     * Full-width letters and digits as half-width, and letters a reading often takes digits for
     * read back as digits when between digits; the length kept, so spans still line up.
     */
    internal fun normalised(text: String): String {
        val half = text.map { if (it.code in 0xFF01..0xFF5E) (it.code - 0xFEE0).toChar() else it }.toCharArray()
        for (i in half.indices) {
            val before = half.getOrNull(i - 1)?.isDigit() == true
            val after = half.getOrNull(i + 1)?.isDigit() == true
            if (before && after) {
                half[i] = when (half[i]) {
                    'O', 'o' -> '0'
                    'l', 'I', '|' -> '1'
                    else -> half[i]
                }
            }
        }
        return String(half)
    }

    /** Whether a bank card number's last digit checks out, as every card's does. */
    internal fun luhn(digits: String): Boolean {
        if (digits.length < 12 || digits.any { !it.isDigit() }) return false
        var total = 0
        for ((i, c) in digits.reversed().withIndex()) {
            var v = (c - '0') * if (i % 2 == 1) 2 else 1
            if (v > 9) v -= 9
            total += v
        }
        return total % 10 == 0
    }

    /** Whether an ID card number's last character checks out, as every card's does. */
    internal fun idChecks(id: String): Boolean {
        if (id.length != 18) return false
        val weights = intArrayOf(7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2)
        var sum = 0
        for (i in 0 until 17) {
            if (!id[i].isDigit()) return false
            sum += (id[i] - '0') * weights[i]
        }
        return "10X98765432"[sum % 11] == id[17].uppercaseChar()
    }

    private const val Provinces = "京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼"
}
