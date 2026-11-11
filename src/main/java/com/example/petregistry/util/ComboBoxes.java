package com.example.petregistry.util;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

/** Helpers for ComboBox. */
public final class ComboBoxes {

    private ComboBoxes() {
    }

    /** Fills a yes/no filter box. Read the choice with yesNoValue. */
    public static void fillYesNo(ComboBox<String> box) {
        box.getItems().setAll("Taip", "Ne");
        showPromptWhenEmpty(box);
    }

    /** "Taip" → true, "Ne" → false, nothing chosen → null (= don't filter by this). */
    public static Boolean yesNoValue(ComboBox<String> box) {
        String choice = box.getValue();
        return choice == null ? null : choice.equals("Taip");
    }

    /** After setValue(null) a ComboBox shows nothing; this button cell shows the prompt text instead. */
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
