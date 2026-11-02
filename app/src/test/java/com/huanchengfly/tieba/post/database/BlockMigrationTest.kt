package com.huanchengfly.tieba.post.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.repository.toBlockRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BlockMigrationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: AppDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    /** A database as version 1 created it, from its exported schema. */
    private fun version1(file: File, vararg statements: String) {
        val schema = Json.parseToJsonElement(File("schemas/${AppDatabase::class.qualifiedName}/1.json").readText())
            .jsonObject.getValue("database").jsonObject
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getValue("entities").jsonArray.forEach { entity ->
                val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
                fun sql(element: kotlinx.serialization.json.JsonElement) =
                    element.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
                db.execSQL(sql(entity.jsonObject.getValue("createSql")))
                entity.jsonObject["indices"]?.jsonArray?.forEach { db.execSQL(sql(it.jsonObject.getValue("createSql"))) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            statements.forEach { db.execSQL(it) }
            db.version = 1
        }
    }

    @Test
    fun `rules keep what they matched, and allow list entries become exceptions`() = runBlocking {
        val file = File(folder.root, "app.db")
        version1(
            file,
            "INSERT INTO block_rules (id, category, type, keywords, username, uid) VALUES (1, 10, 0, '[\"广告\",\"加群\"]', NULL, NULL)",
            "INSERT INTO block_rules (id, category, type, keywords, username, uid) VALUES (2, 11, 1, NULL, 'someone', '7')",
        )

        val db = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2)
            .build()
            .also { database = it }
        val rules = db.blockDao().all().first().map { it.toBlockRule() }

        assertEquals(
            listOf(
                BlockRule(id = 1, match = BlockRule.Match.Keyword(listOf("广告", "加群"))),
                BlockRule(id = 2, match = BlockRule.Match.User(7, "someone"), action = BlockRule.Action.Allow),
            ),
            rules,
        )
    }
}
