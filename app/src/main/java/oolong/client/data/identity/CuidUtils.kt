package oolong.client.data.identity

import oolong.client.util.Base32

object CuidUtils {
    fun getNewCuid(): String {
        val cuid = UIDUtil.cUID
        val encode = Base32.encode(IdCheck.of(cuid.toByteArray()))
        return "$cuid|V$encode"
    }
}
