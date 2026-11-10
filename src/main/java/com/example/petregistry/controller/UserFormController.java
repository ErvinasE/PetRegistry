package com.example.petregistry.controller;

import com.example.petregistry.model.Administrator;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.CompanyService;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controller for user-form.fxml: ONE form for all 4 roles. Each role's extra fields are in their own VBox. */
public class UserFormController {

    private final UserService userService = new UserService();
    private final CompanyService companyService = new CompanyService();
    private final PetService petService = new PetService();

    // common fields
    @FXML private ComboBox<Role> roleBox;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField phoneField;
    @FXML private Label passwordLabel;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField passwordRepeatField;
    @FXML private Label passwordHint;

    // pet owner
    @FXML private VBox ownerFields;
    @FXML private TextField addressField;
    @FXML private VBox petsSection;
    @FXML private ListView<Pet> petsList;

    // caretaker
    @FXML private VBox caretakerFields;
    @FXML private TextField experienceField;
    @FXML private Label caretakerCompanyLabel;
    @FXML private TextArea bioArea;

    // company manager
    @FXML private VBox managerFields;
    @FXML private ComboBox<Company> companyBox;

    /** Field name in ValidationException → the control to mark red. */
    private final Map<String, Control> fieldControls = new LinkedHashMap<>();

    private User user;   // the user being edited (the stored object), or null when creating
    private User saved;  // set when the save worked

    /**
     * Opens the form as a modal window and waits until it is closed.
     *
     * @param parent the window to open on top of
     * @param user   the user to edit, or null for a new one
     * @return the saved user, or null if the user cancelled
     */
    public static User open(Window parent, User user) {
        FXMLLoader loader = new FXMLLoader(UserFormController.class.getResource("/com/example/petregistry/view/user-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load user-form.fxml", e);
        }
        UserFormController form = loader.getController();
        form.fill(user);

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(user == null ? "Naujas vartotojas" : "Redaguoti vartotoją");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.saved;
    }

    @FXML
    private void initialize() {
        roleBox.getItems().setAll(Role.values());
        companyBox.getItems().setAll(companyService.findAll());

        // When the role changes, show that role's fields (only possible while creating).
        roleBox.valueProperty().addListener((observable, oldRole, newRole) -> showFieldsFor(newRole));

        fieldControls.put("role", roleBox);
        fieldControls.put("username", usernameField);
        fieldControls.put("email", emailField);
        fieldControls.put("firstName", firstNameField);
        fieldControls.put("lastName", lastNameField);
        fieldControls.put("phone", phoneField);
        fieldControls.put("password", passwordField);
        fieldControls.put("passwordRepeat", passwordRepeatField);
        fieldControls.put("experienceYears", experienceField);
        fieldControls.put("company", companyBox);
    }

    /** Puts the user's values into the fields. A new user starts with empty fields and no role. */
    private void fill(User existing) {
        user = existing;
        if (existing == null) {
            passwordHint.setText("Bent 8 simboliai.");
            showFieldsFor(null);
            return;
        }
        roleBox.setValue(existing.getRole()); // this also shows the role's fields (the listener above)
        roleBox.setDisable(true);
        usernameField.setText(existing.getUsername());
        emailField.setText(existing.getEmail());
        firstNameField.setText(existing.getFirstName());
        lastNameField.setText(existing.getLastName());
        phoneField.setText(existing.getPhone());
        passwordLabel.setText("Naujas slaptažodis");
        passwordHint.setText("Palikite tuščius, jei slaptažodžio nekeičiate.");

        if (existing instanceof PetOwner owner) {
            addressField.setText(owner.getAddress());
            reloadPets();
        } else if (existing instanceof Caretaker caretaker) {
            experienceField.setText(String.valueOf(caretaker.getExperienceYears()));
            bioArea.setText(caretaker.getBio());
            caretakerCompanyLabel.setText(caretaker.isIndependent()
                    ? "Nepriklausomas" : caretaker.getCompany().getName());
        } else if (existing instanceof CompanyManager manager) {
            companyBox.setValue(manager.getCompany());
        }
        showFieldsFor(existing.getRole());
    }

    /** Shows exactly one role box (or none). The pet list only when editing an owner. */
    private void showFieldsFor(Role role) {
        show(ownerFields, role == Role.PET_OWNER);
        show(petsSection, role == Role.PET_OWNER && user != null);
        show(caretakerFields, role == Role.CARETAKER);
        show(managerFields, role == Role.COMPANY_MANAGER);
        if (role == Role.CARETAKER && user == null) {
            caretakerCompanyLabel.setText("Nepriklausomas (įmonę priskiria įmonės langas)");
        }
        // The boxes changed the form's height: make the window fit again.
        if (roleBox.getScene() != null && roleBox.getScene().getWindow() != null) {
            roleBox.getScene().getWindow().sizeToScene();
        }
    }

    /**
     * visible=false only hides a node: it still takes its space, leaving a gap.
     * managed=false also tells the parent layout to skip it, so the form closes up.
     */
    private static void show(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /**
     * Same pattern as the pet form:
     * 1. Read the fields into a DRAFT (a new object of the right class, stored nowhere) and check it.
     * 2. Any errors → show them all, mark the fields red, change nothing.
     * 3. No errors → copy the values into the real user and call create / update.
     */
    @FXML
    private void onSave() {
        clearErrors();
        Role role = roleBox.getValue();
        Map<String, String> formErrors = new LinkedHashMap<>();
        if (role == null) {
            formErrors.put("role", "Pasirinkite pareigas");
        }
        int experience = role == Role.CARETAKER ? parseExperience(formErrors) : 0;

        // No role yet? Check the common fields with an Administrator draft, so ALL errors still show at once.
        User draft = newUser(role == null ? Role.ADMIN : role);
        // Same id as the stored user, so "username already taken" doesn't count the user against itself.
        draft.setId(user == null ? 0 : user.getId());
        copyFormInto(draft, experience);

        Map<String, String> errors = new LinkedHashMap<>();
        try {
            userService.validate(draft, passwordField.getText(), passwordRepeatField.getText());
        } catch (ValidationException e) {
            errors.putAll(e.getErrors());
        }
        errors.putAll(formErrors); // the form's messages win for the same field (e.g. "not a whole number")
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }

        User target = user != null ? user : newUser(role);
        copyFormInto(target, experience);
        try {
            if (target.isNew()) {
                userService.create(target, passwordField.getText(), passwordRepeatField.getText());
            } else {
                userService.update(target, passwordField.getText(), passwordRepeatField.getText());
            }
        } catch (ValidationException e) {
            showErrors(e.getErrors()); // shouldn't happen: the draft passed the same check
            return;
        }
        saved = target;
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    /** Opens the pet form with this owner fixed. The pet is saved right away (it is its own record). */
    @FXML
    private void onAddPet() {
        Pet pet = PetFormController.open(window(), null, (PetOwner) user);
        if (pet != null) {
            reloadPets();
        }
    }

    /**
     * Removing a pet from its owner = deleting the pet: a pet can't exist without an owner (composition).
     * The delete rule still applies: a pet with reservations stays.
     */
    @FXML
    private void onRemovePet() {
        Pet pet = petsList.getSelectionModel().getSelectedItem();
        if (pet == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite gyvūną sąraše.");
            return;
        }
        if (!Dialogs.confirm(window(), "Pašalinti gyvūną",
                "Ar tikrai pašalinti gyvūną „" + pet.getName() + "“? Jo įrašas bus ištrintas.")) {
            return;
        }
        try {
            petService.delete(pet);
            reloadPets();
        } catch (BusinessRuleException e) {
            Dialogs.error(window(), "Pašalinti negalima", e.getMessage());
        }
    }

    // ----------------------------------------------------------------- helpers

    /** The role decides the class. That's why the role can't change later: Java objects can't change class. */
    private static User newUser(Role role) {
        return switch (role) {
            case ADMIN -> new Administrator();
            case COMPANY_MANAGER -> new CompanyManager();
            case CARETAKER -> new Caretaker();
            case PET_OWNER -> new PetOwner();
        };
    }

    /** Copies the form into the user: the common fields, then only that role's fields. Passwords are passed separately. */
    private void copyFormInto(User target, int experience) {
        target.setUsername(trimmed(usernameField.getText()));
        target.setEmail(trimmed(emailField.getText()));
        target.setFirstName(trimmed(firstNameField.getText()));
        target.setLastName(trimmed(lastNameField.getText()));
        target.setPhone(trimmed(phoneField.getText()));
        if (target instanceof PetOwner owner) {
            owner.setAddress(trimmed(addressField.getText()));
        } else if (target instanceof Caretaker caretaker) {
            caretaker.setExperienceYears(experience);
            caretaker.setBio(trimmed(bioArea.getText()));
        } else if (target instanceof CompanyManager manager) {
            manager.setCompany(companyBox.getValue());
        }
    }

    /** Empty → 0 years. Not a whole number → a form error. */
    private int parseExperience(Map<String, String> formErrors) {
        String text = experienceField.getText() == null ? "" : experienceField.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            formErrors.put("experienceYears", "Patirtis turi būti sveikasis skaičius, pvz. 3");
            return 0;
        }
    }

    private void reloadPets() {
        petsList.getItems().setAll(((PetOwner) user).getPets());
    }

    private static String trimmed(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void showErrors(Map<String, String> errors) {
        for (String field : errors.keySet()) {
            Control control = fieldControls.get(field);
            if (control != null) {
                control.getStyleClass().add("error");
            }
        }
        Dialogs.error(window(), "Neteisingi duomenys", String.join("\n", errors.values()));
    }

    private void clearErrors() {
        fieldControls.values().forEach(control -> control.getStyleClass().removeAll("error"));
    }

    private void close() {
        ((Stage) window()).close();
    }

    private Window window() {
        return usernameField.getScene().getWindow();
    }
}
