package com.commoditycx.security;
import java.util.UUID;
public record TradingPrincipal(UUID userId, String email, UUID participantId) {}
