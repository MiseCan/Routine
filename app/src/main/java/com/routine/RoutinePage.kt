@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.routine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Chrome belongs to its destination, so the complete page participates in back preview. */
@Composable internal fun RoutinePage(page:String,title:String,onBack:()->Unit,onAction:()->Unit,
    onSelectPage:(String)->Unit,onTitleClick:(()->Unit)?=null,actionEnabled:Boolean=true,
    content:@Composable ()->Unit){
    val hideNavigationForKeyboard=page=="chat"&&WindowInsets.isImeVisible
    val boardScrollBehavior = if (page == "board") TopAppBarDefaults.exitUntilCollapsedScrollBehavior() else null
    val pageModifier = Modifier.imePadding()
    Scaffold(
        modifier = if (boardScrollBehavior == null) pageModifier
            else pageModifier.nestedScroll(boardScrollBehavior.nestedScrollConnection),
        topBar = {
            if (boardScrollBehavior != null) {
                MediumTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        IconButton(onClick = onAction, enabled = actionEnabled, shapes = IconButtonDefaults.shapes()) {
                            Icon(MaterialSymbols.Settings, "设置")
                        }
                    },
                    scrollBehavior = boardScrollBehavior,
                )
            } else TopAppBar(
                title = {
                    if (onTitleClick == null) {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Row(
                            modifier = Modifier
                                .clickable(role = Role.Button, onClick = onTitleClick)
                                .minimumInteractiveComponentSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(title, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.Default.ArrowDropDown, "选择对话计划")
                        }
                    }
                },
                navigationIcon = {
                    if (page in listOf("settings", "about", "plan")) {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (page == "chat") {
                        IconButton(onClick = onAction, enabled = actionEnabled, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Default.Delete, "清空聊天")
                        }
                    }
                },
            )
        },
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
