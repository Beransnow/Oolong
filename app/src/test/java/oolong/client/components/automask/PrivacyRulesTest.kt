package oolong.client.components.automask

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRulesTest {
    /** A line of [text] at row [row], each character 10 wide from [left]. */
    private fun line(text: String, row: Int = 0, left: Float = 0f) =
        ReadLine(text, text.indices.map { (left + it * 10f)..(left + it * 10f + 10f) }, left, row * 40f, left + text.length * 10f, row * 40f + 30f)

    private fun found(vararg lines: ReadLine) = PrivacyRules.find(lines.toList()).map { it.kind to it.text }

    @Test
    fun formsGiveAwayTheirKind() {
        assertEquals(listOf(PrivateKind.Phone to "138 0013 8000"), found(line("我的手机号是 138 0013 8000，有事打我")))
        assertEquals(listOf(PrivateKind.Phone to "13912345678"), found(line("快递电话13912345678，地址就不发了")))
        assertEquals(listOf(PrivateKind.Email to "someone.name@example.com"), found(line("邮箱 someone.name@example.com 收件")))
        assertEquals(listOf(PrivateKind.IdCard to "11010519491231002X"), found(line("身份证 11010519491231002X 拍照别发群里")))
        assertEquals(listOf(PrivateKind.Plate to "京A·12345"), found(line("车停在京A·12345旁边")))
        assertEquals(listOf(PrivateKind.Plate to "粤BD12345"), found(line("新车粤BD12345")))
        assertEquals(listOf(PrivateKind.Plate to "沪A1234学"), found(line("教练车沪A1234学")))
    }

    @Test
    fun cardsCheckOutOrAreLabelled() {
        // Passes the check digit
        assertEquals(listOf(PrivateKind.BankCard to "6222021234567890128"), found(line("转到 6222021234567890128 这张")))
        // Does not, but is called a card
        assertEquals(listOf(PrivateKind.BankCard to "6222 0212 3456 7890 123"), found(line("卡号 6222 0212 3456 7890 123 转账用")))
        // Neither: an order number
        assertTrue(found(line("订单号 2026110312345678 已发货")).isEmpty())
        // An ID card number that does not check out is not one
        assertTrue(found(line("编号 110105194912310021")).none { it.first == PrivateKind.IdCard })
    }

    @Test
    fun plainNumbersStayAlone() {
        assertTrue(found(line("今天 15:48 吃了 3 碗饭，价格 128 元")).isEmpty())
        assertTrue(found(line("楼层 123456789 回复")).isEmpty())
    }

    @Test
    fun labelsOnTheSameLine() {
        assertEquals(listOf(PrivateKind.QQ to "123456789"), found(line("我QQ号：123456789 平时都在线")))
        assertEquals(
            listOf(PrivateKind.WeChat to "wxid_abc123", PrivateKind.WeChat to "Oolong_2026"),
            found(line("加我微信 wxid_abc123 或者 vx: Oolong_2026")),
        )
        assertEquals(listOf(PrivateKind.Code to "482913"), found(line("您的验证码为482913，5分钟内有效")))
    }

    @Test
    fun labelsWithTheirDetailBesideOrBelow() {
        // A settings row: the label left, the detail right
        assertEquals(listOf(PrivateKind.WeChat to "oolong_fan"), found(line("微信号", row = 0), line("oolong_fan", row = 0, left = 600f)))
        // The label above, the detail below
        assertEquals(listOf(PrivateKind.QQ to "987654321"), found(line("QQ号码", row = 0), line("987654321", row = 1)))
        // A detail that does not look like one is left
        assertTrue(found(line("微信号", row = 0), line("未设置", row = 0, left = 600f)).isEmpty())
    }

    @Test
    fun readingSlipsBetweenDigits() {
        assertEquals(listOf(PrivateKind.Phone to "1380O138000"), found(line("电话1380O138000")))
    }
}
