package com.example.petregistry.util;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/** Helpers for ComboBox. */
public final class ComboBoxes {

    private ComboBoxes() {
        // only static methods
    }

    /**
     * JavaFX quirk: after setValue(null) a ComboBox shows nothing, instead of its prompt text again.
     * This "button cell" (the part that shows the chosen value) shows the prompt, in grey, whenever the value is empty.
     * Use it on filter boxes that "Rodyti visus" clears.
     */
    public static <T> void showPromptWhenEmpty(ComboBox<T> box) {
        box.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                boolean noValue = empty || item == null;
                setText(noValue ? box.getPromptText() : item.toString());
                setStyle(noValue ? "-fx-text-fill: grey;" : "");
            }
        });
    }
}
