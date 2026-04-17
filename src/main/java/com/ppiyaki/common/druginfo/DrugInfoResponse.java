package com.ppiyaki.common.druginfo;

public record DrugInfoResponse(
        String itemName,
        String entpName,
        String efficacy,
        String usage,
        String precautions,
        String interactions,
        String sideEffects,
        String storageMethod,
        String imageUrl
) {
}
