/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss;

/**
 * @version 2017/08/20 18:46:21
 */
public interface Directional {

    /**
     * Utility to detect.
     * 
     * @return
     */
    default boolean isBuy() {
        return side() == Direction.BUY;
    }

    /**
     * Utility to detect.
     * 
     * @return
     */
    default boolean isSell() {
        return side() == Direction.SELL;
    }

    /**
     * Utility to inverse {@link Direction}.
     * 
     * @return
     */
    default Direction inverse() {
        return side() == Direction.BUY ? Direction.SELL : Direction.BUY;
    }

    /**
     * Get {@link Direction}.
     * 
     * @return
     */
    Direction side();
}
