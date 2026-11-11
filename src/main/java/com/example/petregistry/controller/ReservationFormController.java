package com.example.petregistry.controller;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.Service;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.ReservationService;
import com.example.petregistry.service.ReviewService;
import com.example.petregistry.service.ServiceService;
import com.example.petregistry.service.UserService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import com.example.petregistry.util.Formats;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for reservation-form.fxml: the "new / edit reservation" window.
 * The owner → pets and service → caretakers lists depend on each other.
 */
public class ReservationFormController {

    /** Bookable times: 08:00, 08:30 ... 20:00. */
    private static final LocalTime FIRST_SLOT = LocalTime.of(8, 0);
    private static final LocalTime LAST_SLOT = LocalTime.of(20, 0);
    private static final int SLOT_MINUTES = 30;

    private final ReservationService reservationService = new ReservationService();
    private final ReviewService reviewService = new ReviewService();
    private final ServiceService serviceService = new ServiceService();
    private final PetService petService = new PetService();
    private final UserService userService = new UserService();

    @FXML private ComboBox<PetOwner> ownerBox;
    @FXML private ComboBox<Pet> petBox;
    @FXML private ComboBox<Service> serviceBox;
    @FXML private ComboBox<Caretaker> caretakerBox;
    @FXML private Button clearCaretakerButton;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<LocalTime> timeBox;
    @FXML private Label endLabel;
    @FXML private Label priceLabel;
    @FXML private Label statusLabel;
    @FXML private Label hintLabel;
    @FXML private Button saveButton;

    @FXML private VBox reviewsSection;
    @FXML private ListView<Review> reviewsList;
    @FXML private Button addReviewButton;
    @FXML private Button removeReviewButton;
    @FXML private Label reviewsHint;

    /** Field name in ValidationException → the control(s) to mark red. "start" is two controls: date + time. */
    private final Map<String, List<Control>> fieldControls = new LinkedHashMap<>();

    private Reservation reservation; // the stored reservation, or null for a new one
    private Reservation saved;       // set when Išsaugoti worked

    /**
     * Opens the form as a modal window and waits until it is closed.
     *
     * @param reservation the reservation to edit, or null for a new one
     * @return the saved reservation, or null if Išsaugoti was never pressed successfully
     */
    public static Reservation open(Window parent, Reservation reservation) {
        FXMLLoader loader = new FXMLLoader(ReservationFormController.class.getResource(
                "/com/example/petregistry/view/reservation-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load reservation-form.fxml", e);
        }
        ReservationFormController form = loader.getController();
        form.fill(reservation);

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(reservation == null ? "Nauja rezervacija" : "Redaguoti rezervaciją");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.saved;
    }

    @FXML
    private void initialize() {
        ownerBox.getItems().setAll(userService.findOwners());
        serviceBox.getItems().setAll(serviceService.findActive());
        for (LocalTime t = FIRST_SLOT; !t.isAfter(LAST_SLOT); t = t.plusMinutes(SLOT_MINUTES)) {
            timeBox.getItems().add(t);
        }

        // Past days can't be clicked. (The service still checks "start in the future".)
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });

        ownerBox.valueProperty().addListener((observable, oldOwner, newOwner) -> loadPets(newOwner));
        serviceBox.valueProperty().addListener((observable, oldService, newService) -> {
            loadCaretakers(newService);
            updateCalculated();
        });
        datePicker.valueProperty().addListener((observable, oldDate, newDate) -> updateCalculated());
        timeBox.valueProperty().addListener((observable, oldTime, newTime) -> updateCalculated());

        reviewsList.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(Review review, boolean empty) {
                super.updateItem(review, empty);
                setText(empty || review == null ? null : reviewText(review));
            }
        });

        fieldControls.put("owner", List.of(ownerBox));
        fieldControls.put("pet", List.of(petBox));
        fieldControls.put("service", List.of(serviceBox));
        fieldControls.put("caretaker", List.of(caretakerBox));
        fieldControls.put("start", List.of(datePicker, timeBox));
    }

    /**
     * New: empty fields, status PENDING, no reviews section.
     * Edit: owner and service are locked. A visit that already started can't be changed, only its reviews.
     */
    private void fill(Reservation existing) {
        reservation = existing;
        if (existing == null) {
            statusLabel.setText(ReservationStatus.PENDING.toString());
            show(reviewsSection, false);
            updateCalculated();
            return;
        }
        // Order matters: setting the owner refills the pet list, setting the service refills the caretakers.
        ownerBox.setValue(existing.getOwner());
        petBox.setValue(existing.getPet());
        serviceBox.setValue(existing.getService());
        caretakerBox.setValue(existing.getCaretaker());
        if (existing.getStart() != null) {
            datePicker.setValue(existing.getStart().toLocalDate());
            timeBox.setValue(existing.getStart().toLocalTime());
        }
        statusLabel.setText(existing.getStatus().toString());
        ownerBox.setDisable(true);
        serviceBox.setDisable(true);

        boolean notStartedYet = existing.getStatus() == ReservationStatus.PENDING
                || existing.getStatus() == ReservationStatus.CONFIRMED;
        if (notStartedYet) {
            hintLabel.setText("Šeimininko ir paslaugos keisti negalima. Norėdami juos pakeisti, atšaukite ir sukurkite naują rezervaciją.");
        } else {
            petBox.setDisable(true);
            caretakerBox.setDisable(true);
            clearCaretakerButton.setDisable(true);
            datePicker.setDisable(true);
            timeBox.setDisable(true);
            saveButton.setDisable(true);
            hintLabel.setText("Rezervacija jau „" + existing.getStatus() + "“, jos duomenų keisti negalima.");
        }
        updateCalculated();

        show(reviewsSection, true);
        boolean completed = existing.getStatus() == ReservationStatus.COMPLETED;
        addReviewButton.setDisable(!completed);
        reviewsHint.setText(completed ? "" : "Atsiliepimus galima palikti tik įvykdytai rezervacijai.");
        reloadReviews();
    }

    /** Same draft pattern as the other forms: check a copy first, touch the stored object only if it is valid. */
    @FXML
    private void onSave() {
        clearErrors();
        Reservation draft = new Reservation();
        // validate() is stricter before the visit starts, so the draft needs the real status.
        draft.setStatus(reservation == null ? ReservationStatus.PENDING : reservation.getStatus());
        copyFormInto(draft);
        try {
            reservationService.validate(draft);
        } catch (ValidationException e) {
            showErrors(e.getErrors());
            return;
        }

        Reservation target = reservation == null ? new Reservation() : reservation;
        copyFormInto(target);
        try {
            if (target.isNew()) {
                reservationService.create(target);
            } else {
                reservationService.update(target);
            }
        } catch (ValidationException e) {
            showErrors(e.getErrors()); // shouldn't happen: the draft passed the same check
            return;
        }
        saved = target;
        stage().close();
    }

    @FXML
    private void onClose() {
        stage().close();
    }

    @FXML
    private void onClearCaretaker() {
        caretakerBox.setValue(null);
    }

    // ----------------------------------------------------------------- Atsiliepimai

    @FXML
    private void onAddReview() {
        Review review = ReviewFormController.open(stage(), reservation);
        if (review != null) {
            reloadReviews();
        }
    }

    @FXML
    private void onRemoveReview() {
        Review review = reviewsList.getSelectionModel().getSelectedItem();
        if (review == null) {
            Dialogs.info(stage(), "Nepasirinkta", "Pirmiausia pasirinkite atsiliepimą sąraše.");
            return;
        }
        if (!Dialogs.confirm(stage(), "Pašalinti atsiliepimą", "Ar tikrai pašalinti šį atsiliepimą?")) {
            return;
        }
        reviewService.delete(review);
        reloadReviews();
    }

    private void reloadReviews() {
        reviewsList.getItems().setAll(reservation.getReviews());
    }

    /** "Anna Kazlauskienė → Eglė Vaitkutė: 5/5 (elgesys 4/5) – Ačiū!" */
    private static String reviewText(Review review) {
        String text = review.getAuthor() + " → " + review.getTarget() + ": " + review.getRating() + "/5";
        if (review.getPetBehaviorRating() != null) {
            text += " (gyvūno elgesys " + review.getPetBehaviorRating() + "/5)";
        }
        if (review.getComment() != null) {
            text += " – " + review.getComment();
        }
        return text;
    }

    // ----------------------------------------------------------------- dependent lists and labels

    /** Only the chosen owner's pets. A pet that doesn't belong to the new owner is cleared. */
    private void loadPets(PetOwner owner) {
        Pet chosen = petBox.getValue();
        if (owner == null) {
            petBox.getItems().clear();
        } else {
            petBox.getItems().setAll(petService.search(null, null, owner.getId()));
        }
        petBox.setValue(petBox.getItems().contains(chosen) ? chosen : null);
    }

    /** Only caretakers who work for the service's company. */
    private void loadCaretakers(Service service) {
        Caretaker chosen = caretakerBox.getValue();
        if (service == null || service.getCompany() == null) {
            caretakerBox.getItems().clear();
        } else {
            caretakerBox.getItems().setAll(userService.findCaretakers(service.getCompany().getId()));
        }
        caretakerBox.setValue(caretakerBox.getItems().contains(chosen) ? chosen : null);
    }

    /** The labels the user doesn't type: end = start + duration, and the price (a saved reservation shows its own copied price). */
    private void updateCalculated() {
        Service service = serviceBox.getValue();
        LocalDateTime start = start();
        endLabel.setText(service == null || start == null
                ? "–" : Formats.dateTime(start.plusMinutes(service.getDurationMinutes())));
        if (reservation != null) {
            priceLabel.setText(reservation.getPrice() + " €");
        } else {
            priceLabel.setText(service == null ? "–" : service.getPrice() + " €");
        }
    }

    // ----------------------------------------------------------------- helpers

    /** Date + time → one LocalDateTime, or null if either is missing. */
    private LocalDateTime start() {
        LocalDate date = datePicker.getValue();
        LocalTime time = timeBox.getValue();
        return date == null || time == null ? null : LocalDateTime.of(date, time);
    }

    private void copyFormInto(Reservation target) {
        target.setOwner(ownerBox.getValue());
        target.setPet(petBox.getValue());
        target.setService(serviceBox.getValue());
        target.setCaretaker(caretakerBox.getValue());
        target.setStart(start());
    }

    /** Hidden AND not managed, so the window has no empty gap where the section was. */
    private static void show(VBox box, boolean visible) {
        box.setVisible(visible);
        box.setManaged(visible);
    }

    private void showErrors(Map<String, String> errors) {
        for (String field : errors.keySet()) {
            for (Control control : fieldControls.getOrDefault(field, List.of())) {
                control.getStyleClass().add("error");
            }
        }
        Dialogs.error(stage(), "Neteisingi duomenys", String.join("\n", errors.values()));
    }

    private void clearErrors() {
        List<Control> all = new ArrayList<>();
        fieldControls.values().forEach(all::addAll);
        all.forEach(control -> control.getStyleClass().removeAll("error"));
    }

    private Stage stage() {
        return (Stage) ownerBox.getScene().getWindow();
    }
}
