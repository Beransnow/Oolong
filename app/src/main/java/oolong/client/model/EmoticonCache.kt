package oolong.client.model

import kotlinx.serialization.Serializable

@Serializable
data class EmoticonCache(
    var ids: List<String> = emptyList(),
    var mapping: Map<String, String> = emptyMap()
)
