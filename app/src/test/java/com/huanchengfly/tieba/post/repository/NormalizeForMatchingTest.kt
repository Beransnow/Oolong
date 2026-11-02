package com.huanchengfly.tieba.post.repository

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeForMatchingTest {
    @Test
    fun `the quick way gives what the long way gives`() {
        val samples = listOf(
            "ＡＢＣ", "剧·透", "剧 透", "é", "é", "①", "ｶﾀｶﾅ", "ﬁ", "㈱", "😂", "豈", "𠀀",
            "が", "ゟ", "ヿ", "™", "Ⅻ", "〸", "…", "‥", "【原神】", "「剧透」", "Hello, World!", "第三章：主角其实没死！",
            "ｈｔｔｐ：／／ｅｘａｍｐｌｅ．ｃｏｍ", "ÀÉÎ", "Straße", "İ", "ǅ", "　全角空格",
        )
        samples.forEach { assertEquals(slowNormalizeForMatching(it), normalizeForMatching(it), it) }

        // Random text from the ranges the quick way takes, mixed with a few it doesn't
        val pools = listOf(' '..'~', '一'..'乀', 'ぁ'..'ゖ', 'ァ'..'ヾ', '！'..'～', '‐'..'‧', '　'..'】')
        val others = "éⅫ①ｶ㈱™゙"
        val random = Random(7)
        repeat(2_000) {
            val text = buildString {
                repeat(random.nextInt(1, 24)) {
                    append(if (random.nextInt(40) == 0) others.random(random) else pools.random(random).random(random))
                }
            }
            assertEquals(slowNormalizeForMatching(text), normalizeForMatching(text), text)
        }
    }
}
