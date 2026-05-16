package com.insoftu.mathai.ai;

import com.insoftu.mathai.admin.repository.AppSettingsRepository;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AiProviderManager {

    private final Map<String, AiService> providers;
    private final AppSettingsRepository appSettingsRepository;

    public AiProviderManager(GeminiAiService geminiAiService,
                             NovitaAiService novitaAiService,
                             AppSettingsRepository appSettingsRepository) {
        this.providers = Map.of(
                "gemini", geminiAiService,
                "novita", novitaAiService
        );
        this.appSettingsRepository = appSettingsRepository;
    }

    /**
     * Returns the currently configured AI service based on the app_settings table.
     * If no setting exists, falls back to gemini.
     */
    public AiService getCurrentProvider() {
        String providerKey = appSettingsRepository.findById("ai_provider")
                .map(s -> s.getValue())
                .orElse("gemini");

        AiService provider = providers.get(providerKey);
        if (provider == null) {
            throw new AiServiceException(
                "Unknown AI provider: " + providerKey + ". Valid options: gemini, novita");
        }
        return provider;
    }

    /**
     * Returns all available providers. Used by the admin UI to populate the dropdown.
     */
    public Map<String, AiService> getAvailableProviders() {
        return providers;
    }
}
