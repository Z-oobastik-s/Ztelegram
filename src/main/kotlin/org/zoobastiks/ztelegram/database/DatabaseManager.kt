package org.zoobastiks.ztelegram.database

import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.sql.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Универсальный менеджер базы данных SQLite
 * 
 * Преимущества:
 * - Быстрая работа с данными
 * - Транзакции для надежности
 * - Индексы для производительности
 * - Миграция из YAML файлов
 * - Потокобезопасность
 */
class DatabaseManager(private val plugin: ZTele) {
    
    private val dbFile = File(plugin.dataFolder, "ztelegram.db")
    private var connection: Connection? = null
    private val lock = ReentrantReadWriteLock()
    
    // Версия схемы БД для миграций
    private val dbVersion = 1
    
    /**
     * Инициализирует базу данных
     */
    fun initialize() {
        lock.write {
            try {
                if (!dbFile.parentFile.exists()) {
                    dbFile.parentFile.mkdirs()
                }
                
                // Подключаемся к SQLite
                val url = "jdbc:sqlite:${dbFile.absolutePath}"
                connection = DriverManager.getConnection(url)
                
                // Включаем внешние ключи и оптимизации
                connection?.createStatement()?.use { stmt ->
                    stmt.execute("PRAGMA foreign_keys = ON")
                    stmt.execute("PRAGMA journal_mode = WAL") // Write-Ahead Logging для производительности
                    stmt.execute("PRAGMA synchronous = NORMAL") // Баланс между производительностью и надежностью
                    stmt.execute("PRAGMA cache_size = -64000") // 64MB кэш
                }
                
                // Создаем таблицы
                createTables()
                
                // Проверяем версию БД и выполняем миграции
                checkAndMigrate()
                
                plugin.logger.info("✅ База данных SQLite инициализирована: ${dbFile.absolutePath}")
                
            } catch (e: Exception) {
                plugin.logger.severe("❌ Ошибка инициализации базы данных: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Закрывает соединение с базой данных
     */
    fun close() {
        lock.write {
            try {
                connection?.close()
                connection = null
                plugin.logger.info("База данных закрыта")
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка при закрытии БД: ${e.message}")
            }
        }
    }
    
    /**
     * Получает соединение с БД (потокобезопасно)
     */
    fun getConnection(): Connection? {
        return lock.read {
            connection
        }
    }
    
    /**
     * Выполняет SQL запрос с автоматическим управлением транзакцией
     */
    fun <T> executeQuery(query: String, params: List<Any> = emptyList(), block: (ResultSet) -> T): T? {
        return lock.read {
            try {
                getConnection()?.prepareStatement(query)?.use { stmt ->
                    params.forEachIndexed { index, param ->
                        when (param) {
                            is String -> stmt.setString(index + 1, param)
                            is Int -> stmt.setInt(index + 1, param)
                            is Long -> stmt.setLong(index + 1, param)
                            is Double -> stmt.setDouble(index + 1, param)
                            is Boolean -> stmt.setBoolean(index + 1, param)
                            is LocalDateTime -> stmt.setString(index + 1, param.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            else -> stmt.setString(index + 1, param.toString())
                        }
                    }
                    stmt.executeQuery().use { rs ->
                        block(rs)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка выполнения запроса: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * Выполняет SQL команду (INSERT, UPDATE, DELETE)
     */
    fun executeUpdate(query: String, params: List<Any> = emptyList()): Int {
        return lock.write {
            try {
                getConnection()?.prepareStatement(query)?.use { stmt ->
                    params.forEachIndexed { index, param ->
                        when (param) {
                            is String -> stmt.setString(index + 1, param)
                            is Int -> stmt.setInt(index + 1, param)
                            is Long -> stmt.setLong(index + 1, param)
                            is Double -> stmt.setDouble(index + 1, param)
                            is Boolean -> stmt.setBoolean(index + 1, param)
                            is LocalDateTime -> stmt.setString(index + 1, param.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            else -> stmt.setString(index + 1, param.toString())
                        }
                    }
                    stmt.executeUpdate()
                } ?: 0
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка выполнения обновления: ${e.message}")
                e.printStackTrace()
                0
            }
        }
    }
    
    /**
     * Выполняет несколько команд в одной транзакции
     */
    fun executeTransaction(block: (Connection) -> Unit) {
        lock.write {
            try {
                val conn = getConnection() ?: return
                conn.autoCommit = false
                try {
                    block(conn)
                    conn.commit()
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            } catch (e: Exception) {
                plugin.logger.severe("Ошибка транзакции: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Создает все необходимые таблицы
     */
    private fun createTables() {
        val tables = listOf(
            // Таблица игроков
            """
            CREATE TABLE IF NOT EXISTS players (
                telegram_id TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                registered_date TEXT,
                gender TEXT,
                unlinked INTEGER DEFAULT 0,
                original_name TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """,
            
            // Таблица скрытых игроков
            """
            CREATE TABLE IF NOT EXISTS hidden_players (
                player_name TEXT PRIMARY KEY
            )
            """,
            
            // Таблица черного списка
            """
            CREATE TABLE IF NOT EXISTS blacklist (
                telegram_id TEXT PRIMARY KEY
            )
            """,
            
            // Таблица белого списка
            """
            CREATE TABLE IF NOT EXISTS whitelist (
                telegram_id TEXT PRIMARY KEY
            )
            """,
            
            // Таблица репутации
            """
            CREATE TABLE IF NOT EXISTS reputation (
                player_name TEXT PRIMARY KEY,
                positive INTEGER DEFAULT 0,
                negative INTEGER DEFAULT 0,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """,
            
            // Таблица истории репутации
            """
            CREATE TABLE IF NOT EXISTS reputation_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_name TEXT NOT NULL,
                source TEXT NOT NULL,
                is_positive INTEGER NOT NULL,
                timestamp TEXT NOT NULL,
                reason TEXT,
                FOREIGN KEY (player_name) REFERENCES reputation(player_name) ON DELETE CASCADE
            )
            """,
            
            // Таблица статистики входов
            """
            CREATE TABLE IF NOT EXISTS stats_joins (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT NOT NULL,
                player_name TEXT NOT NULL,
                join_time TEXT NOT NULL
            )
            """,
            
            // Таблица времени игры
            """
            CREATE TABLE IF NOT EXISTS stats_playtime (
                uuid TEXT NOT NULL,
                date TEXT NOT NULL,
                minutes INTEGER DEFAULT 0,
                PRIMARY KEY (uuid, date)
            )
            """,
            
            // Таблица статистики игр
            """
            CREATE TABLE IF NOT EXISTS game_stats (
                telegram_id TEXT PRIMARY KEY,
                total_games INTEGER DEFAULT 0,
                wins INTEGER DEFAULT 0,
                losses INTEGER DEFAULT 0,
                total_earned REAL DEFAULT 0.0,
                total_time INTEGER DEFAULT 0,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
            """,
            
            // Таблица кулдаунов (универсальная)
            """
            CREATE TABLE IF NOT EXISTS cooldowns (
                type TEXT NOT NULL,
                identifier TEXT NOT NULL,
                timestamp TEXT NOT NULL,
                PRIMARY KEY (type, identifier)
            )
            """,
            
            // Таблица переводов денег
            """
            CREATE TABLE IF NOT EXISTS payments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                from_player TEXT NOT NULL,
                to_player TEXT NOT NULL,
                amount REAL NOT NULL,
                timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                status TEXT NOT NULL DEFAULT 'completed'
            )
            """
        )
        
        tables.forEach { sql ->
            try {
                getConnection()?.createStatement()?.use { stmt ->
                    stmt.execute(sql)
                }
            } catch (e: Exception) {
                plugin.logger.severe("Ошибка создания таблицы: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Создает индексы для производительности
     */
    private fun createIndexes() {
        val indexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_players_name ON players(player_name)",
            "CREATE INDEX IF NOT EXISTS idx_reputation_history_player ON reputation_history(player_name)",
            "CREATE INDEX IF NOT EXISTS idx_reputation_history_timestamp ON reputation_history(timestamp)",
            "CREATE INDEX IF NOT EXISTS idx_stats_joins_uuid ON stats_joins(uuid)",
            "CREATE INDEX IF NOT EXISTS idx_stats_joins_time ON stats_joins(join_time)",
            "CREATE INDEX IF NOT EXISTS idx_stats_playtime_date ON stats_playtime(date)",
            "CREATE INDEX IF NOT EXISTS idx_cooldowns_type ON cooldowns(type)",
            "CREATE INDEX IF NOT EXISTS idx_payments_from ON payments(from_player)",
            "CREATE INDEX IF NOT EXISTS idx_payments_to ON payments(to_player)",
            "CREATE INDEX IF NOT EXISTS idx_payments_timestamp ON payments(timestamp)"
        )
        
        indexes.forEach { sql ->
            try {
                getConnection()?.createStatement()?.use { stmt ->
                    stmt.execute(sql)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка создания индекса: ${e.message}")
            }
        }
    }
    
    /**
     * Проверяет версию БД и выполняет миграции
     */
    private fun checkAndMigrate() {
        try {
            // Создаем таблицу версий, если её нет
            getConnection()?.createStatement()?.use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS db_version (
                        version INTEGER PRIMARY KEY
                    )
                """)
            }
            
            // Получаем текущую версию
            val currentVersion = executeQuery("SELECT version FROM db_version LIMIT 1") { rs ->
                if (rs.next()) rs.getInt("version") else 0
            } ?: 0
            
            if (currentVersion < dbVersion) {
                plugin.logger.info("Выполняется миграция БД с версии $currentVersion на $dbVersion")
                
                // Создаем индексы
                createIndexes()
                
                // Обновляем версию
                if (currentVersion == 0) {
                    executeUpdate("INSERT INTO db_version (version) VALUES (?)", listOf(dbVersion))
                } else {
                    executeUpdate("UPDATE db_version SET version = ?", listOf(dbVersion))
                }
                
                plugin.logger.info("✅ Миграция БД завершена")
            }
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка проверки версии БД: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Проверяет, существует ли файл БД
     */
    fun databaseExists(): Boolean {
        return dbFile.exists()
    }
    
    /**
     * Получает путь к файлу БД
     */
    fun getDatabasePath(): String {
        return dbFile.absolutePath
    }
}

