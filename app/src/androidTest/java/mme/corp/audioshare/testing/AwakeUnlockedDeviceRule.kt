package mme.corp.audioshare.testing

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream

/**
 * Device-only UI-test precondition. The emulator may turn its display off between
 * instrumented tests, which causes ActivityScenario/Compose/Espresso to lose the
 * RESUMED activity and produces misleading failures in unrelated assertions.
 *
 * This rule runs OUTSIDE createComposeRule(), so the display is awake before
 * Compose's host Activity is launched. It never changes production behavior or
 * persistent screen-timeout settings.
 */
class AwakeUnlockedDeviceRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val automation = instrumentation.uiAutomation
                // KEYCODE_WAKEUP (224) is not a toggle: it cannot turn an already-on display off.
                listOf("input keyevent 224", "wm dismiss-keyguard").forEach { command ->
                    automation.executeShellCommand(command).use { descriptor ->
                        FileInputStream(descriptor.fileDescriptor).use { stream ->
                            val buffer = ByteArray(256)
                            while (stream.read(buffer) != -1) { /* drain command output */
                            }
                        }
                    }
                }

                val context = instrumentation.targetContext
                val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                check(power.isInteractive && !keyguard.isKeyguardLocked) {
                    "Android UI tests require an awake, unlocked device/emulator. " +
                            "Unlock it and rerun connectedDebugAndroidTest."
                }
                base.evaluate()
            }
        }
}
