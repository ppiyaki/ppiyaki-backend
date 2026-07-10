package com.ppiyaki.user.controller;

import com.ppiyaki.user.controller.dto.TimezoneResponse;
import com.ppiyaki.user.service.TimezoneService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timezones")
public class TimezoneController {

    private final TimezoneService timezoneService;

    public TimezoneController(final TimezoneService timezoneService) {
        this.timezoneService = timezoneService;
    }

    @GetMapping
    public ResponseEntity<List<TimezoneResponse>> readTimezones() {
        final List<TimezoneResponse> responses = timezoneService.readTimezones();
        return ResponseEntity.ok(responses);
    }
}
