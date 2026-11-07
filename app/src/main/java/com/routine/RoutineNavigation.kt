@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import org.json.JSONObject
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import java.util.UUID

/** Stable keys survive saved-instance restoration and identify Miuix's entry state. */
internal data class RoutineRoute(val path:String,val id:String=UUID.randomUUID().toString()):NavKey {
    val screen get()=path.substringBefore('/')
    val planId get()=if(screen=="plan")Uri.decode(path.substringAfter('/'))else ""
}

/** Only route data lives here. Miuix owns rendering, back gestures, effects and settling. */
internal class RoutineNavigator(
    val backStack:NavBackStack=navBackStackOf(RoutineRoute("root")),
    selectedTab:String="board",
){
    var topLevel by mutableStateOf(selectedTab);private set
    val currentScreen get()=(backStack.last() as RoutineRoute).let{if(it.path=="root")topLevel else it.screen}
    fun openTopLevel(route:String){
        require(route in setOf("board","chat"))
        while(backStack.size>1)backStack.removeAt(backStack.lastIndex)
        topLevel=route
    }
    fun navigate(path:String){
        if(path in setOf("board","chat")){openTopLevel(path);return}
        require(path in setOf("settings","about")||path.startsWith("plan/"))
        if((backStack.last() as RoutineRoute).path!=path)backStack.add(RoutineRoute(path))
    }
    fun openPlan(id:String){navigate("plan/${Uri.encode(id)}")}
    fun popBackStack():Boolean {
        if(backStack.size<=1)return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
    fun navigateUp()=popBackStack()
    companion object {
        val StateSaver=Saver<RoutineNavigator,ArrayList<String>>(
            save={nav->arrayListOf(nav.topLevel).apply{nav.backStack.forEach{key->
                val route=key as RoutineRoute
                add(JSONObject().put("path",route.path).put("id",route.id).toString())
            }}},
            restore={saved->
                val routes=saved.drop(1).map{val json=JSONObject(it);RoutineRoute(json.getString("path"),json.getString("id"))}
                RoutineNavigator(navBackStackOf(*routes.toTypedArray()),saved.first())
            },
        )
    }
}

@Composable internal fun rememberRoutineNavigator()=rememberSaveable(saver=RoutineNavigator.StateSaver){RoutineNavigator()}

@Composable internal fun RoutineNavigation(controller:RoutineNavigator,modifier:Modifier=Modifier,screen:@Composable (String,String)->Unit){
    val render by rememberUpdatedState(screen)
    val swipe=if(LocalLayoutDirection.current==LayoutDirection.Rtl)NavSwipeDirection.RightToLeft else NavSwipeDirection.LeftToRight
    NavDisplay(backStack=controller.backStack,modifier=modifier.clipToBounds(),
        transition=NavTransitions.MiuixDefault,
        effects=NavDisplayEffects(cornerClipRadius=rememberNavSystemCornerRadius(),backdropColor=MaterialTheme.colorScheme.background),
        onBack={controller.popBackStack()}){
        entry<RoutineRoute>(contentKey={it.id},swipeDismiss=swipe){route->
            val active=(controller.backStack.last() as RoutineRoute).id==route.id
            Box(Modifier.fillMaxSize().then(if(active)Modifier else Modifier.clearAndSetSemantics{})){
                if(route.path=="root"){
                    // Peer tabs share one root entry; only nested destinations change stack depth.
                    val stateHolder=rememberSaveableStateHolder()
                    Crossfade(controller.topLevel,animationSpec=MaterialTheme.motionScheme.defaultEffectsSpec(),label="top-level-tabs"){tab->
                        stateHolder.SaveableStateProvider(tab){
                            Box(Modifier.fillMaxSize().then(if(tab==controller.topLevel)Modifier else Modifier.clearAndSetSemantics{})){render(tab,"")}
                        }
                    }
                }else render(route.screen,route.planId)
            }
        }
    }
}
