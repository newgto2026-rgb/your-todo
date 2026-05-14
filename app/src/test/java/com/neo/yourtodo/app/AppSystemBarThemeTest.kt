package com.neo.yourtodo.app

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Test

class AppSystemBarThemeTest {

    @Test
    fun appThemes_keepSystemBarButtonsVisibleOnLightSurfaces() {
        val baseThemeFiles = listOf(
            "src/main/res/values/themes.xml",
            "src/main/res/values-night/themes.xml"
        )

        baseThemeFiles.forEach { themePath ->
            val themeXml = Path.of(themePath).readText()

            assertThat(themeXml).contains(
                """<item name="android:windowLightStatusBar">true</item>"""
            )
        }

        val navigationThemeFiles = listOf(
            "src/main/res/values-v27/themes.xml",
            "src/main/res/values-night-v27/themes.xml"
        )

        navigationThemeFiles.forEach { themePath ->
            val themeXml = Path.of(themePath).readText()

            assertThat(themeXml).contains(
                """<item name="android:windowLightNavigationBar">true</item>"""
            )
        }
    }
}
