package com.chai.hui.client.windowing.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;

/** Small declarative UI helpers: {@code Ui.column().gap(4).child(Ui.label(...)).child(Ui.button(...))}. */
public final class Ui {
    private Ui() {}

    public static PanelWidget column() {
        return new PanelWidget(PanelWidget.Direction.COLUMN);
    }

    public static PanelWidget row() {
        return new PanelWidget(PanelWidget.Direction.ROW);
    }

    public static LabelWidget label(Component text) {
        return new LabelWidget(() -> text, 0xFFE6EDF3);
    }

    public static LabelWidget label(Supplier<Component> text) {
        return new LabelWidget(text, 0xFFE6EDF3);
    }

    public static LabelWidget mutedLabel(Supplier<Component> text) {
        return new LabelWidget(text, 0xFF8B98A9);
    }

    public static ButtonWidget button(Component label, Runnable action) {
        return new ButtonWidget(label, action);
    }

    public static TextInputWidget textInput(String initial, int maxLength, Consumer<String> responder,
            Runnable onSubmit) {
        return new TextInputWidget(initial, maxLength, 180, responder, onSubmit);
    }
}
