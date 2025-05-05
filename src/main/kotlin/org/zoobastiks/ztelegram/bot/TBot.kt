package org.zoobastiks.ztelegram.bot

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.User
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.mgr.PMgr
import org.zoobastiks.ztelegram.ColorUtils
import org.zoobastiks.ztelegram.GradientUtils
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

class TBot(private val plugin: ZTele) : TelegramLongPollingBot() {
    private val conf: TConf = ZTele.conf
    private val mgr: PMgr = ZTele.mgr
    private var botsApi: TelegramBotsApi? = null
    private val miniMessage = MiniMessage.miniMessage()
    private var botSession: DefaultBotSession? = null
    
    override fun getBotToken(): String {
        return TConf.botToken
    }
    
    fun start() {
        try {
            // Предотвращаем повторный запуск
            if (botsApi != null || botSession != null) {
                plugin.logger.warning("Detected attempt to start already running bot")
                stop() // Останавливаем существующий бот перед запуском нового
                Thread.sleep(2000) // Даем время для полной остановки
            }
            
            // Проверяем, нет ли уже активной сессии
            if (botSession != null) {
                try {
                    plugin.logger.warning("Detected existing bot session, stopping it first...")
                    botSession!!.stop()
                    botSession = null
                    Thread.sleep(1000) // Даем время для завершения сессии
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to stop existing session: ${e.message}")
                }
            }
            
            // Удаляем возможные конфликтующие потоки
            try {
                val threadGroup = Thread.currentThread().threadGroup
                val threads = arrayOfNulls<Thread>(threadGroup.activeCount() * 2)
                threadGroup.enumerate(threads, true)
                
                for (thread in threads) {
                    if (thread != null && thread != Thread.currentThread() && !thread.isInterrupted && 
                       (thread.name.contains("DefaultBotSession") || 
                        thread.name.contains("Telegram") || 
                        thread.name.contains("telegram"))) {
                        try {
                            plugin.logger.warning("Interrupting existing Telegram thread before start: ${thread.name}")
                            thread.interrupt()
                        } catch (e: Exception) {
                            // Игнорируем ошибки
                        }
                    }
                }
                
                // Даем время для завершения потоков
                Thread.sleep(500)
            } catch (e: Exception) {
                plugin.logger.warning("Error cleaning up before bot start: ${e.message}")
            }
            
            // Принудительно вызываем сборщик мусора, чтобы освободить ресурсы HTTP клиента
            try {
                System.gc()
                Thread.sleep(500) // Даем время для сборки мусора
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
            
            // Устанавливаем таймаут для HTTP клиента перед созданием сессии
            setupHttpTimeouts()
            
            // Создаем экземпляр API
            botsApi = TelegramBotsApi(DefaultBotSession::class.java)
            
            // Сначала очищаем предыдущие сессии с помощью clean запроса
            try {
                val clean = org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook()
                clean.dropPendingUpdates = true
                execute(clean)
                Thread.sleep(500) // Ждем обработки запроса
            } catch (e: Exception) {
                // Игнорируем ошибки здесь
                plugin.logger.warning("Error clearing webhook: ${e.message}")
            }
            
            // Регистрируем бота с получением ссылки на сессию
            val session = botsApi!!.registerBot(this)
            
            // Сохраняем ссылку на сессию для последующего корректного закрытия
            if (session is DefaultBotSession) {
                botSession = session
            }
            
            plugin.logger.info("Telegram bot started successfully!")
        } catch (e: TelegramApiException) {
            plugin.logger.severe("Failed to start Telegram bot: ${e.message}")
            
            // Обнуляем ссылки на случай частичной инициализации
            try {
                botSession = null
                botsApi = null
            } catch (ex: Exception) {
                // Игнорируем ошибки
            }
            
            throw e
        }
    }
    
    // Метод для настройки таймаутов HTTP клиента
    private fun setupHttpTimeouts() {
        try {
            // Настраиваем системные свойства для Apache HTTP Client
            System.setProperty("http.keepAlive", "false")
            System.setProperty("http.maxConnections", "10")
            System.setProperty("sun.net.http.errorstream.enableBuffering", "true")
            System.setProperty("sun.net.client.defaultConnectTimeout", "10000")
            System.setProperty("sun.net.client.defaultReadTimeout", "10000")
            
            // Для тонкой настройки Apache HTTP Client нужно было бы использовать 
            // отдельный экземпляр HttpClient с настроенными параметрами
        } catch (e: Exception) {
            plugin.logger.warning("Error setting up HTTP timeouts: ${e.message}")
        }
    }
    
    fun stop() {
        // Предотвращаем повторную остановку
        if (botsApi == null && botSession == null) {
            plugin.logger.info("Bot is already stopped or was never started")
            return
        }
        
        // Создаем отдельный поток для остановки бота
        val shutdownThread = Thread {
            try {
                // Очищаем вначале ссылку на API, чтобы не создавались новые запросы
                val localBotsApi = botsApi
                botsApi = null
                
                // 1. Работаем с локальной копией botSession для безопасности
                val localBotSession = botSession
                botSession = null
                
                // 2. Сначала пытаемся очистить обновления
                try {
                    val clean = org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook()
                    clean.dropPendingUpdates = true
                    execute(clean)
                    // Небольшая задержка после очистки обновлений
                    Thread.sleep(500)
                } catch (e: Exception) {
                    // Игнорируем ошибки здесь
                    plugin.logger.warning("Error clearing updates: ${e.message}")
                }
                
                // 3. Закрываем сессию, если она существует
                if (localBotSession != null) {
                    try {
                        localBotSession.stop()
                        // Дожидаемся завершения потоков
                        Thread.sleep(500)
                    } catch (e: Exception) {
                        plugin.logger.warning("Error stopping bot session: ${e.message}")
                    }
                }
                
                // 4. Уничтожаем все активные потоки, связанные с DefaultBotSession - более осторожно
                try {
                    val threadGroup = Thread.currentThread().threadGroup
                    val threads = arrayOfNulls<Thread>(threadGroup.activeCount() * 2)
                    threadGroup.enumerate(threads, true)
                    
                    var telegramThreadsFound = false
                    
                    for (thread in threads) {
                        if (thread != null && thread != Thread.currentThread() && !thread.isInterrupted && 
                           (thread.name.contains("DefaultBotSession") || 
                            thread.name.contains("Telegram") || 
                            thread.name.contains("telegram"))) {
                            try {
                                telegramThreadsFound = true
                                plugin.logger.info("Interrupting Telegram thread: ${thread.name}")
                                thread.interrupt()
                            } catch (e: Exception) {
                                // Игнорируем ошибки
                                plugin.logger.warning("Error interrupting thread ${thread.name}: ${e.message}")
                            }
                        }
                    }
                    
                    // Если были найдены потоки Telegram, ждем их завершения
                    if (telegramThreadsFound) {
                        Thread.sleep(1000)
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("Error interrupting bot threads: ${e.message}")
                }
                
                // Устанавливаем флаг успешного завершения
                plugin.logger.info("Telegram bot stopped")
            } catch (e: Exception) {
                plugin.logger.severe("Error stopping Telegram bot: ${e.message}")
            } finally {
                // Сбрасываем ссылки на всякий случай
                botSession = null
                botsApi = null
                
                // Пытаемся собрать мусор для освобождения ресурсов
                try {
                    System.gc()
                } catch (e: Exception) {
                    // Игнорируем ошибки
                }
            }
        }
        
        // Устанавливаем флаг демона, чтобы поток не блокировал выключение сервера
        shutdownThread.isDaemon = true
        shutdownThread.name = "ZTelegram-Shutdown-Thread"
        
        // Запускаем поток и ждем максимум 5 секунд для завершения
        shutdownThread.start()
        try {
            shutdownThread.join(5000) // Увеличиваем время ожидания до 5 секунд
        } catch (e: InterruptedException) {
            plugin.logger.warning("Interrupted while waiting for bot shutdown")
        }
        
        // Если поток все еще активен, выводим предупреждение и продолжаем
        if (shutdownThread.isAlive) {
            plugin.logger.warning("Bot shutdown is taking longer than expected, continuing with reload")
            // Принудительно прерываем поток
            try {
                shutdownThread.interrupt()
            } catch (e: Exception) {
                // Игнорируем ошибки
            }
        }
    }
    
    override fun getBotUsername(): String {
        return "YourTelegramBot"
    }
    
    override fun onUpdateReceived(update: Update) {
        if (!update.hasMessage()) return
        
        val message = update.message
        if (!message.hasText()) return
        
        val chatId = message.chatId.toString()
        val text = message.text
        val username = message.from.userName ?: message.from.firstName
        val userId = message.from.id.toString()
        
        plugin.logger.info("Received message from user: $username, userId: $userId, chatId: $chatId")
        
        when (chatId) {
            conf.mainChannelId -> handleMainChannelMessage(text, username, userId)
            conf.consoleChannelId -> handleConsoleChannelMessage(text, username)
            conf.registerChannelId -> handleRegisterChannelMessage(message, message.from)
        }
    }
    
    private fun handleMainChannelMessage(text: String, username: String, userId: String) {
        if (!conf.mainChannelEnabled) return
        
        // Проверка черного списка
        if (conf.blacklistEnabled && mgr.isPlayerBlacklisted(userId)) {
            // Отправляем сообщение о блокировке в основной канал вместо личного сообщения
            sendAutoDeleteMessage(conf.mainChannelId, conf.blockedMessage, conf.commandsAutoDeleteSeconds)
            return
        }
        
        // Проверка белого списка
        if (conf.whitelistEnabled && !mgr.isPlayerWhitelisted(userId)) {
            // Отправляем сообщение о необходимости регистрации в основной канал вместо личного сообщения
            sendAutoDeleteMessage(conf.mainChannelId, conf.noRegistrationMessage, conf.commandsAutoDeleteSeconds)
            return
        }
        
        if (text.startsWith("/")) {
            handleMainChannelCommand(text, username, userId)
            return
        }
        
        // Проверяем, не играет ли пользователь в игру
        if (ZTele.game.hasActiveGame(userId)) {
            // Обрабатываем ответ на игру
            val (isCorrect, message) = ZTele.game.checkAnswer(userId, text)
            
            // Отправляем ответ через автоудаляемое сообщение, независимо от правильности ответа
            sendAutoDeleteMessage(conf.mainChannelId, message, conf.gameAutoDeleteSeconds)
            
            // Не отправляем обычное сообщение в чат
            return
        }
        
        if (conf.mainChannelChatEnabled) {
            // Получаем связанный игровой ник, если пользователь зарегистрирован
            val playerName = mgr.getPlayerByTelegramId(userId) ?: username
            
            val formattedMessage = conf.formatTelegramToMinecraft
                .replace("%player%", playerName)
                .replace("%message%", text)
                .replace("\\n", "\n")
            
            // Отправляем сообщение на сервер с поддержкой градиентов и цветов
            sendFormattedMessageToServer(formattedMessage)
        }
    }
    
    // Универсальный метод для отправки отформатированных сообщений на сервер
    private fun sendFormattedMessageToServer(message: String) {
        // Проверяем наличие MiniMessage форматирования
        if (message.contains("<") && message.contains(">")) {
            // Если есть MiniMessage теги (градиенты и др.)
            val component = GradientUtils.parseMixedFormat(message)
            Bukkit.getServer().sendMessage(component)
        } else {
            // Для обычных цветовых кодов
            val processedMessage = ColorUtils.translateColorCodes(message)
            Bukkit.getServer().broadcast(Component.text().append(
                LegacyComponentSerializer.legacySection().deserialize(processedMessage)
            ).build())
        }
    }
    
    // Метод для форматирования текста с заменой плейсхолдеров и обработкой переносов строк
    private fun formatMessage(template: String, replacements: Map<String, String>): String {
        var result = template
        
        // Заменяем плейсхолдеры
        for ((key, value) in replacements) {
            result = result.replace(key, value)
        }
        
        // Обрабатываем переносы строк
        return result.replace("\\n", "\n")
    }
    
    private fun handleMainChannelCommand(command: String, username: String, userId: String) {
        plugin.logger.info("Processing command from user: $username, userId: $userId")
        
        // Создаем карту команд и их псевдонимов
        val commandAliases = mapOf(
            "online" to setOf("/online", "/онлайн"),
            "tps" to setOf("/tps", "/тпс"),
            "restart" to setOf("/restart", "/рестарт"),
            "gender" to setOf("/gender", "/пол"),
            "player" to setOf("/player", "/ник", "/игрок"),
            "commands" to setOf("/cmd", "/команды", "/commands", "/help", "/помощь"),
            "game" to setOf("/game", "/игра")
        )
        
        // Разделяем команду и аргументы
        val parts = command.split(" ", limit = 2)
        val cmd = parts[0].lowercase()
        val arguments = if (parts.size > 1) parts[1] else ""
        
        // Определяем, какая команда была использована
        for ((key, aliases) in commandAliases) {
            if (aliases.contains(cmd)) {
                executeCommand(key, arguments, username, userId)
                return
            }
        }
    }
    
    private fun executeCommand(command: String, arguments: String, username: String, userId: String) {
        when (command) {
            "online" -> {
                if (!conf.enabledOnlineCommand) return
                
                val onlinePlayers = Bukkit.getOnlinePlayers()
                val playerNames = onlinePlayers
                    .filter { player -> !mgr.isPlayerHidden(player.name) }
                    .joinToString(", ") { player -> player.name }
                
                val response = formatMessage(conf.onlineCommandResponse, mapOf(
                    "%online%" to onlinePlayers.size.toString(),
                    "%max%" to Bukkit.getMaxPlayers().toString(),
                    "%players%" to (if (playerNames.isEmpty()) "Nobody" else playerNames)
                ))
                
                sendAutoDeleteMessage(conf.mainChannelId, response, conf.commandsAutoDeleteSeconds)
            }
            
            "tps" -> {
                if (!conf.enabledTpsCommand) return
                
                // Получаем все три значения TPS (1, 5, 15 минут)
                val tpsValues = ZTele.tpsTracker.getAllTps()
                
                // Форматируем TPS значения с цветовыми индикаторами
                val formattedValues = tpsValues.mapIndexed { index, tps ->
                    val formattedTps = String.format("%.2f", tps)
                    val period = when(index) {
                        0 -> "1мин"
                        1 -> "5мин"
                        2 -> "15мин"
                        else -> ""
                    }
                    
                    // Добавляем цветовые индикаторы
                    val indicator = when {
                        tps >= 19.5 -> "🟢" // отлично
                        tps >= 18.0 -> "🟡" // хорошо
                        tps >= 15.0 -> "🟠" // нормально
                        else -> "🔴" // плохо
                    }
                    
                    "$indicator $period: $formattedTps"
                }.joinToString("\n")
                
                // Общая оценка сервера
                val avgTps = tpsValues.average()
                val serverStatus = when {
                    avgTps >= 19.5 -> "✅ Отличное"
                    avgTps >= 18.0 -> "✅ Хорошее"
                    avgTps >= 15.0 -> "⚠️ Нормальное"
                    else -> "❌ Плохое"
                }
                
                // Используем шаблон из конфигурации вместо жесткого кода
                val response = formatMessage(conf.tpsCommandResponse, mapOf(
                    "%tps%" to formattedValues,
                    "%status%" to serverStatus
                ))
                
                sendAutoDeleteMessage(conf.mainChannelId, response, conf.commandsAutoDeleteSeconds)
            }
            
            "restart" -> {
                if (!conf.enabledRestartCommand) return
                
                val response = formatMessage(conf.restartCommandResponse, mapOf())
                sendToMainChannel(response)
                
                // Ждем 5 секунд и выполняем команду перезагрузки
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart")
                }, 100L) // 5 секунд (100 тиков)
            }
            
            "gender" -> {
                if (!conf.enabledGenderCommand) return
                
                if (arguments.isEmpty()) {
                    sendAutoDeleteMessage(conf.mainChannelId, conf.genderCommandUsage.replace("\\n", "\n"), conf.commandsAutoDeleteSeconds)
                    return
                }
                
                // Получаем игрока по Telegram ID
                val player = mgr.getPlayerByTelegramId(userId)
                
                if (player == null) {
                    sendAutoDeleteMessage(conf.mainChannelId, conf.genderCommandNoPlayer.replace("\\n", "\n"), conf.commandsAutoDeleteSeconds)
                    return
                }
                
                val genderArg = arguments.lowercase()
                val gender = when {
                    genderArg == "man" || genderArg == "м" -> "man"
                    genderArg == "girl" || genderArg == "ж" -> "girl"
                    else -> null
                }
                
                if (gender == null) {
                    sendAutoDeleteMessage(conf.mainChannelId, conf.genderCommandUsage.replace("\\n", "\n"), conf.commandsAutoDeleteSeconds)
                    return
                }
                
                // Устанавливаем пол игрока
                if (mgr.setPlayerGender(player, gender)) {
                    sendAutoDeleteMessage(conf.mainChannelId, formatMessage(conf.genderCommandResponse, mapOf(
                        "%player%" to player,
                        "%gender%" to conf.getGenderTranslation(gender)
                    )), conf.commandsAutoDeleteSeconds)
                }
            }
            
            "player" -> {
                if (!conf.enabledPlayerCommand) return
                
                if (arguments.isEmpty()) {
                    sendAutoDeleteMessage(conf.mainChannelId, conf.playerCommandUsage.replace("\\n", "\n"), conf.commandsAutoDeleteSeconds)
                    return
                }
                
                val playerName = arguments.split(" ")[0]
                val playerData = mgr.getPlayerData(playerName)
                
                // Проверяем, существует ли игрок в Minecraft, даже если не зарегистрирован
                val isOnline = Bukkit.getPlayerExact(playerName) != null
                val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
                
                if (!offlinePlayer.hasPlayedBefore() && !isOnline) {
                    sendAutoDeleteMessage(conf.mainChannelId, formatMessage(conf.playerCommandNoPlayer, mapOf(
                        "%player%" to playerName
                    )), conf.commandsAutoDeleteSeconds)
                    return
                }
                
                val rawGender = playerData?.gender ?: "Not set"
                // Используем перевод для gender
                val gender = if (rawGender == "man" || rawGender == "girl") conf.getGenderTranslation(rawGender) else conf.getStatusTranslation("not_set")
                
                // Форматируем баланс с двумя знаками после запятой
                val rawBalance = getPlayerBalance(playerName)
                val balance = String.format("%.2f", rawBalance)
                
                val currentHealth = if (isOnline) Bukkit.getPlayerExact(playerName)?.health?.toInt() ?: 0 else 0
                val coords = if (isOnline) {
                    val loc = Bukkit.getPlayerExact(playerName)?.location
                    "X: ${loc?.blockX}, Y: ${loc?.blockY}, Z: ${loc?.blockZ}"
                } else conf.getStatusTranslation("offline_coords")
                
                // Переводим статусы для отображения
                val onlineStatus = if (isOnline) conf.getStatusTranslation("online") else conf.getStatusTranslation("offline")
                
                // Форматируем дату регистрации с корректным форматом
                val registeredDate = if (playerData?.registered != null) {
                    try {
                        // Парсим исходную дату
                        val originalFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        val date = originalFormat.parse(playerData.registered)
                        
                        // Устанавливаем часовой пояс МСК (+3)
                        originalFormat.timeZone = java.util.TimeZone.getTimeZone("Europe/Moscow")
                        
                        // Форматируем дату в нужный формат
                        val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                        dateFormat.timeZone = java.util.TimeZone.getTimeZone("Europe/Moscow")
                        dateFormat.format(date)
                    } catch (e: Exception) {
                        // В случае ошибки парсинга оставляем исходную дату
                        playerData.registered
                    }
                } else conf.getStatusTranslation("not_registered")
                
                // Добавляем новую информацию
                val firstPlayed = if (offlinePlayer.hasPlayedBefore()) {
                    val date = java.util.Date(offlinePlayer.firstPlayed)
                    val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                    dateFormat.format(date)
                } else conf.getStatusTranslation("never")
                
                val deaths = offlinePlayer.getStatistic(org.bukkit.Statistic.DEATHS)
                val level = if (isOnline) Bukkit.getPlayerExact(playerName)?.level ?: 0 else 0
                
                val response = formatMessage(conf.playerCommandResponse, mapOf(
                    "%player%" to playerName,
                    "%gender%" to gender,
                    "%balance%" to balance,
                    "%online%" to onlineStatus,
                    "%health%" to currentHealth.toString(),
                    "%registered%" to registeredDate,
                    "%coords%" to coords,
                    "%first_played%" to firstPlayed,
                    "%deaths%" to deaths.toString(),
                    "%level%" to level.toString()
                ))
                
                sendAutoDeleteMessage(conf.mainChannelId, response, conf.commandsAutoDeleteSeconds)
            }
            
            "commands" -> {
                if (!conf.enabledCommandsListCommand) return
                
                // Отправляем список доступных команд
                sendAutoDeleteMessage(conf.mainChannelId, conf.commandsListResponse.replace("\\n", "\n"), conf.commandsAutoDeleteSeconds)
            }
            
            "game" -> {
                // Получаем имя игрока для игры
                var playerName = ""
                
                // Проверяем аргументы
                if (arguments.isNotEmpty()) {
                    playerName = arguments.split(" ")[0]
                } else {
                    // Если аргументы не указаны, проверяем, есть ли привязанный игрок
                    playerName = mgr.getPlayerByTelegramId(userId) ?: ""
                    
                    if (playerName.isEmpty()) {
                        sendAutoDeleteMessage(conf.mainChannelId, conf.gameNotRegisteredMessage, conf.gameAutoDeleteSeconds)
                        return
                    }
                }
                
                // Запускаем игру
                val gameResponse = ZTele.game.startGame(userId, playerName)
                
                // Отправляем автоудаляемое сообщение с игрой
                sendAutoDeleteMessage(conf.mainChannelId, gameResponse, conf.gameAutoDeleteSeconds)
            }
        }
    }
    
    private fun getPlayerBalance(playerName: String): Double {
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) return 0.0
        
        val rsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy::class.java)
        val economy = rsp?.provider ?: return 0.0
        
        return economy.getBalance(Bukkit.getOfflinePlayer(playerName))
    }
    
    private fun handleConsoleChannelMessage(text: String, username: String) {
        if (!conf.consoleChannelEnabled) return
        
        // Проверяем, является ли сообщение специальной командой белого списка
        if (text.startsWith("/whitelist ")) {
            handleWhitelistCommand(text, username)
            return
        }
        
        // Проверяем, является ли сообщение командой плагина
        if (text.startsWith("/telegram ")) {
            handlePluginCommand(text, username)
            return
        }
        
        // Проверяем, является ли сообщение командой help
        if (text == "/help" || text == "/помощь") {
            sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginHelpMessage, conf.consoleAutoDeleteSeconds)
            return
        }
        
        // Если это обычная команда, выполняем ее как консольную команду
        Bukkit.getScheduler().runTask(plugin, Runnable {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), text)
                
                if (conf.consoleCommandFeedbackEnabled) {
                    val response = formatMessage(conf.consoleCommandFeedback, mapOf(
                        "%command%" to text,
                        "%user%" to username
                    ))
                    
                    sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                }
            } catch (e: Exception) {
                val errorMsg = formatMessage(conf.consoleCommandError, mapOf(
                    "%command%" to text,
                    "%error%" to (e.message ?: "Unknown error")
                ))
                
                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
            }
        })
    }
    
    private fun handleWhitelistCommand(text: String, username: String) {
        val parts = text.split(" ")
        
        if (parts.size < 2) {
            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /whitelist [add|remove|on|off|list] [player]", conf.consoleAutoDeleteSeconds)
            return
        }
        
        val subCommand = parts[1].lowercase()
        
        when (subCommand) {
            "add" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан игрок. Используйте /whitelist add [player]", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val playerName = parts[2]
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Добавляем игрока в whitelist.json Minecraft
                        val whitelistCommand = "whitelist add $playerName"
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), whitelistCommand)
                        
                        // Отправляем сообщение об успехе
                        val response = conf.whitelistAddSuccess.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.whitelistAddError.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "remove" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан игрок. Используйте /whitelist remove [player]", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val playerName = parts[2]
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Удаляем игрока из whitelist.json Minecraft
                        val whitelistCommand = "whitelist remove $playerName"
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), whitelistCommand)
                        
                        // Отправляем сообщение об успехе
                        val response = conf.whitelistRemoveSuccess.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.whitelistRemoveError.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "on" -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Включаем белый список
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist on")
                        
                        // Отправляем сообщение об успехе
                        sendAutoDeleteMessage(conf.consoleChannelId, conf.whitelistOn, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        sendAutoDeleteMessage(conf.consoleChannelId, "❌ Ошибка при включении белого списка.", conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "off" -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Выключаем белый список
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "whitelist off")
                        
                        // Отправляем сообщение об успехе
                        sendAutoDeleteMessage(conf.consoleChannelId, conf.whitelistOff, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        sendAutoDeleteMessage(conf.consoleChannelId, "❌ Ошибка при отключении белого списка.", conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "list" -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Получаем список игроков из белого списка
                        val whitelist = Bukkit.getWhitelistedPlayers()
                        
                        if (whitelist.isEmpty()) {
                            // Если белый список пуст
                            sendAutoDeleteMessage(conf.consoleChannelId, conf.whitelistListEmpty, conf.consoleAutoDeleteSeconds)
                        } else {
                            // Формируем сообщение со списком игроков
                            val sb = StringBuilder(conf.whitelistListHeader)
                            sb.append("\n")
                            
                            for (player in whitelist) {
                                sb.append(conf.whitelistListEntry.replace("%player%", player.name ?: "Unknown"))
                                sb.append("\n")
                            }
                            
                            sendAutoDeleteMessage(conf.consoleChannelId, sb.toString(), conf.consoleAutoDeleteSeconds)
                        }
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        sendAutoDeleteMessage(conf.consoleChannelId, "❌ Ошибка при получении списка игроков.", conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            else -> {
                sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неизвестная подкоманда. Доступные команды: /whitelist [add|remove|on|off|list] [player]", conf.consoleAutoDeleteSeconds)
            }
        }
    }
    
    private fun handlePluginCommand(text: String, username: String) {
        val parts = text.split(" ")
        
        if (parts.size < 2) {
            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram [subcommand]", conf.consoleAutoDeleteSeconds)
            return
        }
        
        val subCommand = parts[1].lowercase()
        
        when (subCommand) {
            "addchannel" -> {
                if (parts.size < 4) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram addchannel <1|2|3> <channelId>", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val channelNumber = parts[2].toIntOrNull()
                if (channelNumber == null || channelNumber < 1 || channelNumber > 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный номер канала. Используйте 1, 2 или 3.", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val channelId = parts[3]
                
                // Выполняем команду в главном потоке
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Обновляем ID канала в конфигурации
                        val config = plugin.config
                        when (channelNumber) {
                            1 -> {
                                config.set("channels.main", channelId)
                                conf.mainChannelId = channelId
                            }
                            2 -> {
                                config.set("channels.console", channelId)
                                conf.consoleChannelId = channelId
                            }
                            3 -> {
                                config.set("channels.register", channelId)
                                conf.registerChannelId = channelId
                            }
                        }
                        
                        plugin.saveConfig()
                        
                        // Отправляем сообщение об успехе
                        val response = conf.pluginAddChannelSuccess
                            .replace("%channel_number%", channelNumber.toString())
                            .replace("%channel_id%", channelId)
                        
                        sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "addplayer" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram addplayer <player>", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val playerName = parts[2]
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Если игрок уже скрыт, отправляем ошибку
                        if (mgr.isPlayerHidden(playerName)) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Игрок $playerName уже скрыт в сообщениях Telegram.", conf.consoleAutoDeleteSeconds)
                            return@Runnable
                        }
                        
                        // Добавляем игрока в список скрытых
                        mgr.addHiddenPlayer(playerName)
                        
                        // Отправляем сообщение об успехе
                        val response = conf.pluginAddPlayerSuccess.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "removeplayer" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram removeplayer <player>", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val playerName = parts[2]
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Если игрок не скрыт, отправляем ошибку
                        if (!mgr.isPlayerHidden(playerName)) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Игрок $playerName не скрыт в сообщениях Telegram.", conf.consoleAutoDeleteSeconds)
                            return@Runnable
                        }
                        
                        // Удаляем игрока из списка скрытых
                        mgr.removeHiddenPlayer(playerName)
                        
                        // Отправляем сообщение об успехе
                        val response = conf.pluginRemovePlayerSuccess.replace("%player%", playerName)
                        sendAutoDeleteMessage(conf.consoleChannelId, response, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "reload" -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Перезагружаем конфигурацию плагина
                        plugin.reloadConfig()
                        conf.reload()
                        
                        // Отправляем сообщение об успехе
                        sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginReloadSuccess, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "unregister" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram unregister <player>", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val playerName = parts[2]
                
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Проверяем, зарегистрирован ли игрок
                        if (!mgr.isPlayerRegistered(playerName)) {
                            sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginUnregisterNotRegistered.replace("%player%", playerName), conf.consoleAutoDeleteSeconds)
                            return@Runnable
                        }
                        
                        // Отменяем регистрацию игрока
                        mgr.unregisterPlayer(playerName)
                        
                        // Отправляем сообщение об успехе
                        sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginUnregisterSuccess.replace("%player%", playerName), conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "hidden" -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Получаем список скрытых игроков
                        val hiddenPlayers = mgr.getHiddenPlayers()
                        
                        if (hiddenPlayers.isEmpty()) {
                            // Если список пуст
                            sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginHiddenEmpty, conf.consoleAutoDeleteSeconds)
                        } else {
                            // Формируем сообщение со списком скрытых игроков
                            val sb = StringBuilder(conf.pluginHiddenHeader)
                            sb.append("\n")
                            
                            for (player in hiddenPlayers) {
                                sb.append("  • $player")
                                sb.append("\n")
                            }
                            
                            sendAutoDeleteMessage(conf.consoleChannelId, sb.toString(), conf.consoleAutoDeleteSeconds)
                        }
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
            
            "whitelist" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram whitelist <add|remove|list|on|off> [player]", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val whitelistCommand = parts[2].lowercase()
                
                when (whitelistCommand) {
                    "add" -> {
                        if (parts.size < 4) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан ID пользователя. Используйте /telegram whitelist add <userId>", conf.consoleAutoDeleteSeconds)
                            return
                        }
                        
                        val userId = parts[3]
                        
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Добавляем пользователя в белый список
                                if (mgr.isPlayerWhitelisted(userId)) {
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistAddAlready.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                                    return@Runnable
                                }
                                
                                mgr.addPlayerToWhitelist(userId)
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistAddSuccess.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "remove" -> {
                        if (parts.size < 4) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан ID пользователя. Используйте /telegram whitelist remove <userId>", conf.consoleAutoDeleteSeconds)
                            return
                        }
                        
                        val userId = parts[3]
                        
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Удаляем пользователя из белого списка
                                if (!mgr.isPlayerWhitelisted(userId)) {
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistRemoveNotFound.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                                    return@Runnable
                                }
                                
                                mgr.removePlayerFromWhitelist(userId)
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistRemoveSuccess.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "list" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Получаем список игроков из белого списка
                                val whitelistedPlayers = mgr.getWhitelistedPlayers()
                                
                                if (whitelistedPlayers.isEmpty()) {
                                    // Если белый список пуст
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistListEmpty, conf.consoleAutoDeleteSeconds)
                                } else {
                                    // Формируем сообщение со списком пользователей
                                    val sb = StringBuilder(conf.pluginWhitelistListHeader)
                                    sb.append("\n")
                                    
                                    for ((userId, playerName) in whitelistedPlayers) {
                                        val displayName = if (playerName.isNotEmpty()) "$userId ($playerName)" else userId
                                        sb.append("  • $displayName")
                                        sb.append("\n")
                                    }
                                    
                                    sendAutoDeleteMessage(conf.consoleChannelId, sb.toString(), conf.consoleAutoDeleteSeconds)
                                }
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "on" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Включаем белый список телеграм
                                val config = plugin.config
                                config.set("main-channel.whitelist.enabled", true)
                                plugin.saveConfig()
                                conf.whitelistEnabled = true
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistOnSuccess, conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "off" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Выключаем белый список телеграм
                                val config = plugin.config
                                config.set("main-channel.whitelist.enabled", false)
                                plugin.saveConfig()
                                conf.whitelistEnabled = false
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginWhitelistOffSuccess, conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    else -> {
                        sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неизвестная подкоманда. Доступные команды: /telegram whitelist [add|remove|list|on|off] [player]", conf.consoleAutoDeleteSeconds)
                    }
                }
            }
            
            "blacklist" -> {
                if (parts.size < 3) {
                    sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неверный формат команды. Используйте /telegram blacklist <add|remove|list|on|off> [player]", conf.consoleAutoDeleteSeconds)
                    return
                }
                
                val blacklistCommand = parts[2].lowercase()
                
                when (blacklistCommand) {
                    "add" -> {
                        if (parts.size < 4) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан ID пользователя. Используйте /telegram blacklist add <userId>", conf.consoleAutoDeleteSeconds)
                            return
                        }
                        
                        val userId = parts[3]
                        
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Добавляем пользователя в черный список
                                if (mgr.isPlayerBlacklisted(userId)) {
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistAddAlready.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                                    return@Runnable
                                }
                                
                                mgr.addPlayerToBlacklist(userId)
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistAddSuccess.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "remove" -> {
                        if (parts.size < 4) {
                            sendAutoDeleteMessage(conf.consoleChannelId, "❌ Не указан ID пользователя. Используйте /telegram blacklist remove <userId>", conf.consoleAutoDeleteSeconds)
                            return
                        }
                        
                        val userId = parts[3]
                        
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Удаляем пользователя из черного списка
                                if (!mgr.isPlayerBlacklisted(userId)) {
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistRemoveNotFound.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                                    return@Runnable
                                }
                                
                                mgr.removePlayerFromBlacklist(userId)
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistRemoveSuccess.replace("%user_id%", userId), conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "list" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Получаем список игроков из черного списка
                                val blacklistedPlayers = mgr.getBlacklistedPlayers()
                                
                                if (blacklistedPlayers.isEmpty()) {
                                    // Если черный список пуст
                                    sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistListEmpty, conf.consoleAutoDeleteSeconds)
                                } else {
                                    // Формируем сообщение со списком пользователей
                                    val sb = StringBuilder(conf.pluginBlacklistListHeader)
                                    sb.append("\n")
                                    
                                    for ((userId, playerName) in blacklistedPlayers) {
                                        val displayName = if (playerName.isNotEmpty()) "$userId ($playerName)" else userId
                                        sb.append("  • $displayName")
                                        sb.append("\n")
                                    }
                                    
                                    sendAutoDeleteMessage(conf.consoleChannelId, sb.toString(), conf.consoleAutoDeleteSeconds)
                                }
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "on" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Включаем черный список телеграм
                                val config = plugin.config
                                config.set("main-channel.blacklist.enabled", true)
                                plugin.saveConfig()
                                conf.blacklistEnabled = true
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistOnSuccess, conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    "off" -> {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            try {
                                // Выключаем черный список телеграм
                                val config = plugin.config
                                config.set("main-channel.blacklist.enabled", false)
                                plugin.saveConfig()
                                conf.blacklistEnabled = false
                                
                                // Отправляем сообщение об успехе
                                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginBlacklistOffSuccess, conf.consoleAutoDeleteSeconds)
                            } catch (e: Exception) {
                                // Отправляем сообщение об ошибке
                                val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                                sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                            }
                        })
                    }
                    
                    else -> {
                        sendAutoDeleteMessage(conf.consoleChannelId, "❌ Неизвестная подкоманда. Доступные команды: /telegram blacklist [add|remove|list|on|off] [player]", conf.consoleAutoDeleteSeconds)
                    }
                }
            }
            
            "help" -> {
                // Отправляем список доступных команд для консольного канала
                sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginHelpMessage, conf.consoleAutoDeleteSeconds)
            }
            
            else -> {
                // Для неизвестных команд выполняем как обычную команду
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), text)
                        
                        // Отправляем сообщение об информации о плагине
                        sendAutoDeleteMessage(conf.consoleChannelId, conf.pluginTelegramInfo, conf.consoleAutoDeleteSeconds)
                    } catch (e: Exception) {
                        // Отправляем сообщение об ошибке
                        val errorMsg = conf.pluginCommandError.replace("%error%", e.message ?: "Unknown error")
                        sendAutoDeleteMessage(conf.consoleChannelId, errorMsg, conf.consoleAutoDeleteSeconds)
                    }
                })
            }
        }
    }
    
    private fun handleRegisterChannelMessage(message: Message, user: User) {
        val messageText = message.text ?: return

        // Сохраняем ID регистрационного канала для отправки ответных сообщений
        val chatId = message.chatId.toString()
        
        // Используем originalText для сохранения оригинального написания ника
        val originalText = messageText.trim()
        
        // Проверяем, является ли сообщение кодом регистрации
        if (originalText.length == conf.linkCodeLength && originalText.matches(Regex("^[a-zA-Z0-9]+$"))) {
            // Обрабатываем как код регистрации
            plugin.logger.info("Processing registration code: $originalText from user: ${user.id}")
            
            // Проверяем, что пользователь не зарегистрирован
            val existingPlayer = mgr.getPlayerByTelegramId(user.id.toString())
            if (existingPlayer != null) {
                sendMessage(
                    chatId,
                    conf.messages.commands.alreadyRegistered
                        .replace("%player%", existingPlayer)
                )
                return
            }
            
            // Валидируем код регистрации
            val validationResult = mgr.validateRegistrationCode(originalText, user.id.toString())
            if (validationResult) {
                // Получаем имя игрока после успешной регистрации
                val playerName = mgr.getPlayerByTelegramId(user.id.toString())
                if (playerName != null) {
                    plugin.logger.info("Registration code validated successfully for user: ${user.id}, player: $playerName")
                    
                    // Отправляем сообщение об успешной регистрации в Telegram
                    sendMessage(
                        chatId,
                        conf.registerCodeSuccess.replace("%player%", playerName)
                    )
                    
                    // Отправляем сообщение об успешной регистрации игроку в игре
                    val player = Bukkit.getPlayerExact(playerName)
                    if (player != null) {
                        sendComponentToPlayer(player, conf.registerSuccessInGame)
                    }
                    
                    // Выполняем команды награды
                    executeRewardCommands(playerName)
                } else {
                    // Если произошла ошибка и игрок не сохранился в БД
                    plugin.logger.warning("Player not found after successful registration code validation")
                    sendMessage(chatId, conf.messages.commands.linkSuccess)
                }
            } else {
                plugin.logger.info("Invalid registration code from user: ${user.id}")
                sendMessage(chatId, conf.messages.commands.linkInvalid)
            }
        } else {
            // Обрабатываем как имя игрока
            plugin.logger.info("Processing username registration: $originalText from user: ${user.id}")
            
            // Проверяем, что пользователь не зарегистрирован
            val existingPlayer = mgr.getPlayerByTelegramId(user.id.toString())
            if (existingPlayer != null) {
                sendMessage(
                    chatId,
                    conf.messages.commands.alreadyRegistered
                        .replace("%player%", existingPlayer)
                )
                return
            }
            
            // Регистрируем игрока
            val registrationResult = mgr.registerPlayer(originalText, user.id.toString())
            if (registrationResult) {
                plugin.logger.info("Registered player $originalText with telegramId: ${user.id}")
                
                // Отправляем сообщение об успешной регистрации в Telegram
                sendMessage(
                    chatId,
                    conf.registerSuccess.replace("%player%", originalText)
                )
                
                // Отправляем сообщение об успешной регистрации игроку в игре
                val player = Bukkit.getPlayerExact(originalText)
                if (player != null) {
                    sendComponentToPlayer(player, conf.registerSuccessInGame)
                }
                
                // Выполняем команды награды
                executeRewardCommands(originalText)
            } else {
                plugin.logger.warning("Failed to register player $originalText with telegramId: ${user.id}")
                sendMessage(chatId, conf.messages.commands.linkPlayerAlreadyRegistered)
            }
        }
    }
    
    // Выполняет команды награды за регистрацию
    private fun executeRewardCommands(playerName: String) {
        if (conf.registerRewardCommands.isEmpty()) {
            plugin.logger.warning("No reward commands configured for registration.")
            return
        }
        
        plugin.logger.info("Executing reward commands for player: $playerName")
        plugin.logger.info("Commands to execute: ${conf.registerRewardCommands}")
        
        val server = Bukkit.getServer()
        for (command in conf.registerRewardCommands) {
            try {
                // Заменяем плейсхолдер игрока
                val parsedCommand = command.replace("%player%", playerName)
                
                plugin.logger.info("Executing reward command: $parsedCommand")
                
                // Отправляем команду в основной поток для выполнения
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    try {
                        // Выполняем команду от имени консоли
                        server.dispatchCommand(server.consoleSender, parsedCommand)
                        plugin.logger.info("Successfully executed reward command: $parsedCommand")
                    } catch (e: Exception) {
                        plugin.logger.severe("Error executing reward command in main thread: ${e.message}")
                        e.printStackTrace()
                    }
                })
            } catch (e: Exception) {
                plugin.logger.severe("Error preparing reward command for $playerName: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    // Отправляет отформатированное сообщение игроку
    private fun sendComponentToPlayer(player: org.bukkit.entity.Player, message: String) {
        if (message.contains("<") && message.contains(">")) {
            // Если есть MiniMessage теги (градиенты и др.)
            val component = GradientUtils.parseMixedFormat(message)
            player.sendMessage(component)
        } else {
            // Для обычных цветовых кодов
            player.sendMessage(ColorUtils.translateColorCodes(message))
        }
    }
    
    fun sendServerStartMessage() {
        if (!conf.mainChannelEnabled || !conf.serverStartEnabled) return
        
        sendToMainChannel(conf.serverStartMessage.replace("\\n", "\n"))
    }
    
    fun sendServerStopMessage() {
        if (!conf.mainChannelEnabled || !conf.serverStopEnabled) return
        
        sendToMainChannel(conf.serverStopMessage.replace("\\n", "\n"))
    }
    
    fun sendPlayerJoinMessage(playerName: String) {
        if (!conf.mainChannelEnabled || !conf.playerJoinEnabled || mgr.isPlayerHidden(playerName)) return
        
        val message = formatMessage(conf.playerJoinMessage, mapOf(
            "%player%" to playerName
        ))
        sendToMainChannel(message)
    }
    
    fun sendPlayerQuitMessage(playerName: String) {
        if (!conf.mainChannelEnabled || !conf.playerQuitEnabled || mgr.isPlayerHidden(playerName)) return
        
        val message = formatMessage(conf.playerQuitMessage, mapOf(
            "%player%" to playerName
        ))
        sendToMainChannel(message)
    }
    
    fun sendPlayerDeathMessage(playerName: String, deathMessage: String) {
        if (!conf.mainChannelEnabled || !conf.playerDeathEnabled || mgr.isPlayerHidden(playerName)) return
        
        // Заменяем имя игрока в сообщении о смерти на плейсхолдер
        // Это необходимо для корректного форматирования, так как имя игрока 
        // может встречаться в сообщении о смерти в разных падежах
        var processedDeathMessage = deathMessage
        
        // Пробуем убрать имя игрока из сообщения о смерти, если оно там есть
        if (processedDeathMessage.contains(playerName)) {
            processedDeathMessage = processedDeathMessage.replace(playerName, "")
        }
        
        // Если сообщение начинается с лишних символов (часто остаются после удаления имени)
        processedDeathMessage = processedDeathMessage.trimStart(' ', '.', ',', ':')
        
        val message = formatMessage(conf.playerDeathMessage, mapOf(
            "%player%" to playerName,
            "%death_message%" to processedDeathMessage
        ))
        
        sendToMainChannel(message)
    }
    
    fun sendPlayerChatMessage(playerName: String, chatMessage: String) {
        if (!conf.mainChannelEnabled || !conf.playerChatEnabled || mgr.isPlayerHidden(playerName)) return
        
        val message = formatMessage(conf.formatMinecraftToTelegram, mapOf(
            "%player%" to playerName,
            "%message%" to chatMessage
        ))
        
        sendToMainChannel(message)
    }
    
    fun sendPlayerCommandMessage(playerName: String, command: String) {
        if (!conf.consoleChannelEnabled || !conf.playerCommandLogEnabled || mgr.isPlayerHidden(playerName)) return
        
        // Используем часовой пояс МСК (+3) для времени
        val now = LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow"))
        val timestamp = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        
        val message = formatMessage(conf.playerCommandLogFormat, mapOf(
            "%time%" to timestamp,
            "%player%" to playerName,
            "%command%" to command
        ))
        
        sendToConsoleChannel(message)
    }
    
    fun sendToMainChannel(message: String) {
        sendMessage(conf.mainChannelId, message)
    }
    
    fun sendToConsoleChannel(message: String) {
        sendAutoDeleteMessage(conf.consoleChannelId, message, conf.consoleAutoDeleteSeconds)
    }
    
    fun sendToRegisterChannel(message: String) {
        sendMessage(conf.registerChannelId, message)
    }
    
    private fun sendMessage(chatId: String, message: String) {
        if (chatId.isEmpty() || message.isEmpty()) return
        
        try {
            val sendMessage = SendMessage(chatId, convertToHtml(message))
            sendMessage.parseMode = "HTML"
            execute(sendMessage)
        } catch (e: TelegramApiException) {
            plugin.logger.warning("Failed to send message to Telegram: ${e.message}")
            plugin.scheduleReconnect()
        }
    }
    
    private fun convertToHtml(text: String): String {
        // Заменяем \n на настоящие переносы строк
        var processedText = text.replace("\\n", "\n")
        
        // Сохраняем кавычки для моноширинного шрифта
        val codeBlocks = mutableMapOf<String, String>()
        var codeCounter = 0
        
        // Сохраняем одиночные обратные кавычки для последующей обработки
        processedText = processedText.replace(Regex("`([^`]+)`")) { match ->
            val placeholder = "CODE_BLOCK_${codeCounter++}"
            codeBlocks[placeholder] = match.groupValues[1]
            placeholder
        }
        
        // Обрабатываем Markdown разметку и запоминаем отформатированные участки
        val formattedParts = mutableMapOf<String, String>()
        var counter = 0
        
        // Жирный текст - разные варианты
        processedText = processedText.replace(Regex("\\*\\*(.*?)\\*\\*|<b>(.*?)</b>|<strong>(.*?)</strong>")) { match -> 
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<b>$content</b>"
            placeholder
        }
        
        // Курсив - разные варианты
        processedText = processedText.replace(Regex("\\*(.*?)\\*|<i>(.*?)</i>|<em>(.*?)</em>")) { match -> 
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<i>$content</i>"
            placeholder
        }
        
        // Моноширинный шрифт (код) - другие варианты
        processedText = processedText.replace(Regex("<code>(.*?)</code>")) { match -> 
            val content = match.groupValues[1]
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<code>$content</code>"
            placeholder
        }
        
        // Зачеркнутый текст - разные варианты
        processedText = processedText.replace(Regex("~~(.*?)~~|<s>(.*?)</s>|<strike>(.*?)</strike>|<del>(.*?)</del>")) { match -> 
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<s>$content</s>"
            placeholder
        }
        
        // Подчеркнутый текст
        processedText = processedText.replace(Regex("<u>(.*?)</u>|__(.*?)__")) { match -> 
            val content = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: ""
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<u>$content</u>"
            placeholder
        }
        
        // Многострочный код с указанием языка
        processedText = processedText.replace(Regex("```([a-zA-Z0-9+]+)?\n(.*?)```", RegexOption.DOT_MATCHES_ALL)) { match -> 
            val language = match.groupValues[1].takeIf { it.isNotEmpty() } ?: ""
            val code = match.groupValues[2]
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            
            if (language.isNotEmpty()) {
                formattedParts[placeholder] = "<pre language=\"$language\">$code</pre>"
            } else {
                formattedParts[placeholder] = "<pre>$code</pre>"
            }
            
            placeholder
        }
        
        // Проверяем наличие тега <pre> с атрибутом language
        processedText = processedText.replace(Regex("<pre language=\"([a-zA-Z0-9+]+)\">(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)) { match -> 
            val language = match.groupValues[1]
            val code = match.groupValues[2]
            val placeholder = "FORMAT_PLACEHOLDER_${counter++}"
            formattedParts[placeholder] = "<pre language=\"$language\">$code</pre>"
            placeholder
        }
        
        // Если текст содержит градиенты или теги MiniMessage
        if (processedText.contains("<") && processedText.contains(">")) {
            try {
                // Обрабатываем MiniMessage форматирование
                val component = GradientUtils.parseMixedFormat(processedText)
                processedText = PlainTextComponentSerializer.plainText().serialize(component)
            } catch (e: Exception) {
                plugin.logger.warning("Error parsing MiniMessage format: ${e.message}")
            }
        }
        
        // Для обычных цветовых кодов
        try {
            val component = LegacyComponentSerializer.legacySection().deserialize(
                processedText.replace("&", "§")
            )
            processedText = PlainTextComponentSerializer.plainText().serialize(component)
        } catch (e: Exception) {
            // Если произошла ошибка, просто убираем цветовые коды
            processedText = ColorUtils.stripColorCodes(processedText)
        }
        
        // Восстанавливаем блоки кода
        for ((placeholder, content) in codeBlocks) {
            processedText = processedText.replace(placeholder, content)
        }
        
        // Экранируем специальные символы HTML
        processedText = processedText
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        
        // Восстанавливаем placeholders с форматированием
        for ((placeholder, htmlTag) in formattedParts) {
            processedText = processedText.replace(placeholder, htmlTag)
        }
        
        // Обрабатываем сохраненные блоки кода
        processedText = processedText.replace(Regex("CODE_BLOCK_(\\d+)")) { match ->
            val index = match.groupValues[1].toInt()
            val content = codeBlocks["CODE_BLOCK_$index"] ?: ""
            "<code>$content</code>"
        }
        
        return processedText
    }
    
    // Отправляем приватное сообщение пользователю
    fun sendPrivateMessage(userId: String, message: String) {
        try {
            val sendMessage = SendMessage()
            sendMessage.chatId = userId
            sendMessage.text = message
            sendMessage.enableHtml(true)
            
            execute(sendMessage)
        } catch (e: Exception) {
            plugin.logger.warning("Failed to send private message to $userId: ${e.message}")
        }
    }
    
    // Отправляем сообщение, которое автоматически удалится через указанное время
    fun sendAutoDeleteMessage(chatId: String, message: String, deleteAfterSeconds: Int = 15) {
        if (chatId.isEmpty() || message.isEmpty()) return
        
        try {
            // Отправляем сообщение
            val sendMessage = SendMessage(chatId, convertToHtml(message))
            sendMessage.parseMode = "HTML"
            val sentMessage = execute(sendMessage)
            
            // Планируем удаление сообщения
            if (deleteAfterSeconds > 0) {
                val messageId = sentMessage.messageId
                
                // Запланировать удаление сообщения через указанное количество секунд
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, Runnable {
                    try {
                        val deleteMessage = DeleteMessage(chatId, messageId)
                        execute(deleteMessage)
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to delete message: ${e.message}")
                    }
                }, deleteAfterSeconds * 20L) // Преобразуем секунды в тики (20 тиков = 1 секунда)
            }
        } catch (e: TelegramApiException) {
            plugin.logger.warning("Failed to send auto-delete message to Telegram: ${e.message}")
        }
    }
} 