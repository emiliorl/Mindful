package com.mindshield.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.mindshield.app.onboarding.PermissionGate
import com.mindshield.app.shell.AppShell
import com.mindshield.app.ui.theme.MindShieldTheme
import com.mindshield.app.util.OnboardingPrefs

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MindShieldTheme {
                // Remember whether onboarding has been completed across recompositions.
                // The actual source-of-truth write happens inside PermissionGate when the
                // user finishes the final step.
                var onboardingDone by rememberSaveable {
                    mutableStateOf(OnboardingPrefs.isComplete(this))
                }

                if (onboardingDone) {
                    AppShell()
                } else {
                    PermissionGate(
                        onFinished = {
                            OnboardingPrefs.markComplete(this)
                            onboardingDone = true
                        }
                    )
                }
            }
        }
    }
}
