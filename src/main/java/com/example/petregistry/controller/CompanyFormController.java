package com.example.petregistry.controller;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Service;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.service.ServiceService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controller for company-form.fxml: a window with inner tabs (Duomenys, Paslaugos, Prižiūrėtojai). */
public class CompanyFormController {

    private final CompanyService companyService = new CompanyService();
    private final ServiceService serviceService = new ServiceService();
    private final UserService userService = new UserService();

    @FXML private TabPane innerTabs;

    // Duomenys
    @FXML private TextField nameField;
    @FXML private TextField codeField;
    @FXML private TextField cityField;
    @FXML private TextField addressField;
    @FXML private CheckBox activeCheck;

    // Paslaugos
    @FXML private Tab servicesTab;
    @FXML private ListView<Service> servicesList;

    // Prižiūrėtojai
    @FXML private Tab caretakersTab;
    @FXML private ListView<Caretaker> caretakersList;
    @FXML private ComboBox<Caretaker> freeCaretakerBox;

    @FXML private Label hintLabel;

    /** Field name in ValidationException → the control to mark red. */
    private final Map<String, Control> fieldControls = new LinkedHashMap<>();

    private Company company; // the stored company; null while a new one is not saved yet

    /**
     * Opens the window and waits until it is closed.
     *
     * @param company the company to edit, or null for a new one
     * @return the company (saved at least once), or null if a new company was never saved
     */
    public static Company open(Window parent, Company company) {
        FXMLLoader loader = new FXMLLoader(CompanyFormController.class.getResource("/com/example/petregistry/view/company-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load company-form.fxml", e);
        }
        CompanyFormController form = loader.getController();
        form.fill(company);

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(company == null ? "Nauja įmonė" : "Redaguoti įmonę");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.company;
    }

    @FXML
    private void initialize() {
        servicesList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Service service, boolean empty) {
                super.updateItem(service, empty);
                setText(empty || service == null ? null : service.getTitle() + " — " + service.getStatus());
            }
        });

        fieldControls.put("name", nameField);
        fieldControls.put("companyCode", codeField);
        fieldControls.put("city", cityField);
    }

    private void fill(Company existing) {
        company = existing;
        if (existing == null) {
            hintLabel.setText("Paslaugas ir prižiūrėtojus galėsite pridėti, kai įmonė bus išsaugota.");
            return;
        }
        nameField.setText(existing.getName());
        codeField.setText(existing.getCompanyCode());
        cityField.setText(existing.getCity());
        addressField.setText(existing.getAddress());
        activeCheck.setSelected(existing.isActive());
        unlockInnerTabs();
    }

    /** Same draft pattern as the other forms. A new company stays open after saving, so services can be added. */
    @FXML
    private void onSave() {
        clearErrors();
        Company draft = new Company();
        // Same id as the stored company, so "code already taken" doesn't count the company against itself.
        draft.setId(company == null ? 0 : company.getId());
        copyFormInto(draft);
        try {
            companyService.validate(draft);
        } catch (ValidationException e) {
            showErrors(e.getErrors());
            return;
        }

        boolean wasNew = company == null;
        Company target = wasNew ? new Company() : company;
        copyFormInto(target);
        try {
            if (wasNew) {
                companyService.create(target);
            } else {
                companyService.update(target);
            }
        } catch (ValidationException e) {
            showErrors(e.getErrors()); // shouldn't happen: the draft passed the same check
            return;
        }
        company = target;

        if (wasNew) {
            stage().setTitle("Redaguoti įmonę");
            hintLabel.setText("Įmonė išsaugota. Dabar galite pridėti paslaugas ir prižiūrėtojus.");
            unlockInnerTabs();
        } else {
            stage().close();
        }
    }

    @FXML
    private void onClose() {
        stage().close();
    }

    // ----------------------------------------------------------------- Paslaugos

    /** Opens the service form with this company fixed. The service is saved right away. */
    @FXML
    private void onAddService() {
        Service service = ServiceFormController.open(stage(), null, company);
        if (service != null) {
            reloadServices();
        }
    }

    /** Removing a service deletes it. A booked service only becomes INACTIVE. */
    @FXML
    private void onRemoveService() {
        Service service = servicesList.getSelectionModel().getSelectedItem();
        if (service == null) {
            Dialogs.info(stage(), "Nepasirinkta", "Pirmiausia pasirinkite paslaugą sąraše.");
            return;
        }
        if (!Dialogs.confirm(stage(), "Pašalinti paslaugą",
                "Ar tikrai pašalinti paslaugą „" + service.getTitle() + "“?")) {
            return;
        }
        boolean deleted = serviceService.delete(service);
        reloadServices();
        if (!deleted) {
            Dialogs.info(stage(), "Paslauga išjungta",
                    "Paslauga turi rezervacijų, todėl ji nepašalinta, o pažymėta kaip neaktyvi.");
        }
    }

    // ----------------------------------------------------------------- Prižiūrėtojai

    @FXML
    private void onHireCaretaker() {
        Caretaker caretaker = freeCaretakerBox.getValue();
        if (caretaker == null) {
            Dialogs.info(stage(), "Nepasirinkta", "Pirmiausia pasirinkite prižiūrėtoją iš sąrašo.");
            return;
        }
        companyService.hireCaretaker(company, caretaker);
        reloadCaretakers();
    }

    /** The caretaker leaves the company but stays in Users. */
    @FXML
    private void onReleaseCaretaker() {
        Caretaker caretaker = caretakersList.getSelectionModel().getSelectedItem();
        if (caretaker == null) {
            Dialogs.info(stage(), "Nepasirinkta", "Pirmiausia pasirinkite prižiūrėtoją sąraše.");
            return;
        }
        companyService.releaseCaretaker(company, caretaker);
        reloadCaretakers();
    }

    // ----------------------------------------------------------------- helpers

    private void unlockInnerTabs() {
        servicesTab.setDisable(false);
        caretakersTab.setDisable(false);
        reloadServices();
        reloadCaretakers();
    }

    private void reloadServices() {
        servicesList.getItems().setAll(company.getServices());
    }

    /** Assigned = this company's list. Free = caretakers with no company (a caretaker works for one company at most). */
    private void reloadCaretakers() {
        caretakersList.getItems().setAll(company.getCaretakers());
        freeCaretakerBox.getItems().setAll(userService.findCaretakers(null).stream()
                .filter(Caretaker::isIndependent)
                .toList());
        freeCaretakerBox.setValue(null);
    }

    private void copyFormInto(Company target) {
        target.setName(trimmed(nameField.getText()));
        target.setCompanyCode(trimmed(codeField.getText()));
        target.setCity(trimmed(cityField.getText()));
        target.setAddress(trimmed(addressField.getText()));
        target.setActive(activeCheck.isSelected());
    }

    private static String trimmed(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void showErrors(Map<String, String> errors) {
        innerTabs.getSelectionModel().selectFirst(); // the bad fields are all on "Duomenys"
        for (String field : errors.keySet()) {
            Control control = fieldControls.get(field);
            if (control != null) {
                control.getStyleClass().add("error");
            }
        }
        Dialogs.error(stage(), "Neteisingi duomenys", String.join("\n", errors.values()));
    }

    private void clearErrors() {
        fieldControls.values().forEach(control -> control.getStyleClass().removeAll("error"));
    }

    private Stage stage() {
        return (Stage) nameField.getScene().getWindow();
    }
}
