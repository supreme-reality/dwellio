package com.dwellio.api.document;

public interface ObjectStorage {

    String createPresignedPutUrl(String storageKey, String contentType);

    boolean objectExists(String storageKey);

    void putObject(String storageKey, byte[] body, String contentType);

    void deleteObject(String storageKey);
}
