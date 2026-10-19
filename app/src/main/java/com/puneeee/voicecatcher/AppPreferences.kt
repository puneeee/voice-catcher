package com.puneeee.voicecatcher

import android.content.Context

enum class ThemeChoice(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

object AppearancePreferences {
    fun theme(context: Context): ThemeChoice = runCatching {
        ThemeChoice.valueOf(context.getSharedPreferences("appearance", Context.MODE_PRIVATE).getString("theme", "SYSTEM")!!)
    }.getOrDefault(ThemeChoice.SYSTEM)

    fun setTheme(context: Context, choice: ThemeChoice) {
        context.getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("theme", choice.name).apply()
    }
}

/** Explainable local suggestions. Explicit category selection in the editor always wins. */
object CategorySuggestions {
    private val rules = listOf(
        TaskCategory.HEALTH to Regex("""\b(bath|bathe|shower|run|running|jog|exercise|workout|walk|medicine|medication|doctor|hospital|lunch|dinner|breakfast|water|sleep|yoga)\b""", RegexOption.IGNORE_CASE),
        TaskCategory.WORK to Regex("""\b(app status|check the app|application|project|meeting|client|report|office|deploy|deployment|bug|code|email|deadline)\b""", RegexOption.IGNORE_CASE),
        TaskCategory.LEARNING to Regex("""\b(study|learn|read|reading|course|class|lesson|exam|practice|homework)\b""", RegexOption.IGNORE_CASE),
        TaskCategory.ERRANDS to Regex("""\b(buy|shop|shopping|groceries|grocery|collect|pickup|pick up|bank|post office|pharmacy)\b""", RegexOption.IGNORE_CASE),
        TaskCategory.PERSONAL to Regex("""\b(family|friend|birthday|call|movie|clean|laundry|home|pay|bill)\b""", RegexOption.IGNORE_CASE),
    )
    fun suggest(title: String): TaskCategory = rules.firstOrNull { it.second.containsMatchIn(title) }?.first ?: TaskCategory.GENERAL

    fun migrateExisting(context: Context) {
        val prefs = context.getSharedPreferences("category_suggestions", Context.MODE_PRIVATE)
        if (prefs.getBoolean("initial_suggestions_done", false)) return
        val store = TaskStore(context)
        store.load().filter { it.status == TaskStatus.OPEN && it.category == TaskCategory.GENERAL }.forEach { task ->
            val suggested = suggest(task.title)
            if (suggested != TaskCategory.GENERAL) store.upsert(task.copy(category = suggested))
        }
        prefs.edit().putBoolean("initial_suggestions_done", true).apply()
    }
}
