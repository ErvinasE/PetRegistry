package com.example.petregistry.controller;

import com.example.petregistry.db.DataAccessException;
import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.ReviewService;
import com.example.petregistry.util.Dialogs;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Window;

import java.util.Optional;

/** Controller for main.fxml: the menu bar, the tabs and the status bar. */
public class MainController {

    // Filled by FXMLLoader: the field name must be fx:id + "Controller".
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
    @FXML private Tab reservationsTabItem;
    @FXML private Label statusLabel;
    @FXML private MenuItem connectItem;
    @FXML private MenuItem disconnectItem;

    @FXML
    private void initialize() {
        // Tabs share data, so a tab reloads every time it is opened.
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == usersTabItem) {
                usersTabController.refresh();
            } else if (newTab == companiesTabItem) {
                companiesTabController.refresh();
            } else if (newTab == servicesTabItem) {
                servicesTabController.refresh();
            } else if (newTab == petsTabItem) {
                petsTabController.refresh();
            } else if (newTab == reservationsTabItem) {
                reservationsTabController.refresh();
            }
        });
    }

    /** Duomenų bazė → Prisijungti…: connect, switch to JDBC, reload every tab. If reading fails, go back to memory. */
    @FXML
    private void onConnect() {
        Optional<String> url = ConnectDialog.show(window());
        if (url.isEmpty()) {
            return;
        }
        try {
            Repositories.useDatabase();
            refreshAllTabs();
        } catch (DataAccessException e) {
            useMemory();
            Dialogs.error(window(), "Duomenų bazės klaida", e.getMessage()
                    + "\nAr įkėlėte docs/schema.sql? Dirbama su duomenimis atmintyje.");
            return;
        }
        statusLabel.setText("Prisijungta prie DB: " + url.get());
        connectItem.setDisable(true);
        disconnectItem.setDisable(false);
    }

    /** Duomenų bazė → Atsijungti: close the connection and go back to fresh in-memory data. */
    @FXML
    private void onDisconnect() {
        useMemory();
    }

    private void useMemory() {
        DatabaseManager.disconnect();
        Repositories.useMemory();
        new ReviewService().refreshAllAverages();
        refreshAllTabs();
        statusLabel.setText("Duomenys atmintyje");
        connectItem.setDisable(false);
        disconnectItem.setDisable(true);
    }

    /** After a storage switch, every tab must reload: their tables still hold objects from the old storage. */
    private void refreshAllTabs() {
        usersTabController.refresh();
        companiesTabController.refresh();
        servicesTabController.refresh();
        petsTabController.refresh();
        reservationsTabController.refresh();
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

    /** Asks "Ar tikrai norite išeiti?" and returns true on "Taip". Public: MainApp uses it too. */
    public boolean confirmExit() {
        return Dialogs.confirm(window(), "Išeiti", "Ar tikrai norite išeiti?");
    }

    /** The main window, owner of the alerts. */
    private Window window() {
        return statusLabel.getScene().getWindow();
    }
}
