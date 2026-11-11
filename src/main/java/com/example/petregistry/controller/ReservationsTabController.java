package com.example.petregistry.controller;

import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.ReservationService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.util.ComboBoxes;
import com.example.petregistry.util.Dialogs;
import com.example.petregistry.util.Formats;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Controller for reservations-tab.fxml: filter bar, table, and Nauja / Redaguoti / Šalinti / Keisti būseną. */
public class ReservationsTabController {

    private final ReservationService reservationService = new ReservationService();
    private final UserService userService = new UserService();

    @FXML private ComboBox<ReservationStatus> statusFilter;
    @FXML private ComboBox<PetOwner> ownerFilter;
    @FXML private DatePicker fromFilter;
    @FXML private DatePicker toFilter;
    @FXML private TextField textFilter;
    @FXML private TextField minPriceFilter;
    @FXML private TextField maxPriceFilter;
    @FXML private TableView<Reservation> table;
    @FXML private TableColumn<Reservation, String> startColumn;
    @FXML private TableColumn<Reservation, String> endColumn;
    @FXML private TableColumn<Reservation, String> ownerColumn;
    @FXML private TableColumn<Reservation, String> petColumn;
    @FXML private TableColumn<Reservation, String> serviceColumn;
    @FXML private TableColumn<Reservation, String> caretakerColumn;

    @FXML
    private void initialize() {
        statusFilter.getItems().setAll(ReservationStatus.values());
        ComboBoxes.showPromptWhenEmpty(statusFilter);
        ComboBoxes.showPromptWhenEmpty(ownerFilter);

        startColumn.setCellValueFactory(cell -> new SimpleStringProperty(Formats.dateTime(cell.getValue().getStart())));
        endColumn.setCellValueFactory(cell -> new SimpleStringProperty(Formats.dateTime(cell.getValue().getEnd())));
        // Nested values: one small lambda per column. textOf turns null (e.g. no caretaker) into "".
        ownerColumn.setCellValueFactory(cell -> textOf(cell.getValue(), Reservation::getOwner));
        petColumn.setCellValueFactory(cell -> textOf(cell.getValue(), Reservation::getPet));
        serviceColumn.setCellValueFactory(cell -> textOf(cell.getValue(), Reservation::getService));
        caretakerColumn.setCellValueFactory(cell -> textOf(cell.getValue(), Reservation::getCaretaker));

        // Double-click on a filled row = Redaguoti.
        table.setRowFactory(tableView -> {
            TableRow<Reservation> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    openForm(row.getItem());
                }
            });
            return row;
        });

        refresh();
    }

    /** Reloads the owner list and the table (keeping the filter). MainController calls it when the tab opens. */
    public void refresh() {
        PetOwner chosen = ownerFilter.getValue();
        ownerFilter.getItems().setAll(userService.findOwners());
        ownerFilter.setValue(ownerFilter.getItems().contains(chosen) ? chosen : null);
        loadTable();
    }

    private void loadTable() {
        LocalDate from = fromFilter.getValue();
        LocalDate to = toFilter.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            Dialogs.error(window(), "Neteisingas filtras", "Data „Nuo“ negali būti vėlesnė už datą „Iki“.");
            return;
        }
        BigDecimal minPrice;
        BigDecimal maxPrice;
        try {
            minPrice = parsePrice(minPriceFilter.getText());
            maxPrice = parsePrice(maxPriceFilter.getText());
        } catch (NumberFormatException e) {
            Dialogs.error(window(), "Neteisingas filtras", "Kaina turi būti skaičius, pvz. 12,50");
            return;
        }
        PetOwner owner = ownerFilter.getValue();
        Integer ownerId = owner == null ? null : owner.getId();
        table.getItems().setAll(reservationService.search(statusFilter.getValue(), ownerId, from, to,
                textFilter.getText(), minPrice, maxPrice));
    }

    /** Empty → null (= no filter). Accepts "12,50" and "12.50". Not a number → NumberFormatException. */
    private static BigDecimal parsePrice(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text.trim().replace(',', '.'));
    }

    @FXML
    private void onSearch() {
        loadTable();
    }

    @FXML
    private void onShowAll() {
        statusFilter.setValue(null);
        ownerFilter.setValue(null);
        fromFilter.setValue(null);
        toFilter.setValue(null);
        textFilter.clear();
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
        Reservation reservation = selectedOrWarn();
        if (reservation != null) {
            openForm(reservation);
        }
    }

    /** Asks first. The reservation's reviews are deleted with it (the rule), so the question says so. */
    @FXML
    private void onDelete() {
        Reservation reservation = selectedOrWarn();
        if (reservation == null) {
            return;
        }
        String question = "Ar tikrai pašalinti rezervaciją „" + reservation.getService() + " – " + reservation.getPet()
                + ", " + Formats.dateTime(reservation.getStart()) + "“?";
        if (!reservation.getReviews().isEmpty()) {
            question += "\nKartu bus pašalinti jos atsiliepimai (" + reservation.getReviews().size() + ").";
        }
        if (!Dialogs.confirm(window(), "Šalinti rezervaciją", question)) {
            return;
        }
        reservationService.delete(reservation);
        refresh();
    }

    /** Keisti būseną: offers only the moves the transition table allows. */
    @FXML
    private void onChangeStatus() {
        Reservation reservation = selectedOrWarn();
        if (reservation == null) {
            return;
        }
        List<ReservationStatus> next = List.copyOf(reservation.getStatus().nextStatuses());
        if (next.isEmpty()) {
            Dialogs.info(window(), "Būsenos keisti negalima",
                    "Rezervacija yra galutinės būsenos „" + reservation.getStatus() + "“.");
            return;
        }
        Dialogs.choose(window(), "Keisti būseną",
                        "Dabar: „" + reservation.getStatus() + "“. Nauja būsena:", next)
                .ifPresent(target -> {
                    try {
                        reservationService.changeStatus(reservation, target);
                    } catch (BusinessRuleException e) {
                        Dialogs.error(window(), "Negalima", e.getMessage());
                    }
                    refresh();
                    table.getSelectionModel().select(reservation);
                });
    }

    /** Always reloads after the window closes: reviews are saved inside it. */
    private void openForm(Reservation reservation) {
        Reservation result = ReservationFormController.open(window(), reservation);
        refresh();
        if (result != null) {
            table.getSelectionModel().select(result);
        }
    }

    /** A cell with the part's name (toString), or "" when the part is missing. */
    private static SimpleStringProperty textOf(Reservation reservation, Function<Reservation, ?> part) {
        return new SimpleStringProperty(Objects.toString(part.apply(reservation), ""));
    }

    private Reservation selectedOrWarn() {
        Reservation reservation = table.getSelectionModel().getSelectedItem();
        if (reservation == null) {
            Dialogs.info(window(), "Nepasirinkta", "Pirmiausia pasirinkite rezervaciją lentelėje.");
        }
        return reservation;
    }

    private Window window() {
        return table.getScene().getWindow();
    }
}
