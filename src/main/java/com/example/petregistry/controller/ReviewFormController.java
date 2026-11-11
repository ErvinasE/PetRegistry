package com.example.petregistry.controller;

import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.User;
import com.example.petregistry.service.ReviewService;
import com.example.petregistry.service.ValidationException;
import com.example.petregistry.util.Dialogs;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Controller for review-form.fxml: a new review for one COMPLETED reservation. */
public class ReviewFormController {

    private static final List<Integer> SCORES = List.of(1, 2, 3, 4, 5);

    private final ReviewService reviewService = new ReviewService();

    @FXML private ComboBox<User> authorBox;
    @FXML private ComboBox<Rateable> targetBox;
    @FXML private ComboBox<Integer> ratingBox;
    @FXML private ComboBox<Integer> behaviorBox;
    @FXML private TextArea commentArea;

    private final Map<String, Control> fieldControls = new LinkedHashMap<>();

    private Reservation reservation;
    private Review saved;

    /** Opens the form and waits. Returns the saved review, or null if the user cancelled. */
    public static Review open(Window parent, Reservation reservation) {
        FXMLLoader loader = new FXMLLoader(ReviewFormController.class.getResource(
                "/com/example/petregistry/view/review-form.fxml"));
        Parent root;
        try {
            root = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Can't load review-form.fxml", e);
        }
        ReviewFormController form = loader.getController();
        form.fill(reservation);

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Naujas atsiliepimas");
        stage.setScene(new Scene(root));
        stage.showAndWait();
        return form.saved;
    }

    @FXML
    private void initialize() {
        ratingBox.getItems().setAll(SCORES);
        behaviorBox.getItems().setAll(SCORES);

        // The target list mixes users and companies, so each row says which one it is.
        targetBox.setCellFactory(listView -> new TargetCell());
        targetBox.setButtonCell(new TargetCell());

        // Who writes decides who can be rated, and whether the pet's behaviour is rated.
        authorBox.valueProperty().addListener((observable, oldAuthor, newAuthor) -> {
            List<Rateable> targets = reviewService.allowedTargets(newAuthor, reservation);
            targetBox.getItems().setAll(targets);
            targetBox.setValue(targets.size() == 1 ? targets.get(0) : null); // one choice → choose it
            boolean caretaker = newAuthor instanceof Caretaker;
            behaviorBox.setDisable(!caretaker);
            if (!caretaker) {
                behaviorBox.setValue(null);
            }
        });
        behaviorBox.setDisable(true);

        fieldControls.put("author", authorBox);
        fieldControls.put("target", targetBox);
        fieldControls.put("rating", ratingBox);
        fieldControls.put("petBehaviorRating", behaviorBox);
    }

    private void fill(Reservation forReservation) {
        reservation = forReservation;
        authorBox.getItems().add(reservation.getOwner());
        if (reservation.getCaretaker() != null) {
            authorBox.getItems().add(reservation.getCaretaker());
        }
    }

    /**
     * No draft needed here: the review is new and stored nowhere yet, and
     * ReviewService.create() validates BEFORE it links or saves anything.
     */
    @FXML
    private void onSave() {
        fieldControls.values().forEach(control -> control.getStyleClass().removeAll("error"));
        Review review = new Review();
        review.setAuthor(authorBox.getValue());
        review.setTarget(targetBox.getValue());
        review.setRating(ratingBox.getValue() == null ? 0 : ratingBox.getValue()); // 0 → "must be 1–5"
        review.setPetBehaviorRating(behaviorBox.getValue());
        String comment = commentArea.getText();
        review.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        try {
            reviewService.create(review, reservation);
        } catch (ValidationException e) {
            for (String field : e.getErrors().keySet()) {
                Control control = fieldControls.get(field);
                if (control != null) {
                    control.getStyleClass().add("error");
                }
            }
            Dialogs.error(stage(), "Neteisingi duomenys", String.join("\n", e.getErrors().values()));
            return;
        }
        saved = review;
        stage().close();
    }

    @FXML
    private void onCancel() {
        stage().close();
    }

    /** "Įmonė: Uodegėlė" or "Prižiūrėtojas: Eglė Vaitkutė". */
    static String targetText(Rateable target) {
        if (target instanceof Company company) {
            return "Įmonė: " + company.getName();
        }
        if (target instanceof User user) {
            return user.getRole() + ": " + user.getFullName();
        }
        return "";
    }

    /** A list row (and the closed box) that shows targetText instead of plain toString(). */
    private static class TargetCell extends ListCell<Rateable> {
        @Override
        protected void updateItem(Rateable target, boolean empty) {
            super.updateItem(target, empty);
            setText(empty || target == null ? null : targetText(target));
        }
    }

    private Stage stage() {
        return (Stage) authorBox.getScene().getWindow();
    }
}
