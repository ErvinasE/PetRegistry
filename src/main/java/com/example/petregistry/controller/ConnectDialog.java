package com.example.petregistry.controller;

import com.example.petregistry.db.DataAccessException;
import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.util.Dialogs;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

/** Duomenų bazė → Prisijungti…: a small Dialog with URL, user and password, built in code. */
final class ConnectDialog {

    // XAMPP defaults: MySQL/MariaDB on this computer, port 3306, user "root" with an empty password.
    private static final String DEFAULT_URL = "jdbc:mysql://localhost:3306/pet_registry";
    private static final String DEFAULT_USER = "root";

    private ConnectDialog() {
    }

    /**
     * Shows the dialog and connects on "Prisijungti". A failed connect keeps it open.
     *
     * @return the URL we connected to, or empty if the user cancelled
     */
    static Optional<String> show(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Prisijungti prie duomenų bazės");
        dialog.setHeaderText(null);

        TextField urlField = new TextField(DEFAULT_URL);
        urlField.setId("urlField");
        urlField.setPrefColumnCount(30);
        TextField userField = new TextField(DEFAULT_USER);
        userField.setId("userField");
        PasswordField passwordField = new PasswordField();
        passwordField.setId("passwordField");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("URL:"), urlField);
        grid.addRow(1, new Label("Vartotojas:"), userField);
        grid.addRow(2, new Label("Slaptažodis:"), passwordField);
        dialog.getDialogPane().setContent(grid);

        ButtonType connect = new ButtonType("Prisijungti", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Atšaukti", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(connect, cancel);

        // Event filter: on a failed connect, consume() keeps the dialog open.
        Node connectButton = dialog.getDialogPane().lookupButton(connect);
        connectButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                DatabaseManager.connect(urlField.getText().trim(), userField.getText().trim(), passwordField.getText());
            } catch (DataAccessException e) {
                Dialogs.error(dialog.getDialogPane().getScene().getWindow(), "Prisijungti nepavyko", e.getMessage());
                event.consume();
            }
        });

        return dialog.showAndWait()
                .filter(button -> button == connect)
                .map(button -> urlField.getText().trim());
    }
}
