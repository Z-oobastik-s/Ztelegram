package org.zoobastiks.ztelegram.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.zoobastiks.ztelegram.ZTele
import org.zoobastiks.ztelegram.utils.TopManager

/**
 * Команда для управления кэшем топов
 * Использование: /ztelegram topcache [clear|stats|clear-playtime|clear-balance]
 * 
 * @author Zoobastiks
 */
class TopCacheCommand : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (!sender.hasPermission("ztelegram.admin")) {
            sender.sendMessage("§cУ вас нет прав для использования этой команды!")
            return true
        }
        
        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "clear" -> {
                TopManager.clearCache()
                sender.sendMessage("§a[ZTelegram] Кэш топов полностью очищен!")
            }
            
            "clear-playtime" -> {
                TopManager.clearCache(TopManager.TopType.PLAYTIME)
                sender.sendMessage("§a[ZTelegram] Кэш топов по времени игры очищен!")
            }
            
            "clear-balance" -> {
                TopManager.clearCache(TopManager.TopType.BALANCE)
                sender.sendMessage("§a[ZTelegram] Кэш топов по балансу очищен!")
            }
            
            "stats" -> {
                val stats = TopManager.getCacheStats()
                sender.sendMessage("§e[ZTelegram] Статистика кэша топов:")
                sender.sendMessage("§7Всего записей: §f${stats.totalEntries}")
                sender.sendMessage("§7Топы по времени: §f${stats.playtimeEntries}")
                sender.sendMessage("§7Топы по балансу: §f${stats.balanceEntries}")
            }
            
            else -> {
                showUsage(sender)
            }
        }
        
        return true
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String>? {
        if (!sender.hasPermission("ztelegram.admin")) {
            return emptyList()
        }
        
        return when (args.size) {
            1 -> listOf("clear", "clear-playtime", "clear-balance", "stats")
                .filter { it.startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
    
    private fun showUsage(sender: CommandSender) {
        sender.sendMessage("§e[ZTelegram] Управление кэшем топов:")
        sender.sendMessage("§7/ztelegram topcache clear §f- очистить весь кэш")
        sender.sendMessage("§7/ztelegram topcache clear-playtime §f- очистить кэш времени игры")
        sender.sendMessage("§7/ztelegram topcache clear-balance §f- очистить кэш балансов")
        sender.sendMessage("§7/ztelegram topcache stats §f- показать статистику кэша")
    }
}
