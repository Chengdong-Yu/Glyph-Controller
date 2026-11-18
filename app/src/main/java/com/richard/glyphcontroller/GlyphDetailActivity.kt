package com.richard.glyphcontroller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.rememberMochiToastState
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.icons.MochiIonicons
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.utils.mochiEnableEdgeToEdge

/** Standalone 33-zone Studio activity sharing [GlyphWorkbenchController] and Mochi theme engine. */
class GlyphDetailActivity : ComponentActivity() {
    private lateinit var controller: GlyphWorkbenchController

    override fun onCreate(savedInstanceState: Bundle?) {
        mochiEnableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            MochiIonicons.preload(applicationContext)
        }

        controller = GlyphWorkbenchController(this, savedInstanceState)

        setContent {
            GlyphAppTheme(
                colorMode = controller.colorMode,
                useMonet = controller.useMonet,
                styleVariant = controller.styleVariant,
                seedColor = controller.seedColor,
                serifTitles = controller.serifTitles,
            ) {
                val nav = rememberMochiNavigator(ROUTE_STUDIO)
                val toastState = rememberMochiToastState()
                val studioTitle = stringResource(R.string.detail_title)
                val vaultTitle = stringResource(R.string.vault_title)
                val routeTitle: (String) -> String = { route ->
                    if (route == ROUTE_SCENES) vaultTitle else studioTitle
                }

                DisposableEffect(controller, toastState) {
                    controller.onFeedback = { title, iconName ->
                        toastState.show(
                            title = title,
                            icon = iconName?.let { captured ->
                                { tint -> MochiIonicon(captured, tint = tint, size = 20.dp) }
                            },
                        )
                    }
                    onDispose {
                        controller.onFeedback = null
                    }
                }

                MochiNavHost(
                    navigator = nav,
                    modifier = Modifier.fillMaxSize(),
                ) { route ->
                    when (route) {
                        ROUTE_STUDIO -> GlyphStudioPage(
                            controller = controller,
                            onOpenScenesFromBar = { nav.push(ROUTE_SCENES) },
                            onOpenScenesFromCard = { nav.push(ROUTE_SCENES, from = GlyphNavIds.ScenesCardSource) },
                            navigationIcon = { MochiBackButton(history = routeTitle) },
                        )

                        ROUTE_SCENES -> GlyphSceneVaultPage(
                            controller = controller,
                            routeTitle = routeTitle,
                            onLoadedIntoStudio = { nav.pop() },
                        )
                    }
                }

                GlyphWorkbenchDialogs(controller = controller)
                MochiToastHost(state = toastState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controller.onResume()
    }

    override fun onPause() {
        controller.onPause()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        controller.onWindowFocusChanged(hasFocus)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        controller.saveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val ROUTE_STUDIO = "studio"
        private const val ROUTE_SCENES = "scenes"
    }
}
