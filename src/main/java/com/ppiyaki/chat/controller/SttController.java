package com.ppiyaki.chat.controller;

import com.ppiyaki.chat.controller.dto.SttResponse;
import com.ppiyaki.chat.service.SttService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stt")
public class SttController {

    private final SttService sttService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SttResponse> transcribe(
            @RequestParam("file") final MultipartFile file,
            @RequestParam(value = "language", defaultValue = "ko") final String language) {
        final String text = sttService.transcribe(file.getResource(), language);
        return ResponseEntity.ok(new SttResponse(text));
    }
}
