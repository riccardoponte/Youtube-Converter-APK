package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.YouTubeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TubeConvert", appName)
    }

    @Test
    fun `extract youtube video ids from single and multi-line links`() {
        val input = """
            https://www.youtube.com/watch?v=dQw4w9WgXcQ
            https://youtu.be/kXYiU_JCYtU
            https://www.youtube.com/shorts/3JZ_D3ELwOQ
        """.trimIndent()

        val ids = YouTubeParser.extractVideoIds(input)
        assertEquals(3, ids.size)
        assertTrue(ids.contains("dQw4w9WgXcQ"))
        assertTrue(ids.contains("kXYiU_JCYtU"))
        assertTrue(ids.contains("3JZ_D3ELwOQ"))
    }
}
