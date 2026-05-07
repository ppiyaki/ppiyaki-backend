package com.ppiyaki.pet.repository;

import com.ppiyaki.pet.Pet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {
}
