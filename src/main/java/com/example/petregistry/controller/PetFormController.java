package com.example.petregistry.controller;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Controller for pet-form.fxml: the "new / edit pet" window. */
public class PetFormController {

    private final PetService petService = new PetService();
    private final UserService userService = new UserService();

    @FXML private TextField nameField;
    @FXML private ComboBox<Species> speciesBox;
    @FXML private ComboBox<PetSize> sizeBox;
    @FXML private DatePicker birthDatePicker;
    @FXML private TextField weightField;
    @FXML private ComboBox<PetOwner> ownerBox;
    @FXML private TextArea notesArea;
    @FXML private ImageView photoView;
    @FXML private Label photoLabel;

    /** Field name in ValidationException → the control to mark red. */
    private final Map<String, Control> fieldControls = new LinkedHashMap<>();

    private Pet pet;            // the pet being edited (the stored object), or a new one
    private String photoPath;   // chosen in the form; written into the pet only on a successful save
    private Pet saved;          // set when the save worked
    private PetOwner fixedOwner; // set when opened from the owner's pet list (the box is locked)

    /**
     * Opens the form as a modal window and waits until it is closed.
     *
     * @param parent the window to open on top of
     * @param pet    the pet to edit, or null for a new one
     * @return the saved pet, or null if the user cancelled
     */
    public static Pet open(Window parent, Pet pet) {
        return open(parent, pet, null);
    }

    /**
     * Same, but with the owner already chosen and locked (used by the owner's pet list in the Users form).
     *
     * @param fixedOwner the owner to use, or null to let the user choose
     */
    public static Pet open(Window parent, Pet pet, PetOwner fixedOwner) {
        FXMLLoader loader = new FXMLLoader(PetFormController.class.getResource("/com/example/petregistry/view/pet-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            // Only happens if the FXML file is missing or broken: a programming error, not a user error.
            throw new UncheckedIOException("Can't load pet-form.fxml", e);
        }
        PetFormController form = loader.getController();
        form.fill(pet);
        if (fixedOwner != null) {
            form.fixedOwner = fixedOwner;
            form.ownerBox.setValue(fixedOwner);
            form.ownerBox.setDisable(true);
        }

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL); // the main window is blocked while the form is open
        stage.setTitle(pet == null ? "Naujas gyvūnas" : "Redaguoti gyvūną");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.saved;
    }

    @FXML
    private void initialize() {
        speciesBox.getItems().setAll(Species.values());
        sizeBox.getItems().setAll(PetSize.values());
        ownerBox.getItems().setAll(userService.findOwners());

        fieldControls.put("name", nameField);
        fieldControls.put("species", speciesBox);
        fieldControls.put("size", sizeBox);
        fieldControls.put("birthDate", birthDatePicker);
        fieldControls.put("weightKg", weightField);
        fieldControls.put("owner", ownerBox);
    }

    /** Puts the pet's values into the fields. A new pet starts with empty fields. */
    private void fill(Pet existing) {
        pet = existing != null ? existing : new Pet();
        photoPath = pet.getPhotoPath();
        if (existing != null) {
            nameField.setText(pet.getName());
            speciesBox.setValue(pet.getSpecies());
            sizeBox.setValue(pet.getSize());
            birthDatePicker.setValue(pet.getBirthDate());
            weightField.setText(formatWeight(pet.getWeightKg()));
            ownerBox.setValue(pet.getOwner());
            notesArea.setText(pet.getNotes());
        }
        showPhoto(photoPath);
    }

    /**
     * 1. Read the fields into a DRAFT pet (not stored anywhere) and check it. A bad number is a form error.
     * 2. Any errors → show them all, mark the fields red, change nothing.
     * 3. No errors → copy the values into the real pet and call create / update.
     * The stored pet is only touched in step 3, so a refused edit leaves no bad values behind.
     */
    @FXML
    private void onSave() {
        clearErrors();
        Map<String, String> formErrors = new LinkedHashMap<>();
        double weight = parseWeight(formErrors);

        Pet draft = new Pet();
        copyFormInto(draft, weight);
        // The fixed owner is used as it is: ownerBox.getValue() may be an equal COPY of it
        // (in LAB2 the list holds fresh objects from the DB), and the owner's pet list must see the new pet.
        PetOwner owner = fixedOwner != null ? fixedOwner : ownerBox.getValue();

        Map<String, String> errors = new LinkedHashMap<>();
        try {
            petService.validate(draft, owner);
        } catch (ValidationException e) {
            errors.putAll(e.getErrors());
        }
        // "must be a number" is more helpful than the service's "must be 0–200" for the same field.
        errors.putAll(formErrors);
        if (!errors.isEmpty()) {
            showErrors(errors);
            return;
        }

        copyFormInto(pet, weight);
        try {
            if (pet.isNew()) {
                petService.create(pet, owner);
            } else {
                petService.update(pet, owner);
            }
        } catch (ValidationException e) {
            // Shouldn't happen (the draft passed the same check), but never lose the message.
            showErrors(e.getErrors());
            return;
        }
        saved = pet;
        close();
    }

    @FXML
    private void onCancel() {
        close();
    }

    @FXML
    private void onClearBirthDate() {
        birthDatePicker.setValue(null);
    }

    @FXML
    private void onChoosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pasirinkite nuotrauką");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Paveikslėliai", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(window());
        if (file != null) { // null = the user pressed Cancel
            photoPath = file.getAbsolutePath();
            showPhoto(photoPath);
        }
    }

    @FXML
    private void onRemovePhoto() {
        photoPath = null;
        showPhoto(null);
    }

    // ----------------------------------------------------------------- helpers

    /** Copies every field of the form into the given pet. The owner is NOT set here: the service links it. */
    private void copyFormInto(Pet target, double weight) {
        target.setName(trimmed(nameField.getText()));
        target.setSpecies(speciesBox.getValue());
        target.setSize(sizeBox.getValue());
        target.setBirthDate(birthDatePicker.getValue());
        target.setWeightKg(weight);
        target.setNotes(trimmed(notesArea.getText()));
        target.setPhotoPath(photoPath);
    }

    /**
     * Reads the weight. Accepts "4,5" and "4.5". Empty → 0, so the service reports "must be more than 0".
     * Not a number → 0 plus a form error.
     */
    private double parseWeight(Map<String, String> formErrors) {
        String text = weightField.getText() == null ? "" : weightField.getText().trim().replace(',', '.');
        if (text.isEmpty()) {
            return 0;
        }
        try {
            double value = Double.parseDouble(text);
            // parseDouble also accepts "NaN" and "Infinity", and NaN would slip past "0 < w <= 200".
            if (!Double.isFinite(value)) {
                throw new NumberFormatException(text);
            }
            return value;
        } catch (NumberFormatException e) {
            formErrors.put("weightKg", "Svoris turi būti skaičius, pvz. 4,5");
            return 0;
        }
    }

    /** 35.0 → "35", 4.2 → "4.2" (no ugly ".0", no 4.2000000001). */
    private static String formatWeight(double weight) {
        return weight == 0 ? "" : BigDecimal.valueOf(weight).stripTrailingZeros().toPlainString();
    }

    /** Text without spaces around it; empty text becomes null. */
    private static String trimmed(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private void showPhoto(String path) {
        photoView.setImage(null);
        if (path == null) {
            photoLabel.setText("Nuotraukos nėra");
            return;
        }
        File file = new File(path);
        if (!file.isFile()) {
            photoLabel.setText("Failas nerastas: " + file.getName());
            return;
        }
        // Loads the file now (not in the background), so isError() is known right away.
        Image image = new Image(file.toURI().toString(), 160, 160, true, true);
        if (image.isError()) {
            photoLabel.setText("Nepavyko atidaryti: " + file.getName());
            return;
        }
        photoView.setImage(image);
        photoLabel.setText(file.getName());
    }

    /** One error alert with every message, and a red glow on every bad field. */
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
        return nameField.getScene().getWindow();
    }
}
