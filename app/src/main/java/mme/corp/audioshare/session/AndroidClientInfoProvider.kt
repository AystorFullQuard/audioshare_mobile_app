package mme.corp.audioshare.session

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class AndroidClientInfoProvider(
    private val context: Context
) {
    fun get(): BackendSessionStartupRequest {
        val model = Build.MODEL.orEmpty().ifBlank { "Android device" }
        val manufacturer = Build.MANUFACTURER.orEmpty()
            .trim()
            .takeIf(String::isNotEmpty)

        val deviceName = manufacturer
            ?.let { "$it $model" }
            ?: model

        return BackendSessionStartupRequest(
            displayName = model,
            deviceName = deviceName,
            appVersion = getAppVersion()
        )
    }

    private fun getAppVersion(): String? {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        return packageInfo.versionName
    }
}
