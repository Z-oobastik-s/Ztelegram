package org.zoobastiks.ztelegram.reputation

import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Профессиональный менеджер системы репутации
 *
 * Функции:
 * - Управление репутацией игроков
 * - Кулдауны на выдачу репутации
 * - Обход кулдаунов для администраторов
 * - Статистика и топы
 * - Сохранение/загрузка данных
 *
 * @author Zoobastiks
 */
class ReputationManager(private val plugin: ZTele) {

    private val reputationData = ConcurrentHashMap<String, ReputationData>()
    private val reputationFile: File = File(plugin.dataFolder, "reputation.yml")
    private var config: YamlConfiguration = YamlConfiguration()

    // Настройки из конфига
    var cooldownMinutes: Int = 30
        private set
    var allowSelfReputation: Boolean = false
        private set
    var requireReason: Boolean = false
        private set
    var minReasonLength: Int = 3
        private set
    var maxReasonLength: Int = 100
        private set
    var enableNotifications: Boolean = true
        private set
    var showReasonInNotification: Boolean = true
        private set

    init {
        loadConfig()
        loadData()
    }

    /**
     * Загружает конфигурацию системы репутации
     */
    private fun loadConfig() {
        val mainConfig = plugin.config

        cooldownMinutes = mainConfig.getInt("reputation.cooldown_minutes", 30)
        allowSelfReputation = mainConfig.getBoolean("reputation.allow_self_reputation", false)
        requireReason = mainConfig.getBoolean("reputation.require_reason", false)
        minReasonLength = mainConfig.getInt("reputation.min_reason_length", 3)
        maxReasonLength = mainConfig.getInt("reputation.max_reason_length", 100)
        enableNotifications = mainConfig.getBoolean("reputation.enable_notifications", true)
        showReasonInNotification = mainConfig.getBoolean("reputation.show_reason_in_notification", true)

        plugin.logger.info("Reputation system loaded: cooldown=${cooldownMinutes}m, selfRep=$allowSelfReputation")
    }

    /**
     * Загружает данные репутации из файла или БД
     */
    fun loadData() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            loadDataFromDatabase()
        } else {
            loadDataFromYaml()
        }
    }
    
    private fun loadDataFromDatabase() {
        try {
            reputationData.clear()
            
            // Загружаем основную информацию о репутации
            ZTele.database.executeQuery("SELECT player_name, positive, negative FROM reputation") { rs ->
                while (rs.next()) {
                    val playerName = rs.getString("player_name")
                    val data = ReputationData(
                        playerName = playerName,
                        positiveRep = rs.getInt("positive"),
                        negativeRep = rs.getInt("negative")
                    )
                    
                    // Загружаем историю
                    ZTele.database.executeQuery(
                        "SELECT source, is_positive, timestamp, reason FROM reputation_history WHERE player_name = ? ORDER BY timestamp DESC",
                        listOf(playerName.lowercase())
                    ) { historyRs ->
                        while (historyRs.next()) {
                            try {
                                val timestamp = LocalDateTime.parse(historyRs.getString("timestamp"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                val entry = ReputationEntry(
                                    source = historyRs.getString("source"),
                                    isPositive = historyRs.getInt("is_positive") == 1,
                                    timestamp = timestamp,
                                    reason = historyRs.getString("reason")
                                )
                                data.receivedFrom[entry.source] = entry
                            } catch (e: Exception) {
                                plugin.logger.warning("Ошибка парсинга истории репутации для $playerName: ${e.message}")
                            }
                        }
                    }
                    
                    reputationData[playerName.lowercase()] = data
                }
            }
            
            plugin.logger.info("Загружена репутация для ${reputationData.size} игроков из БД")
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки репутации из БД, переключаемся на YAML: ${e.message}")
            loadDataFromYaml()
        }
    }
    
    private fun loadDataFromYaml() {
        if (!reputationFile.exists()) {
            plugin.logger.info("Reputation file not found, creating new one")
            saveData()
            return
        }

        try {
            config = YamlConfiguration.loadConfiguration(reputationFile)
            reputationData.clear()

            val playersSection = config.getConfigurationSection("players") ?: return

            for (playerName in playersSection.getKeys(false)) {
                val playerSection = playersSection.getConfigurationSection(playerName) ?: continue

                val data = ReputationData(
                    playerName = playerName,
                    positiveRep = playerSection.getInt("positive", 0),
                    negativeRep = playerSection.getInt("negative", 0)
                )

                // Загружаем историю
                val historySection = playerSection.getConfigurationSection("history")
                if (historySection != null) {
                    for (source in historySection.getKeys(false)) {
                        val entrySection = historySection.getConfigurationSection(source) ?: continue

                        val isPositive = entrySection.getBoolean("positive", true)
                        val timestampStr = entrySection.getString("timestamp") ?: continue
                        val reason = entrySection.getString("reason")

                        try {
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            val timestamp = LocalDateTime.parse(timestampStr, formatter)

                            data.receivedFrom[source] = ReputationEntry(
                                source = source,
                                isPositive = isPositive,
                                timestamp = timestamp,
                                reason = reason
                            )
                        } catch (e: Exception) {
                            plugin.logger.warning("Failed to parse timestamp for $playerName from $source: $timestampStr")
                        }
                    }
                }

                reputationData[playerName.lowercase()] = data
            }

            plugin.logger.info("Loaded reputation data for ${reputationData.size} players")

        } catch (e: Exception) {
            plugin.logger.severe("Failed to load reputation data: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Сохраняет данные репутации в файл или БД
     */
    fun saveData() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            saveDataToDatabase()
        } else {
            saveDataToYaml()
        }
    }
    
    private fun saveDataToDatabase() {
        try {
            ZTele.database.executeTransaction { conn ->
                for ((playerKey, data) in reputationData) {
                    // Сохраняем основную информацию
                    conn.prepareStatement("""
                        INSERT OR REPLACE INTO reputation (player_name, positive, negative, updated_at)
                        VALUES (?, ?, ?, datetime('now'))
                    """).use { stmt ->
                        stmt.setString(1, playerKey)
                        stmt.setInt(2, data.positiveRep)
                        stmt.setInt(3, data.negativeRep)
                        stmt.executeUpdate()
                    }
                    
                    // Сохраняем историю (удаляем старую и вставляем новую)
                    conn.prepareStatement("DELETE FROM reputation_history WHERE player_name = ?").use { stmt ->
                        stmt.setString(1, playerKey)
                        stmt.executeUpdate()
                    }
                    
                    for ((source, entry) in data.receivedFrom) {
                        conn.prepareStatement("""
                            INSERT INTO reputation_history (player_name, source, is_positive, timestamp, reason)
                            VALUES (?, ?, ?, ?, ?)
                        """).use { stmt ->
                            stmt.setString(1, playerKey)
                            stmt.setString(2, source)
                            stmt.setInt(3, if (entry.isPositive) 1 else 0)
                            stmt.setString(4, entry.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            stmt.setString(5, entry.reason)
                            stmt.executeUpdate()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения репутации в БД: ${e.message}")
            saveDataToYaml() // Fallback на YAML
        }
    }
    
    private fun saveDataToYaml() {
        try {
            config = YamlConfiguration()

            for ((playerKey, data) in reputationData) {
                val playerSection = config.createSection("players.$playerKey")
                playerSection.set("positive", data.positiveRep)
                playerSection.set("negative", data.negativeRep)

                // Сохраняем историю
                if (data.receivedFrom.isNotEmpty()) {
                    val historySection = playerSection.createSection("history")

                    for ((source, entry) in data.receivedFrom) {
                        val entrySection = historySection.createSection(source)
                        entrySection.set("positive", entry.isPositive)

                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        entrySection.set("timestamp", entry.timestamp.format(formatter))

                        if (entry.reason != null) {
                            entrySection.set("reason", entry.reason)
                        }
                    }
                }
            }

            config.save(reputationFile)

        } catch (e: Exception) {
            plugin.logger.severe("Failed to save reputation data: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Получает данные репутации игрока
     */
    fun getReputationData(playerName: String): ReputationData {
        val key = playerName.lowercase()
        return reputationData.getOrPut(key) {
            ReputationData(playerName)
        }
    }

    /**
     * Проверяет, может ли источник дать репутацию цели
     */
    fun canGiveReputation(source: String, target: String, isAdmin: Boolean = false): ReputationResult {
        // Проверка на самого себя
        if (!allowSelfReputation && source.equals(target, ignoreCase = true)) {
            return ReputationResult.Failure("Вы не можете изменить свою репутацию!")
        }

        // Администраторы обходят кулдаун
        if (isAdmin) {
            return ReputationResult.Success
        }

        // Проверка кулдауна
        val targetData = getReputationData(target)
        if (!targetData.canReceiveFrom(source, cooldownMinutes)) {
            val entry = targetData.getLastEntryFrom(source)
            val remaining = entry?.getRemainingCooldown(cooldownMinutes) ?: 0
            return ReputationResult.Cooldown(remaining)
        }

        return ReputationResult.Success
    }

    /**
     * Добавляет положительную репутацию
     */
    fun addPositiveReputation(
        source: String,
        target: String,
        reason: String? = null,
        isAdmin: Boolean = false
    ): ReputationResult {
        // Валидация причины
        if (requireReason && reason.isNullOrBlank()) {
            return ReputationResult.Failure("Необходимо указать причину!")
        }

        if (reason != null) {
            if (reason.length < minReasonLength) {
                return ReputationResult.Failure("Причина слишком короткая (минимум $minReasonLength символов)")
            }
            if (reason.length > maxReasonLength) {
                return ReputationResult.Failure("Причина слишком длинная (максимум $maxReasonLength символов)")
            }
        }

        // Проверка возможности
        val canGive = canGiveReputation(source, target, isAdmin)
        if (canGive !is ReputationResult.Success) {
            return canGive
        }

        // Добавляем репутацию
        val targetData = getReputationData(target)
        targetData.addPositive(source, reason)

        saveData()

        return ReputationResult.SuccessWithData(
            targetData = targetData,
            isPositive = true,
            reason = reason
        )
    }

    /**
     * Добавляет отрицательную репутацию
     */
    fun addNegativeReputation(
        source: String,
        target: String,
        reason: String? = null,
        isAdmin: Boolean = false
    ): ReputationResult {
        // Валидация причины
        if (requireReason && reason.isNullOrBlank()) {
            return ReputationResult.Failure("Необходимо указать причину!")
        }

        if (reason != null) {
            if (reason.length < minReasonLength) {
                return ReputationResult.Failure("Причина слишком короткая (минимум $minReasonLength символов)")
            }
            if (reason.length > maxReasonLength) {
                return ReputationResult.Failure("Причина слишком длинная (максимум $maxReasonLength символов)")
            }
        }

        // Проверка возможности
        val canGive = canGiveReputation(source, target, isAdmin)
        if (canGive !is ReputationResult.Success) {
            return canGive
        }

        // Добавляем репутацию
        val targetData = getReputationData(target)
        targetData.addNegative(source, reason)

        saveData()

        return ReputationResult.SuccessWithData(
            targetData = targetData,
            isPositive = false,
            reason = reason
        )
    }

    /**
     * Получает топ игроков по репутации
     */
    fun getTopPlayers(limit: Int = 10, sortBy: SortType = SortType.TOTAL): List<Pair<String, ReputationData>> {
        return reputationData.values
            .sortedByDescending { data ->
                when (sortBy) {
                    SortType.TOTAL -> data.totalReputation
                    SortType.POSITIVE -> data.positiveRep
                    SortType.NEGATIVE -> data.negativeRep
                    SortType.PERCENTAGE -> data.positivePercentage.toInt()
                }
            }
            .take(limit)
            .map { it.playerName to it }
    }

    /**
     * Получает статистику системы репутации
     */
    fun getStatistics(): ReputationStatistics {
        val totalPlayers = reputationData.size
        val totalPositive = reputationData.values.sumOf { it.positiveRep }
        val totalNegative = reputationData.values.sumOf { it.negativeRep }
        val averageReputation = if (totalPlayers > 0) {
            reputationData.values.sumOf { it.totalReputation } / totalPlayers.toDouble()
        } else 0.0

        return ReputationStatistics(
            totalPlayers = totalPlayers,
            totalPositive = totalPositive,
            totalNegative = totalNegative,
            averageReputation = averageReputation
        )
    }

    /**
     * Получает последние изменения репутации
     */
    fun getRecentChanges(limit: Int = 10): List<Pair<String, ReputationEntry>> {
        val allEntries = mutableListOf<Pair<String, ReputationEntry>>()

        for ((playerName, data) in reputationData) {
            for ((_, entry) in data.receivedFrom) {
                allEntries.add(playerName to entry)
            }
        }

        return allEntries
            .sortedByDescending { it.second.timestamp }
            .take(limit)
    }

    /**
     * Сбрасывает репутацию игрока
     */
    fun resetReputation(playerName: String): Boolean {
        val key = playerName.lowercase()
        return if (reputationData.remove(key) != null) {
            saveData()
            true
        } else {
            false
        }
    }

    /**
     * Типы сортировки
     */
    enum class SortType {
        TOTAL,      // По общей репутации
        POSITIVE,   // По положительной
        NEGATIVE,   // По отрицательной
        PERCENTAGE  // По проценту положительной
    }
}

/**
 * Результат операции с репутацией
 */
sealed class ReputationResult {
    object Success : ReputationResult()

    data class SuccessWithData(
        val targetData: ReputationData,
        val isPositive: Boolean,
        val reason: String?
    ) : ReputationResult()

    data class Failure(val message: String) : ReputationResult()

    data class Cooldown(val remainingMinutes: Long) : ReputationResult()
}

/**
 * Статистика системы репутации
 */
data class ReputationStatistics(
    val totalPlayers: Int,
    val totalPositive: Int,
    val totalNegative: Int,
    val averageReputation: Double
)
