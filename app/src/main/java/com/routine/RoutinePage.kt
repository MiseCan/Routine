@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Chrome belongs to its destination, so the complete page participates in back preview. */
@Composable internal fun RoutinePage(page:String,title:String,onBack:()->Unit,onAction:()->Unit,
    onSelectPage:(String)->Unit,onTitleClick:(()->Unit)?=null,actionEnabled:Boolean=true,
    content:@Composable ()->Unit){
    val hideNavigationForKeyboard=page=="chat"&&WindowInsets.isImeVisible
    Scaffold(
        modifier=Modifier.imePadding(),
        topBar={RoutineTopBar(title=title,showBack=page in listOf("settings","about","plan"),
            onBack=onBack,onTitleClick=onTitleClick,
            action=when(page){"chat"->TopBarAction.ClearChat;"board"->TopBarAction.Settings;else->TopBarAction.None},
            actionEnabled=actionEnabled,onAction=onAction)},
        bottomBar={if(page in listOf("board","chat")&&!hideNavigationForKeyboard)NavigationBar{
            NavigationBarItem(selected=page=="board",onClick={onSelectPage("board")},icon={Icon(Icons.Default.Home,null)},label={Text("看板")})
            NavigationBarItem(selected=page=="chat",onClick={onSelectPage("chat")},icon={Icon(Icons.Default.Email,null)},label={Text("AI 聊天")})
        }}
    ){padding->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),contentAlignment=Alignment.TopCenter){
            Box(Modifier.widthIn(max=840.dp).fillMaxSize()){content()}
        }
    }
}
