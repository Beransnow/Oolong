package oolong.client.database

import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Makes [file] a database as [version] created it, from its exported schema, then runs [statements]. */
internal fun createDatabase(file: File, version: Int, vararg statements: String) {
    val schema = Json.parseToJsonElement(File("schemas/${AppDatabase::class.qualifiedName}/$version.json").readText())
        .jsonObject.getValue("database").jsonObject
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        schema.getValue("entities").jsonArray.forEach { entity ->
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            fun sql(element: JsonElement) = element.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            db.execSQL(sql(entity.jsonObject.getValue("createSql")))
            entity.jsonObject["indices"]?.jsonArray?.forEach { db.execSQL(sql(it.jsonObject.getValue("createSql"))) }
        }
        schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        statements.forEach { db.execSQL(it) }
        db.version = version
    }
}
