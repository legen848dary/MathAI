package com.insoftu.mathai.admin.service;

import com.insoftu.mathai.admin.model.AppSettings;
import com.insoftu.mathai.admin.repository.AppSettingsRepository;
import com.insoftu.mathai.ai.AiProviderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminSettingsService {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsService.class);
    private final AppSettingsRepository appSettingsRepository;
    private final AiProviderManager aiProviderManager;

    public AdminSettingsService(AppSettingsRepository appSettingsRepository,
                                AiProviderManager aiProviderManager) {
        this.appSettingsRepository = appSettingsRepository;
        this.aiProviderManager = aiProviderManager;
    }

    /**
     * Returns all settings as key-value pairs plus available AI providers.
     */
    public Map<String, Object> getAllSettings() {
        Map<String, String> settings = appSettingsRepository.findAll().stream()
                .collect(Collectors.toMap(AppSettings::getKey, AppSettings::getValue));

        Set<String> availableProviders = aiProviderManager.getAvailableProviders().keySet();
        String currentProvider = settings.getOrDefault("ai_provider", "gemini");

        return Map.of(
                "settings", settings,
                "currentProvider", currentProvider,
                "availableProviders", availableProviders
        );
    }

    /**
     * Switches the AI provider at runtime.
     */
    @Transactional
    public void setAiProvider(String providerName) {
        // Validate provider exists
        if (!aiProviderManager.getAvailableProviders().containsKey(providerName)) {
            throw new IllegalArgumentException(
                    "Unknown AI provider: " + providerName +
                    ". Available: " + aiProviderManager.getAvailableProviders().keySet());
        }

        AppSettings setting = appSettingsRepository.findById("ai_provider")
                .orElse(new AppSettings("ai_provider", providerName));

        setting.setValue(providerName);
        appSettingsRepository.save(setting);

        log.info("AI provider switched to '{}'", providerName);
    }

    /**
     * Returns the current AI provider name.
     */
    public String getCurrentAiProvider() {
        return appSettingsRepository.findById("ai_provider")
                .map(AppSettings::getValue)
                .orElse("gemini");
    }

    /**
     * Sets an arbitrary app setting key-value pair.
     */
    @Transactional
    public void setSetting(String key, String value) {
        AppSettings setting = appSettingsRepository.findById(key)
                .orElse(new AppSettings(key, value));
        setting.setValue(value);
        appSettingsRepository.save(setting);
    }
}
