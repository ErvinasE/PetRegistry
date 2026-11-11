package com.example.petregistry.controller;

import com.example.petregistry.util.Dialogs;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Window;

/** Controller for main.fxml: the menu bar, the tabs and the status bar. */
public class MainController {

    // FXMLLoader fills these from <fx:include fx:id="usersTab" .../>: the field name must be fx:id + "Controller".
    // M11 will also refresh all tabs after connecting to the database.
    @FXML private UsersTabController usersTabController;
    @FXML private CompaniesTabController companiesTabController;
    @FXML private ServicesTabController servicesTabController;
    @FXML private PetsTabController petsTabController;
    @FXML private ReservationsTabController reservationsTabController;

    @FXML private TabPane tabPane;
    @FXML private Tab usersTabItem;
    @FXML private Tab companiesTabItem;
    @FXML private Tab servicesTabItem;
    @FXML private Tab petsTabItem;
    @FXML private Label statusLabel;

    @FXML
    private void initialize() {
        // Tabs share data: deleting an owner in Users also deletes their pets.
        // So a tab reloads its data every time it is opened, and never shows stale rows.
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == usersTabItem) {
                usersTabController.refresh();
            } else if (newTab == companiesTabItem) {
                companiesTabController.refresh();
            } else if (newTab == servicesTabItem) {
                servicesTabController.refresh();
            } else if (newTab == petsTabItem) {
                petsTabController.refresh();
            }
        });
    }

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
        return Dialogs.confirm(window(), "Išeiti", "Ar tikrai norite išeiti?");
    }

    /** The main window, so alerts open on top of it (and in its centre). */
    private Window window() {
        return statusLabel.getScene().getWindow();
    }
}
