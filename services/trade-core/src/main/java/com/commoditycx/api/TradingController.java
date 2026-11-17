package com.commoditycx.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:4200")
public class TradingController {
    private final List<InstrumentView> instruments = List.of(
        new InstrumentView(1L, "CRUDE-BRENT", "Crude Oil", "USD", "BBL"),
        new InstrumentView(2L, "GOLD-1KG", "Gold", "USD", "KG")
    );

    @GetMapping("/health")
    public Health health() { return new Health("UP", Instant.now()); }

    @GetMapping("/instruments")
    public List<InstrumentView> instruments() { return instruments; }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderAcknowledgement submit(@Valid @RequestBody NewOrder order) {
        if ("LIMIT".equals(order.orderType()) && order.price() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LIMIT orders require price");
        }
        if (instruments.stream().noneMatch(i -> i.id().equals(order.instrumentId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrument not found");
        }
        // Demo acknowledgement only. No persistence, matching, risk checks or execution occurs.
        return new OrderAcknowledgement(UUID.randomUUID(), "RECEIVED_NOT_EXECUTED", Instant.now());
    }

    public record Health(String status, Instant time) {}
    public record InstrumentView(Long id, String symbol, String commodity, String currency, String unit) {}
    public record NewOrder(@NotNull Long instrumentId,
        @NotBlank String participantId,
        @Pattern(regexp = "BUY|SELL") String side,
        @Pattern(regexp = "LIMIT|MARKET") String orderType,
        @DecimalMin("0.00000001") BigDecimal price,
        @NotNull @DecimalMin("0.00000001") BigDecimal quantity) {}
    public record OrderAcknowledgement(UUID orderId, String status, Instant receivedAt) {}
}
