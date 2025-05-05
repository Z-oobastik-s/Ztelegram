package org.zoobastiks.ztelegram.conf

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import java.io.File

class TConf(private val plugin: ZTele) {
    companion object {
        lateinit var botToken: String
            private set
    }
    
    // Channel IDs
    var mainChannelId: String = "-1002111043217"
    var consoleChannelId: String = "-1002656200279"
    var registerChannelId: String = "-1002611802353"
    
    // Main channel settings
    var mainChannelEnabled: Boolean = true
    var mainChannelChatEnabled: Boolean = true
    var formatTelegramToMinecraft: String = "&b[Telegram] &f%player%: &7%message%"
    var formatMinecraftToTelegram: String = "📤 **%player%**: %message%"
    
    // Настройки белого и черного списка
    var whitelistEnabled: Boolean = false
    var blacklistEnabled: Boolean = false
    var noRegistrationMessage: String = "❌ Вы не зарегистрированы! Пожалуйста, зарегистрируйте свой аккаунт в игре с помощью команды /telegram link"
    var blockedMessage: String = "❌ Вы заблокированы и не можете отправлять сообщения на сервер!"
    
    // Настройки команды link
    var linkMessage: String = ""
    var linkSuccessMessage: String = ""
    var linkErrorMessage: String = ""
    var linkWasRegisteredMessage: String = ""
    var linkCodeExpirationMinutes: Int = 10
    var linkCodeLength: Int = 6
    
    // Unlink command settings
    var unlinkNotRegisteredMessage: String = ""
    var unlinkAlreadyUnlinkedMessage: String = ""
    var unlinkSuccessMessage: String = ""
    var unlinkInfoMessage: String = ""
    var unlinkRelinkMessage: String = ""
    var unlinkErrorMessage: String = ""
    
    // Server events
    var serverStartEnabled: Boolean = true
    var serverStopEnabled: Boolean = true
    var serverStartMessage: String = "🟢 Server started"
    var serverStopMessage: String = "🔴 Server stopped"
    
    // Player events
    var playerJoinEnabled: Boolean = true
    var playerQuitEnabled: Boolean = true
    var playerDeathEnabled: Boolean = true
    var playerChatEnabled: Boolean = true
    var playerJoinMessage: String = "🟢 %player% joined the server"
    var playerQuitMessage: String = "🔴 %player% left the server"
    var playerDeathMessage: String = "💀 %player% %death_message%"
    
    // Telegram commands
    var enabledOnlineCommand: Boolean = true
    var enabledTpsCommand: Boolean = true
    var enabledRestartCommand: Boolean = true
    var enabledGenderCommand: Boolean = true
    var enabledPlayerCommand: Boolean = true
    
    // Telegram command responses
    var onlineCommandResponse: String = "Online: %online%/%max%\nPlayers: %players%"
    var tpsCommandResponse: String = "Server TPS: %tps%"
    var restartCommandResponse: String = "⚠️ Server is restarting..."
    var genderCommandUsage: String = "Usage: /gender [man/girl]"
    var genderCommandNoPlayer: String = "You need to register your nickname first!"
    var genderCommandResponse: String = "Gender for %player% set to %gender%"
    var playerCommandUsage: String = "Usage: /player <nickname>"
    var playerCommandNoPlayer: String = "Player %player% not found"
    var playerCommandResponse: String = "Player: %player%\nOnline: %online%\nHealth: %health%\nGender: %gender%\nRegistered: %registered%\nFirst played: %first_played%\nDeaths: %deaths%\nLevel: %level%\nBalance: %balance%\nCoordinates: %coords%"
    
    // Новая команда для вывода списка команд
    var enabledCommandsListCommand: Boolean = true
    var commandsListResponse: String = """
        <gradient:#0052CC:#45B6FE>Доступные команды:</gradient>
        
        <gradient:#4CAF50:#8BC34A>• /online, /онлайн</gradient> - показать список игроков онлайн
        <gradient:#4CAF50:#8BC34A>• /tps, /тпс</gradient> - показать TPS сервера
        <gradient:#4CAF50:#8BC34A>• /restart, /рестарт</gradient> - перезапустить сервер
        <gradient:#4CAF50:#8BC34A>• /gender [man/girl], /пол [м/ж]</gradient> - установить свой пол
        <gradient:#4CAF50:#8BC34A>• /player [nickname], /ник [никнейм]</gradient> - информация об игроке
        <gradient:#4CAF50:#8BC34A>• /cmd, /команды</gradient> - показать список всех команд
        <gradient:#4CAF50:#8BC34A>• /game [nickname], /игра [никнейм]</gradient> - сыграть в игру "Угадай слово"
        
        <gradient:#FF9800:#FFEB3B>Команды доступны только в следующих каналах:</gradient>
        • Основной канал: все команды
        • Канал для регистрации: только имя пользователя
        • Консольный канал: любые серверные команды
    """
    
    // Gender translations
    var genderTranslations: Map<String, String> = mapOf(
        "man" to "Мужчина",
        "girl" to "Женщина"
    )
    
    // Status translations
    var statusTranslations: Map<String, String> = mapOf(
        "online" to "Онлайн",
        "offline" to "Оффлайн",
        "not_set" to "Не указано",
        "not_registered" to "Не зарегистрирован",
        "never" to "Никогда",
        "offline_coords" to "Недоступно"
    )
    
    // Game settings
    var gameEnabled: Boolean = true
    var gameNotRegisteredMessage: String = "❌ Вы не зарегистрированы! Пожалуйста, зарегистрируйте свой аккаунт в игре с помощью команды /telegram link"
    var gameStartMessage: String = "🎮 Игра \"Угадай слово\" началась! Игрок: %player%\n\n%question%"
    var gameCorrectMessage: String = "✅ Правильно! %player% угадал слово: %word%\n🎁 Награда: %reward%"
    var gameIncorrectMessage: String = "❌ Неправильно! Попробуйте еще раз.\nПодсказка: %hint%"
    var gameOverMessage: String = "⏱ Время вышло! Правильный ответ: %word%"
    var gameAlreadyPlayingMessage: String = "❌ У вас уже есть активная игра! Закончите текущую игру, прежде чем начать новую."
    var gameAutoDeleteSeconds: Int = 15  // Время в секундах для автоудаления сообщений игры
    
    // Commands auto-delete timeout
    var commandsAutoDeleteSeconds: Int = 30
    
    // Console channel settings
    var consoleChannelEnabled: Boolean = true
    var playerCommandLogEnabled: Boolean = true
    var playerCommandLogFormat: String = "[%time%] %player% executed: %command%"
    var consoleCommandFeedbackEnabled: Boolean = true
    var consoleCommandFeedback: String = "✅ Command executed: %command%"
    var consoleCommandError: String = "❌ Command failed: %command%\nError: %error%"
    var consoleAutoDeleteSeconds: Int = 30
    
    // Console whitelist commands
    var whitelistAddSuccess: String = "✅ Игрок %player% добавлен в белый список сервера"
    var whitelistRemoveSuccess: String = "✅ Игрок %player% удален из белого списка сервера"
    var whitelistAddError: String = "❌ Не удалось добавить игрока %player% в белый список"
    var whitelistRemoveError: String = "❌ Игрок %player% не найден в белом списке"
    var whitelistOn: String = "✅ Белый список сервера успешно включен"
    var whitelistOff: String = "✅ Белый список сервера успешно отключен"
    var whitelistListHeader: String = "📋 Список игроков в белом списке сервера:"
    var whitelistListEmpty: String = "📋 Белый список сервера пуст"
    var whitelistListEntry: String = "  • %player%"
    
    // Console plugin commands
    var pluginCommandSuccess: String = "✅ Команда плагина выполнена успешно"
    var pluginCommandError: String = "❌ Ошибка при выполнении команды плагина: %error%"
    var pluginTelegramInfo: String = "📱 Основная информация о плагине ZTelegram"
    var pluginAddChannelSuccess: String = "📱 Канал #%channel_number% обновлен на %channel_id%"
    var pluginAddPlayerSuccess: String = "📱 Игрок %player% теперь скрыт в сообщениях Telegram"
    var pluginRemovePlayerSuccess: String = "📱 Игрок %player% теперь виден в сообщениях Telegram"
    
    // Новые команды плагина
    var pluginReloadSuccess: String = "✅ Конфигурация плагина успешно перезагружена"
    var pluginUnregisterSuccess: String = "✅ Регистрация игрока %player% успешно отменена"
    var pluginUnregisterNotRegistered: String = "❌ Игрок %player% не зарегистрирован в Telegram"
    var pluginHiddenEmpty: String = "📋 Список скрытых игроков пуст"
    var pluginHiddenHeader: String = "📋 Список скрытых игроков:"
    
    // Whitelist команды
    var pluginWhitelistAddSuccess: String = "✅ Пользователь с ID %user_id% добавлен в белый список"
    var pluginWhitelistAddAlready: String = "❌ Пользователь с ID %user_id% уже находится в белом списке"
    var pluginWhitelistRemoveSuccess: String = "✅ Пользователь с ID %user_id% удален из белого списка"
    var pluginWhitelistRemoveNotFound: String = "❌ Пользователь с ID %user_id% не найден в белом списке"
    var pluginWhitelistListEmpty: String = "📋 Белый список пользователей Telegram пуст"
    var pluginWhitelistListHeader: String = "📋 Белый список пользователей Telegram:"
    var pluginWhitelistOnSuccess: String = "✅ Белый список Telegram включен"
    var pluginWhitelistOffSuccess: String = "✅ Белый список Telegram отключен"
    
    // Blacklist команды
    var pluginBlacklistAddSuccess: String = "✅ Пользователь с ID %user_id% добавлен в черный список"
    var pluginBlacklistAddAlready: String = "❌ Пользователь с ID %user_id% уже находится в черном списке"
    var pluginBlacklistRemoveSuccess: String = "✅ Пользователь с ID %user_id% удален из черного списка"
    var pluginBlacklistRemoveNotFound: String = "❌ Пользователь с ID %user_id% не найден в черном списке"
    var pluginBlacklistListEmpty: String = "📋 Черный список пользователей Telegram пуст"
    var pluginBlacklistListHeader: String = "📋 Черный список пользователей Telegram:"
    var pluginBlacklistOnSuccess: String = "✅ Черный список Telegram включен"
    var pluginBlacklistOffSuccess: String = "✅ Черный список Telegram отключен"
    
    // Help команда
    var pluginHelpMessage: String = """
        📋 Доступные команды для консольного канала:
        
        ⚙️ Команды сервера:
        • Любая серверная команда без префикса
        
        🛠️ Команды белого списка:
        • /whitelist add <player> - добавить игрока в белый список сервера
        • /whitelist remove <player> - удалить игрока из белого списка сервера
        • /whitelist on - включить белый список сервера
        • /whitelist off - отключить белый список сервера
        • /whitelist list - показать список игроков в белом списке
        
        📱 Команды плагина Telegram:
        • /telegram addchannel <1|2|3> <channelId> - обновить ID канала
        • /telegram addplayer <player> - скрыть игрока в сообщениях Telegram
        • /telegram removeplayer <player> - показать игрока в сообщениях Telegram
        • /telegram reload - перезагрузить конфигурацию плагина
        • /telegram unregister <player> - отменить регистрацию игрока
        • /telegram hidden - показать список скрытых игроков
        • /telegram whitelist add/remove/list/on/off - управление белым списком Telegram
        • /telegram blacklist add/remove/list/on/off - управление черным списком Telegram
        • /telegram help - показать эту справку
    """
    
    // Register channel settings
    var registerChannelEnabled: Boolean = true
    var registerInvalidUsername: String = "❌ Invalid username: %player%"
    var registerAlreadyRegistered: String = "❌ Player %player% is already registered"
    var registerPlayerOffline: String = "❌ Player %player% is not online"
    var registerSuccess: String = "✅ Successfully registered player %player%"
    var registerSuccessInGame: String = "§a✅ Your account has been linked to Telegram!"
    var registerRewardCommands: List<String> = listOf("eco give %player% 50")
    var registerCodeInvalid: String = "❌ Неверный код регистрации или срок его действия истек."
    var registerCodeSuccess: String = "✅ Код подтвержден. Аккаунт %player% успешно зарегистрирован."
    
    // Plugin settings
    var pluginPrefix: String = "§b[ZTelegram]§r"
    var telegramLink: String = "https://t.me/ReZoobastik"
    var telegramCommandMessage: String = "<gradient:#FF0000:#A6EB0F>〔Телеграм〕</gradient> <hover:show_text:\"Кликни, чтобы открыть канал\"><gradient:#A6EB0F:#00FF00>Присоединяйтесь к нашему Telegram каналу!</gradient></hover>"
    var telegramClickText: String = "Нажмите сюда, чтобы открыть"
    var telegramHoverText: String = "Открыть Telegram канал"
    var noPermissionMessage: String = "§cУ вас нет доступа к этой команде!"
    
    // Helper methods
    data class Messages(
        var commands: CommandMessages = CommandMessages(),
        var broadcast: BroadcastMessages = BroadcastMessages()
    )
    
    data class CommandMessages(
        var alreadyRegistered: String = "❌ Вы уже зарегистрированы с именем %player%!",
        var linkSuccess: String = "✅ Регистрация успешна! Теперь вы можете отправлять сообщения в игру.",
        var linkInvalid: String = "❌ Неверный код регистрации или срок его действия истек.",
        var linkPlayerAlreadyRegistered: String = "❌ Игрок с таким именем уже зарегистрирован!"
    )
    
    data class BroadcastMessages(
        var playerRegistered: String = "&b%player% &eзарегистрировал свой аккаунт в Telegram"
    )
    
    // Messages объект, который будет содержать все сообщения для команд
    var messages = Messages()
    
    // Files
    private val playersFile = File(plugin.dataFolder, "players.yml")
    private lateinit var playersConfig: YamlConfiguration
    
    init {
        plugin.saveDefaultConfig()
        if (!playersFile.exists()) {
            plugin.saveResource("players.yml", false)
        }
        
        playersConfig = YamlConfiguration.loadConfiguration(playersFile)
        
        // Инициализируем messages с дефолтными значениями
        messages = Messages(
            CommandMessages(
                alreadyRegistered = "❌ Вы уже зарегистрированы с именем %player%!",
                linkSuccess = "✅ Регистрация успешна! Теперь вы можете отправлять сообщения в игру.",
                linkInvalid = "❌ Неверный код регистрации или срок его действия истек.",
                linkPlayerAlreadyRegistered = "❌ Игрок с таким именем уже зарегистрирован!"
            ),
            BroadcastMessages(
                playerRegistered = "&b%player% &eзарегистрировал свой аккаунт в Telegram"
            )
        )
        
        loadConfig()
    }
    
    fun reload() {
        plugin.reloadConfig()
        playersConfig = YamlConfiguration.loadConfiguration(playersFile)
        loadConfig()
    }
    
    fun getPlayersConfig(): YamlConfiguration {
        return playersConfig
    }
    
    fun savePlayersConfig() {
        try {
            playersConfig.save(playersFile)
        } catch (e: Exception) {
            plugin.logger.severe("Could not save players.yml: ${e.message}")
        }
    }
    
    // Получение перевода для gender
    fun getGenderTranslation(gender: String): String {
        return genderTranslations[gender.lowercase()] ?: gender
    }
    
    // Получение перевода для статуса
    fun getStatusTranslation(status: String): String {
        val key = status.lowercase().replace(" ", "_")
        return statusTranslations[key] ?: status
    }
    
    private fun loadConfig() {
        val conf = plugin.config
        
        // Bot settings
        botToken = conf.getString("bot.token", "") ?: ""
        
        // Channel IDs
        mainChannelId = conf.getString("channels.main", "-1002111043217") ?: "-1002111043217"
        consoleChannelId = conf.getString("channels.console", "-1002656200279") ?: "-1002656200279"
        registerChannelId = conf.getString("channels.register", "-1002611802353") ?: "-1002611802353"
        
        // Main channel settings
        mainChannelEnabled = conf.getBoolean("main-channel.enabled", true)
        mainChannelChatEnabled = conf.getBoolean("main-channel.chat-enabled", true)
        formatTelegramToMinecraft = conf.getString("main-channel.format-telegram-to-minecraft", formatTelegramToMinecraft) ?: formatTelegramToMinecraft
        formatMinecraftToTelegram = conf.getString("main-channel.format-minecraft-to-telegram", formatMinecraftToTelegram) ?: formatMinecraftToTelegram
        
        // Настройки белого и черного списка
        whitelistEnabled = conf.getBoolean("main-channel.whitelist.enabled", false)
        blacklistEnabled = conf.getBoolean("main-channel.blacklist.enabled", false)
        noRegistrationMessage = conf.getString("main-channel.whitelist.no-registration-message", noRegistrationMessage) ?: noRegistrationMessage
        blockedMessage = conf.getString("main-channel.blacklist.blocked-message", blockedMessage) ?: blockedMessage
        
        // Загрузка сообщений для команд
        val messagesSection = conf.getConfigurationSection("messages")
        if (messagesSection != null) {
            // Загрузка сообщений для регистрации
            val commandsSection = messagesSection.getConfigurationSection("commands")
            if (commandsSection != null) {
                messages.commands.alreadyRegistered = commandsSection.getString("already-registered", messages.commands.alreadyRegistered) ?: messages.commands.alreadyRegistered
                messages.commands.linkSuccess = commandsSection.getString("link-success", messages.commands.linkSuccess) ?: messages.commands.linkSuccess
                messages.commands.linkInvalid = commandsSection.getString("link-invalid", messages.commands.linkInvalid) ?: messages.commands.linkInvalid
                messages.commands.linkPlayerAlreadyRegistered = commandsSection.getString("link-player-already-registered", messages.commands.linkPlayerAlreadyRegistered) ?: messages.commands.linkPlayerAlreadyRegistered
            }
            
            // Загрузка сообщений для рассылки
            val broadcastSection = messagesSection.getConfigurationSection("broadcast")
            if (broadcastSection != null) {
                messages.broadcast.playerRegistered = broadcastSection.getString("player-registered", messages.broadcast.playerRegistered) ?: messages.broadcast.playerRegistered
            }
        }
        
        // Настройки команды link
        linkMessage = conf.getString("plugin.link.message", linkMessage) ?: linkMessage
        linkSuccessMessage = conf.getString("plugin.link.success-message", linkSuccessMessage) ?: linkSuccessMessage
        linkErrorMessage = conf.getString("plugin.link.error-message", linkErrorMessage) ?: linkErrorMessage
        linkWasRegisteredMessage = conf.getString("plugin.link.was-registered-message", linkWasRegisteredMessage) ?: linkWasRegisteredMessage
        linkCodeExpirationMinutes = conf.getInt("plugin.link.code-expiration-minutes", 10)
        linkCodeLength = conf.getInt("plugin.link.code-length", 6)
        
        // Unlink command settings
        unlinkNotRegisteredMessage = conf.getString("plugin.unlink.not-registered-message", unlinkNotRegisteredMessage) ?: unlinkNotRegisteredMessage
        unlinkAlreadyUnlinkedMessage = conf.getString("plugin.unlink.already-unlinked-message", unlinkAlreadyUnlinkedMessage) ?: unlinkAlreadyUnlinkedMessage
        unlinkSuccessMessage = conf.getString("plugin.unlink.success-message", unlinkSuccessMessage) ?: unlinkSuccessMessage
        unlinkInfoMessage = conf.getString("plugin.unlink.info-message", unlinkInfoMessage) ?: unlinkInfoMessage
        unlinkRelinkMessage = conf.getString("plugin.unlink.relink-message", unlinkRelinkMessage) ?: unlinkRelinkMessage
        unlinkErrorMessage = conf.getString("plugin.unlink.error-message", unlinkErrorMessage) ?: unlinkErrorMessage
        
        // Server events
        serverStartEnabled = conf.getBoolean("events.server-start.enabled", true)
        serverStopEnabled = conf.getBoolean("events.server-stop.enabled", true)
        serverStartMessage = conf.getString("events.server-start.message", serverStartMessage) ?: serverStartMessage
        serverStopMessage = conf.getString("events.server-stop.message", serverStopMessage) ?: serverStopMessage
        
        // Player events
        playerJoinEnabled = conf.getBoolean("events.player-join.enabled", true)
        playerQuitEnabled = conf.getBoolean("events.player-quit.enabled", true)
        playerDeathEnabled = conf.getBoolean("events.player-death.enabled", true)
        playerChatEnabled = conf.getBoolean("events.player-chat.enabled", true)
        playerJoinMessage = conf.getString("events.player-join.message", playerJoinMessage) ?: playerJoinMessage
        playerQuitMessage = conf.getString("events.player-quit.message", playerQuitMessage) ?: playerQuitMessage
        playerDeathMessage = conf.getString("events.player-death.message", playerDeathMessage) ?: playerDeathMessage
        
        // Telegram commands
        enabledOnlineCommand = conf.getBoolean("commands.online.enabled", true)
        enabledTpsCommand = conf.getBoolean("commands.tps.enabled", true)
        enabledRestartCommand = conf.getBoolean("commands.restart.enabled", true)
        enabledGenderCommand = conf.getBoolean("commands.gender.enabled", true)
        enabledPlayerCommand = conf.getBoolean("commands.player.enabled", true)
        
        // Telegram command responses
        onlineCommandResponse = conf.getString("commands.online.response", onlineCommandResponse) ?: onlineCommandResponse
        tpsCommandResponse = conf.getString("commands.tps.response", tpsCommandResponse) ?: tpsCommandResponse
        restartCommandResponse = conf.getString("commands.restart.response", restartCommandResponse) ?: restartCommandResponse
        genderCommandUsage = conf.getString("commands.gender.usage", genderCommandUsage) ?: genderCommandUsage
        genderCommandNoPlayer = conf.getString("commands.gender.no-player", genderCommandNoPlayer) ?: genderCommandNoPlayer
        genderCommandResponse = conf.getString("commands.gender.response", genderCommandResponse) ?: genderCommandResponse
        playerCommandUsage = conf.getString("commands.player.usage", playerCommandUsage) ?: playerCommandUsage
        playerCommandNoPlayer = conf.getString("commands.player.no-player", playerCommandNoPlayer) ?: playerCommandNoPlayer
        playerCommandResponse = conf.getString("commands.player.response", playerCommandResponse) ?: playerCommandResponse
        
        // Добавляем загрузку новой команды списка команд
        enabledCommandsListCommand = conf.getBoolean("commands.cmd_list.enabled", true)
        commandsListResponse = conf.getString("commands.cmd_list.response", commandsListResponse) ?: commandsListResponse
        
        // Загружаем переводы для gender если они есть в конфиге
        val genderTranslationsSection = conf.getConfigurationSection("commands.gender.translations")
        if (genderTranslationsSection != null) {
            val translations = mutableMapOf<String, String>()
            for (key in genderTranslationsSection.getKeys(false)) {
                val translation = genderTranslationsSection.getString(key)
                if (translation != null) {
                    translations[key] = translation
                }
            }
            if (translations.isNotEmpty()) {
                genderTranslations = translations
            }
        }
        
        // Загружаем переводы для статусов
        val statusTranslationsSection = conf.getConfigurationSection("status")
        if (statusTranslationsSection != null) {
            val translations = mutableMapOf<String, String>()
            for (key in statusTranslationsSection.getKeys(false)) {
                val translation = statusTranslationsSection.getString(key)
                if (translation != null) {
                    translations[key] = translation
                }
            }
            if (translations.isNotEmpty()) {
                statusTranslations = translations
            }
        }
        
        // Загружаем настройки игры
        gameEnabled = conf.getBoolean("commands.game.enabled", true)
        gameNotRegisteredMessage = conf.getString("commands.game.not-registered", gameNotRegisteredMessage) ?: gameNotRegisteredMessage
        gameStartMessage = conf.getString("commands.game.start", gameStartMessage) ?: gameStartMessage
        gameCorrectMessage = conf.getString("commands.game.correct", gameCorrectMessage) ?: gameCorrectMessage
        gameIncorrectMessage = conf.getString("commands.game.incorrect", gameIncorrectMessage) ?: gameIncorrectMessage
        gameOverMessage = conf.getString("commands.game.game-over", gameOverMessage) ?: gameOverMessage
        gameAlreadyPlayingMessage = conf.getString("commands.game.already-playing", gameAlreadyPlayingMessage) ?: gameAlreadyPlayingMessage
        gameAutoDeleteSeconds = conf.getInt("commands.game.auto-delete-seconds", 15)
        
        // Commands auto-delete timeout
        commandsAutoDeleteSeconds = conf.getInt("commands.auto-delete-seconds", 30)
        
        // Console channel settings
        consoleChannelEnabled = conf.getBoolean("console-channel.enabled", true)
        playerCommandLogEnabled = conf.getBoolean("console-channel.player-command-log.enabled", true)
        playerCommandLogFormat = conf.getString("console-channel.player-command-log.format", playerCommandLogFormat) ?: playerCommandLogFormat
        consoleCommandFeedbackEnabled = conf.getBoolean("console-channel.command-feedback.enabled", true)
        consoleCommandFeedback = conf.getString("console-channel.command-feedback.success", consoleCommandFeedback) ?: consoleCommandFeedback
        consoleCommandError = conf.getString("console-channel.command-feedback.error", consoleCommandError) ?: consoleCommandError
        consoleAutoDeleteSeconds = conf.getInt("console-channel.auto-delete-seconds", 30)
        
        // Console whitelist commands
        whitelistAddSuccess = conf.getString("console-channel.whitelist-commands.add-success", whitelistAddSuccess) ?: whitelistAddSuccess
        whitelistRemoveSuccess = conf.getString("console-channel.whitelist-commands.remove-success", whitelistRemoveSuccess) ?: whitelistRemoveSuccess
        whitelistAddError = conf.getString("console-channel.whitelist-commands.add-error", whitelistAddError) ?: whitelistAddError
        whitelistRemoveError = conf.getString("console-channel.whitelist-commands.remove-error", whitelistRemoveError) ?: whitelistRemoveError
        whitelistOn = conf.getString("console-channel.whitelist-commands.whitelist-on", whitelistOn) ?: whitelistOn
        whitelistOff = conf.getString("console-channel.whitelist-commands.whitelist-off", whitelistOff) ?: whitelistOff
        whitelistListHeader = conf.getString("console-channel.whitelist-commands.list-header", whitelistListHeader) ?: whitelistListHeader
        whitelistListEmpty = conf.getString("console-channel.whitelist-commands.list-empty", whitelistListEmpty) ?: whitelistListEmpty
        whitelistListEntry = conf.getString("console-channel.whitelist-commands.list-entry", whitelistListEntry) ?: whitelistListEntry
        
        // Console plugin commands
        pluginCommandSuccess = conf.getString("console-channel.plugin-commands.success", pluginCommandSuccess) ?: pluginCommandSuccess
        pluginCommandError = conf.getString("console-channel.plugin-commands.error", pluginCommandError) ?: pluginCommandError
        pluginTelegramInfo = conf.getString("console-channel.plugin-commands.telegram", pluginTelegramInfo) ?: pluginTelegramInfo
        pluginAddChannelSuccess = conf.getString("console-channel.plugin-commands.addchannel", pluginAddChannelSuccess) ?: pluginAddChannelSuccess
        pluginAddPlayerSuccess = conf.getString("console-channel.plugin-commands.addplayer", pluginAddPlayerSuccess) ?: pluginAddPlayerSuccess
        pluginRemovePlayerSuccess = conf.getString("console-channel.plugin-commands.removeplayer", pluginRemovePlayerSuccess) ?: pluginRemovePlayerSuccess
        
        // Новые команды плагина
        pluginReloadSuccess = conf.getString("console-channel.plugin-commands.reload-success", pluginReloadSuccess) ?: pluginReloadSuccess
        pluginUnregisterSuccess = conf.getString("console-channel.plugin-commands.unregister-success", pluginUnregisterSuccess) ?: pluginUnregisterSuccess
        pluginUnregisterNotRegistered = conf.getString("console-channel.plugin-commands.unregister-not-registered", pluginUnregisterNotRegistered) ?: pluginUnregisterNotRegistered
        pluginHiddenEmpty = conf.getString("console-channel.plugin-commands.hidden-empty", pluginHiddenEmpty) ?: pluginHiddenEmpty
        pluginHiddenHeader = conf.getString("console-channel.plugin-commands.hidden-header", pluginHiddenHeader) ?: pluginHiddenHeader
        
        // Whitelist команды плагина
        pluginWhitelistAddSuccess = conf.getString("console-channel.plugin-commands.whitelist-add-success", pluginWhitelistAddSuccess) ?: pluginWhitelistAddSuccess
        pluginWhitelistAddAlready = conf.getString("console-channel.plugin-commands.whitelist-add-already", pluginWhitelistAddAlready) ?: pluginWhitelistAddAlready
        pluginWhitelistRemoveSuccess = conf.getString("console-channel.plugin-commands.whitelist-remove-success", pluginWhitelistRemoveSuccess) ?: pluginWhitelistRemoveSuccess
        pluginWhitelistRemoveNotFound = conf.getString("console-channel.plugin-commands.whitelist-remove-not-found", pluginWhitelistRemoveNotFound) ?: pluginWhitelistRemoveNotFound
        pluginWhitelistListEmpty = conf.getString("console-channel.plugin-commands.whitelist-list-empty", pluginWhitelistListEmpty) ?: pluginWhitelistListEmpty
        pluginWhitelistListHeader = conf.getString("console-channel.plugin-commands.whitelist-list-header", pluginWhitelistListHeader) ?: pluginWhitelistListHeader
        pluginWhitelistOnSuccess = conf.getString("console-channel.plugin-commands.whitelist-on-success", pluginWhitelistOnSuccess) ?: pluginWhitelistOnSuccess
        pluginWhitelistOffSuccess = conf.getString("console-channel.plugin-commands.whitelist-off-success", pluginWhitelistOffSuccess) ?: pluginWhitelistOffSuccess
        
        // Blacklist команды плагина
        pluginBlacklistAddSuccess = conf.getString("console-channel.plugin-commands.blacklist-add-success", pluginBlacklistAddSuccess) ?: pluginBlacklistAddSuccess
        pluginBlacklistAddAlready = conf.getString("console-channel.plugin-commands.blacklist-add-already", pluginBlacklistAddAlready) ?: pluginBlacklistAddAlready
        pluginBlacklistRemoveSuccess = conf.getString("console-channel.plugin-commands.blacklist-remove-success", pluginBlacklistRemoveSuccess) ?: pluginBlacklistRemoveSuccess
        pluginBlacklistRemoveNotFound = conf.getString("console-channel.plugin-commands.blacklist-remove-not-found", pluginBlacklistRemoveNotFound) ?: pluginBlacklistRemoveNotFound
        pluginBlacklistListEmpty = conf.getString("console-channel.plugin-commands.blacklist-list-empty", pluginBlacklistListEmpty) ?: pluginBlacklistListEmpty
        pluginBlacklistListHeader = conf.getString("console-channel.plugin-commands.blacklist-list-header", pluginBlacklistListHeader) ?: pluginBlacklistListHeader
        pluginBlacklistOnSuccess = conf.getString("console-channel.plugin-commands.blacklist-on-success", pluginBlacklistOnSuccess) ?: pluginBlacklistOnSuccess
        pluginBlacklistOffSuccess = conf.getString("console-channel.plugin-commands.blacklist-off-success", pluginBlacklistOffSuccess) ?: pluginBlacklistOffSuccess
        
        // Help команда плагина
        pluginHelpMessage = conf.getString("console-channel.plugin-commands.help-message", pluginHelpMessage) ?: pluginHelpMessage
        
        // Register channel settings
        registerChannelEnabled = conf.getBoolean("register-channel.enabled", true)
        registerInvalidUsername = conf.getString("register-channel.invalid-username", registerInvalidUsername) ?: registerInvalidUsername
        registerAlreadyRegistered = conf.getString("register-channel.already-registered", registerAlreadyRegistered) ?: registerAlreadyRegistered
        registerPlayerOffline = conf.getString("register-channel.player-offline", registerPlayerOffline) ?: registerPlayerOffline
        registerSuccess = conf.getString("register-channel.success", registerSuccess) ?: registerSuccess
        registerSuccessInGame = conf.getString("register-channel.success-in-game", registerSuccessInGame) ?: registerSuccessInGame
        registerCodeInvalid = conf.getString("register-channel.code-invalid", registerCodeInvalid) ?: registerCodeInvalid
        registerCodeSuccess = conf.getString("register-channel.code-success", registerCodeSuccess) ?: registerCodeSuccess
        registerRewardCommands = conf.getStringList("register-channel.reward-commands").ifEmpty { 
            listOf("eco give %player% 50") 
        }
        
        // Plugin settings
        pluginPrefix = conf.getString("plugin.prefix", pluginPrefix) ?: pluginPrefix
        telegramLink = conf.getString("plugin.telegram-link", telegramLink) ?: telegramLink
        telegramCommandMessage = conf.getString("plugin.telegram-command-message", telegramCommandMessage) ?: telegramCommandMessage
        telegramClickText = conf.getString("plugin.telegram-click-text", telegramClickText) ?: telegramClickText
        telegramHoverText = conf.getString("plugin.telegram-hover-text", telegramHoverText) ?: telegramHoverText
        noPermissionMessage = conf.getString("plugin.no-permission-message", noPermissionMessage) ?: noPermissionMessage
    }
} 