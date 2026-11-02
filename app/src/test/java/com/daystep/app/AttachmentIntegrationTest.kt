package com.daystep.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AttachmentIntegrationTest {
    private val model=AvailableModel("returned-vision","图片模型",12000,true)
    private fun payload(messages:List<ConversationMessage>,selected:AvailableModel=model)=DeepSeekClient.payload("JSON","简洁",JSONObject().put("today","2026-10-29"),messages,selected){byteArrayOf(1,2,3)}
    private fun message(files:List<ChatAttachment>)=ConversationMessage("user","按附件调整计划","2026-10-29T18:00:00+08:00",files)
    @Test fun imagesAreSentAsRealImagePartsRatherThanFileNames(){
        val file=ChatAttachment("a","课程截图.png","image/png",images=listOf("a/0.jpg"))
        val body=payload(listOf(message(listOf(file)))).getJSONArray("messages").getJSONObject(2).getJSONArray("content")
        val image=(0 until body.length()).map{body.getJSONObject(it)}.single{it.getString("type")=="image_url"}
        assertEquals("data:image/jpeg;base64,AQID",image.getJSONObject("image_url").getString("url"));assertFalse(body.toString().contains("a/0.jpg"))
    }
    @Test fun documentTextAndAllPdfPagesAreIncluded(){
        val files=listOf(ChatAttachment("a","要求.docx","word","验收要求：交三页PDF"),ChatAttachment("b","材料.pdf","application/pdf",images=listOf("b/0.jpg","b/1.jpg")))
        val content=payload(listOf(message(files))).getJSONArray("messages").getJSONObject(2).getJSONArray("content")
        assertTrue(content.toString().contains("验收要求：交三页PDF"));assertEquals(2,(0 until content.length()).count{content.getJSONObject(it).getString("type")=="image_url"})
    }
    @Test fun plainMessagesKeepThePreviousStringFormat(){assertTrue(payload(listOf(message(emptyList()))).getJSONArray("messages").getJSONObject(2).get("content") is String)}
    @Test fun textDocumentsWorkWithTextOnlyModelsButImagesCannotBeSilentlyDropped(){
        val textModel=AvailableModel("text","文字模型")
        payload(listOf(message(listOf(ChatAttachment("a","任务.txt","text/plain","完成课程报告")))),textModel)
        try{payload(listOf(message(listOf(ChatAttachment("b","图片.png","image/png",images=listOf("b/0.jpg"))))),textModel);fail("Expected unsupported-image error")}catch(e:IllegalStateException){assertTrue(e.message!!.contains("不支持图片"))}
    }
    @Test fun oldAndNewMessagesSerializeWithoutLosingAttachments(){
        val original=message(listOf(ChatAttachment("a","课程.txt","text/plain","周五交报告"),ChatAttachment("b","表格.png","image/png",images=listOf("b/0.jpg"))))
        assertEquals(original.attachments,ChatAttachment.decode(JSONObject(original.json().toString()).getJSONArray("attachments")))
        assertTrue(ChatAttachment.decode(null).isEmpty())
    }
    @Test fun modelImageSupportComesFromDiscoveryMetadata(){
        val list=DeepSeekClient.parseModels(JSONObject().put("data",JSONArray().put(JSONObject().put("id","custom-image-model").put("input_modalities",JSONArray().put("text").put("image"))).put(JSONObject().put("id","deepseek-flash").put("input_modalities",JSONArray().put("text")))))
        assertTrue(list[0].supportsImages);assertFalse(list[1].supportsImages)
    }
    private val existing=listOf(Step("a-0","报告","写正文","2026-10-30","2026-11-01",30,planId="a"),Step("b-0","报告","改图片","2026-10-31","2026-11-02",20,planId="b"))
    @Test fun selectingAPlanRejectsMissingAndWrongTargetsEvenWithDuplicateTitles(){
        for(target in listOf(JSONObject.NULL,"b")){try{RevisionPolicy.validate(JSONObject().put("targetPlanId",target),existing,"a");fail("Expected revision target rejection")}catch(e:AiFormatException){assertEquals("REVISION_TARGET",e.code)}}
        RevisionPolicy.validate(JSONObject().put("targetPlanId","a"),existing,"a")
    }
    @Test fun naturalLanguageRevisionsCanTargetAnyExistingPlanButNeverADeletedPlan(){
        RevisionPolicy.validate(JSONObject().put("targetPlanId","b"),existing,"")
        try{RevisionPolicy.validate(JSONObject().put("targetPlanId","deleted"),existing,"");fail("Expected deleted plan rejection")}catch(e:AiFormatException){assertEquals("UNKNOWN_PLAN",e.code)}
    }
    @Test fun confirmedRevisionsUpdateOnlyTheTargetAndKeepCompletionHistory(){
        val done=existing[0].copy(id="a-1",done=true,completions=listOf(CompletionRecord("完成了资料检索","2026-10-29T18:00:00+08:00")))
        val next=existing[0].copy(id="revision-0",title="把正文分成三个小节")
        val merged=mergeRevision(existing+done,listOf(next),"a")
        assertTrue(merged.contains(done));assertTrue(merged.contains(existing[1]));assertTrue(merged.contains(next));assertFalse(merged.contains(existing[0]))
    }
}
