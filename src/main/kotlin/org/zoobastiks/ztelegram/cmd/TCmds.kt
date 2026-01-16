package org.zoobastiks.ztelegram.cmd

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.zoobastiks.ztelegram.GradientUtils
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.conf.TConf
import org.zoobastiks.ztelegram.mgr.PMgr
import org.zoobastiks.ztelegram.utils.PlaceholderEngine

class TCmds(private val plugin: ZTele) : CommandExecutor, TabCompleter {
    private val conf: TConf
        get() = ZTele.conf
    private val mgr: PMgr
        get() = ZTele.mgr
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            if (!sender.hasPermission("ztelegram.command.link")) {
                sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                return true
            }
            handleTelegramCommand(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "link" -> {
                // Команда /telegram link должна выполняться только игроками
                if (sender !is Player) {
                    sender.sendMessage("${conf.pluginPrefix} §cЭта команда доступна только для игроков!")
                    return true
                }
                
                // Проверяем права на использование
                if (!sender.hasPermission("ztelegram.command.link.use")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                
                return handleLinkCommand(sender)
            }
            "unlink" -> {
                // Команда /telegram unlink должна выполняться только игроками
                if (sender !is Player) {
                    sender.sendMessage("${conf.pluginPrefix} §cЭта команда доступна только для игроков!")
                    return true
                }
                
                // Проверяем права на использование команды unlink
                if (!sender.hasPermission("ztelegram.command.unlink.use")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                
                return handleUnlinkCommand(sender)
            }
            "whitelist" -> {
                if (!sender.hasPermission("ztelegram.command.whitelist")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleWhitelistCommand(sender, args)
            }
            "blacklist" -> {
                if (!sender.hasPermission("ztelegram.command.blacklist")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleBlacklistCommand(sender, args)
            }
            "reload" -> {
                if (!sender.hasPermission("ztelegram.command.reload")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                
                if (args.size > 1 && args[1].lowercase() == "game") {
                    // Перезагрузка только конфигурации игры
                    plugin.reloadGame()
                    sender.sendMessage("${ZTele.conf.pluginPrefix} §aGame configuration reloaded!")
                    return true
                }
                
                sender.sendMessage("${ZTele.conf.pluginPrefix} §eReloading plugin...")
                plugin.logger.info("🔄 Plugin reload initiated by ${sender.name}")
                
                // Создаем асинхронную задачу для отслеживания статуса перезагрузки
                Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                    // Запускаем перезагрузку плагина
                    plugin.reload()
                    plugin.logger.info("✅ Plugin reload completed")
                    
                    // После завершения перезагрузки отправляем сообщение через основной поток
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        // Отправляем сообщение об успешной перезагрузке
                        sender.sendMessage("${ZTele.conf.pluginPrefix} §aPlugin reloaded successfully!")
                    })
                })
                
                return true
            }
            "help" -> {
                if (!sender.hasPermission("ztelegram.command.help")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                showHelpMenu(sender)
                return true
            }
            "unregister" -> {
                if (!sender.hasPermission("ztelegram.command.unregister")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleUnregisterCommand(sender, args)
            }
            "addplayer" -> {
                if (!sender.hasPermission("ztelegram.command.addplayer")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleAddPlayerCommand(sender, args)
            }
            "removeplayer" -> {
                if (!sender.hasPermission("ztelegram.command.removeplayer")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleRemovePlayerCommand(sender, args)
            }
            "addchannel" -> {
                if (!sender.hasPermission("ztelegram.command.addchannel")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleAddChannelCommand(sender, args)
            }
            "hidden" -> {
                if (!sender.hasPermission("ztelegram.command.hidden")) {
                    sender.sendMessage("${ZTele.conf.pluginPrefix} ${ZTele.conf.noPermissionMessage}")
                    return true
                }
                return handleHiddenListCommand(sender, args)
            }
            else -> {
                sender.sendMessage("${conf.pluginPrefix} §cUnknown command: §f${args[0]}")
                sender.sendMessage("${conf.pluginPrefix} §7Use §f/telegram help §7for a list of commands")
                return true
            }
        }
    }
    
    private fun handleTelegramCommand(sender: CommandSender): Boolean {
        // Отправляем компонент с ссылкой на телеграм
        val component = GradientUtils.parseMixedFormat(conf.telegramCommandMessage)
        
        if (sender is Player) {
            sender.sendMessage(component)
            
            // Отправляем компонент с кликабельной ссылкой
            val clickableText = Component.text()
                .append(Component.text("» "))
                .append(Component.text(conf.telegramClickText)
                    .color(NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.openUrl(conf.telegramLink))
                    .hoverEvent(HoverEvent.showText(Component.text(conf.telegramHoverText))))
                .build()
            
            sender.sendMessage(clickableText)
        } else {
            sender.sendMessage(PlainTextComponentSerializer.plainText().serialize(component))
            sender.sendMessage("» ${conf.telegramLink}")
        }
        
        return true
    }
    
    private fun handleUnregisterCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram unregister <nickname>")
            return false
        }
        
        val playerName = args[1]
        
        if (!mgr.isPlayerRegistered(playerName)) {
            sender.sendMessage("${conf.pluginPrefix} §cPlayer §f$playerName §cis not registered!")
            return false
        }
        
        mgr.unregisterPlayer(playerName)
        sender.sendMessage("${conf.pluginPrefix} §aPlayer §f$playerName §ahas been unregistered!")
        
        return true
    }
    
    private fun handleAddPlayerCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player && args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram addplayer <nickname>")
            return false
        }
        
        val playerName = if (args.size >= 2) args[1] else (sender as Player).name
        
        if (mgr.isPlayerHidden(playerName)) {
            sender.sendMessage("${conf.pluginPrefix} §cPlayer §f$playerName §cis already hidden!")
            return false
        }
        
        mgr.addHiddenPlayer(playerName)
        sender.sendMessage("${conf.pluginPrefix} §aPlayer §f$playerName §ahas been hidden from Telegram messages!")
        
        return true
    }
    
    private fun handleRemovePlayerCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (sender !is Player && args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram removeplayer <nickname>")
            return false
        }
        
        val playerName = if (args.size >= 2) args[1] else (sender as Player).name
        
        if (!mgr.isPlayerHidden(playerName)) {
            sender.sendMessage("${conf.pluginPrefix} §cPlayer §f$playerName §cis not hidden!")
            return false
        }
        
        mgr.removeHiddenPlayer(playerName)
        sender.sendMessage("${conf.pluginPrefix} §aPlayer §f$playerName §ais now visible in Telegram messages!")
        
        return true
    }
    
    private fun handleAddChannelCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram addchannel <1|2|3> <channelId>")
            return false
        }
        
        val channelNumber = args[1].toIntOrNull()
        if (channelNumber == null || channelNumber < 1 || channelNumber > 3) {
            sender.sendMessage("${conf.pluginPrefix} §cInvalid channel number: §f${args[1]}")
            return false
        }
        
        val channelId = args[2]
        
        val config = plugin.config
        when (channelNumber) {
            1 -> {
                config.set("channels.main", channelId)
                conf.mainChannelId = channelId
                sender.sendMessage("${conf.pluginPrefix} §aMain channel ID updated to §f$channelId")
            }
            2 -> {
                config.set("channels.console", channelId)
                conf.consoleChannelId = channelId
                sender.sendMessage("${conf.pluginPrefix} §aConsole channel ID updated to §f$channelId")
            }
            3 -> {
                config.set("channels.register", channelId)
                conf.registerChannelId = channelId
                sender.sendMessage("${conf.pluginPrefix} §aRegister channel ID updated to §f$channelId")
            }
        }
        
        plugin.saveConfig()
        return true
    }
    
    private fun handleHiddenListCommand(sender: CommandSender, @Suppress("UNUSED_PARAMETER") args: Array<out String>): Boolean {
        val hiddenPlayers = mgr.getHiddenPlayers()
        
        if (hiddenPlayers.isEmpty()) {
            sender.sendMessage("${conf.pluginPrefix} §7No players are hidden from Telegram messages")
            return true
        }
        
        sender.sendMessage("${conf.pluginPrefix} §7Hidden players:")
        for (player in hiddenPlayers) {
            sender.sendMessage("${conf.pluginPrefix} §8- §f$player")
        }
        
        return true
    }
    
    private fun handleLinkCommand(player: Player): Boolean {
        val playerName = player.name
        
        // Проверяем, уже зарегистрирован ли игрок
        if (mgr.isPlayerRegistered(playerName)) {
            player.sendMessage("${conf.pluginPrefix} §cВы уже зарегистрированы в Telegram!")
            return true
        }
        
        // Проверяем, был ли игрок когда-либо зарегистрирован (но отключил привязку)
        val playerData = mgr.getPlayerData(playerName)
        if (playerData != null && playerData.unlinked) {
            // Игрок был зарегистрирован ранее, но отключил привязку
            player.sendMessage(GradientUtils.parseMixedFormat(conf.linkWasRegisteredMessage))
        }
        
        // Генерируем код регистрации с использованием оригинального имени
        val code = mgr.generateRegistrationCode(player.name)
        
        // Отправляем сообщение с кодом
        val codeContext = PlaceholderEngine.createCustomContext(mapOf("code" to code))
        val codeMessage = PlaceholderEngine.process(conf.linkCodeMessage, codeContext)
        player.sendMessage(GradientUtils.parseMixedFormat(codeMessage))
        
        // Отправляем инструкцию
        player.sendMessage(GradientUtils.parseMixedFormat(conf.linkCodeInstruction))
        
        // Отправляем информацию о времени действия кода
        val expirationContext = PlaceholderEngine.createCustomContext(mapOf("minutes" to conf.linkCodeExpirationMinutes.toString()))
        val expirationMessage = PlaceholderEngine.process(conf.linkCodeExpiration, expirationContext)
        player.sendMessage(GradientUtils.parseMixedFormat(expirationMessage))
        
        return true
    }
    
    private fun handleUnlinkCommand(player: Player): Boolean {
        val playerName = player.name
        
        // Проверяем, зарегистрирован ли игрок
        if (!mgr.isPlayerRegistered(playerName)) {
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkNotRegisteredMessage))
            return true
        }
        
        // Получаем данные игрока
        val playerData = mgr.getPlayerData(playerName)
        
        // Проверяем, не отключен ли уже аккаунт
        if (playerData?.unlinked == true || playerData?.telegramId?.isEmpty() == true) {
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkAlreadyUnlinkedMessage))
            return true
        }
        
        // Отключаем привязку к Telegram
        if (mgr.unlinkPlayer(playerName)) {
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkSuccessMessage))
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkInfoMessage))
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkRelinkMessage))
        } else {
            player.sendMessage(GradientUtils.parseMixedFormat(conf.unlinkErrorMessage))
        }
        
        return true
    }
    
    private fun handleWhitelistCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram whitelist <add|remove|list> [telegramId]")
            return false
        }
        
        when (args[1].lowercase()) {
            "add" -> {
                if (args.size < 3) {
                    sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram whitelist add <telegramId>")
                    return false
                }
                
                val telegramId = args[2]
                if (mgr.addToWhitelist(telegramId)) {
                    sender.sendMessage("${conf.pluginPrefix} §aTelegram ID §f$telegramId §aдобавлен в белый список")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §cTelegram ID §f$telegramId §cуже находится в белом списке")
                }
            }
            "remove" -> {
                if (args.size < 3) {
                    sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram whitelist remove <telegramId>")
                    return false
                }
                
                val telegramId = args[2]
                if (mgr.removeFromWhitelist(telegramId)) {
                    sender.sendMessage("${conf.pluginPrefix} §aTelegram ID §f$telegramId §aудален из белого списка")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §cTelegram ID §f$telegramId §cне найден в белом списке")
                }
            }
            "list" -> {
                val whitelist = mgr.getWhitelist()
                if (whitelist.isEmpty()) {
                    sender.sendMessage("${conf.pluginPrefix} §7Белый список пуст")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §7Белый список (${whitelist.size} записей):")
                    for (id in whitelist) {
                        // Получаем имя игрока по Telegram ID, если есть
                        val playerName = mgr.getPlayerByTelegramId(id)
                        val displayName = if (playerName != null) "$id (${playerName})" else id
                        sender.sendMessage("${conf.pluginPrefix} §8- §f$displayName")
                    }
                }
            }
            else -> {
                sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram whitelist <add|remove|list> [telegramId]")
                return false
            }
        }
        
        return true
    }
    
    private fun handleBlacklistCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram blacklist <add|remove|list> [telegramId]")
            return false
        }
        
        when (args[1].lowercase()) {
            "add" -> {
                if (args.size < 3) {
                    sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram blacklist add <telegramId>")
                    return false
                }
                
                val telegramId = args[2]
                if (mgr.addToBlacklist(telegramId)) {
                    sender.sendMessage("${conf.pluginPrefix} §aTelegram ID §f$telegramId §aдобавлен в черный список")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §cTelegram ID §f$telegramId §cуже находится в черном списке")
                }
            }
            "remove" -> {
                if (args.size < 3) {
                    sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram blacklist remove <telegramId>")
                    return false
                }
                
                val telegramId = args[2]
                if (mgr.removeFromBlacklist(telegramId)) {
                    sender.sendMessage("${conf.pluginPrefix} §aTelegram ID §f$telegramId §aудален из черного списка")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §cTelegram ID §f$telegramId §cне найден в черном списке")
                }
            }
            "list" -> {
                val blacklist = mgr.getBlacklist()
                if (blacklist.isEmpty()) {
                    sender.sendMessage("${conf.pluginPrefix} §7Черный список пуст")
                } else {
                    sender.sendMessage("${conf.pluginPrefix} §7Черный список (${blacklist.size} записей):")
                    for (id in blacklist) {
                        // Получаем имя игрока по Telegram ID, если есть
                        val playerName = mgr.getPlayerByTelegramId(id)
                        val displayName = if (playerName != null) "$id (${playerName})" else id
                        sender.sendMessage("${conf.pluginPrefix} §8- §f$displayName")
                    }
                }
            }
            else -> {
                sender.sendMessage("${conf.pluginPrefix} §cUsage: /telegram blacklist <add|remove|list> [telegramId]")
                return false
            }
        }
        
        return true
    }
    
    private fun showHelpMenu(sender: CommandSender) {
        sender.sendMessage("§6§lZTelegram §7- §fHelp Menu")
        sender.sendMessage("§8§m--------------------------")
        
        // Основная команда
        if (sender.hasPermission("ztelegram.command.link")) {
            sender.sendMessage("§e/telegram §7- Show Telegram channel link")
        }
        
        // Help команда
        if (sender.hasPermission("ztelegram.command.help")) {
            sender.sendMessage("§e/telegram help §7- Show this help menu")
        }
        
        // Link команда
        if (sender.hasPermission("ztelegram.command.link.use")) {
            sender.sendMessage("§e/telegram link §7- Generate a registration code for Telegram")
        }
        
        // Unlink команда
        if (sender.hasPermission("ztelegram.command.unlink.use")) {
            sender.sendMessage("§e/telegram unlink §7- Disconnect your account from Telegram")
        }
        
        // Hide/Show команды
        if (sender.hasPermission("ztelegram.command.addplayer")) {
            sender.sendMessage("§e/telegram addplayer [player] §7- Hide player from Telegram messages")
        }
        
        if (sender.hasPermission("ztelegram.command.removeplayer")) {
            sender.sendMessage("§e/telegram removeplayer [player] §7- Show player in Telegram messages")
        }
        
        // Список скрытых игроков
        if (sender.hasPermission("ztelegram.command.hidden")) {
            sender.sendMessage("§e/telegram hidden §7- List all hidden players")
        }
        
        // Отмена регистрации
        if (sender.hasPermission("ztelegram.command.unregister")) {
            sender.sendMessage("§e/telegram unregister <player> §7- Unregister a player")
        }
        
        // Управление черным и белым списком
        if (sender.hasPermission("ztelegram.command.whitelist")) {
            sender.sendMessage("§e/telegram whitelist <add|remove|list> [telegramId] §7- Manage whitelist")
        }
        
        if (sender.hasPermission("ztelegram.command.blacklist")) {
            sender.sendMessage("§e/telegram blacklist <add|remove|list> [telegramId] §7- Manage blacklist")
        }
        
        // Административные команды
        if (sender.hasPermission("ztelegram.command.reload")) {
            sender.sendMessage("§e/telegram reload §7- Reload the plugin")
            sender.sendMessage("§e/telegram reload game §7- Reload only game configuration")
        }
        
        if (sender.hasPermission("ztelegram.command.addchannel")) {
            sender.sendMessage("§e/telegram addchannel <1|2|3> <channelId> §7- Update channel ID")
        }
        
        sender.sendMessage("§8§m--------------------------")
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (command.name.equals("telegram", ignoreCase = true)) {
            if (args.size == 1) {
                val completions = mutableListOf<String>()
                
                // Add subcommands based on permissions
                if (sender.hasPermission("ztelegram.command.help")) completions.add("help")
                
                // Разделяем права для команд link и unlink
                if (sender.hasPermission("ztelegram.command.link.use")) {
                    completions.add("link")
                }
                if (sender.hasPermission("ztelegram.command.unlink.use")) {
                    completions.add("unlink")
                }
                if (sender.hasPermission("ztelegram.command.reload")) completions.add("reload")
                if (sender.hasPermission("ztelegram.command.unregister")) completions.add("unregister")
                if (sender.hasPermission("ztelegram.command.addplayer")) {
                    completions.add("addplayer")
                }
                if (sender.hasPermission("ztelegram.command.removeplayer")) {
                    completions.add("removeplayer")
                }
                if (sender.hasPermission("ztelegram.command.addchannel")) {
                    completions.add("addchannel")
                }
                if (sender.hasPermission("ztelegram.command.hidden")) {
                    completions.add("hidden")
                }
                if (sender.hasPermission("ztelegram.command.whitelist")) {
                    completions.add("whitelist")
                }
                if (sender.hasPermission("ztelegram.command.blacklist")) {
                    completions.add("blacklist")
                }
                
                return completions.filter { it.startsWith(args[0], ignoreCase = true) }
            } else if (args.size == 2) {
                when (args[0].lowercase()) {
                    "reload" -> {
                        if (sender.hasPermission("ztelegram.command.reload")) {
                            return listOf("game").filter { it.startsWith(args[1], ignoreCase = true) }
                        }
                    }
                    "unregister" -> {
                        if (sender.hasPermission("ztelegram.command.unregister")) {
                            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                        }
                    }
                    "addplayer", "removeplayer" -> {
                        if (sender.hasPermission("ztelegram.command.addplayer") || sender.hasPermission("ztelegram.command.removeplayer")) {
                            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                        }
                    }
                    "addchannel" -> {
                        if (sender.hasPermission("ztelegram.command.addchannel")) {
                            return listOf("1", "2", "3").filter { it.startsWith(args[1], ignoreCase = true) }
                        }
                    }
                    "whitelist", "blacklist" -> {
                        if ((args[0].lowercase() == "whitelist" && sender.hasPermission("ztelegram.command.whitelist")) ||
                            (args[0].lowercase() == "blacklist" && sender.hasPermission("ztelegram.command.blacklist"))) {
                            return listOf("add", "remove", "list").filter { it.startsWith(args[1], ignoreCase = true) }
                        }
                    }
                }
            } else if (args.size == 3) {
                when (args[0].lowercase()) {
                    "whitelist", "blacklist" -> {
                        if (args[1].lowercase() == "remove") {
                            // Предложить список ID из черного/белого списка
                            val ids = if (args[0].lowercase() == "whitelist") {
                                mgr.getWhitelist()
                            } else {
                                mgr.getBlacklist()
                            }
                            return ids.filter { it.startsWith(args[2], ignoreCase = true) }
                        }
                    }
                }
            }
        }
        
        return null
    }
} 