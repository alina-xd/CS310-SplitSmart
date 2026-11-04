package com.team19.splitsmart.service.split;

import com.team19.splitsmart.data.model.SplitMethod;

/** Returns the SplitStrategy for a split method. Owner: Part C. */
public class SplitStrategyFactory {

    public SplitStrategy getStrategy(SplitMethod method) {
        switch (method) {
            case EXACT_AMOUNT:
                return new ExactAmountSplit();
            case PERCENTAGE:
                return new PercentageSplit();
            case EQUAL:
            default:
                return new EqualSplit();
        }
    }
}
