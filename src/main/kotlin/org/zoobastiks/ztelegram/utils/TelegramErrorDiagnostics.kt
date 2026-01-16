package org.zoobastiks.ztelegram.utils

import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.zoobastiks.ztelegram.ZTele
import java.io.File

/**
 * Диагностика ошибок Telegram API с детальным анализом
 */
object TelegramErrorDiagnostics {
    
    private val plugin: ZTele
        get() = ZTele.instance
    
    /**
     * Анализирует ошибку Telegram API и выводит детальную информацию
     */
    fun diagnoseError(
        exception: Exception,
        context: String,
        message: String? = null,
        configPath: String? = null
    ) {
        when (exception) {
            is TelegramApiRequestException -> diagnoseApiRequestException(exception, context, message, configPath)
            is TelegramApiException -> diagnoseApiException(exception, context, message, configPath)
            else -> diagnoseGenericException(exception, context, message, configPath)
        }
    }
    
    /**
     * Диагностирует ошибки запросов к Telegram API
     */
    private fun diagnoseApiRequestException(
        exception: TelegramApiRequestException,
        context: String,
        message: String?,
        configPath: String?
    ) {
        val errorCode = exception.errorCode
        val apiResponse = exception.apiResponse ?: "Unknown error"
        
        plugin.logger.severe("╔════════════════════════════════════════════════════════════════╗")
        plugin.logger.severe("║           ❌ ОШИБКА TELEGRAM API - ДЕТАЛЬНАЯ ДИАГНОСТИКА      ║")
        plugin.logger.severe("╚════════════════════════════════════════════════════════════════╝")
        plugin.logger.severe("")
        plugin.logger.severe("📍 Контекст: $context")
        plugin.logger.severe("🔢 Код ошибки: $errorCode")
        plugin.logger.severe("📝 Ответ API: $apiResponse")
        plugin.logger.severe("")
        
        // Анализируем конкретную ошибку
        when (errorCode) {
            400 -> diagnose400Error(apiResponse, message, configPath)
            401 -> diagnose401Error()
            403 -> diagnose403Error(apiResponse)
            404 -> diagnose404Error()
            429 -> diagnose429Error(apiResponse)
            else -> diagnoseUnknownError(errorCode, apiResponse)
        }
        
        plugin.logger.severe("════════════════════════════════════════════════════════════════")
        plugin.logger.severe("")
    }
    
    /**
     * Диагностирует ошибку 400 (Bad Request)
     */
    private fun diagnose400Error(apiResponse: String, message: String?, configPath: String?) {
        plugin.logger.severe("🔍 Тип ошибки: BAD REQUEST (400)")
        plugin.logger.severe("")
        
        when {
            apiResponse.contains("Can't parse entities", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Ошибка парсинга Markdown/HTML форматирования")
                plugin.logger.severe("")
                
                if (message != null) {
                    plugin.logger.severe("📄 Проблемное сообщение:")
                    plugin.logger.severe("┌────────────────────────────────────────────────────────")
                    
                    // Выводим сообщение с подсветкой проблемных мест
                    val lines = message.split("\n")
                    for ((index, line) in lines.withIndex()) {
                        plugin.logger.severe("│ ${index + 1}: $line")
                    }
                    plugin.logger.severe("└────────────────────────────────────────────────────────")
                    plugin.logger.severe("")
                    
                    // Ищем проблемные символы
                    val issues = findMarkdownIssues(message)
                    if (issues.isNotEmpty()) {
                        plugin.logger.severe("🔎 Обнаруженные проблемы:")
                        for (issue in issues) {
                            plugin.logger.severe("   • $issue")
                        }
                        plugin.logger.severe("")
                    }
                }
                
                if (configPath != null) {
                    val (file, line) = findConfigLocation(configPath)
                    if (file != null && line != null) {
                        plugin.logger.severe("📂 Файл конфигурации: $file")
                        plugin.logger.severe("📍 Строка: $line")
                        plugin.logger.severe("🔑 Путь: $configPath")
                        plugin.logger.severe("")
                    }
                }
                
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   1. Проверьте парность символов форматирования:")
                plugin.logger.severe("      • Звездочки (*) для жирного текста")
                plugin.logger.severe("      • Подчеркивания (_) для курсива")
                plugin.logger.severe("      • Обратные кавычки (`) для кода")
                plugin.logger.severe("      • Квадратные скобки [ ] для ссылок")
                plugin.logger.severe("")
                plugin.logger.severe("   2. Экранируйте специальные символы:")
                plugin.logger.severe("      • Используйте \\\\ перед символами: _ * [ ] ( ) ~ ` > # + - = | { } . !")
                plugin.logger.severe("")
                plugin.logger.severe("   3. Или используйте обычный текст без форматирования")
            }
            
            apiResponse.contains("message is too long", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Сообщение слишком длинное")
                plugin.logger.severe("")
                
                if (message != null) {
                    plugin.logger.severe("📏 Длина сообщения: ${message.length} символов")
                    plugin.logger.severe("📏 Максимум: 4096 символов")
                    plugin.logger.severe("")
                }
                
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   • Сократите текст сообщения")
                plugin.logger.severe("   • Разбейте на несколько сообщений")
                plugin.logger.severe("   • Используйте более короткие форматы")
            }
            
            apiResponse.contains("chat not found", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Чат не найден")
                plugin.logger.severe("")
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   1. Проверьте правильность ID канала в config.yml")
                plugin.logger.severe("   2. Убедитесь, что бот добавлен в группу")
                plugin.logger.severe("   3. Убедитесь, что бот является администратором")
                plugin.logger.severe("   4. Для тем: проверьте формат ID (например: -1001234567890_123)")
            }
            
            apiResponse.contains("message thread not found", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Тема (topic) не найдена")
                plugin.logger.severe("")
                
                if (configPath != null) {
                    val (file, line) = findConfigLocation(configPath)
                    if (file != null && line != null) {
                        plugin.logger.severe("📂 Файл: $file")
                        plugin.logger.severe("📍 Строка: $line")
                        plugin.logger.severe("🔑 Путь: $configPath")
                        plugin.logger.severe("")
                    }
                }
                
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   1. Проверьте, существует ли тема в канале")
                plugin.logger.severe("   2. Убедитесь, что ID темы указан правильно")
                plugin.logger.severe("   3. Формат: CHANNEL_ID_TOPIC_ID (например: -1001234567890_123)")
                plugin.logger.severe("   4. Или уберите ID темы, чтобы писать в основной канал")
            }
            
            else -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Неизвестная ошибка 400")
                plugin.logger.severe("")
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   • Проверьте формат сообщения")
                plugin.logger.severe("   • Проверьте настройки канала")
                plugin.logger.severe("   • Обратитесь к документации Telegram Bot API")
            }
        }
    }
    
    /**
     * Диагностирует ошибку 401 (Unauthorized)
     */
    private fun diagnose401Error() {
        plugin.logger.severe("🔍 Тип ошибки: UNAUTHORIZED (401)")
        plugin.logger.severe("")
        plugin.logger.severe("❌ ПРОБЛЕМА: Неверный токен бота")
        plugin.logger.severe("")
        
        val (file, line) = findConfigLocation("bot.token")
        if (file != null && line != null) {
            plugin.logger.severe("📂 Файл: $file")
            plugin.logger.severe("📍 Строка: $line")
            plugin.logger.severe("")
        }
        
        plugin.logger.severe("💡 РЕШЕНИЕ:")
        plugin.logger.severe("   1. Проверьте токен бота в config.yml (bot.token)")
        plugin.logger.severe("   2. Получите новый токен у @BotFather в Telegram")
        plugin.logger.severe("   3. Убедитесь, что токен скопирован полностью")
        plugin.logger.severe("   4. Формат токена: 123456789:ABCdefGHIjklMNOpqrsTUVwxyz")
    }
    
    /**
     * Диагностирует ошибку 403 (Forbidden)
     */
    private fun diagnose403Error(apiResponse: String) {
        plugin.logger.severe("🔍 Тип ошибки: FORBIDDEN (403)")
        plugin.logger.severe("")
        
        when {
            apiResponse.contains("bot was blocked", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Бот заблокирован пользователем")
                plugin.logger.severe("")
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   • Пользователь должен разблокировать бота")
                plugin.logger.severe("   • Или удалите пользователя из белого списка")
            }
            
            apiResponse.contains("not enough rights", ignoreCase = true) -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Недостаточно прав у бота")
                plugin.logger.severe("")
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   1. Сделайте бота администратором группы")
                plugin.logger.severe("   2. Дайте боту права на:")
                plugin.logger.severe("      • Отправку сообщений")
                plugin.logger.severe("      • Удаление сообщений (если используется)")
                plugin.logger.severe("      • Управление темами (если используются)")
            }
            
            else -> {
                plugin.logger.severe("❌ ПРОБЛЕМА: Доступ запрещен")
                plugin.logger.severe("")
                plugin.logger.severe("💡 РЕШЕНИЕ:")
                plugin.logger.severe("   • Проверьте права бота в группе")
                plugin.logger.severe("   • Убедитесь, что бот добавлен в группу")
            }
        }
    }
    
    /**
     * Диагностирует ошибку 404 (Not Found)
     */
    private fun diagnose404Error() {
        plugin.logger.severe("🔍 Тип ошибки: NOT FOUND (404)")
        plugin.logger.severe("")
        plugin.logger.severe("❌ ПРОБЛЕМА: Ресурс не найден")
        plugin.logger.severe("")
        plugin.logger.severe("💡 РЕШЕНИЕ:")
        plugin.logger.severe("   • Проверьте правильность ID канала")
        plugin.logger.severe("   • Убедитесь, что канал существует")
        plugin.logger.severe("   • Проверьте, что бот добавлен в канал")
    }
    
    /**
     * Диагностирует ошибку 429 (Too Many Requests)
     */
    private fun diagnose429Error(apiResponse: String) {
        plugin.logger.severe("🔍 Тип ошибки: TOO MANY REQUESTS (429)")
        plugin.logger.severe("")
        plugin.logger.severe("❌ ПРОБЛЕМА: Превышен лимит запросов к API")
        plugin.logger.severe("")
        
        // Пытаемся извлечь время ожидания
        val retryAfterRegex = Regex("retry after (\\d+)")
        val match = retryAfterRegex.find(apiResponse)
        if (match != null) {
            val seconds = match.groupValues[1]
            plugin.logger.severe("⏰ Повторите попытку через: $seconds секунд")
            plugin.logger.severe("")
        }
        
        plugin.logger.severe("💡 РЕШЕНИЕ:")
        plugin.logger.severe("   1. Подождите указанное время")
        plugin.logger.severe("   2. Уменьшите частоту отправки сообщений")
        plugin.logger.severe("   3. Увеличьте auto-delete-seconds в config.yml")
        plugin.logger.severe("   4. Отключите ненужные уведомления")
    }
    
    /**
     * Диагностирует неизвестную ошибку
     */
    private fun diagnoseUnknownError(errorCode: Int, @Suppress("UNUSED_PARAMETER") apiResponse: String) {
        plugin.logger.severe("🔍 Тип ошибки: UNKNOWN ($errorCode)")
        plugin.logger.severe("")
        plugin.logger.severe("❌ ПРОБЛЕМА: Неизвестная ошибка API")
        plugin.logger.severe("")
        plugin.logger.severe("💡 РЕШЕНИЕ:")
        plugin.logger.severe("   • Проверьте документацию Telegram Bot API")
        plugin.logger.severe("   • Обратитесь к разработчику плагина")
        plugin.logger.severe("   • Сообщите об ошибке: https://t.me/Zoobastiks")
    }
    
    /**
     * Диагностирует общие исключения Telegram API
     */
    private fun diagnoseApiException(
        exception: TelegramApiException,
        context: String,
        @Suppress("UNUSED_PARAMETER") message: String?,
        @Suppress("UNUSED_PARAMETER") configPath: String?
    ) {
        plugin.logger.severe("╔════════════════════════════════════════════════════════════════╗")
        plugin.logger.severe("║              ❌ ОШИБКА TELEGRAM API                            ║")
        plugin.logger.severe("╚════════════════════════════════════════════════════════════════╝")
        plugin.logger.severe("")
        plugin.logger.severe("📍 Контекст: $context")
        plugin.logger.severe("📝 Сообщение: ${exception.message}")
        plugin.logger.severe("")
        plugin.logger.severe("💡 РЕШЕНИЕ:")
        plugin.logger.severe("   • Проверьте подключение к интернету")
        plugin.logger.severe("   • Проверьте доступность api.telegram.org")
        plugin.logger.severe("   • Перезапустите плагин: /telegram reload")
        plugin.logger.severe("")
        plugin.logger.severe("════════════════════════════════════════════════════════════════")
        plugin.logger.severe("")
    }
    
    /**
     * Диагностирует общие исключения
     */
    private fun diagnoseGenericException(
        exception: Exception,
        context: String,
        @Suppress("UNUSED_PARAMETER") message: String?,
        @Suppress("UNUSED_PARAMETER") configPath: String?
    ) {
        plugin.logger.severe("╔════════════════════════════════════════════════════════════════╗")
        plugin.logger.severe("║                    ❌ ОШИБКА ПЛАГИНА                          ║")
        plugin.logger.severe("╚════════════════════════════════════════════════════════════════╝")
        plugin.logger.severe("")
        plugin.logger.severe("📍 Контекст: $context")
        plugin.logger.severe("🔍 Тип: ${exception.javaClass.simpleName}")
        plugin.logger.severe("📝 Сообщение: ${exception.message}")
        plugin.logger.severe("")
        
        if (plugin.config.getBoolean("debug.enabled", false)) {
            plugin.logger.severe("🐛 Stack Trace:")
            exception.printStackTrace()
            plugin.logger.severe("")
        }
        
        plugin.logger.severe("════════════════════════════════════════════════════════════════")
        plugin.logger.severe("")
    }
    
    /**
     * Находит проблемы в Markdown форматировании
     */
    private fun findMarkdownIssues(text: String): List<String> {
        val issues = mutableListOf<String>()
        
        // Проверка парности символов
        val asteriskCount = text.count { it == '*' }
        if (asteriskCount % 2 != 0) {
            issues.add("Непарное количество символов * (жирный текст) - найдено: $asteriskCount")
        }
        
        val underscoreCount = text.count { it == '_' }
        if (underscoreCount % 2 != 0) {
            issues.add("Непарное количество символов _ (курсив) - найдено: $underscoreCount")
        }
        
        val backtickCount = text.count { it == '`' }
        if (backtickCount % 2 != 0) {
            issues.add("Непарное количество символов ` (код) - найдено: $backtickCount")
        }
        
        val openBrackets = text.count { it == '[' }
        val closeBrackets = text.count { it == ']' }
        if (openBrackets != closeBrackets) {
            issues.add("Непарное количество квадратных скобок - открывающих: $openBrackets, закрывающих: $closeBrackets")
        }
        
        // Поиск неэкранированных специальных символов
        val specialChars = listOf('_', '*', '[', ']', '(', ')', '~', '`', '>', '#', '+', '-', '=', '|', '{', '}', '.', '!')
        for (char in specialChars) {
            val positions = text.indices.filter { text[it] == char && (it == 0 || text[it - 1] != '\\') }
            if (positions.isNotEmpty() && char !in listOf('*', '_', '`', '[', ']')) {
                issues.add("Неэкранированный символ '$char' на позициях: ${positions.take(5).joinToString(", ")}${if (positions.size > 5) "..." else ""}")
            }
        }
        
        return issues
    }
    
    /**
     * Находит местоположение параметра в конфигурационном файле
     */
    private fun findConfigLocation(configPath: String): Pair<String?, Int?> {
        val configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile.exists()) return Pair(null, null)
        
        val lines = configFile.readLines()
        val searchKey = configPath.substringAfterLast(".")
        
        for ((index, line) in lines.withIndex()) {
            if (line.trim().startsWith("$searchKey:")) {
                return Pair("config.yml", index + 1)
            }
        }
        
        return Pair("config.yml", null)
    }
}
