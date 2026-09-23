package com.aegispunish.core.util;

import java.util.List;

public class ProofValidator {

    private final List<String> allowedDomains;

    public ProofValidator(List<String> allowedDomains) {
        this.allowedDomains = allowedDomains == null || allowedDomains.isEmpty()
                ? List.of("https://", "youtube.com", "youtu.be", "imgur.com", "prnt.sc")
                : allowedDomains;
    }

    public boolean isValid(String proof) {
        if (proof == null || proof.isBlank()) return false;
        String lower = proof.toLowerCase();
        for (String domain : allowedDomains) {
            if (lower.contains(domain.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
