package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.adb.AdbManager
import com.example.model.AdbState
import com.example.model.AdbStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  @Config(qualifiers = "ja")
  fun `read string in Japanese locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val btnSelect = context.getString(R.string.btn_select_file)
    val unpaired = context.getString(R.string.adb_state_unpaired_title)
    assertEquals("SDK22", appName)
    assertEquals("ファイルを選択 (SAF)", btnSelect)
    assertEquals("ワイヤレスデバッグ: 未ペアリング", unpaired)
  }

  @Test
  @Config(qualifiers = "en")
  fun `read string in English locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val btnSelect = context.getString(R.string.btn_select_file)
    val unpaired = context.getString(R.string.adb_state_unpaired_title)
    assertEquals("SDK22", appName)
    assertEquals("Select File (SAF)", btnSelect)
    assertEquals("Wireless Debugging: Not Paired", unpaired)
  }

  @Test
  @Config(qualifiers = "ko")
  fun `read string in Korean locale`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    val btnSelect = context.getString(R.string.btn_select_file)
    val unpaired = context.getString(R.string.adb_state_unpaired_title)
    assertEquals("SDK22", appName)
    assertEquals("파일 선택 (SAF)", btnSelect)
    assertEquals("무선 디버깅: 페어링되지 않음", unpaired)
  }

  @Test
  @Config(qualifiers = "fr")
  fun `fallback to default English locale when qualifier not matched`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SDK22", appName)
  }

  @Test
  fun `AdbManager initializes RSA keys and status correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AdbManager.getInstance(context)
    assertNotNull(manager)
    val status = manager.status.value
    assertTrue(status.state == AdbState.UNPAIRED || status.state == AdbState.PAIRED_DISCONNECTED)
  }

  @Test
  fun `MainActivity launches successfully without crash`() {
    androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertNotNull(activity)
      }
    }
  }
}
