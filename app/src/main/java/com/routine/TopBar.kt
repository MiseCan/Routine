@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.routine

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class TopBarAction { None, ClearChat, Settings }

/** Stable app-bar slots: only the space before the title changes, on every animation frame. */
@Composable internal fun RoutineTopBar(title:String,showBack:Boolean,backDescription:String="返回",onBack:()->Unit,action:TopBarAction,actionEnabled:Boolean=true,onTitleClick:(()->Unit)?=null,onAction:()->Unit){
    val motion=MaterialTheme.motionScheme
    val navigation=updateTransition(showBack,label="topbar-navigation")
    val space by navigation.animateDp(transitionSpec={motion.defaultSpatialSpec()},label="title-leading-space"){if(it)56.dp else 0.dp}
    val opacity by navigation.animateFloat(transitionSpec={motion.fastEffectsSpec()},label="back-opacity"){if(it)1f else 0f}
    val scale by navigation.animateFloat(transitionSpec={motion.defaultSpatialSpec()},label="back-scale"){if(it)1f else .8f}
    TopAppBar(title={
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            // Retain the button even at zero width so removing it never triggers a new title inset.
            Box(Modifier.width(space.coerceAtLeast(0.dp)).height(48.dp).clipToBounds()){
                IconButton(onClick=onBack,enabled=showBack,shapes=IconButtonDefaults.shapes(),modifier=Modifier.requiredSize(48.dp).graphicsLayer{alpha=opacity.coerceIn(0f,1f);scaleX=scale;scaleY=scale}.then(if(showBack)Modifier else Modifier.clearAndSetSemantics{})){
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,backDescription)
                }
            }
            Crossfade(title,modifier=Modifier.weight(1f).then(if(onTitleClick!=null)Modifier.clickable(onClick=onTitleClick)else Modifier),animationSpec=motion.defaultEffectsSpec(),label="topbar-title-text"){text->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(text,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.testTag("topbar-title"))
                    if(onTitleClick!=null)Icon(Icons.Default.ArrowDropDown,"选择对话计划",Modifier.size(20.dp))
                }
            }
        }
    },actions={
        // One fixed touch target shared by both actions; outgoing and incoming icons overlap.
        Box(Modifier.size(48.dp).testTag("topbar-action"),contentAlignment=Alignment.Center){
            AnimatedContent(action,modifier=Modifier.fillMaxSize(),contentAlignment=Alignment.Center,transitionSpec={
                ((fadeIn(motion.defaultEffectsSpec())+scaleIn(motion.defaultSpatialSpec(),initialScale=.84f)) togetherWith
                    (fadeOut(motion.fastEffectsSpec())+scaleOut(motion.defaultSpatialSpec(),targetScale=.84f))) using null
            },label="topbar-action-change"){target->
                Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){
                    if(target!=TopBarAction.None)IconButton(onClick=onAction,enabled=target==action&&actionEnabled,shapes=IconButtonDefaults.shapes(),modifier=Modifier.size(48.dp)){
                        if(target==TopBarAction.ClearChat)Icon(Icons.Default.Delete,"清空聊天")else Icon(MaterialSymbols.Settings,"设置")
                    }
                }
            }
        }
    })
}
