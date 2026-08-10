/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.webrtc.audio

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.SoundPool
import assertk.assertThat
import assertk.assertions.isTrue
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.signal.core.util.logging.Log
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.testutil.MockAppDependenciesRule
import com.red.sovereign.testutil.SystemOutLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class, sdk = [31])
class FullREDAudioManagerApi31Test {

  companion object {
    @JvmStatic
    @BeforeClass
    fun setUpClass() {
      Log.initialize(SystemOutLogger())
    }
  }

  @get:Rule
  val appDependencies = MockAppDependenciesRule()

  private lateinit var androidAudioManager: AudioManagerCompat
  private lateinit var eventListener: REDAudioManager.EventListener

  @Before
  fun setUp() {
    androidAudioManager = AppDependencies.androidCallAudioManager
    eventListener = mockk(relaxed = true)

    val soundPool: SoundPool = mockk(relaxed = true)
    every { androidAudioManager.createSoundPool() } returns soundPool
    every { soundPool.load(any<Context>(), any(), any()) } returns 1
  }

  @Test
  fun `reasserts user selected device when Android reports a different communication device`() {
    val userSelectedDevice = createDevice(10, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone speaker")
    val systemChangedDevice = createDevice(11, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "Phone earpiece")

    var currentCommunicationDevice: AudioDeviceInfo? = systemChangedDevice
    every { androidAudioManager.communicationDevice } answers { currentCommunicationDevice }
    every { androidAudioManager.availableCommunicationDevices } returns listOf(userSelectedDevice, systemChangedDevice)
    every { androidAudioManager.setCommunicationDevice(any()) } answers {
      currentCommunicationDevice = firstArg()
      true
    }

    val manager = FullREDAudioManagerApi31(AppDependencies.application, eventListener)

    try {
      setState(manager, REDAudioManager.State.RUNNING)
      setUserSelectedAudioDevice(manager, userSelectedDevice)

      clearMocks(androidAudioManager, answers = false, recordedCalls = true)
      clearMocks(eventListener, answers = false, recordedCalls = true)

      triggerCommunicationDeviceChanged(manager, systemChangedDevice)

      verify(timeout = 2_000) { androidAudioManager.setCommunicationDevice(userSelectedDevice) }
      verify(timeout = 2_000) {
        eventListener.onAudioDeviceChanged(
          REDAudioManager.AudioDevice.SPEAKER_PHONE,
          setOf(REDAudioManager.AudioDevice.SPEAKER_PHONE, REDAudioManager.AudioDevice.EARPIECE)
        )
      }
      verify(exactly = 0) { eventListener.onAudioDeviceChangeFailed() }
    } finally {
      shutdownManager(manager)
    }
  }

  @Test
  fun `does not reassert when Android reports the same user selected communication device`() {
    val selectedDevice = createDevice(20, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Phone speaker")

    every { androidAudioManager.communicationDevice } returns selectedDevice
    every { androidAudioManager.availableCommunicationDevices } returns listOf(selectedDevice)

    val manager = FullREDAudioManagerApi31(AppDependencies.application, eventListener)

    try {
      setState(manager, REDAudioManager.State.RUNNING)
      setUserSelectedAudioDevice(manager, selectedDevice)

      clearMocks(androidAudioManager, answers = false, recordedCalls = true)
      clearMocks(eventListener, answers = false, recordedCalls = true)

      triggerCommunicationDeviceChanged(manager, selectedDevice)

      verify(exactly = 0) { androidAudioManager.setCommunicationDevice(any()) }
      verify(exactly = 0) { eventListener.onAudioDeviceChanged(any(), any()) }
      verify(exactly = 0) { eventListener.onAudioDeviceChangeFailed() }
    } finally {
      shutdownManager(manager)
    }
  }

  private fun createDevice(id: Int, type: Int, productName: String): AudioDeviceInfo {
    return mockk {
      every { this@mockk.id } returns id
      every { this@mockk.type } returns type
      every { this@mockk.productName } returns productName
    }
  }

  private fun triggerCommunicationDeviceChanged(manager: FullREDAudioManagerApi31, device: AudioDeviceInfo) {
    val listenerField = FullREDAudioManagerApi31::class.java.getDeclaredField("communicationDeviceChangedListener")
    listenerField.isAccessible = true
    val listener = listenerField.get(manager) as AudioManager.OnCommunicationDeviceChangedListener

    val handlerField = REDAudioManager::class.java.getDeclaredField("handler")
    handlerField.isAccessible = true
    val handler = handlerField.get(manager) as REDAudioHandler

    val latch = CountDownLatch(1)
    val posted = handler.post {
      listener.onCommunicationDeviceChanged(device)
      latch.countDown()
    }

    assertThat(posted).isTrue()
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue()
  }

  private fun setState(manager: FullREDAudioManagerApi31, state: REDAudioManager.State) {
    val stateField = REDAudioManager::class.java.getDeclaredField("state")
    stateField.isAccessible = true
    stateField.set(manager, state)
  }

  private fun setUserSelectedAudioDevice(manager: FullREDAudioManagerApi31, device: AudioDeviceInfo?) {
    val userSelectedField = FullREDAudioManagerApi31::class.java.getDeclaredField("userSelectedAudioDevice")
    userSelectedField.isAccessible = true
    userSelectedField.set(manager, device)
  }

  private fun shutdownManager(manager: FullREDAudioManagerApi31) {
    setState(manager, REDAudioManager.State.UNINITIALIZED)
    setUserSelectedAudioDevice(manager, null)
    manager.shutdown()

    val threadField = REDAudioManager::class.java.getDeclaredField("commandAndControlThread")
    threadField.isAccessible = true

    val start = System.nanoTime()
    while (threadField.get(manager) != null && TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) <= 2_000) {
      Thread.sleep(10)
    }
  }
}
