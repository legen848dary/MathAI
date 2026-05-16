package com.insoftu.mathai.ai;

import com.insoftu.mathai.admin.model.AppSettings;
import com.insoftu.mathai.admin.repository.AppSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiProviderManagerTest {

    @Mock private GeminiAiService geminiAiService;
    @Mock private NovitaAiService novitaAiService;
    @Mock private AppSettingsRepository appSettingsRepository;

    private AiProviderManager manager;

    @BeforeEach
    void setUp() {
        lenient().when(geminiAiService.getProviderName()).thenReturn("gemini");
        lenient().when(novitaAiService.getProviderName()).thenReturn("novita");

        manager = new AiProviderManager(
                geminiAiService,
                novitaAiService,
                appSettingsRepository
        );
    }

    @Test
    void shouldReturnGeminiByDefault() {
        when(appSettingsRepository.findById("ai_provider")).thenReturn(Optional.empty());

        AiService provider = manager.getCurrentProvider();
        assertEquals("gemini", provider.getProviderName());
    }

    @Test
    void shouldReturnConfiguredProvider() {
        when(appSettingsRepository.findById("ai_provider"))
                .thenReturn(Optional.of(new AppSettings("ai_provider", "novita")));

        AiService provider = manager.getCurrentProvider();
        assertEquals("novita", provider.getProviderName());
    }

    @Test
    void shouldThrowForUnknownProvider() {
        when(appSettingsRepository.findById("ai_provider"))
                .thenReturn(Optional.of(new AppSettings("ai_provider", "openai")));

        AiServiceException ex = assertThrows(AiServiceException.class,
                () -> manager.getCurrentProvider());
        assertTrue(ex.getMessage().contains("Unknown AI provider"));
    }

    @Test
    void shouldExposeAvailableProviders() {
        Map<String, AiService> providers = manager.getAvailableProviders();
        assertEquals(2, providers.size());
        assertTrue(providers.containsKey("gemini"));
        assertTrue(providers.containsKey("novita"));
    }
}
