package com.routine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Loads the packaged launcher through real framework resources; never touches user settings. */
@RunWith(AndroidJUnit4::class)
class LauncherColorTest {
    private fun context(night:Boolean):Context {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val config=Configuration(base.resources.configuration)
        config.uiMode=(config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if(night)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return base.createConfigurationContext(config)
    }
    private fun render(drawable:Drawable)=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888).also{
        drawable.setBounds(0,0,108,108);drawable.draw(Canvas(it))
    }
    private fun verify(night:Boolean,background:Int,foreground:Int,accent:Int){
        val ctx=context(night)
        val icon=ctx.getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val bg=render(icon.background)
        val fg=render(icon.foreground)
        try{
            assertEquals("Packaged background must resolve the live system palette",ctx.getColor(background),bg.getPixel(54,54))
            assertEquals("Main mark must resolve the live system palette",ctx.getColor(foreground),fg.getPixel(35,54))
            assertEquals("Daylight dot must resolve the live system palette",ctx.getColor(accent),fg.getPixel(70,34))
            assertEquals("Negative space remains transparent",0,Color.alpha(fg.getPixel(44,44)))
        }finally{bg.recycle();fg.recycle()}
    }
    @Test fun ordinaryLightIconUsesActualDeviceSystemColors()=verify(false,android.R.color.system_accent1_100,android.R.color.system_accent1_700,android.R.color.system_accent1_600)
    @Test fun ordinaryDarkIconUsesActualDeviceSystemColors()=verify(true,android.R.color.system_accent1_800,android.R.color.system_accent1_100,android.R.color.system_accent1_300)
    @Test fun themedMonochromeLayerRemainsUntintedForTheLauncher(){
        if(Build.VERSION.SDK_INT<33)return
        for(night in listOf(false,true)){
            val icon=context(night).getDrawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
            val monochrome=icon.monochrome
            assertNotNull(monochrome)
            val bitmap=render(monochrome!!)
            try{
                assertEquals(Color.WHITE,bitmap.getPixel(35,54));assertEquals(Color.WHITE,bitmap.getPixel(70,34))
                assertEquals(0,Color.alpha(bitmap.getPixel(44,44)))
            }finally{bitmap.recycle()}
        }
    }
}
