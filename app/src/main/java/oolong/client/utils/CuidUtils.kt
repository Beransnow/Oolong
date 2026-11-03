package oolong.client.utils

import oolong.client.utils.helios.Base32
import oolong.client.utils.helios.Hasher

object CuidUtils {
    fun getNewCuid(): String {
        val cuid = UIDUtil.cUID
        val encode = Base32.encode(Hasher.hash(cuid.toByteArray()))
        return "$cuid|V$encode"
    }
}
