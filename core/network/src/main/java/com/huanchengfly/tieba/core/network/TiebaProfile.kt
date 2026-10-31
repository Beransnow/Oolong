package com.huanchengfly.tieba.core.network

enum class TiebaProtocol { PROTOBUF }

/**
 * The client identities requests are sent as. Version numbers and per-version constants live only
 * here; an exception to the common version must say why it exists.
 */
enum class TiebaProfile(
    val version: String,
    val frameworkVersion: String,
    val sdkVersion: String,
    val swanGameVersion: String,
    val protocol: TiebaProtocol,
    val baseUrl: String = "https://tiebac.baidu.com/",
) {
    PROTO_12_52(
        version = "12.52.1.0",
        frameworkVersion = "3340042",
        sdkVersion = "2.34.0",
        swanGameVersion = "1038000",
        protocol = TiebaProtocol.PROTOBUF,
    ),

    /**
     * `pb/floor` only returns sub-post images to 22.x clients. Remove once every API moves to a
     * 22.x version.
     */
    PROTO_22_FLOOR(
        version = "22.10.1.0",
        frameworkVersion = "4220001",
        sdkVersion = "3.36.0",
        swanGameVersion = "2035000",
        protocol = TiebaProtocol.PROTOBUF,
    ),
}
