package com.reelscore.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reelscore.app.ui.app.AppDetailScreen
import com.reelscore.app.ui.distance.DistanceScreen
import com.reelscore.app.ui.home.HomeScreen
import com.reelscore.app.ui.insights.InsightsScreen
import com.reelscore.app.ui.components.BatteryPrompt
import com.reelscore.app.ui.components.FloatingNavBar
import com.reelscore.app.ui.components.NavTab
import com.reelscore.app.ui.components.SharedScopes
import com.reelscore.app.ui.components.TabHost
import com.reelscore.app.ui.friends.FriendsScreen
import com.reelscore.app.ui.journey.JourneyScreen
import com.reelscore.app.ui.settings.GalleryScreen
import com.reelscore.app.ui.settings.HelpScreen
import com.reelscore.app.ui.settings.SettingsScreen
import com.reelscore.app.ui.you.YouScreen

private object Routes {
    const val TABS = "tabs"
    const val APP = "app/{pkg}"
    const val SETTINGS = "settings"
    const val HELP = "help"
    const val JOURNEY = "journey"
    const val GALLERY = "gallery"
    const val DISTANCE = "distance"
    fun app(pkg: String) = "app/$pkg"
}

private object Tabs {
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val FRIENDS = "friends"
    const val YOU = "you"
}

private val tabs = listOf(
    NavTab(Tabs.HOME, "Home", Icons.Rounded.Home),
    NavTab(Tabs.INSIGHTS, "Insights", Icons.Rounded.BarChart),
    NavTab(Tabs.FRIENDS, "Friends", Icons.Rounded.Group),
    NavTab(Tabs.YOU, "You", Icons.Rounded.Person),
)
private val tabIds = tabs.map { it.route }

private const val PAGE_FADE_MS = 260

/**
 * Tabs live in one destination ([TabHost]) so switching never rebuilds them. The NavHost only
 * handles pages that stack on top: app detail, settings and help.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val onTabs = entry?.destination?.route.let { it == null || it == Routes.TABS }
    var selectedTab by rememberSaveable { mutableStateOf(Tabs.HOME) }

    // A Surface, not a plain background, so text everywhere defaults to onBackground in both themes.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            NavHost(
                navController = nav,
                startDestination = Routes.TABS,
                enterTransition = { fadeIn(tween(PAGE_FADE_MS)) },
                exitTransition = { fadeOut(tween(PAGE_FADE_MS)) },
                popEnterTransition = { fadeIn(tween(PAGE_FADE_MS)) },
                popExitTransition = { fadeOut(tween(PAGE_FADE_MS)) },
            ) {
                composable(Routes.TABS) {
                    // Remembered so it's the same object on every recomposition: a new instance
                    // would make Compose re-run every tab on each tab switch.
                    val scopes = remember(this) { SharedScopes(this@SharedTransitionLayout, this) }
                    // Back on another tab returns to Home before leaving the app.
                    BackHandler(enabled = selectedTab != Tabs.HOME) { selectedTab = Tabs.HOME }
                    TabHost(selected = selectedTab, allTabs = tabIds, modifier = Modifier.fillMaxSize()) { tab ->
                        when (tab) {
                            Tabs.HOME -> HomeScreen(
                                scopes = scopes,
                                onOpenApp = { nav.navigate(Routes.app(it)) },
                                onTurnOn = { selectedTab = Tabs.YOU },
                                onOpenDistance = { nav.navigate(Routes.DISTANCE) },
                                onOpenInsights = { selectedTab = Tabs.INSIGHTS },
                                onOpenFriends = { selectedTab = Tabs.FRIENDS },
                                onOpenYou = { selectedTab = Tabs.YOU },
                            )
                            Tabs.INSIGHTS -> InsightsScreen(
                                scopes = scopes,
                                onOpenApp = { nav.navigate(Routes.app(it)) },
                                onOpenDistance = { nav.navigate(Routes.DISTANCE) },
                            )
                            Tabs.FRIENDS -> FriendsScreen()
                            Tabs.YOU -> YouScreen(
                                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                                onStartJourney = { nav.navigate(Routes.JOURNEY) },
                            )
                        }
                    }
                }
                composable(Routes.APP) { backStack ->
                    AppDetailScreen(
                        pkg = backStack.arguments?.getString("pkg").orEmpty(),
                        scopes = SharedScopes(this@SharedTransitionLayout, this),
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenHelp = { nav.navigate(Routes.HELP) },
                        onOpenGallery = { nav.navigate(Routes.GALLERY) },
                    )
                }
                composable(Routes.HELP) { HelpScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.DISTANCE) { DistanceScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.GALLERY) { GalleryScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.JOURNEY) {
                    JourneyScreen(onBack = { nav.popBackStack() }, onStarted = { nav.popBackStack() })
                }
            }
        }

        // A backdrop behind the status bar, so scrolled content doesn't run under the clock.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f)),
        )

        AnimatedVisibility(
            visible = onTabs,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            FloatingNavBar(
                tabs = tabs,
                selectedRoute = selectedTab,
                onSelect = { selectedTab = it.route },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp),
            )
        }

        BatteryPrompt()
    } }
}
