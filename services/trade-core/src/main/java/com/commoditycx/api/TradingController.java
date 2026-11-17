package com.commoditycx.api;

import com.commoditycx.security.TradingPrincipal;
import com.commoditycx.service.TradingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class TradingController {
    private final TradingService service;
    public TradingController(TradingService service){this.service=service;}
    @GetMapping("/instruments") public List<Map<String,Object>> instruments(){return service.instruments();}
    @PostMapping("/admin/instruments") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE')")
    public Map<String,Object> instrument(@RequestBody Map<String,Object> body,@AuthenticationPrincipal TradingPrincipal actor){return service.createInstrument(body,actor);}
    @PostMapping("/rfqs") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> rfq(@RequestBody Map<String,Object> body,@AuthenticationPrincipal TradingPrincipal actor){return service.createRfq(body,actor);}
    @PostMapping("/rfqs/{rfqId}/quotes") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> quote(@PathVariable UUID rfqId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal TradingPrincipal actor){return service.quote(rfqId,body,actor);}
    @PostMapping("/quotes/{quoteId}/accept")
    public Map<String,Object> accept(@PathVariable UUID quoteId,@AuthenticationPrincipal TradingPrincipal actor){return service.acceptQuote(quoteId,actor);}
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String,Object>> order(@RequestBody Map<String,Object> body,@AuthenticationPrincipal TradingPrincipal actor){return service.submitOrder(body,actor);}
    @DeleteMapping("/orders/{orderId}") public Map<String,Object> cancel(@PathVariable UUID orderId,@AuthenticationPrincipal TradingPrincipal actor){return service.cancelOrder(orderId,actor);}
    @GetMapping("/instruments/{instrumentId}/order-book") public List<Map<String,Object>> book(@PathVariable UUID instrumentId){return service.orderBook(instrumentId);}
    @PostMapping("/trades/{tradeId}/confirm") public Map<String,Object> confirm(@PathVariable UUID tradeId,@AuthenticationPrincipal TradingPrincipal actor){return service.confirmTrade(tradeId,actor);}
    @GetMapping("/audit/{entityId}") @PreAuthorize("hasAnyRole('ADMIN','COMPLIANCE','RISK_OFFICER')")
    public List<Map<String,Object>> audit(@PathVariable UUID entityId){return service.audit(entityId);}
    @PutMapping("/admin/participants/{participantId}/risk-limits") @PreAuthorize("hasAnyRole('ADMIN','RISK_OFFICER')")
    public Map<String,Object> risk(@PathVariable UUID participantId,@RequestBody Map<String,Object> body,@AuthenticationPrincipal TradingPrincipal actor){
        service.setRisk(participantId,body,actor);return Map.of("participantId",participantId,"updated",true);
    }
}
