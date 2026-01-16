package org.zoobastiks.ztelegram.mgr

import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

/**
 * Менеджер кулдаунов для команды /unreg
 */
class UnregCooldownManager(private val plugin: ZTele) {
    
    private val cooldowns = ConcurrentHashMap<Long, LocalDateTime>()
    private val cooldownFile = File(plugin.dataFolder, "unreg_cooldowns.yml")
    private lateinit var cooldownConfig: YamlConfiguration
    
    init {
        loadCooldowns()
    }
    
    /**
     * Проверяет, может ли пользователь использовать команду /unreg
     */
    fun canUnregister(telegramId: Long): Boolean {
        // Администраторы не имеют кулдауна
        if (ZTele.conf.isAdministrator(telegramId)) {
            return true
        }
        
        val lastUsed = cooldowns[telegramId] ?: return true
        val now = LocalDateTime.now()
        val hoursPassed = ChronoUnit.HOURS.between(lastUsed, now)
        
        return hoursPassed >= ZTele.conf.unregCommandCooldownHours
    }
    
    /**
     * Получает оставшееся время до следующего использования
     */
    fun getRemainingTime(telegramId: Long): String {
        val lastUsed = cooldowns[telegramId] ?: return "0ч"
        val now = LocalDateTime.now()
        val hoursPassed = ChronoUnit.HOURS.between(lastUsed, now)
        val hoursRemaining = ZTele.conf.unregCommandCooldownHours - hoursPassed
        
        return if (hoursRemaining > 0) {
            "${hoursRemaining}ч"
        } else {
            "0ч"
        }
    }
    
    /**
     * Устанавливает кулдаун для пользователя
     */
    fun setCooldown(telegramId: Long) {
        cooldowns[telegramId] = LocalDateTime.now()
        saveCooldowns()
    }
    
    /**
     * Загружает кулдауны из файла или БД
     */
    private fun loadCooldowns() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            loadCooldownsFromDatabase()
        } else {
            loadCooldownsFromYaml()
        }
    }
    
    private fun loadCooldownsFromDatabase() {
        try {
            ZTele.database.executeQuery(
                "SELECT identifier, timestamp FROM cooldowns WHERE type = ?",
                listOf("unreg")
            ) { rs ->
                while (rs.next()) {
                    try {
                        val telegramId = rs.getString("identifier").toLong()
                        val timestamp = rs.getString("timestamp")
                        val dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        cooldowns[telegramId] = dateTime
                    } catch (e: Exception) {
                        plugin.logger.warning("Ошибка загрузки кулдауна отмены регистрации: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки кулдаунов отмены регистрации из БД, переключаемся на YAML: ${e.message}")
            loadCooldownsFromYaml()
        }
    }
    
    private fun loadCooldownsFromYaml() {
        if (!cooldownFile.exists()) {
            cooldownFile.parentFile.mkdirs()
            cooldownFile.createNewFile()
        }
        
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile)
        
        // Загружаем данные из конфигурации
        for (key in cooldownConfig.getKeys(false)) {
            try {
                val telegramId = key.toLong()
                val timeString = cooldownConfig.getString(key)
                if (timeString != null) {
                    val dateTime = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    cooldowns[telegramId] = dateTime
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load cooldown for $key: ${e.message}")
            }
        }
    }
    
    /**
     * Сохраняет кулдауны в файл или БД
     */
    private fun saveCooldowns() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            saveCooldownsToDatabase()
        } else {
            saveCooldownsToYaml()
        }
    }
    
    private fun saveCooldownsToDatabase() {
        try {
            // Очищаем старые записи (старше 30 дней)
            val cutoff = LocalDateTime.now().minusDays(30)
            cooldowns.entries.removeAll { it.value.isBefore(cutoff) }
            
            // Сохраняем актуальные кулдауны
            ZTele.database.executeTransaction { conn ->
                // Удаляем все старые кулдауны этого типа
                conn.prepareStatement("DELETE FROM cooldowns WHERE type = ?").use { stmt ->
                    stmt.setString(1, "unreg")
                    stmt.executeUpdate()
                }
                
                // Вставляем новые
                for ((telegramId, dateTime) in cooldowns) {
                    conn.prepareStatement("INSERT INTO cooldowns (type, identifier, timestamp) VALUES (?, ?, ?)").use { stmt ->
                        stmt.setString(1, "unreg")
                        stmt.setString(2, telegramId.toString())
                        stmt.setString(3, dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                        stmt.executeUpdate()
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения кулдаунов отмены регистрации в БД: ${e.message}")
            saveCooldownsToYaml() // Fallback на YAML
        }
    }
    
    private fun saveCooldownsToYaml() {
        try {
            // Очищаем старые записи (старше 30 дней)
            val cutoff = LocalDateTime.now().minusDays(30)
            cooldowns.entries.removeAll { it.value.isBefore(cutoff) }
            
            // Сохраняем актуальные кулдауны
            cooldownConfig = YamlConfiguration()
            for ((telegramId, dateTime) in cooldowns) {
                cooldownConfig.set(telegramId.toString(), dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            
            cooldownConfig.save(cooldownFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save unreg cooldowns: ${e.message}")
        }
    }
    
    /**
     * Очищает старые кулдауны
     */
    fun cleanup() {
        val cutoff = LocalDateTime.now().minusDays(30)
        cooldowns.entries.removeAll { it.value.isBefore(cutoff) }
        saveCooldowns()
    }
}
