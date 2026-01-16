package org.zoobastiks.ztelegram.menu

import org.bukkit.Bukkit
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.bot.TBot
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.utils.PlaceholderEngine
import java.text.SimpleDateFormat
import java.util.*

/**
 * Менеджер для управления меню регистрации в канале регистрации
 * Легко расширяемый класс для будущих изменений
 */
class RegisterMenuManager(
    private val bot: TBot,
    private val plugin: ZTele
) {
    private val conf: TConf
        get() = ZTele.conf
    
    /**
     * Показывает главное меню регистрации
     */
    fun showMainMenu(chatId: String, messageId: Int?, userId: Long, username: String) {
        val isRegistered = ZTele.mgr.getPlayerByTelegramId(userId.toString()) != null
        val registeredPlayerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        
        val message = buildString {
            append("📝 **МЕНЮ РЕГИСТРАЦИИ**\n\n")
            if (isRegistered && registeredPlayerName != null) {
                append("✅ Вы зарегистрированы как: `$registeredPlayerName`\n\n")
            } else {
                append("❌ Вы не зарегистрированы\n\n")
            }
            append("Выберите действие:")
        }
        
        val keyboard = InlineKeyboardMarkup()
        val buttons = mutableListOf<List<InlineKeyboardButton>>()
        
        // Кнопка "Зарегистрироваться" - только если не зарегистрирован
        if (!isRegistered) {
            val registerStart = CallbackData.REGISTER_START
            buttons.add(listOf(
                InlineKeyboardButton().apply {
                    text = "✅ Зарегистрироваться"
                    callbackData = "$registerStart".withUserId(userId)
                }
            ))
        }
        
        // Кнопка "Отменить регистрацию" - только если зарегистрирован
        if (isRegistered) {
            val registerUnregister = CallbackData.REGISTER_UNREGISTER
            buttons.add(listOf(
                InlineKeyboardButton().apply {
                    text = "❌ Отменить регистрацию"
                    callbackData = "$registerUnregister".withUserId(userId)
                }
            ))
        }
        
        // Кнопка "Список зарегистрированных игроков" - только для администраторов
        if (conf.isAdministrator(userId)) {
            val registerList = CallbackData.REGISTER_LIST
            buttons.add(listOf(
                InlineKeyboardButton().apply {
                    text = "📋 Список игроков"
                    callbackData = "$registerList".withUserId(userId)
                }
            ))
        }
        
        // Кнопка "Информация о регистрации"
        val registerInfo = CallbackData.REGISTER_INFO
        buttons.add(listOf(
            InlineKeyboardButton().apply {
                text = "ℹ️ Как зарегистрироваться"
                callbackData = "$registerInfo".withUserId(userId)
            }
        ))
        
        // Кнопка "Награда за регистрацию"
        val registerRewards = CallbackData.REGISTER_REWARDS
        buttons.add(listOf(
            InlineKeyboardButton().apply {
                text = "🎁 Награда за регистрацию"
                callbackData = "$registerRewards".withUserId(userId)
            }
        ))
        
        // Кнопка "Связать доп аккаунт" - только если зарегистрирован
        if (isRegistered) {
            val linkedAccountsCount = getLinkedAccountsCount(userId)
            val registerLinkAccount = CallbackData.REGISTER_LINK_ACCOUNT
            if (linkedAccountsCount < 3) {
                buttons.add(listOf(
                    InlineKeyboardButton().apply {
                        text = "🔗 Связать доп аккаунт (${linkedAccountsCount}/3)"
                        callbackData = "$registerLinkAccount".withUserId(userId)
                    }
                ))
            } else {
                buttons.add(listOf(
                    InlineKeyboardButton().apply {
                        text = "🔗 Связать доп аккаунт (3/3 - максимум)"
                        callbackData = "$registerLinkAccount".withUserId(userId)
                    }
                ))
            }
        }
        
        keyboard.keyboard = buttons
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Показывает информацию о том, как зарегистрироваться
     */
    fun showRegistrationInfo(chatId: String, messageId: Int?, userId: Long) {
        val message = buildString {
            append("ℹ️ **КАК ЗАРЕГИСТРИРОВАТЬСЯ**\n\n")
            append("1️⃣ Введите свой никнейм Minecraft в этот чат\n")
            append("   • Только английские буквы, цифры и символ _\n")
            append("   • Длина от 3 до 16 символов\n\n")
            append("2️⃣ Или используйте код регистрации:\n")
            append("   • В игре выполните команду `/telegram link`\n")
            append("   • Получите код и введите его здесь\n\n")
            append("✅ После регистрации вы получите награду!")
        }
        
        val keyboard = InlineKeyboardMarkup()
        val registerMenu = CallbackData.REGISTER_MENU
        keyboard.keyboard = listOf(listOf(
            InlineKeyboardButton().apply {
                text = "🔙 Назад"
                callbackData = "$registerMenu".withUserId(userId)
            }
        ))
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Показывает информацию о наградах за регистрацию
     */
    fun showRegistrationRewards(chatId: String, messageId: Int?, userId: Long) {
        val message = buildString {
            append("🎁 **НАГРАДЫ ЗА РЕГИСТРАЦИЮ**\n\n")
            append("При регистрации вы получите:\n")
            append("♻️ 500 монет\n")
            append("♻️ 20 уровней опыта\n")
            append("♻️ Зачарованный железный меч\n\n")
            append("💌 Меч выдается только если вы\n")
            append("💌 в это время были на сервере.\n\n")
            append("🖤 Награды начисляются автоматически!")
        }
        
        val keyboard = InlineKeyboardMarkup()
        val registerMenu = CallbackData.REGISTER_MENU
        keyboard.keyboard = listOf(listOf(
            InlineKeyboardButton().apply {
                text = "🔙 Назад"
                callbackData = "$registerMenu".withUserId(userId)
            }
        ))
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Показывает список зарегистрированных игроков (только для администраторов)
     */
    fun showRegisteredPlayersList(chatId: String, messageId: Int?, userId: Long) {
        // Проверяем права администратора
        if (!conf.isAdministrator(userId)) {
            val message = "❌ **ДОСТУП ЗАПРЕЩЕН**\n\nЭта функция доступна только администраторам."
            val keyboard = InlineKeyboardMarkup()
            val registerMenu = CallbackData.REGISTER_MENU
            keyboard.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
            if (messageId != null) {
                bot.editMenuMessage(chatId, messageId, message, keyboard)
            } else {
                bot.sendMenuMessage(chatId, message, keyboard)
            }
            return
        }
        
        showRegisteredPlayersListPage(chatId, messageId, userId, 0)
    }
    
    /**
     * Показывает страницу списка зарегистрированных игроков
     */
    fun showRegisteredPlayersListPage(chatId: String, messageId: Int?, userId: Long, page: Int) {
        // Проверяем права администратора
        if (!conf.isAdministrator(userId)) {
            val message = "❌ **ДОСТУП ЗАПРЕЩЕН**\n\nЭта функция доступна только администраторам."
            val keyboard = InlineKeyboardMarkup()
            val registerMenu = CallbackData.REGISTER_MENU
            keyboard.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
            if (messageId != null) {
                bot.editMenuMessage(chatId, messageId, message, keyboard)
            } else {
                bot.sendMenuMessage(chatId, message, keyboard)
            }
            return
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val allPlayersData = ZTele.mgr.getAllRegisteredPlayers()
            val allPlayers = allPlayersData.keys.sorted()
            
            if (allPlayers.isEmpty()) {
                val message = "📋 **СПИСОК ЗАРЕГИСТРИРОВАННЫХ ИГРОКОВ**\n\n❌ Нет зарегистрированных игроков"
                val keyboard = InlineKeyboardMarkup()
                val registerMenu = CallbackData.REGISTER_MENU
                keyboard.keyboard = listOf(listOf(
                    InlineKeyboardButton().apply {
                        text = "🔙 Назад"
                        callbackData = "$registerMenu".withUserId(userId)
                    }
                ))
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    if (messageId != null) {
                        bot.editMenuMessage(chatId, messageId, message, keyboard)
                    } else {
                        bot.sendMenuMessage(chatId, message, keyboard)
                    }
                })
                return@Runnable
            }
            
            // Количество игроков на странице (примерно 15, чтобы не превысить лимит Telegram в 4096 символов)
            val itemsPerPage = 15
            val totalPages = (allPlayers.size + itemsPerPage - 1) / itemsPerPage
            val currentPage = page.coerceIn(0, totalPages - 1)
            
            val startIndex = currentPage * itemsPerPage
            val endIndex = (startIndex + itemsPerPage).coerceAtMost(allPlayers.size)
            
            val message = buildString {
                append("📋 **СПИСОК ЗАРЕГИСТРИРОВАННЫХ ИГРОКОВ**\n")
                append("Страница ${currentPage + 1} из $totalPages\n")
                append("Всего игроков: ${allPlayers.size}\n\n")
                
                for (i in startIndex until endIndex) {
                    val lowerName = allPlayers[i]
                    val originalName = ZTele.mgr.getOriginalPlayerName(lowerName)
                    val isOnline = Bukkit.getPlayerExact(originalName) != null
                    val statusEmoji = if (isOnline) "🟢" else "🔴"
                    val telegramId = allPlayersData[lowerName]?.toString() ?: "N/A"
                    
                    // Получаем информацию о Telegram канале с кликабельной ссылкой
                    val playerData = ZTele.mgr.getPlayerData(originalName)
                    val telegramLink = if (playerData != null && playerData.telegramId.isNotEmpty() && telegramId != "N/A") {
                        // Создаем кликабельную ссылку на профиль пользователя в HTML формате
                        // Формат: <a href="tg://user?id=USER_ID">Профиль</a>
                        "<a href=\"tg://user?id=$telegramId\">Профиль</a>"
                    } else {
                        "N/A"
                    }
                    
                    // Отображаем в одну строку
                    if (telegramLink != "N/A") {
                        append("${i + 1}. $statusEmoji `$originalName` $telegramId $telegramLink\n")
                    } else {
                        append("${i + 1}. $statusEmoji `$originalName` $telegramId N/A\n")
                    }
                }
            }
            
            val keyboard = InlineKeyboardMarkup()
            val buttons = mutableListOf<List<InlineKeyboardButton>>()
            
            // Кнопки навигации
            val navButtons = mutableListOf<InlineKeyboardButton>()
            if (currentPage > 0) {
                navButtons.add(InlineKeyboardButton().apply {
                    text = "◀️ Назад"
                    callbackData = "${CallbackData.REGISTER_LIST_PAGE}:${currentPage - 1}".withUserId(userId)
                })
            }
            if (currentPage < totalPages - 1) {
                navButtons.add(InlineKeyboardButton().apply {
                    text = "Вперед ▶️"
                    callbackData = "${CallbackData.REGISTER_LIST_PAGE}:${currentPage + 1}".withUserId(userId)
                })
            }
            if (navButtons.isNotEmpty()) {
                buttons.add(navButtons)
            }
            
            // Кнопка "Назад"
            val registerMenu = CallbackData.REGISTER_MENU
            buttons.add(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
            
            keyboard.keyboard = buttons
            
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (messageId != null) {
                    bot.editMenuMessage(chatId, messageId, message, keyboard)
                } else {
                    bot.sendMenuMessage(chatId, message, keyboard)
                }
            })
        })
    }
    
    /**
     * Показывает подтверждение отмены регистрации
     */
    fun showUnregisterConfirm(chatId: String, messageId: Int?, userId: Long) {
        val registeredPlayerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        
        val message = buildString {
            append("⚠️ **ПОДТВЕРЖДЕНИЕ ОТМЕНЫ РЕГИСТРАЦИИ**\n\n")
            if (registeredPlayerName != null) {
                append("Вы действительно хотите отменить регистрацию\n")
                append("для игрока `$registeredPlayerName`?\n\n")
            } else {
                append("Вы действительно хотите отменить регистрацию?\n\n")
            }
            append("❌ После отмены вы потеряете доступ к функциям,\n")
            append("связанным с вашим аккаунтом Telegram.")
        }
        
        val keyboard = InlineKeyboardMarkup()
        val registerUnregisterConfirm = CallbackData.REGISTER_UNREGISTER_CONFIRM
        val registerMenu = CallbackData.REGISTER_MENU
        keyboard.keyboard = listOf(
            listOf(
                InlineKeyboardButton().apply {
                    text = "✅ Да, отменить"
                    callbackData = "$registerUnregisterConfirm".withUserId(userId)
                },
                InlineKeyboardButton().apply {
                    text = "❌ Нет, вернуться"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            )
        )
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Выполняет отмену регистрации
     */
    fun executeUnregister(chatId: String, messageId: Int?, userId: Long) {
        val registeredPlayerName = ZTele.mgr.getPlayerByTelegramId(userId.toString())
        
        if (registeredPlayerName == null) {
            val message = "❌ **ОШИБКА**\n\nВы не зарегистрированы."
            val keyboard = InlineKeyboardMarkup()
            val registerMenu = CallbackData.REGISTER_MENU
            keyboard.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
            if (messageId != null) {
                bot.editMenuMessage(chatId, messageId, message, keyboard)
            } else {
                bot.sendMenuMessage(chatId, message, keyboard)
            }
            return
        }
        
        val success = ZTele.mgr.unregisterPlayer(registeredPlayerName)
        
        val message = if (success) {
            buildString {
                append("✅ **РЕГИСТРАЦИЯ ОТМЕНЕНА**\n\n")
                append("Регистрация для игрока `$registeredPlayerName`\n")
                append("успешно отменена.\n\n")
                append("Вы можете зарегистрироваться снова.")
            }
        } else {
            "❌ **ОШИБКА**\n\nНе удалось отменить регистрацию."
        }
        
            val keyboard = InlineKeyboardMarkup()
            val registerMenu = CallbackData.REGISTER_MENU
            keyboard.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 В меню"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Показывает меню для связывания дополнительного аккаунта
     */
    fun showLinkAccountMenu(chatId: String, messageId: Int?, userId: Long) {
        val linkedAccountsCount = getLinkedAccountsCount(userId)
        
        if (linkedAccountsCount >= 3) {
            val message = "❌ **ДОСТИГНУТ ЛИМИТ**\n\nВы уже связали максимальное количество аккаунтов (3)."
            val keyboard = InlineKeyboardMarkup()
            val registerMenu = CallbackData.REGISTER_MENU
            keyboard.keyboard = listOf(listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            ))
            if (messageId != null) {
                bot.editMenuMessage(chatId, messageId, message, keyboard)
            } else {
                bot.sendMenuMessage(chatId, message, keyboard)
            }
            return
        }
        
        val message = buildString {
            append("🔗 **СВЯЗАТЬ ДОПОЛНИТЕЛЬНЫЙ АККАУНТ**\n\n")
            append("Связано аккаунтов: $linkedAccountsCount/3\n\n")
            append("Для связывания дополнительного аккаунта:\n")
            append("1️⃣ В игре выполните команду `/telegram link`\n")
            append("2️⃣ Получите код регистрации\n")
            append("3️⃣ Введите код в этот чат\n\n")
            append("⏱️ Код действителен ${conf.linkCodeExpirationMinutes} минут")
        }
        
        val keyboard = InlineKeyboardMarkup()
        val registerLinkAccount = CallbackData.REGISTER_LINK_ACCOUNT
        val registerMenu = CallbackData.REGISTER_MENU
        keyboard.keyboard = listOf(
            listOf(
                InlineKeyboardButton().apply {
                    text = "🔄 Обновить"
                    callbackData = "$registerLinkAccount".withUserId(userId)
                }
            ),
            listOf(
                InlineKeyboardButton().apply {
                    text = "🔙 Назад"
                    callbackData = "$registerMenu".withUserId(userId)
                }
            )
        )
        
        if (messageId != null) {
            bot.editMenuMessage(chatId, messageId, message, keyboard)
        } else {
            bot.sendMenuMessage(chatId, message, keyboard)
        }
    }
    
    /**
     * Получает количество связанных аккаунтов для пользователя
     * TODO: Реализовать подсчет связанных аккаунтов (сейчас возвращает 0)
     */
    private fun getLinkedAccountsCount(userId: Long): Int {
        // Пока что возвращаем 0, так как в текущей реализации
        // один Telegram аккаунт может быть связан только с одним игроком
        // В будущем можно добавить поддержку множественных связей
        return 0
    }
    
    /**
     * Расширение для добавления userId к callback data
     */
    private fun String.withUserId(userId: Long): String {
        return "$this:$userId"
    }
}

