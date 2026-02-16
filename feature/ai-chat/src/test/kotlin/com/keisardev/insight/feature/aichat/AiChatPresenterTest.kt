package com.keisardev.insight.feature.aichat

import com.google.common.truth.Truth.assertThat
import com.keisardev.insight.core.model.ModelState
import com.keisardev.insight.feature.aichat.fakes.FakeChatRepository
import com.keisardev.insight.feature.aichat.fakes.FakeModelDownloadTrigger
import com.keisardev.insight.feature.aichat.fakes.FakeModelRepository
import com.slack.circuit.test.FakeNavigator
import com.slack.circuit.test.presenterTestOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AiChatPresenterTest {

    private lateinit var chatRepository: FakeChatRepository
    private lateinit var modelRepository: FakeModelRepository
    private lateinit var modelDownloadTrigger: FakeModelDownloadTrigger
    private lateinit var navigator: FakeNavigator

    @Before
    fun setup() {
        chatRepository = FakeChatRepository(isEnabled = true)
        modelRepository = FakeModelRepository()
        modelDownloadTrigger = FakeModelDownloadTrigger()
        navigator = FakeNavigator(AiChatScreen)
    }

    @Test
    fun `initial state when AI is enabled`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            // Consume welcome message emissions from LaunchedEffect
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }
            assertThat(state.isAiEnabled).isTrue()
            assertThat(state.isLoading).isFalse()
            assertThat(state.inputText).isEmpty()
            assertThat(state.showModelSetup).isFalse()
        }
    }

    @Test
    fun `initial state when AI is disabled`() = runTest {
        chatRepository = FakeChatRepository(isEnabled = false)

        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.isAiEnabled).isFalse()
            assertThat(state.showModelSetup).isTrue()
        }
    }

    @Test
    fun `OnInputChange updates input text`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }

            state.eventSink(AiChatScreen.Event.OnInputChange("Hello"))

            val updated = awaitItem()
            assertThat(updated.inputText).isEqualTo("Hello")
        }
    }

    @Test
    fun `OnSend sends message through repository`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }

            state.eventSink(AiChatScreen.Event.OnInputChange("How much did I spend?"))
            state = awaitItem()
            state.eventSink(AiChatScreen.Event.OnSend)

            // Input should be cleared and message sent
            do {
                state = awaitItem()
            } while (state.inputText.isNotEmpty())

            assertThat(state.inputText).isEmpty()
            assertThat(chatRepository.sendMessageCallCount).isEqualTo(1)
            assertThat(chatRepository.lastSentContent).isEqualTo("How much did I spend?")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OnSend with blank input does nothing`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }

            state.eventSink(AiChatScreen.Event.OnSend)

            // No message should be sent
            assertThat(chatRepository.sendMessageCallCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OnDismissModelSetup hides model setup`() = runTest {
        chatRepository = FakeChatRepository(isEnabled = false)

        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            val state = awaitItem()
            assertThat(state.showModelSetup).isTrue()

            state.eventSink(AiChatScreen.Event.OnDismissModelSetup)

            val updated = awaitItem()
            assertThat(updated.showModelSetup).isFalse()
        }
    }

    @Test
    fun `OnCancelDownload cancels download and stops service`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }

            state.eventSink(AiChatScreen.Event.OnCancelDownload)

            assertThat(modelRepository.cancelDownloadCallCount).isEqualTo(1)
            assertThat(modelDownloadTrigger.stopCallCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `OnChangeModel shows model selection`() = runTest {
        presenterTestOf(
            presentFunction = {
                AiChatPresenter(
                    navigator = navigator,
                    chatRepository = chatRepository,
                    modelRepository = modelRepository,
                    modelDownloadTrigger = modelDownloadTrigger,
                ).present()
            },
        ) {
            var state = awaitItem()
            if (state.messages.isEmpty()) {
                state = awaitItem()
            }
            assertThat(state.showModelSelection).isFalse()

            state.eventSink(AiChatScreen.Event.OnChangeModel)

            val updated = awaitItem()
            assertThat(updated.showModelSelection).isTrue()
        }
    }
}
