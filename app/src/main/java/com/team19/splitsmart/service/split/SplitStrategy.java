package com.team19.splitsmart.service.split;

import java.util.Map;

/**
 * One split method. inputs is keyed by participant uid. Returns each participant's share in
 * cents; the shares must always add up to totalCents (FR-2.5).
 */
public interface SplitStrategy {

    Map<String, Long> computeShares(long totalCents, Map<String, Double> inputs);
}
