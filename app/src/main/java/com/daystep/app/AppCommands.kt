package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Commands are a constrained application protocol, not executable code or arbitrary UI input. */
data class AppCommand(val type:String,val targetId:String?,val values:JSONObject) {
    fun json()=JSONObject().put("type",type).put("targetId",targetId?:JSONObject.NULL).put("values",JSONObject(values.toString()))
}

object AppCommands {
    val supportedTypes=setOf("create_task","update_task","complete_task","reopen_task","delete_task","update_plan","delete_plan","update_settings","select_model","refresh_models","clear_chat","navigate","select_plan","generate_plan","confirm_plan","add_attachment","remove_attachment","inspect_tasks")
    private val standalone=setOf("navigate","clear_chat","refresh_models","generate_plan","confirm_plan","add_attachment","inspect_tasks")
    private val taskTypes=setOf("create_task","update_task","complete_task","reopen_task","delete_task","update_plan","delete_plan")
    private val targetRequired=setOf("update_task","complete_task","reopen_task","delete_task","update_plan","delete_plan","select_model","remove_attachment")
    private val pages=setOf("today","overdue","tomorrow","plans","settings","about","plan","chat")
    private fun fail(message:String):Nothing=throw AiFormatException("APP_COMMAND",message)
    private fun keys(o:JSONObject)=o.keys().asSequence().toSet()
    private fun text(o:JSONObject,key:String,max:Int,blank:Boolean=false):String {
        val value=o.opt(key) as? String?:fail("操作字段 $key 必须是文字。")
        if(value.length>max||(!blank&&value.isBlank()))fail("操作字段 $key 内容无效。")
        return value
    }
    private fun date(value:String):LocalDate {
        if(!Regex("\\d{4}-\\d{2}-\\d{2}").matches(value))fail("任务日期必须是 YYYY-MM-DD。")
        return try{LocalDate.parse(value)}catch(_:Exception){fail("任务日期无效。")}
    }
    private fun deadline(value:String):LocalDate {
        if(!Regex("\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2})?").matches(value))fail("截止日期格式应为 YYYY-MM-DD 或 YYYY-MM-DD HH:mm。")
        val result=date(value.take(10))
        if(value.length>10)try{LocalTime.parse(value.drop(11))}catch(_:Exception){fail("截止时刻无效。")}
        return result
    }
    private fun minutes(o:JSONObject) {
        val number=o.opt("minutes") as? Number?:fail("预计分钟数必须是整数。")
        if(number.toDouble()!=number.toInt().toDouble()||number.toInt() !in 5..480)fail("预计分钟数应为5～480的整数。")
    }
    private fun integer(o:JSONObject,key:String,min:Int,max:Int):Int {
        val number=o.opt(key) as? Number?:fail("$key 必须是整数。")
        if(number.toDouble()!=number.toInt().toDouble()||number.toInt() !in min..max)fail("$key 必须在 $min～$max 之间。")
        return number.toInt()
    }
    private fun target(command:AppCommand):String=command.targetId?.takeIf{it.isNotBlank()}?:fail("这项操作缺少准确的对象ID，请先确认要操作哪一项。")

    fun parse(raw:JSONArray):List<AppCommand> {
        if(raw.length()>20)fail("一次最多执行20项操作，请分批处理。")
        val commands=(0 until raw.length()).map { index->
            val o=raw.optJSONObject(index)?:fail("操作必须是JSON对象。")
            if(keys(o)!=setOf("type","targetId","values"))fail("操作字段必须为 type、targetId、values。")
            val type=text(o,"type",40)
            if(type !in supportedTypes)fail("软件不支持这项操作：$type。")
            val id=if(o.isNull("targetId"))null else text(o,"targetId",1000)
            val values=o.optJSONObject("values")?:fail("操作values必须是对象。")
            val command=AppCommand(type,id,JSONObject(values.toString()))
            validateShape(command)
            command
        }
        if(commands.size>1&&commands.any{it.type in standalone})fail("页面跳转、清空聊天、生成或确认计划、刷新模型和选择附件需要单独执行。")
        return commands
    }

    private fun validateShape(c:AppCommand) {
        val v=c.values
        val allowed=when(c.type){
            "create_task"->setOf("planId","title","date","minutes","definitionOfDone")
            "update_task"->setOf("title","date","minutes","definitionOfDone")
            "complete_task"->setOf("note")
            "update_plan"->setOf("title","shortTitle","deadline")
            "update_settings"->setOf("theme","dynamicColor","remindersEnabled","personalPrompt")
            "navigate"->setOf("page")
            "add_attachment"->setOf("kind")
            "inspect_tasks"->setOf("planId","date","from","to","offset","limit")
            else->emptySet()
        }
        if(!allowed.containsAll(keys(v)))fail("操作 ${c.type} 包含不支持的字段。")
        if(c.type in setOf("create_task","complete_task","navigate","add_attachment")&&keys(v)!=allowed)fail("操作 ${c.type} 缺少必需字段。")
        if(c.type in setOf("update_task","update_plan","update_settings")&&v.length()==0)fail("请说明具体要修改的内容。")
        if(c.type in targetRequired)target(c)
        else if(c.type !in setOf("navigate","select_plan")&&c.targetId!=null)fail("这项操作不使用targetId。")
        when(c.type){
            "create_task","update_task"->{
                if(c.type=="create_task")text(v,"planId",1000)
                if(v.has("title"))text(v,"title",300)
                if(v.has("date"))date(text(v,"date",10))
                if(v.has("minutes"))minutes(v)
                if(v.has("definitionOfDone"))text(v,"definitionOfDone",2000,blank=true)
            }
            "complete_task"->text(v,"note",4000)
            "update_plan"->{
                if(v.has("title"))text(v,"title",200)
                if(v.has("shortTitle")){
                    val short=text(v,"shortTitle",28).trim()
                    if(short.codePointCount(0,short.length)>7||short.contains('…')||short.contains("..."))fail("计划简称需不超过7个字，不能使用省略号。")
                }
                if(v.has("deadline"))deadline(text(v,"deadline",16))
            }
            "update_settings"->{
                if(v.has("theme")&&text(v,"theme",20) !in setOf("跟随系统","浅色","深色"))fail("主题模式无效。")
                for(key in listOf("dynamicColor","remindersEnabled"))if(v.has(key)&&v.opt(key) !is Boolean)fail("$key 必须是true或false。")
                if(v.has("personalPrompt"))text(v,"personalPrompt",PromptPolicy.MAX_LENGTH,blank=true)
            }
            "navigate"->{
                val page=text(v,"page",20)
                if(page !in pages)fail("页面名称无效。")
                if(page=="plan")target(c)else if(c.targetId!=null)fail("只有计划详情页面需要targetId。")
            }
            "add_attachment"->if(text(v,"kind",20) !in setOf("images","files"))fail("附件类型只能是images或files。")
            "inspect_tasks"->{
                if(v.has("planId"))text(v,"planId",1000)
                for(key in listOf("date","from","to"))if(v.has(key))date(text(v,key,10))
                if(v.has("from")&&v.has("to")&&date(v.getString("from"))>date(v.getString("to")))fail("查询开始日期不能晚于结束日期。")
                if(v.has("offset"))integer(v,"offset",0,Int.MAX_VALUE)
                if(v.has("limit"))integer(v,"limit",1,200)
            }
        }
    }

    /** Validates against current IDs and simulates the entire batch before any persistent write. */
    fun validate(commands:List<AppCommand>,steps:List<Step>,chatIds:Set<String>,modelIds:Set<String>,draftAttachmentIds:Set<String>) {
        val checked=parse(JSONArray().apply{commands.forEach{put(it.json())}})
        var simulated=steps
        checked.forEach { c->
            if(c.type in taskTypes)simulated=reduceOne(simulated,c,"validation")
            when(c.type){
                "select_model"->if(target(c) !in modelIds)fail("目标模型不在当前可用列表中，请先刷新模型。")
                "remove_attachment"->if(target(c) !in draftAttachmentIds)fail("目标附件已移除或已发送。")
                "select_plan"->if(c.targetId!=null&&simulated.none{planKey(it)==c.targetId})fail("目标计划已删除或不存在。")
                "navigate"->if(c.values.getString("page")=="plan"&&simulated.none{planKey(it)==c.targetId})fail("目标计划已删除或不存在。")
                "inspect_tasks"->if(c.values.has("planId")&&simulated.none{planKey(it)==c.values.getString("planId")})fail("查询的计划已删除或不存在。")
            }
        }
    }

    fun applySteps(steps:List<Step>,commands:List<AppCommand>,at:String):List<Step> {
        val checked=parse(JSONArray().apply{commands.forEach{put(it.json())}})
        var result=steps.toList()
        checked.filter{it.type in taskTypes}.forEach{result=reduceOne(result,it,at)}
        return result
    }

    /** Read-only paginated lookup: preserves stable IDs and full completion records. */
    fun inspect(steps:List<Step>,command:AppCommand):JSONObject {
        val c=parse(JSONArray().put(command.json())).single()
        if(c.type!="inspect_tasks")fail("不是任务查询操作。")
        val v=c.values
        if(v.has("planId")&&steps.none{planKey(it)==v.getString("planId")})fail("查询的计划已删除或不存在。")
        val filtered=steps.filter{s->
            (!v.has("planId")||planKey(s)==v.getString("planId"))&&
                (!v.has("date")||s.date==v.getString("date"))&&
                (!v.has("from")||s.date>=v.getString("from"))&&
                (!v.has("to")||s.date<=v.getString("to"))
        }.sortedWith(compareBy<Step>{it.date}.thenBy{it.id})
        val offset=if(v.has("offset"))v.getInt("offset")else 0
        val limit=if(v.has("limit"))v.getInt("limit")else 100
        val page=filtered.drop(offset).take(limit)
        val tasks=JSONArray().apply{page.forEach{s->put(JSONObject()
            .put("id",s.id).put("planId",planKey(s)).put("project",s.project).put("shortTitle",s.shortProject).put("title",s.title)
            .put("date",s.date).put("ddl",s.ddl).put("minutes",s.minutes).put("done",s.done)
            .put("definitionOfDone",s.definitionOfDone)
            .put("completionRecords",JSONArray().apply{s.completions.forEach{put(JSONObject().put("note",it.note).put("at",it.at))}}))}}
        return JSONObject().put("total",filtered.size).put("offset",offset).put("hasMore",offset.toLong()+page.size<filtered.size).put("tasks",tasks)
    }

    private fun reduceOne(steps:List<Step>,c:AppCommand,at:String):List<Step> {
        val v=c.values
        if(c.type=="create_task"){
            val plan=v.getString("planId")
            val sample=steps.firstOrNull{planKey(it)==plan}?:fail("新增任务所属计划已删除或不存在。")
            if(date(v.getString("date"))>deadline(sample.ddl))fail("任务日期不能晚于计划截止日期，请先调整计划截止日期。")
            if(steps.count{planKey(it)==plan}>=ScheduleRules.MAX_ACTIONS)fail("该计划已达到每日任务数量上限，请分阶段安排。")
            return steps+Step(UUID.randomUUID().toString(),sample.project,v.getString("title").trim(),v.getString("date"),sample.ddl,v.getInt("minutes"),definitionOfDone=v.getString("definitionOfDone").trim(),planId=plan,shortProject=sample.shortProject)
        }
        if(c.type in setOf("update_plan","delete_plan")){
            val plan=target(c)
            val existing=steps.filter{planKey(it)==plan}
            if(existing.isEmpty())fail("目标计划已删除或不存在。")
            if(c.type=="delete_plan")return steps.filterNot{planKey(it)==plan}
            val ddl=if(v.has("deadline"))v.getString("deadline")else existing.first().ddl
            val end=deadline(ddl)
            if(existing.any{date(it.date)>end})fail("截止日期不能早于该计划已有任务的日期。")
            return steps.map{if(planKey(it)==plan){
                val newTitle=if(v.has("title"))v.getString("title").trim()else it.project
                val short=if(v.has("shortTitle"))v.getString("shortTitle").trim()else if(newTitle!=it.project)""else it.shortProject
                it.copy(project=newTitle,ddl=ddl,planId=plan,shortProject=short)
            }else it}
        }
        val id=target(c)
        val original=steps.firstOrNull{it.id==id}?:fail("目标任务已删除或不存在。")
        val updated=when(c.type){
            "delete_task"->return steps.filterNot{it.id==id}
            "update_task"->{
                val newDate=if(v.has("date"))v.getString("date")else original.date
                if(date(newDate)>deadline(original.ddl))fail("任务日期不能晚于计划截止日期。")
                original.copy(title=if(v.has("title"))v.getString("title").trim()else original.title,date=newDate,minutes=if(v.has("minutes"))v.getInt("minutes")else original.minutes,definitionOfDone=if(v.has("definitionOfDone"))v.getString("definitionOfDone").trim()else original.definitionOfDone)
            }
            "complete_task"->{if(original.done)fail("该任务已经完成，无需重复记录。请先撤销完成再添加新的完成记录。");completedStep(original,v.getString("note"),at)}
            "reopen_task"->{if(!original.done)fail("该任务已经是未完成状态。");original.copy(done=false)}
            else->fail("不是任务操作。")
        }
        return steps.map{if(it.id==id)updated else it}
    }

    /** Review text includes actual targets and values; nothing is applied while generating it. */
    fun summaries(commands:List<AppCommand>,steps:List<Step>,chats:Map<String,String>):List<String> = commands.map { c->
        val v=c.values
        val task=steps.firstOrNull{it.id==c.targetId}
        val plan=steps.filter{planKey(it)==c.targetId}
        val taskName=task?.let{"「${it.project} · ${it.date} · ${it.title}」"}?:"任务 ${c.targetId}"
        val planName=plan.firstOrNull()?.project?.let{"「$it」"}?:"计划 ${c.targetId}"
        when(c.type){
            "create_task"->"在「${steps.firstOrNull{planKey(it)==v.optString("planId")}?.project?:v.optString("planId") }」新增 ${v.getString("date")} 的任务「${v.getString("title")}」，预计 ${v.getInt("minutes")} 分钟；完成标准：${v.getString("definitionOfDone")}"
            "update_task"->"修改$taskName："+keys(v).joinToString("；"){key->when(key){"title"->"内容改为「${v.getString(key)}」";"date"->"日期 ${task?.date} → ${v.getString(key)}";"minutes"->"预计 ${task?.minutes} → ${v.getInt(key)} 分钟";else->"完成标准改为「${v.getString(key)}」"}}
            "complete_task"->"完成$taskName，本次完成内容：${v.getString("note")}"
            "reopen_task"->"撤销$taskName 的完成状态，保留全部完成记录。"
            "delete_task"->"删除$taskName，同时删除它的 ${task?.completions?.size?:0} 条完成记录。"
            "update_plan"->"修改$planName："+keys(v).joinToString("；"){when(it){"title"->"名称改为「${v.getString(it)}」";"shortTitle"->"简称改为「${v.getString(it)}」";else->"截止 ${plan.firstOrNull()?.ddl} → ${v.getString(it)}"}}
            "delete_plan"->"删除$planName，同时删除全部 ${plan.size} 项每日任务和 ${plan.sumOf{it.completions.size}} 条完成记录。"
            "update_settings"->"修改设置："+keys(v).joinToString("；"){key->when(key){"theme"->"主题改为${v.getString(key)}";"dynamicColor"->"${if(v.getBoolean(key))"开启"else "关闭"}动态取色";"remindersEnabled"->"${if(v.getBoolean(key))"开启"else "关闭"}今日任务提醒";else->"个性化提示词${if(v.getString(key).isBlank())"清空"else "改为：${v.getString(key)}"}"}}
            "select_model"->"选择模型：${c.targetId}"
            "refresh_models"->"从 DeepSeek 重新获取可用模型列表。"
            "clear_chat"->"清空当前聊天的消息、草稿和附件；保留看板中的计划。"
            "navigate"->"打开"+when(v.getString("page")){"today"->"今日任务";"overdue"->"待补做任务";"tomorrow"->"明日任务";"plans"->"所有计划";"settings"->"设置";"about"->"关于";"plan"->"$planName 的详情";else->"AI聊天"}+"。"
            "select_plan"->if(c.targetId==null)"取消指定要修改的计划。"else "选择$planName，后续对话用于修改此计划。"
            "generate_plan"->"根据本次聊天中已确认的信息生成计划预览，预览确认后才保存。"
            "confirm_plan"->"确认当前可保存的计划预览，写入或更新看板。"
            "add_attachment"->"打开${if(v.getString("kind")=="images")"图片"else "文件"}选择器，由你选择要发送的附件。"
            "remove_attachment"->"移除尚未发送的附件 ${c.targetId}。"
            "inspect_tasks"->"查询任务与完成记录"+(if(v.length()>0)"：${v}"else "。")
            else->fail("不支持的操作。")
        }
    }
}
