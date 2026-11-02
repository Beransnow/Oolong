package com.huanchengfly.tieba.post.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.huanchengfly.tieba.post.model.BlockRule
import com.huanchengfly.tieba.post.model.BlockRule.Condition
import com.huanchengfly.tieba.post.model.BlockRule.Pattern
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

    /** A database as [version] created it, from its exported schema. */
    private fun create(file: File, version: Int, vararg statements: String) {
        val schema = Json.parseToJsonElement(File("schemas/${AppDatabase::class.qualifiedName}/$version.json").readText())
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
            db.version = version
        }
    }

    private fun open(file: File) = Room.databaseBuilder(context, AppDatabase::class.java, file.absolutePath)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        .build()
        .also { database = it }

    @Test
    fun `rules keep what they matched, and allow list entries become exceptions`() = runBlocking {
        val file = File(folder.root, "app.db")
        create(
            file,
            1,
            "INSERT INTO block_rules (id, category, type, keywords, username, uid) VALUES (1, 10, 0, '[\"广告\",\"加群\"]', NULL, NULL)",
            "INSERT INTO block_rules (id, category, type, keywords, username, uid) VALUES (2, 11, 1, NULL, 'someone', '7')",
        )

        val rules = open(file).blockDao().all().first().map { it.toBlockRule() }

        assertEquals(
            listOf(
                BlockRule(id = 1, condition = Condition.Text(Pattern.Words(listOf("广告", "加群")))),
                BlockRule(id = 2, condition = Condition.User(7, "someone"), action = BlockRule.Action.Allow),
            ),
            rules,
        )
    }

    @Test
    fun `each kind of rule and its conditions becomes one condition`() = runBlocking {
        val file = File(folder.root, "app.db")
        val columns = "id, category, type, keywords, username, uid, forum_name, display_name, level, regex, in_forum, title_only, " +
            "scope, action, expires_at, enabled, hits, last_hit_at, created_at"
        create(
            file,
            3,
            "INSERT INTO block_rules ($columns) VALUES (1, 10, 0, '[\"互关|互粉\"]', NULL, NULL, NULL, NULL, NULL, 1, NULL, 1, 1, 2, 9000, 0, 4, 300, 200)",
            "INSERT INTO block_rules ($columns) VALUES (2, 10, 3, NULL, NULL, NULL, NULL, NULL, 4, 0, '星穹铁道', 0, 0, 1, NULL, 1, 0, NULL, 0)",
            "INSERT INTO block_rules ($columns) VALUES (3, 10, 2, NULL, NULL, NULL, '抽卡', NULL, NULL, 0, NULL, 0, 0, 0, NULL, 1, 0, NULL, 0)",
            "INSERT INTO block_rules ($columns) VALUES (4, 11, 1, NULL, 'someone', '7', NULL, '某人', NULL, 0, NULL, 0, 0, 0, NULL, 1, 0, NULL, 0)",
        )

        val rules = open(file).blockDao().all().first().map { it.toBlockRule() }

        assertEquals(
            listOf(
                BlockRule(
                    id = 1,
                    condition = Condition.Text(Pattern.Regex("互关|互粉"), BlockRule.TextField.Title),
                    scope = BlockRule.Scope.Feeds,
                    action = BlockRule.Action.Hide,
                    expiresAt = 9000,
                    enabled = false,
                    hits = 4,
                    lastHitAt = 300,
                    createdAt = 200,
                ),
                BlockRule(
                    id = 2,
                    condition = Condition.AllOf(listOf(Condition.Forum("星穹铁道"), Condition.LevelBelow(4))),
                    action = BlockRule.Action.Fold,
                ),
                BlockRule(id = 3, condition = Condition.Forum("抽卡")),
                BlockRule(id = 4, condition = Condition.User(7, "someone", "某人"), action = BlockRule.Action.Allow),
            ),
            rules,
        )
    }
}
