// ───────────────────────────────────────────────────────────────────
// ThinkLessApplication, registers every AutomationAction, once
// ───────────────────────────────────────────────────────────────────
// Application.onCreate() is guaranteed to run before ANY other
// component in this app (an Activity, a Service, a BroadcastReceiver
//, including BootReceiver, which can be the first and only thing
// that runs after a device reboot with no Activity ever launching,
// and the future TaskerPluginReceiver, which can fire from Tasker
// with this app's UI never opened). Registering actions here, rather
// than in MainActivity.onCreate, is what makes "a Tasker-fired action
// works even if you've never opened this app since installing it"
// actually true instead of accidentally-true-because-you-happened-to-
// open-the-app-first.
// ───────────────────────────────────────────────────────────────────

package com.landonkea.thinklessschedulemore

import android.app.Application

class ThinkLessApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutomationRegistry.register(SendSmsAction())
    }
}
