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

import java.util.Objects;
import java.util.function.ToDoubleFunction;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.magicwerk.brownies.collections.BigList;

import cointoss.chart.Chart;
import cointoss.chart.Tick;

/**
 * @version 2017/09/27 21:41:30
 */
public class CandleChartData {

    public final StringProperty name = new SimpleStringProperty(this, "name", "");

    int defaultColorIndex;

    String defaultColor;

    private Chart chart;

    /**
     * @param capacity
     */
    public CandleChartData(Chart chart) {
        this.chart = Objects.requireNonNull(chart);
    }

    public int size() {
        return chart.ticks.size();
    }

    public double getX(final int index) throws ArrayIndexOutOfBoundsException {
        if (index < 0 || index >= chart.ticks.size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return chart.getTick(index).start.toInstant().toEpochMilli();
    }

    public double getY(final int index) throws ArrayIndexOutOfBoundsException {
        if (index < 0 || index >= chart.ticks.size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return chart.getTick(index).getWeightMedian().toDouble();
    }

    /**
     * <b>このデータがxでソートされているときに限り</b>valueが出てくるインデックスを検索します。
     * minModeにより、valueを越えない最大インデックスか、valueより大きな最小インデックスを探すかを変えられます。<br>
     * このデータがソートされていないときの挙動は保証しません
     * 
     * @param value
     * @param minMode trueの時、value「より小さくならない最小の」インデックスを検索する。<br>
     *            falseの時、value「を越えない最大の」インデックスを検索する
     * @return
     */
    public int searchXIndex(final double value, final boolean minMode) {
        if (minMode) {
            return findMinIndex(chart.ticks, chart.ticks.size(), value, i -> i.start.toInstant().toEpochMilli());
        } else {
            return findMaxIndex(chart.ticks, chart.ticks.size(), value, i -> i.start.toInstant().toEpochMilli());
        }
    }

    /**
     * ※aが昇順に整列されているときに限る。 valueを越えない最大のaの場所を探索する
     * 
     * @param a
     * @param size
     * @param value
     * @return
     */
    private static int findMaxIndex(BigList<Tick> a, int size, double value, ToDoubleFunction<Tick> converter) {
        int start = 1, end = size - 2, middle = (start + end) >> 1;

        while (end - start > 1) {
            final double d = converter.applyAsDouble(a.get(middle));
            if (d == value) {
                return middle;
            }
            if (d < value) {
                start = middle;
            } else {
                end = middle;
            }
            middle = (start + end) >> 1;
        }

        if (converter.applyAsDouble(a.get(start)) > value) {
            return start - 1;
        }
        if (converter.applyAsDouble(a.get(end)) <= value) {
            return end;
        }
        return start;
    }

    /**
     * ※aが昇順に整列されているときに限る。 vより小さくならない最小のaの場所を探索する
     * 
     * @param a
     * @param size
     * @param v
     * @return
     */
    private static int findMinIndex(BigList<Tick> a, int size, double v, ToDoubleFunction<Tick> converter) {
        if (size < 2) {
            return 0;
        }
        if (size == 2) {
            return 1;
        }
        int l = 1, r = size - 2, m = (l + r) >> 1;

        while (r - l > 1) {
            final double d = converter.applyAsDouble(a.get(m));
            if (d == v) {
                return m;
            }
            if (d < v) {
                l = m;
            } else {
                r = m;
            }
            m = (l + r) >> 1;
        }
        if (converter.applyAsDouble(a.get(l)) >= v) {
            return l;
        }
        if (converter.applyAsDouble(a.get(r)) < v) {
            return r + 1;
        }
        return r;
    }
}
