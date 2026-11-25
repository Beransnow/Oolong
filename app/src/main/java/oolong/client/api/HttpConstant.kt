package oolong.client.api

object Error {
    const val ERROR_NETWORK = 10
    const val ERROR_UNKNOWN = -1
    const val ERROR_PARSE = -2
    const val ERROR_NOT_LOGGED_IN = 11
    const val ERROR_LOGGED_IN_EXPIRED = 12
    const val ERROR_UPDATE_NOT_ENABLE = 100
}

object Header {
    const val NO_ST_PARAMS = "no_st_params"
    const val NO_ST_PARAMS_TRUE = "true"

    const val FORCE_PARAM = "force_param"
    const val FORCE_PARAM_QUERY = "query"

    const val FORCE_LOGIN = "force_login"
    const val FORCE_LOGIN_TRUE = "true"

    const val DROP_PARAMS = "drop_params"
    const val DROP_HEADERS = "drop_headers"
    const val NO_COMMON_PARAMS = "no_common_params"

    const val ADD_WEB_COOKIE = "add_cookie"
    const val ADD_WEB_COOKIE_FALSE = "false"

    const val ACCEPT_LANGUAGE = "Accept-Language"
    const val ACCEPT = "Accept"
    const val ACCEPT_LANGUAGE_VALUE = "zh-CN,zh;q=0.9"
    const val COOKIE = "cookie"
    const val HOST = "Host"
    const val ORIGIN = "Origin"
    const val REFERER = "Referer"
    const val USER_AGENT = "User-Agent"
    const val PRAGMA = "Pragma"
    const val CACHE_CONTROL = "Cache-Control"
    const val CUID = "cuid"
    const val CHARSET = "Charset"
    const val CUID_GALAXY2 = "cuid_galaxy2"
    const val CUID_GALAXY3 = "c3_aid"
    const val CUID_GID = "cuid_gid"
    const val CLIENT_TYPE = "client_type"
    const val CLIENT_USER_TOKEN = "client_user_token"
    const val CLIENT_LOG_ID = "client_logid"
    const val X_BD_DATA_TYPE = "x_bd_data_type"
}
