package org.zoobastiks.ztelegram.bot

import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.utils.TelegramErrorDiagnostics
import java.util.concurrent.ConcurrentHashMap

/**
 * Безопасная отправка сообщений в Telegram с детальной диагностикой ошибок
 */
class TelegramMessageSender(private val bot: TBot) {
    
    private val plugin: ZTele
        get() = ZTele.instance
    
    // Кэш последних ошибок для предотвращения спама в логах
    private val errorCache = ConcurrentHashMap<String, Long>()
    private val ERROR_CACHE_DURATION = 60_000L // 1 минута
    
    /**
     * Отправляет сообщение с автоматической диагностикой ошибок
     */
    fun sendMessage(
        chatId: String,
        text: String,
        parseMode: String = "Markdown",
        configPath: String? = null,
        context: String = "SEND_MESSAGE",
        retryWithoutThread: Boolean = true
    ): Message? {
        try {
            val sendMessage = SendMessage().apply {
                this.chatId = chatId
                this.text = text
                this.parseMode = parseMode
                this.disableWebPagePreview = true
            }
            
            // Если chatId содержит тему (формат: -1001234567890_123)
            if (chatId.contains("_")) {
                val parts = chatId.split("_")
                val baseChatId = parts[0]
                val threadId = parts[1].toIntOrNull()
                
                sendMessage.chatId = baseChatId
                sendMessage.messageThreadId = threadId
                
                if (plugin.config.getBoolean("debug.enabled", false)) {
                    plugin.logger.info("Sending auto-delete message to thread $threadId in chat $baseChatId")
                }
            }
            
            return bot.execute(sendMessage)
            
        } catch (e: TelegramApiRequestException) {
            // Проверяем, является ли ошибка "thread not found"
            if (retryWithoutThread && chatId.contains("_") && 
                (e.message?.contains("thread not found", ignoreCase = true) == true ||
                 e.message?.contains("message thread not found", ignoreCase = true) == true)) {
                
                // Пробуем отправить в основной канал без темы
                val baseChatId = chatId.substringBefore("_")
                plugin.logger.info("Thread not found, retrying without thread: $baseChatId")
                return sendMessage(baseChatId, text, parseMode, configPath, context, retryWithoutThread = false)
            }
            
            handleTelegramError(e, context, text, configPath, chatId)
            return null
        } catch (e: TelegramApiException) {
            handleTelegramError(e, context, text, configPath, chatId)
            return null
        } catch (e: Exception) {
            handleGenericError(e, context, text, configPath, chatId)
            return null
        }
    }
    
    /**
     * Отправляет сообщение с автоудалением
     */
    fun sendAutoDeleteMessage(
        chatId: String,
        text: String,
        deleteAfterSeconds: Int,
        parseMode: String = "Markdown",
        configPath: String? = null,
        context: String = "SEND_AUTO_DELETE"
    ): Message? {
        val message = sendMessage(chatId, text, parseMode, configPath, context)
        
        if (message == null) return null
        
        if (deleteAfterSeconds > 0) {
            val actualChatId = message.chatId.toString()
            scheduleMessageDeletion(actualChatId, message.messageId, deleteAfterSeconds)
        }
        
        return message
    }
    
    /**
     * Планирует удаление сообщения
     */
    private fun scheduleMessageDeletion(chatId: String, messageId: Int, delaySeconds: Int) {
        plugin.server.scheduler.runTaskLaterAsynchronously(plugin, Runnable {
            try {
                val deleteMessage = DeleteMessage().apply {
                    this.chatId = if (chatId.contains("_")) chatId.substringBefore("_") else chatId
                    this.messageId = messageId
                }
                bot.execute(deleteMessage)
            } catch (e: Exception) {
                // Игнорируем ошибки удаления (сообщение могло быть уже удалено)
                if (plugin.config.getBoolean("debug.enabled", false)) {
                    plugin.logger.fine("Could not delete message $messageId: ${e.message}")
                }
            }
        }, (delaySeconds * 20L))
    }
    
    /**
     * Обрабатывает ошибки Telegram API
     */
    private fun handleTelegramError(
        exception: Exception,
        context: String,
        message: String,
        configPath: String?,
        chatId: String
    ) {
        // Проверяем кэш ошибок
        val errorKey = "${exception.javaClass.simpleName}:${exception.message}:$configPath"
        val lastErrorTime = errorCache[errorKey] ?: 0L
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastErrorTime < ERROR_CACHE_DURATION) {
            // Ошибка уже была недавно, не спамим в логи
            return
        }
        
        errorCache[errorKey] = currentTime
        
        // Выполняем детальную диагностику
        TelegramErrorDiagnostics.diagnoseError(
            exception = exception,
            context = context,
            message = message,
            configPath = configPath
        )
        
        // Дополнительная информация
        plugin.logger.severe("📤 Попытка отправки в chatId: $chatId")
        plugin.logger.severe("")
    }
    
    /**
     * Обрабатывает общие ошибки
     */
    private fun handleGenericError(
        exception: Exception,
        context: String,
        @Suppress("UNUSED_PARAMETER") message: String,
        @Suppress("UNUSED_PARAMETER") configPath: String?,
        chatId: String
    ) {
        val errorKey = "${exception.javaClass.simpleName}:${exception.message}"
        val lastErrorTime = errorCache[errorKey] ?: 0L
        val currentTime = System.currentTimeMillis()
        
        if (currentTime - lastErrorTime < ERROR_CACHE_DURATION) {
            return
        }
        
        errorCache[errorKey] = currentTime
        
        plugin.logger.severe("╔════════════════════════════════════════════════════════════════╗")
        plugin.logger.severe("║                  ❌ НЕОЖИДАННАЯ ОШИБКА                         ║")
        plugin.logger.severe("╚════════════════════════════════════════════════════════════════╝")
        plugin.logger.severe("")
        plugin.logger.severe("📍 Контекст: $context")
        plugin.logger.severe("🔍 Тип: ${exception.javaClass.simpleName}")
        plugin.logger.severe("📝 Сообщение: ${exception.message}")
        plugin.logger.severe("📤 ChatId: $chatId")
        
        if (configPath != null) {
            plugin.logger.severe("🔑 Путь в конфиге: $configPath")
        }
        
        plugin.logger.severe("")
        
        if (plugin.config.getBoolean("debug.enabled", false)) {
            plugin.logger.severe("🐛 Stack Trace:")
            exception.printStackTrace()
        }
        
        plugin.logger.severe("════════════════════════════════════════════════════════════════")
        plugin.logger.severe("")
    }
    
    /**
     * Очищает кэш ошибок
     */
    fun clearErrorCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = errorCache.entries
            .filter { currentTime - it.value > ERROR_CACHE_DURATION }
            .map { it.key }
        
        expiredKeys.forEach { errorCache.remove(it) }
    }
}
