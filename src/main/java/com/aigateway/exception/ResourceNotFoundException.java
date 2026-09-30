package com.aigateway.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException conversation(Long id) {
        return new ResourceNotFoundException("Conversation not found: id=" + id);
    }
}
