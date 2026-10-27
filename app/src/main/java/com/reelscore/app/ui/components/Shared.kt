package com.reelscore.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.reelscore.app.service.ReelCounterService

/** The scopes a screen needs to take part in the app row -> app detail morph. */
@OptIn(ExperimentalSharedTransitionApi::class)
class SharedScopes(val shared: SharedTransitionScope, val anim: AnimatedVisibilityScope)

/** The container that morphs between an app's row and its detail header. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAppContainer(scopes: SharedScopes, pkg: String): Modifier = with(scopes.shared) {
    this@sharedAppContainer.sharedBounds(rememberSharedContentState("app-$pkg"), scopes.anim)
}

/** The app icon, which flies from the row to the detail header. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedAppIcon(scopes: SharedScopes, pkg: String): Modifier = with(scopes.shared) {
    this@sharedAppIcon.sharedElement(rememberSharedContentState("icon-$pkg"), scopes.anim)
}

/** Whether the counter is switched on in Accessibility settings, rechecked whenever the screen resumes. */
@Composable
fun rememberCounterEnabled(): Boolean {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(ReelCounterService.isEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        enabled = ReelCounterService.isEnabled(context)
        onPauseOrDispose { }
    }
    return enabled
}

/** Content padding for a tab: clear of the status bar on top and the floating nav bar at the bottom. */
@Composable
fun tabContentPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 20.dp, end = 20.dp, top = top + 28.dp, bottom = bottom + 112.dp)
}
