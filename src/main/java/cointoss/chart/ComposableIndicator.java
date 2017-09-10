/*
 * Copyright (C) 2017 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.chart;

import eu.verdelhan.ta4j.Decimal;

/**
 * @version 2017/09/10 14:15:09
 */
public abstract class ComposableIndicator extends Indicator<Decimal> {

    protected final Indicator<Decimal> indicator;

    /**
     * @param chart
     * @param indicator
     */
    protected ComposableIndicator(Indicator<Decimal> indicator) {
        super(indicator.chart);

        this.indicator = indicator;
    }
}
