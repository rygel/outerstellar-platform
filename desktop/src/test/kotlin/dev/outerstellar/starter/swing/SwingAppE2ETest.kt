package dev.outerstellar.starter.swing

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.viewmodel.SyncViewModel
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.Locale

class SwingAppE2ETest {

    private val messageService = mockk<MessageService>(relaxed = true)
    private val syncService = mockk<SyncService>(relaxed = true)
    private val i18nService = I18nService.create("messages").also { it.setLocale(Locale.ENGLISH) }

    @Test
    fun `viewmodel correctly holds author and content`() {
        val viewModel = SyncViewModel(messageService, syncService, i18nService)
        viewModel.author = "E2E Tester"
        viewModel.content = "Test content from E2E"
        
        assertEquals("E2E Tester", viewModel.author)
        assertEquals("Test content from E2E", viewModel.content)
    }

    @Test
    fun `creating a message calls the message service`() {
        val viewModel = SyncViewModel(messageService, syncService, i18nService)
        viewModel.author = "E2E Tester"
        viewModel.content = "Test content from E2E"
        
        // Test the viewmodel logic which is what the UI button triggers
        viewModel.createMessage { }
        
        verify { messageService.createLocalMessage("E2E Tester", "Test content from E2E") }
    }
}
