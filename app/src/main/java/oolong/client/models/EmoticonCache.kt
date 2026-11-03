package oolong.client.models

data class EmoticonCache(
    var ids: List<String> = emptyList(),
    var mapping: Map<String, String> = emptyMap()
)
