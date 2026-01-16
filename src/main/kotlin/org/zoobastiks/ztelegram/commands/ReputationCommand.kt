package org.zoobastiks.ztelegram.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.reputation.ReputationResult

/**
 * Команда управления репутацией в игре
 * 
 * Использование:
 * /rep <игрок> - показать репутацию игрока
 * /rep +<игрок> [причина] - дать положительную репутацию
 * /rep -<игрок> [причина] - дать отрицательную репутацию
 * /rep top [positive|negative|percentage] - топ игроков
 * /rep stats - статистика системы
 * /rep reset <игрок> - сбросить репутацию (только админы)
 * 
 * @author Zoobastiks
 */
class ReputationCommand(private val plugin: ZTele) : CommandExecutor, TabCompleter {
    
    private val repManager = ZTele.reputation
    private val conf = ZTele.conf
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "top", "топ" -> {
                handleTopCommand(sender, args)
            }
            
            "stats", "статистика" -> {
                handleStatsCommand(sender)
            }
            
            "reset", "сброс" -> {
                if (!sender.hasPermission("ztelegram.reputation.admin")) {
                    sender.sendMessage("${conf.pluginPrefix} §cУ вас нет прав для использования этой команды!")
                    return true
                }
                handleResetCommand(sender, args)
            }
            
            "help", "помощь" -> {
                showUsage(sender)
            }
            
            else -> {
                // Проверяем, начинается ли с + или -
                val arg = args[0]
                when {
                    arg.startsWith("+") -> {
                        val targetName = arg.substring(1)
                        val reason = if (args.size > 1) args.drop(1).joinToString(" ") else null
                        handleGiveReputation(sender, targetName, true, reason)
                    }
                    
                    arg.startsWith("-") -> {
                        val targetName = arg.substring(1)
                        val reason = if (args.size > 1) args.drop(1).joinToString(" ") else null
                        handleGiveReputation(sender, targetName, false, reason)
                    }
                    
                    else -> {
                        // Показываем репутацию игрока
                        handleShowReputation(sender, arg)
                    }
                }
            }
        }
        
        return true
    }
    
    private fun handleShowReputation(sender: CommandSender, targetName: String) {
        val data = repManager.getReputationData(targetName)
        
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§6§l⭐ Репутация игрока §f$targetName")
        sender.sendMessage("")
        sender.sendMessage("§7Уровень: ${data.reputationLevel.getColoredDisplay()}")
        sender.sendMessage("§7Общий рейтинг: §f${data.totalReputation}")
        sender.sendMessage("§a+ Положительная: §f${data.positiveRep}")
        sender.sendMessage("§c- Отрицательная: §f${data.negativeRep}")
        sender.sendMessage("§7Процент положительной: §f${String.format("%.1f", data.positivePercentage)}%")
        
        // Показываем последние записи
        val recentEntries = data.getRecentEntries(3)
        if (recentEntries.isNotEmpty()) {
            sender.sendMessage("")
            sender.sendMessage("§7Последние изменения:")
            for (entry in recentEntries) {
                val sign = if (entry.isPositive) "§a+" else "§c-"
                val reasonText = if (entry.reason != null) " §8(${entry.reason})" else ""
                sender.sendMessage("  $sign §7от §f${entry.source} §8${entry.getFormattedDate()}$reasonText")
            }
        }
        
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    private fun handleGiveReputation(sender: CommandSender, targetName: String, isPositive: Boolean, reason: String?) {
        // Получаем имя источника
        val sourceName = if (sender is Player) sender.name else "Console"
        
        // Проверяем права администратора
        val isAdmin = sender.hasPermission("ztelegram.reputation.admin")
        
        // Проверяем, существует ли целевой игрок
        val targetPlayer = Bukkit.getOfflinePlayer(targetName)
        if (!targetPlayer.hasPlayedBefore() && Bukkit.getPlayerExact(targetName) == null) {
            sender.sendMessage("${conf.pluginPrefix} §cИгрок §f$targetName §cне найден!")
            return
        }
        
        // Даем репутацию
        val result = if (isPositive) {
            repManager.addPositiveReputation(sourceName, targetName, reason, isAdmin)
        } else {
            repManager.addNegativeReputation(sourceName, targetName, reason, isAdmin)
        }
        
        // Обрабатываем результат
        when (result) {
            is ReputationResult.Success -> {
                sender.sendMessage("${conf.pluginPrefix} §aРепутация успешно изменена!")
            }
            
            is ReputationResult.SuccessWithData -> {
                val sign = if (result.isPositive) "§a+" else "§c-"
                val action = if (result.isPositive) "повысили" else "понизили"
                
                sender.sendMessage("${conf.pluginPrefix} §7Вы $action репутацию игрока §f$targetName")
                sender.sendMessage("${conf.pluginPrefix} §7Текущий рейтинг: $sign§f${result.targetData.totalReputation} §8(${result.targetData.reputationLevel.getColoredDisplay()}§8)")
                
                // Уведомляем целевого игрока, если он онлайн
                val onlineTarget = Bukkit.getPlayerExact(targetName)
                if (onlineTarget != null && repManager.enableNotifications) {
                    onlineTarget.sendMessage("${conf.pluginPrefix} §7Игрок §f$sourceName §7${if (result.isPositive) "повысил" else "понизил"} вашу репутацию!")
                    if (repManager.showReasonInNotification && result.reason != null) {
                        onlineTarget.sendMessage("${conf.pluginPrefix} §7Причина: §f${result.reason}")
                    }
                    onlineTarget.sendMessage("${conf.pluginPrefix} §7Ваш рейтинг: $sign§f${result.targetData.totalReputation}")
                }
            }
            
            is ReputationResult.Failure -> {
                sender.sendMessage("${conf.pluginPrefix} §c${result.message}")
            }
            
            is ReputationResult.Cooldown -> {
                val hours = result.remainingMinutes / 60
                val minutes = result.remainingMinutes % 60
                val timeStr = if (hours > 0) {
                    "${hours}ч ${minutes}м"
                } else {
                    "${minutes}м"
                }
                sender.sendMessage("${conf.pluginPrefix} §cВы уже изменяли репутацию этого игрока!")
                sender.sendMessage("${conf.pluginPrefix} §cПодождите еще §f$timeStr")
            }
        }
    }
    
    private fun handleTopCommand(sender: CommandSender, args: Array<String>) {
        val sortType = if (args.size > 1) {
            when (args[1].lowercase()) {
                "positive", "pos", "+" -> org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.POSITIVE
                "negative", "neg", "-" -> org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.NEGATIVE
                "percentage", "percent", "%" -> org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.PERCENTAGE
                else -> org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.TOTAL
            }
        } else {
            org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.TOTAL
        }
        
        val topPlayers = repManager.getTopPlayers(10, sortType)
        
        if (topPlayers.isEmpty()) {
            sender.sendMessage("${conf.pluginPrefix} §7Нет данных о репутации")
            return
        }
        
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§6§l🏆 Топ-10 игроков по репутации")
        sender.sendMessage("")
        
        topPlayers.forEachIndexed { index, (playerName, data) ->
            val position = index + 1
            val medal = when (position) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "§7$position."
            }
            
            val value = when (sortType) {
                org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.TOTAL -> 
                    "§f${data.totalReputation}"
                org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.POSITIVE -> 
                    "§a+${data.positiveRep}"
                org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.NEGATIVE -> 
                    "§c-${data.negativeRep}"
                org.zoobastiks.ztelegram.reputation.ReputationManager.SortType.PERCENTAGE -> 
                    "§f${String.format("%.1f", data.positivePercentage)}%"
            }
            
            sender.sendMessage("  $medal §f$playerName §8- $value §8(${data.reputationLevel.getColoredDisplay()}§8)")
        }
        
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    private fun handleStatsCommand(sender: CommandSender) {
        val stats = repManager.getStatistics()
        
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§6§l📊 Статистика системы репутации")
        sender.sendMessage("")
        sender.sendMessage("§7Всего игроков: §f${stats.totalPlayers}")
        sender.sendMessage("§a+ Положительных оценок: §f${stats.totalPositive}")
        sender.sendMessage("§c- Отрицательных оценок: §f${stats.totalNegative}")
        sender.sendMessage("§7Средний рейтинг: §f${String.format("%.2f", stats.averageReputation)}")
        sender.sendMessage("§7Кулдаун: §f${repManager.cooldownMinutes} минут")
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    private fun handleResetCommand(sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage("${conf.pluginPrefix} §cИспользование: /rep reset <игрок>")
            return
        }
        
        val targetName = args[1]
        
        if (repManager.resetReputation(targetName)) {
            sender.sendMessage("${conf.pluginPrefix} §aРепутация игрока §f$targetName §aсброшена!")
        } else {
            sender.sendMessage("${conf.pluginPrefix} §cИгрок §f$targetName §cне имеет репутации!")
        }
    }
    
    private fun showUsage(sender: CommandSender) {
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        sender.sendMessage("§6§l⭐ Система репутации")
        sender.sendMessage("")
        sender.sendMessage("§e/rep <игрок> §7- показать репутацию")
        sender.sendMessage("§e/rep +<игрок> [причина] §7- дать §a+rep")
        sender.sendMessage("§e/rep -<игрок> [причина] §7- дать §c-rep")
        sender.sendMessage("§e/rep top [тип] §7- топ игроков")
        sender.sendMessage("§e/rep stats §7- статистика системы")
        
        if (sender.hasPermission("ztelegram.reputation.admin")) {
            sender.sendMessage("§e/rep reset <игрок> §7- сбросить репутацию")
        }
        
        sender.sendMessage("")
        sender.sendMessage("§7Типы топа: §ftotal, positive, negative, percentage")
        sender.sendMessage("§8§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        return when (args.size) {
            1 -> {
                val suggestions = mutableListOf("top", "stats", "help")
                
                // Добавляем имена онлайн игроков
                suggestions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                
                // Добавляем префиксы + и -
                suggestions.addAll(Bukkit.getOnlinePlayers().map { "+${it.name}" })
                suggestions.addAll(Bukkit.getOnlinePlayers().map { "-${it.name}" })
                
                if (sender.hasPermission("ztelegram.reputation.admin")) {
                    suggestions.add("reset")
                }
                
                suggestions.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            
            2 -> {
                when (args[0].lowercase()) {
                    "top", "топ" -> listOf("total", "positive", "negative", "percentage")
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                    
                    "reset", "сброс" -> {
                        if (sender.hasPermission("ztelegram.reputation.admin")) {
                            Bukkit.getOnlinePlayers().map { it.name }
                                .filter { it.startsWith(args[1], ignoreCase = true) }
                        } else emptyList()
                    }
                    
                    else -> emptyList()
                }
            }
            
            else -> emptyList()
        }
    }
}
