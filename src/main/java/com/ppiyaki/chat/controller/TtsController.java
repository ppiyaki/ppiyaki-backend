package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.TtsRequest;
import com.ppiyaki.chat.service.TtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tts")
public class TtsController {

    private final TtsService ttsService;

    @PostMapping
    public ResponseEntity<byte[]> synthesize(@RequestBody @Valid final TtsRequest ttsRequest) {
        final byte[] audioBytes = ttsService.synthesize(ttsRequest.text());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .body(audioBytes);
    }
}
