/*
 * Copyright (C) 2023 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package trademate.chart.part;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javafx.beans.Observable;
import javafx.beans.property.DoubleProperty;
import kiss.Disposable;
import kiss.Signal;
import trademate.chart.ChartCanvas;
import trademate.setting.PerformanceSetting;
import viewtify.Viewtify;
import viewtify.preference.Preferences;
import viewtify.ui.canvas.EnhancedCanvas;
import viewtify.ui.helper.LayoutAssistant;

public abstract class ChartPart {

    /** The parent chart. */
    public final ChartCanvas parent;

    /** The layout manage. */
    public final LayoutAssistant layout;

    /** The actual canvas. */
    public final EnhancedCanvas canvas;

    /** The all managed canvases. */
    public final List<EnhancedCanvas> managed = new ArrayList();

    /** The disposer on hidden. */
    protected Disposable disposeOnHidden = Disposable.empty();

    /**
     * Set up part of the chart.
     * 
     * @param parent
     */
    protected ChartPart(ChartCanvas parent) {
        this.parent = parent;
        this.layout = new LayoutAssistant(parent);
        this.canvas = createCanvas();
    }

    /**
     * Create new canvas.
     * 
     * @return
     */
    protected final EnhancedCanvas createCanvas() {
        EnhancedCanvas canvas = new EnhancedCanvas().visibleWhen(layout.canLayout);

        // manage the created new canvas automatically
        managed.add(canvas);

        return canvas;
    }

    /**
     * Invoke when the canvas is shown.
     */
    public void onShown() {
        for (EnhancedCanvas canvas : managed) {
            canvas.bindSizeTo(parent);
        }

        disposeOnHidden = Disposable.empty();
    }

    /**
     * Invoke when the canvas is hiddent.
     */
    public void onHidden() {
        // clear all contents and minimize them to reduce memory usage
        for (EnhancedCanvas canvas : managed) {
            canvas.clear().size(0, 0);
        }

        disposeOnHidden.dispose();
    }

    /**
     * Invoke when the mouse moves on the parent canvas.
     */
    public void onMouseMove(double x, double y) {
        // do nothing
    }

    public void onMouseExit() {
        // do nothing
    }

    public abstract void draw();

    protected final Observable[] chartAxisModification() {
        return new DoubleProperty[] {parent.axisX.scroll.valueProperty(), parent.axisX.scroll.visibleAmountProperty()};
    }

    protected final Signal userInterfaceModification() {
        PerformanceSetting performance = Preferences.of(PerformanceSetting.class);

        return Viewtify.observe(parent.widthProperty())
                .merge(Viewtify.observe(parent.heightProperty()))
                .debounce(performance.refreshRate, TimeUnit.MILLISECONDS, false);
    }
}
