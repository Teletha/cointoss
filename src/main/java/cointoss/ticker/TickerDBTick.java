/*
 * Copyright (C) 2024 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.ticker;

import typewriter.duck.DuckModel;

public class TickerDBTick extends DuckModel {

    public long time;

    public double end;

    public double start;

    public double high;

    public double low;
}
