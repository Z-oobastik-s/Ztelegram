package org.zoobastiks.ztelegram.conf

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.mgr.SchedulerManager
import org.zoobastiks.ztelegram.mgr.RestartManager
import java.io.File

class TConf(private val plugin: ZTele) {
    companion object {
        lateinit var botToken: String
            private set
    }

    /**
     * Конфигурация награды
     */
    data class RewardConfig(
        val name: String,
        val commands: List<String>
    )

    // Debug settings
    var debugEnabled: Boolean = false
    var validationEnabled: Boolean = true
    
    // Database settings
    var databaseEnabled: Boolean = true // Включить SQLite базу данных вместо YAML файлов

    // Command settings - unreg
    var enabledUnregCommand: Boolean = true
    var unregCommandUsage: String = "Использование: /unreg <никнейм> - отменить регистрацию"
    var unregCommandSuccess: String = "✅ Регистрация игрока %player% отменена"
    var unregCommandNotRegistered: String = "❌ Игрок %player% не зарегистрирован"
    var unregCommandCooldown: String = "⏰ Вы можете отменить регистрацию только раз в 12 часов. Осталось: %time%"
    var unregCommandNoPermission: String = "\n\nВам эта команда\nНе доступна\n\nДля получения списка команд\nИспользуйте /help\n"
    var unregCommandCooldownHours: Int = 12

    // Command settings - list
    var enabledListCommand: Boolean = true
    var listCommandHeader: String = "📋 **Зарегистрированные игроки:**"
    var listCommandEntry: String = "• %player% (ID: %telegram_id%)"
    var listCommandEmpty: String = "❌ Нет зарегистрированных игроков"
    var listCommandFooter: String = "Всего зарегистрировано: %count%"

    // Command settings - random (roulette)
    var enabledRandomCommand: Boolean = true
    var randomCommandCooldownMinutes: Int = 5
    var randomCommandCooldown: String = "⏰ **Подождите**\n❌ Повторное использование доступно через: %time%"
    var randomCommandNoPlayers: String = "❌ **Невозможно провести рулетку**\n👥 На сервере недостаточно игроков онлайн"
    var randomCommandOnlyOnePlayer: String = "❌ **Невозможно провести рулетку**\n👤 На сервере только один игрок"
    var randomCommandError: String = "❌ **Ошибка при выполнении рулетки**\nПопробуйте позже"
    var randomCommandWinTelegram: String = ""
    var randomCommandBroadcastCommand: String = "bc &a[Рулетка] &e%player% &aвыиграл награду!"
    var randomCommandRewards: List<String> = emptyList()
    var randomCommandRewardDescriptions: List<String> = emptyList()

    // Menu settings
    var menuEnabled: Boolean = true
    var menuAutoCloseSeconds: Int = 300 // 5 минут по умолчанию
    
    // Rate limiting для защиты от спама
    var menuRateLimitMaxClicks: Int = 100 // Максимум нажатий за окно времени
    var menuRateLimitTimeWindowSeconds: Int = 30 // Окно времени в секундах
    var menuRateLimitBlockSeconds: Int = 10 // Время блокировки при превышении лимита
    var menuMainText: String = "📱 **ГЛАВНОЕ МЕНЮ**\n\nПривет, %user%! Выберите действие:"
    var menuRandomText: String = "🎲 **РУЛЕТКА**\n\nПривет, %user%!\n\nЗапустите рулетку и выиграйте случайную награду!"
    var menuStatsText: String = "📊 **СТАТИСТИКА**\n\nПривет, %user%!\n\nВыберите тип статистики:"
    var menuSettingsText: String = "⚙️ **НАСТРОЙКИ**\n\nПривет, %user%!\n\nНастройки будут доступны в будущих обновлениях."
    var menuInfoText: String = "ℹ️ **ИНФОРМАЦИЯ**\n\nПривет, %user%!\n\nВыберите раздел:"
    var menuInfoLinksText: String = "🔗 **ССЫЛКИ**\n\n📱 Telegram: https://t.me/ReZoobastik\n🖥️ IP сервера: Zoobastiks.20tps.name"
    var menuInfoServerText: String = "🖥️ **ИНФОРМАЦИЯ О СЕРВЕРЕ**\n\n📌 Версия сервера: `1.21.8`\n🎮 Поддерживает вход с: `1.16 - 1.21.11`\n🎤 Версия голосового мода: `voicechat-2.6.7`\n\n🌐 **Ссылки:**\n📱 Сайт сервера: https://z-oobastik-s.github.io/MySiteDev/index.html\n💬 Дискорд: https://discord.com/invite/g462MJEm3H\n\n👤 **Связь с владельцем:**\n@Zoobastiks или @yajobs"
    var menuErrorBlocked: String = "❌ Вы заблокированы в чате"
    var menuErrorNotRegistered: String = "❌ Вы не зарегистрированы. Используйте канал регистрации"
    var menuErrorNotOwner: String = "❌ Это меню принадлежит другому пользователю"
    var menuErrorGeneral: String = "❌ Произошла ошибка. Попробуйте позже"
    var menuRandomCooldownInfo: String = "⏳ Осталось времени: %time%"
    var menuPlayerText: String? = null
    var menuRepText: String? = null
    var menuRestartText: String? = null
    
    // Staff list settings
    data class StaffPlayer(
        val rank: String,
        val telegram: String,
        val telegramId: Long,
        val name: String,
        val nickname: String,
        val actions: List<StaffAction>
    )
    
    data class StaffAction(
        val type: String, // "write", "ticket", "info"
        val enabled: Boolean
    )
    
    var staffListEnabled: Boolean = true
    var staffListHeaderText: String = "👥 **СПИСОК АДМИНИСТРАЦИИ СЕРВЕРА**\n\n"
    var staffListPlayerFormat: String = "%rank%: %nickname%"
    var staffListPlayerDetailFormat: String = "%rank%\nТелеграм - @%telegram%\nИмя - %name%\nНикнейм в игре - %nickname%"
    var staffListButtonWrite: String = "✉️ Написать"
    var staffListButtonTicket: String = "🎫 Создать тикет"
    var staffListButtonInfo: String = "ℹ️ Информация"
    var staffListPlayers: List<StaffPlayer> = emptyList()

    // Payment settings
    var paymentEnabled: Boolean = true
    var paymentMinAmount: Double = 1.0
    var paymentMaxAmount: Double = 0.0 // 0 = без ограничений
    var paymentBroadcastCommand: String = "bc" // Команда для оповещения в игре
    var paymentBroadcastMessage: String = "%from_player% перевёл %amount% %currency% игроку %to_player%"
    var menuPaymentText: String = "💸 **ПЕРЕВОД ДЕНЕГ**\n\nПривет, %user%!\n\nВыберите действие:"
    var menuPaymentHistoryText: String = "📜 **ИСТОРИЯ ПЕРЕВОДОВ**\n\nПривет, %user%!\n\nВаша история переводов:"
    var paymentCommandUsage: String = "Использование: /pay <ник_игрока> <сумма>"
    var paymentCommandNotRegistered: String = "❌ Вы не зарегистрированы. Используйте канал регистрации"
    var paymentCommandVaultNotFound: String = "❌ Экономика недоступна. Плагин Vault не найден."
    var paymentCommandInvalidAmount: String = "❌ Неверный формат суммы. Используйте число (например: 100 или 100.5)"
    var paymentCommandSuccess: String = "✅ Перевод выполнен!\n\n💰 От: **%from_player%**\n💰 Кому: **%to_player%**\n💵 Сумма: **%amount%** %currency%\n💳 Ваш баланс: **%balance%** %currency%"
    var paymentCommandErrorWithdraw: String = "❌ Ошибка при списании средств: %error%"
    var paymentCommandErrorDeposit: String = "❌ Ошибка при зачислении средств: %error%"
    var paymentCommandErrorGeneral: String = "❌ Произошла ошибка при выполнении перевода."
    var paymentCommandErrorMinAmount: String = "❌ Минимальная сумма перевода: %min_amount% %currency%"
    var paymentCommandErrorMaxAmount: String = "❌ Максимальная сумма перевода: %max_amount% %currency%"
    var paymentCommandErrorSamePlayer: String = "❌ Нельзя переводить деньги самому себе."
    var paymentCommandErrorPlayerNotFound: String = "❌ Игрок **%player%** не найден."
    var paymentCommandErrorInsufficientFunds: String = "❌ Недостаточно средств. Ваш баланс: %balance% %currency%"

    // Contextual help
    var helpMain: String = ""
    var helpRegister: String = ""
    var helpGame: String = ""
    var helpStatistics: String = ""
    var helpReputation: String = ""
    var helpConsole: String = ""

    // Channel IDs
    var mainChannelId: String = "-1002111043217"
    var consoleChannelId: String = "-1002656200279"
    var registerChannelId: String = "-1002611802353"
    var gameChannelId: String = ""
    var statisticsChannelId: String = ""

    // Administrators
    var administratorIds: List<Long> = emptyList()

    // Scheduler settings
    var schedulerEnabled: Boolean = true
    var schedulerTimezone: String = "Europe/Moscow"
    var schedulerDailyTasks: Map<String, SchedulerManager.SchedulerTaskConfig> = emptyMap()
    var schedulerLoggingConsole: Boolean = true
    var schedulerLoggingTelegram: Boolean = true

    // Restart command settings - immediate
    var restartImmediateMessage: String = "🔄 **Перезагрузка сервера**\nИнициирована мгновенная перезагрузка сервера..."
    var restartImmediateResponse: String = "⚠️ **Сервер перезагружается...**"
    var restartImmediateCommand: String = "restart"

    // Новая система рестарта - Telegram сообщения
    var restartTelegramTimerStarted: String = "⏰ **Таймер рестарта запущен**\n🕐 Сервер будет перезагружен через **%time%**\n👤 Инициатор: %admin%"
    var restartTelegramInvalidFormat: String = "❌ **Неверный формат времени!**\nИспользуйте: `/restart 5m` или `/restart 10m`\nДопустимый диапазон: %min%-%max% минут"
    var restartTelegramTimeRangeError: String = "❌ **Время вне допустимого диапазона!**\nМинимум: %min% минут, Максимум: %max% минут"
    var restartTelegramTimerCancelled: String = "🚫 **Таймер рестарта отменен**\n👤 Администратором: %admin%"
    var restartTelegramTimerActive: String = "⏰ **Таймер уже активен!**\nОсталось времени: **%remaining%**\nИспользуйте `/restart cancel` для отмены"
    var restartTelegramCancelSuccess: String = "✅ **Запланированный рестарт успешно отменен**\n👤 Администратор: %admin%"
    var restartTelegramCancelNoRestart: String = "❌ **Нет активного запланированного рестарта**"

    // Новая система рестарта - Серверные команды
    var restartServerTimerStarted: String = "bc &c[РЕСТАРТ] &fСервер будет перезагружен через &e%time%&f! Подготовьтесь!"
    var restartServerFinalCommand: String = "bc &c[РЕСТАРТ] &fСервер перезагружается..."
    var restartServerTimerCancelled: String = "bc &a[РЕСТАРТ] &fТаймер перезагрузки отменен администратором!"

    // Команды рестарта
    var restartCommand: String = "restart"
    var restartPreCommands: List<String> = listOf("save-all", "kick @a &cСервер перезагружается! Подключайтесь через минуту.")

    // Предупреждения по времени
    var restartWarningMinutes: List<RestartManager.WarningConfig> = emptyList()
    var restartWarningSeconds: List<RestartManager.WarningConfig> = emptyList()

    // Фильтрация сообщений
    var messageFilterEnabled: Boolean = true
    var messageFilterWhitelistUsers: List<Long> = emptyList()
    var messageFilterBlockBots: Boolean = true
    var messageFilterMaxLength: Int = 500


    // Main channel settings
    var mainChannelEnabled: Boolean = true
    var mainChannelChatEnabled: Boolean = true
    var formatTelegramToMinecraft: String = "&b[Telegram] &f%player%: &7%message%"
    var formatMinecraftToTelegram: String = "📤 **%player%**: %message%"

    // Новые настройки чата
    var chatMinecraftToTelegramEnabled: Boolean = true
    var chatMinecraftToTelegramFormat: String = "〔%player%〕 %message%"
    var chatTelegramToMinecraftEnabled: Boolean = true
    var chatTelegramToMinecraftFormat: String = "〔Телеграм〕%username%: %message%"
    var chatPlayerChatEnabled: Boolean = true

    // События игроков
    var chatPlayerJoinEnabled: Boolean = true
    var chatPlayerJoinMessage: String = "➕ **%player%** зашёл на сервер"
    var chatPlayerQuitEnabled: Boolean = true
    var chatPlayerQuitMessage: String = "➖ **%player%** покинул сервер"
    var chatPlayerDeathEnabled: Boolean = true
    var chatPlayerDeathMessage: String = "💀 **%player%** %reason%"
    var chatPlayerDeathUseRussianMessages: Boolean = true
    var chatPlayerDeathDebugMessages: Boolean = false

    // Белый и черный списки в чате
    var chatWhitelistEnabled: Boolean = true
    var chatWhitelistNoRegistrationMessage: String = "❌ Вы не можете здесь написать.\\n\\n✅ Для регистрации вашего ника\\n🛜 Перейдите в тему https://t.me/ReZoobastik/309520\\n🌍 Введите там свой ник без пробелов и слэшей.\\n\\n❤️‍🔥 За регистрацию никнейма выдается награда.\\n💯 Она начисляется автоматически на ваш никнейм в игре."
    var chatBlacklistEnabled: Boolean = true
    var chatBlacklistBlockedMessage: String = "❌ Вы заблокированы и не можете отправлять сообщения на сервер!"

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
    var linkCodeMessage: String = ""
    var linkCodeInstruction: String = ""
    var linkCodeExpiration: String = ""
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
    var restartCommandMessage: String = "🔄 Сервер перезагружается..."
    var enabledGenderCommand: Boolean = true
    var enabledPlayerCommand: Boolean = true
    var enabledStatsCommand: Boolean = true
    var enabledTopCommand: Boolean = true
    var enabledTopBalCommand: Boolean = true

    // Telegram command responses
    var onlineCommandResponse: String = "🌍 **Онлайн:** %online%/%max%\n👫 **Список игроков:** %players%\n🖥 **IP сервера:** `Zoobastiks.20tps.name`"
    var onlineCommandNoPlayers: String = "🏜️ **Сервер пустой**\nНа сервере никого нет"
    var onlineCommandHeader: String = "🎮 **Игроки онлайн** (%count%/%max%)"
    var onlineCommandPlayerFormat: String = "👤 `%player%`"
    var onlineCommandSeparator: String = "\n"
    var onlineCommandFooter: String = "\n🕐 Обновлено: %time%"
    var tpsCommandResponse: String = "🖥️ **Производительность сервера:** `%tps%`"
    var tpsCommandMessage: String = "⚡ **Производительность сервера**\n\n📊 TPS: `%tps%`\n🎯 Статус: %status%\n🕐 Время: `%time%`"
    var tpsStatusExcellent: String = "🟢 Отлично"
    var tpsStatusGood: String = "🟡 Хорошо"
    var tpsStatusPoor: String = "🟠 Плохо"
    var tpsStatusCritical: String = "🔴 Критично"
    var restartCommandResponse: String = "⚠️ Server is restarting..."
    var genderCommandUsage: String = "Usage: /gender [man/girl]"
    var genderCommandNoPlayer: String = "You need to register your nickname first!"
    var genderCommandResponse: String = "Gender for %player% set to %gender%"
    var playerCommandUsage: String = "Usage: /player <nickname>"
    var playerCommandNoPlayer: String = "Player %player% not found"
    var playerCommandResponse: String = "Player: %player%\nOnline: %online%\nHealth: %health%\nGender: %gender%\nRegistered: %registered%\nFirst played: %first_played%\nDeaths: %deaths%\nLevel: %level%\nBalance: %balance%\nCoordinates: %coords%"

    // Stats command
    var statsCommandUsage: String = "Использование: /stats [1h|1d|1w|1m]"
    var statsNoPlayers: String = "❌ За указанный период не было игроков."
    var statsMessage: String = """📊 **Статистика за %period%**

👥 Уникальных игроков: **%unique_count%**
📋 Список игроков:
%players%"""
    var statsHeader: String = "📊 Статистика игроков за %period%:"
    var statsEntry: String = " • %player%"
    var statsFooter: String = "Всего игроков: %count%"

    // Top command (playtime)
    var topCommandUsage: String = "Использование: /top [1h|1d|1w|1m]"
    var topNoData: String = "❌ За указанный период нет данных о времени игры."
    var topMessage: String = """🏆 **Топ-10 по времени игры** (%period%)

🥇 `%player_1%` » **%time_1%**
🥈 `%player_2%` » **%time_2%**
🥉 `%player_3%` » **%time_3%**
④ `%player_4%` » **%time_4%**
⓹ `%player_5%` » **%time_5%**
⓺ `%player_6%` » **%time_6%**
⓻ `%player_7%` » **%time_7%**
⓼ `%player_8%` » **%time_8%**
⓽ `%player_9%` » **%time_9%**
⓾ `%player_10%` » **%time_10%**"""

    // TopBal command
    var topBalMessage: String = """💰 **Топ-10 игроков по балансу**

🥇 `%player_1%` » **%balance_1%** ⛃
🥈 `%player_2%` » **%balance_2%** ⛃
🥉 `%player_3%` » **%balance_3%** ⛃
④ `%player_4%` » **%balance_4%** ⛃
⓹ `%player_5%` » **%balance_5%** ⛃
⓺ `%player_6%` » **%balance_6%** ⛃
⓻ `%player_7%` » **%balance_7%** ⛃
⓼ `%player_8%` » **%balance_8%** ⛃
⓽ `%player_9%` » **%balance_9%** ⛃
⓾ `%player_10%` » **%balance_10%** ⛃

💎 *Самые богатые игроки сервера — топ-%count%*"""
    var topBalNoData: String = "❌ Нет данных о балансах игроков."
    var topBalError: String = "❌ Ошибка при получении данных о балансах."

    // Auto notifications settings
    var autoNotificationsEnabled: Boolean = true
    var autoNotificationsTimezone: String = "Europe/Moscow"

    // Playtime top auto notifications
    var playtimeTopAutoEnabled: Boolean = true
    var playtimeTopAutoPeriod: String = "1d"
    var playtimeTopAutoSchedule: String = "12:00,20:00"
    var playtimeTopAutoTitle: String = "🏆 **ЕЖЕДНЕВНЫЙ ТОП АКТИВНОСТИ** 🏆"
    var playtimeTopAutoFooter: String = "⏰ *Обновляется автоматически каждый день*"
    var playtimeTopAutoDeleteSeconds: Int = 300

    // Playtime top exclude settings
    var playtimeTopExcludeEnabled: Boolean = true
    var playtimeTopExcludePermissions: List<String> = listOf("group.admin", "group.moderator", "ztelegram.top.exclude")

    // Playtime top rewards
    var playtimeTopRewardsEnabled: Boolean = true
    var playtimeTopRewardsList: List<RewardConfig> = emptyList()
    var playtimeTopRewardsNotification: String = "👑 **%player%** получил награду за 1-е место!\n🎁 Награда: **%reward_name%**\n⏱ Время игры: **%time%**"
    var playtimeTopRewardsNotificationAutoDeleteSeconds: Int = 60

    // Balance top auto notifications
    var balanceTopAutoEnabled: Boolean = true
    var balanceTopAutoSchedule: String = "18:00"
    var balanceTopAutoTitle: String = "💰 **ЕЖЕДНЕВНЫЙ ТОП БОГАЧЕЙ** 💰"
    var balanceTopAutoFooter: String = "💎 *Самые успешные игроки сервера*"
    var balanceTopAutoDeleteSeconds: Int = 300

    // Balance top exclude settings
    var balanceTopExcludeEnabled: Boolean = true
    var balanceTopExcludePermissions: List<String> = listOf("group.admin", "group.moderator", "ztelegram.top.exclude")

    // Balance top rewards
    var balanceTopRewardsEnabled: Boolean = true
    var balanceTopRewardsList: List<RewardConfig> = emptyList()
    var balanceTopRewardsNotification: String = "👑 **%player%** получил награду за богатство!\n🎁 Награда: **%reward_name%**\n💰 Баланс: **%balance%** ⛃"
    var balanceTopRewardsNotificationAutoDeleteSeconds: Int = 60

    // Daily summary settings
    var dailySummaryEnabled: Boolean = true
    var dailySummaryTime: String = "23:59"
    var dailySummaryTimezone: String = "Europe/Moscow"
    var dailySummaryMessage: String = """📈 **Ежедневная сводка сервера**

📅 Дата: %date%
👥 Уникальных игроков: **%unique_players%**
📊 Пиковый онлайн: **%peak_online%**

🏆 **Топ-5 по активности:**
%top_playtime%

💰 **Топ-3 богачей:**
%top_balance%

🎮 Играйте больше и попадите в завтрашний топ!"""

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
        <gradient:#4CAF50:#8BC34A>• /stats [1h|1d|1w|1m], /статистика [1ч|1д|1н|1м]</gradient> - статистика игроков
        <gradient:#4CAF50:#8BC34A>• /top [1h|1d|1w|1m], /топ [1ч|1д|1н|1м]</gradient> - топ по времени игры
        <gradient:#4CAF50:#8BC34A>• /topbal, /топбал</gradient> - топ игроков по балансу

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

    // Stats period translations
    var statsTranslations: Map<String, String> = mapOf(
        "h" to "час",
        "d" to "день",
        "w" to "неделю",
        "m" to "месяц"
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

    // Game settings from game.yml
    var gameEnabled: Boolean = true
    var gameCommandEnabled: Boolean = true
    var gameAutoDeleteSeconds: Int = 0

    // Game process settings
    var gameTimeoutSeconds: Int = 60
    var gameMinWordLength: Int = 4
    var gameMaxWordLength: Int = 10
    var gameCooldownSeconds: Int = 30
    var gameMaxConcurrentGames: Int = 10

    // Game rewards
    var gameBaseReward: Int = 5
    var gameSpeedBonus: Int = 1
    var gameMaxBonus: Int = 10
    var gameRewardCommands: List<String> = listOf(
        "eco give %player% %reward%",
        "broadcast 🎉 %player% выиграл в игру \"Угадай слово\" и получил %reward% монет!"
    )

    // Game messages
    var gameMessageStart: String = """🎮 **Игра "Угадай слово" началась!**

🎯 **Угадайте слово:** `%word_hint%`
🔤 **Букв в слове:** %length%
⏱️ **Время:** %time% секунд
💰 **Награда:** %base_reward%+ монет

✏️ Напишите ваш ответ в чат!"""
    var gameMessageWin: String = """🎉 **Поздравляем с победой!**

✅ **Правильный ответ:** `%word%`
⚡ **Время ответа:** %answer_time% сек
💰 **Базовая награда:** %base_reward% монет
🚀 **Бонус за скорость:** +%speed_bonus% монет
💎 **Итого получено:** %total_reward% монет
📊 **Ваша статистика:** %wins%/%total_games% побед"""
    var gameMessageLose: String = """😢 **Время вышло!**

💡 **Правильное слово:** `%word%`
🔄 **Попробуйте снова:** используйте `/game`
📊 **Ваша статистика:** %wins%/%total_games% побед"""
    var gameMessageAlreadyPlaying: String = """❌ **Вы уже играете!**

⏳ Сначала завершите текущую игру
⚡ Или дождитесь окончания времени"""
    var gameMessageNotRegistered: String = """❌ **Регистрация требуется!**

📝 Сначала зарегистрируйтесь в канале регистрации
✅ Используйте команду `/reg ваш_ник`"""
    var gameMessagePlayerNotFound: String = """❌ **Игрок не найден!**

👤 Игрок `%player%` не найден на сервере
✏️ Проверьте правильность написания ника"""
    var gameMessageCooldown: String = """⏰ **Подождите немного!**

🕐 Следующая игра доступна через: %time%
💡 Это поможет другим игрокам тоже поиграть"""
    var gameMessageTooManyGames: String = """🚫 **Сервер занят!**

🎮 Сейчас идет максимальное количество игр
⏳ Попробуйте через несколько минут"""

    // Commands auto-delete timeout
    var commandsAutoDeleteSeconds: Int = 30

    // Error messages
    var errorsNoAdminPermission: String = "❌ **Команда недоступна.**\n🚷 Вы не являетесь администратором.\n\n❤️ IP сервера: `Zoobastiks.20tps.name`\n\n✏️ Просмотр команд  » `/help`"
    var errorsCommandNotAllowed: String = "❌ **Команда недоступна в этом канале.**\n📍 Используйте соответствующий канал для этой команды.\n\n❤️ IP сервера: `Zoobastiks.20tps.name`\n\n✏️ Просмотр команд  » `/help`"
    var errorsUnregNoPermission: String = "❌ **Отмена регистрации недоступна.**\n🚷 Вы можете отменить только **свою** регистрацию.\n\n❤️ IP сервера: `Zoobastiks.20tps.name`\n\n✏️ Просмотр команд  » `/help`"

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
    var registerInvalidUsername: String = "❌ Неверный никнейм: %player%\n📝 Разрешены только английские буквы, цифры и символ _"
    var registerAlreadyRegistered: String = "❌ Никнейм %player% уже зарегистрирован\n❤️ Айпи сервера: your-server.com"
    var registerUserAlreadyRegistered: String = "❌ Вы уже зарегистрированы с именем %player%"
    var registerPlayerOffline: String = "❌ Игрок %player% не в сети\n❌ Зайдите на сервер под этим никнеймом\n✅ И повторите попытку еще раз."
    var registerSuccess: String = "✅ Ваш ник %player% зарегистрирован.\n🎁 Награда: 50 монет\n♻️ Награда: Получена"
    var registerSuccessInGame: String = "<gradient:#FF0000:#A6EB0F>〔Телеграм〕</gradient> <hover:show_text:\"Аккаунт привязан к Telegram\"><gradient:#A6EB0F:#00FF00>Ваш аккаунт успешно привязан!</gradient></hover>"
    var registerRewardCommands: List<String> = listOf("eco give %player% 50", "broadcast &b%player% &eзарегистрировал свой аккаунт в Telegram")
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
    private var playersConfig: YamlConfiguration

    init {
        plugin.saveDefaultConfig()
        loadConfig()
        // Создаем players.yml только если база данных отключена
        if (!databaseEnabled && !playersFile.exists()) {
            plugin.saveResource("players.yml", false)
        }

        // Загружаем конфиг только если файл существует (для обратной совместимости)
        if (playersFile.exists()) {
        playersConfig = YamlConfiguration.loadConfiguration(playersFile)
        } else {
            playersConfig = YamlConfiguration()
        }

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
        // Загружаем конфиг только если файл существует (для обратной совместимости)
        if (playersFile.exists()) {
        playersConfig = YamlConfiguration.loadConfiguration(playersFile)
        } else {
            playersConfig = YamlConfiguration()
        }
        loadConfig()
    }

    fun getPlayersConfig(): YamlConfiguration {
        return playersConfig
    }

    fun savePlayersConfig() {
        // Не сохраняем players.yml если база данных включена
        if (databaseEnabled) {
            return
        }
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

    // Получение перевода для периода статистики
    fun getStatsTranslation(period: String): String {
        return statsTranslations[period.lowercase()] ?: period
    }

    /**
     * Проверяет, является ли пользователь администратором
     */
    fun isAdministrator(telegramId: Long): Boolean {
        return administratorIds.contains(telegramId)
    }

    private fun loadConfig() {
        val conf = plugin.config

        // Debug settings
        debugEnabled = conf.getBoolean("debug.enabled", false)
        validationEnabled = conf.getBoolean("debug.validation-enabled", true)
        
        // Database settings
        databaseEnabled = conf.getBoolean("database.enabled", true)

        // Bot settings
        botToken = conf.getString("bot.token", "") ?: ""

        // Channel IDs
        mainChannelId = conf.getString("channels.main", "-1002111043217") ?: "-1002111043217"
        consoleChannelId = conf.getString("channels.console", "-1002656200279") ?: "-1002656200279"
        registerChannelId = conf.getString("channels.register", "-1002611802353") ?: "-1002611802353"
        gameChannelId = conf.getString("channels.game", "") ?: ""
        statisticsChannelId = conf.getString("channels.statistics", "") ?: ""

        if (debugEnabled) {
            plugin.logger.info("📢 [TConf] Загружены каналы:")
            plugin.logger.info("   Main: '$mainChannelId'")
            plugin.logger.info("   Console: '$consoleChannelId'")
            plugin.logger.info("   Register: '$registerChannelId'")
            plugin.logger.info("   Game: '$gameChannelId'")
            plugin.logger.info("   Statistics: '$statisticsChannelId'")
        }

        // Administrators
        administratorIds = try {
            // Сначала пробуем getLongList
            val longList = conf.getLongList("administrators.telegram_ids")
            if (longList.isNotEmpty()) {
                longList
            } else {
                // Если не работает, пробуем через getList и конвертируем
                conf.getList("administrators.telegram_ids")?.mapNotNull {
                    when (it) {
                        is Long -> it
                        is Int -> it.toLong()
                        is String -> it.toLongOrNull()
                        else -> null
                    }
                } ?: emptyList()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Ошибка загрузки администраторов: ${e.message}")
            emptyList()
        }

        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Загружены администраторы: $administratorIds")
            plugin.logger.info("🔧 [TConf] Количество администраторов: ${administratorIds.size}")
            plugin.logger.info("🔧 [TConf] Сырые данные из config: ${conf.get("administrators.telegram_ids")}")
        }


        // Command settings - unreg
        enabledUnregCommand = conf.getBoolean("commands.unreg.enabled", true)
        unregCommandUsage = conf.getString("commands.unreg.usage", unregCommandUsage) ?: unregCommandUsage
        unregCommandSuccess = conf.getString("commands.unreg.success", unregCommandSuccess) ?: unregCommandSuccess
        unregCommandNotRegistered = conf.getString("commands.unreg.not_registered", unregCommandNotRegistered) ?: unregCommandNotRegistered
        unregCommandCooldown = conf.getString("commands.unreg.cooldown", unregCommandCooldown) ?: unregCommandCooldown
        unregCommandNoPermission = conf.getString("commands.unreg.no_permission", unregCommandNoPermission) ?: unregCommandNoPermission
        unregCommandCooldownHours = conf.getInt("commands.unreg.cooldown_hours", 12)

        // Command settings - list
        enabledListCommand = conf.getBoolean("commands.list.enabled", true)
        listCommandHeader = conf.getString("commands.list.header", listCommandHeader) ?: listCommandHeader
        listCommandEntry = conf.getString("commands.list.entry", listCommandEntry) ?: listCommandEntry
        listCommandEmpty = conf.getString("commands.list.empty", listCommandEmpty) ?: listCommandEmpty
        listCommandFooter = conf.getString("commands.list.footer", listCommandFooter) ?: listCommandFooter

        // Command settings - random (roulette)
        enabledRandomCommand = conf.getBoolean("commands.random.enabled", true)
        randomCommandCooldownMinutes = conf.getInt("commands.random.cooldown_minutes", 5)
        randomCommandCooldown = conf.getString("commands.random.cooldown", randomCommandCooldown) ?: randomCommandCooldown
        randomCommandNoPlayers = conf.getString("commands.random.no-players", randomCommandNoPlayers) ?: randomCommandNoPlayers
        randomCommandOnlyOnePlayer = conf.getString("commands.random.only-one-player", randomCommandOnlyOnePlayer) ?: randomCommandOnlyOnePlayer
        randomCommandError = conf.getString("commands.random.error", randomCommandError) ?: randomCommandError
        randomCommandWinTelegram = conf.getString("commands.random.win-telegram", randomCommandWinTelegram) ?: randomCommandWinTelegram
        randomCommandBroadcastCommand = conf.getString("commands.random.broadcast-command", randomCommandBroadcastCommand) ?: randomCommandBroadcastCommand
        randomCommandRewards = conf.getStringList("commands.random.rewards").takeIf { it.isNotEmpty() } ?: randomCommandRewards
        randomCommandRewardDescriptions = conf.getStringList("commands.random.reward-descriptions").takeIf { it.isNotEmpty() } ?: randomCommandRewardDescriptions

        // Menu settings
        menuEnabled = conf.getBoolean("commands.menu.enabled", true)
        menuAutoCloseSeconds = conf.getInt("commands.menu.auto-close-seconds", menuAutoCloseSeconds)
        menuRateLimitMaxClicks = conf.getInt("commands.menu.rate-limit.max-clicks", menuRateLimitMaxClicks)
        menuRateLimitTimeWindowSeconds = conf.getInt("commands.menu.rate-limit.time-window-seconds", menuRateLimitTimeWindowSeconds)
        menuRateLimitBlockSeconds = conf.getInt("commands.menu.rate-limit.block-seconds", menuRateLimitBlockSeconds)
        menuMainText = conf.getString("commands.menu.main-text", menuMainText) ?: menuMainText
        menuRandomText = conf.getString("commands.menu.random-text", menuRandomText) ?: menuRandomText
        menuStatsText = conf.getString("commands.menu.stats-text", menuStatsText) ?: menuStatsText
        menuSettingsText = conf.getString("commands.menu.settings-text", menuSettingsText) ?: menuSettingsText
        menuInfoText = conf.getString("commands.menu.info-text", menuInfoText) ?: menuInfoText
        menuInfoLinksText = conf.getString("commands.menu.info-links-text", menuInfoLinksText) ?: menuInfoLinksText
        menuInfoServerText = conf.getString("commands.menu.info-server-text", menuInfoServerText) ?: menuInfoServerText
        menuErrorBlocked = conf.getString("commands.menu.error-blocked", menuErrorBlocked) ?: menuErrorBlocked
        menuErrorNotRegistered = conf.getString("commands.menu.error-not-registered", menuErrorNotRegistered) ?: menuErrorNotRegistered
        menuErrorNotOwner = conf.getString("commands.menu.error-not-owner", menuErrorNotOwner) ?: menuErrorNotOwner
        menuErrorGeneral = conf.getString("commands.menu.error-general", menuErrorGeneral) ?: menuErrorGeneral
        menuRandomCooldownInfo = conf.getString("commands.menu.random-cooldown-info", menuRandomCooldownInfo) ?: menuRandomCooldownInfo
        menuPlayerText = conf.getString("commands.menu.player-text")
        menuRepText = conf.getString("commands.menu.rep-text")
        menuRestartText = conf.getString("commands.menu.restart-text")
        
        // Staff list settings
        staffListEnabled = conf.getBoolean("staff-list.enabled", true)
        // Разрешаем пустые строки для отключения элементов
        staffListHeaderText = conf.getString("staff-list.header-text") ?: "👥 **СПИСОК АДМИНИСТРАЦИИ СЕРВЕРА**\n\n"
        staffListPlayerFormat = conf.getString("staff-list.player-format") ?: "%rank%: %nickname%"
        staffListPlayerDetailFormat = conf.getString("staff-list.player-detail-format") ?: "%rank%\nТелеграм - @%telegram%\nИмя - %name%\nНикнейм в игре - %nickname%"
        staffListButtonWrite = conf.getString("staff-list.button-write") ?: "✉️ Написать"
        staffListButtonTicket = conf.getString("staff-list.button-ticket") ?: "🎫 Создать тикет"
        staffListButtonInfo = conf.getString("staff-list.button-info") ?: "ℹ️ Информация"
        
        // Загружаем список игроков
        val playersList = mutableListOf<StaffPlayer>()
        val playersConfigList = conf.getList("staff-list.players")
        if (playersConfigList != null) {
            for (playerObj in playersConfigList) {
                if (playerObj is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val playerMap = playerObj as Map<String, Any>
                    val rank = (playerMap["rank"] as? String) ?: ""
                    val telegram = (playerMap["telegram"] as? String) ?: ""
                    val telegramId = when (val id = playerMap["telegram_id"]) {
                        is Number -> id.toLong()
                        is String -> id.toLongOrNull() ?: 0L
                        else -> 0L
                    }
                    val name = (playerMap["name"] as? String) ?: ""
                    val nickname = (playerMap["nickname"] as? String) ?: ""
                    
                    // Загружаем действия
                    val actionsList = mutableListOf<StaffAction>()
                    val actionsObj = playerMap["actions"]
                    if (actionsObj is List<*>) {
                        for (actionObj in actionsObj) {
                            if (actionObj is Map<*, *>) {
                                @Suppress("UNCHECKED_CAST")
                                val actionMap = actionObj as Map<String, Any>
                                val type = (actionMap["type"] as? String) ?: ""
                                val enabled = (actionMap["enabled"] as? Boolean) ?: true
                                actionsList.add(StaffAction(type, enabled))
                            }
                        }
                    }
                    
                    playersList.add(StaffPlayer(rank, telegram, telegramId, name, nickname, actionsList))
                }
            }
        }
        staffListPlayers = playersList
        
        // Payment settings
        paymentEnabled = conf.getBoolean("payment.enabled", true)
        paymentMinAmount = conf.getDouble("payment.min-amount", 1.0)
        paymentMaxAmount = conf.getDouble("payment.max-amount", 0.0)
        paymentBroadcastCommand = conf.getString("payment.broadcast-command", "bc") ?: "bc"
        paymentBroadcastMessage = conf.getString("payment.broadcast-message", paymentBroadcastMessage) ?: paymentBroadcastMessage
        menuPaymentText = conf.getString("commands.menu.payment-text", menuPaymentText) ?: menuPaymentText
        menuPaymentHistoryText = conf.getString("commands.menu.payment-history-text", menuPaymentHistoryText) ?: menuPaymentHistoryText
        paymentCommandUsage = conf.getString("payment.command-usage", paymentCommandUsage) ?: paymentCommandUsage
        paymentCommandNotRegistered = conf.getString("payment.command-not-registered", paymentCommandNotRegistered) ?: paymentCommandNotRegistered
        paymentCommandVaultNotFound = conf.getString("payment.command-vault-not-found", paymentCommandVaultNotFound) ?: paymentCommandVaultNotFound
        paymentCommandInvalidAmount = conf.getString("payment.command-invalid-amount", paymentCommandInvalidAmount) ?: paymentCommandInvalidAmount
        paymentCommandSuccess = conf.getString("payment.command-success", paymentCommandSuccess) ?: paymentCommandSuccess
        paymentCommandErrorWithdraw = conf.getString("payment.command-error-withdraw", paymentCommandErrorWithdraw) ?: paymentCommandErrorWithdraw
        paymentCommandErrorDeposit = conf.getString("payment.command-error-deposit", paymentCommandErrorDeposit) ?: paymentCommandErrorDeposit
        paymentCommandErrorGeneral = conf.getString("payment.command-error-general", paymentCommandErrorGeneral) ?: paymentCommandErrorGeneral
        paymentCommandErrorMinAmount = conf.getString("payment.command-error-min-amount", paymentCommandErrorMinAmount) ?: paymentCommandErrorMinAmount
        paymentCommandErrorMaxAmount = conf.getString("payment.command-error-max-amount", paymentCommandErrorMaxAmount) ?: paymentCommandErrorMaxAmount
        paymentCommandErrorSamePlayer = conf.getString("payment.command-error-same-player", paymentCommandErrorSamePlayer) ?: paymentCommandErrorSamePlayer
        paymentCommandErrorPlayerNotFound = conf.getString("payment.command-error-player-not-found", paymentCommandErrorPlayerNotFound) ?: paymentCommandErrorPlayerNotFound
        paymentCommandErrorInsufficientFunds = conf.getString("payment.command-error-insufficient-funds", paymentCommandErrorInsufficientFunds) ?: paymentCommandErrorInsufficientFunds

        // Contextual help

        // Загружаем help сообщения из config.yml или используем значения по умолчанию
        // Пробуем сначала help, потом commands.help (для совместимости)
        helpMain = conf.getString("help.main") ?: conf.getString("commands.help.main") ?: "🤖 **Доступные команды:**\n\n/online - список игроков онлайн\n/tps - производительность сервера\n/stats [период] - статистика игроков\n/top [период] - топ по времени игры\n/topbal - топ по балансу\n/player никнейм - информация об игроке\n/rep - команды репутации\n/restart - перезагрузка сервера (админ)"
        helpRegister = conf.getString("help.register") ?: conf.getString("commands.help.register") ?: "📝 **Команды регистрации:**\n\nваш_никнейм - зарегистрироваться\n/unreg никнейм - отменить регистрацию\n/list - список игроков (админ)"
        helpGame = conf.getString("help.game") ?: conf.getString("commands.help.game") ?: "🎮 **Игровые команды:**\n\n/game - начать игру\n/игра - начать игру"
        helpStatistics = conf.getString("help.statistics") ?: conf.getString("commands.help.statistics") ?: "📊 **Команды статистики:**\n\n/stats [период] - статистика игроков\n/top [период] - топ по времени игры\n/topbal - топ по балансу"
        helpReputation = conf.getString("help.reputation") ?: "⭐ **Команды репутации:**\n\n**Ответьте на сообщение** и используйте:\n• `/rep+` или `/+rep` - дать положительную репутацию\n• `/rep-` или `/-rep` - дать отрицательную репутацию\n• Обязательно укажите причину\n\n**Информация:**\n• `/rep @игрок` - посмотреть репутацию игрока\n• `/rep` - ваша репутация\n• `/reptop` - топ по репутации\n• `/reprecent` - последние изменения репутации"
        helpConsole = conf.getString("help.console") ?: conf.getString("console-channel.plugin-commands.help-message") ?: pluginHelpMessage

        if (debugEnabled) {
            plugin.logger.info("🔄 Loaded help messages:")
            plugin.logger.info("📋 Main help length: ${helpMain.length} chars")
            plugin.logger.info("📋 Main help preview: ${helpMain.take(100)}...")
            plugin.logger.info("📋 Register help length: ${helpRegister.length} chars")
            plugin.logger.info("📋 Game help length: ${helpGame.length} chars")
            plugin.logger.info("📋 Statistics help length: ${helpStatistics.length} chars")
            plugin.logger.info("📋 Console help length: ${helpConsole.length} chars")
        }

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
        linkCodeMessage = conf.getString("plugin.link.code-message", linkCodeMessage) ?: linkCodeMessage
        linkCodeInstruction = conf.getString("plugin.link.code-instruction", linkCodeInstruction) ?: linkCodeInstruction
        linkCodeExpiration = conf.getString("plugin.link.code-expiration", linkCodeExpiration) ?: linkCodeExpiration
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
        restartCommandMessage = conf.getString("commands.restart.message", restartCommandMessage) ?: restartCommandMessage
        enabledGenderCommand = conf.getBoolean("commands.gender.enabled", true)
        enabledPlayerCommand = conf.getBoolean("commands.player.enabled", true)
        enabledStatsCommand = conf.getBoolean("commands.stats.enabled", true)
        enabledTopCommand = conf.getBoolean("commands.top.enabled", true)
        enabledTopBalCommand = conf.getBoolean("commands.topbal.enabled", true)

        // Telegram command responses
        onlineCommandResponse = conf.getString("commands.online.response", onlineCommandResponse) ?: onlineCommandResponse
        onlineCommandNoPlayers = conf.getString("commands.online.no-players", onlineCommandNoPlayers) ?: onlineCommandNoPlayers
        onlineCommandHeader = conf.getString("commands.online.header", onlineCommandHeader) ?: onlineCommandHeader
        onlineCommandPlayerFormat = conf.getString("commands.online.player-format", onlineCommandPlayerFormat) ?: onlineCommandPlayerFormat
        onlineCommandSeparator = conf.getString("commands.online.separator", onlineCommandSeparator) ?: onlineCommandSeparator
        onlineCommandFooter = conf.getString("commands.online.footer", onlineCommandFooter) ?: onlineCommandFooter
        tpsCommandResponse = conf.getString("commands.tps.response", tpsCommandResponse) ?: tpsCommandResponse
        tpsCommandMessage = conf.getString("commands.tps.message", tpsCommandMessage) ?: tpsCommandMessage
        tpsStatusExcellent = conf.getString("commands.tps.status.excellent", tpsStatusExcellent) ?: tpsStatusExcellent
        tpsStatusGood = conf.getString("commands.tps.status.good", tpsStatusGood) ?: tpsStatusGood
        tpsStatusPoor = conf.getString("commands.tps.status.poor", tpsStatusPoor) ?: tpsStatusPoor
        tpsStatusCritical = conf.getString("commands.tps.status.critical", tpsStatusCritical) ?: tpsStatusCritical
        restartCommandResponse = conf.getString("commands.restart.response", restartCommandResponse) ?: restartCommandResponse
        genderCommandUsage = conf.getString("commands.gender.usage", genderCommandUsage) ?: genderCommandUsage
        genderCommandNoPlayer = conf.getString("commands.gender.no-player", genderCommandNoPlayer) ?: genderCommandNoPlayer
        genderCommandResponse = conf.getString("commands.gender.response", genderCommandResponse) ?: genderCommandResponse
        playerCommandUsage = conf.getString("commands.player.usage", playerCommandUsage) ?: playerCommandUsage
        playerCommandNoPlayer = conf.getString("commands.player.not-found", playerCommandNoPlayer) ?: playerCommandNoPlayer
        playerCommandResponse = conf.getString("commands.player.response", playerCommandResponse) ?: playerCommandResponse

        // Stats command
        statsCommandUsage = conf.getString("commands.stats.usage", statsCommandUsage) ?: statsCommandUsage
        statsNoPlayers = conf.getString("commands.stats.no-data", statsNoPlayers) ?: statsNoPlayers
        statsMessage = conf.getString("commands.stats.message", statsMessage) ?: statsMessage
        statsHeader = conf.getString("commands.stats.header", statsHeader) ?: statsHeader
        statsEntry = conf.getString("commands.stats.entry", statsEntry) ?: statsEntry
        statsFooter = conf.getString("commands.stats.footer", statsFooter) ?: statsFooter

        // Top command
        topCommandUsage = conf.getString("commands.top.usage", topCommandUsage) ?: topCommandUsage
        topNoData = conf.getString("commands.top.no-data", topNoData) ?: topNoData
        topMessage = conf.getString("commands.top.message", topMessage) ?: topMessage

        // TopBal command
        topBalMessage = conf.getString("commands.topbal.message", topBalMessage) ?: topBalMessage
        topBalNoData = conf.getString("commands.topbal.no-data", topBalNoData) ?: topBalNoData
        topBalError = conf.getString("commands.topbal.error", topBalError) ?: topBalError

        // Auto notifications
        autoNotificationsEnabled = conf.getBoolean("auto_notifications.enabled", autoNotificationsEnabled)
        autoNotificationsTimezone = conf.getString("auto_notifications.timezone", autoNotificationsTimezone) ?: autoNotificationsTimezone

        // Playtime top auto notifications
        playtimeTopAutoEnabled = conf.getBoolean("auto_notifications.playtime_top.enabled", playtimeTopAutoEnabled)
        playtimeTopAutoPeriod = conf.getString("auto_notifications.playtime_top.period", playtimeTopAutoPeriod) ?: playtimeTopAutoPeriod
        playtimeTopAutoSchedule = conf.getString("auto_notifications.playtime_top.schedule", playtimeTopAutoSchedule) ?: playtimeTopAutoSchedule
        playtimeTopAutoTitle = conf.getString("auto_notifications.playtime_top.title", playtimeTopAutoTitle) ?: playtimeTopAutoTitle
        playtimeTopAutoFooter = conf.getString("auto_notifications.playtime_top.footer", playtimeTopAutoFooter) ?: playtimeTopAutoFooter
        playtimeTopAutoDeleteSeconds = conf.getInt("auto_notifications.playtime_top.auto-delete-seconds", playtimeTopAutoDeleteSeconds)

        // Playtime top exclude settings
        playtimeTopExcludeEnabled = conf.getBoolean("auto_notifications.playtime_top.exclude.enabled", playtimeTopExcludeEnabled)
        playtimeTopExcludePermissions = conf.getStringList("auto_notifications.playtime_top.exclude.permissions").takeIf { it.isNotEmpty() } ?: playtimeTopExcludePermissions

        // Playtime top rewards
        playtimeTopRewardsEnabled = conf.getBoolean("auto_notifications.playtime_top.rewards.enabled", playtimeTopRewardsEnabled)
        playtimeTopRewardsNotification = conf.getString("auto_notifications.playtime_top.rewards.notification", playtimeTopRewardsNotification) ?: playtimeTopRewardsNotification
        playtimeTopRewardsNotificationAutoDeleteSeconds = conf.getInt("auto_notifications.playtime_top.rewards.notification-auto-delete-seconds", playtimeTopRewardsNotificationAutoDeleteSeconds)

        // Load playtime rewards list
        val playtimeRewardsList = mutableListOf<RewardConfig>()
        val playtimeRewardsSection = conf.getConfigurationSection("auto_notifications.playtime_top.rewards.list")
        if (playtimeRewardsSection != null) {
            for (key in playtimeRewardsSection.getKeys(false)) {
                val rewardSection = playtimeRewardsSection.getConfigurationSection(key)
                if (rewardSection != null) {
                    val name = rewardSection.getString("name") ?: "Неизвестная награда"
                    val commands = rewardSection.getStringList("commands")
                    if (commands.isNotEmpty()) {
                        playtimeRewardsList.add(RewardConfig(name, commands))
                    }
                }
            }
        }
        playtimeTopRewardsList = playtimeRewardsList

        // Balance top auto notifications
        balanceTopAutoEnabled = conf.getBoolean("auto_notifications.balance_top.enabled", balanceTopAutoEnabled)
        balanceTopAutoSchedule = conf.getString("auto_notifications.balance_top.schedule", balanceTopAutoSchedule) ?: balanceTopAutoSchedule
        balanceTopAutoTitle = conf.getString("auto_notifications.balance_top.title", balanceTopAutoTitle) ?: balanceTopAutoTitle
        balanceTopAutoFooter = conf.getString("auto_notifications.balance_top.footer", balanceTopAutoFooter) ?: balanceTopAutoFooter
        balanceTopAutoDeleteSeconds = conf.getInt("auto_notifications.balance_top.auto-delete-seconds", balanceTopAutoDeleteSeconds)

        // Balance top exclude settings
        balanceTopExcludeEnabled = conf.getBoolean("auto_notifications.balance_top.exclude.enabled", balanceTopExcludeEnabled)
        balanceTopExcludePermissions = conf.getStringList("auto_notifications.balance_top.exclude.permissions").takeIf { it.isNotEmpty() } ?: balanceTopExcludePermissions

        // Balance top rewards
        balanceTopRewardsEnabled = conf.getBoolean("auto_notifications.balance_top.rewards.enabled", balanceTopRewardsEnabled)
        balanceTopRewardsNotification = conf.getString("auto_notifications.balance_top.rewards.notification", balanceTopRewardsNotification) ?: balanceTopRewardsNotification
        balanceTopRewardsNotificationAutoDeleteSeconds = conf.getInt("auto_notifications.balance_top.rewards.notification-auto-delete-seconds", balanceTopRewardsNotificationAutoDeleteSeconds)

        // Load balance rewards list
        val balanceRewardsList = mutableListOf<RewardConfig>()
        val balanceRewardsSection = conf.getConfigurationSection("auto_notifications.balance_top.rewards.list")
        if (balanceRewardsSection != null) {
            for (key in balanceRewardsSection.getKeys(false)) {
                val rewardSection = balanceRewardsSection.getConfigurationSection(key)
                if (rewardSection != null) {
                    val name = rewardSection.getString("name") ?: "Неизвестная награда"
                    val commands = rewardSection.getStringList("commands")
                    if (commands.isNotEmpty()) {
                        balanceRewardsList.add(RewardConfig(name, commands))
                    }
                }
            }
        }
        balanceTopRewardsList = balanceRewardsList

        // Daily summary settings
        dailySummaryEnabled = conf.getBoolean("daily-summary.enabled", dailySummaryEnabled)
        dailySummaryTime = conf.getString("daily-summary.time", dailySummaryTime) ?: dailySummaryTime
        dailySummaryTimezone = conf.getString("daily-summary.timezone", dailySummaryTimezone) ?: dailySummaryTimezone
        dailySummaryMessage = conf.getString("daily-summary.message", dailySummaryMessage) ?: dailySummaryMessage

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

        // Загружаем переводы для stats если они есть в конфиге
        val statsTranslationsSection = conf.getConfigurationSection("commands.stats.translations")
        if (statsTranslationsSection != null) {
            val translations = mutableMapOf<String, String>()
            for (key in statsTranslationsSection.getKeys(false)) {
                val translation = statsTranslationsSection.getString(key)
                if (translation != null) {
                    translations[key] = translation
                }
            }
            if (translations.isNotEmpty()) {
                statsTranslations = translations
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

        // Загружаем настройки игры из game.yml
        loadGameConfig()

        // Commands auto-delete timeout
        commandsAutoDeleteSeconds = conf.getInt("commands.auto-delete-seconds", 30)

        // Error messages
        errorsNoAdminPermission = conf.getString("commands.errors.no-admin-permission", errorsNoAdminPermission) ?: errorsNoAdminPermission
        errorsCommandNotAllowed = conf.getString("commands.errors.command-not-allowed", errorsCommandNotAllowed) ?: errorsCommandNotAllowed
        errorsUnregNoPermission = conf.getString("commands.errors.unreg-no-permission", errorsUnregNoPermission) ?: errorsUnregNoPermission

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
        registerUserAlreadyRegistered = conf.getString("register-channel.user-already-registered", registerUserAlreadyRegistered) ?: registerUserAlreadyRegistered
        registerPlayerOffline = conf.getString("register-channel.player-offline", registerPlayerOffline) ?: registerPlayerOffline
        registerSuccess = conf.getString("register-channel.success", registerSuccess) ?: registerSuccess
        registerSuccessInGame = conf.getString("register-channel.success-in-game", registerSuccessInGame) ?: registerSuccessInGame
        registerCodeInvalid = conf.getString("register-channel.code-invalid", registerCodeInvalid) ?: registerCodeInvalid
        registerCodeSuccess = conf.getString("register-channel.code-success", registerCodeSuccess) ?: registerCodeSuccess
        registerRewardCommands = conf.getStringList("register-channel.reward-commands").ifEmpty {
            listOf("eco give %player% 50", "broadcast &b%player% &eзарегистрировал свой аккаунт в Telegram")
        }

        // Plugin settings
        pluginPrefix = conf.getString("plugin.prefix", pluginPrefix) ?: pluginPrefix
        telegramLink = conf.getString("plugin.telegram-link", telegramLink) ?: telegramLink
        telegramCommandMessage = conf.getString("plugin.telegram-command-message", telegramCommandMessage) ?: telegramCommandMessage
        telegramClickText = conf.getString("plugin.telegram-click-text", telegramClickText) ?: telegramClickText
        telegramHoverText = conf.getString("plugin.telegram-hover-text", telegramHoverText) ?: telegramHoverText
        noPermissionMessage = conf.getString("plugin.no-permission-message", noPermissionMessage) ?: noPermissionMessage

        // Загружаем настройки планировщика
        loadSchedulerConfig(conf)

        // Загружаем настройки рестарта
        loadRestartConfig(conf)

        // Загружаем настройки фильтрации сообщений
        loadMessageFilterConfig(conf)

        // Загружаем настройки чата
        loadChatConfig(conf)

        // Загружаем настройки репутации
        loadReputationConfig()

        plugin.logger.info("✅ Configuration loaded successfully")
    }

    /**
     * Загрузка настроек планировщика
     */
    private fun loadSchedulerConfig(conf: FileConfiguration) {
        schedulerEnabled = conf.getBoolean("scheduler.enabled", true)
        schedulerTimezone = conf.getString("scheduler.timezone", schedulerTimezone) ?: schedulerTimezone
        schedulerLoggingConsole = conf.getBoolean("scheduler.logging.console", true)
        schedulerLoggingTelegram = conf.getBoolean("scheduler.logging.telegram", true)

        // Загружаем ежедневные задачи
        val tasksSection = conf.getConfigurationSection("scheduler.daily_tasks")
        val tasks = mutableMapOf<String, SchedulerManager.SchedulerTaskConfig>()

        tasksSection?.getKeys(false)?.forEach { taskName ->
            val taskSection = conf.getConfigurationSection("scheduler.daily_tasks.$taskName")
            if (taskSection != null) {
                val time = taskSection.getString("time", "12:00") ?: "12:00"
                val commands = taskSection.getStringList("commands")
                val enabled = taskSection.getBoolean("enabled", true)

                tasks[taskName] = SchedulerManager.SchedulerTaskConfig(time, commands, enabled)
            }
        }

        schedulerDailyTasks = tasks
    }

    /**
     * Загрузка настроек рестарта
     */
    private fun loadRestartConfig(conf: FileConfiguration) {
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Начинаем загрузку конфигурации рестарта...")

            // Проверим, есть ли вообще секция restart
            val restartSection = conf.getConfigurationSection("commands.restart")
            plugin.logger.info("🔧 [TConf] Секция commands.restart найдена: ${restartSection != null}")

            val scheduledSection = conf.getConfigurationSection("commands.restart.scheduled")
            plugin.logger.info("🔧 [TConf] Секция commands.restart.scheduled найдена: ${scheduledSection != null}")

            val serverSection = conf.getConfigurationSection("commands.restart.scheduled.server")
            plugin.logger.info("🔧 [TConf] Секция commands.restart.scheduled.server найдена: ${serverSection != null}")

            val warningsSection = conf.getConfigurationSection("commands.restart.scheduled.server.warnings")
            plugin.logger.info("🔧 [TConf] Секция commands.restart.scheduled.server.warnings найдена: ${warningsSection != null}")
        }

        // Immediate restart
        restartImmediateMessage = conf.getString("commands.restart.immediate.message", restartImmediateMessage) ?: restartImmediateMessage
        restartImmediateResponse = conf.getString("commands.restart.immediate.response", restartImmediateResponse) ?: restartImmediateResponse
        restartImmediateCommand = conf.getString("commands.restart.immediate.command", restartImmediateCommand) ?: restartImmediateCommand

        // Новая система рестарта - Telegram сообщения
        restartTelegramTimerStarted = conf.getString("commands.restart.scheduled.telegram.timer_started", restartTelegramTimerStarted) ?: restartTelegramTimerStarted
        restartTelegramInvalidFormat = conf.getString("commands.restart.scheduled.telegram.invalid_format", restartTelegramInvalidFormat) ?: restartTelegramInvalidFormat
        restartTelegramTimeRangeError = conf.getString("commands.restart.scheduled.telegram.time_range_error", restartTelegramTimeRangeError) ?: restartTelegramTimeRangeError
        restartTelegramTimerCancelled = conf.getString("commands.restart.scheduled.telegram.timer_cancelled", restartTelegramTimerCancelled) ?: restartTelegramTimerCancelled
        restartTelegramTimerActive = conf.getString("commands.restart.scheduled.telegram.timer_active", restartTelegramTimerActive) ?: restartTelegramTimerActive
        restartTelegramCancelSuccess = conf.getString("commands.restart.scheduled.telegram.cancel_success", restartTelegramCancelSuccess) ?: restartTelegramCancelSuccess
        restartTelegramCancelNoRestart = conf.getString("commands.restart.scheduled.telegram.cancel_no_restart", restartTelegramCancelNoRestart) ?: restartTelegramCancelNoRestart

        // Новая система рестарта - Серверные команды
        restartServerTimerStarted = conf.getString("commands.restart.scheduled.server.timer_started", restartServerTimerStarted) ?: restartServerTimerStarted
        restartServerFinalCommand = conf.getString("commands.restart.scheduled.server.final_command", restartServerFinalCommand) ?: restartServerFinalCommand
        restartServerTimerCancelled = conf.getString("commands.restart.scheduled.server.timer_cancelled", restartServerTimerCancelled) ?: restartServerTimerCancelled

        // Команды рестарта
        restartCommand = conf.getString("commands.restart.scheduled.commands.restart", restartCommand) ?: restartCommand
        restartPreCommands = conf.getStringList("commands.restart.scheduled.commands.pre_restart").takeIf { it.isNotEmpty() } ?: restartPreCommands

        // Загружаем предупреждения в минутах
        val minuteWarnings = mutableListOf<RestartManager.WarningConfig>()
        val minutesList = conf.getMapList("commands.restart.scheduled.server.warnings.minutes")
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Загружаем предупреждения в минутах. Список найден: true, размер: ${minutesList.size}")
        }

        minutesList.forEach { item ->
            if (item is Map<*, *>) {
                val time = (item["time"] as? Int) ?: 0
                val command = (item["command"] as? String) ?: ""
                if (debugEnabled) {
                    plugin.logger.info("🔧 [TConf] Загружено предупреждение в минутах: время=$time, команда='$command'")
                }
                if (command.isNotEmpty()) {
                    minuteWarnings.add(RestartManager.WarningConfig(time, command))
                }
            }
        }
        restartWarningMinutes = minuteWarnings
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Всего загружено предупреждений в минутах: ${minuteWarnings.size}")
        }

        // Загружаем предупреждения в секундах
        val secondWarnings = mutableListOf<RestartManager.WarningConfig>()
        val secondsList = conf.getMapList("commands.restart.scheduled.server.warnings.seconds")
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Загружаем предупреждения в секундах. Список найден: true, размер: ${secondsList.size}")
        }

        secondsList.forEach { item ->
            if (item is Map<*, *>) {
                val time = (item["time"] as? Int) ?: 0
                val command = (item["command"] as? String) ?: ""
                if (debugEnabled) {
                    plugin.logger.info("🔧 [TConf] Загружено предупреждение в секундах: время=$time, команда='$command'")
                }
                if (command.isNotEmpty()) {
                    secondWarnings.add(RestartManager.WarningConfig(time, command))
                }
            }
        }
        restartWarningSeconds = secondWarnings
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Всего загружено предупреждений в секундах: ${secondWarnings.size}")
        }
    }

    /**
     * Загрузка настроек фильтрации сообщений
     */
    private fun loadMessageFilterConfig(conf: FileConfiguration) {
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Загружаем настройки фильтрации сообщений...")
        }

        // Основные настройки фильтрации
        // messageFilterEnabled управляет всей системой фильтрации (если false - все фильтры отключены)
        messageFilterEnabled = false // По умолчанию отключаем фильтрацию, чтобы не блокировать сообщения
        messageFilterBlockBots = conf.getBoolean("message-filter.block-bots", messageFilterBlockBots)
        messageFilterMaxLength = conf.getInt("message-filter.length-limits.max-message-length", messageFilterMaxLength)

        // Загружаем белый список пользователей (если нужно использовать whitelist, включайте messageFilterEnabled вручную в коде)
        messageFilterWhitelistUsers = conf.getLongList("message-filter.whitelist.users")

        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Фильтрация включена: $messageFilterEnabled")
            plugin.logger.info("🔧 [TConf] Блокировка ботов: $messageFilterBlockBots")
            plugin.logger.info("🔧 [TConf] Максимальная длина сообщения: $messageFilterMaxLength")
            plugin.logger.info("🔧 [TConf] Белый список пользователей: ${messageFilterWhitelistUsers.size} пользователей")
        }
    }

    /**
     * Загрузка настроек чата
     */
    private fun loadChatConfig(conf: FileConfiguration) {
        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Загружаем настройки чата...")
        }

        // Пересылка сообщений Minecraft → Telegram
        chatMinecraftToTelegramEnabled = conf.getBoolean("chat.minecraft-to-telegram.enabled", chatMinecraftToTelegramEnabled)
        chatMinecraftToTelegramFormat = conf.getString("chat.minecraft-to-telegram.format", chatMinecraftToTelegramFormat) ?: chatMinecraftToTelegramFormat

        // Пересылка сообщений Telegram → Minecraft
        chatTelegramToMinecraftEnabled = conf.getBoolean("chat.telegram-to-minecraft.enabled", chatTelegramToMinecraftEnabled)
        chatTelegramToMinecraftFormat = conf.getString("chat.telegram-to-minecraft.format", chatTelegramToMinecraftFormat) ?: chatTelegramToMinecraftFormat

        // Пересылка игрового чата
        chatPlayerChatEnabled = conf.getBoolean("chat.player-chat.enabled", chatPlayerChatEnabled)

        // События игроков - вход
        chatPlayerJoinEnabled = conf.getBoolean("chat.player-events.join.enabled", chatPlayerJoinEnabled)
        chatPlayerJoinMessage = conf.getString("chat.player-events.join.message", chatPlayerJoinMessage) ?: chatPlayerJoinMessage

        // События игроков - выход
        chatPlayerQuitEnabled = conf.getBoolean("chat.player-events.quit.enabled", chatPlayerQuitEnabled)
        chatPlayerQuitMessage = conf.getString("chat.player-events.quit.message", chatPlayerQuitMessage) ?: chatPlayerQuitMessage

        // События игроков - смерть
        chatPlayerDeathEnabled = conf.getBoolean("chat.player-events.death.enabled", chatPlayerDeathEnabled)
        chatPlayerDeathMessage = conf.getString("chat.player-events.death.message", chatPlayerDeathMessage) ?: chatPlayerDeathMessage
        chatPlayerDeathUseRussianMessages = conf.getBoolean("chat.player-events.death.use-russian-messages", chatPlayerDeathUseRussianMessages)
        chatPlayerDeathDebugMessages = conf.getBoolean("chat.player-events.death.debug-messages", chatPlayerDeathDebugMessages)


        // Белый и черный списки
        chatWhitelistEnabled = conf.getBoolean("chat.whitelist.enabled", chatWhitelistEnabled)
        chatWhitelistNoRegistrationMessage = conf.getString("chat.whitelist.no-registration-message", chatWhitelistNoRegistrationMessage) ?: chatWhitelistNoRegistrationMessage
        chatBlacklistEnabled = conf.getBoolean("chat.blacklist.enabled", chatBlacklistEnabled)
        chatBlacklistBlockedMessage = conf.getString("chat.blacklist.blocked-message", chatBlacklistBlockedMessage) ?: chatBlacklistBlockedMessage

        if (debugEnabled) {
            plugin.logger.info("🔧 [TConf] Minecraft → Telegram: $chatMinecraftToTelegramEnabled, формат: '$chatMinecraftToTelegramFormat'")
            plugin.logger.info("🔧 [TConf] Telegram → Minecraft: $chatTelegramToMinecraftEnabled, формат: '$chatTelegramToMinecraftFormat'")
            plugin.logger.info("🔧 [TConf] Игровой чат: $chatPlayerChatEnabled")
            plugin.logger.info("🔧 [TConf] События: join=$chatPlayerJoinEnabled, quit=$chatPlayerQuitEnabled, death=$chatPlayerDeathEnabled")
            plugin.logger.info("🔧 [TConf] Белый список: $chatWhitelistEnabled")
            plugin.logger.info("🔧 [TConf] Черный список: $chatBlacklistEnabled")
        }

        // Обновляем старые переменные для обратной совместимости
        if (chatTelegramToMinecraftEnabled) {
            formatTelegramToMinecraft = chatTelegramToMinecraftFormat.replace("%username%", "%player%")
        }
        if (chatMinecraftToTelegramEnabled) {
            formatMinecraftToTelegram = chatMinecraftToTelegramFormat
        }

        // Обновляем старые переменные белого и черного списков
        whitelistEnabled = chatWhitelistEnabled
        blacklistEnabled = chatBlacklistEnabled
        noRegistrationMessage = chatWhitelistNoRegistrationMessage.replace("\\n", "\n")
        blockedMessage = chatBlacklistBlockedMessage

        // Загружаем настройки планировщика
        schedulerEnabled = conf.getBoolean("scheduler.enabled", true)
        schedulerTimezone = conf.getString("scheduler.timezone", "Europe/Moscow") ?: "Europe/Moscow"
        schedulerLoggingConsole = conf.getBoolean("scheduler.logging.console", true)
        schedulerLoggingTelegram = conf.getBoolean("scheduler.logging.telegram", true)

        // Загружаем ежедневные задачи
        val dailyTasksSection = conf.getConfigurationSection("scheduler.daily_tasks")
        if (dailyTasksSection != null) {
            val tasks = mutableMapOf<String, SchedulerManager.SchedulerTaskConfig>()

            for (taskName in dailyTasksSection.getKeys(false)) {
                val taskSection = dailyTasksSection.getConfigurationSection(taskName)
                if (taskSection != null) {
                    val time = taskSection.getString("time", "00:00") ?: "00:00"
                    val commands = taskSection.getStringList("commands")
                    val enabled = taskSection.getBoolean("enabled", false)

                    tasks[taskName] = SchedulerManager.SchedulerTaskConfig(time, commands, enabled)
                }
            }

            schedulerDailyTasks = tasks
        }

        if (debugEnabled) {
            plugin.logger.info("⏰ [TConf] Планировщик: enabled=$schedulerEnabled, timezone=$schedulerTimezone")
            plugin.logger.info("⏰ [TConf] Загружено задач: ${schedulerDailyTasks.size}")
            for ((name, task) in schedulerDailyTasks) {
                plugin.logger.info("   - $name: ${task.time}, enabled=${task.enabled}, commands=${task.commands.size}")
            }
        }

        // Загружаем настройки автоматических уведомлений
        autoNotificationsEnabled = conf.getBoolean("auto_notifications.enabled", true)
        autoNotificationsTimezone = conf.getString("auto_notifications.timezone", "Europe/Kiev") ?: "Europe/Kiev"

        // Playtime top auto
        playtimeTopAutoEnabled = conf.getBoolean("auto_notifications.playtime_top.enabled", true)
        playtimeTopAutoPeriod = conf.getString("auto_notifications.playtime_top.period", "1d") ?: "1d"
        playtimeTopAutoSchedule = conf.getString("auto_notifications.playtime_top.schedule", "12:00,20:00") ?: "12:00,20:00"
        playtimeTopAutoTitle = conf.getString("auto_notifications.playtime_top.title", playtimeTopAutoTitle) ?: playtimeTopAutoTitle
        playtimeTopAutoFooter = conf.getString("auto_notifications.playtime_top.footer", playtimeTopAutoFooter) ?: playtimeTopAutoFooter
        playtimeTopAutoDeleteSeconds = conf.getInt("auto_notifications.playtime_top.auto-delete-seconds", 0)

        // Playtime top exclude
        playtimeTopExcludeEnabled = conf.getBoolean("auto_notifications.playtime_top.exclude.enabled", true)
        playtimeTopExcludePermissions = conf.getStringList("auto_notifications.playtime_top.exclude.permissions").takeIf { it.isNotEmpty() }
            ?: listOf("group.admin", "group.moderator", "ztelegram.top.exclude")

        // Playtime top rewards
        playtimeTopRewardsEnabled = conf.getBoolean("auto_notifications.playtime_top.rewards.enabled", true)
        playtimeTopRewardsNotification = conf.getString("auto_notifications.playtime_top.rewards.notification", playtimeTopRewardsNotification) ?: playtimeTopRewardsNotification
        playtimeTopRewardsNotificationAutoDeleteSeconds = conf.getInt("auto_notifications.playtime_top.rewards.notification-auto-delete-seconds", 0)

        // Загружаем список наград для playtime top
        val playtimeRewardsList = conf.getMapList("auto_notifications.playtime_top.rewards.list")
        if (playtimeRewardsList.isNotEmpty()) {
            val rewards = mutableListOf<RewardConfig>()
            for (rewardMap in playtimeRewardsList) {
                val name = rewardMap["name"]?.toString() ?: "Награда"
                @Suppress("UNCHECKED_CAST")
                val commands = (rewardMap["commands"] as? List<String>) ?: emptyList()
                if (commands.isNotEmpty()) {
                    rewards.add(RewardConfig(name, commands))
                }
            }
            this.playtimeTopRewardsList = rewards
        } else {
            // Если нет списка наград, используем старый формат (commands напрямую)
            val commands = conf.getStringList("auto_notifications.playtime_top.rewards.commands")
            if (commands.isNotEmpty()) {
                this.playtimeTopRewardsList = listOf(RewardConfig("Награда за активность", commands))
            }
        }

        // Balance top auto
        balanceTopAutoEnabled = conf.getBoolean("auto_notifications.balance_top.enabled", true)
        balanceTopAutoSchedule = conf.getString("auto_notifications.balance_top.schedule", "18:00") ?: "18:00"
        balanceTopAutoTitle = conf.getString("auto_notifications.balance_top.title", balanceTopAutoTitle) ?: balanceTopAutoTitle
        balanceTopAutoFooter = conf.getString("auto_notifications.balance_top.footer", balanceTopAutoFooter) ?: balanceTopAutoFooter
        balanceTopAutoDeleteSeconds = conf.getInt("auto_notifications.balance_top.auto-delete-seconds", 0)

        // Balance top exclude
        balanceTopExcludeEnabled = conf.getBoolean("auto_notifications.balance_top.exclude.enabled", true)
        balanceTopExcludePermissions = conf.getStringList("auto_notifications.balance_top.exclude.permissions").takeIf { it.isNotEmpty() }
            ?: listOf("group.admin", "group.moderator", "ztelegram.top.exclude")

        // Balance top rewards
        balanceTopRewardsEnabled = conf.getBoolean("auto_notifications.balance_top.rewards.enabled", true)
        balanceTopRewardsNotification = conf.getString("auto_notifications.balance_top.rewards.notification", balanceTopRewardsNotification) ?: balanceTopRewardsNotification
        balanceTopRewardsNotificationAutoDeleteSeconds = conf.getInt("auto_notifications.balance_top.rewards.notification-auto-delete-seconds", 0)

        // Загружаем список наград для balance top
        val balanceRewardsList = conf.getMapList("auto_notifications.balance_top.rewards.list")

        if (debugEnabled) {
            plugin.logger.info("🎁 [TConf] Loading balance top rewards...")
            plugin.logger.info("   - MapList size: ${balanceRewardsList.size}")
        }

        if (balanceRewardsList.isNotEmpty()) {
            val rewards = mutableListOf<RewardConfig>()
            for ((index, rewardMap) in balanceRewardsList.withIndex()) {
                val name = rewardMap["name"]?.toString() ?: "Награда"
                @Suppress("UNCHECKED_CAST")
                val commands = (rewardMap["commands"] as? List<String>) ?: emptyList()

                if (debugEnabled) {
                    plugin.logger.info("   - Reward #${index + 1}: name='$name', commands=${commands.size}")
                }

                if (commands.isNotEmpty()) {
                    rewards.add(RewardConfig(name, commands))
                }
            }
            this.balanceTopRewardsList = rewards

            if (debugEnabled) {
                plugin.logger.info("   ✅ Loaded ${rewards.size} balance top rewards")
            }
        } else {
            if (debugEnabled) {
                plugin.logger.info("   ⚠️ MapList is empty, trying old format...")
            }

            // Если нет списка наград, используем старый формат (commands напрямую)
            val commands = conf.getStringList("auto_notifications.balance_top.rewards.commands")
            if (commands.isNotEmpty()) {
                this.balanceTopRewardsList = listOf(RewardConfig("Награда за богатство", commands))

                if (debugEnabled) {
                    plugin.logger.info("   ✅ Loaded 1 reward from old format (${commands.size} commands)")
                }
            } else {
                if (debugEnabled) {
                    plugin.logger.warning("   ❌ No rewards found in config!")
                }
            }
        }

        if (debugEnabled) {
            plugin.logger.info("🔔 [TConf] Автоуведомления: enabled=$autoNotificationsEnabled, timezone=$autoNotificationsTimezone")
            plugin.logger.info("🏆 [TConf] Playtime top: enabled=$playtimeTopAutoEnabled, schedule=$playtimeTopAutoSchedule, rewards=${playtimeTopRewardsList.size}")
            plugin.logger.info("💰 [TConf] Balance top: enabled=$balanceTopAutoEnabled, schedule=$balanceTopAutoSchedule, rewards=${balanceTopRewardsList.size}")
        }
    }

    // Reputation system configuration
    var reputationAutoDeleteSeconds: Int = 60
    var reputationSuccessPositive: String = ""
    var reputationSuccessNegative: String = ""
    var reputationReasonLine: String = ""
    var reputationCooldown: String = ""
    var reputationSelfError: String = ""
    var reputationReasonRequired: String = ""
    var reputationReasonTooShort: String = ""
    var reputationReasonTooLong: String = ""
    var reputationNotRegistered: String = ""
    var reputationReplyRequired: String = ""
    var reputationInfo: String = ""
    var reputationRecentEntry: String = ""
    var reputationRecentHeader: String = ""
    var reputationRecentReasonPart: String = ""
    var reputationTop: String = ""
    var reputationTopEntry: String = ""
    var reputationTopEmpty: String = ""
    var reputationIngameReceivedPositive: String = ""
    var reputationIngameReceivedNegative: String = ""
    var reputationIngameReasonSuffix: String = ""
    var reputationIngameSelfInfo: String = ""

    /**
     * Загрузка настроек игры из game.yml
     */
    private fun loadGameConfig() {
        try {
            val gameFile = File(ZTele.instance.dataFolder, "game.yml")
            if (!gameFile.exists()) {
                // Создаем дефолтный game.yml если его нет
                ZTele.instance.saveResource("game.yml", false)
                if (debugEnabled) {
                    plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Создан файл game.yml с настройками по умолчанию")
                }
            }

            val gameConf = YamlConfiguration.loadConfiguration(gameFile)

            if (debugEnabled) {
                plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Загрузка настроек игры из game.yml")
            }

            // Основные настройки
            gameEnabled = gameConf.getBoolean("enabled", gameEnabled)
            gameCommandEnabled = gameConf.getBoolean("command.enabled", gameCommandEnabled)
            gameAutoDeleteSeconds = gameConf.getInt("command.auto-delete-seconds", gameAutoDeleteSeconds)

            // Настройки игрового процесса
            gameTimeoutSeconds = gameConf.getInt("game_settings.timeout_seconds", gameTimeoutSeconds)
            gameMinWordLength = gameConf.getInt("game_settings.min_word_length", gameMinWordLength)
            gameMaxWordLength = gameConf.getInt("game_settings.max_word_length", gameMaxWordLength)
            gameCooldownSeconds = gameConf.getInt("game_settings.cooldown_seconds", gameCooldownSeconds)
            gameMaxConcurrentGames = gameConf.getInt("game_settings.max_concurrent_games", gameMaxConcurrentGames)

            // Настройки наград
            gameBaseReward = gameConf.getInt("rewards.base_reward", gameBaseReward)
            gameSpeedBonus = gameConf.getInt("rewards.speed_bonus", gameSpeedBonus)
            gameMaxBonus = gameConf.getInt("rewards.max_bonus", gameMaxBonus)
            gameRewardCommands = gameConf.getStringList("rewards.commands").takeIf { it.isNotEmpty() } ?: gameRewardCommands

            // Сообщения игры
            gameMessageStart = gameConf.getString("messages.game_start", gameMessageStart) ?: gameMessageStart
            gameMessageWin = gameConf.getString("messages.game_win", gameMessageWin) ?: gameMessageWin
            gameMessageLose = gameConf.getString("messages.game_lose", gameMessageLose) ?: gameMessageLose
            gameMessageAlreadyPlaying = gameConf.getString("messages.already_playing", gameMessageAlreadyPlaying) ?: gameMessageAlreadyPlaying
            gameMessageNotRegistered = gameConf.getString("messages.not_registered", gameMessageNotRegistered) ?: gameMessageNotRegistered
            gameMessagePlayerNotFound = gameConf.getString("messages.player_not_found", gameMessagePlayerNotFound) ?: gameMessagePlayerNotFound
            gameMessageCooldown = gameConf.getString("messages.cooldown", gameMessageCooldown) ?: gameMessageCooldown
            gameMessageTooManyGames = gameConf.getString("messages.too_many_games", gameMessageTooManyGames) ?: gameMessageTooManyGames

            if (debugEnabled) {
                plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Настройки игры загружены успешно")
                plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Игра включена: $gameEnabled")
                plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Команда включена: $gameCommandEnabled")
                plugin.logger.info("[ZTelegram] 🎮 [GameConfig] Автоудаление: $gameAutoDeleteSeconds сек")
            }

        } catch (e: Exception) {
            plugin.logger.severe("[ZTelegram] ❌ [GameConfig] Ошибка при загрузке game.yml: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Загрузка настроек системы репутации
     */
    private fun loadReputationConfig() {
        try {
            // Конфиг уже перезагружен в ZTele.onEnable() после ConfigUpdater
            val conf = plugin.config

            // Проверяем существование секции reputation
            if (!conf.contains("reputation")) {
                plugin.logger.severe("❌ [ReputationConfig] Секция 'reputation' не найдена в config.yml!")
                plugin.logger.severe("   [ReputationConfig] Будут использованы дефолтные значения")
            } else {
                plugin.logger.info("✅ [ReputationConfig] Секция 'reputation' найдена в конфиге")
            }

            // Автоудаление сообщений
            reputationAutoDeleteSeconds = conf.getInt("reputation.auto_delete_seconds", 60)

            // Сообщения в Telegram
            reputationSuccessPositive = conf.getString("reputation.messages.success_positive")
                ?.takeIf { it.isNotEmpty() }
                ?: "👍 **%source%** повысил репутацию игрока **%target%**\n%reason_line%\n⭐ Рейтинг: +**%total%** (%level%)"
            reputationSuccessNegative = conf.getString("reputation.messages.success_negative")
                ?.takeIf { it.isNotEmpty() }
                ?: "👎 **%source%** понизил репутацию игрока **%target%**\n%reason_line%\n⭐ Рейтинг: **%total%** (%level%)"
            reputationReasonLine = conf.getString("reputation.messages.reason_line")
                ?.takeIf { it.isNotEmpty() }
                ?: "💬 Причина: `%reason%`"
            reputationCooldown = conf.getString("reputation.messages.cooldown")
                ?.takeIf { it.isNotEmpty() }
                ?: "⏰ **Подождите!**\n\nВы сможете изменить репутацию **%target%** через: **%time%**\n💡 Это защита от спама репутацией"
            reputationSelfError = conf.getString("reputation.messages.self_error")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Ошибка!**\n\nВы не можете изменить свою репутацию\n💡 Попросите других игроков оценить вас"
            reputationReasonRequired = conf.getString("reputation.messages.reason_required")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Требуется причина!**\n\nУкажите причину изменения репутации\n📝 Минимум %min_length% символов\n\n**Пример:** +%target% За помощь на сервере"
            reputationReasonTooShort = conf.getString("reputation.messages.reason_too_short")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Причина слишком короткая!**\n\nМинимальная длина: **%min_length%** символов\n📝 Ваша причина: %actual_length% символов"
            reputationReasonTooLong = conf.getString("reputation.messages.reason_too_long")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Причина слишком длинная!**\n\nМаксимальная длина: **%max_length%** символов\n📝 Ваша причина: %actual_length% символов"
            reputationNotRegistered = conf.getString("reputation.messages.not_registered")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Игрок не найден!**\n\nИгрок **%target%** не зарегистрирован в Telegram\n💡 Попросите его зарегистрироваться: `/reg ник`"
            reputationReplyRequired = conf.getString("reputation.messages.reply_required")
                ?.takeIf { it.isNotEmpty() }
                ?: "❌ **Ответьте на сообщение!**\n\nЧтобы изменить репутацию, ответьте на сообщение игрока\n💡 Используйте: **+** или **-** (или **+rep** / **-rep**)"
            reputationInfo = conf.getString("reputation.messages.info")
                ?.takeIf { it.isNotEmpty() }
                ?: "⭐ **Репутация игрока %player%**\n\n🎯 Общий рейтинг: **%total%**\n📊 Уровень: %level%\n\n👍 Положительная: **%positive%**\n👎 Отрицательная: **%negative%**\n📈 Процент: **%percent%%**\n\n%recent_entries%"
            reputationRecentEntry = conf.getString("reputation.messages.recent_entry")
                ?.takeIf { it.isNotEmpty() }
                ?: "• %emoji% **%source%** %date%%reason_part%"
            reputationRecentHeader = conf.getString("reputation.messages.recent_header")
                ?.takeIf { it.isNotEmpty() }
                ?: "\n📜 **Последние изменения:**\n"
            reputationRecentReasonPart = conf.getString("reputation.messages.recent_reason_part")
                ?.takeIf { it.isNotEmpty() }
                ?: " - `%reason%`"
            reputationTop = conf.getString("reputation.messages.top")
                ?.takeIf { it.isNotEmpty() }
                ?: "🏆 **Топ-%count% игроков по репутации**\n\n%entries%\n\n💡 Ставьте репутацию игрокам ответом на их сообщения"
            reputationTopEntry = conf.getString("reputation.messages.top_entry")
                ?.takeIf { it.isNotEmpty() }
                ?: "%position%. **%player%** — %level% (**%total%**)"
            reputationTopEmpty = conf.getString("reputation.messages.top_empty")
                ?.takeIf { it.isNotEmpty() }
                ?: "📭 **Топ репутации пуст**\n\nПока никто не получил репутацию\n💡 Будьте первым! Ответьте + или - на сообщение игрока"

            // Сообщения в игре
            reputationIngameReceivedPositive = conf.getString("reputation.ingame_messages.received_positive")
                ?.takeIf { it.isNotEmpty() }
                ?: "<gradient:#00FF00:#A6EB0F>〔Репутация〕</gradient> <hover:show_text:\"Посмотреть в Telegram\"><gradient:#A6EB0F:#00FF00>%source% повысил вашу репутацию! (+%total%)</gradient></hover>"
            reputationIngameReceivedNegative = conf.getString("reputation.ingame_messages.received_negative")
                ?.takeIf { it.isNotEmpty() }
                ?: "<gradient:#FF0000:#FF5733>〔Репутация〕</gradient> <hover:show_text:\"Посмотреть в Telegram\"><gradient:#FF5733:#FF0000>%source% понизил вашу репутацию! (%total%)</gradient></hover>"
            reputationIngameReasonSuffix = conf.getString("reputation.ingame_messages.reason_suffix")
                ?.takeIf { it.isNotEmpty() }
                ?: "\n<gradient:#FFA500:#FF8C00>Причина: %reason%</gradient>"
            reputationIngameSelfInfo = conf.getString("reputation.ingame_messages.self_info")
                ?.takeIf { it.isNotEmpty() }
                ?: "<gradient:#FF0000:#A6EB0F>〔Репутация〕</gradient> <gradient:#A6EB0F:#00FF00>Ваша репутация:</gradient>\n<gradient:#FFFFFF:#CCCCCC>Рейтинг: %total% | Уровень: %level%</gradient>\n<gradient:#FFFFFF:#CCCCCC>Положительная: %positive% | Отрицательная: %negative%</gradient>"

            // ВСЕГДА логируем загрузку репутации для диагностики
            plugin.logger.info("✅ [ReputationConfig] Настройки репутации загружены успешно")
            plugin.logger.info("   [ReputationConfig] Success Positive: ${reputationSuccessPositive.length} символов")
            plugin.logger.info("   [ReputationConfig] Success Negative: ${reputationSuccessNegative.length} символов")
            plugin.logger.info("   [ReputationConfig] Автоудаление: $reputationAutoDeleteSeconds сек")

            // Проверяем критические поля
            if (reputationSuccessPositive.isEmpty() || reputationSuccessNegative.isEmpty()) {
                plugin.logger.severe("⚠️ [ReputationConfig] КРИТИЧЕСКАЯ ОШИБКА: Репутационные сообщения пусты!")
                plugin.logger.severe("   [ReputationConfig] Это приведет к отправке пустых сообщений в Telegram")
            }

            if (debugEnabled) {
                plugin.logger.info("   [ReputationConfig] Preview Success Positive: ${reputationSuccessPositive.take(50)}...")
                plugin.logger.info("   [ReputationConfig] Preview Success Negative: ${reputationSuccessNegative.take(50)}...")
            }

        } catch (e: Exception) {
            plugin.logger.severe("❌ [ReputationConfig] Ошибка при загрузке настроек репутации: ${e.message}")
            e.printStackTrace()
        }
    }
}
