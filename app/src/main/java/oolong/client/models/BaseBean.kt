package oolong.client.models

import oolong.client.toJson

open class BaseBean {
    override fun toString(): String {
        return toJson()
    }
}
