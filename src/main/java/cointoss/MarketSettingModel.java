/*
 * Copyright (C) 2021 cointoss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss;

import java.util.List;

import cointoss.execution.ExecutionDeltaLogger;
import cointoss.execution.ExecutionLog;
import cointoss.execution.ExecutionLogger;
import cointoss.util.arithmetic.Num;
import icy.manipulator.Icy;
import kiss.I;

@Icy
interface MarketSettingModel {

    /**
     * Sepcify the market type.
     * 
     * @return
     */
    @Icy.Property
    MarketType type();

    /**
     * Specify the target currency.
     * 
     * @return
     */
    @Icy.Property
    CurrencySetting target();

    /**
     * Specify the base currency.
     * 
     * @return
     */
    @Icy.Property
    CurrencySetting base();

    /**
     * Get the bid size range of target currency.
     */
    @Icy.Property
    default List<Num> targetCurrencyBidSizes() {
        return List.of(Num.ONE);
    }

    /**
     * Get the price range modifier of base currency.
     */
    @Icy.Property
    default int priceRangeModifier() {
        return 10;
    }

    /**
     * Get the recommended price range of base currency.
     */
    default Num recommendedPriceRange() {
        return base().minimumSize.multiply(priceRangeModifier());
    }

    /**
     * Configure max acquirable execution size per one request.
     * 
     * @return
     */
    @Icy.Property
    default int acquirableExecutionSize() {
        return 100;
    }

    /**
     * Configure {@link ExecutionLog} parser.
     * 
     * @return
     */
    @Icy.Property
    default Class<? extends ExecutionLogger> executionLogger() {
        return ExecutionDeltaLogger.class;
    }

    /**
     * Create new {@link ExecutionLogger}.
     * 
     * @return
     */
    public default ExecutionLogger createExecutionLogger() {
        return I.make(executionLogger());
    }
}