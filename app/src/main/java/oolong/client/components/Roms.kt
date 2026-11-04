package oolong.client.components

import android.os.Build

/**
 * The ROM a device runs, as its family and major version ("HyperOS 2", "ColorOS 15"), read from the
 * properties each vendor sets; empty when none is recognised. Build.DISPLAY differs too much from
 * vendor to vendor to be read for this.
 */
object Roms {
    fun family(): String = runCatching { detect() }.getOrNull().orEmpty()

    /**
     * The ROM's own build number. Most vendors write it into Build.DISPLAY; HyperOS and MIUI leave
     * Android's build id there ("CP2A.260605.016") and put theirs ("OS2.0.6.0.VMCCNXM") in
     * Build.VERSION.INCREMENTAL.
     */
    fun version(): String {
        val xiaomi = prop("ro.mi.os.version.name") != null || prop("ro.miui.ui.version.name") != null
        return (if (xiaomi) Build.VERSION.INCREMENTAL else Build.DISPLAY).orEmpty()
    }

    private fun detect(): String? {
        prop("ro.mi.os.version.name")?.let { name ->
            // "OS2.0" and the like
            return major(name)?.let { "HyperOS $it" } ?: "HyperOS"
        }
        prop("ro.miui.ui.version.name")?.let { name -> return major(name)?.let { "MIUI $it" } ?: "MIUI" }
        prop("ro.build.version.oplusrom")?.let { version ->
            val name = if (Build.MANUFACTURER.equals("OnePlus", true) && prop("ro.oxygen.version") != null) "OxygenOS" else "ColorOS"
            return major(version)?.let { "$name $it" } ?: name
        }
        prop("ro.vivo.os.version")?.let { version ->
            val name = if (prop("ro.vivo.os.name")?.contains("Funtouch", true) == true) "Funtouch OS" else "OriginOS"
            return major(version)?.let { "$name $it" } ?: name
        }
        prop("ro.build.version.magic")?.let { version -> return major(version)?.let { "MagicOS $it" } ?: "MagicOS" }
        if (huaweiOsBrand() == "harmony") {
            return major(prop("hw_sc.build.platform.version").orEmpty())?.let { "HarmonyOS $it" } ?: "HarmonyOS"
        }
        prop("ro.build.version.emui")?.let { version -> return major(version)?.let { "EMUI $it" } ?: "EMUI" }
        oneUi()?.let { return "One UI $it" }
        prop("ro.build.display.id")?.let { id ->
            Regex("""Flyme\s*(?:OS\s*)?(\d+)""", RegexOption.IGNORE_CASE).find(id)?.let { return "Flyme ${it.groupValues[1]}" }
        }
        return null
    }

    /** The first number in [text]: "OS2.0" → 2, "V140" → 14 (MIUI writes its version times ten), "EmotionUI_13.0" → 13. */
    private fun major(text: String): Int? {
        val digits = Regex("""\d+""").find(text)?.value?.toIntOrNull() ?: return null
        return if (text.startsWith("V") && digits >= 100) digits / 10 else digits
    }

    private fun prop(name: String): String? = runCatching {
        val get = Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
        (get.invoke(null, name) as? String)?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun huaweiOsBrand(): String? = runCatching {
        Class.forName("com.huawei.system.BuildEx").getMethod("getOsBrand").invoke(null) as? String
    }.getOrNull()

    /** One UI's major version from its own property, written major × 10000 + minor × 100 + patch. */
    private fun oneUi(): Int? = prop("ro.build.version.oneui")?.toIntOrNull()?.div(10000)?.takeIf { it in 1..20 }
}
