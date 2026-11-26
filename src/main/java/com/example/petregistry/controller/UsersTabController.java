package com.example.petregistry.controller;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.User;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.UserService;
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

/** Controller for users-tab.fxml: filter bar, table, and Naujas / Redaguoti / Šalinti / Blokuoti. */
public class UsersTabController {

    private final UserService userService = new UserService();

    @FXML private TextField textFilter;
    @FXML private ComboBox<Role> roleFilter;
    @FXML private ComboBox<String> blockedFilter;
    @FXML private TableView<User> table;
    @FXML private TableColumn<User, String> companyColumn;
    @FXML private TableColumn<User, String> blockedColumn;
    @FXML private Button blockButton;

    @FXML
    private void initialize() {
        roleFilter.getItems().setAll(Role.values());
        ComboBoxes.showPromptWhenEmpty(roleFilter);
        ComboBoxes.fillYesNo(blockedFilter);

        companyColumn.setCellValueFactory(cell -> new SimpleStringProperty(companyText(cell.getValue())));
        blockedColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().isBlocked() ? "Taip" : "Ne"));

        // Double-click on a filled row = Redaguoti.
        table.setRowFactory(tableView -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        // The button always says what it will do.
        table.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldUser, newUser) -> updateBlockButton(newUser));

        refresh();
    }

    /** Reloads the table (keeping the current filter). MainController also calls it when this tab is opened. */
    public void refresh() {
        table.getItems().setAll(userService.search(textFilter.getText(), roleFilter.getValue(),
                ComboBoxes.yesNoValue(blockedFilter)));
    }

    @FXML
    private void onSearch() {
        refresh();
    }

    @FXML
    private void onShowAll() {
        textFilter.clear();
        roleFilter.setValue(null);
        blockedFilter.setValue(null);
        refresh();
    }

    @FXML
    private void onNew() {
        openForm(null);
    }

    @FXML
    private void onEdit() {
        User user = selectedOrWarn();
        if (user != null) {
            openForm(user);
        }
    }

    /** Asks first. Deleting an owner also deletes their pets, so the question says so. */
    @FXML
    private void onDelete() {
        User user = selectedOrWarn();
        if (user == null) {
            return;
        }
        String question = "Ar tikrai pašalinti vartotoją „" + user.getFullName() + "“?";
        if (user instanceof PetOwner owner && !owner.getPets().isEmpty()) {
            question += "\nKartu bus pašalinti jo gyvūnai (" + owner.getPets().size() + ").";
        }
        if (!Dialogs.confirm(window(), "Šalinti vartotoją", question)) {
            return;
        }
        try {
            userService.delete(user);
            refresh();
        } catch (BusinessRuleException e) {
            Dialogs.error(window(), "Pašalinti negalima", e.getMessage());
        }
    }

    /** Blokuoti / Atblokuoti: flips the selected user's "blocked" flag. */
    @FXML
    private void onToggleBlocked() {
        User user = selectedOrWarn();
        if (user == null) {
            return;
        }
        userService.setBlocked(user, !user.isBlocked());
        refresh();
        table.getSelectionModel().select(user);
    }

    private void openForm(User user) {
        User saved = UserFormController.open(window(), user);
        if (saved != null) {
            refresh();
            table.getSelectionModel().select(saved);
        }
    }

    private void updateBlockButton(User user) {
        blockButton.setText(user != null && user.isBlocked() ? "Atblokuoti" : "Blokuoti");
    }

    /** Managers: their company. Caretakers: their company or "Nepriklausomas". Others: nothing. */
    private static String companyText(User user) {
        if (user instanceof CompanyManager manager) {
            return manager.getCompany() == null ? "" : manager.getCompany().getName();
        }
        if (user instanceof Caretaker caretaker) {
            return caretaker.isIndependent() ? "Nepriklausomas" : caretaker.getCompany().getName();
        }
        return "";
    }

    private User selectedOrWarn() {
        User user = table.getSelectionModel().getSelectedItem();
        if (user == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite vartotoją lentelėje.");
        }
        return user;
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
