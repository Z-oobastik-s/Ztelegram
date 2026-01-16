package org.zoobastiks.ztelegram.utils

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File

/**
 * Валидатор конфигурационных файлов плагина
 * Проверяет корректность настроек и выводит детальные сообщения об ошибках
 */
class ConfigValidator(private val plugin: ZTele) {
    
    private val errors = mutableListOf<ValidationError>()
    private val warnings = mutableListOf<ValidationWarning>()
    
    data class ValidationError(
        val file: String,
        val path: String,
        val line: Int?,
        val message: String,
        val suggestion: String? = null
    )
    
    data class ValidationWarning(
        val file: String,
        val path: String,
        val message: String
    )
    
    /**
     * Валидирует все конфигурационные файлы
     */
    fun validateAll(): Boolean {
        errors.clear()
        warnings.clear()
        
        plugin.logger.info("╔════════════════════════════════════════════════════════════════╗")
        plugin.logger.info("║          🔍 ПРОВЕРКА КОНФИГУРАЦИИ ПЛАГИНА ZTELEGRAM           ║")
        plugin.logger.info("╚════════════════════════════════════════════════════════════════╝")
        
        // Валидируем config.yml
        validateConfigYml()
        
        // Валидируем game.yml
        validateGameYml()
        
        // Валидируем players.yml
        validatePlayersYml()
        
        // Выводим результаты
        printResults()
        
        return errors.isEmpty()
    }
    
    /**
     * Валидирует config.yml
     */
    private fun validateConfigYml() {
        val configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile.exists()) {
            errors.add(ValidationError(
                "config.yml",
                "",
                null,
                "Файл config.yml не найден!",
                "Перезапустите сервер для создания файла по умолчанию"
            ))
            return
        }
        
        val config = plugin.config
        val fileContent = configFile.readLines()
        
        // Проверка токена бота
        validateBotToken(config, fileContent)
        
        // Проверка ID каналов
        validateChannelIds(config, fileContent)
        
        // Проверка форматов сообщений
        validateMessageFormats(config, fileContent)
        
        // Проверка команд
        validateCommands(config, fileContent)
        
        // Проверка расписаний
        validateSchedules(config, fileContent)
    }
    
    /**
     * Валидирует токен бота
     */
    private fun validateBotToken(config: FileConfiguration, fileContent: List<String>) {
        val token = config.getString("bot.token") ?: ""
        val line = findLineNumber(fileContent, "token:")
        
        if (token.isEmpty() || token == "YOUR_BOT_TOKEN_HERE") {
            errors.add(ValidationError(
                "config.yml",
                "bot.token",
                line,
                "Токен бота не настроен!",
                "Получите токен у @BotFather в Telegram и укажите в bot.token"
            ))
        } else if (!token.matches(Regex("^\\d+:[A-Za-z0-9_-]{35}$"))) {
            errors.add(ValidationError(
                "config.yml",
                "bot.token",
                line,
                "Неверный формат токена бота!",
                "Токен должен быть в формате: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz-1234567890"
            ))
        }
    }
    
    /**
     * Валидирует ID каналов
     */
    private fun validateChannelIds(config: FileConfiguration, fileContent: List<String>) {
        val channels = mapOf(
            "channels.main" to "Основной канал",
            "channels.console" to "Консольный канал",
            "channels.register" to "Канал регистрации"
        )
        
        for ((path, name) in channels) {
            val channelId = config.getString(path) ?: ""
            val line = findLineNumber(fileContent, path.substringAfter("channels.") + ":")
            
            if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
                errors.add(ValidationError(
                    "config.yml",
                    path,
                    line,
                    "$name не настроен!",
                    "Добавьте бота в группу и получите chat ID через https://api.telegram.org/bot<TOKEN>/getUpdates"
                ))
            } else if (!channelId.matches(Regex("^-?\\d+(_\\d+)?$"))) {
                errors.add(ValidationError(
                    "config.yml",
                    path,
                    line,
                    "Неверный формат ID канала для $name!",
                    "ID должен быть числом (например: -1001234567890) или с темой (например: -1001234567890_123)"
                ))
            }
        }
        
        // Проверка опциональных каналов
        val gameChannel = config.getString("channels.game") ?: ""
        if (gameChannel.isNotEmpty() && !gameChannel.matches(Regex("^-?\\d+(_\\d+)?$"))) {
            val line = findLineNumber(fileContent, "game:")
            errors.add(ValidationError(
                "config.yml",
                "channels.game",
                line,
                "Неверный формат ID игрового канала!",
                "Оставьте пустым ('') или укажите корректный ID"
            ))
        }
    }
    
    /**
     * Валидирует форматы сообщений на наличие некорректного Markdown
     */
    private fun validateMessageFormats(config: FileConfiguration, fileContent: List<String>) {
        val messagePaths = listOf(
            "commands.online.response",
            "commands.tps.message",
            "commands.top.message",
            "commands.topbal.message",
            "commands.player.response",
            "commands.stats.message",
            "help.main",
            "help.register",
            "help.game",
            "help.console"
        )
        
        for (path in messagePaths) {
            val message = config.getString(path) ?: continue
            if (message.isEmpty()) continue
            
            val line = findLineNumber(fileContent, path.substringAfterLast(".") + ":")
            
            // Проверка на незакрытые Markdown теги
            val issues = validateMarkdown(message)
            if (issues.isNotEmpty()) {
                errors.add(ValidationError(
                    "config.yml",
                    path,
                    line,
                    "Ошибка форматирования Markdown: ${issues.joinToString(", ")}",
                    "Проверьте парность символов *, _, `, [ и ]"
                ))
            }
            
            // Проверка на некорректные HTML entities
            if (message.contains("<") && message.contains(">")) {
                val htmlIssues = validateHtmlEntities(message)
                if (htmlIssues.isNotEmpty()) {
                    warnings.add(ValidationWarning(
                        "config.yml",
                        path,
                        "Возможные проблемы с HTML: ${htmlIssues.joinToString(", ")}"
                    ))
                }
            }
        }
    }
    
    /**
     * Валидирует Markdown форматирование
     * 
     * ВАЖНО: Метод игнорирует подчеркивания (_) в следующих контекстах:
     * - Внутри плейсхолдеров: %player_name%, %time_1%, %unique_count%
     * - Внутри обратных кавычек: `ваш_никнейм`, `player_1`
     * - В URL и путях: https://example.com/path_to_file, t.me/channel_name
     * 
     * Это предотвращает ложные срабатывания валидатора на технические символы,
     * которые не являются Markdown-форматированием.
     */
    private fun validateMarkdown(text: String): List<String> {
        val issues = mutableListOf<String>()
        
        // Проверка парности звездочек (жирный текст)
        val boldCount = text.count { it == '*' }
        if (boldCount % 2 != 0) {
            issues.add("Непарное количество символов * (жирный текст)")
        }
        
        // Проверка парности подчеркиваний (курсив)
        // Исключаем подчеркивания внутри плейсхолдеров (%...%) и обратных кавычек (`...`)
        val cleanedText = removeProtectedUnderscores(text)
        val italicCount = cleanedText.count { it == '_' }
        if (italicCount % 2 != 0) {
            issues.add("Непарное количество символов _ (курсив)")
        }
        
        // Проверка парности обратных кавычек (код)
        val codeCount = text.count { it == '`' }
        if (codeCount % 2 != 0) {
            issues.add("Непарное количество символов ` (код)")
        }
        
        // Проверка парности квадратных скобок (ссылки)
        val openBrackets = text.count { it == '[' }
        val closeBrackets = text.count { it == ']' }
        if (openBrackets != closeBrackets) {
            issues.add("Непарное количество квадратных скобок [ ] (ссылки)")
        }
        
        return issues
    }
    
    /**
     * Удаляет подчеркивания из защищенных контекстов (плейсхолдеры, код)
     * чтобы не считать их как Markdown форматирование
     * 
     * Примеры обработки:
     * - "%player_1%" -> "%player1%" (плейсхолдер)
     * - "`ваш_никнейм`" -> "`вашникнейм`" (код)
     * - "https://t.me/channel_name" -> "https://t.me/channelname" (URL)
     * - "_курсив_" -> "_курсив_" (остается без изменений - это Markdown)
     * 
     * @param text Исходный текст для обработки
     * @return Текст с удаленными подчеркиваниями из защищенных контекстов
     */
    private fun removeProtectedUnderscores(text: String): String {
        var result = text
        
        // Удаляем подчеркивания внутри плейсхолдеров %...%
        // Используем более точный паттерн, который находит все плейсхолдеры
        result = result.replace(Regex("%[^%]+%")) { matchResult ->
            matchResult.value.replace("_", "")
        }
        
        // Удаляем подчеркивания внутри обратных кавычек `...`
        // Обрабатываем как одинарные, так и тройные кавычки
        result = result.replace(Regex("`[^`]+`")) { matchResult ->
            matchResult.value.replace("_", "")
        }
        
        // Удаляем подчеркивания в URL (содержат :// или начинаются с http/https)
        result = result.replace(Regex("https?://[^\\s]+")) { matchResult ->
            matchResult.value.replace("_", "")
        }
        
        // Удаляем подчеркивания в путях вида t.me/... или /путь/к/файлу
        result = result.replace(Regex("[a-zA-Z0-9]+\\.[a-zA-Z]+/[^\\s]+")) { matchResult ->
            matchResult.value.replace("_", "")
        }
        
        return result
    }
    
    /**
     * Валидирует HTML entities
     */
    private fun validateHtmlEntities(text: String): List<String> {
        val issues = mutableListOf<String>()
        
        // Проверка на незакрытые теги
        val tagPattern = Regex("<([a-zA-Z]+)[^>]*>")
        val closingTagPattern = Regex("</([a-zA-Z]+)>")
        
        val openTags = tagPattern.findAll(text).map { it.groupValues[1] }.toList()
        val closeTags = closingTagPattern.findAll(text).map { it.groupValues[1] }.toList()
        
        val unclosedTags = openTags.filterNot { closeTags.contains(it) }
        if (unclosedTags.isNotEmpty()) {
            issues.add("Незакрытые теги: ${unclosedTags.joinToString(", ")}")
        }
        
        return issues
    }
    
    /**
     * Валидирует команды
     */
    private fun validateCommands(config: FileConfiguration, @Suppress("UNUSED_PARAMETER") fileContent: List<String>) {
        // Проверка команд рестарта
        val restartEnabled = config.getBoolean("commands.restart.enabled", true)
        if (restartEnabled) {
            val minMinutes = config.getInt("commands.restart.scheduled.timer.min_minutes", 1)
            val maxMinutes = config.getInt("commands.restart.scheduled.timer.max_minutes", 60)
            
            if (minMinutes < 1) {
                warnings.add(ValidationWarning(
                    "config.yml",
                    "commands.restart.scheduled.timer.min_minutes",
                    "Минимальное время рестарта меньше 1 минуты может быть опасным"
                ))
            }
            
            if (maxMinutes > 1440) {
                warnings.add(ValidationWarning(
                    "config.yml",
                    "commands.restart.scheduled.timer.max_minutes",
                    "Максимальное время рестарта больше 24 часов может быть избыточным"
                ))
            }
        }
    }
    
    /**
     * Валидирует расписания
     */
    private fun validateSchedules(config: FileConfiguration, fileContent: List<String>) {
        // Проверка расписания автоуведомлений
        if (config.getBoolean("auto_notifications.enabled", false)) {
            val playtimeSchedule = config.getString("auto_notifications.playtime_top.schedule") ?: ""
            validateTimeSchedule(playtimeSchedule, "auto_notifications.playtime_top.schedule", fileContent)
            
            val balanceSchedule = config.getString("auto_notifications.balance_top.schedule") ?: ""
            validateTimeSchedule(balanceSchedule, "auto_notifications.balance_top.schedule", fileContent)
        }
        
        // Проверка планировщика задач
        if (config.getBoolean("scheduler.enabled", false)) {
            val dailyTasks = config.getConfigurationSection("scheduler.daily_tasks")
            if (dailyTasks != null) {
                for (taskName in dailyTasks.getKeys(false)) {
                    val time = config.getString("scheduler.daily_tasks.$taskName.time") ?: ""
                    validateTimeFormat(time, "scheduler.daily_tasks.$taskName.time", fileContent)
                }
            }
        }
    }
    
    /**
     * Валидирует формат расписания (HH:MM,HH:MM)
     */
    private fun validateTimeSchedule(schedule: String, path: String, fileContent: List<String>) {
        if (schedule.isEmpty()) return
        
        val times = schedule.split(",")
        for (time in times) {
            validateTimeFormat(time.trim(), path, fileContent)
        }
    }
    
    /**
     * Валидирует формат времени (HH:MM)
     */
    private fun validateTimeFormat(time: String, path: String, fileContent: List<String>) {
        if (time.isEmpty()) return
        
        val line = findLineNumber(fileContent, path.substringAfterLast(".") + ":")
        
        if (!time.matches(Regex("^([0-1]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
            errors.add(ValidationError(
                "config.yml",
                path,
                line,
                "Неверный формат времени: '$time'",
                "Используйте формат HH:MM (например: 12:00 или 09:30)"
            ))
        }
    }
    
    /**
     * Валидирует game.yml
     */
    private fun validateGameYml() {
        val gameFile = File(plugin.dataFolder, "game.yml")
        if (!gameFile.exists()) {
            warnings.add(ValidationWarning(
                "game.yml",
                "",
                "Файл game.yml не найден, используются настройки по умолчанию"
            ))
            return
        }
        
        val config = YamlConfiguration.loadConfiguration(gameFile)
        val fileContent = gameFile.readLines()
        
        // Проверка наличия слов для игры
        val wordsSection = config.getConfigurationSection("words")
        if (wordsSection == null || wordsSection.getKeys(false).isEmpty()) {
            val line = findLineNumber(fileContent, "words:")
            errors.add(ValidationError(
                "game.yml",
                "words",
                line,
                "Не найдены слова для игры!",
                "Добавьте хотя бы одну категорию слов (например: length_3, length_4)"
            ))
        } else {
            // Проверка каждой категории слов
            for (category in wordsSection.getKeys(false)) {
                val words = config.getStringList("words.$category")
                if (words.isEmpty()) {
                    warnings.add(ValidationWarning(
                        "game.yml",
                        "words.$category",
                        "Категория '$category' не содержит слов"
                    ))
                }
            }
        }
        
        // Проверка команд награды
        val rewardCommands = config.getStringList("rewards.commands")
        if (rewardCommands.isEmpty()) {
            warnings.add(ValidationWarning(
                "game.yml",
                "rewards.commands",
                "Не настроены команды награды за победу в игре"
            ))
        }
    }
    
    /**
     * Валидирует players.yml
     */
    private fun validatePlayersYml() {
        val playersFile = File(plugin.dataFolder, "players.yml")
        if (!playersFile.exists()) {
            // Это нормально для первого запуска
            return
        }
        
        val config = YamlConfiguration.loadConfiguration(playersFile)
        
        // Проверка структуры файла
        val playersSection = config.getConfigurationSection("players")
        if (playersSection != null) {
            for (playerName in playersSection.getKeys(false)) {
                val telegramId = config.getString("players.$playerName.telegram-id")
                if (telegramId == null || telegramId.isEmpty()) {
                    warnings.add(ValidationWarning(
                        "players.yml",
                        "players.$playerName.telegram-id",
                        "У игрока $playerName отсутствует telegram-id"
                    ))
                }
            }
        }
    }
    
    /**
     * Находит номер строки в файле по ключевому слову
     */
    private fun findLineNumber(lines: List<String>, keyword: String): Int? {
        for ((index, line) in lines.withIndex()) {
            if (line.trim().startsWith(keyword)) {
                return index + 1 // Нумерация с 1
            }
        }
        return null
    }
    
    /**
     * Выводит результаты валидации
     */
    private fun printResults() {
        plugin.logger.info("")
        
        if (errors.isEmpty() && warnings.isEmpty()) {
            plugin.logger.info("╔════════════════════════════════════════════════════════════════╗")
            plugin.logger.info("║                    ✅ ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ                   ║")
            plugin.logger.info("║              Конфигурация настроена корректно!                 ║")
            plugin.logger.info("╚════════════════════════════════════════════════════════════════╝")
        } else {
            if (errors.isNotEmpty()) {
                plugin.logger.severe("╔════════════════════════════════════════════════════════════════╗")
                plugin.logger.severe("║                  ❌ ОБНАРУЖЕНЫ ОШИБКИ (${errors.size})                      ║")
                plugin.logger.severe("╚════════════════════════════════════════════════════════════════╝")
                plugin.logger.severe("")
                
                for ((index, error) in errors.withIndex()) {
                    plugin.logger.severe("┌─ Ошибка #${index + 1} ─────────────────────────────────────────────")
                    plugin.logger.severe("│ 📄 Файл: ${error.file}")
                    if (error.line != null) {
                        plugin.logger.severe("│ 📍 Строка: ${error.line}")
                    }
                    plugin.logger.severe("│ 🔑 Путь: ${error.path}")
                    plugin.logger.severe("│ ❌ Проблема: ${error.message}")
                    if (error.suggestion != null) {
                        plugin.logger.severe("│ 💡 Решение: ${error.suggestion}")
                    }
                    plugin.logger.severe("└────────────────────────────────────────────────────────────────")
                    plugin.logger.severe("")
                }
            }
            
            if (warnings.isNotEmpty()) {
                plugin.logger.warning("╔════════════════════════════════════════════════════════════════╗")
                plugin.logger.warning("║                 ⚠️  ОБНАРУЖЕНЫ ПРЕДУПРЕЖДЕНИЯ (${warnings.size})              ║")
                plugin.logger.warning("╚════════════════════════════════════════════════════════════════╝")
                plugin.logger.warning("")
                
                for ((index, warning) in warnings.withIndex()) {
                    plugin.logger.warning("┌─ Предупреждение #${index + 1} ────────────────────────────────────")
                    plugin.logger.warning("│ 📄 Файл: ${warning.file}")
                    plugin.logger.warning("│ 🔑 Путь: ${warning.path}")
                    plugin.logger.warning("│ ⚠️  Сообщение: ${warning.message}")
                    plugin.logger.warning("└────────────────────────────────────────────────────────────────")
                    plugin.logger.warning("")
                }
            }
        }
        
        plugin.logger.info("")
    }
    
    /**
     * Валидирует конкретное сообщение и возвращает детальную информацию об ошибке
     */
    fun validateMessage(message: String, context: String): ValidationResult {
        val issues = mutableListOf<String>()
        
        // Проверка Markdown
        val markdownIssues = validateMarkdown(message)
        issues.addAll(markdownIssues)
        
        // Проверка HTML
        if (message.contains("<") && message.contains(">")) {
            val htmlIssues = validateHtmlEntities(message)
            issues.addAll(htmlIssues)
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            context = context,
            message = message
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<String>,
        val context: String,
        val message: String
    )
}
