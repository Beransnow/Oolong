package oolong.client.utils

object CuidUtils {
    fun getNewCuid(): String {
        val cuid = UIDUtil.cUID
        val encode = Base32.encode(IdCheck.of(cuid.toByteArray()))
        return "$cuid|V$encode"
    }
}
