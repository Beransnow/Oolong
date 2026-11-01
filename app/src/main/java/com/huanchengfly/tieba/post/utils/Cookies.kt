package com.huanchengfly.tieba.post.utils

/** The name–value pairs of a `Cookie` header. */
fun parseCookie(cookie: String): Map<String, String> = cookie
    .split(";")
    .map { it.trim().split("=") }
    .filter { it.size > 1 }
    .associate { it.first() to it.drop(1).joinToString("=") }

/** A `Set-Cookie` value logging the web pages of baidu.com in with [bduss]. */
fun bdussCookie(bduss: String): String = "BDUSS=$bduss; Path=/; Max-Age=315360000; Domain=.baidu.com; Httponly"
