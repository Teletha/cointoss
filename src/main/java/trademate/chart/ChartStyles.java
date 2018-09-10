/*
 * Copyright (C) 2017 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package trademate.chart;

import stylist.Style;
import stylist.StyleDSL;
import stylist.value.Color;

/**
 * @version 2018/09/03 1:21:23
 */
public interface ChartStyles extends StyleDSL {

    Color buy = $.rgb(32, 151, 77);

    Color sell = $.rgb(247, 105, 77);

    Style Label = () -> {
        font.size(11, px);
        background.color("-fx-background");
        display.opacity(0.8);
    };

    Style BackGrid = () -> {
        stroke.width(0.5, px).color($.hsl(0, 0, 16));
        font.color("-fx-mid-text-color");
    };

    Style MouseTrack = () -> {
        stroke.width(0.5, px).color($.rgb(80, 80, 80));
        font.color("-fx-light-text-color");
    };

    Style PriceSignal = () -> {
        Color color = $.rgb(180, 100, 100);

        stroke.width(0.5, px).color(color);
        font.color(color);
    };

    Style PriceLatest = () -> {
        Color color = $.rgb(140, 100, 40);

        stroke.width(0.5, px).color(color);
        font.color(color);
    };

    Style PriceSFD = () -> {
        Color color = $.rgb(40, 100, 40);

        stroke.width(0.5, px).color(color);
        font.color(color);
    };

    Style OrderSupportBuy = () -> {
        stroke.width(0.5, px).color(buy).dashArray(2, 4);
        font.color(buy);
    };

    Style OrderSupportSell = () -> {
        stroke.width(0.5, px).color(sell).dashArray(2, 4);
        font.color(sell);
    };
}
