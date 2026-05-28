package com.ppiyaki.infrastructure.druginfo;

import java.util.Optional;

public interface DrugInfoCache {

    Optional<DrugInfoResponse> get(String itemName);

    void put(String itemName, Optional<DrugInfoResponse> response);
}
