package com.example.petregistry.controller;

import com.example.petregistry.model.Company;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.model.Species;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.service.ServiceService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Controller for service-form.fxml: the "new / edit service" window. */
public class ServiceFormController {

    private static final int MIN_DURATION = 15;
    private static final int MAX_DURATION = 1440;
    private static final int DURATION_STEP = 15;
    private static final int DEFAULT_DURATION = 60;

    private final ServiceService serviceService = new ServiceService();
    private final CompanyService companyService = new CompanyService();

    @FXML private ComboBox<Company> companyBox;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<ServiceType> typeBox;
    @FXML private TextField priceField;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private ComboBox<ServiceLocation> locationBox;
    @FXML private Label statusLabel;
    @FXML private ComboBox<ServiceStatus> statusBox;
    @FXML private FlowPane speciesPane;
    @FXML private FlowPane sizesPane;

    // One CheckBox per enum value, made in code.
    private final Map<Species, CheckBox> speciesChecks = new EnumMap<>(Species.class);
    private final Map<PetSize, CheckBox> sizeChecks = new EnumMap<>(PetSize.class);

    /** Field name in ValidationException → the node to mark red. */
    private final Map<String, Node> fieldNodes = new LinkedHashMap<>();

    private Service service;  // the service being edited (the stored object), or a new one
    private Service saved;    // set when the save worked
    private Company fixedCompany; // set when opened from the company window (the box is locked)

    /**
     * Opens the form as a modal window and waits until it is closed.
     *
     * @param service the service to edit, or null for a new one
     * @return the saved service, or null if the user cancelled
     */
    public static Service open(Window parent, Service service) {
        return open(parent, service, null);
    }

    /**
     * Same, but with the company already chosen and locked (used by the company window's service list).
     *
     * @param fixedCompany the company to use, or null to let the user choose
     */
    public static Service open(Window parent, Service service, Company fixedCompany) {
        FXMLLoader loader = new FXMLLoader(ServiceFormController.class.getResource("/com/example/petregistry/view/service-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load service-form.fxml", e);
        }
        ServiceFormController form = loader.getController();
        form.fill(service);
        if (fixedCompany != null) {
            form.fixedCompany = fixedCompany;
            form.companyBox.setValue(fixedCompany);
            form.companyBox.setDisable(true);
        }

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(service == null ? "Nauja paslauga" : "Redaguoti paslaugą");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.saved;
    }

    @FXML
    private void initialize() {
        companyBox.getItems().setAll(companyService.findAll());
        typeBox.getItems().setAll(ServiceType.values());
        locationBox.getItems().setAll(ServiceLocation.values());
        statusBox.getItems().setAll(ServiceStatus.values());
        setUpDurationSpinner();

        // Generated from the enums: if a new Species is added later, the form gets its CheckBox automatically.
        for (Species species : Species.values()) {
            CheckBox check = new CheckBox(species.toString());
            speciesChecks.put(species, check);
            speciesPane.getChildren().add(check);
        }
        for (PetSize size : PetSize.values()) {
            CheckBox check = new CheckBox(size.toString());
            sizeChecks.put(size, check);
            sizesPane.getChildren().add(check);
        }

        fieldNodes.put("company", companyBox);
        fieldNodes.put("title", titleField);
        fieldNodes.put("type", typeBox);
        fieldNodes.put("price", priceField);
        fieldNodes.put("durationMinutes", durationSpinner);
        fieldNodes.put("location", locationBox);
        fieldNodes.put("allowedSpecies", speciesPane);
        fieldNodes.put("allowedSizes", sizesPane);
    }

    /** The arrows move by 15. Typing is allowed too, so the service still checks "multiple of 15". */
    private void setUpDurationSpinner() {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory = new SpinnerValueFactory.IntegerSpinnerValueFactory(
                MIN_DURATION, MAX_DURATION, DEFAULT_DURATION, DURATION_STEP);
        // The default converter throws NumberFormatException on bad text; this one keeps the old value.
        factory.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value.toString();
            }

            @Override
            public Integer fromString(String text) {
                try {
                    return Integer.valueOf(text.trim());
                } catch (NumberFormatException e) {
                    return factory.getValue();
                }
            }
        });
        durationSpinner.setValueFactory(factory);
    }

    /** Puts the service's values into the fields. A new service: empty fields, default duration, no status row. */
    private void fill(Service existing) {
        service = existing != null ? existing : new Service();
        boolean editing = existing != null;
        show(statusLabel, editing);
        show(statusBox, editing);
        if (!editing) {
            return;
        }
        companyBox.setValue(service.getCompany());
        titleField.setText(service.getTitle());
        descriptionArea.setText(service.getDescription());
        typeBox.setValue(service.getType());
        priceField.setText(service.getPrice() == null ? "" : service.getPrice().toPlainString());
        durationSpinner.getValueFactory().setValue(service.getDurationMinutes());
        locationBox.setValue(service.getLocation());
        statusBox.setValue(service.getStatus());
        speciesChecks.forEach((species, check) -> check.setSelected(service.getAllowedSpecies().contains(species)));
        sizeChecks.forEach((size, check) -> check.setSelected(service.getAllowedSizes().contains(size)));
    }

    /**
     * Reads the fields into a DRAFT service and checks it. On errors: show them all and change nothing.
     * On success: copy the values into the real service and call create / update.
     */
    @FXML
    private void onSave() {
        clearErrors();
        Map<String, String> formErrors = new LinkedHashMap<>();
        BigDecimal price = parsePrice(formErrors);
        int duration = parseDuration(formErrors);
        // Use the fixed company itself: companyBox.getValue() may be an equal copy, and the company window must see the new service.
        Company company = fixedCompany != null ? fixedCompany : companyBox.getValue();

        Service draft = new Service();
        copyFormInto(draft, price, duration);

        Map<String, String> errors = new LinkedHashMap<>();
        try {
            serviceService.validate(draft, company);
        } catch (ValidationException e) {
            errors.putAll(e.getErrors());
        }
        errors.putAll(formErrors); // "must be a number" beats "enter a price" for the same field
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }

        copyFormInto(service, price, duration);
        try {
            if (service.isNew()) {
                serviceService.create(service, company);
            } else {
                serviceService.update(service, company);
            }
        } catch (ValidationException e) {
            showErrors(e.getErrors()); // shouldn't happen: the draft passed the same check
            return;
        }
        saved = service;
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    // ----------------------------------------------------------------- helpers

    /** Copies the form into the service. The company is NOT set here: the service layer links it (both sides). */
    private void copyFormInto(Service target, BigDecimal price, int duration) {
        target.setTitle(trimmed(titleField.getText()));
        target.setDescription(trimmed(descriptionArea.getText()));
        target.setType(typeBox.getValue());
        target.setPrice(price);
        target.setDurationMinutes(duration);
        target.setLocation(locationBox.getValue());
        if (statusBox.isVisible() && statusBox.getValue() != null) {
            target.setStatus(statusBox.getValue()); // edit only; a new service keeps its default ACTIVE
        }
        target.setAllowedSpecies(checked(speciesChecks, Species.class));
        target.setAllowedSizes(checked(sizeChecks, PetSize.class));
    }

    /** The enum values whose CheckBox is ticked. */
    private static <E extends Enum<E>> Set<E> checked(Map<E, CheckBox> checks, Class<E> type) {
        Set<E> result = EnumSet.noneOf(type);
        checks.forEach((value, check) -> {
            if (check.isSelected()) {
                result.add(value);
            }
        });
        return result;
    }

    /** "25,50" or "25.50" → 25.50. Empty → null (the service says "enter a price"). Not a number → form error. */
    private BigDecimal parsePrice(Map<String, String> formErrors) {
        String text = priceField.getText() == null ? "" : priceField.getText().trim().replace(',', '.');
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            formErrors.put("price", "Kaina turi būti skaičius, pvz. 25,00");
            return null;
        }
    }

    /** Reads the text the user SEES, not spinner.getValue(): after typing "abc" the value is still the old number. */
    private int parseDuration(Map<String, String> formErrors) {
        String text = durationSpinner.getEditor().getText().trim();
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            formErrors.put("durationMinutes", "Trukmė turi būti sveikasis skaičius minutėmis, pvz. 60");
            return 0;
        }
    }

    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private static String trimmed(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void showErrors(Map<String, String> errors) {
        for (String field : errors.keySet()) {
            Node node = fieldNodes.get(field);
            if (node != null) {
                node.getStyleClass().add("error");
            }
        }
        Dialogs.error(window(), "Neteisingi duomenys", String.join("\n", errors.values()));
    }

    private void clearErrors() {
        fieldNodes.values().forEach(node -> node.getStyleClass().removeAll("error"));
    }

    private void close() {
        ((Stage) window()).close();
    }

    private Window window() {
        return titleField.getScene().getWindow();
    }
}
