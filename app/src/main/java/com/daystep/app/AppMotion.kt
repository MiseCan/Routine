@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.daystep.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color

/** Spatial and effects springs come from the same official Material motion scheme. */
@Composable fun <T> MotionContent(value:T,modifier:Modifier=Modifier,content:@Composable (T)->Unit){
    val motion=MaterialTheme.motionScheme
    AnimatedContent(targetState=value,modifier=modifier,transitionSpec={
        (fadeIn(motion.defaultEffectsSpec())+slideInVertically(motion.fastSpatialSpec()){it/16}) togetherWith
            fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip=false){_,_->motion.defaultSpatialSpec()}
    },label="content-change"){target->Column(verticalArrangement=Arrangement.spacedBy(12.dp)){content(target)}}
}

@Composable fun MotionVisibility(visible:Boolean,content:@Composable ()->Unit){
    val motion=MaterialTheme.motionScheme
    AnimatedVisibility(visible=visible,enter=fadeIn(motion.defaultEffectsSpec())+expandVertically(motion.defaultSpatialSpec(),expandFrom=Alignment.Top),exit=fadeOut(motion.fastEffectsSpec())+shrinkVertically(motion.defaultSpatialSpec(),shrinkTowards=Alignment.Top)){content()}
}

@Composable fun MotionProgress(value:Float,modifier:Modifier=Modifier){
    val progress by animateFloatAsState(value.coerceIn(0f,1f),MaterialTheme.motionScheme.defaultEffectsSpec(),label="task-progress")
    LinearProgressIndicator(progress={progress},modifier=modifier)
}

fun Modifier.motionPress(source:MutableInteractionSource):Modifier=composed{
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed).98f else 1f,MaterialTheme.motionScheme.fastSpatialSpec(),label="card-press")
    graphicsLayer{scaleX=scale;scaleY=scale}
}

/** Keep removed rows alive until their collapse finishes; stable keys preserve item state. */
@Composable fun <T> MotionList(items:List<T>,keyOf:(T)->String,content:@Composable (T)->Unit){
    var retained by remember{mutableStateOf(items)}
    val liveKeys=items.map(keyOf).toSet()
    val values=items.associateBy(keyOf)
    SideEffect{
        val merged=retained.map{values[keyOf(it)]?:it}+items.filter{new->retained.none{keyOf(it)==keyOf(new)}}
        if(merged!=retained)retained=merged
    }
    retained.forEach{item->key(keyOf(item)){
        val visible=remember{MutableTransitionState(false)}
        visible.targetState=keyOf(item) in liveKeys
        val motion=MaterialTheme.motionScheme
        AnimatedVisibility(visibleState=visible,enter=fadeIn(motion.defaultEffectsSpec())+expandVertically(motion.defaultSpatialSpec()),exit=fadeOut(motion.fastEffectsSpec())+shrinkVertically(motion.defaultSpatialSpec())){
            Box{content(values[keyOf(item)]?:item)}
        }
        LaunchedEffect(visible.isIdle,visible.currentState,visible.targetState){if(visible.isIdle&&!visible.currentState&&!visible.targetState)retained=retained.filterNot{keyOf(it)==keyOf(item)}}
    }}
}

/** Delay callbacks until the native dialog surface and scrim have finished exiting. */
@Composable fun MotionDialog(onDismissRequest:()->Unit,title:@Composable ()->Unit,text:@Composable ()->Unit,confirmLabel:String,onConfirm:()->Unit,confirmEnabled:Boolean=true,dismissLabel:String="取消",destructive:Boolean=false){
    var open by remember{mutableStateOf(false)}
    var pending by remember{mutableStateOf<(() -> Unit)?>(null)}
    val motion=MaterialTheme.motionScheme
    val transition=updateTransition(open,label="dialog")
    val alpha by transition.animateFloat(transitionSpec={motion.defaultEffectsSpec()},label="dialog-opacity"){if(it)1f else 0f}
    val scale by transition.animateFloat(transitionSpec={motion.defaultSpatialSpec()},label="dialog-scale"){if(it)1f else .92f}
    fun close(action:()->Unit){if(pending==null){pending=action;open=false}}
    LaunchedEffect(Unit){open=true}
    LaunchedEffect(transition.isRunning,transition.currentState,open,pending){if(pending!=null&&!open&&!transition.isRunning&&!transition.currentState){val action=pending;pending=null;action?.invoke()}}
    Dialog(onDismissRequest={close(onDismissRequest)}){
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect{window?.setDimAmount(.32f*alpha.coerceIn(0f,1f));window?.setWindowAnimations(0)}
        Surface(modifier=Modifier.widthIn(min=280.dp,max=560.dp).graphicsLayer{this.alpha=alpha;scaleX=scale;scaleY=scale},shape=AlertDialogDefaults.shape,color=AlertDialogDefaults.containerColor,tonalElevation=AlertDialogDefaults.TonalElevation){
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)){
                ProvideTextStyle(MaterialTheme.typography.headlineSmall){title()}
                Box(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState())){ProvideTextStyle(MaterialTheme.typography.bodyMedium){text()}}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){
                    TextButton(onClick={close(onDismissRequest)},enabled=pending==null,shapes=ButtonDefaults.shapes()){Text(dismissLabel)}
                    TextButton(onClick={close(onConfirm)},enabled=confirmEnabled&&pending==null,shapes=ButtonDefaults.shapes()){Text(confirmLabel,color=if(destructive)MaterialTheme.colorScheme.error else Color.Unspecified)}
                }
            }
        }
    }
}

fun pageDepth(page:String)=when(page){"board","chat"->0;"settings","plan"->1;"about"->2;else->0}
fun pageDirection(from:String,to:String)=if(pageDepth(from)!=pageDepth(to)){if(pageDepth(to)>pageDepth(from))1 else -1}else if(to=="chat")1 else -1

@Composable fun MotionPages(page:String,modifier:Modifier=Modifier,content:@Composable (String)->Unit){
    val motion=MaterialTheme.motionScheme
    AnimatedContent(targetState=page,modifier=modifier,transitionSpec={
        val direction=pageDirection(initialState,targetState)
        val nested=pageDepth(initialState)!=pageDepth(targetState)
        ((fadeIn(motion.defaultEffectsSpec())+slideInHorizontally(motion.defaultSpatialSpec()){direction*it/(if(nested)5 else 12)}) togetherWith
            (fadeOut(motion.fastEffectsSpec())+slideOutHorizontally(motion.defaultSpatialSpec()){-direction*it/12})).apply{targetContentZIndex=if(direction>0)1f else 0f}
    },label="page-navigation"){screen->content(screen)}
}

@Composable fun animatedColors(target:ColorScheme):ColorScheme{
    val spec=remember{MotionScheme.expressive().defaultEffectsSpec<Color>()}
    @Composable fun c(value:Color):Color=animateColorAsState(value,spec,label="theme-color").value
    return target.copy(primary=c(target.primary),onPrimary=c(target.onPrimary),primaryContainer=c(target.primaryContainer),onPrimaryContainer=c(target.onPrimaryContainer),secondary=c(target.secondary),onSecondary=c(target.onSecondary),secondaryContainer=c(target.secondaryContainer),onSecondaryContainer=c(target.onSecondaryContainer),tertiary=c(target.tertiary),onTertiary=c(target.onTertiary),tertiaryContainer=c(target.tertiaryContainer),onTertiaryContainer=c(target.onTertiaryContainer),background=c(target.background),onBackground=c(target.onBackground),surface=c(target.surface),onSurface=c(target.onSurface),surfaceVariant=c(target.surfaceVariant),onSurfaceVariant=c(target.onSurfaceVariant),surfaceContainer=c(target.surfaceContainer),surfaceContainerLow=c(target.surfaceContainerLow),surfaceContainerLowest=c(target.surfaceContainerLowest),surfaceContainerHigh=c(target.surfaceContainerHigh),surfaceContainerHighest=c(target.surfaceContainerHighest),surfaceBright=c(target.surfaceBright),surfaceDim=c(target.surfaceDim),outline=c(target.outline),outlineVariant=c(target.outlineVariant),error=c(target.error),onError=c(target.onError),errorContainer=c(target.errorContainer),onErrorContainer=c(target.onErrorContainer),inverseSurface=c(target.inverseSurface),inverseOnSurface=c(target.inverseOnSurface),inversePrimary=c(target.inversePrimary),surfaceTint=c(target.surfaceTint))
}
