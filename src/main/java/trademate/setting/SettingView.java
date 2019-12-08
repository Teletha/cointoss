/*
 * Copyright (C) 2019 CoinToss Development Team
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          https://opensource.org/licenses/MIT
 */
package trademate.setting;

import java.util.List;

import kiss.Managed;
import kiss.Singleton;
import stylist.Style;
import stylist.StyleDSL;
import transcript.Transcript;
import viewtify.ui.UI;
import viewtify.ui.UILabel;
import viewtify.ui.UIPane;
import viewtify.ui.View;
import viewtify.ui.helper.User;

@Managed(value = Singleton.class)
public class SettingView extends View {

    private UILabel general;

    private UILabel appearance;

    private UILabel chart;

    private UILabel notification;

    private UILabel bitflyer;

    private UIPane setting;

    /**
     * UI definition.
     */
    class view extends UI {
        {
            $(hbox, () -> {
                $(vbox, style.categoryPane, () -> {
                    $(general, style.categoryLabel);
                    $(appearance, style.categoryLabel);
                    $(chart, style.categoryLabel);
                    $(notification, style.categoryLabel);
                    $(bitflyer, style.categoryLabel);
                });
                $(setting);
            });
        }
    }

    /**
     * Style definition.
     */
    interface style extends StyleDSL {

        Style categoryPane = () -> {
            padding.top(40, px);
        };

        Style categoryLabel = () -> {
            display.width(200, px).height(20, px);
            padding.vertical(10, px).left(40, px);
            cursor.pointer();
            font.size(16, px);

            $.hover(() -> {
                background.color("derive(-fx-base, 15%)");
            });
        };

        Style Selected = () -> {
            background.color("derive(-fx-base, 6%)");
        };

        Transcript general = Transcript.en("General");

        Transcript appearance = Transcript.en("Appearance");

        Transcript chart = Transcript.en("Chart");

        Transcript notification = Transcript.en("Notification");

        Transcript bitflyer = Transcript.en("Bitflyer");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void initialize() {
        select(notification, NotificationSetting.class);

        general.text(style.general).when(User.MouseClick, () -> select(general, GeneralSetting.class));
        appearance.text(style.appearance).when(User.MouseClick, () -> select(appearance, AppearanceSetting.class));
        chart.text(style.chart).when(User.MouseClick, () -> select(appearance, ChartSetting.class));
        notification.text(style.notification).when(User.MouseClick, () -> select(notification, NotificationSetting.class));
        bitflyer.text(style.bitflyer).when(User.MouseClick, () -> select(bitflyer, BitFlyerSetting.class));
    }

    private void select(UILabel selected, Class<? extends View> view) {
        for (UILabel label : List.of(general, appearance, notification, bitflyer)) {
            if (label == selected) {
                label.style(style.Selected);
            } else {
                label.unstyle(style.Selected);
            }
        }
        setting.set(view);
    }
}
