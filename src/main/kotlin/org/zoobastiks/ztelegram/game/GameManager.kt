package org.zoobastiks.ztelegram.game

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.utils.PlaceholderEngine
import java.io.File
import java.util.*
import kotlin.random.Random

class GameManager(private val plugin: ZTele) {
    private val gameFile = File(plugin.dataFolder, "game.yml")
    private val statsFile = File(plugin.dataFolder, "game_stats.yml")
    private lateinit var gameConfig: FileConfiguration
    private lateinit var statsConfig: FileConfiguration
    
    // Настройки игры
    var enabled: Boolean = true
    var gameCommandEnabled: Boolean = true
    var baseReward: Double = 5.0
    var speedBonus: Double = 1.0
    var maxBonus: Double = 10.0
    var rewardCommands: List<String> = listOf("eco give %player% 5")
    
    // Активные игры: telegramUsername -> GameSession
    private val activeGames = mutableMapOf<String, GameSession>()
    
    // Простая статистика игр: telegramUsername -> GameStats
    private val gameStats = mutableMapOf<String, GameStats>()
    
    // Слова для игры (разделены по длине)
    private var wordsList: List<WordPair> = listOf()
    private var wordsByLength: Map<String, List<WordPair>> = mapOf()
    
    // Класс для хранения пары слов (оригинал и с пропусками)
    data class WordPair(val original: String, val masked: String)
    
    // Класс для хранения информации об активной игре
    data class GameSession(
        val playerName: String,
        val wordPair: WordPair,
        val startTime: Long,
        var taskId: Int = -1
    )
    
    // Класс для хранения статистики игрока
    data class GameStats(
        var totalGames: Int = 0,
        var wins: Int = 0,
        var losses: Int = 0,
        var totalEarned: Double = 0.0,
        var totalTime: Long = 0L // Общее время ответов в миллисекундах
    ) {
        val winRate: Int get() = if (totalGames > 0) (wins * 100 / totalGames) else 0
        val avgTime: Long get() = if (wins > 0) totalTime / wins else 0L
    }
    
    init {
        if (!gameFile.exists()) {
            plugin.saveResource("game.yml", false)
        }
        
        // Создаем файл статистики только если база данных отключена
        if (!ZTele.conf.databaseEnabled && !statsFile.exists()) {
            try {
                statsFile.createNewFile()
            } catch (e: Exception) {
                plugin.logger.warning("Failed to create game stats file: ${e.message}")
            }
        }
        
        loadConfig()
        loadStats()
    }
    
    fun reload() {
        loadConfig()
        loadStats()
    }
    
    private fun loadConfig() {
        try {
            gameConfig = YamlConfiguration.loadConfiguration(gameFile)
            
            // Загружаем основные настройки
            enabled = gameConfig.getBoolean("enabled", true)
            gameCommandEnabled = gameConfig.getBoolean("command.enabled", true)
            
            // Загружаем настройки наград из новой структуры
            baseReward = gameConfig.getDouble("rewards.base_reward", 5.0)
            speedBonus = gameConfig.getDouble("rewards.speed_bonus", 1.0)
            maxBonus = gameConfig.getDouble("rewards.max_bonus", 10.0)
            rewardCommands = gameConfig.getStringList("rewards.commands")
            if (rewardCommands.isEmpty()) {
                rewardCommands = listOf("eco give %player% 5")
            }
            
            // Загружаем слова для игры из новой структуры (по длине)
            val wordsList = mutableListOf<WordPair>()
            val wordsByLengthMap = mutableMapOf<String, MutableList<WordPair>>()
            
            // Загружаем слова по длине
            val lengthCategories = listOf("length_3", "length_4", "length_5", "length_6", "length_7", "length_8", "length_9_plus")
            for (category in lengthCategories) {
                val categoryWords = gameConfig.getStringList("words.$category")
                val categoryWordPairs = mutableListOf<WordPair>()
                
                for (word in categoryWords) {
                    val maskedWord = createMaskedWord(word)
                    val wordPair = WordPair(word, maskedWord)
                    wordsList.add(wordPair)
                    categoryWordPairs.add(wordPair)
                }
                
                if (categoryWordPairs.isNotEmpty()) {
                    wordsByLengthMap[category] = categoryWordPairs
                }
            }
            
            this.wordsByLength = wordsByLengthMap
            
            if (wordsList.isEmpty()) {
                // Если нет слов в конфиге, добавляем стандартные
                val defaultWords = listOf(
                    WordPair("Буратино утопился", "Бу__тин_ _топ_лс_"),
                    WordPair("Колобок повесился", "К_ло_ок п_в_си_ся"),
                    WordPair("Красная шапочка", "Кр__ная ш_по_ка"),
                    WordPair("Серый волк", "С_р_й в_лк"),
                    WordPair("Minecraft сервер", "M_n_cr_ft с_рв_р"),
                    WordPair("Телеграм бот", "Т_л_гр_м б_т"),
                    WordPair("Золотой ключик", "З_л_т_й кл_ч_к"),
                    WordPair("Зеленый огр", "З_л_н_й о_р"),
                    WordPair("Подземелье дракона", "П_дз_м_ль_ др_к_на"),
                    WordPair("Волшебная палочка", "В_лш_бн_я п_л_чк_")
                )
                wordsList.addAll(defaultWords)
                
                // Сохраняем стандартные слова в конфиг
                saveDefaultWords(defaultWords)
            }
            
            this.wordsList = wordsList
            
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load game.yml: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Создает маскированную версию слова с улучшенными подсказками
     */
    private fun createMaskedWord(originalWord: String): String {
        if (originalWord.length <= 3) {
            // Для коротких слов показываем только первую букву
            return originalWord.first() + "_".repeat(originalWord.length - 1)
        }
        
        val masked = StringBuilder()
        val words = originalWord.split(" ")
        
        for (i in words.indices) {
            val word = words[i]
            if (word.length <= 2) {
                // Короткие слова (предлоги и т.д.) показываем полностью
                masked.append(word)
            } else {
                // Создаем массив символов для маскировки
                val chars = word.toCharArray()
                val result = CharArray(word.length) { '_' }
                
                when (word.length) {
                    3 -> {
                        // Для слов из 3 букв: показываем первую и последнюю (К_с)
                        result[0] = chars[0]
                        result[2] = chars[2]
                    }
                    4 -> {
                        // Для слов из 4 букв: показываем первую и последнюю (к_т_)
                        result[0] = chars[0]
                        result[3] = chars[3]
                    }
                    5 -> {
                        // Для слов из 5 букв: показываем первую, среднюю и последнюю (к_с_с)
                        result[0] = chars[0]
                        result[2] = chars[2]
                        result[4] = chars[4]
                    }
                    6 -> {
                        // Для слов из 6 букв: показываем первую, вторую и последнюю (ко___с)
                        result[0] = chars[0]
                        result[1] = chars[1]
                        result[5] = chars[5]
                    }
                    in 7..9 -> {
                        // Для слов 7-9 букв: показываем первые 2, среднюю и последние 2
                        result[0] = chars[0]
                        result[1] = chars[1]
                        result[word.length / 2] = chars[word.length / 2]
                        result[word.length - 2] = chars[word.length - 2]
                        result[word.length - 1] = chars[word.length - 1]
                    }
                    else -> {
                        // Для очень длинных слов: показываем первые 3 и последние 3
                        result[0] = chars[0]
                        result[1] = chars[1]
                        result[2] = chars[2]
                        result[word.length - 3] = chars[word.length - 3]
                        result[word.length - 2] = chars[word.length - 2]
                        result[word.length - 1] = chars[word.length - 1]
                    }
                }
                
                masked.append(result.joinToString(""))
            }
            
            if (i < words.size - 1) {
                masked.append(" ")
            }
        }
        
        return masked.toString()
    }

    private fun saveDefaultWords(@Suppress("UNUSED_PARAMETER") words: List<WordPair>) {
        // Больше не сохраняем дефолтные слова в старом формате
        // Они уже есть в новом game.yml
        plugin.logger.info("[ZTelegram] 🎮 [GameManager] Используются слова из game.yml")
    }
    
    /**
     * Загружает статистику игроков из файла или БД
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
            gameStats.clear()
            
            ZTele.database.executeQuery(
                "SELECT telegram_id, total_games, wins, losses, total_earned, total_time FROM game_stats"
            ) { rs ->
                while (rs.next()) {
                    val telegramId = rs.getString("telegram_id")
                    val stats = GameStats(
                        totalGames = rs.getInt("total_games"),
                        wins = rs.getInt("wins"),
                        losses = rs.getInt("losses"),
                        totalEarned = rs.getDouble("total_earned"),
                        totalTime = rs.getLong("total_time")
                    )
                    gameStats[telegramId] = stats
                }
            }
            
            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("[GameManager] Загружена статистика для ${gameStats.size} игроков из БД")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки статистики игр из БД, переключаемся на YAML: ${e.message}")
            loadStatsFromYaml()
        }
    }
    
    private fun loadStatsFromYaml() {
        // Не загружаем из YAML если база данных включена
        if (ZTele.conf.databaseEnabled) {
            return
        }
        
        try {
            // Проверяем существование файла перед загрузкой
            if (!statsFile.exists()) {
                return
            }
            
            statsConfig = YamlConfiguration.loadConfiguration(statsFile)
            gameStats.clear()
            
            val playersSection = statsConfig.getConfigurationSection("players")
            if (playersSection != null) {
                for (telegramId in playersSection.getKeys(false)) {
                    val playerSection = playersSection.getConfigurationSection(telegramId)
                    if (playerSection != null) {
                        val stats = GameStats(
                            totalGames = playerSection.getInt("totalGames", 0),
                            wins = playerSection.getInt("wins", 0),
                            losses = playerSection.getInt("losses", 0),
                            totalEarned = playerSection.getDouble("totalEarned", 0.0),
                            totalTime = playerSection.getLong("totalTime", 0L)
                        )
                        gameStats[telegramId] = stats
                    }
                }
            }
            
            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("[GameManager] Loaded stats for ${gameStats.size} players")
            }
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load game stats: ${e.message}")
        }
    }
    
    /**
     * Сохраняет статистику игроков в файл или БД
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
            ZTele.database.executeTransaction { conn ->
                for ((telegramId, stats) in gameStats) {
                    conn.prepareStatement("""
                        INSERT OR REPLACE INTO game_stats 
                        (telegram_id, total_games, wins, losses, total_earned, total_time, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
                    """).use { stmt ->
                        stmt.setString(1, telegramId)
                        stmt.setInt(2, stats.totalGames)
                        stmt.setInt(3, stats.wins)
                        stmt.setInt(4, stats.losses)
                        stmt.setDouble(5, stats.totalEarned)
                        stmt.setLong(6, stats.totalTime)
                        stmt.executeUpdate()
                    }
                }
            }
            
            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("[GameManager] Сохранена статистика для ${gameStats.size} игроков в БД")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка сохранения статистики игр в БД: ${e.message}")
            saveStatsToYaml() // Fallback на YAML
        }
    }
    
    private fun saveStatsToYaml() {
        // Не сохраняем в YAML если база данных включена
        if (ZTele.conf.databaseEnabled) {
            return
        }
        
        try {
            statsConfig = YamlConfiguration()
            
            for ((telegramId, stats) in gameStats) {
                val playerPath = "players.$telegramId"
                statsConfig.set("$playerPath.totalGames", stats.totalGames)
                statsConfig.set("$playerPath.wins", stats.wins)
                statsConfig.set("$playerPath.losses", stats.losses)
                statsConfig.set("$playerPath.totalEarned", stats.totalEarned)
                statsConfig.set("$playerPath.totalTime", stats.totalTime)
            }
            
            statsConfig.save(statsFile)
            
            if (ZTele.conf.debugEnabled) {
                plugin.logger.info("[GameManager] Saved stats for ${gameStats.size} players")
            }
            
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save game stats: ${e.message}")
        }
    }
    
    // Сохранение конфигурации игры
    fun saveGameConfig() {
        try {
            gameConfig.set("enabled", enabled)
            gameConfig.set("command.enabled", gameCommandEnabled)
            gameConfig.set("rewards.base_reward", baseReward)
            gameConfig.set("rewards.speed_bonus", speedBonus)
            gameConfig.set("rewards.max_bonus", maxBonus)
            gameConfig.set("rewards.commands", rewardCommands)
            
            // Сохраняем настройки времени
            gameConfig.set("settings.time_seconds", gameConfig.getInt("settings.time_seconds", 60))
            
            gameConfig.save(gameFile)
            plugin.logger.info("Game configuration saved successfully")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save game configuration: ${e.message}")
        }
    }
    
    /**
     * Определяет уровень сложности игрока на основе его статистики
     */
    private fun getPlayerDifficultyLevel(telegramUsername: String): String {
        val stats = gameStats[telegramUsername] ?: return "beginner"
        
        // Если игр мало, используем начальный уровень
        if (stats.totalGames < 5) {
            return "beginner"
        }
        
        // Определяем уровень на основе процента побед и количества игр
        return when {
            stats.winRate >= 80 && stats.totalGames >= 20 -> "master"
            stats.winRate >= 70 && stats.totalGames >= 15 -> "expert"
            stats.winRate >= 60 && stats.totalGames >= 10 -> "hard"
            stats.winRate >= 50 && stats.totalGames >= 8 -> "medium"
            stats.winRate >= 40 && stats.totalGames >= 5 -> "easy"
            else -> "beginner"
        }
    }
    
    /**
     * Получает случайное слово подходящей сложности для игрока
     */
    private fun getWordForPlayer(telegramUsername: String): WordPair? {
        val difficultyLevel = getPlayerDifficultyLevel(telegramUsername)
        
        // Получаем категории слов для данного уровня сложности
        val availableCategories = when (difficultyLevel) {
            "beginner" -> listOf("length_3", "length_4")
            "easy" -> listOf("length_4", "length_5")
            "medium" -> listOf("length_5", "length_6")
            "hard" -> listOf("length_6", "length_7")
            "expert" -> listOf("length_7", "length_8")
            "master" -> listOf("length_8", "length_9_plus")
            else -> listOf("length_4", "length_5") // fallback
        }
        
        // Собираем все доступные слова для данного уровня
        val availableWords = mutableListOf<WordPair>()
        for (category in availableCategories) {
            wordsByLength[category]?.let { words ->
                availableWords.addAll(words)
            }
        }
        
        // Если нет слов для данного уровня, используем все слова
        if (availableWords.isEmpty()) {
            return if (wordsList.isNotEmpty()) {
                wordsList[Random.nextInt(wordsList.size)]
            } else {
                null
            }
        }
        
        return availableWords[Random.nextInt(availableWords.size)]
    }

    fun startGame(telegramUsername: String, playerName: String): String {
        if (!enabled) {
            return "❌ Игра временно отключена."
        }
        
        // Проверяем, не играет ли игрок уже
        if (activeGames.containsKey(telegramUsername)) {
            return ZTele.conf.gameMessageAlreadyPlaying
        }
        
        // Проверяем, существует ли игрок
        val player = Bukkit.getPlayerExact(playerName)
        if (player == null && !Bukkit.getOfflinePlayer(playerName).hasPlayedBefore()) {
            return "❌ Игрок $playerName не найден! Укажите правильный никнейм."
        }
        
        // Получаем слово подходящей сложности для игрока
        val selectedWord = getWordForPlayer(telegramUsername)
        if (selectedWord == null) {
            return "❌ Список слов пуст. Обратитесь к администратору."
        }
        
        // Создаем игровую сессию
        val gameSession = GameSession(
            playerName = playerName,
            wordPair = selectedWord,
            startTime = System.currentTimeMillis()
        )
        
        // Получаем значение времени игры из конфигурации
        val gameTimeSeconds = ZTele.conf.gameTimeoutSeconds
        
        // Запускаем таймер для завершения игры по таймауту
        val taskId = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            handleTimeout(telegramUsername)
        }, gameTimeSeconds * 20L).taskId
        
        gameSession.taskId = taskId
        
        // Сохраняем игровую сессию
        activeGames[telegramUsername] = gameSession
        
        // Форматируем маскированное слово для лучшего отображения в Telegram
        val formattedMaskedWord = formatMaskedWord(selectedWord.masked)
        
        // Возвращаем сообщение о начале игры
        val context = PlaceholderEngine.createCustomContext(mapOf(
            "player" to playerName,
            "question" to formattedMaskedWord,
            "time" to gameTimeSeconds.toString(),
            "word_hint" to formattedMaskedWord,
            "word" to formattedMaskedWord,  // Добавляем плейсхолдер %word%
            "base_reward" to String.format("%.0f", baseReward),
            "length" to selectedWord.original.length.toString(),
            "first_letter" to if (selectedWord.original.isNotEmpty()) selectedWord.original.first().toString() else "?",
            "last_letter" to if (selectedWord.original.isNotEmpty()) selectedWord.original.last().toString() else "?"
        ))
        return PlaceholderEngine.process(ZTele.conf.gameMessageStart, context)
            .replace("\\n", "\n")
    }
    
    // Форматирует маскированное слово для лучшего отображения в Telegram
    private fun formatMaskedWord(maskedWord: String): String {
        // Если слово слишком длинное, разбиваем его на строки
        val maxLineLength = 30
        
        if (maskedWord.length > maxLineLength) {
            val words = maskedWord.split("\\s+".toRegex())
            val lines = mutableListOf<String>()
            var currentLine = StringBuilder()
            
            for (word in words) {
                if (currentLine.length + word.length + 1 > maxLineLength) {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString().trim())
                        currentLine = StringBuilder()
                    }
                }
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(word)
            }
            
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString().trim())
            }
            
            // Объединяем строки с переносами
            return lines.joinToString("\n")
        }
        
        return maskedWord
    }
    
    fun checkAnswer(telegramUsername: String, answer: String): Pair<Boolean, String> {
        val gameSession = activeGames[telegramUsername] ?: return Pair(false, "❌ У вас нет активной игры. Напишите /game [nickname] для начала игры.")
        
        // Сравниваем ответ с правильным словом (игнорируя регистр)
        if (answer.trim().equals(gameSession.wordPair.original, ignoreCase = true)) {
            // Правильный ответ
            
            // Отменяем таймер
            if (gameSession.taskId != -1) {
                Bukkit.getScheduler().cancelTask(gameSession.taskId)
            }
            
            // Обновляем статистику игрока
            val answerTime = System.currentTimeMillis() - gameSession.startTime
            val stats = gameStats.getOrPut(telegramUsername) { GameStats() }
            stats.totalGames++
            stats.wins++
            stats.totalTime += answerTime
            
            // Рассчитываем награду с бонусом за скорость
            val gameTimeSeconds = ZTele.conf.gameTimeoutSeconds
            val answerTimeSeconds = (answerTime / 1000.0)
            val remainingTime = gameTimeSeconds - answerTimeSeconds
            
            // Бонус за каждые 10 секунд оставшегося времени
            val speedBonusAmount = if (remainingTime > 0) {
                kotlin.math.min(maxBonus, (remainingTime / 10.0) * speedBonus)
            } else {
                0.0
            }
            
            val totalReward = baseReward + speedBonusAmount
            stats.totalEarned += totalReward
            
            // Сохраняем статистику
            saveStats()
            
            // Выдаем награду
            giveReward(gameSession.playerName, totalReward)
            
            // Получаем баланс игрока
            val playerBalance = getPlayerBalance(gameSession.playerName)
            val formattedBalance = String.format("%.2f", playerBalance)
            
            // Удаляем игровую сессию
            activeGames.remove(telegramUsername)
            
            // Возвращаем сообщение о победе
            val winContext = PlaceholderEngine.createCustomContext(mapOf(
                "player" to gameSession.playerName,
                "word" to gameSession.wordPair.original,
                "reward" to String.format("%.1f", totalReward),
                "total_reward" to String.format("%.1f", totalReward),
                "base_reward" to String.format("%.1f", baseReward),
                "speed_bonus" to String.format("%.1f", speedBonusAmount),
                "player_money" to formattedBalance,
                "answer_time" to String.format("%.1f", answerTimeSeconds),
                "wins" to stats.wins.toString(),
                "total_games" to stats.totalGames.toString()
            ))
            return Pair(true, PlaceholderEngine.process(ZTele.conf.gameMessageWin, winContext)
                .replace("\\n", "\n"))
        }
        
        // Неправильный ответ - форматируем маскированное слово для лучшего отображения
        val formattedMaskedWord = formatMaskedWord(gameSession.wordPair.masked)
        
        // Добавляем эмодзи 🎮 для обозначения игрового слова, чтобы TBot.convertToHtml распознал его как маскированное слово
        val gameHint = "🎮 $formattedMaskedWord"
        
        return Pair(false, "❌ Неправильно! Попробуйте еще раз.\nПодсказка: $gameHint")
    }
    
    // Метод для получения баланса игрока
    private fun getPlayerBalance(playerName: String): Double {
        try {
            // Проверяем, есть ли у нас Vault для доступа к экономике
            if (plugin.server.pluginManager.getPlugin("Vault") != null) {
                val rsp = plugin.server.servicesManager.getRegistration(net.milkbowl.vault.economy.Economy::class.java)
                if (rsp != null) {
                    val economy = rsp.provider
                    val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
                    return economy.getBalance(offlinePlayer)
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка при получении баланса игрока $playerName: ${e.message}")
        }
        return 0.0
    }
    
    /**
     * Получает статистику игрока
     */
    fun getPlayerStats(telegramUsername: String): GameStats {
        return gameStats.getOrDefault(telegramUsername, GameStats())
    }
    
    /**
     * Получает топ игроков по количеству побед
     */
    fun getTopPlayers(limit: Int = 10): List<Pair<String, GameStats>> {
        return gameStats.entries
            .filter { it.value.totalGames > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, GameStats>> { it.value.wins }
                .thenByDescending { it.value.winRate }
                .thenBy { it.value.avgTime })
            .take(limit)
            .map { entry ->
                // Получаем имя игрока по Telegram ID
                val playerName = ZTele.mgr.getPlayerByTelegramId(entry.key) ?: "Unknown"
                playerName to entry.value
            }
    }
    
    private fun handleTimeout(telegramUsername: String) {
        val gameSession = activeGames[telegramUsername] ?: return
        
        // Удаляем игровую сессию
        activeGames.remove(telegramUsername)
        
        // Обновляем статистику игрока
        val stats = gameStats.getOrPut(telegramUsername) { GameStats() }
        stats.totalGames++
        stats.losses++
        
        // Сохраняем статистику
        saveStats()
        
        // Отправляем сообщение о проигрыше с правильно отформатированным словом
        val loseContext = PlaceholderEngine.createCustomContext(mapOf(
            "word" to gameSession.wordPair.original,
            "wins" to stats.wins.toString(),
            "total_games" to stats.totalGames.toString(),
            "player" to gameSession.playerName
        ))
        val loseMessage = PlaceholderEngine.process(ZTele.conf.gameMessageLose, loseContext)
            .replace("\\n", "\n")
        
        // Отправляем автоудаляемое сообщение через бота в игровой канал
        val gameChannelId = if (ZTele.conf.gameChannelId.isNotEmpty()) {
            ZTele.conf.gameChannelId
        } else {
            ZTele.conf.mainChannelId
        }
        
        plugin.getBot().sendAutoDeleteMessage(
            gameChannelId, 
            loseMessage, 
            ZTele.conf.gameAutoDeleteSeconds
        )
    }
    
    private fun giveReward(playerName: String, rewardAmount: Double) {
        // Выполняем команды для выдачи наград
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (command in rewardCommands) {
                try {
                    val context = PlaceholderEngine.createCustomContext(mapOf(
                        "player" to playerName,
                        "reward" to String.format("%.1f", rewardAmount)
                    ))
                    val cmd = PlaceholderEngine.process(command, context)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    
                    if (ZTele.conf.debugEnabled) {
                        plugin.logger.info("[GameManager] Executed reward command: $cmd")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to execute reward command for player $playerName: ${e.message}")
                }
            }
        })
    }
    
    fun hasActiveGame(telegramUsername: String): Boolean {
        return activeGames.containsKey(telegramUsername)
    }
    
    fun getActiveGame(telegramUsername: String): GameSession? {
        return activeGames[telegramUsername]
    }
    
    fun cancelGame(telegramUsername: String): Boolean {
        val gameSession = activeGames[telegramUsername] ?: return false
        
        // Отменяем таймер
        if (gameSession.taskId != -1) {
            Bukkit.getScheduler().cancelTask(gameSession.taskId)
        }
        
        // Удаляем игровую сессию
        activeGames.remove(telegramUsername)
        
        return true
    }
    
    fun cancelAllGames() {
        // Отменяем все активные игры
        for (gameSession in activeGames.values) {
            if (gameSession.taskId != -1) {
                Bukkit.getScheduler().cancelTask(gameSession.taskId)
            }
        }
        
        // Очищаем список активных игр
        activeGames.clear()
    }
} 