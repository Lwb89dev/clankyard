package dev.clankyard.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

internal object TextMateBootstrap {
    const val THEME_NAME = "workshop-night"
    private const val THEME_PATH = "textmate/workshop-night.json"
    private const val LANGUAGES_PATH = "textmate/languages.json"

    @Volatile
    private var loaded = false
    private val lock = Any()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            loadLocked(context.applicationContext)
            loaded = true
        }
    }

    private fun loadLocked(context: Context) {
        val files = FileProviderRegistry.getInstance()
        files.addFileProvider(AssetsFileResolver(context.assets))
        val themeSource = IThemeSource.fromInputStream(
            files.tryGetInputStream(THEME_PATH),
            THEME_PATH,
            null,
        )
        val theme = ThemeModel(themeSource, THEME_NAME)
        theme.isDark = true
        val themes = ThemeRegistry.getInstance()
        themes.loadTheme(theme)
        themes.setTheme(THEME_NAME)
        GrammarRegistry.getInstance().loadGrammars(LANGUAGES_PATH)
    }
}
