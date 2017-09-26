/*
 * Copyright (C) 2017 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package cointoss.visual;

import java.util.BitSet;
import java.util.List;

import javafx.beans.InvalidationListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineJoin;

import cointoss.chart.Tick;
import cointoss.visual.shape.GraphLine;
import cointoss.visual.shape.GraphShape;

/**
 * グラフを実際に描画するエリアです
 */
public class GraphPlotArea extends Region {

    /** The validator. */
    protected final InvalidationListener plotValidateListener = observable -> {
        if (isPlotValidate()) {
            setPlotValidate(false);
            setGraphShapeValidate(false);
            setNeedsLayout(true);
        }
    };

    private final Rectangle clip = new Rectangle();

    private final Group background = new LocalGroup();

    /** The line chart manager */
    private final Group lines = new LocalGroup();

    /** The candle chart manager */
    private final Group candles = new LocalGroup();

    private final Group foreground = new LocalGroup();

    private final Group userBackround = new LocalGroup();

    private final Group userForeground = new LocalGroup();

    private final Path verticalGridLines = new Path();

    private final Path horizontalGridLines = new Path();

    private final Path verticalMinorGridLines = new Path();

    private final Path horizontalMinorGridLines = new Path();

    private final Path verticalRowFill = new Path();

    private final Path horizontalRowFill = new Path();

    /** The line chart color manager. */
    private final BitSet lineColorManager = new BitSet(8);

    /** The line chart data list. */
    private ObservableList<Tick> candleChartData;

    /** The line chart data list. */
    private ObservableList<LineChartData> lineChartData;

    /** The line chart data observer. */
    private final InvalidationListener lineDataObserver = o -> {
        ReadOnlyBooleanProperty b = (ReadOnlyBooleanProperty) o;
        if (!b.get() && isPlotValidate()) {
            setPlotValidate(false);
            setNeedsLayout(true);
        }
    };

    /** The line chart data list observer. */
    private final ListChangeListener<LineChartData> lineDataListObserver = change -> {
        change.next();
        for (LineChartData d : change.getRemoved()) {
            lineColorManager.clear(d.defaultColorIndex);
            d.validateProperty().removeListener(lineDataObserver);
        }
        for (LineChartData d : change.getAddedSubList()) {
            d.defaultColorIndex = lineColorManager.nextClearBit(0);
            lineColorManager.set(d.defaultColorIndex, true);
            d.defaultColor = "default-color" + (d.defaultColorIndex % 8);
            d.validateProperty().addListener(lineDataObserver);
        }
    };

    /**
     * 
     */
    public GraphPlotArea() {
        getStyleClass().setAll("chart-plot-background");
        widthProperty().addListener(plotValidateListener);
        heightProperty().addListener(plotValidateListener);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        verticalMinorGridLines.setVisible(false);
        horizontalMinorGridLines.setVisible(false);
        verticalRowFill.getStyleClass().setAll("chart-alternative-column-fill");
        horizontalRowFill.getStyleClass().setAll("chart-alternative-row-fill");
        verticalGridLines.getStyleClass().setAll("chart-vertical-grid-lines");
        horizontalGridLines.getStyleClass().setAll("chart-horizontal-grid-lines");
        verticalMinorGridLines.getStyleClass().setAll("chart-vertical-grid-lines", "chart-vertical-minor-grid-lines");
        horizontalMinorGridLines.getStyleClass().setAll("chart-horizontal-grid-lines", "chart-horizontal-minor-grid-lines");
        getChildren()
                .addAll(verticalRowFill, horizontalRowFill, verticalMinorGridLines, horizontalMinorGridLines, verticalGridLines, horizontalGridLines, background, userBackround, lines, candles, foreground, userForeground);
    }

    /**
     * ユーザが任意に使える背景領域
     * 
     * @return
     */
    public final ObservableList<Node> getBackgroundChildren() {
        return userBackround.getChildren();
    }

    /**
     * ユーザが任意に使える前景領域
     * 
     * @return
     */
    public final ObservableList<Node> getForegroundChildren() {
        return userForeground.getChildren();
    }

    @Override
    protected void layoutChildren() {
        if (isAutoPlot() && !isPlotValidate()) {
            plotData();
        }
        if (!isGraphShapeValidate()) {
            drawGraphShapes();
        }
    }

    public void plotData() {
        final Axis xaxis = getXAxis();
        final Axis yaxis = getYAxis();

        if (xaxis == null || yaxis == null) {
            setPlotValidate(true);
            setGraphShapeValidate(true);
            return;
        }
        final double w = getWidth(), h = getHeight();
        drawGraphShapes();
        if (!isPlotValidate()) {
            drawBackGroundLine();
            plotLineChartDatas(w, h);
            setPlotValidate(true);
        }
    }

    public void drawGraphShapes() {
        if (isPlotValidate() && isGraphShapeValidate()) {
            return;
        }
        final Axis xaxis = getXAxis();
        final Axis yaxis = getYAxis();

        if (xaxis == null || yaxis == null) {
            setGraphShapeValidate(true);
            return;
        }

        final double w = getWidth(), h = getHeight();
        if (w != xaxis.getWidth() || h != yaxis.getHeight()) {
            return;
        }
        List<GraphShape> lines = backGroundShapes;
        if (lines != null) {

            for (final GraphShape gl : lines) {
                gl.setNodeProperty(xaxis, yaxis, w, h);
            }
        }
        lines = foreGroundShapes;
        if (lines != null) {
            for (final GraphShape gl : lines) {
                gl.setNodeProperty(xaxis, yaxis, w, h);
            }
        }
        setGraphShapeValidate(true);
    }

    public void drawBackGroundLine() {
        final Axis xaxis = getXAxis();
        final Axis yaxis = getYAxis();

        if (xaxis == null || yaxis == null) {
            setPlotValidate(true);
            return;
        }

        final double w = getWidth(), h = getHeight();
        // 背景の線を描画

        V: {
            final Axis axis = xaxis;
            final List<Double> vTicks = axis.getMajorTicks();
            final List<Boolean> vFill = axis.getMajorTicksFill();
            final ObservableList<PathElement> lele = verticalGridLines.getElements();
            final ObservableList<PathElement> fele = verticalRowFill.getElements();
            int lelesize = lele.size();
            final int felesize = fele.size();
            final boolean fill = isAlternativeColumnFillVisible();
            final boolean line = isVerticalGridLinesVisible();
            verticalGridLines.setVisible(line);
            verticalRowFill.setVisible(fill);
            final int e = vTicks.size();
            if (!line) {
                lele.clear();
            } else if (lelesize > e * 2) {
                lele.remove(e * 2, lelesize);
                lelesize = e * 2;
            }
            if (!fill) {
                fele.clear();
            }
            int findex = 0;

            if (!line && !fill) {
                break V;
            }
            for (int i = 0; i < e; i++) {
                final double d = vTicks.get(i);
                if (line) {
                    MoveTo mt;
                    LineTo lt;
                    if (i * 2 < lelesize) {
                        mt = (MoveTo) lele.get(i * 2);
                        lt = (LineTo) lele.get(i * 2 + 1);
                    } else {
                        mt = new MoveTo();
                        lt = new LineTo();
                        lele.addAll(mt, lt);
                    }
                    mt.setX(d);
                    mt.setY(0);
                    lt.setX(d);
                    lt.setY(h);
                }
                if (fill) {
                    final boolean f = vFill.get(i);
                    MoveTo m;
                    LineTo l1, l2, l3;

                    if (f || i == 0) {
                        if (findex < felesize) {
                            m = (MoveTo) fele.get(findex);
                            l1 = (LineTo) fele.get(findex + 1);
                            l2 = (LineTo) fele.get(findex + 2);
                            l3 = (LineTo) fele.get(findex + 3);
                            findex += 5;
                        } else {
                            m = new MoveTo();
                            l1 = new LineTo();
                            l2 = new LineTo();
                            l3 = new LineTo();
                            fele.addAll(m, l1, l2, l3, new ClosePath());
                        }
                    } else {
                        continue;
                    }
                    double x0, x1;
                    if (!f) {
                        x0 = 0;
                        x1 = d;
                    } else if (i == e - 1) {
                        x0 = d;
                        x1 = w;
                    } else {
                        x0 = d;
                        x1 = vTicks.get(i + 1);
                    }
                    m.setX(x0);
                    m.setY(0);
                    l1.setX(x0);
                    l1.setY(h);
                    l2.setX(x1);
                    l2.setY(h);
                    l3.setX(x1);
                    l3.setY(0);
                } // end fill
            } // end for
            if (findex < felesize) {
                fele.remove(findex, felesize);
            }
        } // end V

        H: {
            final Axis axis = yaxis;
            final List<Double> hTicks = axis.getMajorTicks();
            final List<Boolean> hFill = axis.getMajorTicksFill();
            final ObservableList<PathElement> lele = horizontalGridLines.getElements();
            final ObservableList<PathElement> fele = horizontalRowFill.getElements();
            int lelesize = lele.size();
            final int felesize = fele.size();
            final boolean fill = isAlternativeRowFillVisible();
            final boolean line = isHorizontalGridLinesVisible();
            horizontalGridLines.setVisible(line);
            horizontalRowFill.setVisible(fill);
            final int e = hTicks.size();
            if (!line) {
                lele.clear();
            } else if (lelesize > e * 2) {
                lele.remove(e * 2, lelesize);
                lelesize = e * 2;
            }
            if (!fill) {
                fele.clear();
            }
            int findex = 0;
            if (!line && !fill) {
                break H;
            }
            for (int i = 0; i < e; i++) {
                final double d = hTicks.get(i);
                if (line) {
                    MoveTo mt;
                    LineTo lt;
                    if (i * 2 < lelesize) {
                        mt = (MoveTo) lele.get(i * 2);
                        lt = (LineTo) lele.get(i * 2 + 1);
                    } else {
                        mt = new MoveTo();
                        lt = new LineTo();
                        lele.addAll(mt, lt);
                    }
                    mt.setX(0);
                    mt.setY(d);
                    lt.setX(w);
                    lt.setY(d);
                }
                if (fill) {
                    final boolean f = hFill.get(i);
                    MoveTo m;
                    LineTo l1, l2, l3;
                    if (f || i == 0) {
                        if (findex < felesize) {
                            m = (MoveTo) fele.get(findex);
                            l1 = (LineTo) fele.get(findex + 1);
                            l2 = (LineTo) fele.get(findex + 2);
                            l3 = (LineTo) fele.get(findex + 3);
                            findex += 5;
                        } else {
                            m = new MoveTo();
                            l1 = new LineTo();
                            l2 = new LineTo();
                            l3 = new LineTo();
                            fele.addAll(m, l1, l2, l3, new ClosePath());
                        }
                    } else {
                        continue;
                    }
                    double y0, y1;
                    if (!f) {
                        y0 = h;
                        y1 = d;
                    } else if (i == e - 1) {
                        y0 = d;
                        y1 = 0;
                    } else {
                        y0 = d;
                        y1 = hTicks.get(i + 1);
                    }
                    m.setX(0);
                    m.setY(y0);
                    l1.setX(w);
                    l1.setY(y0);
                    l2.setX(w);
                    l2.setY(y1);
                    l3.setX(0);
                    l3.setY(y1);
                } // end fill
            } // end for
            if (findex < felesize) {
                fele.remove(findex, felesize);
            }
        } // end H

        if (isVerticalMinorGridLinesVisible()) {
            final Axis axis = xaxis;
            final List<Double> minorTicks = axis.getMinorTicks();

            final ObservableList<PathElement> ele = verticalMinorGridLines.getElements();
            final int elesize = ele.size();
            final int e = minorTicks.size();
            if (elesize > e * 2) {
                ele.remove(e * 2, elesize);
            }
            for (int i = 0; i < e; i++) {
                final double d = minorTicks.get(i);
                MoveTo mt;
                LineTo lt;
                if (i * 2 < elesize) {
                    mt = (MoveTo) ele.get(i * 2);
                    lt = (LineTo) ele.get(i * 2 + 1);
                } else {
                    mt = new MoveTo();
                    lt = new LineTo();
                    ele.addAll(mt, lt);
                }
                mt.setX(d);
                mt.setY(0);
                lt.setX(d);
                lt.setY(h);
            }
        }

        if (isHorizontalMinorGridLinesVisible()) {
            final Axis axis = yaxis;
            final List<Double> minorTicks = axis.getMinorTicks();

            final ObservableList<PathElement> ele = horizontalMinorGridLines.getElements();
            final int elesize = ele.size();
            final int e = minorTicks.size();
            if (elesize > e * 2) {
                ele.remove(e * 2, elesize);
            }
            for (int i = 0; i < e; i++) {
                final double d = minorTicks.get(i);
                MoveTo mt;
                LineTo lt;
                if (i * 2 < elesize) {
                    mt = (MoveTo) ele.get(i * 2);
                    lt = (LineTo) ele.get(i * 2 + 1);
                } else {
                    mt = new MoveTo();
                    lt = new LineTo();
                    ele.addAll(mt, lt);
                }
                mt.setX(0);
                mt.setY(d);
                lt.setX(w);
                lt.setY(d);
            }
        }
    }

    /**
     * Draw line chart.
     * 
     * @param width
     * @param height
     */
    protected void plotLineChartDatas(double width, double height) {
        ObservableList<Node> paths = lines.getChildren();
        List<LineChartData> datas = lineChartData;

        if (datas == null) {
            paths.clear();
        } else {
            int sizeData = datas.size();
            int sizePath = paths.size();

            if (sizeData < sizePath) {
                paths.remove(sizeData, sizePath);
                sizePath = sizeData;
            }

            for (int i = 0; i < sizeData; i++) {
                int defaultColorIndex = 2;
                LineChartData data = datas.get(i);

                Path path;

                if (i < sizePath) {
                    path = (Path) paths.get(i);
                } else {
                    path = new Path();
                    path.setStrokeLineJoin(StrokeLineJoin.BEVEL);
                    path.getStyleClass().setAll("chart-series-line", "series" + i, data.defaultColor);
                    paths.add(path);
                }

                ObservableList<String> className = path.getStyleClass();

                if (!className.get(defaultColorIndex).equals(data.defaultColor)) {
                    className.set(defaultColorIndex, data.defaultColor);
                }
                plotLineChartData(data, path, width, height);
            }
        }
    }

    private final double DISTANCE_THRESHOLD = 0.5;

    private PlotLine plotline = new PlotLine();

    /**
     * Draw chart data.
     * 
     * @param data
     * @param path
     * @param width
     * @param height
     */
    protected void plotLineChartData(LineChartData data, Path path, double width, double height) {
        if (data.size() == 0) {
            path.setVisible(false);
            return;
        } else {
            path.setVisible(true);
        }

        if (data.size() < 2000) {
            plotline.clearMemory();
        }

        Orientation orientation = getOrientation();
        int start, end;
        if (orientation == Orientation.HORIZONTAL) {// x軸方向昇順
            Axis axis = getXAxis();
            double low = axis.getLowerValue();
            double up = axis.getUpperValue();
            start = data.searchXIndex(low, false);
            end = data.searchXIndex(up, true);

        } else {
            Axis axis = getYAxis();
            double low = axis.getLowerValue();
            double up = axis.getUpperValue();
            start = data.searchYIndex(low, false);
            end = data.searchYIndex(up, true);
        }
        start = Math.max(0, start - 2);

        plotLineChartData(data, path, width, height, start, end);
    }

    /**
     * Draw chart data.
     * 
     * @param data
     * @param path
     * @param width
     * @param height
     * @param start
     * @param end
     */
    private void plotLineChartData(LineChartData data, Path path, double width, double height, int start, int end) {
        ObservableList<PathElement> elements = path.getElements();
        int elementSize = elements.size();
        Axis xaxis = getXAxis();
        Axis yaxis = getYAxis();
        Orientation orientation = getOrientation();

        if (orientation == Orientation.HORIZONTAL) {// x軸方向昇順
            boolean moveTo = true;
            double beforeX = 0, beforeY = 0;
            int elementIndex = 0;
            for (int i = start; i <= end; i++) {
                double x = data.getX(i);
                double y = data.getY(i);

                // 座標変換
                x = xaxis.getDisplayPosition(x);
                y = yaxis.getDisplayPosition(y);

                if (moveTo) {// 線が途切れている場合
                    if (elementIndex < elementSize) {
                        PathElement pathElement = elements.get(elementIndex);
                        if (pathElement.getClass() == MoveTo.class) {// 再利用
                            MoveTo m = ((MoveTo) pathElement);
                            m.setX(x);
                            m.setY(y);
                        } else {
                            MoveTo m = new MoveTo(x, y);
                            elements.set(elementIndex, m);// 置換
                        }
                        elementIndex++;
                    } else {
                        MoveTo m = new MoveTo(x, y);
                        elements.add(m);
                    }
                    moveTo = false;
                    beforeX = x;
                    beforeY = y;
                } else {// 線が続いている場合
                    double l = Math.hypot(x - beforeX, y - beforeY);
                    // 距離が小さすぎる場合は無視
                    if (l < DISTANCE_THRESHOLD) {
                        continue;
                    }
                    if (elementIndex < elementSize) {
                        final PathElement pathElement = elements.get(elementIndex);
                        if (pathElement.getClass() == LineTo.class) {
                            LineTo m = ((LineTo) pathElement);
                            m.setX(x);
                            m.setY(y);
                        } else {
                            LineTo m = new LineTo(x, y);
                            elements.set(elementIndex, m);
                        }
                        elementIndex++;
                    } else {
                        LineTo m = new LineTo(x, y);
                        elements.add(m);
                    }
                    beforeX = x;
                    beforeY = y;
                }
            } // end for

            if (elementIndex < elementSize) {
                elements.remove(elementIndex, elementSize);
            }
        } else {
            boolean moveTo = true;
            double beforeX = 0, beforeY = 0;
            int elei = 0;
            for (int i = start; i <= end; i++) {
                double x = data.getX(i);
                double y = data.getY(i);

                // 座標変換
                x = xaxis.getDisplayPosition(x);
                y = yaxis.getDisplayPosition(y);

                if (moveTo) {// 線が途切れている場合
                    if (elei < elementSize) {
                        PathElement pathElement = elements.get(elei);
                        if (pathElement.getClass() == MoveTo.class) {// 再利用
                            MoveTo m = ((MoveTo) pathElement);
                            m.setX(x);
                            m.setY(y);
                        } else {
                            MoveTo m = new MoveTo(x, y);
                            elements.set(elei, m);// 置換
                        }
                        elei++;
                    } else {
                        MoveTo m = new MoveTo(x, y);
                        elements.add(m);
                    }
                    moveTo = false;
                    beforeY = y;
                    beforeX = x;
                } else {// 線が続いている場合
                    double l = Math.hypot(x - beforeX, y - beforeY);
                    // 距離が小さすぎる場合は無視
                    if (l < DISTANCE_THRESHOLD) {
                        continue;
                    }
                    if (elei < elementSize) {
                        PathElement pathElement = elements.get(elei);
                        if (pathElement.getClass() == LineTo.class) {
                            LineTo m = ((LineTo) pathElement);
                            m.setX(x);
                            m.setY(y);
                        } else {
                            LineTo m = new LineTo(x, y);
                            elements.set(elei, m);
                        }
                        elei++;
                    } else {
                        LineTo m = new LineTo(x, y);
                        elements.add(m);
                    }
                    beforeY = y;
                    beforeX = x;
                }
            } // end for

            if (elei < elementSize) {
                elements.remove(elei, elementSize);
            }
        }

    }

    // ----------------------------------------------------------------

    /**
     * 横方向のグリッド線を表示するかどうかのプロパティ
     * 
     * @return
     */
    public final BooleanProperty horizontalGridLinesVisibleProperty() {
        if (horizontalGridLinesVisibleProperty == null) {
            horizontalGridLinesVisibleProperty = new SimpleBooleanProperty(this, "horizontalGridLinesVisible", true);
        }
        return horizontalGridLinesVisibleProperty;
    }

    public final boolean isHorizontalGridLinesVisible() {
        return horizontalGridLinesVisibleProperty == null ? true : horizontalGridLinesVisibleProperty.get();
    }

    public final void setHorizontalGridLinesVisible(final boolean value) {
        horizontalGridLinesVisibleProperty().set(value);
    }

    private BooleanProperty horizontalGridLinesVisibleProperty;

    /**
     * 縦方向のグリッド線を表示するかどうかのプロパティ
     * 
     * @return
     */
    public final BooleanProperty verticalGridLinesVisibleProperty() {
        if (verticalGridLinesVisibleProperty == null) {
            verticalGridLinesVisibleProperty = new SimpleBooleanProperty(this, "verticalGridLinesVisible", true);
        }
        return verticalGridLinesVisibleProperty;
    }

    public final boolean isVerticalGridLinesVisible() {
        return verticalGridLinesVisibleProperty == null ? true : verticalGridLinesVisibleProperty.get();
    }

    public final void setVerticalGridLinesVisible(final boolean value) {
        verticalGridLinesVisibleProperty().set(value);
    }

    private BooleanProperty verticalGridLinesVisibleProperty;

    /**
     * 横方向minor tickの線の可視性
     * 
     * @return
     */
    public final BooleanProperty horizontalMinorGridLinesVisibleProperty() {
        if (horizontalMinorGridLinesVisibleProperty == null) {
            horizontalMinorGridLinesVisibleProperty = new SimpleBooleanProperty(this, "horizontalMinorGridLinesVisible", false);
            horizontalMinorGridLines.visibleProperty().bind(horizontalMinorGridLinesVisibleProperty);
        }
        return horizontalMinorGridLinesVisibleProperty;
    }

    public final boolean isHorizontalMinorGridLinesVisible() {
        return horizontalMinorGridLinesVisibleProperty == null ? false : horizontalMinorGridLinesVisibleProperty.get();
    }

    public final void setHorizontalMinorGridLinesVisible(final boolean value) {
        horizontalMinorGridLinesVisibleProperty().set(value);
    }

    private BooleanProperty horizontalMinorGridLinesVisibleProperty;

    /**
     * 縦方向minor tickの線の可視性
     * 
     * @return
     */
    public final BooleanProperty verticalMinorGridLinesVisibleProperty() {
        if (verticalMinorGridLinesVisibleProperty == null) {
            verticalMinorGridLinesVisibleProperty = new SimpleBooleanProperty(this, "verticalMinorGridLinesVisible", false);
            verticalMinorGridLines.visibleProperty().bind(verticalMinorGridLinesVisibleProperty);
        }
        return verticalMinorGridLinesVisibleProperty;
    }

    public final boolean isVerticalMinorGridLinesVisible() {
        return verticalMinorGridLinesVisibleProperty == null ? false : verticalMinorGridLinesVisibleProperty.get();
    }

    public final void setVerticalMinorGridLinesVisible(final boolean value) {
        verticalMinorGridLinesVisibleProperty().set(value);
    }

    private BooleanProperty verticalMinorGridLinesVisibleProperty;

    /**
     * 縦方向に交互に背景を塗りつぶすかどうか
     * 
     * @return
     */
    public final BooleanProperty alternativeColumnFillVisibleProperty() {
        if (alternativeColumnFillVisibleProperty == null) {
            alternativeColumnFillVisibleProperty = new SimpleBooleanProperty(this, "alternativeColumnFillVisible", true);
        }
        return alternativeColumnFillVisibleProperty;
    }

    public final boolean isAlternativeColumnFillVisible() {
        return alternativeColumnFillVisibleProperty == null ? true : alternativeColumnFillVisibleProperty.get();
    }

    public final void setAlternativeColumnFillVisible(final boolean value) {
        alternativeColumnFillVisibleProperty().set(value);
    }

    private BooleanProperty alternativeColumnFillVisibleProperty;

    /**
     * 横方向に交互に背景を塗りつぶす
     * 
     * @return
     */
    public final BooleanProperty alternativeRowFillVisibleProperty() {
        if (alternativeRowFillVisibleProperty == null) {
            alternativeRowFillVisibleProperty = new SimpleBooleanProperty(this, "alternativeRowFillVisible", true);
        }
        return alternativeRowFillVisibleProperty;
    }

    public final boolean isAlternativeRowFillVisible() {
        return alternativeRowFillVisibleProperty == null ? true : alternativeRowFillVisibleProperty.get();
    }

    public final void setAlternativeRowFillVisible(final boolean value) {
        alternativeRowFillVisibleProperty().set(value);
    }

    private BooleanProperty alternativeRowFillVisibleProperty;

    private ChangeListener<Axis> axisListener = (observable, oldValue, newValue) -> {
        if (oldValue != null) {
            oldValue.lowerValueProperty().removeListener(plotValidateListener);
            oldValue.visibleAmountProperty().removeListener(plotValidateListener);
        }

        if (newValue != null) {
            newValue.lowerValueProperty().addListener(plotValidateListener);
            newValue.visibleAmountProperty().addListener(plotValidateListener);
        }
        if (isPlotValidate()) {
            setPlotValidate(false);
            requestLayout();
        }
    };

    /**
     * x-axis
     * 
     * @return
     */
    public final ObjectProperty<Axis> xAxisProperty() {
        if (xAxisProperty == null) {
            xAxisProperty = new SimpleObjectProperty<>(this, "xAxis", null);
            xAxisProperty.addListener(axisListener);
        }
        return xAxisProperty;
    }

    public final Axis getXAxis() {
        return xAxisProperty == null ? null : xAxisProperty.get();
    }

    public final void setXAxis(final Axis value) {
        xAxisProperty().set(value);
    }

    private ObjectProperty<Axis> xAxisProperty;

    /**
     * y-axis
     * 
     * @return
     */
    public final ObjectProperty<Axis> yAxisProperty() {
        if (yAxisProperty == null) {
            yAxisProperty = new SimpleObjectProperty<>(this, "yAxis", null);
            yAxisProperty.addListener(axisListener);
        }
        return yAxisProperty;
    }

    public Axis getYAxis() {
        return yAxisProperty == null ? null : yAxisProperty.get();
    }

    public final void setYAxis(final Axis value) {
        yAxisProperty().set(value);
    }

    private ObjectProperty<Axis> yAxisProperty;

    private boolean graphshapeValidate = true;

    protected final void setGraphShapeValidate(final boolean b) {
        graphshapeValidate = b;
    }

    public final boolean isGraphShapeValidate() {
        return graphshapeValidate;
    }

    private InvalidationListener getGraphShapeValidateListener() {
        if (graphShapeValidateListener == null) {
            graphShapeValidateListener = o -> {
                final ReadOnlyBooleanProperty p = (ReadOnlyBooleanProperty) o;
                final boolean b = p.get();
                if (!b && isGraphShapeValidate()) {
                    setGraphShapeValidate(false);
                    setNeedsLayout(true);

                }
            };
        }
        return graphShapeValidateListener;
    }

    private InvalidationListener graphShapeValidateListener;

    private ObservableList<GraphShape> backGroundShapes, foreGroundShapes;

    public final ObservableList<GraphShape> getBackGroundShapes() {
        if (backGroundShapes == null) {
            backGroundShapes = FXCollections.observableArrayList();
            final ListChangeListener<GraphShape> l = (c) -> {
                c.next();
                final Group g = background;
                final ObservableList<Node> ch = g.getChildren();
                final InvalidationListener listener = getGraphShapeValidateListener();
                for (final GraphShape gl : c.getRemoved()) {
                    final Node node = gl.getNode();
                    if (node != null) {
                        ch.remove(node);
                    }
                    gl.validateProperty().removeListener(listener);
                }
                for (final GraphShape gl : c.getAddedSubList()) {
                    final Node node = gl.getNode();
                    if (node != null) {
                        ch.add(gl.getNode());
                    }
                    gl.validateProperty().addListener(listener);
                }
                if (isPlotValidate()) {
                    setGraphShapeValidate(false);
                    setPlotValidate(false);
                    setNeedsLayout(true);
                }
            };
            backGroundShapes.addListener(l);
        }
        return backGroundShapes;
    }

    public final ObservableList<GraphShape> getForeGroundShapes() {
        if (foreGroundShapes == null) {
            foreGroundShapes = FXCollections.observableArrayList();
            final ListChangeListener<GraphShape> l = c -> {
                c.next();
                final Group g = foreground;
                final ObservableList<Node> ch = g.getChildren();
                final InvalidationListener listener = getGraphShapeValidateListener();
                for (final GraphShape gl : c.getRemoved()) {
                    final Node node = gl.getNode();
                    if (node != null) {
                        ch.remove(node);
                    }
                    gl.validateProperty().removeListener(listener);
                }
                for (final GraphShape gl : c.getAddedSubList()) {
                    final Node node = gl.getNode();
                    if (node != null) {
                        ch.add(gl.getNode());
                    }
                    gl.validateProperty().addListener(listener);
                }
                if (isPlotValidate()) {
                    setGraphShapeValidate(false);
                    requestLayout();
                }
            };
            foreGroundShapes.addListener(l);
        }
        return foreGroundShapes;
    }

    /**
     * Set data list for line chart.
     * 
     * @param datalist
     */
    public final void setLineChartDataList(ObservableList<LineChartData> datalist) {
        // clear old list configuration
        if (lineChartData != null) {
            lineChartData.removeListener(lineDataListObserver);
            for (LineChartData data : lineChartData) {
                data.validateProperty().removeListener(lineDataObserver);
            }
            lineColorManager.clear();
        }

        // add new list configuration
        if (datalist != null) {
            datalist.addListener(lineDataListObserver);
            for (LineChartData data : datalist) {
                data.defaultColorIndex = lineColorManager.nextClearBit(0);
                lineColorManager.set(data.defaultColorIndex, true);
                data.defaultColor = "default-color" + (data.defaultColorIndex % 8);
                data.validateProperty().addListener(lineDataObserver);
            }
        }

        // update
        lineChartData = datalist;

        if (isPlotValidate()) {
            setPlotValidate(false);
            setNeedsLayout(true);
        }
    }

    /**
     * Set data list for line chart.
     * 
     * @param datalist
     */
    public final void setCandleChartDataList(ObservableList<Tick> datalist) {
        // clear old list configuration
        if (candleChartData != null) {
            // lineChartData.removeListener(lineDataListObserver);
            // for (LineChartData data : lineChartData) {
            // data.validateProperty().removeListener(lineDataObserver);
            // }
            // lineColorManager.clear();
        }

        // add new list configuration
        if (datalist != null) {
            // datalist.addListener(lineDataListObserver);
            // for (LineChartData data : datalist) {
            // data.defaultColorIndex = lineColorManager.nextClearBit(0);
            // lineColorManager.set(data.defaultColorIndex, true);
            // data.defaultColor = "default-color" + (data.defaultColorIndex % 8);
            // data.validateProperty().addListener(lineDataObserver);
            // }
        }

        // update
        candleChartData = datalist;

        if (isPlotValidate()) {
            setPlotValidate(false);
            setNeedsLayout(true);
        }
    }

    /**
     * layoutChildrenを実行時に自動的にグラフエリアの描画も行うかどうか。 falseにした場合は必要なときに自分でplotDataを呼び出す必要がある。 デフォルトはtrue
     * 
     * @return
     */
    public final BooleanProperty autoPlotProperty() {
        if (autoPlotProperty == null) {
            autoPlotProperty = new SimpleBooleanProperty(this, "autoPlot", true);
        }
        return autoPlotProperty;
    }

    public final boolean isAutoPlot() {
        return autoPlotProperty == null ? true : autoPlotProperty.get();
    }

    public final void setAutoPlot(final boolean value) {
        autoPlotProperty().set(value);
    }

    private BooleanProperty autoPlotProperty;

    protected final boolean isPlotValidate() {
        return plotValidate;
    }

    protected final void setPlotValidate(final boolean bool) {
        plotValidate = bool;
    }

    /** 状態の正当性を示すプロパティ */
    private boolean plotValidate = false;

    /**
     * x軸方向に連続なデータか、y軸方向に連続なデータかを指定するプロパティ
     * 
     * @return
     */
    public final ObjectProperty<Orientation> orientationProperty() {
        if (orientationProperty == null) {
            orientationProperty = new SimpleObjectProperty<>(this, "orientation", Orientation.HORIZONTAL);
        }
        return orientationProperty;
    }

    public final Orientation getOrientation() {
        return orientationProperty == null ? Orientation.HORIZONTAL : orientationProperty.get();
    }

    public final void setOrientation(final Orientation value) {
        orientationProperty().set(value);
    }

    private ObjectProperty<Orientation> orientationProperty;

    private GraphLine verticalZeroLine, horizontalZeroLine;

    public final GraphLine getVerticalZeroLine() {
        if (verticalZeroLine == null) {
            verticalZeroLine = new GraphLine();
            verticalZeroLine.setOrientation(Orientation.VERTICAL);
            verticalZeroLine.getStyleClass().setAll("chart-vertical-zero-line");
        }
        return verticalZeroLine;
    }

    public final GraphLine getHorizontalZeroLine() {
        if (horizontalZeroLine == null) {
            horizontalZeroLine = new GraphLine();
            horizontalZeroLine.setOrientation(Orientation.HORIZONTAL);
            horizontalZeroLine.getStyleClass().setAll("chart-horizontal-zero-line");
        }
        return horizontalZeroLine;
    }

    public void showVerticalZeroLine() {
        final ObservableList<GraphShape> backGroundLine = getBackGroundShapes();
        final GraphLine l = getVerticalZeroLine();
        l.setVisible(true);
        if (!backGroundLine.contains(l)) {
            backGroundLine.add(l);
        }
    }

    public void showHorizontalZeroLine() {
        final ObservableList<GraphShape> backGroundLine = getBackGroundShapes();
        final GraphLine l = getHorizontalZeroLine();
        l.setVisible(true);
        if (!backGroundLine.contains(l)) {
            backGroundLine.add(l);
        }
    }

    /**
     * Get data for line chart.
     * 
     * @return
     */
    public final ObservableList<LineChartData> getLineDataList() {
        return lineChartData;
    }

    /**
     * Get data for candle chart.
     * 
     * @return
     */
    public final ObservableList<Tick> getCandleDataList() {
        return candleChartData;
    }

    /**
     * @version 2017/09/26 1:20:10
     */
    private final class LocalGroup extends Group {

        /**
         * 
         */
        private LocalGroup() {
            setAutoSizeChildren(false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void requestLayout() {
        }
    }

}
