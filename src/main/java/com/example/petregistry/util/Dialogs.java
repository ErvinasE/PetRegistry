package com.example.petregistry.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/** Small alerts used by every screen. Each one opens on top of its owner window and waits for the user. */
public final class Dialogs {

    // Lithuanian button texts; CANCEL_CLOSE makes the dialog's X mean "Ne".
    public static final ButtonType YES = new ButtonType("Taip", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType NO = new ButtonType("Ne", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogs() {
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

    /** Drop-down choice. Returns the chosen value, or empty if the user cancels. */
    public static <T> Optional<T> choose(Window owner, String title, String text, List<T> choices) {
        ChoiceDialog<T> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(text);
        dialog.getDialogPane().getButtonTypes().setAll(
                new ButtonType("Gerai", ButtonBar.ButtonData.OK_DONE),
                new ButtonType("Atšaukti", ButtonBar.ButtonData.CANCEL_CLOSE));
        return dialog.showAndWait();
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
