package com.example.petregistry.controller;

import com.example.petregistry.model.Company;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.service.ServiceService;
import com.example.petregistry.util.ComboBoxes;
import com.example.petregistry.util.Dialogs;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;

import java.math.BigDecimal;

/** Controller for services-tab.fxml: filter bar, table, and Nauja / Redaguoti / Šalinti / Laikinai neteikiama. */
public class ServicesTabController {

    private final ServiceService serviceService = new ServiceService();
    private final CompanyService companyService = new CompanyService();

    @FXML private ComboBox<ServiceType> typeFilter;
    @FXML private ComboBox<Company> companyFilter;
    @FXML private TextField minPriceFilter;
    @FXML private TextField maxPriceFilter;
    @FXML private TableView<Service> table;
    @FXML private TableColumn<Service, String> companyColumn;
    @FXML private Button pauseButton;

    @FXML
    private void initialize() {
        typeFilter.getItems().setAll(ServiceType.values());
        ComboBoxes.showPromptWhenEmpty(typeFilter);
        ComboBoxes.showPromptWhenEmpty(companyFilter);

        companyColumn.setCellValueFactory(cell -> {
            Company company = cell.getValue().getCompany();
            return new SimpleStringProperty(company == null ? "" : company.getName());
        });

        // Double-click on a filled row = Redaguoti.
        table.setRowFactory(tableView -> {
            TableRow<Service> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        // The pause button always says what it will do for the selected service.
        table.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldService, newService) -> updatePauseButton(newService));

        refresh();
    }

    /** Reloads the company list and the table (keeping the filter). MainController calls it when the tab opens. */
    public void refresh() {
        Company chosen = companyFilter.getValue();
        companyFilter.getItems().setAll(companyService.findAll());
        companyFilter.setValue(companyFilter.getItems().contains(chosen) ? chosen : null);
        loadTable();
    }

    /** Reads the price filters first. A bad number shows an error and does NOT search. */
    private void loadTable() {
        BigDecimal minPrice;
        BigDecimal maxPrice;
        try {
            minPrice = parsePrice(minPriceFilter.getText());
            maxPrice = parsePrice(maxPriceFilter.getText());
        } catch (NumberFormatException e) {
            Dialogs.error(window(), "Neteisingas filtras", "Kaina turi būti skaičius, pvz. 12,50");
            return;
        }
        Company company = companyFilter.getValue();
        Integer companyId = company == null ? null : company.getId();
        table.getItems().setAll(serviceService.search(typeFilter.getValue(), companyId, minPrice, maxPrice));
    }

    @FXML
    private void onSearch() {
        loadTable();
    }

    @FXML
    private void onShowAll() {
        typeFilter.setValue(null);
        companyFilter.setValue(null);
        minPriceFilter.clear();
        maxPriceFilter.clear();
        loadTable();
    }

    @FXML
    private void onNew() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        Service service = selectedOrWarn();
        if (service != null) {
            openForm(service);
        }
    }

    /** Asks first. A service with reservations only becomes INACTIVE, and we tell the user. */
    @FXML
    private void onDelete() {
        Service service = selectedOrWarn();
        if (service == null) {
            return;
        }
        if (!Dialogs.confirm(window(), "Šalinti paslaugą", "Ar tikrai pašalinti paslaugą „" + service.getTitle() + "“?")) {
            return;
        }
        boolean deleted = serviceService.delete(service);
        refresh();
        if (!deleted) {
            Dialogs.info(window(), "Paslauga išjungta",
                    "Paslauga turi rezervacijų, todėl ji nepašalinta, o pažymėta kaip neaktyvi.");
            table.getSelectionModel().select(service);
        }
    }

    /** Laikinai neteikiama / Vėl teikti: ACTIVE ↔ TEMPORARILY_UNAVAILABLE. */
    @FXML
    private void onTogglePaused() {
        Service service = selectedOrWarn();
        if (service == null) {
            return;
        }
        boolean pause = service.getStatus() != ServiceStatus.TEMPORARILY_UNAVAILABLE;
        try {
            serviceService.setTemporarilyUnavailable(service, pause);
        } catch (BusinessRuleException e) {
            Dialogs.error(window(), "Negalima", e.getMessage()); // e.g. the service is INACTIVE
            return;
        }
        refresh();
        table.getSelectionModel().select(service);
    }

    private void openForm(Service service) {
        Service saved = ServiceFormController.open(window(), service);
        if (saved != null) {
            refresh();
            table.getSelectionModel().select(saved);
        }
    }

    private void updatePauseButton(Service service) {
        boolean paused = service != null && service.getStatus() == ServiceStatus.TEMPORARILY_UNAVAILABLE;
        pauseButton.setText(paused ? "Vėl teikti" : "Laikinai neteikiama");
        // An INACTIVE service can't be paused or resumed, so the button is greyed out.
        pauseButton.setDisable(service != null && service.getStatus() == ServiceStatus.INACTIVE);
    }

    /** Empty → null (= no filter). Accepts "12,50" and "12.50". Not a number → NumberFormatException. */
    private static BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.trim().replace(',', '.'));
    }

    private Service selectedOrWarn() {
        Service service = table.getSelectionModel().getSelectedItem();
        if (service == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite paslaugą lentelėje.");
        }
        return service;
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
