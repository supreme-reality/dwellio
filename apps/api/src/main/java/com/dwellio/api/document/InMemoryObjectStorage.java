package com.dwellio.api.document;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "dwellio.storage.type", havingValue = "memory")
public class InMemoryObjectStorage implements ObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String createPresignedPutUrl(String storageKey, String contentType) {
        return "memory://upload/" + storageKey;
    }

    @Override
    public boolean objectExists(String storageKey) {
        return objects.containsKey(storageKey);
    }

    @Override
    public void putObject(String storageKey, byte[] body, String contentType) {
        objects.put(storageKey, body == null ? new byte[0] : body);
    }

    @Override
    public void deleteObject(String storageKey) {
        objects.remove(storageKey);
    }
}
