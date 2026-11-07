package com.routine

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Only synthetic files in a unique cache folder are used; user chats/preferences are untouched. */
@RunWith(AndroidJUnit4::class)
class AttachmentStoreTest {
    private fun checkFile(ext:String,write:(File)->Unit,verify:(ChatAttachment,AttachmentStore)->Unit){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val source=File(context.cacheDir,"test-attachment-${UUID.randomUUID()}.$ext")
        val store=AttachmentStore(context);var attachment:ChatAttachment?=null
        try{write(source);attachment=store.importFile(Uri.fromFile(source));verify(attachment,store)}finally{attachment?.let{store.remove(it)};source.delete()}
    }
    @Test fun imagesAreCopiedAndRemainReadableAfterTheSourceIsGone(){
        checkFile("png",{file->val bitmap=Bitmap.createBitmap(100,80,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}){attachment,store->
            assertEquals(1,attachment.images.size);val bytes=store.readImage(attachment.images.single());assertTrue(bytes.size>0);assertEquals(100,store.thumbnail(attachment.images.single())!!.width)
        }
    }
    @Test fun pdfImportsEveryPageAsAnImage(){
        checkFile("pdf",{file->val pdf=PdfDocument();repeat(2){index->val page=pdf.startPage(PdfDocument.PageInfo.Builder(300,400,index+1).create());page.canvas.drawColor(if(index==0)Color.WHITE else Color.LTGRAY);pdf.finishPage(page)};file.outputStream().use{pdf.writeTo(it)};pdf.close()}){attachment,store->assertEquals(2,attachment.images.size);attachment.images.forEach{assertTrue(store.readImage(it).isNotEmpty())}}
    }
    @Test fun wordBodyIsExtractedInParagraphOrder(){
        checkFile("docx",{file->ZipOutputStream(file.outputStream()).use{zip->zip.putNextEntry(ZipEntry("word/document.xml"));zip.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>课程报告</w:t></w:r></w:p><w:p><w:r><w:t>提交三页PDF</w:t></w:r></w:p></w:body></w:document>""".toByteArray());zip.closeEntry()}}){attachment,_->assertTrue(attachment.text.contains("课程报告\n提交三页PDF"));assertTrue(attachment.images.isEmpty())}
    }
    @Test fun utf16AndUtf8DocumentsKeepTheirChineseText(){
        for(charset in listOf(Charsets.UTF_8,Charsets.UTF_16))checkFile("txt",{it.writeBytes("周五交报告，已有大纲".toByteArray(charset))}){attachment,_->assertEquals("周五交报告，已有大纲",attachment.text)}
    }
}
