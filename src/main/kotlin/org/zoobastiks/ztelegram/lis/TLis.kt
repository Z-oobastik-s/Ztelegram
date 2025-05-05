package org.zoobastiks.ztelegram.lis

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.bot.TBot
import org.bukkit.Bukkit

class TLis(private val plugin: ZTele) : Listener {
    private val bot: TBot
        get() = ZTele.bot

    // Флаг для отслеживания обработанных сообщений чата
    private val processedMessages = mutableMapOf<String, Long>()
    
    // Время хранения кэша сообщений (в миллисекундах)
    private val MESSAGE_CACHE_EXPIRY = 1000L // 1 секунда

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name
        bot.sendPlayerJoinMessage(playerName)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val playerName = event.player.name
        bot.sendPlayerQuitMessage(playerName)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val deathMessage = event.deathMessage ?: ""
        bot.sendPlayerDeathMessage(player.name, deathMessage)
    }

    /**
     * Обработчик нового API чата PaperMC
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAsyncChat(event: AsyncChatEvent) {
        try {
            val playerName = event.player.name
            val message = PlainTextComponentSerializer.plainText().serialize(event.message())
            
            // Создаем уникальный ключ для этого сообщения
            val messageKey = "$playerName:$message:${System.currentTimeMillis()}"
            
            // Проверяем, не обрабатывали ли мы уже такое сообщение недавно
            if (!hasRecentlySentMessage(playerName, message)) {
                bot.sendPlayerChatMessage(playerName, message)
                // Сохраняем сообщение в кэше
                markMessageAsProcessed(playerName, message)
                plugin.logger.info("Message sent to Telegram from AsyncChatEvent: $playerName - $message")
            } else {
                plugin.logger.info("Skipping duplicate message from AsyncChatEvent: $playerName - $message")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Error processing AsyncChatEvent: ${e.message}")
        }
    }

    /**
     * Обработчик старого API чата Bukkit/Spigot
     * Используется как запасной, если новый API не сработал
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // Проверяем, поддерживает ли сервер новый API чата (Paper)
        if (isPaperChatAPISupported()) {
            // Новый API поддерживается, этот метод не нужен
            return
        }
        
        val playerName = event.player.name
        val message = event.message
        
        // Проверяем, не обрабатывали ли мы уже такое сообщение недавно
        if (!hasRecentlySentMessage(playerName, message)) {
            bot.sendPlayerChatMessage(playerName, message)
            // Сохраняем сообщение в кэше
            markMessageAsProcessed(playerName, message)
            plugin.logger.info("Message sent to Telegram from AsyncPlayerChatEvent: $playerName - $message")
        } else {
            plugin.logger.info("Skipping duplicate message from AsyncPlayerChatEvent: $playerName - $message")
        }
    }

    /**
     * Специальный обработчик для совместимости с плагином AdvancedChat
     * Вызывается только если плагин AdvancedChat установлен
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChatLowestPriority(event: AsyncPlayerChatEvent) {
        // Используем только с плагином AdvancedChat
        if (!event.isCancelled && Bukkit.getPluginManager().getPlugin("AdvancedChat") != null) {
            val playerName = event.player.name
            val message = event.message
            
            // Задержка для предотвращения дублирования с другими обработчиками
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                // Проверяем, не обрабатывали ли мы уже такое сообщение недавно
                if (!hasRecentlySentMessage(playerName, message)) {
                    bot.sendPlayerChatMessage(playerName, message)
                    // Сохраняем сообщение в кэше
                    markMessageAsProcessed(playerName, message)
                    plugin.logger.info("Message sent to Telegram from AdvancedChat: $playerName - $message")
                } else {
                    plugin.logger.info("Skipping duplicate message from AdvancedChat: $playerName - $message")
                }
            }, 1L)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val playerName = event.player.name
        val command = event.message.substring(1) // Remove the leading '/'
        
        // Log command to Telegram
        bot.sendPlayerCommandMessage(playerName, command)
    }

    /**
     * Проверяет, поддерживается ли новый API чата от Paper
     */
    private fun isPaperChatAPISupported(): Boolean {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent")
            return true
        } catch (e: ClassNotFoundException) {
            return false
        }
    }

    /**
     * Проверяет, было ли недавно отправлено аналогичное сообщение
     */
    private fun hasRecentlySentMessage(playerName: String, message: String): Boolean {
        val key = "$playerName:$message"
        val currentTime = System.currentTimeMillis()
        
        // Очищаем старые записи
        val expiredKeys = processedMessages.filter { currentTime - it.value > MESSAGE_CACHE_EXPIRY }.keys
        expiredKeys.forEach { processedMessages.remove(it) }
        
        return processedMessages.containsKey(key)
    }

    /**
     * Отмечает сообщение как обработанное, чтобы избежать дублирования
     */
    private fun markMessageAsProcessed(playerName: String, message: String) {
        val key = "$playerName:$message"
        processedMessages[key] = System.currentTimeMillis()
    }
} 