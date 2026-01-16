package org.zoobastiks.ztelegram.stats

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.bukkit.entity.Player

/**
 * Менеджер статистики игроков для команды /stats
 */
class StatsManager(private val plugin: ZTele) {
    
    // Хранилище логов входа игроков: UUID -> список входов
    val playerJoinLogs = ConcurrentHashMap<UUID, MutableList<PlayerJoinLog>>()
    
    // Хранилище активных сессий: UUID -> время входа
    private val activeSessions = ConcurrentHashMap<UUID, LocalDateTime>()
    
    // Хранилище времени последнего обновления статистики для активных сессий: UUID -> время последнего обновления
    private val lastUpdateTime = ConcurrentHashMap<UUID, LocalDateTime>()
    
    // Хранилище времени игры по дням: дата -> UUID -> время в минутах
    private val dailyPlaytime = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Long>>()
    
    // Файл для сохранения статистики
    private val statsFile = File(plugin.dataFolder, "stats.yml")
    private lateinit var statsConfig: YamlConfiguration
    
    // Задача для автоочистки статистики
    private var cleanupTaskId: Int = -1
    
    // Задача для ежедневных сводок
    private var dailySummaryTaskId: Int = -1
    
    // Задача для периодического обновления времени игры активных сессий
    private var playtimeUpdateTaskId: Int = -1
    
    init {
        loadStats()
        scheduleCleanup()
        scheduleDailySummary()
        schedulePlaytimeUpdate()
    }
    
    /**
     * Записывает вход игрока в статистику
     */
    fun recordPlayerJoin(playerUuid: UUID, playerName: String) {
        val now = LocalDateTime.now()
        val joinLog = PlayerJoinLog(playerName, now)
        
        playerJoinLogs.computeIfAbsent(playerUuid) { mutableListOf() }.add(joinLog)
        
        // Начинаем отслеживать сессию
        activeSessions[playerUuid] = now
        lastUpdateTime[playerUuid] = now
        
        // Сохраняем в БД или файл для постоянства данных
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            try {
                ZTele.database.executeUpdate(
                    "INSERT INTO stats_joins (uuid, player_name, join_time) VALUES (?, ?, ?)",
                    listOf(playerUuid.toString(), playerName, now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                )
            } catch (e: Exception) {
                plugin.logger.warning("Ошибка сохранения входа в БД: ${e.message}")
                saveStats() // Fallback
            }
        } else {
            saveStats()
        }
        
        plugin.logger.info("Recorded join for player: $playerName ($playerUuid)")
    }
    
    /**
     * Записывает выход игрока и подсчитывает время сессии
     */
    fun recordPlayerQuit(playerUuid: UUID, playerName: String) {
        val quitTime = LocalDateTime.now()
        val joinTime = activeSessions.remove(playerUuid)
        val lastUpdate = lastUpdateTime.remove(playerUuid)
        
        if (joinTime != null && lastUpdate != null) {
            // Подсчитываем длительность сессии с момента последнего обновления
            val sessionDuration = ChronoUnit.MINUTES.between(lastUpdate, quitTime)
            
            if (sessionDuration > 0) {
                val dateKey = quitTime.toLocalDate().toString()
                
                // Добавляем время к дневной статистике
                dailyPlaytime.computeIfAbsent(dateKey) { ConcurrentHashMap() }
                    .compute(playerUuid) { _, currentTime -> 
                        (currentTime ?: 0) + sessionDuration 
                    }
                
                plugin.logger.info("Recorded session for $playerName: $sessionDuration minutes")
                
                // Сохраняем в БД или файл
                if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
                    try {
                        // Получаем текущее время игры для этой даты
                        val currentMinutes = dailyPlaytime[dateKey]?.get(playerUuid) ?: 0L
                        ZTele.database.executeUpdate(
                            "INSERT OR REPLACE INTO stats_playtime (uuid, date, minutes) VALUES (?, ?, ?)",
                            listOf(playerUuid.toString(), dateKey, currentMinutes)
                        )
                    } catch (e: Exception) {
                        plugin.logger.warning("Ошибка сохранения времени игры в БД: ${e.message}")
                        saveStats() // Fallback
                    }
                } else {
                    saveStats()
                }
            }
        }
    }
    
    /**
     * Получает статистику уникальных игроков за указанный период
     */
    fun getStats(period: StatsPeriod): StatsResult {
        val now = LocalDateTime.now()
        val cutoffTime = when (period) {
            StatsPeriod.HOUR -> now.minus(1, ChronoUnit.HOURS)
            StatsPeriod.DAY -> now.minus(1, ChronoUnit.DAYS)
            StatsPeriod.WEEK -> now.minus(7, ChronoUnit.DAYS)
            StatsPeriod.MONTH -> now.minus(30, ChronoUnit.DAYS)
            StatsPeriod.TODAY -> LocalDate.now().atStartOfDay()
            StatsPeriod.CUSTOM -> now // CUSTOM период обрабатывается отдельным методом
        }
        
        val uniquePlayers = mutableSetOf<String>()
        
        for ((_, logs) in playerJoinLogs) {
            val relevantLogs = if (period == StatsPeriod.TODAY) {
                // For "today", we check if any join was today
                logs.filter { it.joinTime.toLocalDate() == LocalDate.now() }
            } else {
                // For other periods, we check joins after cutoff time
                logs.filter { it.joinTime.isAfter(cutoffTime) }
            }
            
            if (relevantLogs.isNotEmpty()) {
                // Используем последний известный никнейм игрока
                val latestLog = relevantLogs.maxByOrNull { it.joinTime }
                latestLog?.let { uniquePlayers.add(it.playerName) }
            }
        }
        
        return StatsResult(uniquePlayers.size, uniquePlayers.sorted())
    }
    
    /**
     * Получает количество новых игроков (которые впервые зашли на сервер) за указанную дату
     */
    fun getNewPlayersCount(date: LocalDate): Int {
        val newPlayers = mutableSetOf<UUID>()
        
        for ((uuid, logs) in playerJoinLogs) {
            // Проверяем, был ли первый вход игрока в указанную дату
            val firstJoin = logs.minByOrNull { it.joinTime }
            if (firstJoin != null && firstJoin.joinTime.toLocalDate() == date) {
                newPlayers.add(uuid)
            }
        }
        
        return newPlayers.size
    }
    
    /**
     * Получает топ игроков по времени игры за указанный период
     */
    fun getPlaytimeTop(period: StatsPeriod, limit: Int = 10): List<PlaytimeEntry> {
        val now = LocalDate.now()
        val startDate = when (period) {
            StatsPeriod.HOUR -> now // Для часа используем сегодняшний день
            StatsPeriod.DAY -> now.minusDays(1)
            StatsPeriod.WEEK -> now.minusDays(7)
            StatsPeriod.MONTH -> now.minusDays(30)
            StatsPeriod.TODAY -> now
            StatsPeriod.CUSTOM -> now // CUSTOM период обрабатывается отдельным методом
        }
        
        val playtimeMap = mutableMapOf<UUID, Long>()
        
        // Собираем время игры за период
        for ((dateStr, dayData) in dailyPlaytime) {
            val date = LocalDate.parse(dateStr)
            if (!date.isBefore(startDate) && !date.isAfter(now)) {
                for ((uuid, minutes) in dayData) {
                    playtimeMap[uuid] = (playtimeMap[uuid] ?: 0) + minutes
                }
            }
        }
        
        // Добавляем текущее время активных сессий, если они еще не были сохранены
        val currentDateTime = LocalDateTime.now()
        val todayKey = now.toString()
        for ((uuid, joinTime) in activeSessions) {
            val lastUpdate = lastUpdateTime[uuid] ?: joinTime
            val currentSessionMinutes = ChronoUnit.MINUTES.between(lastUpdate, currentDateTime)
            if (currentSessionMinutes > 0) {
                // Добавляем только если это сегодня (для периода TODAY) или если дата входит в период
                val sessionDate = lastUpdate.toLocalDate()
                if (!sessionDate.isBefore(startDate) && !sessionDate.isAfter(now)) {
                    playtimeMap[uuid] = (playtimeMap[uuid] ?: 0) + currentSessionMinutes
                }
            }
        }
        
        // Получаем имена игроков и сортируем по времени
        return playtimeMap.entries
            .mapNotNull { (uuid, minutes) ->
                val playerName = getLatestPlayerName(uuid)
                if (playerName != null) {
                    PlaytimeEntry(playerName, minutes)
                } else null
            }
            .sortedByDescending { it.minutes }
            .take(limit)
    }
    
    /**
     * Получает последнее известное имя игрока по UUID
     */
    private fun getLatestPlayerName(uuid: UUID): String? {
        return playerJoinLogs[uuid]?.lastOrNull()?.playerName
    }
    
    /**
     * Получает имя игрока по UUID
     */
    private fun getPlayerName(uuid: UUID): String? {
        // Сначала ищем в логах входа
        val logs = playerJoinLogs[uuid]
        if (!logs.isNullOrEmpty()) {
            return logs.last().playerName // Возвращаем последний известный никнейм
        }
        
        // Если в логах нет, пробуем получить из Bukkit
        val offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid)
        return if (offlinePlayer.hasPlayedBefore()) {
            offlinePlayer.name
        } else {
            null
        }
    }
    
    /**
     * Получает время игры конкретного игрока за указанный период
     */
    fun getPlayerPlaytime(playerName: String, period: StatsPeriod): Long {
        val player = Bukkit.getPlayer(playerName) ?: Bukkit.getOfflinePlayer(playerName)
        val uuid = player.uniqueId
        
        val now = LocalDate.now()
        val startDate = when (period) {
            StatsPeriod.HOUR -> now // Для часа используем сегодняшний день
            StatsPeriod.DAY -> now.minusDays(1)
            StatsPeriod.WEEK -> now.minusDays(7)
            StatsPeriod.MONTH -> now.minusDays(30)
            StatsPeriod.TODAY -> now
            StatsPeriod.CUSTOM -> now // CUSTOM период обрабатывается отдельным методом
        }
        
        var totalPlaytime = 0L
        
        // Собираем время игры за период
        for ((dateStr, dayData) in dailyPlaytime) {
            val date = LocalDate.parse(dateStr)
            if (!date.isBefore(startDate) && !date.isAfter(now)) {
                val minutes = dayData[uuid] ?: 0L
                totalPlaytime += minutes
            }
        }
        
        // Добавляем время текущей сессии, если игрок онлайн
        val activeSession = activeSessions[uuid]
        val lastUpdate = lastUpdateTime[uuid]
        if (activeSession != null && lastUpdate != null) {
            // Используем lastUpdate для более точного подсчета (учитываем уже сохраненное время)
            val sessionMinutes = ChronoUnit.MINUTES.between(lastUpdate, LocalDateTime.now())
            if (sessionMinutes > 0) {
                totalPlaytime += sessionMinutes
            }
        }
        
        return totalPlaytime
    }

    /**
     * Форматирует время в минутах в читаемый формат
     */
    fun formatPlaytime(minutes: Long): String {
        val days = minutes / (24 * 60)
        val hours = (minutes % (24 * 60)) / 60
        val mins = minutes % 60
        
        return when {
            days > 0 -> "${days}д ${hours}ч ${mins}м"
            hours > 0 -> "${hours}ч ${mins}м"
            else -> "${mins}м"
        }
    }
    
    /**
     * Очищает статистику (выполняется автоматически каждые 24 часа)
     */
    fun cleanupOldStats() {
        val cutoffTime = LocalDateTime.now().minus(31, ChronoUnit.DAYS) // Храним месяц данных
        var removedEntries = 0
        
        for ((_, logs) in playerJoinLogs) {
            val sizeBefore = logs.size
            logs.removeIf { it.joinTime.isBefore(cutoffTime) }
            removedEntries += sizeBefore - logs.size
        }
        
        // Удаляем пустые записи
        playerJoinLogs.entries.removeIf { it.value.isEmpty() }
        
        if (removedEntries > 0) {
            plugin.logger.info("Cleaned up $removedEntries old stats entries")
            saveStats()
        }
    }
    
    /**
     * Планирует автоматическую очистку статистики
     */
    private fun scheduleCleanup() {
        // Очистка каждые 24 часа (24 * 60 * 60 * 20 тиков)
        cleanupTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            { cleanupOldStats() },
            20L * 60L * 60L, // Задержка: 1 час
            20L * 60L * 60L * 24L // Период: 24 часа
        )
    }
    
    /**
     * Планирует ежедневные сводки
     */
    private fun scheduleDailySummary() {
        // Отправка сводки каждый день в 00:00 (через 24 часа после запуска)
        dailySummaryTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            { sendDailySummary() },
            20L * 60L * 60L * 24L, // Задержка: 24 часа
            20L * 60L * 60L * 24L // Период: 24 часа
        )
    }
    
    /**
     * Планирует периодическое обновление времени игры для активных сессий
     */
    private fun schedulePlaytimeUpdate() {
        // Обновление каждые 5 минут (5 * 60 * 20 тиков)
        playtimeUpdateTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
            plugin,
            { updateActiveSessionsPlaytime() },
            20L * 60L * 5L, // Задержка: 5 минут
            20L * 60L * 5L // Период: 5 минут
        )
    }
    
    /**
     * Обновляет время игры для всех активных сессий
     */
    private fun updateActiveSessionsPlaytime() {
        val now = LocalDateTime.now()
        val dateKey = now.toLocalDate().toString()
        
        // Создаем копию списка активных сессий для безопасной итерации
        val sessionsToUpdate = activeSessions.keys.toList()
        
        for (uuid in sessionsToUpdate) {
            val lastUpdate = lastUpdateTime[uuid]
            if (lastUpdate != null) {
                // Вычисляем время с момента последнего обновления
                val elapsedMinutes = ChronoUnit.MINUTES.between(lastUpdate, now)
                
                if (elapsedMinutes > 0) {
                    // Получаем имя игрока для логирования
                    val playerName = getLatestPlayerName(uuid) ?: "Unknown"
                    
                    // Добавляем время к дневной статистике
                    dailyPlaytime.computeIfAbsent(dateKey) { ConcurrentHashMap() }
                        .compute(uuid) { _, currentTime -> 
                            (currentTime ?: 0) + elapsedMinutes 
                        }
                    
                    // Обновляем время последнего обновления
                    lastUpdateTime[uuid] = now
                    
                    if (ZTele.conf.debugEnabled) {
                        plugin.logger.info("Updated playtime for $playerName: +$elapsedMinutes minutes (total: ${dailyPlaytime[dateKey]?.get(uuid) ?: 0} minutes)")
                    }
                    
                    // Сохраняем в БД или файл
                    if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
                        try {
                            val currentMinutes = dailyPlaytime[dateKey]?.get(uuid) ?: 0L
                            ZTele.database.executeUpdate(
                                "INSERT OR REPLACE INTO stats_playtime (uuid, date, minutes) VALUES (?, ?, ?)",
                                listOf(uuid.toString(), dateKey, currentMinutes)
                            )
                        } catch (e: Exception) {
                            plugin.logger.warning("Ошибка сохранения времени игры в БД для $playerName: ${e.message}")
                        }
                    }
                }
            }
        }
        
        // Сохраняем статистику в файл, если БД не используется
        if (!ZTele.conf.databaseEnabled) {
            saveStats()
        }
    }
    
    /**
     * Отправляет ежедневную сводку в Telegram
     */
    private fun sendDailySummary() {
        try {
            val yesterday = LocalDate.now().minusDays(1)
            val yesterdayStats = getStats(StatsPeriod.DAY)
            val newPlayersCount = getNewPlayersCount(yesterday)
            val playtimeTop = getPlaytimeTop(StatsPeriod.DAY, 10)
            
            // Отправляем сводку через бота с количеством новых игроков
            ZTele.bot.sendDailySummary(yesterdayStats, playtimeTop, yesterday, newPlayersCount)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send daily summary: ${e.message}")
        }
    }
    
    /**
     * Останавливает задачи очистки
     */
    fun shutdown() {
        if (cleanupTaskId != -1) {
            Bukkit.getScheduler().cancelTask(cleanupTaskId)
            cleanupTaskId = -1
        }
        if (dailySummaryTaskId != -1) {
            Bukkit.getScheduler().cancelTask(dailySummaryTaskId)
            dailySummaryTaskId = -1
        }
        if (playtimeUpdateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(playtimeUpdateTaskId)
            playtimeUpdateTaskId = -1
        }
        
        // Обновляем время игры для всех активных сессий перед завершением
        updateActiveSessionsPlaytime()
        
        // Сохраняем активные сессии как завершенные (на случай, если что-то осталось)
        val now = LocalDateTime.now()
        for ((uuid, joinTime) in activeSessions) {
            val lastUpdate = lastUpdateTime[uuid] ?: joinTime
            val sessionDuration = ChronoUnit.MINUTES.between(lastUpdate, now)
            
            if (sessionDuration > 0) {
                val dateKey = now.toLocalDate().toString()
                dailyPlaytime.computeIfAbsent(dateKey) { ConcurrentHashMap() }
                    .compute(uuid) { _, currentTime -> 
                        (currentTime ?: 0) + sessionDuration 
                    }
            }
        }
        activeSessions.clear()
        lastUpdateTime.clear()
        
        saveStats()
    }
    
    /**
     * Загружает статистику из файла или БД
     */
    private fun loadStats() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            loadStatsFromDatabase()
        } else {
            loadStatsFromYaml()
        }
    }
    
    private fun loadStatsFromDatabase() {
        try {
            // Загружаем логи входов
            ZTele.database.executeQuery("SELECT uuid, player_name, join_time FROM stats_joins ORDER BY join_time DESC") { rs ->
                while (rs.next()) {
                    try {
                        val uuid = UUID.fromString(rs.getString("uuid"))
                        val playerName = rs.getString("player_name")
                        val joinTime = LocalDateTime.parse(rs.getString("join_time"), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        
                        playerJoinLogs.computeIfAbsent(uuid) { mutableListOf() }
                            .add(PlayerJoinLog(playerName, joinTime))
                    } catch (e: Exception) {
                        plugin.logger.warning("Ошибка загрузки лога входа: ${e.message}")
                    }
                }
            }
            
            // Загружаем время игры
            ZTele.database.executeQuery("SELECT uuid, date, minutes FROM stats_playtime") { rs ->
                while (rs.next()) {
                    try {
                        val uuid = UUID.fromString(rs.getString("uuid"))
                        val date = rs.getString("date")
                        val minutes = rs.getLong("minutes")
                        
                        dailyPlaytime.computeIfAbsent(date) { ConcurrentHashMap() }[uuid] = minutes
                    } catch (e: Exception) {
                        plugin.logger.warning("Ошибка загрузки времени игры: ${e.message}")
                    }
                }
            }
            
            plugin.logger.info("Загружена статистика из БД: ${playerJoinLogs.size} игроков, ${dailyPlaytime.size} дней")
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки статистики из БД, переключаемся на YAML: ${e.message}")
            loadStatsFromYaml()
        }
    }
    
    private fun loadStatsFromYaml() {
        if (!statsFile.exists()) {
            plugin.saveResource("stats.yml", false)
        }
        
        statsConfig = YamlConfiguration.loadConfiguration(statsFile)
        
        // Загружаем данные из файла
        val playersSection = statsConfig.getConfigurationSection("players")
        if (playersSection != null) {
            for (uuidString in playersSection.getKeys(false)) {
                try {
                    val uuid = UUID.fromString(uuidString)
                    val playerSection = playersSection.getConfigurationSection(uuidString)
                    
                    if (playerSection != null) {
                        val logs = mutableListOf<PlayerJoinLog>()
                        val joinsSection = playerSection.getConfigurationSection("joins")
                        
                        if (joinsSection != null) {
                            for (joinKey in joinsSection.getKeys(false)) {
                                val joinSection = joinsSection.getConfigurationSection(joinKey)
                                if (joinSection != null) {
                                    val playerName = joinSection.getString("name") ?: continue
                                    val timeString = joinSection.getString("time") ?: continue
                                    
                                    try {
                                        val joinTime = LocalDateTime.parse(timeString, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                        logs.add(PlayerJoinLog(playerName, joinTime))
                                    } catch (e: Exception) {
                                        plugin.logger.warning("Failed to parse join time: $timeString")
                                    }
                                }
                            }
                        }
                        
                        if (logs.isNotEmpty()) {
                            playerJoinLogs[uuid] = logs
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to load stats for UUID: $uuidString - ${e.message}")
                }
            }
        }
        
        // Загружаем данные времени игры
        val playtimeSection = statsConfig.getConfigurationSection("playtime")
        if (playtimeSection != null) {
            for (dateKey in playtimeSection.getKeys(false)) {
                val daySection = playtimeSection.getConfigurationSection(dateKey)
                if (daySection != null) {
                    val dayMap = ConcurrentHashMap<UUID, Long>()
                    for (uuidString in daySection.getKeys(false)) {
                        try {
                            val uuid = UUID.fromString(uuidString)
                            val minutes = daySection.getLong(uuidString)
                            if (minutes > 0) {
                                dayMap[uuid] = minutes
                            }
                        } catch (e: Exception) {
                            plugin.logger.warning("Failed to load playtime for UUID: $uuidString")
                        }
                    }
                    if (dayMap.isNotEmpty()) {
                        dailyPlaytime[dateKey] = dayMap
                    }
                }
            }
        }
        
        plugin.logger.info("Loaded stats for ${playerJoinLogs.size} players and ${dailyPlaytime.size} days of playtime data")
    }
    
    /**
     * Сохраняет статистику в файл или БД
     */
    private fun saveStats() {
        if (ZTele.conf.databaseEnabled && ZTele.database.databaseExists()) {
            saveStatsToDatabase()
        } else {
            saveStatsToYaml()
        }
    }
    
    private fun saveStatsToDatabase() {
        try {
            // Сохраняем только новые логи входов (старые уже в БД)
            // В реальности, recordPlayerJoin уже сохраняет каждый вход, так что здесь можно ничего не делать
            // Но для надежности сохраним все
            
            // Сохраняем время игры
            ZTele.database.executeTransaction { conn ->
                for ((dateKey, dayData) in dailyPlaytime) {
                    for ((uuid, minutes) in dayData) {
                        conn.prepareStatement("""
                            INSERT OR REPLACE INTO stats_playtime (uuid, date, minutes)
                            VALUES (?, ?, ?)
                        """).use { stmt ->
                            stmt.setString(1, uuid.toString())
                            stmt.setString(2, dateKey)
                            stmt.setLong(3, minutes)
                            stmt.executeUpdate()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения статистики в БД: ${e.message}")
            saveStatsToYaml() // Fallback на YAML
        }
    }
    
    private fun saveStatsToYaml() {
        try {
            // Очищаем существующие данные
            statsConfig.set("players", null)
            statsConfig.set("playtime", null)
            
            // Сохраняем данные входов
            for ((uuid, logs) in playerJoinLogs) {
                val uuidString = uuid.toString()
                var joinIndex = 0
                
                for (log in logs) {
                    val path = "players.$uuidString.joins.join_$joinIndex"
                    statsConfig.set("$path.name", log.playerName)
                    statsConfig.set("$path.time", log.joinTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    joinIndex++
                }
            }
            
            // Сохраняем данные времени игры
            for ((dateKey, dayData) in dailyPlaytime) {
                for ((uuid, minutes) in dayData) {
                    val path = "playtime.$dateKey.${uuid}"
                    statsConfig.set(path, minutes)
                }
            }
            
            statsConfig.save(statsFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save stats: ${e.message}")
        }
    }
    
    /**
     * Перезагружает конфигурацию
     */
    fun reload() {
        saveStats()
        playerJoinLogs.clear()
        loadStats()
    }
    
    /**
     * Лог входа игрока
     */
    data class PlayerJoinLog(
        val playerName: String,
        val joinTime: LocalDateTime
    )
    
    /**
     * Результат статистики
     */
    data class StatsResult(
        val count: Int,
        val players: List<String>
    )
    
    /**
     * Запись топа по времени игры
     */
    data class PlaytimeEntry(
        val playerName: String,
        val minutes: Long
    )
    
    /**
     * Периоды для статистики
     */
    enum class StatsPeriod(val displayName: String) {
        HOUR("час"),
        DAY("день"),
        WEEK("неделю"),
        MONTH("месяц"),
        TODAY("сегодня"),
        CUSTOM("указанный период")
    }
    
    /**
     * Парсит строку периода в количество часов
     * Поддерживает форматы: 1h, 24h, 1d, 7d, 1w, 4w, 1m
     */
    fun parsePeriodToHours(periodStr: String): Long? {
        val cleanPeriod = periodStr.lowercase().trim()
        
        return try {
            when {
                cleanPeriod.endsWith("h") || cleanPeriod.endsWith("ч") -> {
                    val hours = cleanPeriod.dropLast(1).toLong()
                    if (hours > 0) hours else null
                }
                cleanPeriod.endsWith("d") || cleanPeriod.endsWith("д") -> {
                    val days = cleanPeriod.dropLast(1).toLong()
                    if (days > 0) days * 24 else null
                }
                cleanPeriod.endsWith("w") || cleanPeriod.endsWith("н") -> {
                    val weeks = cleanPeriod.dropLast(1).toLong()
                    if (weeks > 0) weeks * 24 * 7 else null
                }
                cleanPeriod.endsWith("m") || cleanPeriod.endsWith("м") -> {
                    val months = cleanPeriod.dropLast(1).toLong()
                    if (months > 0) months * 24 * 30 else null // Приблизительно 30 дней в месяце
                }
                else -> null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * Получает топ игроков по времени игры за произвольный период
     */
    fun getPlaytimeTopCustom(periodStr: String, limit: Int = 10): List<PlaytimeEntry> {
        val hours = parsePeriodToHours(periodStr) ?: return emptyList()
        val cutoffTime = LocalDateTime.now().minusHours(hours)
        
        val playtimeMap = mutableMapOf<UUID, Long>()
        val playerNames = mutableMapOf<UUID, String>()
        
        // Собираем время игры за указанный период
        for ((dateStr, dayData) in dailyPlaytime) {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE)
            val dateTime = date.atStartOfDay()
            
            if (dateTime.isAfter(cutoffTime) || dateTime.isEqual(cutoffTime)) {
                for ((uuid, minutes) in dayData) {
                    playtimeMap[uuid] = playtimeMap.getOrDefault(uuid, 0L) + minutes
                    
                    // Получаем имя игрока
                    val playerName = getPlayerName(uuid)
                    if (playerName != null) {
                        playerNames[uuid] = playerName
                    }
                }
            }
        }
        
        // Добавляем время активных сессий
        val now = LocalDateTime.now()
        for ((uuid, startTime) in activeSessions) {
            if (startTime.isAfter(cutoffTime)) {
                val sessionMinutes = ChronoUnit.MINUTES.between(startTime, now)
                playtimeMap[uuid] = playtimeMap.getOrDefault(uuid, 0L) + sessionMinutes
                
                val playerName = getPlayerName(uuid)
                if (playerName != null) {
                    playerNames[uuid] = playerName
                }
            }
        }
        
        // Сортируем и возвращаем топ
        return playtimeMap.entries
            .filter { playerNames.containsKey(it.key) }
            .sortedByDescending { it.value }
            .take(limit)
            .map { PlaytimeEntry(playerNames[it.key]!!, it.value) }
    }
    
    /**
     * Получает статистику уникальных игроков за произвольный период
     */
    fun getUniquePlayersCustom(periodStr: String): StatsResult {
        val hours = parsePeriodToHours(periodStr) ?: return StatsResult(0, emptyList())
        val cutoffTime = LocalDateTime.now().minusHours(hours)
        
        val uniquePlayers = mutableSetOf<String>()
        
        // Проверяем логи входа
        for ((_, joinLogs) in playerJoinLogs) {
            for (log in joinLogs) {
                if (log.joinTime.isAfter(cutoffTime)) {
                    uniquePlayers.add(log.playerName)
                }
            }
        }
        
        return StatsResult(
            count = uniquePlayers.size,
            players = uniquePlayers.toList().sorted()
        )
    }
    
    /**
     * Форматирует период для отображения
     */
    fun formatPeriodDisplay(periodStr: String): String {
        val hours = parsePeriodToHours(periodStr) ?: return periodStr
        
        return when {
            hours < 24 -> "${hours}ч"
            hours == 24L -> "1д"
            hours % (24 * 7) == 0L -> "${hours / (24 * 7)}н"
            hours % 24 == 0L -> "${hours / 24}д"
            else -> "${hours}ч"
        }
    }
}
