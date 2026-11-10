package com.example.petregistry.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Window;

import java.util.Optional;

/** Controller for main.fxml: the menu bar, the tabs and the status bar. */
public class MainController {

    // Lithuanian button texts (the built-in YES/NO would show English "Yes"/"No").
    // CANCEL_CLOSE also lets the X on the dialog mean "Ne".
    private static final ButtonType YES = new ButtonType("Taip", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType NO = new ButtonType("Ne", ButtonBar.ButtonData.CANCEL_CLOSE);

    // FXMLLoader fills these from <fx:include fx:id="usersTab" .../>: the field name must be fx:id + "Controller".
    // Not used yet. M6+ calls refresh() on them, and M11 refreshes all tabs after connecting.
    @FXML private UsersTabController usersTabController;
    @FXML private CompaniesTabController companiesTabController;
    @FXML private ServicesTabController servicesTabController;
    @FXML private PetsTabController petsTabController;
    @FXML private ReservationsTabController reservationsTabController;

    @FXML private Label statusLabel;

    /** Pagalba → Apie */
    @FXML
    private void onAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(window());
        alert.setTitle("Apie");
        alert.setHeaderText("Pet Registry");
        alert.setContentText("Gyvūnų priežiūros paslaugų registras.\n"
                + "VILNIUS TECH, Programavimo technologijos.");
        alert.showAndWait();
    }

    /** Failas → Išeiti: closes the app only on "Taip". */
    @FXML
    private void onExit() {
        if (confirmExit()) {
            Platform.exit();
        }
    }

    /**
     * Asks "Ar tikrai norite išeiti?" and returns true on "Taip".
     * Public, because MainApp uses it too when the user clicks the window's X.
     */
    public boolean confirmExit() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Ar tikrai norite išeiti?", YES, NO);
        alert.initOwner(window());
        alert.setTitle("Išeiti");
        alert.setHeaderText(null);
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == YES;
    }

    /** The main window, so alerts open on top of it (and in its centre). */
    private Window window() {
        return statusLabel.getScene().getWindow();
    }
}
