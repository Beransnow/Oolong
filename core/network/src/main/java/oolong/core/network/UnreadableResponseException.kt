package oolong.core.network

import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import java.io.IOException
import java.lang.reflect.Type

/**
 * A response that came back but could not be read, as a web page where data was expected. It names
 * the endpoint (its path, without the query, which may hold what the user typed) and the type of
 * what came, which is most of what tells one such failure from another.
 */
class UnreadableResponseException(
    val endpoint: String,
    val contentType: String?,
    cause: Throwable,
) : RuntimeException("Unreadable response from $endpoint" + contentType?.let { " ($it)" }.orEmpty(), cause)

/**
 * Wraps the converters of [delegate] so that a response they fail on throws
 * [UnreadableResponseException]. Errors they mean to raise, all I/O exceptions like
 * [TiebaServerException], and a body cut off while it is read, pass as they are.
 */
internal class UnreadableResponseConverterFactory(private val delegate: Converter.Factory) : Converter.Factory() {
    override fun responseBodyConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit): Converter<ResponseBody, *>? {
        val converter = delegate.responseBodyConverter(type, annotations, retrofit) ?: return null
        val endpoint = endpointOf(annotations)
        return Converter<ResponseBody, Any?> { body ->
            val contentType = body.contentType()?.let { "${it.type}/${it.subtype}" }
            try {
                converter.convert(body)
            } catch (e: IOException) {
                throw e
            } catch (e: Exception) {
                throw UnreadableResponseException(endpoint, contentType, e)
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ) = delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit)

    override fun stringConverter(type: Type, annotations: Array<out Annotation>, retrofit: Retrofit) =
        delegate.stringConverter(type, annotations, retrofit)

    /** The path a service method's annotation names, without its query. */
    private fun endpointOf(annotations: Array<out Annotation>): String {
        val path = annotations.firstNotNullOfOrNull { annotation ->
            when (annotation) {
                is GET -> annotation.value
                is POST -> annotation.value
                is PUT -> annotation.value
                is DELETE -> annotation.value
                is PATCH -> annotation.value
                is HEAD -> annotation.value
                else -> null
            }
        }
        return path?.substringBefore('?')?.ifEmpty { null } ?: "?"
    }
}
