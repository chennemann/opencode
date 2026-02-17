package de.chennemann.opencode.mobile.domain.usecase.connection

import de.chennemann.opencode.mobile.domain.service.connection.ConnectionActionService
import de.chennemann.opencode.mobile.domain.service.model.RefreshInput
import de.chennemann.opencode.mobile.domain.service.model.RefreshResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RefreshServerUseCaseTest {
    @Test
    fun rejectsBlankUrl() = runTest {
        val action = RecordingConnectionActionService()
        val useCase = RefreshServerUseCase(action)

        val result = useCase("   ")

        assertEquals(
            RefreshServerResult(
                accepted = false,
                endpoint = null,
                reason = "url_blank",
            ),
            result,
        )
        assertTrue(action.calls.isEmpty())
    }

    @Test
    fun rejectsMalformedUrl() = runTest {
        val action = RecordingConnectionActionService()
        val useCase = RefreshServerUseCase(action)

        val result = useCase("bad url")

        assertEquals(
            RefreshServerResult(
                accepted = false,
                endpoint = null,
                reason = "url_invalid",
            ),
            result,
        )
        assertTrue(action.calls.isEmpty())
    }

    @Test
    fun normalizesAndRefreshesValidUrl() = runTest {
        val action = RecordingConnectionActionService().also {
            it.result = RefreshResult(accepted = true, reason = null)
        }
        val useCase = RefreshServerUseCase(action)

        val result = useCase("demo.local:4096/")

        assertEquals(
            RefreshServerResult(
                accepted = true,
                endpoint = "http://demo.local:4096",
                reason = null,
            ),
            result,
        )
        assertEquals(
            listOf(
                RefreshInput(
                    endpoint = "http://demo.local:4096",
                    userInitiated = true,
                )
            ),
            action.calls,
        )
    }

    @Test
    fun forwardsFailureReasonFromAction() = runTest {
        val action = RecordingConnectionActionService().also {
            it.result = RefreshResult(accepted = false, reason = "Connection failed")
        }
        val useCase = RefreshServerUseCase(action)

        val result = useCase("http://demo.local:4096")

        assertEquals(
            RefreshServerResult(
                accepted = false,
                endpoint = "http://demo.local:4096",
                reason = "Connection failed",
            ),
            result,
        )
    }
}

private class RecordingConnectionActionService : ConnectionActionService {
    val calls = mutableListOf<RefreshInput>()
    var result = RefreshResult(accepted = true, reason = null)

    override suspend fun refresh(input: RefreshInput): RefreshResult {
        calls += input
        return result
    }
}
