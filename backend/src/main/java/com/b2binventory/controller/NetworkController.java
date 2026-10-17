package com.b2binventory.controller;

import com.b2binventory.dto.TradeDtos.*;
import com.b2binventory.entity.ConnectionStatus;
import com.b2binventory.service.trade.NetworkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Business network: find other businesses and connect. Permissions are checked in NetworkService. */
@RestController
@RequestMapping("/api/network")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Business network")
public class NetworkController {

    private final NetworkService network;

    public NetworkController(NetworkService network) {
        this.network = network;
    }

    @GetMapping("/search")
    public List<SearchResult> search(@RequestParam String q) {
        return network.search(q);
    }

    @GetMapping("/me")
    public MyBusinessCode me() {
        return network.myCode();
    }

    @PutMapping("/me/discoverable")
    public MyBusinessCode discoverable(@RequestBody DiscoverableRequest request) {
        return network.setDiscoverable(request.discoverable());
    }

    @GetMapping("/connections")
    public List<ConnectionResponse> connections(@RequestParam(required = false) ConnectionStatus status) {
        return network.list(status);
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponse request(@Valid @RequestBody ConnectionRequest request) {
        return network.request(request);
    }

    @PostMapping("/connections/{id}/accept")
    public ConnectionResponse accept(@PathVariable Long id) {
        return network.accept(id);
    }

    @PostMapping("/connections/{id}/reject")
    public ConnectionResponse reject(@PathVariable Long id) {
        return network.reject(id);
    }

    @PostMapping("/connections/{id}/remove")
    public ConnectionResponse remove(@PathVariable Long id) {
        return network.remove(id);
    }
}
