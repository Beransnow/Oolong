package com.huanchengfly.tieba.post.utils

import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumString
import com.huanchengfly.tieba.post.utils.StringUtil.getShortNumeralString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "zh-rCN")
class ShortNumberTest {
    @Test
    fun `running text counts in 万 and 亿`() {
        assertEquals("9999", 9_999L.getShortNumString())
        assertEquals("1.2万", 12_345L.getShortNumString())
        assertEquals("12万", 123_456L.getShortNumString())
        assertEquals("1.2亿", 123_456_789L.getShortNumString())
    }

    @Test
    fun `the numeral face counts in W, never KW`() {
        assertEquals("9999", 9_999L.getShortNumeralString())
        assertEquals("1.2W", 12_345L.getShortNumeralString())
        assertEquals("999.9W", 9_999_999L.getShortNumeralString())
        assertEquals("1234W", 12_345_678L.getShortNumeralString())
    }
}
