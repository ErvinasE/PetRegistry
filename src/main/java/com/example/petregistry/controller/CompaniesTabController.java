package com.example.petregistry.controller;

import com.example.petregistry.model.Company;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.util.ComboBoxes;
import com.example.petregistry.util.Dialogs;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;

/** Controller for companies-tab.fxml: filter bar, table and the Nauja / Redaguoti / Šalinti buttons. */
public class CompaniesTabController {

    private final CompanyService companyService = new CompanyService();

    @FXML private TextField textFilter;
    @FXML private TextField cityFilter;
    @FXML private ComboBox<String> activeFilter;
    @FXML private TableView<Company> table;
    @FXML private TableColumn<Company, String> activeColumn;
    @FXML private TableColumn<Company, String> servicesColumn;
    @FXML private TableColumn<Company, String> caretakersColumn;

    @FXML
    private void initialize() {
        ComboBoxes.fillYesNo(activeFilter);
        activeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isActive() ? "Taip" : "Ne"));
        servicesColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getServices().size())));
        caretakersColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String.valueOf(cell.getValue().getCaretakers().size())));

        // Double-click on a filled row = Redaguoti.
        table.setRowFactory(tableView -> {
            TableRow<Company> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        refresh();
    }

    /** Reloads the table (keeping the filter). MainController also calls it when this tab is opened. */
    public void refresh() {
        table.getItems().setAll(companyService.search(textFilter.getText(), cityFilter.getText(),
                ComboBoxes.yesNoValue(activeFilter)));
    }

    @FXML
    private void onSearch() {
        refresh();
    }

    @FXML
    private void onShowAll() {
        textFilter.clear();
        cityFilter.clear();
        activeFilter.setValue(null);
        refresh();
    }

    @FXML
    private void onNew() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        Company company = selectedOrWarn();
        if (company != null) {
            openForm(company);
        }
    }

    /** Asks first, and says what else happens. A refused delete shows the rule's message. */
    @FXML
    private void onDelete() {
        Company company = selectedOrWarn();
        if (company == null) {
            return;
        }
        String question = "Ar tikrai pašalinti įmonę „" + company.getName() + "“?"
                + "\nKartu bus pašalintos jos paslaugos, o prižiūrėtojai taps nepriklausomi.";
        if (!Dialogs.confirm(window(), "Šalinti įmonę", question)) {
            return;
        }
        try {
            companyService.delete(company);
            refresh();
        } catch (BusinessRuleException e) {
            Dialogs.error(window(), "Pašalinti negalima", e.getMessage());
        }
    }

    /** Always reloads after the window closes: services and caretakers are saved inside it. */
    private void openForm(Company company) {
        Company result = CompanyFormController.open(window(), company);
        refresh();
        if (result != null) {
            table.getSelectionModel().select(result);
        }
    }

    private Company selectedOrWarn() {
        Company company = table.getSelectionModel().getSelectedItem();
        if (company == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite įmonę lentelėje.");
        }
        return company;
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
