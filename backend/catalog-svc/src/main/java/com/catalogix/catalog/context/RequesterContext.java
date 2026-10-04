package com.catalogix.catalog.context;

public record RequesterContext(
    String bearerToken,
    Long requesterId,
    String requesterRole) {
}