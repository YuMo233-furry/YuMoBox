package com.example.yumoflatimagemanager.data.local

import android.content.Context
import android.os.Environment
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

@Database(
    entities = [TagEntity::class, MediaTagCrossRef::class, TagReferenceEntity::class, TagGroupEntity::class, TagGroupTagCrossRef::class, com.example.yumoflatimagemanager.data.WatermarkPreset::class],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
	abstract fun tagDao(): TagDao
	abstract fun watermarkDao(): WatermarkDao

	companion object {
		@Volatile private var INSTANCE: AppDatabase? = null
		fun get(context: Context): AppDatabase = 
			INSTANCE ?: synchronized(this) {
                // 获取公共文件夹路径，用于存储数据库
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                val appDir = File(publicDir, "YuMoBox")
                appDir.mkdirs() // 创建应用专用目录
                
                val dbPath = File(appDir, "yumo_box.db").absolutePath
                
                INSTANCE ?: Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java,
					dbPath
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration() // 添加这行以防止迁移失败
                .build().also { INSTANCE = it }
			}

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tags ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段到tags表
                database.execSQL("ALTER TABLE tags ADD COLUMN isExpanded INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE tags ADD COLUMN imageCount INTEGER NOT NULL DEFAULT 0")
                
                // 创建标签引用关系表（不手动添加外键约束，让Room自动处理）
                database.execSQL("""
                    CREATE TABLE tag_references (
                        parentTagId INTEGER NOT NULL,
                        childTagId INTEGER NOT NULL,
                        PRIMARY KEY(parentTagId, childTagId)
                    )
                """)
                
                // 创建索引
                database.execSQL("CREATE INDEX index_tag_references_childTagId ON tag_references(childTagId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 为tag_references表添加sortOrder字段
                database.execSQL("ALTER TABLE tag_references ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建水印预设表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS watermark_presets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        text TEXT,
                        textSize REAL NOT NULL,
                        textColor INTEGER NOT NULL,
                        textBold INTEGER NOT NULL,
                        imageUri TEXT,
                        imageScale REAL NOT NULL,
                        anchor TEXT NOT NULL,
                        offsetX INTEGER NOT NULL,
                        offsetY INTEGER NOT NULL,
                        alpha INTEGER NOT NULL,
                        rotation REAL NOT NULL,
                        createdAt INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加两套独立的排序字段
                database.execSQL("ALTER TABLE tags ADD COLUMN referencedGroupSortOrder INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE tags ADD COLUMN normalGroupSortOrder INTEGER NOT NULL DEFAULT 0")
                
                // 兼容旧版数据升级策略：
                // 1. 首先将所有现有标签的 sortOrder 复制到 normalGroupSortOrder（保持原有顺序）
                database.execSQL("UPDATE tags SET normalGroupSortOrder = sortOrder")
                
                // 2. 然后根据标签的 sortOrder 值范围来判断它们应该属于哪个组
                // 根据现有代码逻辑：有引用标签的本体标签组使用 1000-99999，无引用标签的本体标签组使用 100000+
                
                // 将有引用标签的本体标签组的标签迁移到 referencedGroupSortOrder
                database.execSQL("""
                    UPDATE tags 
                    SET referencedGroupSortOrder = sortOrder, normalGroupSortOrder = 0
                    WHERE sortOrder >= 1000 AND sortOrder < 100000
                """)
                
                // 将无引用标签的本体标签组的标签保持在 normalGroupSortOrder
                database.execSQL("""
                    UPDATE tags 
                    SET normalGroupSortOrder = sortOrder, referencedGroupSortOrder = 0
                    WHERE sortOrder >= 100000 OR sortOrder = 0
                """)
                
                // 3. 对于 sortOrder = 0 的标签（通常是新创建的），默认放在无引用标签的本体标签组
                // 4. 重新分配排序值，确保每个组内的排序值连续且合理
                
                // 重新分配有引用标签的本体标签组的排序值（从1000开始，间隔1000）
                // 使用兼容旧版本SQLite的方法，避免使用ROW_NUMBER() OVER
                database.execSQL("""
                    UPDATE tags 
                    SET referencedGroupSortOrder = (
                        SELECT (COUNT(*) - 1) * 1000 + 1000
                        FROM tags t2 
                        WHERE t2.referencedGroupSortOrder > 0 
                        AND (
                            t2.sortOrder < tags.sortOrder 
                            OR (t2.sortOrder = tags.sortOrder AND t2.name <= tags.name)
                        )
                    )
                    WHERE referencedGroupSortOrder > 0
                """)
                
                // 重新分配无引用标签的本体标签组的排序值（从1000开始，间隔1000）
                // 使用兼容旧版本SQLite的方法，避免使用ROW_NUMBER() OVER
                database.execSQL("""
                    UPDATE tags 
                    SET normalGroupSortOrder = (
                        SELECT (COUNT(*) - 1) * 1000 + 1000
                        FROM tags t2 
                        WHERE t2.normalGroupSortOrder > 0 
                        AND (
                            t2.sortOrder < tags.sortOrder 
                            OR (t2.sortOrder = tags.sortOrder AND t2.name <= tags.name)
                        )
                    )
                    WHERE normalGroupSortOrder > 0
                """)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建标签组表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tag_groups (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // 创建标签组与标签的关联表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS tag_group_tag_cross_ref (
                        tagGroupId INTEGER NOT NULL,
                        tagId INTEGER NOT NULL,
                        PRIMARY KEY(tagGroupId, tagId),
                        FOREIGN KEY(tagGroupId) REFERENCES tag_groups(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """)

                // 创建索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tag_group_tag_cross_ref_tagGroupId ON tag_group_tag_cross_ref(tagGroupId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_tag_group_tag_cross_ref_tagId ON tag_group_tag_cross_ref(tagId)")

                // 插入默认的"未分组"标签组
                database.execSQL("INSERT INTO tag_groups (id, name, sortOrder, isDefault) VALUES (1, '未分组', 0, 1)")
            }
        }
	}
}


