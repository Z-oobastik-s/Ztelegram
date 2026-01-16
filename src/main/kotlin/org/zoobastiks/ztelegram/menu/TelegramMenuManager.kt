package org.zoobastiks.ztelegram.menu

import org.bukkit.Bukkit
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.bot.TBot
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.utils.PlaceholderEngine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Менеджер для управления интерактивным Telegram меню
 */
class TelegramMenuManager(
    private val bot: TBot,
    private val plugin: ZTele
) {
    private val conf: TConf
        get() = ZTele.conf
    
    // Rate limiting для защиты от спама
    private val clickCounts = ConcurrentHashMap<Long, MutableList<Long>>()
    private val blockedUsers = ConcurrentHashMap<Long, Long>()
    
    // Авто-закрытие меню
    private val autoCloseTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val executor = Executors.newScheduledThreadPool(2)
    
    /**
     * Перезагружает меню (обновляет конфигурацию)
     */
    fun reload() {
        // Очищаем все активные задачи авто-закрытия
        autoCloseTasks.values.forEach { it.cancel(false) }
        autoCloseTasks.clear()
        clickCounts.clear()
        blockedUsers.clear()
    }
    
    /**
     * Открывает главное меню для пользователя
     */
    fun openMainMenu(chatId: String, userId: Long, username: String) {
        val isAdmin = conf.isAdministrator(userId)
        val menuTextRaw = conf.menuMainText ?: "📱 **ГЛАВНОЕ МЕНЮ**\n\nПривет, %user%! Выберите действие:"
        val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf(
            "user" to username
        )))
        
        val screen = MainMenuScreen(menuText, userId, isAdmin)
        val sentMessage = bot.sendMenuMessage(chatId, screen.text, screen.keyboard)
        scheduleMenuAutoClose(chatId, sentMessage?.messageId ?: 0)
    }
    
    /**
     * Обрабатывает callback от нажатия кнопки
     */
    fun handleCallback(callbackQuery: CallbackQuery): Boolean {
        val data = callbackQuery.data ?: return false
        val userId = callbackQuery.from.id
        val username = callbackQuery.from.userName ?: callbackQuery.from.firstName
        val chatId = callbackQuery.message.chatId.toString()
        val messageId = callbackQuery.message.messageId
        val callbackQueryId = callbackQuery.id
        
        // Проверяем rate limit (защита от спама)
        if (!checkRateLimit(userId)) {
            val blockTime = blockedUsers[userId] ?: 0
            val remainingSeconds = ((blockTime - System.currentTimeMillis()) / 1000).toInt()
            if (remainingSeconds > 0) {
                bot.answerCallbackQuery(callbackQueryId, "⏳ Слишком много нажатий! Подождите ${remainingSeconds}с", showAlert = true)
                return true
            } else {
                // Разблокируем, если время истекло
                blockedUsers.remove(userId)
            }
        }
        
        // Парсим callback_data для проверки владельца
        val (action, ownerId) = CallbackData.parseCallbackData(data)
        
        if (conf.debugEnabled) {
            plugin.logger.info("🔍 [MenuCallback] data: $data, parsed action: $action, ownerId: $ownerId, userId: $userId")
        }
        
        // Проверяем, что меню принадлежит этому пользователю
        if (ownerId != null && ownerId != userId) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return true
        }
        
        // Проверяем права доступа (белый/черный список)
        // Исключение: для меню регистрации не проверяем whitelist, так как оно предназначено для незарегистрированных игроков
        val isRegisterCallback = action.startsWith("register:")
        
        if (conf.blacklistEnabled && ZTele.mgr.isPlayerBlacklisted(userId.toString())) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorBlocked, showAlert = true)
            return true
        }
        
        // Пропускаем проверку whitelist для меню регистрации
        if (!isRegisterCallback && conf.whitelistEnabled && !ZTele.mgr.isPlayerWhitelisted(userId.toString())) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotRegistered, showAlert = true)
            return true
        }
        
        try {
            // Обработка callback'ов для регистрации (перенаправляем в RegisterMenuManager)
            if (isRegisterCallback) {
                return handleRegisterCallback(action, chatId, messageId, userId, username, callbackQueryId)
            }
            
            // Обработка главного меню
            if (action == CallbackData.MAIN_MENU) {
                showMainMenu(chatId, messageId, userId, username)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            // Обработка callback'ов для настроек
            when (action) {
                CallbackData.SETTINGS_MENU -> {
                    showSettingsMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_RESTART -> {
                    showSettingsRestartMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_PLAYERS -> {
                    showSettingsPlayersMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_WEATHER -> {
                    showSettingsWeatherMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_TIME -> {
                    showSettingsTimeMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_SERVER -> {
                    showSettingsServerMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                // Рестарт в настройках
                CallbackData.SETTINGS_RESTART_NOW -> {
                    handleSettingsRestartNow(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_RESTART_5MIN -> {
                    handleSettingsRestart5Min(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_RESTART_CANCEL -> {
                    handleSettingsRestartCancel(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                // Погода
                CallbackData.SETTINGS_WEATHER_CLEAR -> {
                    handleSettingsWeatherClear(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_WEATHER_RAIN -> {
                    handleSettingsWeatherRain(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_WEATHER_THUNDER -> {
                    handleSettingsWeatherThunder(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                // Время
                CallbackData.SETTINGS_TIME_DAY -> {
                    handleSettingsTimeDay(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_TIME_NIGHT -> {
                    handleSettingsTimeNight(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_TIME_NOON -> {
                    handleSettingsTimeNoon(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_TIME_MIDNIGHT -> {
                    handleSettingsTimeMidnight(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                // Сервер
                CallbackData.SETTINGS_SERVER_RELOAD -> {
                    handleSettingsServerReload(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                CallbackData.SETTINGS_SERVER_STOP -> {
                    handleSettingsServerStop(chatId, messageId, userId, username, callbackQueryId)
                    return true
                }
                
                // Переводы денег
                CallbackData.PAYMENT_MENU -> {
                    showPaymentMenu(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.PAYMENT_HISTORY -> {
                    showPaymentHistory(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.PAYMENT_TRANSFER -> {
                    showPaymentTransferSelectPlayer(chatId, messageId, userId, username)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            // Обработка выбора игрока для перевода
            if (action.startsWith("${CallbackData.PAYMENT_TRANSFER_SELECT}:")) {
                val targetPlayerName = action.removePrefix("${CallbackData.PAYMENT_TRANSFER_SELECT}:")
                if (targetPlayerName.isNotEmpty()) {
                    showPaymentTransferSelectAmount(chatId, messageId, userId, username, targetPlayerName)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            // Обработка выбора суммы перевода
            if (action.startsWith("${CallbackData.PAYMENT_TRANSFER_AMOUNT}:")) {
                val parts = action.removePrefix("${CallbackData.PAYMENT_TRANSFER_AMOUNT}:").split(":")
                if (parts.size >= 2) {
                    val targetPlayerName = parts[0]
                    val amountStr = parts[1]
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        showPaymentTransferConfirm(chatId, messageId, userId, username, targetPlayerName, amount)
                        bot.answerCallbackQuery(callbackQueryId)
                        return true
                    }
                }
            }
            
            // Обработка подтверждения перевода
            if (action.startsWith("${CallbackData.PAYMENT_TRANSFER_CONFIRM}:")) {
                val parts = action.removePrefix("${CallbackData.PAYMENT_TRANSFER_CONFIRM}:").split(":")
                if (parts.size >= 2) {
                    val targetPlayerName = parts[0]
                    val amountStr = parts[1]
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        executePaymentTransfer(chatId, messageId, userId, username, callbackQueryId, targetPlayerName, amount)
                        return true
                    }
                }
            }
            
            // Обработка пагинации списка наград рулетки
            if (action.startsWith("${CallbackData.RANDOM_REWARDS_PAGE}:")) {
                val pageStr = action.removePrefix("${CallbackData.RANDOM_REWARDS_PAGE}:")
                val page = pageStr.toIntOrNull() ?: 0
                showRandomRewardsPage(chatId, messageId, userId, page)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            // Управление игроками - выбор игрока и действия (обрабатываем после when, так как они содержат дополнительные параметры)
            // Формат: "menu:settings:player:select:PlayerName:userId"
            if (action.startsWith("${CallbackData.SETTINGS_PLAYER_SELECT}:")) {
                val playerName = action.removePrefix("${CallbackData.SETTINGS_PLAYER_SELECT}:")
                if (playerName.isNotEmpty()) {
                    showSettingsPlayerActionsMenu(chatId, messageId, userId, username, callbackQueryId, playerName)
                    return true
                }
            }
            // Формат: "menu:settings:player:kick:PlayerName:userId"
            else if (action.startsWith("${CallbackData.SETTINGS_PLAYER_KICK}:")) {
                val playerName = action.removePrefix("${CallbackData.SETTINGS_PLAYER_KICK}:")
                if (playerName.isNotEmpty()) {
                    handleSettingsPlayerKick(chatId, messageId, userId, username, callbackQueryId, playerName)
                    return true
                }
            }
            // Формат: "menu:settings:player:ban10min:PlayerName:userId"
            else if (action.startsWith("${CallbackData.SETTINGS_PLAYER_BAN_10MIN}:")) {
                val playerName = action.removePrefix("${CallbackData.SETTINGS_PLAYER_BAN_10MIN}:")
                if (playerName.isNotEmpty()) {
                    handleSettingsPlayerBan10Min(chatId, messageId, userId, username, callbackQueryId, playerName)
                    return true
                }
            }
            // Формат: "menu:settings:player:kill:PlayerName:userId"
            else if (action.startsWith("${CallbackData.SETTINGS_PLAYER_KILL}:")) {
                val playerName = action.removePrefix("${CallbackData.SETTINGS_PLAYER_KILL}:")
                if (playerName.isNotEmpty()) {
                    handleSettingsPlayerKill(chatId, messageId, userId, username, callbackQueryId, playerName)
                    return true
                }
            }
            
            // Обработка остальных callback'ов меню
            when (action) {
                CallbackData.CLOSE -> {
                    bot.deleteMessage(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId, "Меню закрыто")
                    cancelMenuAutoClose(chatId, messageId)
                    return true
                }
                
                CallbackData.RANDOM_MENU -> {
                    // Показываем меню рулетки
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val canStart = ZTele.randomManager.canUseRandom(userId)
                        val cooldownTime = if (!canStart) ZTele.randomManager.getRemainingTime(userId) else null
                        val menuTextRaw = conf.menuRandomText ?: "🎲 **РУЛЕТКА**\n\nПривет, %user%!\n\nЗапустите рулетку и выиграйте случайную награду!"
                        val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf("user" to username)))
                        val screen = RandomMenuScreen(menuText, canStart, cooldownTime, userId)
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.ONLINE -> {
                    if (!conf.enabledOnlineCommand) {
                        bot.answerCallbackQuery(callbackQueryId, "Команда отключена", showAlert = false)
                        return true
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val onlinePlayers = Bukkit.getOnlinePlayers()
                            .filter { !ZTele.mgr.isPlayerHidden(it.name) }
                        val response = if (onlinePlayers.isEmpty()) {
                            conf.onlineCommandNoPlayers
                        } else {
                            PlaceholderEngine.process(conf.onlineCommandResponse)
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад в меню"
                                callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, response, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.TPS -> {
                    if (!conf.enabledTpsCommand) {
                        bot.answerCallbackQuery(callbackQueryId, "Команда отключена", showAlert = false)
                        return true
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val response = PlaceholderEngine.process(conf.tpsCommandMessage)
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад в меню"
                                callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, response, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.RANDOM_START -> {
                    if (!conf.enabledRandomCommand) {
                        bot.answerCallbackQuery(callbackQueryId, "Рулетка отключена", showAlert = false)
                        return true
                    }
                    if (!ZTele.randomManager.canUseRandom(userId)) {
                        val remainingTime = ZTele.randomManager.getRemainingTime(userId)
                        bot.answerCallbackQuery(callbackQueryId, "⏳ Кулдаун: $remainingTime", showAlert = true)
                        return true
                    }
                    // Отвечаем на callback query сразу
                    bot.answerCallbackQuery(callbackQueryId, "🎲 Запускаем рулетку...", showAlert = false)
                    // Запускаем рулетку
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        try {
                            val onlinePlayers = Bukkit.getOnlinePlayers()
                            if (onlinePlayers.isEmpty()) {
                                val errorMessage = conf.randomCommandNoPlayers
                                val keyboard = InlineKeyboardMarkup()
                                keyboard.keyboard = listOf(listOf(
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                        text = "🔙 Назад"
                                        callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                    }
                                ))
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                })
                                return@Runnable
                            }
                            
                            if (onlinePlayers.size == 1) {
                                val errorMessage = conf.randomCommandOnlyOnePlayer
                                val keyboard = InlineKeyboardMarkup()
                                keyboard.keyboard = listOf(listOf(
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                        text = "🔙 Назад"
                                        callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                    }
                                ))
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                })
                                return@Runnable
                            }
                            
                            val winner = ZTele.randomManager.selectRandomPlayer()
                            if (winner == null) {
                                val errorMessage = conf.randomCommandNoPlayers
                                val keyboard = InlineKeyboardMarkup()
                                keyboard.keyboard = listOf(listOf(
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                        text = "🔙 Назад"
                                        callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                    }
                                ))
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                })
                                return@Runnable
                            }
                            
                            val rewards = conf.randomCommandRewards
                            if (rewards.isEmpty()) {
                                val errorMessage = conf.randomCommandError
                                val keyboard = InlineKeyboardMarkup()
                                keyboard.keyboard = listOf(listOf(
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                        text = "🔙 Назад"
                                        callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                    }
                                ))
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                })
                                return@Runnable
                            }
                            
                            val rewardCommand = ZTele.randomManager.selectRandomReward(rewards)
                            if (rewardCommand == null) {
                                val errorMessage = conf.randomCommandError
                                val keyboard = InlineKeyboardMarkup()
                                keyboard.keyboard = listOf(listOf(
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                        text = "🔙 Назад"
                                        callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                    }
                                ))
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                })
                                return@Runnable
                            }
                            
                            // Устанавливаем кулдаун
                            ZTele.randomManager.setCooldown(userId)
                            
                            // Получаем описание награды
                            val rewardDescriptions = conf.randomCommandRewardDescriptions
                            val rewardIndex = rewards.indexOf(rewardCommand)
                            val rewardDescription = if (rewardIndex >= 0 && rewardIndex < rewardDescriptions.size) {
                                rewardDescriptions[rewardIndex]
                            } else {
                                rewardCommand // Используем саму команду как описание, если описания нет
                            }
                            
                            val now = java.time.LocalDateTime.now(java.time.ZoneId.of("Europe/Moscow"))
                            val timeStr = now.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
                            
                            val context = PlaceholderEngine.createCustomContext(mapOf(
                                "player" to winner,
                                "reward" to rewardDescription,
                                "server" to "Zoobastiks.20tps.name",
                                "time" to timeStr
                            ))
                            val telegramMessage = PlaceholderEngine.process(conf.randomCommandWinTelegram, context)
                            
                            // Выполняем команду награды и оповещения
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                try {
                                    val processedRewardCommand = rewardCommand.replace("%player%", winner)
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedRewardCommand)
                                    
                                    // Выполняем команду оповещения в игре
                                    val broadcastCommand = conf.randomCommandBroadcastCommand
                                    if (broadcastCommand.isNotEmpty()) {
                                        val processedBroadcast = broadcastCommand
                                            .replace("%player%", winner)
                                            .replace("%reward%", rewardDescription)
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedBroadcast)
                                    }
                                    
                                    val keyboard = InlineKeyboardMarkup()
                                    keyboard.keyboard = listOf(listOf(
                                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                            text = "🔙 Назад в меню"
                                            callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                                        }
                                    ))
                                    bot.editMenuMessage(chatId, messageId, telegramMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                } catch (e: Exception) {
                                    plugin.logger.severe("Ошибка при выполнении награды: ${e.message}")
                                    e.printStackTrace()
                                    val errorMessage = "❌ Ошибка при выполнении награды"
                                    val keyboard = InlineKeyboardMarkup()
                                    keyboard.keyboard = listOf(listOf(
                                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                            text = "🔙 Назад"
                                            callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                                        }
                                    ))
                                    bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                                    scheduleMenuAutoClose(chatId, messageId)
                                }
                            })
                        } catch (e: Exception) {
                            plugin.logger.severe("Ошибка при запуске рулетки: ${e.message}")
                            e.printStackTrace()
                            val errorMessage = "❌ Ошибка при запуске рулетки"
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                        }
                    ))
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                bot.editMenuMessage(chatId, messageId, errorMessage, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                            })
                        }
                    })
                    return true
                }
                
                CallbackData.RANDOM_REWARDS -> {
                    // Отвечаем на callback query сразу
                    bot.answerCallbackQuery(callbackQueryId)
                    showRandomRewardsPage(chatId, messageId, userId, 0)
                    return true
                }
                
                CallbackData.RANDOM_CHECK_COOLDOWN -> {
                    val canStart = ZTele.randomManager.canUseRandom(userId)
                    val cooldownTime = if (!canStart) ZTele.randomManager.getRemainingTime(userId) else null
                    val menuTextRaw = conf.menuRandomText ?: "🎲 **РУЛЕТКА**\n\nПривет, %user%!\n\nЗапустите рулетку и выиграйте случайную награду!"
                    val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf("user" to username)))
                    val screen = RandomMenuScreen(menuText, canStart, cooldownTime, userId)
                    bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.STATS_MENU -> {
                    val menuTextRaw = conf.menuStatsText ?: "📊 **СТАТИСТИКА**\n\nПривет, %user%!\n\nВыберите тип статистики:"
                    val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf("user" to username)))
                    val screen = StatsMenuScreen(menuText, userId)
                    bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.STATS_TODAY -> {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val stats = ZTele.stats.getStats(org.zoobastiks.ztelegram.stats.StatsManager.StatsPeriod.TODAY)
                        val message = buildString {
                            append("📈 **Статистика за сегодня:**\n\n")
                            append("👥 Уникальных игроков: **${stats.count}**\n")
                            if (stats.players.isNotEmpty()) {
                                append("\n📋 **Список игроков:**\n")
                                stats.players.take(20).forEach { playerName ->
                                    append("• $playerName\n")
                                }
                                if (stats.players.size > 20) {
                                    append("\n... и еще ${stats.players.size - 20} игроков")
                                }
                            }
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.STATS_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.STATS_TOP -> {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val top = ZTele.stats.getPlaytimeTop(org.zoobastiks.ztelegram.stats.StatsManager.StatsPeriod.TODAY, 10)
                        val message = buildString {
                            append("🏆 **Топ игроков за сегодня:**\n\n")
                            if (top.isEmpty()) {
                                append("📭 Нет данных")
                            } else {
                                top.forEachIndexed { index, entry ->
                                    val medal = when (index + 1) {
                                        1 -> "🥇"
                                        2 -> "🥈"
                                        3 -> "🥉"
                                        else -> "${index + 1}."
                                    }
                                    val playtime = ZTele.stats.formatPlaytime(entry.minutes)
                                    append("$medal **${entry.playerName}** - $playtime\n")
                                }
                            }
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.STATS_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.STATS_TOP_BAL -> {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val economy = ZTele.economy
                        if (economy == null) {
                            val message = "❌ Экономика недоступна"
                            val keyboard = createEmptyKeyboard()
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                bot.editMenuMessage(chatId, messageId, message, keyboard)
                            })
                            return@Runnable
                        }
                        val allPlayers = mutableListOf<Pair<String, Double>>()
                        for (offlinePlayer in Bukkit.getOfflinePlayers()) {
                            if (offlinePlayer.name != null) {
                                val balance = economy.getBalance(offlinePlayer)
                                if (balance > 0) {
                                    allPlayers.add(Pair(offlinePlayer.name!!, balance))
                                }
                            }
                        }
                        val top = allPlayers.sortedByDescending { it.second }.take(10)
                        val message = buildString {
                            append("💰 **Топ по балансу:**\n\n")
                            top.forEachIndexed { index, (playerName, balance) ->
                                append("${index + 1}. $playerName - ${String.format("%.2f", balance)} ${economy.currencyNamePlural()}\n")
                            }
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.STATS_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.PLAYER_MENU -> {
                    // Отвечаем на callback query сразу
                    bot.answerCallbackQuery(callbackQueryId)
                    
                    val message = "👤 **ВЫБОР ИГРОКА**\n\nВыберите тип списка игроков:"
                        val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(
                        listOf(
                                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "📋 Зарегистрированные игроки"
                                callbackData = CallbackData.PLAYER_LIST_REGISTERED.withUserId(userId)
                                }
                        ),
                        listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🟢 Онлайн игроки"
                                callbackData = CallbackData.PLAYER_LIST_ONLINE.withUserId(userId)
                            }
                        ),
                        listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад в меню"
                                callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                            }
                        )
                    )
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                    return true
                }
                
                CallbackData.PLAYER_LIST_REGISTERED -> {
                    bot.answerCallbackQuery(callbackQueryId)
                    showPlayerList(chatId, messageId, userId, showOnlyOnline = false)
                    return true
                }
                
                CallbackData.PLAYER_LIST_ONLINE -> {
                    bot.answerCallbackQuery(callbackQueryId)
                    showPlayerList(chatId, messageId, userId, showOnlyOnline = true)
                    return true
                }
                
                CallbackData.REP_MENU -> {
                    val menuTextRaw = conf.menuRepText ?: "⭐ **РЕПУТАЦИЯ**\n\nПривет, %user%!\n\nВыберите действие:"
                    val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf("user" to username)))
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🏆 Топ игроков"
                            callbackData = CallbackData.REP_TOP.withUserId(userId)
                        }),
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "📜 Последние изменения"
                            callbackData = CallbackData.REP_RECENT.withUserId(userId)
                        }),
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад в меню"
                            callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                        })
                    )
                    bot.editMenuMessage(chatId, messageId, menuText, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.REP_TOP -> {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val topPlayers = ZTele.reputation.getTopPlayers(10)
                        val message = buildString {
                            append("🏆 **Топ-10 игроков по репутации**\n\n")
                            topPlayers.forEachIndexed { index, (playerName, repData) ->
                                val position = index + 1
                                val medal = when (position) {
                                    1 -> "🥇"
                                    2 -> "🥈"
                                    3 -> "🥉"
                                    else -> "$position."
                                }
                                append("$medal **$playerName** - +${repData.positiveRep}/-${repData.negativeRep}\n")
                            }
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.REP_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.REP_RECENT -> {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val recentChanges = ZTele.reputation.getRecentChanges(10)
                        val message = if (recentChanges.isEmpty()) {
                            "📜 **Нет недавних изменений**\nПока никто не получил репутацию!"
                        } else {
                            buildString {
                                append("📜 **Последние изменения репутации**\n\n")
                                recentChanges.forEach { (targetPlayer, entry) ->
                                    val sign = if (entry.isPositive) "+" else "-"
                                    val emoji = if (entry.isPositive) "👍" else "👎"
                                    val reasonText = if (entry.reason != null) "\n   _\"${entry.reason}\"_" else ""
                                    append("$emoji **${entry.source}** → **$targetPlayer** ($sign)$reasonText\n")
                                }
                            }
                        }
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.REP_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.STAFF_LIST -> {
                    if (!conf.isAdministrator(userId)) {
                        bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
                        return true
                    }
                    // Показываем список администрации
                    val players = conf.staffListPlayers
                    val screen = StaffListMenuScreen(conf.staffListHeaderText, players, conf.staffListPlayerFormat, userId)
                    bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.INFO_MENU -> {
                    val menuTextRaw = conf.menuInfoText ?: "ℹ️ **ИНФОРМАЦИЯ**\n\nПривет, %user%!\n\nВыберите раздел:"
                    val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf("user" to username)))
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔗 Ссылки"
                            callbackData = CallbackData.INFO_LINKS.withUserId(userId)
                        }),
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🖥️ Сервер"
                            callbackData = CallbackData.INFO_SERVER.withUserId(userId)
                        }),
                        listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад в меню"
                            callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                        })
                    )
                    bot.editMenuMessage(chatId, messageId, menuText, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.INFO_LINKS -> {
                    val menuText = conf.menuInfoLinksText ?: "🔗 **ССЫЛКИ**\n\n📱 Telegram: https://t.me/ReZoobastik\n🖥️ IP сервера: Zoobastiks.20tps.name"
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                        text = "🔙 Назад"
                        callbackData = CallbackData.INFO_MENU.withUserId(userId)
                    }))
                    bot.editMenuMessage(chatId, messageId, menuText, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
                
                CallbackData.INFO_SERVER -> {
                    val menuText = conf.menuInfoServerText ?: "🖥️ **ИНФОРМАЦИЯ О СЕРВЕРЕ**"
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                        text = "🔙 Назад"
                        callbackData = CallbackData.INFO_MENU.withUserId(userId)
                    }))
                    bot.editMenuMessage(chatId, messageId, menuText, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            // Обработка callback'ов с параметрами (игроки, статистика, репутация и т.д.)
            if (action.startsWith("${CallbackData.PLAYER_SELECT}:")) {
                val playerName = action.removePrefix("${CallbackData.PLAYER_SELECT}:")
                if (playerName.isNotEmpty()) {
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        // Используем правильный регистр для поиска данных игрока
                        val playerData = ZTele.mgr.getPlayerData(playerName)
                        // Для проверки онлайн статуса используем оригинальное имя
                        val isOnline = Bukkit.getPlayerExact(playerName) != null
                        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
                        
                        // Проверяем, существует ли игрок в Minecraft, даже если не зарегистрирован (как в команде /player)
                        if (!offlinePlayer.hasPlayedBefore() && !isOnline) {
                            val context = PlaceholderEngine.createCustomContext(mapOf("player" to playerName))
                            val response = PlaceholderEngine.process(conf.playerCommandNoPlayer, context)
                            val keyboard = InlineKeyboardMarkup()
                            keyboard.keyboard = listOf(listOf(
                                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                    text = "🔙 Назад"
                                    callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                                }
                            ))
                            Bukkit.getScheduler().runTask(plugin, Runnable {
                                bot.editMenuMessage(chatId, messageId, response, keyboard)
                                scheduleMenuAutoClose(chatId, messageId)
                            })
                            return@Runnable
                        }
                        
                        val rawGender = playerData?.gender ?: "Not set"
                        val gender = if (rawGender == "man" || rawGender == "girl") conf.getGenderTranslation(rawGender) else conf.getStatusTranslation("not_set")
                        
                        // Используем ту же логику получения баланса, что и в команде /player
                        val rawBalance = bot.getPlayerBalance(playerName)
                        val balance = String.format("%.2f", rawBalance)
                        
                        val currentHealth = if (isOnline) Bukkit.getPlayerExact(playerName)?.health?.toInt() ?: 0 else 0
                        val coords = if (isOnline) {
                            val player = Bukkit.getPlayerExact(playerName)
                            val loc = player?.location
                            if (loc != null) "X: ${loc.blockX}, Y: ${loc.blockY}, Z: ${loc.blockZ}" else conf.getStatusTranslation("offline_coords")
                        } else conf.getStatusTranslation("offline_coords")
                        
                        val onlineStatus = if (isOnline) conf.getStatusTranslation("online") else conf.getStatusTranslation("offline")
                        
                        // Форматируем дату регистрации с корректным форматом (как в команде /player)
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
                        
                        val firstPlayed = if (offlinePlayer.hasPlayedBefore()) {
                            val date = java.util.Date(offlinePlayer.firstPlayed)
                            val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                            dateFormat.format(date)
                        } else conf.getStatusTranslation("never")
                        
                        val lastSeen = if (isOnline) {
                            conf.getStatusTranslation("online")
                        } else if (offlinePlayer.lastPlayed > 0) {
                            val date = java.util.Date(offlinePlayer.lastPlayed)
                            val dateFormat = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                            dateFormat.format(date)
                        } else {
                            conf.getStatusTranslation("never")
                        }
                        
                        val deaths = offlinePlayer.getStatistic(org.bukkit.Statistic.DEATHS)
                        val level = if (isOnline) Bukkit.getPlayerExact(playerName)?.level ?: 0 else 0
                        
                        // Получаем данные репутации (как в команде /player)
                        val repData = ZTele.reputation.getReputationData(playerName)
                        val reputation = repData.totalReputation.toString()
                        val reputationPositive = repData.positiveRep.toString()
                        val reputationNegative = repData.negativeRep.toString()
                        val reputationLevel = repData.reputationLevel.emoji + " " + repData.reputationLevel.displayName
                        val reputationPercent = String.format("%.1f", repData.positivePercentage)
                        
                        val context = PlaceholderEngine.createCustomContext(mapOf(
                            "player" to playerName,
                            "gender" to gender,
                            "balance" to balance,
                            "online" to onlineStatus,
                            "health" to currentHealth.toString(),
                            "registered" to registeredDate,
                            "coords" to coords,
                            "first_played" to firstPlayed,
                            "last_seen" to lastSeen,
                            "deaths" to deaths.toString(),
                            "level" to level.toString(),
                            "reputation" to reputation,
                            "reputation_positive" to reputationPositive,
                            "reputation_negative" to reputationNegative,
                            "reputation_level" to reputationLevel,
                            "reputation_percent" to reputationPercent
                        ))
                        
                        val message = PlaceholderEngine.process(conf.playerCommandResponse, context)
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                            }
                        ))
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            bot.editMenuMessage(chatId, messageId, message, keyboard)
                            scheduleMenuAutoClose(chatId, messageId)
                        })
                    })
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            // Обработка действий администрации (формат: "menu:staff:write:index:userId")
            if (action.startsWith("${CallbackData.STAFF_WRITE}:")) {
                val indexStr = action.removePrefix("${CallbackData.STAFF_WRITE}:")
                val index = indexStr.toIntOrNull()
                if (index != null && index >= 0 && index < conf.staffListPlayers.size) {
                    val staffPlayer = conf.staffListPlayers[index]
                    val telegramUsername = staffPlayer.telegram
                    val message = "✉️ **Написать администратору**\n\n" +
                            "Для связи с **${staffPlayer.nickname}** (@$telegramUsername) напишите ему в личные сообщения Telegram."
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.STAFF_LIST.withUserId(userId)
                        }
                    ))
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            if (action.startsWith("${CallbackData.STAFF_TICKET}:")) {
                val indexStr = action.removePrefix("${CallbackData.STAFF_TICKET}:")
                val index = indexStr.toIntOrNull()
                if (index != null && index >= 0 && index < conf.staffListPlayers.size) {
                    val staffPlayer = conf.staffListPlayers[index]
                    val message = "🎫 **Создать тикет**\n\n" +
                            "Для создания тикета с **${staffPlayer.nickname}** используйте команду `/ticket` в чате сервера."
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.STAFF_LIST.withUserId(userId)
                        }
                    ))
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
            if (action.startsWith("${CallbackData.STAFF_INFO}:")) {
                val indexStr = action.removePrefix("${CallbackData.STAFF_INFO}:")
                val index = indexStr.toIntOrNull()
                if (index != null && index >= 0 && index < conf.staffListPlayers.size) {
                    val staffPlayer = conf.staffListPlayers[index]
                    val detailFormat = conf.staffListPlayerDetailFormat
                    val message = detailFormat
                        .replace("%rank%", staffPlayer.rank)
                        .replace("%telegram%", staffPlayer.telegram)
                        .replace("%name%", staffPlayer.name)
                        .replace("%nickname%", staffPlayer.nickname)
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.STAFF_LIST.withUserId(userId)
                        }
                    ))
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    bot.answerCallbackQuery(callbackQueryId)
                    return true
                }
            }
            
        } catch (e: Exception) {
            plugin.logger.severe("Error handling callback: ${e.message}")
            e.printStackTrace()
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorGeneral, showAlert = true)
        }
        
        return false
    }
    
    /**
     * Проверяет rate limit для пользователя
     */
    private fun checkRateLimit(userId: Long): Boolean {
        if (conf.menuRateLimitMaxClicks <= 0) return true // Rate limiting отключен через конфиг
        if (conf.isAdministrator(userId)) return true // Администраторы не ограничены
        
        val now = System.currentTimeMillis()
        val windowStart = now - (conf.menuRateLimitTimeWindowSeconds * 1000L)
        
        val clicks = clickCounts.getOrPut(userId) { mutableListOf() }
        clicks.removeAll { it < windowStart }
        
        if (clicks.size >= conf.menuRateLimitMaxClicks) {
            if (conf.menuRateLimitBlockSeconds > 0) {
                blockedUsers[userId] = now + (conf.menuRateLimitBlockSeconds * 1000L)
            }
            return false
        }
        
        clicks.add(now)
        return true
    }
    
    /**
     * Планирует автоматическое закрытие меню
     */
    private fun scheduleMenuAutoClose(chatId: String, messageId: Int) {
        val menuAutoCloseSeconds = conf.menuAutoCloseSeconds
        if (menuAutoCloseSeconds <= 0) return
        
        val key = "$chatId:$messageId"
        
        // Отменяем предыдущую задачу, если есть
        autoCloseTasks[key]?.cancel(false)
        
        // Захватываем ссылки для использования в лямбде
        val botRef = bot
        val autoCloseTasksRef = autoCloseTasks
        val pluginRef = plugin
        
        // Планируем новую задачу
        val task = executor.schedule({
            try {
                botRef.deleteMessage(chatId, messageId)
                autoCloseTasksRef.remove(key)
            } catch (e: Exception) {
                pluginRef.logger.warning("Error auto-closing menu: ${e.message}")
            }
        }, menuAutoCloseSeconds.toLong(), TimeUnit.SECONDS)
        
        autoCloseTasks[key] = task
    }
    
    /**
     * Отменяет автоматическое закрытие меню
     */
    private fun cancelMenuAutoClose(chatId: String, messageId: Int) {
        val key = "$chatId:$messageId"
        autoCloseTasks[key]?.cancel(false)
        autoCloseTasks.remove(key)
    }
    
    /**
     * Создает пустую клавиатуру для сообщений без кнопок
     */
    private fun createEmptyKeyboard(): InlineKeyboardMarkup {
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = emptyList()
        return keyboard
    }
    
    /**
     * Показывает главное меню
     */
    private fun showMainMenu(chatId: String, messageId: Int, userId: Long, username: String) {
        val isAdmin = conf.isAdministrator(userId)
        val menuTextRaw = conf.menuMainText ?: "📱 **ГЛАВНОЕ МЕНЮ**\n\nПривет, %user%! Выберите действие:"
        val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf(
            "user" to username
        )))
        
        val screen = MainMenuScreen(menuText, userId, isAdmin)
        bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает главное меню настроек (только для администраторов)
     */
    private fun showSettingsMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        // Проверяем права администратора
        if (!conf.isAdministrator(userId)) {
            val errorText = conf.menuErrorNotOwner
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад в меню"
                    callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, errorText, backButton)
            return
        }
        
        val menuText = "⚙️ **НАСТРОЙКИ СЕРВЕРА**\n\n" +
                "Выберите категорию для управления сервером:\n\n" +
                "⚠️ Внимание: Все действия доступны только администраторам!"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔄 Рестарт"
                    callbackData = CallbackData.SETTINGS_RESTART.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "👥 Игроки"
                    callbackData = CallbackData.SETTINGS_PLAYERS.withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🌤️ Погода"
                    callbackData = CallbackData.SETTINGS_WEATHER.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🕐 Время"
                    callbackData = CallbackData.SETTINGS_TIME.withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🖥️ Сервер"
                    callbackData = CallbackData.SETTINGS_SERVER.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад в меню"
                    callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, menuText, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает меню рестарта в настройках
     */
    private fun showSettingsRestartMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery("", conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        val menuText = "🔄 **РЕСТАРТ СЕРВЕРА**\n\n" +
                "Выберите действие:\n\n" +
                "⚠️ Внимание: Рестарт перезагрузит сервер!"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "⚡ Мгновенный"
                    callbackData = CallbackData.SETTINGS_RESTART_NOW.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "⏰ Через 5 минут"
                    callbackData = CallbackData.SETTINGS_RESTART_5MIN.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "❌ Отменить"
                    callbackData = CallbackData.SETTINGS_RESTART_CANCEL.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, menuText, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает меню управления игроками
     */
    private fun showSettingsPlayersMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery("", conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val onlinePlayers = Bukkit.getOnlinePlayers().map { it.name }.sorted()
                
                if (onlinePlayers.isEmpty()) {
                    val message = "👥 **УПРАВЛЕНИЕ ИГРОКАМИ**\n\n❌ На сервере нет игроков онлайн"
                    val backButton = InlineKeyboardMarkup()
                    backButton.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                        }
                    ))
                    bot.editMenuMessage(chatId, messageId, message, backButton)
                    scheduleMenuAutoClose(chatId, messageId)
                    return@Runnable
                }
                
                val message = "👥 **УПРАВЛЕНИЕ ИГРОКАМИ**\n\n" +
                        "Выберите игрока для управления:\n\n" +
                        "Всего онлайн: **${onlinePlayers.size}**"
                
                val keyboard = InlineKeyboardMarkup()
                val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
                
                onlinePlayers.chunked(2).forEach { chunk ->
                    val row = chunk.map { playerName ->
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "👤 $playerName"
                            callbackData = "${CallbackData.SETTINGS_PLAYER_SELECT}:$playerName".withUserId(userId)
                        }
                    }
                    buttons.add(row)
                }
                
                buttons.add(listOf(
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                        text = "🔙 Назад"
                        callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                    }
                ))
                
                keyboard.keyboard = buttons
                bot.editMenuMessage(chatId, messageId, message, keyboard)
                scheduleMenuAutoClose(chatId, messageId)
            } catch (e: Exception) {
                plugin.logger.severe("Error loading players for settings: ${e.message}")
                e.printStackTrace()
                bot.editMenuMessage(chatId, messageId, conf.menuErrorGeneral, createEmptyKeyboard())
            }
        })
    }
    
    /**
     * Показывает меню действий с игроком
     */
    private fun showSettingsPlayerActionsMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String, playerName: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId)
        
        val message = "👤 **УПРАВЛЕНИЕ ИГРОКОМ**\n\n" +
                "Игрок: **$playerName**\n\n" +
                "Выберите действие:"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "👢 Кикнуть"
                    callbackData = "${CallbackData.SETTINGS_PLAYER_KICK}:$playerName".withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🚫 Бан 10 мин"
                    callbackData = "${CallbackData.SETTINGS_PLAYER_BAN_10MIN}:$playerName".withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "💀 Убить"
                    callbackData = "${CallbackData.SETTINGS_PLAYER_KILL}:$playerName".withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 К списку игроков"
                    callbackData = CallbackData.SETTINGS_PLAYERS.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, message, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает меню погоды
     */
    private fun showSettingsWeatherMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery("", conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        val menuText = "🌤️ **ПОГОДА**\n\n" +
                "Выберите тип погоды:"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "☀️ Ясно"
                    callbackData = CallbackData.SETTINGS_WEATHER_CLEAR.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🌧️ Дождь"
                    callbackData = CallbackData.SETTINGS_WEATHER_RAIN.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "⛈️ Гроза"
                    callbackData = CallbackData.SETTINGS_WEATHER_THUNDER.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, menuText, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает меню времени
     */
    private fun showSettingsTimeMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery("", conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        val menuText = "🕐 **ВРЕМЯ СЕРВЕРА**\n\n" +
                "Выберите время:"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "☀️ День"
                    callbackData = CallbackData.SETTINGS_TIME_DAY.withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🌙 Ночь"
                    callbackData = CallbackData.SETTINGS_TIME_NIGHT.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🌅 Полдень"
                    callbackData = CallbackData.SETTINGS_TIME_NOON.withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🌆 Полночь"
                    callbackData = CallbackData.SETTINGS_TIME_MIDNIGHT.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, menuText, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает меню управления сервером
     */
    private fun showSettingsServerMenu(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery("", conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        val menuText = "🖥️ **УПРАВЛЕНИЕ СЕРВЕРОМ**\n\n" +
                "Выберите действие:\n\n" +
                "⚠️ Внимание: Будьте осторожны!"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔄 Перезагрузить плагин"
                    callbackData = CallbackData.SETTINGS_SERVER_RELOAD.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🛑 Остановить сервер"
                    callbackData = CallbackData.SETTINGS_SERVER_STOP.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = CallbackData.SETTINGS_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, menuText, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Обработчики действий
     */
    private fun handleSettingsRestartNow(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🔄 Запускаем рестарт...", showAlert = false)
        bot.editMenuMessage(chatId, messageId, "🔄 **Рестарт запущен**\n\nСервер перезагружается...", createEmptyKeyboard())
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), conf.restartImmediateCommand)
        })
    }
    
    private fun handleSettingsRestart5Min(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "⏰ Планируем рестарт...", showAlert = false)
        
        ZTele.restartManager.scheduleRestart(5, username)
        
        val backButton = InlineKeyboardMarkup()
        backButton.keyboard = listOf(listOf(
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                text = "🔙 Назад"
                callbackData = CallbackData.SETTINGS_RESTART.withUserId(userId)
            }
        ))
        
        bot.editMenuMessage(chatId, messageId, "⏰ **Рестарт запланирован**\n\nСервер будет перезагружен через 5 минут.", backButton)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    private fun handleSettingsRestartCancel(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "❌ Отменяем рестарт...", showAlert = false)
        
        ZTele.restartManager.cancelScheduledRestart(username)
        
        val backButton = InlineKeyboardMarkup()
        backButton.keyboard = listOf(listOf(
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                text = "🔙 Назад"
                callbackData = CallbackData.SETTINGS_RESTART.withUserId(userId)
            }
        ))
        
        bot.editMenuMessage(chatId, messageId, "❌ **Рестарт отменен**\n\nЗапланированный рестарт был отменен.", backButton)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    private fun handleSettingsPlayerKick(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String, playerName: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "👢 Кикаем игрока...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerName)
            if (player != null) {
                player.kickPlayer("Кикнут администратором через Telegram")
                bot.editMenuMessage(chatId, messageId, "✅ **Игрок кикнут**\n\nИгрок **$playerName** был кикнут с сервера.", createEmptyKeyboard())
            } else {
                bot.editMenuMessage(chatId, messageId, "❌ **Ошибка**\n\nИгрок **$playerName** не найден онлайн.", createEmptyKeyboard())
            }
        })
    }
    
    private fun handleSettingsPlayerBan10Min(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String, playerName: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🚫 Баним игрока...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerName)
            if (player != null) {
                // Используем команду консоли для бана
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban $playerName Временный бан на 10 минут")
                player.kickPlayer("Вы забанены на 10 минут")
                
                // Автоматически разбанить через 10 минут
                val playerNameToUnban = playerName
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon $playerNameToUnban")
                }, 10 * 60 * 20L) // 10 минут в тиках
                
                bot.editMenuMessage(chatId, messageId, "✅ **Игрок забанен**\n\nИгрок **$playerName** забанен на 10 минут.", createEmptyKeyboard())
            } else {
                bot.editMenuMessage(chatId, messageId, "❌ **Ошибка**\n\nИгрок **$playerName** не найден онлайн.", createEmptyKeyboard())
            }
        })
    }
    
    private fun handleSettingsPlayerKill(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String, playerName: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "💀 Убиваем игрока...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val player = Bukkit.getPlayer(playerName)
            if (player != null) {
                player.health = 0.0
                bot.editMenuMessage(chatId, messageId, "✅ **Игрок убит**\n\nИгрок **$playerName** был убит.", createEmptyKeyboard())
            } else {
                bot.editMenuMessage(chatId, messageId, "❌ **Ошибка**\n\nИгрок **$playerName** не найден онлайн.", createEmptyKeyboard())
            }
        })
    }
    
    private fun handleSettingsWeatherClear(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "☀️ Устанавливаем ясную погоду...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.setStorm(false)
                world.setThundering(false)
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к погоде"
                    callbackData = CallbackData.SETTINGS_WEATHER.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Погода изменена**\n\nУстановлена ясная погода на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsWeatherRain(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🌧️ Устанавливаем дождь...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.setStorm(true)
                world.setThundering(false)
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к погоде"
                    callbackData = CallbackData.SETTINGS_WEATHER.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Погода изменена**\n\nУстановлен дождь на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsWeatherThunder(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "⛈️ Устанавливаем грозу...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.setStorm(true)
                world.setThundering(true)
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к погоде"
                    callbackData = CallbackData.SETTINGS_WEATHER.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Погода изменена**\n\nУстановлена гроза на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsTimeDay(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "☀️ Устанавливаем день...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.time = 1000L // День
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад ко времени"
                    callbackData = CallbackData.SETTINGS_TIME.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Время изменено**\n\nУстановлен день на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsTimeNight(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🌙 Устанавливаем ночь...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.time = 13000L // Ночь
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад ко времени"
                    callbackData = CallbackData.SETTINGS_TIME.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Время изменено**\n\nУстановлена ночь на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsTimeNoon(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🌅 Устанавливаем полдень...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.time = 6000L // Полдень
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад ко времени"
                    callbackData = CallbackData.SETTINGS_TIME.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Время изменено**\n\nУстановлен полдень на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsTimeMidnight(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🌆 Устанавливаем полночь...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.getWorlds().forEach { world ->
                world.time = 18000L // Полночь
            }
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад ко времени"
                    callbackData = CallbackData.SETTINGS_TIME.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, "✅ **Время изменено**\n\nУстановлена полночь на всех мирах.", backButton)
        })
    }
    
    private fun handleSettingsServerReload(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🔄 Перезагружаем плагин...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload Ztelegram")
            bot.editMenuMessage(chatId, messageId, "✅ **Плагин перезагружен**\n\nКоманда перезагрузки отправлена в консоль.", createEmptyKeyboard())
        })
    }
    
    private fun handleSettingsServerStop(chatId: String, messageId: Int, userId: Long, @Suppress("UNUSED_PARAMETER") username: String, callbackQueryId: String) {
        if (!conf.isAdministrator(userId)) {
            bot.answerCallbackQuery(callbackQueryId, conf.menuErrorNotOwner, showAlert = true)
            return
        }
        
        bot.answerCallbackQuery(callbackQueryId, "🛑 Останавливаем сервер...", showAlert = false)
        
        Bukkit.getScheduler().runTask(plugin, Runnable {
            Bukkit.shutdown()
            bot.editMenuMessage(chatId, messageId, "🛑 **Сервер останавливается**\n\nСервер будет остановлен через несколько секунд.", createEmptyKeyboard())
        })
    }
    
    /**
     * Показывает меню переводов денег
     */
    private fun showPaymentMenu(chatId: String, messageId: Int, userId: Long, username: String) {
        // Проверяем, что переводы включены
        if (!conf.paymentEnabled) {
            val errorText = "❌ Переводы денег отключены"
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад в меню"
                    callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, errorText, backButton)
            return
        }
        
        // Проверяем, что Vault доступен
        if (ZTele.economy == null) {
            val errorText = conf.paymentCommandVaultNotFound
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад в меню"
                    callbackData = CallbackData.MAIN_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, errorText, backButton)
            return
        }
        
        val menuTextRaw = conf.menuPaymentText ?: "💸 **ПЕРЕВОД ДЕНЕГ**\n\nПривет, %user%!\n\nВыберите действие:"
        val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf(
            "user" to username
        )))
        
        val screen = PaymentMenuScreen(menuText, userId)
        bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает историю переводов
     */
    private fun showPaymentHistory(chatId: String, messageId: Int, userId: Long, username: String) {
        // Проверяем регистрацию
        val playerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        if (playerName == null) {
            val errorText = conf.paymentCommandNotRegistered
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к переводам"
                    callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, errorText, backButton)
            return
        }
        
        // Получаем историю и статистику асинхронно
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val history = ZTele.paymentManager.getPaymentHistory(playerName, 20)
            val stats = ZTele.paymentManager.getPaymentStats(playerName)
            
            val menuTextRaw = conf.menuPaymentHistoryText ?: "📜 **ИСТОРИЯ ПЕРЕВОДОВ**\n\nПривет, %user%!\n\nВаша история переводов:"
            val menuText = PlaceholderEngine.process(menuTextRaw, PlaceholderEngine.createCustomContext(mapOf(
                "user" to username
            )))
            
            val screen = PaymentHistoryScreen(menuText, history, stats, playerName, userId)
            
            // Обновляем сообщение в основном потоке
            Bukkit.getScheduler().runTask(plugin, Runnable {
                bot.editMenuMessage(chatId, messageId, screen.text, screen.keyboard)
                scheduleMenuAutoClose(chatId, messageId)
            })
        })
    }
    
    /**
     * Показывает список игроков для выбора получателя перевода
     */
    private fun showPaymentTransferSelectPlayer(chatId: String, messageId: Int, userId: Long, username: String) {
        // Проверяем регистрацию
        val fromPlayerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        if (fromPlayerName == null) {
            val errorText = conf.paymentCommandNotRegistered
            val backButton = InlineKeyboardMarkup()
            backButton.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к переводам"
                    callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, errorText, backButton)
            return
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            // Получаем только онлайн игроков
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val onlinePlayers = Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { !it.equals(fromPlayerName, ignoreCase = true) } // Исключаем самого отправителя
                    .sorted()
                
                if (onlinePlayers.isEmpty()) {
                    val message = "💸 **ПЕРЕВОД МОНЕТ**\n\n❌ Нет онлайн игроков для перевода"
                    val keyboard = InlineKeyboardMarkup()
                    keyboard.keyboard = listOf(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад к переводам"
                            callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                        }
                    ))
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                    return@Runnable
                }
                
                val message = "💸 **ПЕРЕВОД МОНЕТ**\n\nВыберите игрока для перевода:"
                val keyboard = InlineKeyboardMarkup()
                val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
                
                onlinePlayers.chunked(2).forEach { chunk ->
                    val row = chunk.map { playerName ->
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = playerName
                            callbackData = "${CallbackData.PAYMENT_TRANSFER_SELECT}:$playerName".withUserId(userId)
                        }
                    }
                    buttons.add(row)
                }
                
                buttons.add(listOf(
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                        text = "🔙 Назад к переводам"
                        callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                    }
                ))
                
                keyboard.keyboard = buttons
                bot.editMenuMessage(chatId, messageId, message, keyboard)
                scheduleMenuAutoClose(chatId, messageId)
            })
        })
    }
    
    /**
     * Показывает выбор суммы перевода
     */
    private fun showPaymentTransferSelectAmount(chatId: String, messageId: Int, userId: Long, username: String, targetPlayerName: String) {
        val message = "💸 **ПЕРЕВОД МОНЕТ**\n\nИгрок: **$targetPlayerName**\n\nВыберите сумму перевода:"
        val keyboard = InlineKeyboardMarkup()
        val amounts = listOf(1.0, 10.0, 50.0, 100.0, 1000.0, 5000.0, 10000.0)
        val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
        
        // Размещаем кнопки по 2 в ряд
        amounts.chunked(2).forEach { chunk ->
            val row = chunk.map { amount ->
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "${amount.toInt()}"
                    callbackData = "${CallbackData.PAYMENT_TRANSFER_AMOUNT}:$targetPlayerName:$amount".withUserId(userId)
                }
            }
            buttons.add(row)
        }
        
        buttons.add(listOf(
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                text = "🔙 Назад к выбору игрока"
                callbackData = CallbackData.PAYMENT_TRANSFER.withUserId(userId)
            }
        ))
        
        keyboard.keyboard = buttons
        bot.editMenuMessage(chatId, messageId, message, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает подтверждение перевода
     */
    private fun showPaymentTransferConfirm(chatId: String, messageId: Int, userId: Long, username: String, targetPlayerName: String, amount: Double) {
        val economy = ZTele.economy
        val currencyName = economy?.currencyNamePlural() ?: "монет"
        val message = "💸 **ПОДТВЕРЖДЕНИЕ ПЕРЕВОДА**\n\n" +
                "Отправитель: **${ZTele.mgr.getPlayerByTelegramId(userId.toString()) ?: "Неизвестно"}**\n" +
                "Получатель: **$targetPlayerName**\n" +
                "Сумма: **${String.format("%.2f", amount)}** $currencyName\n\n" +
                "Подтвердите перевод:"
        
        val keyboard = InlineKeyboardMarkup()
        keyboard.keyboard = listOf(
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "✅ Подтвердить"
                    callbackData = "${CallbackData.PAYMENT_TRANSFER_CONFIRM}:$targetPlayerName:$amount".withUserId(userId)
                },
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "❌ Отмена"
                    callbackData = CallbackData.PAYMENT_TRANSFER.withUserId(userId)
                }
            ),
            listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к переводам"
                    callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                }
            )
        )
        
        bot.editMenuMessage(chatId, messageId, message, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Выполняет перевод денег
     */
    private fun executePaymentTransfer(chatId: String, messageId: Int, userId: Long, username: String, callbackQueryId: String, targetPlayerName: String, amount: Double) {
        val fromPlayerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        if (fromPlayerName == null) {
            bot.answerCallbackQuery(callbackQueryId, conf.paymentCommandNotRegistered, showAlert = true)
            return
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val result = ZTele.paymentManager.transferMoney(fromPlayerName, targetPlayerName, amount)
            val economy = ZTele.economy
            val currencyName = economy?.currencyNamePlural() ?: "монет"
            
            val message: String
            val keyboard = InlineKeyboardMarkup()
            
            if (result.success) {
                val newBalance = result.newBalance ?: 0.0
                message = "✅ **ПЕРЕВОД ВЫПОЛНЕН**\n\n" +
                        "Отправитель: **$fromPlayerName**\n" +
                        "Получатель: **$targetPlayerName**\n" +
                        "Сумма: **${String.format("%.2f", amount)}** $currencyName\n" +
                        "Ваш баланс: **${String.format("%.2f", newBalance)}** $currencyName"
            } else {
                val errorMessage = when (result.errorCode) {
                    "vault_not_found" -> conf.paymentCommandVaultNotFound
                    "invalid_amount" -> conf.paymentCommandInvalidAmount
                    "min_amount" -> conf.paymentCommandErrorMinAmount
                        .replace("%min_amount%", String.format("%.2f", conf.paymentMinAmount))
                        .replace("%currency%", currencyName)
                    "max_amount" -> conf.paymentCommandErrorMaxAmount
                        .replace("%max_amount%", String.format("%.2f", conf.paymentMaxAmount))
                        .replace("%currency%", currencyName)
                    "same_player" -> conf.paymentCommandErrorSamePlayer
                    "player_not_found" -> conf.paymentCommandErrorPlayerNotFound.replace("%player%", targetPlayerName)
                    "insufficient_funds" -> {
                        val balance = ZTele.economy?.getBalance(Bukkit.getOfflinePlayer(fromPlayerName)) ?: 0.0
                        conf.paymentCommandErrorInsufficientFunds
                            .replace("%balance%", String.format("%.2f", balance))
                            .replace("%currency%", currencyName)
                    }
                    "withdraw_error" -> conf.paymentCommandErrorWithdraw.replace("%error%", result.errorMessage ?: "Неизвестная ошибка")
                    "deposit_error" -> conf.paymentCommandErrorDeposit.replace("%error%", result.errorMessage ?: "Неизвестная ошибка")
                    else -> conf.paymentCommandErrorGeneral
                }
                message = "❌ **ОШИБКА ПЕРЕВОДА**\n\n$errorMessage"
            }
            
            keyboard.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад к переводам"
                    callbackData = CallbackData.PAYMENT_MENU.withUserId(userId)
                }
            ))
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                bot.editMenuMessage(chatId, messageId, message, keyboard)
                scheduleMenuAutoClose(chatId, messageId)
                bot.answerCallbackQuery(callbackQueryId, if (result.success) "✅ Перевод выполнен" else "❌ Ошибка перевода", showAlert = !result.success)
            })
        })
    }
    
    /**
     * Показывает страницу со списком наград рулетки
     * @param page Номер страницы (начиная с 0)
     */
    private fun showRandomRewardsPage(chatId: String, messageId: Int, userId: Long, page: Int) {
        val rewards = conf.randomCommandRewards
        val descriptions = conf.randomCommandRewardDescriptions
        
        if (rewards.isEmpty()) {
            val message = "📋 **Список наград пуст**"
            val keyboard = InlineKeyboardMarkup()
            keyboard.keyboard = listOf(listOf(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
                }
            ))
            bot.editMenuMessage(chatId, messageId, message, keyboard)
            scheduleMenuAutoClose(chatId, messageId)
            return
        }
        
        // Количество наград на странице (примерно 25, чтобы не превысить лимит Telegram в 4096 символов)
        val itemsPerPage = 25
        val totalPages = (rewards.size + itemsPerPage - 1) / itemsPerPage
        val currentPage = page.coerceIn(0, totalPages - 1)
        
        val startIndex = currentPage * itemsPerPage
        val endIndex = (startIndex + itemsPerPage).coerceAtMost(rewards.size)
        
        val message = buildString {
            append("📋 **Список наград рулетки**\n")
            append("Страница ${currentPage + 1} из $totalPages\n\n")
            
            for (i in startIndex until endIndex) {
                val desc = descriptions.getOrNull(i) ?: rewards[i]
                append("${i + 1}. $desc\n")
            }
        }
        
        val keyboard = InlineKeyboardMarkup()
        val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
        
        // Кнопки навигации по страницам
        if (totalPages > 1) {
            val navButtons = mutableListOf<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>()
            
            // Кнопка "Назад" (предыдущая страница)
            if (currentPage > 0) {
                navButtons.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "◀️ Назад"
                    callbackData = "${CallbackData.RANDOM_REWARDS_PAGE}:${currentPage - 1}".withUserId(userId)
                })
            }
            
            // Кнопка "Вперед" (следующая страница)
            if (currentPage < totalPages - 1) {
                navButtons.add(org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                    text = "Вперед ▶️"
                    callbackData = "${CallbackData.RANDOM_REWARDS_PAGE}:${currentPage + 1}".withUserId(userId)
                })
            }
            
            if (navButtons.isNotEmpty()) {
                buttons.add(navButtons)
            }
        }
        
        // Кнопка "Назад в меню"
        buttons.add(listOf(
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                text = "🔙 Назад"
                callbackData = CallbackData.RANDOM_MENU.withUserId(userId)
            }
        ))
        
        keyboard.keyboard = buttons
        bot.editMenuMessage(chatId, messageId, message, keyboard)
        scheduleMenuAutoClose(chatId, messageId)
    }
    
    /**
     * Показывает список игроков (зарегистрированные или только онлайн)
     */
    private fun showPlayerList(chatId: String, messageId: Int, userId: Long, showOnlyOnline: Boolean) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            if (showOnlyOnline) {
                // Показываем только онлайн игроков
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val onlinePlayers = Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .sorted()
                    
                    if (onlinePlayers.isEmpty()) {
                        val message = "👤 **ОНЛАЙН ИГРОКИ**\n\n❌ На сервере нет игроков"
                        val keyboard = InlineKeyboardMarkup()
                        keyboard.keyboard = listOf(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                            }
                        ))
                        bot.editMenuMessage(chatId, messageId, message, keyboard)
                        scheduleMenuAutoClose(chatId, messageId)
                        return@Runnable
                    }
                    
                    val message = "👤 **ОНЛАЙН ИГРОКИ**\n\nВыберите игрока для просмотра информации:"
                    val keyboard = InlineKeyboardMarkup()
                    val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
                    
                    onlinePlayers.chunked(2).forEach { chunk ->
                        val row = chunk.map { playerName ->
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🟢 $playerName"
                                callbackData = "${CallbackData.PLAYER_SELECT}:$playerName".withUserId(userId)
                            }
                        }
                        buttons.add(row)
                    }
                    
                    buttons.add(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                        }
                    ))
                    
                    keyboard.keyboard = buttons
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                    scheduleMenuAutoClose(chatId, messageId)
                })
            } else {
                // Показываем всех зарегистрированных игроков
                val allPlayers = ZTele.mgr.getAllRegisteredPlayers().keys.sorted()
                val message = if (allPlayers.isEmpty()) {
                    "👤 **ЗАРЕГИСТРИРОВАННЫЕ ИГРОКИ**\n\n❌ Нет зарегистрированных игроков"
                } else {
                    "👤 **ЗАРЕГИСТРИРОВАННЫЕ ИГРОКИ**\n\nВыберите игрока для просмотра информации:"
                }
                val keyboard = InlineKeyboardMarkup()
                val buttons = mutableListOf<List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>>()
                
                if (allPlayers.isNotEmpty()) {
                    // Проверяем статус игроков синхронно на основном потоке
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        allPlayers.chunked(2).forEach { chunk ->
                            val row = chunk.map { lowerPlayerName ->
                                // Получаем оригинальное имя с правильным регистром
                                val originalName = ZTele.mgr.getOriginalPlayerName(lowerPlayerName)
                                val isOnline = Bukkit.getPlayerExact(originalName) != null
                                val statusEmoji = if (isOnline) "🟢" else "🔴"
                                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                    text = "$statusEmoji $originalName"
                                    // Используем оригинальное имя для callback
                                    callbackData = "${CallbackData.PLAYER_SELECT}:$originalName".withUserId(userId)
                                }
                            }
                            buttons.add(row)
                        }
                        buttons.add(listOf(
                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                                text = "🔙 Назад"
                                callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                            }
                        ))
                        keyboard.keyboard = buttons
                        bot.editMenuMessage(chatId, messageId, message, keyboard)
                        scheduleMenuAutoClose(chatId, messageId)
                    })
                } else {
                    buttons.add(listOf(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                            text = "🔙 Назад"
                            callbackData = CallbackData.PLAYER_MENU.withUserId(userId)
                        }
                    ))
                    keyboard.keyboard = buttons
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        bot.editMenuMessage(chatId, messageId, message, keyboard)
                        scheduleMenuAutoClose(chatId, messageId)
                    })
                }
            }
        })
    }
    
    /**
     * Обрабатывает callback'и для меню регистрации
     */
    private fun handleRegisterCallback(
        action: String,
        chatId: String,
        messageId: Int,
        userId: Long,
        username: String,
        callbackQueryId: String
    ): Boolean {
        // Убираем userId из action если он там есть (формат: "action:userId")
        val actionWithoutUserId = if (action.contains(":")) {
            val parts = action.split(":")
            // Проверяем, является ли последняя часть числом (userId)
            val lastPart = parts.lastOrNull()
            if (lastPart?.toLongOrNull() != null && parts.size > 1) {
                // Это userId, убираем его
                parts.dropLast(1).joinToString(":")
            } else {
                action
            }
        } else {
            action
        }
        
        // Обрабатываем специальные случаи с дополнительными параметрами
        // Проверяем REGISTER_LIST_PAGE до обработки остальных callback'ов
        // Важно: проверяем как actionWithoutUserId, так и исходный action, так как userId может быть уже удален
        val checkAction = actionWithoutUserId
        if (checkAction.startsWith("${CallbackData.REGISTER_LIST_PAGE}:")) {
            // Извлекаем номер страницы: формат "register:list:page:1"
            val pageStr = checkAction.removePrefix("${CallbackData.REGISTER_LIST_PAGE}:")
            // Убираем возможные дополнительные параметры после номера страницы
            val pageNumber = pageStr.split(":").firstOrNull()?.toIntOrNull() ?: 0
            if (conf.debugEnabled) {
                plugin.logger.info("📄 [RegisterMenu] Переход на страницу $pageNumber (из action: $action, actionWithoutUserId: $actionWithoutUserId)")
            }
            ZTele.registerMenuManager.showRegisteredPlayersListPage(chatId, messageId, userId, pageNumber)
            bot.answerCallbackQuery(callbackQueryId)
            return true
        }
        
        // Для остальных используем точное совпадение
        when (actionWithoutUserId) {
            CallbackData.REGISTER_MENU -> {
                ZTele.registerMenuManager.showMainMenu(chatId, messageId, userId, username)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_START -> {
                // Показываем инструкцию для регистрации
                val message = "✅ **РЕГИСТРАЦИЯ**\n\n" +
                        "Введите свой никнейм Minecraft в этот чат.\n\n" +
                        "📝 Требования:\n" +
                        "• Только английские буквы, цифры и символ _\n" +
                        "• Длина от 3 до 16 символов\n\n" +
                        "Или используйте код регистрации из игры."
                val keyboard = InlineKeyboardMarkup()
                keyboard.keyboard = listOf(listOf(
                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton().apply {
                        text = "🔙 Назад"
                        callbackData = CallbackData.REGISTER_MENU.withUserId(userId)
                    }
                ))
                bot.editMenuMessage(chatId, messageId, message, keyboard)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_UNREGISTER -> {
                ZTele.registerMenuManager.showUnregisterConfirm(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_UNREGISTER_CONFIRM -> {
                ZTele.registerMenuManager.executeUnregister(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_LIST -> {
                ZTele.registerMenuManager.showRegisteredPlayersList(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            
            CallbackData.REGISTER_INFO -> {
                ZTele.registerMenuManager.showRegistrationInfo(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_REWARDS -> {
                ZTele.registerMenuManager.showRegistrationRewards(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
            
            CallbackData.REGISTER_LINK_ACCOUNT -> {
                ZTele.registerMenuManager.showLinkAccountMenu(chatId, messageId, userId)
                bot.answerCallbackQuery(callbackQueryId)
                return true
            }
        }
        
        return false
    }
}
