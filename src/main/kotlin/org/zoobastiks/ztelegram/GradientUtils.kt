package org.zoobastiks.ztelegram

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object GradientUtils {
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacySection()

    /**
     * Converts a string with gradient and legacy colors to a component.
     * Example: <gradient:#FF0000:#A6EB0F>Text</gradient> &aOther text
     *
     * @param text The text to parse
     * @return The parsed Component
     */
    fun parseMixedFormat(text: String): Component {
        // Process real newlines
        val processedText = text.replace("\\n", "\n")
        
        try {
            // Если текст уже содержит MiniMessage теги, пробуем его обработать напрямую
            if (processedText.contains("<") && processedText.contains(">")) {
                // Преобразуем стандартные цветовые коды в MiniMessage формат
                val convertedText = processedText.replace("&", "§") // Стандартизируем на §
                    .replace(Regex("§([0-9a-fk-orA-FK-OR])")) { match ->
                        val code = match.groupValues[1]
                        "<${getMiniMessageColor(code)}>"
                    }
                
                return miniMessage.deserialize(convertedText)
            } else {
                // Для текста только с обычными цветовыми кодами
                return legacySerializer.deserialize(
                    processedText.replace("&", "§")
                )
            }
        } catch (e: Exception) {
            // В случае ошибки, возвращаем простой текст
            return Component.text(processedText)
                .color(NamedTextColor.WHITE)
        }
    }

    /**
     * Converts a Component to a legacy string with § color codes
     *
     * @param component The Component to convert
     * @return A string with legacy color codes
     */
    fun componentToLegacy(component: Component): String {
        return legacySerializer.serialize(component)
    }
    
    /**
     * Parse a string with MiniMessage format into a Component
     *
     * @param text The MiniMessage text to parse
     * @return The parsed Component
     */
    fun parseMinimessage(text: String): Component {
        val processedText = text.replace("\\n", "\n")
        
        return try {
            miniMessage.deserialize(processedText)
        } catch (e: Exception) {
            Component.text(processedText)
        }
    }
    
    /**
     * Converts a legacy color code to a MiniMessage color name
     *
     * @param code The legacy color code (0-9, a-f, k-o, r)
     * @return The corresponding MiniMessage color name
     */
    private fun getMiniMessageColor(code: String): String {
        return when (code.lowercase()) {
            "0" -> "black"
            "1" -> "dark_blue"
            "2" -> "dark_green"
            "3" -> "dark_aqua"
            "4" -> "dark_red"
            "5" -> "dark_purple"
            "6" -> "gold"
            "7" -> "gray"
            "8" -> "dark_gray"
            "9" -> "blue"
            "a" -> "green"
            "b" -> "aqua"
            "c" -> "red"
            "d" -> "light_purple"
            "e" -> "yellow"
            "f" -> "white"
            "k" -> "obfuscated"
            "l" -> "bold"
            "m" -> "strikethrough"
            "n" -> "underlined"
            "o" -> "italic"
            "r" -> "reset"
            else -> "white"
        }
    }
}