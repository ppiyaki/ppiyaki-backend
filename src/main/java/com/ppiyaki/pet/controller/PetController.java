package com.ppiyaki.pet.controller;

import com.ppiyaki.pet.BadgeType;
import com.ppiyaki.pet.controller.dto.BadgeTypeResponse;
import com.ppiyaki.pet.controller.dto.PetResponse;
import com.ppiyaki.pet.service.PetService;
import java.util.Arrays;
import java.util.List;
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

    @GetMapping("/badges/types")
    public ResponseEntity<List<BadgeTypeResponse>> readBadgeTypes() {
        final List<BadgeTypeResponse> responses = Arrays.stream(BadgeType.values())
                .map(BadgeTypeResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
