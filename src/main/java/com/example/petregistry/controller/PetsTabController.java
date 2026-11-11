package com.example.petregistry.controller;

import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.util.ComboBoxes;
import com.example.petregistry.util.Dialogs;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;

import java.time.LocalDate;

/** Controller for pets-tab.fxml: filter bar, table and the Naujas / Redaguoti / Šalinti buttons. */
public class PetsTabController {

    private final PetService petService = new PetService();
    private final UserService userService = new UserService();

    @FXML private TextField nameFilter;
    @FXML private ComboBox<Species> speciesFilter;
    @FXML private ComboBox<PetOwner> ownerFilter;
    @FXML private ComboBox<PetSize> sizeFilter;
    @FXML private DatePicker bornFromFilter;
    @FXML private DatePicker bornToFilter;
    @FXML private TextField minWeightFilter;
    @FXML private TextField maxWeightFilter;
    @FXML private TableView<Pet> table;
    @FXML private TableColumn<Pet, String> ownerColumn;

    /** FXMLLoader calls this once, after all @FXML fields are filled. */
    @FXML
    private void initialize() {
        speciesFilter.getItems().setAll(Species.values());
        ComboBoxes.showPromptWhenEmpty(speciesFilter);
        ComboBoxes.showPromptWhenEmpty(ownerFilter);
        sizeFilter.getItems().setAll(PetSize.values());
        ComboBoxes.showPromptWhenEmpty(sizeFilter);

        // Nested value: PropertyValueFactory can't reach pet.getOwner().getFullName(), so we use a lambda.
        ownerColumn.setCellValueFactory(cell -> {
            PetOwner owner = cell.getValue().getOwner();
            return new SimpleStringProperty(owner == null ? "" : owner.getFullName());
        });

        // Double-click on a filled row = Redaguoti.
        table.setRowFactory(tableView -> {
            TableRow<Pet> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        refresh();
    }

    /** Reloads the owner list and the table (keeping the current filter). Call after every change. */
    public void refresh() {
        PetOwner chosen = ownerFilter.getValue();
        ownerFilter.getItems().setAll(userService.findOwners());
        // The chosen owner may have been deleted meanwhile.
        ownerFilter.setValue(ownerFilter.getItems().contains(chosen) ? chosen : null);
        loadTable();
    }

    /** Checks the date and weight filters first. A bad value shows an error and does NOT search. */
    private void loadTable() {
        LocalDate bornFrom = bornFromFilter.getValue();
        LocalDate bornTo = bornToFilter.getValue();
        if (bornFrom != null && bornTo != null && bornFrom.isAfter(bornTo)) {
            Dialogs.error(window(), "Neteisingas filtras", "Gimimo data „nuo“ negali būti vėlesnė už datą „iki“.");
            return;
        }
        Double minWeight;
        Double maxWeight;
        try {
            minWeight = parseWeight(minWeightFilter.getText());
            maxWeight = parseWeight(maxWeightFilter.getText());
        } catch (NumberFormatException e) {
            Dialogs.error(window(), "Neteisingas filtras", "Svoris turi būti skaičius, pvz. 4,5");
            return;
        }
        PetOwner owner = ownerFilter.getValue();
        Integer ownerId = owner == null ? null : owner.getId();
        table.getItems().setAll(petService.search(nameFilter.getText(), speciesFilter.getValue(), ownerId,
                sizeFilter.getValue(), bornFrom, bornTo, minWeight, maxWeight));
    }

    /** Empty → null (= no filter). Accepts "4,5" and "4.5". Not a number → NumberFormatException. */
    private static Double parseWeight(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return Double.parseDouble(text.trim().replace(',', '.'));
    }

    /** Ieškoti (also Enter in the name field). */
    @FXML
    private void onSearch() {
        loadTable();
    }

    /** Rodyti visus: clear every filter and show all pets. */
    @FXML
    private void onShowAll() {
        nameFilter.clear();
        speciesFilter.setValue(null);
        ownerFilter.setValue(null);
        sizeFilter.setValue(null);
        bornFromFilter.setValue(null);
        bornToFilter.setValue(null);
        minWeightFilter.clear();
        maxWeightFilter.clear();
        loadTable();
    }

    @FXML
    private void onNew() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        Pet pet = selectedOrWarn();
        if (pet != null) {
            openForm(pet);
        }
    }

    /** Asks first. A refused delete (the pet has reservations) shows the rule's message. */
    @FXML
    private void onDelete() {
        Pet pet = selectedOrWarn();
        if (pet == null) {
            return;
        }
        if (!Dialogs.confirm(window(), "Šalinti gyvūną", "Ar tikrai pašalinti gyvūną „" + pet.getName() + "“?")) {
            return;
        }
        try {
            petService.delete(pet);
            refresh();
        } catch (BusinessRuleException e) {
            Dialogs.error(window(), "Pašalinti negalima", e.getMessage());
        }
    }

    /** Opens the form (null = new pet). After a save, reloads the table and selects the saved pet. */
    private void openForm(Pet pet) {
        Pet saved = PetFormController.open(window(), pet);
        if (saved != null) {
            refresh();
            table.getSelectionModel().select(saved);
        }
    }

    /** The selected pet, or null after telling the user to select one. */
    private Pet selectedOrWarn() {
        Pet pet = table.getSelectionModel().getSelectedItem();
        if (pet == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite gyvūną lentelėje.");
        }
        return pet;
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
