package org.zoobastiks.ztelegram.database

import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Система миграции данных из YAML файлов в SQLite
 * 
 * Мигрирует:
 * - players.yml -> players, hidden_players, blacklist, whitelist
 * - reputation.yml -> reputation, reputation_history
 * - stats.yml -> stats_joins, stats_playtime
 * - game_stats.yml -> game_stats
 * - random_cooldowns.yml -> cooldowns
 * - unreg_cooldowns.yml -> cooldowns
 */
class DataMigrator(private val plugin: ZTele, private val db: DatabaseManager) {
    
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    
    /**
     * Выполняет миграцию всех данных из YAML в SQLite
     */
    fun migrateAll(): Boolean {
        // Создаем таблицу для отслеживания миграции данных (отдельно от версии схемы БД)
        try {
            db.executeUpdate("""
                CREATE TABLE IF NOT EXISTS data_migration_status (
                    migration_type TEXT PRIMARY KEY,
                    completed INTEGER DEFAULT 0,
                    completed_at TEXT
                )
            """)
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка создания таблицы статуса миграции: ${e.message}")
        }
        
        // Проверяем, была ли уже выполнена миграция данных
        val dataMigrationDone = db.executeQuery("SELECT completed FROM data_migration_status WHERE migration_type = 'yaml_to_sqlite'") { rs ->
            if (rs.next()) rs.getInt("completed") == 1 else false
        } ?: false
        
        if (dataMigrationDone) {
            plugin.logger.info("Миграция данных из YAML уже выполнена ранее, пропускаем")
            return true
        }
        
        // Проверяем, есть ли YAML файлы для миграции
        val hasYamlFiles = File(plugin.dataFolder, "players.yml").exists() ||
                          File(plugin.dataFolder, "reputation.yml").exists() ||
                          File(plugin.dataFolder, "stats.yml").exists() ||
                          File(plugin.dataFolder, "game_stats.yml").exists() ||
                          File(plugin.dataFolder, "random_cooldowns.yml").exists() ||
                          File(plugin.dataFolder, "unreg_cooldowns.yml").exists()
        
        if (!hasYamlFiles) {
            plugin.logger.info("YAML файлы не найдены, миграция не требуется")
            // Помечаем миграцию как выполненную, чтобы не проверять каждый раз
            db.executeUpdate("""
                INSERT OR REPLACE INTO data_migration_status (migration_type, completed, completed_at)
                VALUES ('yaml_to_sqlite', 1, datetime('now'))
            """)
            return true
        }
        
        plugin.logger.info("🔄 Начинается миграция данных из YAML в SQLite...")
        
        var success = true
        
        try {
            db.executeTransaction { _ ->
                // Мигрируем данные по порядку
                success = success && migratePlayers()
                success = success && migrateReputation()
                success = success && migrateStats()
                success = success && migrateGameStats()
                success = success && migrateCooldowns()
                
                if (success) {
                    plugin.logger.info("✅ Миграция данных завершена успешно")
                } else {
                    plugin.logger.warning("⚠️ Миграция завершена с предупреждениями")
                }
            }
            
            // Помечаем миграцию как выполненную (даже при предупреждениях)
            if (success) {
                db.executeUpdate("""
                    INSERT OR REPLACE INTO data_migration_status (migration_type, completed, completed_at)
                    VALUES ('yaml_to_sqlite', 1, datetime('now'))
                """)
                plugin.logger.info("✅ Статус миграции сохранен в БД")
            }
        } catch (e: Exception) {
            plugin.logger.severe("❌ Ошибка миграции данных: ${e.message}")
            e.printStackTrace()
            success = false
        }
        
        return success
    }
    
    /**
     * Мигрирует данные игроков из players.yml
     */
    private fun migratePlayers(): Boolean {
        val playersFile = File(plugin.dataFolder, "players.yml")
        if (!playersFile.exists()) {
            plugin.logger.info("Файл players.yml не найден, пропускаем миграцию")
            return true
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(playersFile)
            
            // Мигрируем скрытых игроков
            val hiddenList = config.getStringList("hidden-players")
            hiddenList.forEach { playerName ->
                db.executeUpdate(
                    "INSERT OR IGNORE INTO hidden_players (player_name) VALUES (?)",
                    listOf(playerName.lowercase())
                )
            }
            
            // Мигрируем черный список
            val blacklist = config.getStringList("blacklist")
            blacklist.forEach { telegramId ->
                db.executeUpdate(
                    "INSERT OR IGNORE INTO blacklist (telegram_id) VALUES (?)",
                    listOf(telegramId)
                )
            }
            
            // Мигрируем белый список
            val whitelist = config.getStringList("whitelist")
            whitelist.forEach { telegramId ->
                db.executeUpdate(
                    "INSERT OR IGNORE INTO whitelist (telegram_id) VALUES (?)",
                    listOf(telegramId)
                )
            }
            
            // Мигрируем зарегистрированных игроков
            val playersSection = config.getConfigurationSection("players")
            if (playersSection != null) {
                for (playerName in playersSection.getKeys(false)) {
                    val playerSection = playersSection.getConfigurationSection(playerName)
                    if (playerSection != null) {
                        val telegramId = playerSection.getString("telegram-id") ?: continue
                        val registered = playerSection.getString("registered")
                        val gender = playerSection.getString("gender")
                        val unlinked = playerSection.getBoolean("unlinked", false)
                        val originalName = playerSection.getString("original-name") ?: playerName
                        
                        db.executeUpdate(
                            """
                            INSERT OR REPLACE INTO players 
                            (telegram_id, player_name, registered_date, gender, unlinked, original_name)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                            listOf(telegramId, playerName, registered ?: "", gender ?: "", if (unlinked) 1 else 0, originalName)
                        )
                    }
                }
            }
            
            plugin.logger.info("✅ Мигрированы данные игроков: ${hiddenList.size} скрытых, ${blacklist.size} в черном списке, ${whitelist.size} в белом списке")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка миграции players.yml: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Мигрирует данные репутации из reputation.yml
     */
    private fun migrateReputation(): Boolean {
        val reputationFile = File(plugin.dataFolder, "reputation.yml")
        if (!reputationFile.exists()) {
            plugin.logger.info("Файл reputation.yml не найден, пропускаем миграцию")
            return true
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(reputationFile)
            val playersSection = config.getConfigurationSection("players") ?: return true
            
            var migratedCount = 0
            
            for (playerName in playersSection.getKeys(false)) {
                val playerSection = playersSection.getConfigurationSection(playerName) ?: continue
                
                val positive = playerSection.getInt("positive", 0)
                val negative = playerSection.getInt("negative", 0)
                
                // Вставляем основную запись репутации
                db.executeUpdate(
                    """
                    INSERT OR REPLACE INTO reputation (player_name, positive, negative)
                    VALUES (?, ?, ?)
                    """,
                    listOf(playerName.lowercase(), positive, negative)
                )
                
                // Мигрируем историю
                val historySection = playerSection.getConfigurationSection("history")
                if (historySection != null) {
                    for (source in historySection.getKeys(false)) {
                        val entrySection = historySection.getConfigurationSection(source) ?: continue
                        
                        val isPositive = entrySection.getBoolean("positive", true)
                        val timestampStr = entrySection.getString("timestamp") ?: continue
                        val reason = entrySection.getString("reason")
                        
                        try {
                            // Парсим timestamp в разных форматах
                            val timestamp = try {
                                LocalDateTime.parse(timestampStr, dateFormatter)
                            } catch (e: Exception) {
                                try {
                                    LocalDateTime.parse(timestampStr, dateTimeFormatter)
                                } catch (e2: Exception) {
                                    LocalDateTime.now()
                                }
                            }
                            
                            db.executeUpdate(
                                """
                                INSERT INTO reputation_history 
                                (player_name, source, is_positive, timestamp, reason)
                                VALUES (?, ?, ?, ?, ?)
                                """,
                                listOf(
                                    playerName.lowercase(),
                                    source,
                                    if (isPositive) 1 else 0,
                                    timestamp.format(dateTimeFormatter),
                                    reason ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            plugin.logger.warning("Ошибка миграции истории репутации для $playerName от $source: ${e.message}")
                        }
                    }
                }
                
                migratedCount++
            }
            
            plugin.logger.info("✅ Мигрированы данные репутации для $migratedCount игроков")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка миграции reputation.yml: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Мигрирует данные статистики из stats.yml
     */
    private fun migrateStats(): Boolean {
        val statsFile = File(plugin.dataFolder, "stats.yml")
        if (!statsFile.exists()) {
            plugin.logger.info("Файл stats.yml не найден, пропускаем миграцию")
            return true
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(statsFile)
            val playersSection = config.getConfigurationSection("players") ?: return true
            
            var joinsCount = 0
            
            for (uuidString in playersSection.getKeys(false)) {
                try {
                    UUID.fromString(uuidString) // Проверяем валидность UUID
                    val playerSection = playersSection.getConfigurationSection(uuidString) ?: continue
                    
                    // Мигрируем логи входов
                    val joinsSection = playerSection.getConfigurationSection("joins")
                    if (joinsSection != null) {
                        for (joinKey in joinsSection.getKeys(false)) {
                            val joinSection = joinsSection.getConfigurationSection(joinKey)
                            if (joinSection != null) {
                                val playerName = joinSection.getString("name") ?: continue
                                val timeString = joinSection.getString("time") ?: continue
                                
                                try {
                                    val joinTime = LocalDateTime.parse(timeString, dateTimeFormatter)
                                    
                                    db.executeUpdate(
                                        """
                                        INSERT INTO stats_joins (uuid, player_name, join_time)
                                        VALUES (?, ?, ?)
                                        """,
                                        listOf(uuidString, playerName, joinTime.format(dateTimeFormatter))
                                    )
                                    joinsCount++
                                } catch (e: Exception) {
                                    plugin.logger.warning("Ошибка парсинга времени входа: $timeString")
                                }
                            }
                        }
                    }
                    
                    // Мигрируем время игры (если есть в старой структуре)
                    // Это может быть в другом формате, поэтому проверяем разные варианты
                    
                } catch (e: Exception) {
                    plugin.logger.warning("Ошибка миграции статистики для UUID $uuidString: ${e.message}")
                }
            }
            
            plugin.logger.info("✅ Мигрированы данные статистики: $joinsCount входов")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка миграции stats.yml: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Мигрирует данные статистики игр из game_stats.yml
     */
    private fun migrateGameStats(): Boolean {
        val gameStatsFile = File(plugin.dataFolder, "game_stats.yml")
        if (!gameStatsFile.exists()) {
            plugin.logger.info("Файл game_stats.yml не найден, пропускаем миграцию")
            return true
        }
        
        try {
            val config = YamlConfiguration.loadConfiguration(gameStatsFile)
            val playersSection = config.getConfigurationSection("players") ?: return true
            
            var migratedCount = 0
            
            for (telegramId in playersSection.getKeys(false)) {
                val playerSection = playersSection.getConfigurationSection(telegramId) ?: continue
                
                val totalGames = playerSection.getInt("totalGames", 0)
                val wins = playerSection.getInt("wins", 0)
                val losses = playerSection.getInt("losses", 0)
                val totalEarned = playerSection.getDouble("totalEarned", 0.0)
                val totalTime = playerSection.getLong("totalTime", 0L)
                
                db.executeUpdate(
                    """
                    INSERT OR REPLACE INTO game_stats 
                    (telegram_id, total_games, wins, losses, total_earned, total_time)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    listOf(telegramId, totalGames, wins, losses, totalEarned, totalTime)
                )
                
                migratedCount++
            }
            
            plugin.logger.info("✅ Мигрированы данные статистики игр для $migratedCount игроков")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("Ошибка миграции game_stats.yml: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Мигрирует кулдауны из random_cooldowns.yml и unreg_cooldowns.yml
     */
    private fun migrateCooldowns(): Boolean {
        var success = true
        
        // Мигрируем кулдауны рулетки
        val randomCooldownsFile = File(plugin.dataFolder, "random_cooldowns.yml")
        if (randomCooldownsFile.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(randomCooldownsFile)
                
                // Глобальный кулдаун рулетки
                val globalCooldown = config.getString("global_cooldown")
                if (globalCooldown != null) {
                    db.executeUpdate(
                        """
                        INSERT OR REPLACE INTO cooldowns (type, identifier, timestamp)
                        VALUES (?, ?, ?)
                        """,
                        listOf("random", "global", globalCooldown)
                    )
                }
                
                plugin.logger.info("✅ Мигрированы кулдауны рулетки")
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка миграции random_cooldowns.yml: ${e.message}")
                success = false
            }
        }
        
        // Мигрируем кулдауны отмены регистрации
        val unregCooldownsFile = File(plugin.dataFolder, "unreg_cooldowns.yml")
        if (unregCooldownsFile.exists()) {
            try {
                val config = YamlConfiguration.loadConfiguration(unregCooldownsFile)
                
                for (key in config.getKeys(false)) {
                    val timeString = config.getString(key)
                    if (timeString != null) {
                        db.executeUpdate(
                            """
                            INSERT OR REPLACE INTO cooldowns (type, identifier, timestamp)
                            VALUES (?, ?, ?)
                            """,
                            listOf("unreg", key, timeString)
                        )
                    }
                }
                
                plugin.logger.info("✅ Мигрированы кулдауны отмены регистрации")
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка миграции unreg_cooldowns.yml: ${e.message}")
                success = false
            }
        }
        
        return success
    }
}

