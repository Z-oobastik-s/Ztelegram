package org.zoobastiks.ztelegram.mgr

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

/**
 * Менеджер рулетки для команды /random
 * 
 * Функции:
 * - Управление глобальным кулдауном для команды /random (5 минут для всех игроков)
 * - Выбор случайного игрока из онлайн
 * - Выбор случайной награды из списка
 * - Обход кулдаунов для администраторов
 */
class RandomManager(private val plugin: ZTele) {
    
    // Глобальный кулдаун для всех игроков (кроме администраторов)
    private val globalCooldown = AtomicReference<LocalDateTime?>(null)
    private val cooldownFile = File(plugin.dataFolder, "random_cooldowns.yml")
    private lateinit var cooldownConfig: YamlConfiguration
    
    // Настройки из конфига
    var cooldownMinutes: Int = 5
        private set
    
    init {
        loadCooldowns()
    }
    
    /**
     * Загружает настройки из конфига
     */
    fun loadConfig() {
        val mainConfig = plugin.config
        cooldownMinutes = mainConfig.getInt("random.cooldown_minutes", 5)
    }
    
    /**
     * Проверяет, может ли пользователь использовать команду /random
     */
    fun canUseRandom(telegramId: Long): Boolean {
        // Администраторы не имеют кулдауна
        if (ZTele.conf.isAdministrator(telegramId)) {
            return true
        }
        
        val lastUsed = globalCooldown.get() ?: return true
        val now = LocalDateTime.now()
        val minutesPassed = ChronoUnit.MINUTES.between(lastUsed, now)
        
        return minutesPassed >= cooldownMinutes
    }
    
    /**
     * Получает оставшееся время до следующего использования
     */
    fun getRemainingTime(@Suppress("UNUSED_PARAMETER") telegramId: Long): String {
        val lastUsed = globalCooldown.get() ?: return "0м"
        val now = LocalDateTime.now()
        val minutesPassed = ChronoUnit.MINUTES.between(lastUsed, now)
        val minutesRemaining = cooldownMinutes - minutesPassed
        
        return if (minutesRemaining > 0) {
            "${minutesRemaining}м"
        } else {
            "0м"
        }
    }
    
    /**
     * Устанавливает глобальный кулдаун (для всех игроков)
     */
    fun setCooldown(@Suppress("UNUSED_PARAMETER") telegramId: Long) {
        globalCooldown.set(LocalDateTime.now())
        saveCooldowns()
    }
    
    /**
     * Выбирает случайного игрока из онлайн
     * Использует улучшенный алгоритм случайного выбора для честного распределения
     * @return имя игрока или null, если нет онлайн игроков
     */
    fun selectRandomPlayer(): String? {
        // Получаем всех онлайн игроков (включая скрытых)
        val eligiblePlayers = Bukkit.getOnlinePlayers()
            .map { it.name }
            .toMutableList()
        
        if (eligiblePlayers.isEmpty()) {
            return null
        }
        
        // Если только один игрок, возвращаем его
        if (eligiblePlayers.size == 1) {
            return eligiblePlayers[0]
        }
        
        // Используем ThreadLocalRandom для лучшей производительности и случайности
        // Перемешиваем список для дополнительной случайности
        eligiblePlayers.shuffle(ThreadLocalRandom.current())
        
        // Выбираем случайного игрока из перемешанного списка
        val randomIndex = ThreadLocalRandom.current().nextInt(eligiblePlayers.size)
        return eligiblePlayers[randomIndex]
    }
    
    /**
     * Выбирает случайную награду из списка
     * Использует улучшенный алгоритм случайного выбора для честного распределения
     * @param rewards список команд-наград
     * @return случайная команда или null, если список пуст
     */
    fun selectRandomReward(rewards: List<String>): String? {
        if (rewards.isEmpty()) {
            return null
        }
        
        // Если только одна награда, возвращаем её
        if (rewards.size == 1) {
            return rewards[0]
        }
        
        // Создаем копию списка и перемешиваем для дополнительной случайности
        val shuffledRewards = rewards.toMutableList()
        shuffledRewards.shuffle(ThreadLocalRandom.current())
        
        // Выбираем случайную награду из перемешанного списка
        val randomIndex = ThreadLocalRandom.current().nextInt(shuffledRewards.size)
        return shuffledRewards[randomIndex]
    }
    
    /**
     * Загружает глобальный кулдаун из файла или БД
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
            val timestamp = ZTele.database.executeQuery(
                "SELECT timestamp FROM cooldowns WHERE type = ? AND identifier = ?",
                listOf("random", "global")
            ) { rs ->
                if (rs.next()) rs.getString("timestamp") else null
            }
            
            if (timestamp != null) {
                try {
                    globalCooldown.set(LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                } catch (e: Exception) {
                    plugin.logger.warning("Ошибка парсинга кулдауна рулетки: ${e.message}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки кулдауна рулетки из БД, переключаемся на YAML: ${e.message}")
            loadCooldownsFromYaml()
        }
    }
    
    private fun loadCooldownsFromYaml() {
        if (!cooldownFile.exists()) {
            cooldownFile.parentFile.mkdirs()
            cooldownFile.createNewFile()
        }
        
        cooldownConfig = YamlConfiguration.loadConfiguration(cooldownFile)
        
        // Загружаем глобальный кулдаун
        val timeString = cooldownConfig.getString("global_cooldown")
        if (timeString != null) {
            try {
                val dateTime = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                // Проверяем, не истек ли кулдаун (если старше 7 дней, игнорируем)
                val cutoff = LocalDateTime.now().minusDays(7)
                if (dateTime.isAfter(cutoff)) {
                    globalCooldown.set(dateTime)
                }
            } catch (e: Exception) {
                plugin.logger.warning("Failed to load global random cooldown: ${e.message}")
            }
        }
    }
    
    /**
     * Сохраняет глобальный кулдаун в файл или БД
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
            val lastUsed = globalCooldown.get()
            if (lastUsed != null) {
                ZTele.database.executeUpdate(
                    "INSERT OR REPLACE INTO cooldowns (type, identifier, timestamp) VALUES (?, ?, ?)",
                    listOf("random", "global", lastUsed.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                )
            } else {
                // Удаляем кулдаун, если он null
                ZTele.database.executeUpdate(
                    "DELETE FROM cooldowns WHERE type = ? AND identifier = ?",
                    listOf("random", "global")
                )
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения кулдауна рулетки в БД: ${e.message}")
            saveCooldownsToYaml() // Fallback на YAML
        }
    }
    
    private fun saveCooldownsToYaml() {
        try {
            cooldownConfig = YamlConfiguration()
            val lastUsed = globalCooldown.get()
            if (lastUsed != null) {
                cooldownConfig.set("global_cooldown", lastUsed.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            }
            
            cooldownConfig.save(cooldownFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save global random cooldown: ${e.message}")
        }
    }
    
    /**
     * Очищает истекший кулдаун
     */
    fun cleanup() {
        val lastUsed = globalCooldown.get() ?: return
        val cutoff = LocalDateTime.now().minusDays(7)
        if (lastUsed.isBefore(cutoff)) {
            globalCooldown.set(null)
            saveCooldowns()
        }
    }
}

