/*
 * Copyright (C) 2023 The COINTOSS Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package cointoss.market.bitbank;

import cointoss.market.MarketAccount;
import kiss.Variable;

public class BitbankAccount extends MarketAccount<BitbankAccount> {

    /** The API key. */
    public final Variable<String> apiKey = Variable.empty();

    /** The API secret. */
    public final Variable<String> apiSecret = Variable.empty();

    /**
     * Hide constructor.
     */
    private BitbankAccount() {
        restore().auto();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean validate() {
        return true;
    }
}