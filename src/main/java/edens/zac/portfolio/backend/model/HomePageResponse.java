package edens.zac.portfolio.backend.model;

import java.time.Instant;
import java.util.List;

// Simple wrapper for home page response to keep top-level JSON an object
public record HomePageResponse(
        List<HomeCardModel> items,
        int count,
        int maxPriority,
        Instant generatedAt
) {}
