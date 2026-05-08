package com.ppiyaki.pet.controller;

import com.ppiyaki.pet.controller.dto.PetResponse;
import com.ppiyaki.pet.service.PetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {

    private final PetService petService;

    public PetController(final PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/me")
    public ResponseEntity<PetResponse> readMyPet(@AuthenticationPrincipal final Long userId) {
        final PetResponse petResponse = petService.readMyPet(userId);
        return ResponseEntity.ok(petResponse);
    }
}
