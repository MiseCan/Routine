package com.routine

/** One UI snapshot, collected with the screen's lifecycle. */
data class PlannerUiState(
    val activeChatId:String="",
    val hasKey:Boolean=false,
    val customPrompt:String="",
    val messages:List<ConversationMessage> = emptyList(),
    val result:PlannerResult?=null,
    val previewStale:Boolean=false,
    val questionRound:Int=0,
    val answerDraft:String="{}",
    val busy:Boolean=false,
    val attaching:Boolean=false,
    val draftAttachments:List<ChatAttachment> = emptyList(),
    val editingPlanId:String="",
    val checking:Boolean=false,
    val configuring:Boolean=false,
    val models:List<AvailableModel> = emptyList(),
    val model:String="",
    val modelStatus:String="",
    val error:String?=null,
    val connectionStatus:String="",
    val input:String="",
    val confirmedId:String?="",
    val pendingCommands:List<AppCommand> = emptyList(),
    val commandStatus:String="",
    val attachmentRequest:Int=0,
    val attachmentKind:String="files",
) {
    val working get()=busy||attaching
    val confirmed get()=result?.planId!=null&&result.planId==confirmedId
    val canGeneratePlan get()=InterviewPolicy.generationAvailable(questionRound)
    fun selectedAnswer(id:String)=runCatching{org.json.JSONObject(answerDraft).optString(id)}.getOrDefault("")
}
