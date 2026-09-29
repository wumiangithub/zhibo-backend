package com.zhibo.liveprovider;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class LiveProviderRegistry {

    private final Map<String, LiveProvider> providers;

    public LiveProviderRegistry(List<LiveProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(LiveProvider::code, Function.identity()));
    }

    public LiveProvider require(String provider) {
        LiveProvider liveProvider = providers.get(provider);
        if (liveProvider == null) {
            throw new ProviderNotAvailableException();
        }
        return liveProvider;
    }
}
