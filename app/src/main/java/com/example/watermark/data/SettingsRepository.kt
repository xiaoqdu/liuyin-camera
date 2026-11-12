package com.example.watermark.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "watermark_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        // DataStore keys
        private val KEY_NICKNAME = stringPreferencesKey("nickname")
        private val KEY_TEMPLATE = stringPreferencesKey("watermark_template")
        private val KEY_TEMPLATE_PRESET = intPreferencesKey("watermark_template_preset")
        private val KEY_LOCATION = stringPreferencesKey("location_override")
        private val KEY_TIME_FORMAT = intPreferencesKey("watermark_time_format")
        private val KEY_COLOR = intPreferencesKey("watermark_color")
        private val KEY_SIZE = floatPreferencesKey("watermark_size")
        private val KEY_BG_STYLE = intPreferencesKey("watermark_bg_style")
        private val KEY_BG_COLOR = intPreferencesKey("watermark_bg_color")
        private val KEY_FONT = intPreferencesKey("watermark_font")
        private val KEY_POSITION = intPreferencesKey("watermark_position")
        private val KEY_STYLE = intPreferencesKey("watermark_style")
        private val KEY_LAYOUT = intPreferencesKey("watermark_layout")
        private val KEY_COORDS = booleanPreferencesKey("watermark_show_coords")
        private val KEY_SHOW_WEATHER = booleanPreferencesKey("show_weather")
        private val KEY_SHOW_TEMP = booleanPreferencesKey("show_temperature")
        private val KEY_SHOW_HUMIDITY = booleanPreferencesKey("show_humidity")
        private val KEY_SHOW_WIND = booleanPreferencesKey("show_wind")
        private val KEY_SHOW_ALTITUDE = booleanPreferencesKey("show_altitude")
        private val KEY_SHOW_BEARING = booleanPreferencesKey("show_bearing")
        private val KEY_SHOW_LUNAR = booleanPreferencesKey("show_lunar")
        private val KEY_SHOW_FESTIVAL = booleanPreferencesKey("show_festival")
        private val KEY_SHOW_CONSTELLATION = booleanPreferencesKey("show_constellation")
        private val KEY_SHOW_COUNTDOWN = booleanPreferencesKey("show_countdown")
        private val KEY_SHOW_BATTERY = booleanPreferencesKey("show_battery")
        private val KEY_SHOW_NETWORK = booleanPreferencesKey("show_network")
        private val KEY_EXTRA_NOTE = stringPreferencesKey("extra_note")
        private val KEY_COUNTDOWN_TARGET = stringPreferencesKey("countdown_target")
        private val KEY_COUNTDOWN_LABEL = stringPreferencesKey("countdown_label")
        private val KEY_LOCK_TIME_LOCATION = booleanPreferencesKey("lock_time_location")
        private val KEY_FAVORITE_TEMPLATES = stringPreferencesKey("favorite_templates")
        private val KEY_INDUSTRY_DATA = stringPreferencesKey("industry_data")
        private val KEY_CAMERA_RATIO = stringPreferencesKey("camera_ratio")
        private val KEY_CAMERA_TIMER = intPreferencesKey("camera_timer_sec")
        private val KEY_CAMERA_BURST = intPreferencesKey("camera_burst")
        private val KEY_CAMERA_FLASH = intPreferencesKey("camera_flash")
        private val KEY_VOLUME_SHUTTER = booleanPreferencesKey("volume_shutter")
        private val KEY_SHOW_LEVEL = booleanPreferencesKey("show_level")
        private val KEY_SHOW_GRID = booleanPreferencesKey("show_grid")

        val defaultSettings = WatermarkSettings()

        fun positionFromInt(value: Int): WatermarkPosition =
            WatermarkPosition.values().getOrElse(value) { WatermarkPosition.BOTTOM_RIGHT }

        fun positionToInt(position: WatermarkPosition): Int = position.ordinal

        fun styleFromInt(value: Int): WatermarkStyle =
            WatermarkStyle.values().getOrElse(value) { WatermarkStyle.SOLID }

        fun styleToInt(style: WatermarkStyle): Int = style.ordinal

        fun fontFromInt(value: Int): WatermarkFontFamily =
            WatermarkFontFamily.values().getOrElse(value) { WatermarkFontFamily.DEFAULT }

        fun fontToInt(font: WatermarkFontFamily): Int = font.ordinal

        fun timeFormatFromInt(value: Int): WatermarkTimeFormat =
            WatermarkTimeFormat.values().getOrElse(value) { WatermarkTimeFormat.YYYY_MM_DD_WEEKTIME }

        fun timeFormatToInt(format: WatermarkTimeFormat): Int = format.ordinal

        fun templatePresetFromInt(value: Int): WatermarkTemplatePreset =
            WatermarkTemplatePreset.values().getOrElse(value) { WatermarkTemplatePreset.BASIC }

        fun templatePresetToInt(preset: WatermarkTemplatePreset): Int = preset.ordinal

        fun bgStyleFromInt(value: Int): WatermarkBgStyle =
            WatermarkBgStyle.values().getOrElse(value) { WatermarkBgStyle.SOLID }

        fun bgStyleToInt(bgStyle: WatermarkBgStyle): Int = bgStyle.ordinal

        fun layoutFromInt(value: Int): WatermarkLayout =
            WatermarkLayout.values().getOrElse(value) { WatermarkLayout.STAMP }

        fun layoutToInt(layout: WatermarkLayout): Int = layout.ordinal
    }

    val settingsFlow: Flow<WatermarkSettings> = context.dataStore.data
        .map { prefs ->
            WatermarkSettings(
                nickname = prefs[KEY_NICKNAME] ?: defaultSettings.nickname,
                locationOverride = prefs[KEY_LOCATION] ?: defaultSettings.locationOverride,
                watermarkTimeFormat = timeFormatFromInt(prefs[KEY_TIME_FORMAT] ?: defaultSettings.watermarkTimeFormat.ordinal),
                watermarkColor = prefs[KEY_COLOR] ?: defaultSettings.watermarkColor,
                watermarkSize = prefs[KEY_SIZE] ?: defaultSettings.watermarkSize,
                watermarkFontFamily = fontFromInt(prefs[KEY_FONT] ?: defaultSettings.watermarkFontFamily.ordinal),
                watermarkPosition = positionFromInt(prefs[KEY_POSITION] ?: defaultSettings.watermarkPosition.ordinal),
                watermarkStyle = styleFromInt(prefs[KEY_STYLE] ?: defaultSettings.watermarkStyle.ordinal),
                watermarkTemplatePreset = templatePresetFromInt(prefs[KEY_TEMPLATE_PRESET] ?: defaultSettings.watermarkTemplatePreset.ordinal),
                watermarkBgStyle = bgStyleFromInt(prefs[KEY_BG_STYLE] ?: defaultSettings.watermarkBgStyle.ordinal),
                watermarkBgColor = prefs[KEY_BG_COLOR] ?: defaultSettings.watermarkBgColor,
                watermarkLayout = layoutFromInt(prefs[KEY_LAYOUT] ?: defaultSettings.watermarkLayout.ordinal),
                showCoordinates = prefs[KEY_COORDS] ?: defaultSettings.showCoordinates,
                showWeather = prefs[KEY_SHOW_WEATHER] ?: defaultSettings.showWeather,
                showTemperature = prefs[KEY_SHOW_TEMP] ?: defaultSettings.showTemperature,
                showHumidity = prefs[KEY_SHOW_HUMIDITY] ?: defaultSettings.showHumidity,
                showWind = prefs[KEY_SHOW_WIND] ?: defaultSettings.showWind,
                showAltitude = prefs[KEY_SHOW_ALTITUDE] ?: defaultSettings.showAltitude,
                showBearing = prefs[KEY_SHOW_BEARING] ?: defaultSettings.showBearing,
                showLunar = prefs[KEY_SHOW_LUNAR] ?: defaultSettings.showLunar,
                showFestival = prefs[KEY_SHOW_FESTIVAL] ?: defaultSettings.showFestival,
                showConstellation = prefs[KEY_SHOW_CONSTELLATION] ?: defaultSettings.showConstellation,
                showCountdown = prefs[KEY_SHOW_COUNTDOWN] ?: defaultSettings.showCountdown,
                showBattery = prefs[KEY_SHOW_BATTERY] ?: defaultSettings.showBattery,
                showNetwork = prefs[KEY_SHOW_NETWORK] ?: defaultSettings.showNetwork,
                extraNote = prefs[KEY_EXTRA_NOTE] ?: defaultSettings.extraNote,
                countdownTarget = prefs[KEY_COUNTDOWN_TARGET] ?: defaultSettings.countdownTarget,
                countdownLabel = prefs[KEY_COUNTDOWN_LABEL] ?: defaultSettings.countdownLabel,
                lockTimeLocation = prefs[KEY_LOCK_TIME_LOCATION] ?: defaultSettings.lockTimeLocation,
                favoriteTemplates = prefs[KEY_FAVORITE_TEMPLATES] ?: defaultSettings.favoriteTemplates,
                industryData = prefs[KEY_INDUSTRY_DATA] ?: defaultSettings.industryData,
                cameraRatio = prefs[KEY_CAMERA_RATIO] ?: defaultSettings.cameraRatio,
                cameraTimerSec = prefs[KEY_CAMERA_TIMER] ?: defaultSettings.cameraTimerSec,
                cameraBurst = prefs[KEY_CAMERA_BURST] ?: defaultSettings.cameraBurst,
                cameraFlash = prefs[KEY_CAMERA_FLASH] ?: defaultSettings.cameraFlash,
                volumeShutter = prefs[KEY_VOLUME_SHUTTER] ?: defaultSettings.volumeShutter,
                showLevel = prefs[KEY_SHOW_LEVEL] ?: defaultSettings.showLevel,
                showGrid = prefs[KEY_SHOW_GRID] ?: defaultSettings.showGrid,
                watermarkTemplate = when (val preset = templatePresetFromInt(prefs[KEY_TEMPLATE_PRESET] ?: defaultSettings.watermarkTemplatePreset.ordinal)) {
                    WatermarkTemplatePreset.CUSTOM -> prefs[KEY_TEMPLATE] ?: defaultSettings.watermarkTemplate
                    else -> preset.template
                }
            )
        }

    suspend fun saveSettings(settings: WatermarkSettings) {
        context.dataStore.edit { prefs ->
            prefs[KEY_NICKNAME] = settings.nickname
            prefs[KEY_TEMPLATE] = when (settings.watermarkTemplatePreset) {
                WatermarkTemplatePreset.CUSTOM -> settings.watermarkTemplate
                else -> ""
            }
            prefs[KEY_TEMPLATE_PRESET] = templatePresetToInt(settings.watermarkTemplatePreset)
            prefs[KEY_BG_STYLE] = bgStyleToInt(settings.watermarkBgStyle)
            prefs[KEY_BG_COLOR] = settings.watermarkBgColor
            prefs[KEY_LOCATION] = settings.locationOverride
            prefs[KEY_TIME_FORMAT] = timeFormatToInt(settings.watermarkTimeFormat)
            prefs[KEY_COLOR] = settings.watermarkColor
            prefs[KEY_SIZE] = settings.watermarkSize
            prefs[KEY_FONT] = fontToInt(settings.watermarkFontFamily)
            prefs[KEY_POSITION] = positionToInt(settings.watermarkPosition)
            prefs[KEY_STYLE] = styleToInt(settings.watermarkStyle)
            prefs[KEY_LAYOUT] = layoutToInt(settings.watermarkLayout)
            prefs[KEY_COORDS] = settings.showCoordinates
            prefs[KEY_SHOW_WEATHER] = settings.showWeather
            prefs[KEY_SHOW_TEMP] = settings.showTemperature
            prefs[KEY_SHOW_HUMIDITY] = settings.showHumidity
            prefs[KEY_SHOW_WIND] = settings.showWind
            prefs[KEY_SHOW_ALTITUDE] = settings.showAltitude
            prefs[KEY_SHOW_BEARING] = settings.showBearing
            prefs[KEY_SHOW_LUNAR] = settings.showLunar
            prefs[KEY_SHOW_FESTIVAL] = settings.showFestival
            prefs[KEY_SHOW_CONSTELLATION] = settings.showConstellation
            prefs[KEY_SHOW_COUNTDOWN] = settings.showCountdown
            prefs[KEY_SHOW_BATTERY] = settings.showBattery
            prefs[KEY_SHOW_NETWORK] = settings.showNetwork
            prefs[KEY_EXTRA_NOTE] = settings.extraNote
            prefs[KEY_COUNTDOWN_TARGET] = settings.countdownTarget
            prefs[KEY_COUNTDOWN_LABEL] = settings.countdownLabel
            prefs[KEY_LOCK_TIME_LOCATION] = settings.lockTimeLocation
            prefs[KEY_FAVORITE_TEMPLATES] = settings.favoriteTemplates
            prefs[KEY_INDUSTRY_DATA] = settings.industryData
            prefs[KEY_CAMERA_RATIO] = settings.cameraRatio
            prefs[KEY_CAMERA_TIMER] = settings.cameraTimerSec
            prefs[KEY_CAMERA_BURST] = settings.cameraBurst
            prefs[KEY_CAMERA_FLASH] = settings.cameraFlash
            prefs[KEY_VOLUME_SHUTTER] = settings.volumeShutter
            prefs[KEY_SHOW_LEVEL] = settings.showLevel
            prefs[KEY_SHOW_GRID] = settings.showGrid
        }
    }
}
