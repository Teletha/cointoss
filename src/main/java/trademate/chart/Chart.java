/*
 * Copyright (C) 2024 The COINTOSS Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package trademate.chart;

import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleFunction;

import cointoss.ticker.Span;
import cointoss.ticker.Ticker;
import cointoss.util.Chrono;
import hypatia.Primitives;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.layout.Region;
import trademate.setting.PerformanceSetting;
import viewtify.preference.Preferences;
import viewtify.ui.helper.LayoutAssistant;

public class Chart extends Region {

    /** The time unit interval. */
    private static long M = 60;

    private static long D = M * 60 * 24;

    /** The x-axis UI. */
    public final Axis axisX = new Axis(1, 15, Side.BOTTOM)
            .units(M, 5 * M, 10 * M, 30 * M, 60 * M, 2 * 60 * M, 4 * 60 * M, 6 * 60 * M, 12 * 60 * M, D, 2 * D, 3 * D, 7 * D, 15 * D, 30 * D, 90 * D, 180 * D, 360 * D)
            .padding(60);

    /** The y-axis UI. */
    public final Axis axisY = new Axis(4, 8, Side.RIGHT).visibleScroll(false);

    /** The minimum number of ticks. */
    private final IntegerProperty minimumTickNumber = new SimpleIntegerProperty(25);

    /** The maximum number of ticks. */
    private final IntegerProperty maximumTickNumber = new SimpleIntegerProperty(2000);

    /** The chart view. */
    private final ChartView chart;

    /** The actual graph drawer. */
    private final ChartCanvas canvas;

    /** The layout manager. */
    private final LayoutAssistant layoutChart = new LayoutAssistant(this);

    /**
     * 
     */
    public Chart(ChartView chart) {
        this.chart = chart;
        this.canvas = new ChartCanvas(chart, axisX, axisY);

        PerformanceSetting performance = Preferences.of(PerformanceSetting.class);

        layoutChart.layoutBy(widthProperty(), heightProperty())
                .layoutBy(axisX.scroll.valueProperty(), axisX.scroll.visibleAmountProperty())
                .layoutBy(axisY.scroll.valueProperty(), axisY.scroll.visibleAmountProperty())
                .layoutBy(chart.ticker.observe())
                .layoutBy(chart.ticker.observe()
                        .switchMap(ticker -> ticker.open.startWithNull())
                        .throttle(performance.refreshRate, TimeUnit.MILLISECONDS));

        // configure axis label
        chart.market.observe().to(m -> {
            DoubleFunction<String> readablePrice = p -> Primitives.roundString(p, m.service.setting.base.scale);
            DoubleFunction<String> readableTime = seconds -> {
                ZonedDateTime time = Chrono.systemBySeconds((long) seconds);
                int hour = time.getHour() * 60 * 60 - time.getOffset().getTotalSeconds();

                if (time.getMinute() == 0 && hour == 0) {
                    return time.format(Chrono.Date);
                } else {
                    return time.format(Chrono.TimeWithoutSec);
                }
            };

            axisX.tickLabelFormatter.set(readableTime);
            axisY.tickLabelFormatter.set(readablePrice);
        });

        getChildren().addAll(canvas, axisX, axisY);
    }

    /**
     * Layout chart immediately.
     */
    public final void layoutForcely() {
        layoutChart.layoutForcely();
        canvas.layoutForcely();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void layoutChildren() {
        layoutChart.layout(() -> {
            Insets insets = getInsets();
            double x = insets.getLeft();
            double y = insets.getTop();
            double width = getWidth() - x - insets.getRight();
            double height = getHeight() - y - insets.getBottom();
            double axisXHeight = axisX.prefHeight(width);
            double axisYWidth = axisY.prefWidth(height);
            double mainHeight = Math.max(0, height - axisXHeight);
            double mainWidth = Math.max(0, width - axisYWidth);

            setAxisXRange((int) width);
            setAxisYRange();

            // layout axis
            axisX.resizeRelocate(x, y + mainHeight, mainWidth, axisXHeight);
            axisY.resizeRelocate(x + mainWidth, y, axisYWidth, mainHeight);
            axisX.layout();
            axisY.layout();

            // layout chart
            canvas.resizeRelocate(x, y, mainWidth, mainHeight);
            canvas.layoutChildren();
        });
    }

    /**
     * Set x-axis range.
     */
    private void setAxisXRange(int width) {
        chart.ticker.to(ticker -> {
            if (ticker.ticks.isEmpty()) {
                return;
            }

            minimumTickNumber.set(Math.round(width / 7));

            long seconds = ticker.span.seconds;
            axisX.logicalMinValue.set(Math.max(ticker.ticks.firstTime(), ticker.ticks.computeLogicalFirstCacheTime()));
            axisX.logicalMaxValue.set(ticker.latest().openTime());
            axisX.visibleMinRange.set(minimumTickNumber.doubleValue() * seconds);
            axisX.visibleMaxRange.set(maximumTickNumber.doubleValue() * seconds);
            axisX.zoom();
        });
    }

    /**
     * Set y-axis range.
     */
    private void setAxisYRange() {
        double[] minmax = {Double.MAX_VALUE, Double.MIN_VALUE};

        long start = (long) axisX.computeVisibleMinValue();
        long end = (long) axisX.computeVisibleMaxValue();
        long duration = end - start;

        Span span;

        if (chart.ticker.isPresent()) {
            span = chart.ticker.v.span;
        } else if (1 < duration / 86400 /* 60x60x24 */) {
            span = Span.Day;
        } else if (1 < duration / 3600 /* 60x60 */) {
            span = Span.Hour1;
        } else if (1 < duration / 300 /* 60x5 */) {
            span = Span.Minute5;
        } else {
            span = Span.Minute1;
        }

        if (chart.market.isPresent()) {
            Ticker ticker = chart.market.v.tickers.on(span);
            ticker.ticks.query(start, end).to(tick -> {
                double high = tick.highPrice();
                double low = tick.lowPrice();
                if (low < minmax[0]) minmax[0] = low;
                if (minmax[1] < high) minmax[1] = high;
            });
        }

        double margin = (minmax[1] - minmax[0]) * 0.5d;
        axisY.logicalMaxValue.set(minmax[1] + margin);
        axisY.logicalMinValue.set(minmax[0] - margin);
    }
}