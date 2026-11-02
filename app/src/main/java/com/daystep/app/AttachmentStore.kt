package com.daystep.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID
import java.util.zip.ZipFile

class AttachmentStore(private val context:Context){
    private val root=File(context.filesDir,"chat_attachments").apply{mkdirs()}
    fun clearAll(){root.listFiles()?.forEach{if(it.canonicalFile.parentFile==root.canonicalFile)it.deleteRecursively()}}
    private fun file(path:String)=File(root,path).canonicalFile.also{check(it.path.startsWith(root.canonicalPath+File.separator)){"附件路径无效。"}}
    fun readImage(path:String)=file(path).also{check(it.isFile){"附件已不可用，请重新添加。"};check(it.length()<=4_000_000){"图片过大，请重新添加。"}}.readBytes()
    fun thumbnail(path:String):Bitmap?=runCatching{val f=file(path);val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(f.path,bounds);BitmapFactory.decodeFile(f.path,BitmapFactory.Options().apply{inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/256).coerceAtLeast(1)})}.getOrNull()
    fun remove(attachment:ChatAttachment){val dir=file(attachment.id);if(dir.parentFile==root.canonicalFile)dir.deleteRecursively()}
    fun importFile(uri:Uri):ChatAttachment {
        val id=UUID.randomUUID().toString();val dir=File(root,id).apply{mkdirs()}
        try{
            val resolver=context.contentResolver
            val name=(runCatching{resolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}}.getOrNull()?:uri.lastPathSegment?:"附件").take(180)
            val mime=resolver.getType(uri)?:"application/octet-stream"
            val source=File(dir,"source")
            resolver.openInputStream(uri)?.use{input->source.outputStream().use{out->val b=ByteArray(8192);var total=0;while(true){val n=input.read(b);if(n<0)break;total+=n;check(total<=12*1024*1024){"单个附件不能超过12MB。"};out.write(b,0,n)}}}?:error("无法打开附件，请重新选择。")
            check(source.length()>0){"文件为空，请选择有内容的文件。"}
            val ext=name.substringAfterLast('.',"").lowercase()
            val images=mutableListOf<String>();var text=""
            fun save(bitmap:Bitmap,index:Int){try{val image=File(dir,"$index.jpg");image.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.JPEG,88,it)){"图片处理失败。"}};check(image.length()<=4_000_000){"图片内容过大，请缩小后重试。"};images+="$id/$index.jpg"}finally{bitmap.recycle()}}
            when{
                mime.startsWith("image/")||ext in setOf("jpg","jpeg","png","webp","gif","heic","heif")-> {
                    val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(source.path,bounds)
                    check(bounds.outWidth>0&&bounds.outHeight>0){"无法识别图片，请改用 JPG 或 PNG。"}
                    var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048)sample*=2
                    val bitmap=BitmapFactory.decodeFile(source.path,BitmapFactory.Options().apply{inSampleSize=sample})?:error("图片解码失败。")
                    // Keep text screenshots readable, with a bounded working bitmap.
                    val white=Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(white).apply{drawColor(Color.WHITE);drawBitmap(bitmap,0f,0f,null)};bitmap.recycle()
                    val orientation=runCatching{android.media.ExifInterface(source.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1)}.getOrDefault(1)
                    val matrix=android.graphics.Matrix().apply{when(orientation){2->setScale(-1f,1f);3->setRotate(180f);4->setScale(1f,-1f);5->{setRotate(90f);postScale(-1f,1f)};6->setRotate(90f);7->{setRotate(-90f);postScale(-1f,1f)};8->setRotate(-90f)}}
                    val upright=Bitmap.createBitmap(white,0,0,white.width,white.height,matrix,true);if(upright!==white)white.recycle();save(upright,0)
                    if(ext=="gif")text="动态图仅读取第一帧。"
                }
                mime=="application/pdf"||ext=="pdf"->ParcelFileDescriptor.open(source,ParcelFileDescriptor.MODE_READ_ONLY).use{descriptor->
                    PdfRenderer(descriptor).use{pdf->check(pdf.pageCount in 1..12){"PDF 最多支持12页，请拆分后添加；不会截取或遗漏页面。"};for(i in 0 until pdf.pageCount)pdf.openPage(i).use{page->val ratio=1800f/maxOf(page.width,page.height);val bitmap=Bitmap.createBitmap((page.width*ratio).toInt().coerceAtLeast(1),(page.height*ratio).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);save(bitmap,i)}}
                }
                ext=="docx"||mime=="application/vnd.openxmlformats-officedocument.wordprocessingml.document"-> {
                    text=ZipFile(source).use{zip->val entry=zip.getEntry("word/document.xml")?:error("Word 文件内容无效。");check(entry.size in 1..2_000_000){"Word 内容过大，请精简后添加。"};zip.getInputStream(entry).use{input->val parser=Xml.newPullParser();parser.setInput(input,"UTF-8");val out=StringBuilder();while(parser.eventType!=XmlPullParser.END_DOCUMENT){if(parser.eventType==XmlPullParser.START_TAG&&parser.name=="t")out.append(parser.nextText());if(parser.eventType==XmlPullParser.END_TAG&&parser.name=="p")out.append('\n');check(out.length<=60_000){"文件文字超过6万字，请拆分后添加。"};parser.next()};out.toString()}}
                    check(text.isNotBlank()){"未找到可读取的 Word 正文，请改为 PDF 或图片。"}
                    text="（读取 Word 正文；图片、图表和批注请另发图片或 PDF。）\n$text"
                }
                mime.startsWith("text/")||ext in setOf("txt","md","csv","json","xml","log","yaml","yml")-> {
                    check(source.length()<=300_000){"文本文件过大，请拆分后添加。"}
                    val bytes=source.readBytes();val encoding=if(bytes.size>=2&&((bytes[0]==0xff.toByte()&&bytes[1]==0xfe.toByte())||(bytes[0]==0xfe.toByte()&&bytes[1]==0xff.toByte())))Charsets.UTF_16 else Charsets.UTF_8
                    text=runCatching{encoding.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().trimStart('\uFEFF')}.getOrElse{error("文字编码无法识别，请保存为 UTF-8 后添加。")}
                    check(!text.contains('\u0000')&&text.isNotBlank()){"文件不是可读取的文本，请改为 PDF 或图片。"};check(text.length<=60_000){"文件文字超过6万字，请拆分后添加。"}
                }
                else->error("暂支持图片、PDF、DOCX、TXT、Markdown、CSV 和 JSON；请转换后再添加。")
            }
            source.delete()
            return ChatAttachment(id,name,mime,text,images)
        }catch(e:Exception){dir.deleteRecursively();throw if(e is IllegalStateException)e else IllegalStateException("无法读取附件，请检查文件是否损坏或加密，或换成图片后重试。",e)}
    }
}
