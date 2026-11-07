package com.routine

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

data class ChatAttachment(val id:String,val name:String,val mime:String,val text:String="",val images:List<String> = emptyList()) {
    fun json()=JSONObject().put("id",id).put("name",name).put("mime",mime).put("text",text).put("images",JSONArray(images))
    companion object {
        fun parse(o:JSONObject)=ChatAttachment(o.getString("id"),o.getString("name"),o.getString("mime"),o.optString("text"),o.optJSONArray("images")?.let{a->(0 until a.length()).map{a.getString(it)}}?:emptyList())
        fun decode(a:JSONArray?)=a?.let{(0 until it.length()).map{i->parse(it.getJSONObject(i))}}?:emptyList()
    }
}

/** Typed user content, with attachment bodies treated as source material rather than instructions. */
object AttachmentContent {
    fun user(message:ConversationMessage,readImage:(String)->ByteArray):Any {
        val heading="[消息时间 ${message.at}]\n${message.content}"
        if(message.attachments.isEmpty())return heading
        val parts=JSONArray().put(JSONObject().put("type","text").put("text",heading))
        message.attachments.forEach{file->
            parts.put(JSONObject().put("type","text").put("text","[附件资料：${file.name}]\n${file.text}"))
            file.images.forEachIndexed{i,path->
                parts.put(JSONObject().put("type","text").put("text","${file.name} · 第 ${i+1} 张/页"))
                parts.put(JSONObject().put("type","image_url").put("image_url",JSONObject().put("url","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(readImage(path)))))
            }
        }
        return parts
    }
    fun validate(messages:List<ConversationMessage>,model:AvailableModel){
        val files=messages.flatMap{it.attachments}
        if(files.any{it.images.isNotEmpty()}&&!model.supportsImages)throw IllegalStateException("当前模型不支持图片或 PDF，请在下方切换支持图片的模型；若没有可选项，请到设置刷新模型列表。")
        check(files.sumOf{it.text.length}<=120_000){"本会话附件文字已过多，请新建聊天并仅附上本次需要的文件。"}
        check(files.sumOf{it.images.size}<=24){"本会话图片或 PDF 页面已过多，请新建聊天并附上本次需要的资料。"}
    }
}
