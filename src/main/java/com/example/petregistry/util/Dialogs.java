package com.example.petregistry.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

/** Small alerts used by every screen. Each one opens on top of its owner window and waits for the user. */
public final class Dialogs {

    // Lithuanian button texts (the built-in YES/NO would show English "Yes"/"No").
    // CANCEL_CLOSE also makes the dialog's X mean "Ne".
    public static final ButtonType YES = new ButtonType("Taip", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType NO = new ButtonType("Ne", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogs() {
        // only static methods
    }

    public static void info(Window owner, String title, String text) {
        show(Alert.AlertType.INFORMATION, owner, title, text);
    }

    public static void error(Window owner, String title, String text) {
        show(Alert.AlertType.ERROR, owner, title, text);
    }

    /** Asks a yes/no question. Returns true only on "Taip". */
    public static boolean confirm(Window owner, String title, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, YES, NO);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        return alert.showAndWait().filter(answer -> answer == YES).isPresent();
    }

    private static void show(Alert.AlertType type, Window owner, String title, String text) {
        Alert alert = new Alert(type);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(text);
        alert.showAndWait();
    }
}
