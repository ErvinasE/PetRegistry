import com.example.petregistry.MainApp;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Review;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.BusinessRuleException;
import com.example.petregistry.service.ReservationService;
import com.example.petregistry.service.ServiceService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * M9 "Done when": a reservation can go PENDING → CONFIRMED → IN_PROGRESS → COMPLETED and get a review;
 * an illegal transition and a second review by the same author are refused.
 * Also: filters, dependent choice lists, past days disabled, end/price labels, price copy, delete with reviews.
 */
@SuppressWarnings("unchecked")
public class M9Check {

    private static int pass;
    private static int fail;

    private static Stage main;
    private static TabPane tabs;
    private static Parent tab;
    private static TableView<Reservation> table;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("CRASHED: " + t + "  (PASSED " + pass + ", FAILED " + fail + " so far)");
            System.exit(2);
        }
    }

    private static void run() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        main = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            return s;
        });
        tabs = onFx(() -> (TabPane) ((BorderPane) main.getScene().getRoot()).getCenter());
        onFx(() -> {
            tabs.getSelectionModel().select(tabNamed("Rezervacijos"));
            return null;
        });
        tab = onFx(() -> (Parent) tabNamed("Rezervacijos").getContent());
        table = onFx(() -> (TableView<Reservation>) tab.lookup("#table"));
        Thread.sleep(300);

        PetOwner anna = (PetOwner) user("anna");
        PetOwner jonas = (PetOwner) user("jonas");
        Caretaker tomas = (Caretaker) user("tomas");
        Caretaker egle = (Caretaker) user("egle");
        Pet reksas = pet("Reksas");
        Pet murka = pet("Murka");
        Service walking = service("Šunų vedžiojimas");
        Service grooming = service("Kailio kirpimas");
        Company letenele = Repositories.companies().findByCode("123456789").orElseThrow();
        Reservation seedCompleted = byStatus(ReservationStatus.COMPLETED);

        // ============================================================ table
        title("table");
        onFx(() -> {
            check("shows the 5 seed reservations", table.getItems().size() == 5);
            check("columns: Pradžia, Pabaiga, Šeimininkas, Gyvūnas, Paslauga, Prižiūrėtojas, Kaina (€), Būsena",
                    table.getColumns().stream().map(TableColumn::getText).toList().equals(List.of(
                            "Pradžia", "Pabaiga", "Šeimininkas", "Gyvūnas", "Paslauga", "Prižiūrėtojas", "Kaina (€)", "Būsena")));
            Reservation r = seedCompleted;
            check("dates shown as 'yyyy-MM-dd HH:mm' (no 'T')", cell(0, r).matches("\\d{4}-\\d\\d-\\d\\d \\d\\d:\\d\\d"));
            check("owner / pet / service / caretaker names", anna.getFullName().equals(cell(2, r))
                    && "Murka".equals(cell(3, r)) && "Gyvūnų viešbutis".equals(cell(4, r)) && egle.getFullName().equals(cell(5, r)));
            check("status label 'Įvykdyta'", "Įvykdyta".equals(cell(7, r)));
            return null;
        });

        // ============================================================ filters
        title("filters");
        onFx(() -> {
            combo(tab, "#statusFilter").setValue(ReservationStatus.COMPLETED);
            button(tab, "Ieškoti").fire();
            return null;
        });
        check("status Įvykdyta → 1 row", rows().equals(List.of(seedCompleted)));
        onFx(() -> {
            button(tab, "Rodyti visus").fire();
            combo(tab, "#ownerFilter").setValue(anna);
            button(tab, "Ieškoti").fire();
            return null;
        });
        check("owner Anna → her 2 reservations", rows().size() == 2 && rows().stream().allMatch(r -> r.getOwner() == anna));
        onFx(() -> {
            button(tab, "Rodyti visus").fire();
            ((DatePicker) tab.lookup("#fromFilter")).setValue(LocalDate.now().plusDays(4));
            button(tab, "Ieškoti").fire();
            return null;
        });
        check("from today+4 → the +5 and +7 day reservations", rows().size() == 2);
        onFx(() -> {
            ((DatePicker) tab.lookup("#toFilter")).setValue(LocalDate.now().plusDays(5));
            button(tab, "Ieškoti").fire();
            return null;
        });
        check("from today+4 to today+5 (inclusive) → 1", rows().size() == 1);
        onFx(() -> {
            ((DatePicker) tab.lookup("#toFilter")).setValue(LocalDate.now());
            return null;
        });
        clickLater(tab, "Ieškoti");
        DialogPane badRange = awaitDialog();
        check("from after to → error 'Nuo negali būti vėlesnė'", badRange != null
                && onFx(badRange::getContentText).contains("negali būti vėlesnė"));
        answer(badRange, "OK");
        clickNow(tab, "Rodyti visus");
        check("Rodyti visus → 5 again", rows().size() == 5);

        // ============================================================ nothing selected
        title("buttons with nothing selected");
        onFx(() -> {
            table.getSelectionModel().clearSelection();
            return null;
        });
        for (String b : List.of("Redaguoti", "Šalinti", "Keisti būseną")) {
            clickLater(tab, b);
            DialogPane info = awaitDialog();
            check(b + " → 'Pirmiausia pasirinkite rezervaciją lentelėje.'", info != null
                    && "Pirmiausia pasirinkite rezervaciją lentelėje.".equals(onFx(info::getContentText)));
            answer(info, "OK");
        }

        // ============================================================ new reservation form
        title("Nauja: the form");
        new ServiceService().setTemporarilyUnavailable(grooming, true); // paused services can't be booked
        Parent f = openForm(null, "Nauja rezervacija");
        onFx(() -> {
            check("status shows 'Laukiama'", "Laukiama".equals(label(f, "#statusLabel")));
            check("no reviews section for a new reservation", !shown(f, "#reviewsSection"));
            check("service list = ACTIVE only (paused 'Kailio kirpimas' missing)",
                    combo(f, "#serviceBox").getItems().size() == 4 && !combo(f, "#serviceBox").getItems().contains(grooming));
            check("time list 08:00 … 20:00 every 30 min (25 slots)", combo(f, "#timeBox").getItems().size() == 25
                    && combo(f, "#timeBox").getItems().get(0).equals(LocalTime.of(8, 0))
                    && combo(f, "#timeBox").getItems().get(24).equals(LocalTime.of(20, 0)));
            DatePicker dp = (DatePicker) f.lookup("#datePicker");
            DateCell c = dp.getDayCellFactory().call(dp);
            c.updateItem(LocalDate.now().minusDays(1), false);
            boolean yesterdayOff = c.isDisable();
            c.updateItem(LocalDate.now().plusDays(1), false);
            check("calendar: yesterday disabled, tomorrow enabled", yesterdayOff && !c.isDisable());
            check("pet list empty until an owner is chosen", combo(f, "#petBox").getItems().isEmpty());
            return null;
        });
        new ServiceService().setTemporarilyUnavailable(grooming, false);

        title("Nauja: save empty → all errors at once");
        String errors = save(f);
        check("4 messages: owner, pet, service, start", errors != null && errors.split("\n").length == 4
                && errors.contains("Pasirinkite šeimininką") && errors.contains("Pasirinkite gyvūną")
                && errors.contains("Pasirinkite paslaugą") && errors.contains("Pasirinkite pradžios laiką"));
        check("owner, pet, service, date and time are red", onFx(() -> red(f, "#ownerBox") && red(f, "#petBox")
                && red(f, "#serviceBox") && red(f, "#datePicker") && red(f, "#timeBox")));

        title("Nauja: dependent lists (listeners)");
        onFx(() -> {
            combo(f, "#ownerBox").setValue(anna);
            check("owner Anna → pets Reksas, Murka", combo(f, "#petBox").getItems().equals(List.of(reksas, murka)));
            combo(f, "#petBox").setValue(murka);
            combo(f, "#ownerBox").setValue(jonas);
            check("owner → Jonas: Murka is cleared, Jonas's pets shown", combo(f, "#petBox").getValue() == null
                    && combo(f, "#petBox").getItems().stream().allMatch(p -> ((Pet) p).getOwner() == jonas));
            combo(f, "#ownerBox").setValue(anna);
            combo(f, "#serviceBox").setValue(walking);
            check("service at Letenėlė → caretaker list = Tomas only", combo(f, "#caretakerBox").getItems().equals(List.of(tomas)));
            check("price label shows the service price '15.00 €'", "15.00 €".equals(label(f, "#priceLabel")));
            check("end label '–' until date and time are chosen", "–".equals(label(f, "#endLabel")));
            ((DatePicker) f.lookup("#datePicker")).setValue(LocalDate.now().plusDays(2));
            combo(f, "#timeBox").setValue(LocalTime.of(9, 30));
            check("end = start + 60 min → 10:30", label(f, "#endLabel").endsWith("10:30"));
            return null;
        });

        title("Nauja: rules");
        onFx(() -> {
            combo(f, "#petBox").setValue(murka);
            return null;
        });
        check("cat Murka + dog walking → 'Paslauga netinka šiam gyvūnui'", String.valueOf(save(f)).contains("netinka"));
        onFx(() -> {
            combo(f, "#petBox").setValue(reksas);
            combo(f, "#caretakerBox").setValue(egle); // not in the list, but set it anyway (like a stale value)
            return null;
        });
        check("Eglė (Uodegėlė) for a Letenėlė service → 'nedirba šios paslaugos įmonėje'",
                String.valueOf(save(f)).contains("nedirba"));
        if (LocalTime.now().isAfter(LocalTime.of(8, 5))) {
            onFx(() -> {
                combo(f, "#caretakerBox").setValue(tomas);
                ((DatePicker) f.lookup("#datePicker")).setValue(LocalDate.now());
                combo(f, "#timeBox").setValue(LocalTime.of(8, 0));
                return null;
            });
            check("today 08:00 (already past) → 'Pradžia turi būti ateityje'", "Pradžia turi būti ateityje".equals(save(f)));
        }
        int before = Repositories.reservations().findAll().size();
        check("nothing saved so far", before == 5);
        onFx(() -> {
            combo(f, "#caretakerBox").setValue(tomas);
            ((DatePicker) f.lookup("#datePicker")).setValue(LocalDate.now().plusDays(2));
            combo(f, "#timeBox").setValue(LocalTime.of(9, 30));
            return null;
        });
        Window fw = window(f);
        check("valid → no error, window closes", save(f) == null && waitClosed(fw));
        Reservation created = Repositories.reservations().findAll().get(5);
        check("stored: PENDING, Anna/Reksas/walking/Tomas", created.getStatus() == ReservationStatus.PENDING
                && created.getOwner() == anna && created.getPet() == reksas && created.getService() == walking
                && created.getCaretaker() == tomas);
        check("end = start + 60 min", created.getEnd().equals(created.getStart().plusMinutes(60)));
        check("price COPIED from the service (15.00)", created.getPrice().compareTo(new BigDecimal("15.00")) == 0);
        check("table shows 6 rows and selects the new one", rows().size() == 6
                && onFx(() -> table.getSelectionModel().getSelectedItem()) == created);

        title("price copy: a later price change doesn't rewrite the booking");
        walking.changePrice(new BigDecimal("99.00"));
        new ServiceService().update(walking, letenele);
        check("reservation still costs 15.00", created.getPrice().compareTo(new BigDecimal("15.00")) == 0);

        // ============================================================ edit PENDING
        title("Redaguoti PENDING");
        Parent e = openForm(created, "Redaguoti rezervaciją");
        onFx(() -> {
            check("owner and service locked, pet / caretaker / date / time editable",
                    combo(e, "#ownerBox").isDisabled() && combo(e, "#serviceBox").isDisabled()
                            && !combo(e, "#petBox").isDisabled() && !combo(e, "#timeBox").isDisabled());
            check("fields filled from the reservation", combo(e, "#petBox").getValue() == reksas
                    && combo(e, "#caretakerBox").getValue() == tomas && combo(e, "#timeBox").getValue().equals(LocalTime.of(9, 30)));
            check("price label shows the BOOKED price (15.00), not the new 99.00", "15.00 €".equals(label(e, "#priceLabel")));
            check("reviews section shown, but 'Pridėti' disabled (not completed)", shown(e, "#reviewsSection")
                    && buttonById(e, "#addReviewButton").isDisabled()
                    && label(e, "#reviewsHint").contains("tik įvykdytai"));
            combo(e, "#timeBox").setValue(LocalTime.of(11, 0));
            return null;
        });
        Window ew = window(e);
        check("save → closes", save(e) == null && waitClosed(ew));
        check("start moved to 11:00 and end followed (12:00)", created.getStart().toLocalTime().equals(LocalTime.of(11, 0))
                && created.getEnd().toLocalTime().equals(LocalTime.of(12, 0)));
        check("price still 15.00", created.getPrice().compareTo(new BigDecimal("15.00")) == 0);

        // ============================================================ status changes
        title("Keisti būseną: PENDING → CONFIRMED → IN_PROGRESS → COMPLETED");
        select(created);
        clickLater(tab, "Keisti būseną");
        DialogPane choose = awaitDialog();
        check("dialog offers only Patvirtinta, Atmesta, Atšaukta", choose != null
                && java.util.Set.copyOf(onFx(() -> choiceItems(choose))).equals(java.util.Set.of(ReservationStatus.CONFIRMED, ReservationStatus.REJECTED, ReservationStatus.CANCELLED)));
        check("buttons are Lithuanian (Gerai / Atšaukti)", onFx(() -> choose.getButtonTypes().stream().map(b -> b.getText()).toList())
                .equals(List.of("Gerai", "Atšaukti")));
        answer(choose, "Atšaukti");
        check("Atšaukti → still PENDING", created.getStatus() == ReservationStatus.PENDING);
        changeStatus(created, ReservationStatus.CONFIRMED);
        check("→ Patvirtinta", created.getStatus() == ReservationStatus.CONFIRMED && "Patvirtinta".equals(onFx(() -> cell(7, created))));
        changeStatus(created, ReservationStatus.IN_PROGRESS);
        check("→ Vykdoma", created.getStatus() == ReservationStatus.IN_PROGRESS);
        changeStatus(created, ReservationStatus.COMPLETED);
        check("→ Įvykdyta", created.getStatus() == ReservationStatus.COMPLETED);
        select(created);
        clickLater(tab, "Keisti būseną");
        DialogPane done = awaitDialog();
        check("Įvykdyta → info 'galutinės būsenos'", done != null && onFx(done::getContentText).contains("galutinės būsenos"));
        answer(done, "OK");

        title("illegal transitions are refused");
        try {
            new ReservationService().changeStatus(created, ReservationStatus.CONFIRMED);
            check("COMPLETED → CONFIRMED refused", false);
        } catch (BusinessRuleException ex) {
            check("COMPLETED → CONFIRMED refused: '" + ex.getMessage() + "'", ex.getMessage().startsWith("Negalima keisti būsenos"));
        }
        Reservation pending = byStatus(ReservationStatus.PENDING);
        try {
            new ReservationService().changeStatus(pending, ReservationStatus.COMPLETED);
            check("PENDING → COMPLETED refused", false);
        } catch (BusinessRuleException ex) {
            check("PENDING → COMPLETED refused (skips steps)", pending.getStatus() == ReservationStatus.PENDING);
        }

        // ============================================================ reviews
        title("Redaguoti COMPLETED: locked fields, reviews");
        Parent c = openForm(created, "Redaguoti rezervaciją");
        onFx(() -> {
            check("all booking fields and Išsaugoti disabled", combo(c, "#petBox").isDisabled()
                    && ((DatePicker) c.lookup("#datePicker")).isDisabled() && buttonById(c, "#saveButton").isDisabled());
            check("hint says it can't be changed", label(c, "#hintLabel").contains("keisti negalima"));
            check("'Pridėti atsiliepimą' enabled", !buttonById(c, "#addReviewButton").isDisabled());
            check("no reviews yet", reviewList(c).getItems().isEmpty());
            return null;
        });

        title("review form: empty save");
        Parent r = openReview(c);
        String rErrors = save(r);
        check("3 messages: author, target, rating", rErrors != null && rErrors.split("\n").length == 3
                && rErrors.contains("Pasirinkite autorių") && rErrors.contains("Pasirinkite, ką vertinate")
                && rErrors.contains("Įvertinimas turi būti nuo 1 iki 5"));
        onFx(() -> {
            check("authors = owner and caretaker", combo(r, "#authorBox").getItems().equals(List.of(anna, tomas)));
            combo(r, "#authorBox").setValue(anna);
            check("owner → targets: caretaker Tomas and company Letenėlė",
                    combo(r, "#targetBox").getItems().equals(List.of(tomas, letenele)));
            check("owner → behaviour box disabled", combo(r, "#behaviorBox").isDisabled());
            combo(r, "#authorBox").setValue(tomas);
            check("caretaker → only target is the owner, chosen automatically",
                    combo(r, "#targetBox").getItems().equals(List.of(anna)) && combo(r, "#targetBox").getValue() == anna);
            check("caretaker → behaviour box enabled", !combo(r, "#behaviorBox").isDisabled());
            combo(r, "#ratingBox").setValue(4);
            return null;
        });
        check("caretaker without behaviour → 'Įvertinkite gyvūno elgesį'", "Įvertinkite gyvūno elgesį".equals(save(r)));
        onFx(() -> {
            combo(r, "#authorBox").setValue(anna);
            combo(r, "#targetBox").setValue(tomas);
            combo(r, "#ratingBox").setValue(5);
            ((TextArea) r.lookup("#commentArea")).setText("  Puiku!  ");
            return null;
        });
        Window rw = window(r);
        check("owner → Tomas 5/5 saved, form closes", save(r) == null && waitClosed(rw));
        check("list shows 1 review", onFx(() -> reviewList(c).getItems().size()) == 1);
        Review ownerReview = created.getReviews().get(0);
        check("review stored: author Anna, target Tomas, comment trimmed, linked to the reservation",
                ownerReview.getAuthor() == anna && ownerReview.getTarget() == tomas && "Puiku!".equals(ownerReview.getComment())
                        && ownerReview.getReservation() == created && Repositories.reviews().findById(ownerReview.getId()).isPresent());
        check("Tomas's average updated to 5.0", tomas.getAverageRating() == 5.0);

        title("second review by the same author is refused");
        Parent r2 = openReview(c);
        onFx(() -> {
            combo(r2, "#authorBox").setValue(anna);
            combo(r2, "#targetBox").setValue(letenele);
            combo(r2, "#ratingBox").setValue(3);
            return null;
        });
        check("Anna again → 'jau paliko atsiliepimą'", String.valueOf(save(r2)).contains("jau paliko atsiliepimą"));
        onFx(() -> {
            combo(r2, "#authorBox").setValue(tomas);
            combo(r2, "#ratingBox").setValue(4);
            combo(r2, "#behaviorBox").setValue(5);
            return null;
        });
        Window r2w = window(r2);
        check("caretaker Tomas → Anna 4/5, behaviour 5 saved", save(r2) == null && waitClosed(r2w));
        check("list shows 2 reviews, one says 'gyvūno elgesys 5/5'", onFx(() -> reviewList(c).getItems().size() == 2
                && reviewList(c).getItems().stream().anyMatch(x -> x.getPetBehaviorRating() != null && x.getPetBehaviorRating() == 5)));
        double annaAvg = anna.getAverageRating();
        check("Anna's average = (4 seed + 4 new) / 2 = 4.0", annaAvg == 4.0);

        title("remove a review");
        onFx(() -> {
            reviewList(c).getSelectionModel().select(ownerReview);
            return null;
        });
        clickLater(c, "Pašalinti atsiliepimą");
        DialogPane rm = awaitDialog();
        answer(rm, "Taip");
        check("1 review left; Tomas's average back to 0", onFx(() -> reviewList(c).getItems().size()) == 1
                && tomas.getAverageRating() == 0.0 && Repositories.reviews().findById(ownerReview.getId()).isEmpty());
        Window cw = window(c);
        clickNow(c, "Uždaryti");
        waitClosed(cw);

        title("seed COMPLETED reservation shows its 2 reviews");
        Parent s = openForm(seedCompleted, "Redaguoti rezervaciją");
        check("2 seed reviews listed", onFx(() -> reviewList(s).getItems().size()) == 2);
        Window sw = window(s);
        clickNow(s, "Uždaryti");
        waitClosed(sw);

        // ============================================================ delete
        title("Šalinti a reservation with reviews");
        int reviewsBefore = Repositories.reviews().findAll().size();
        select(created);
        clickLater(tab, "Šalinti");
        DialogPane del = awaitDialog();
        check("question mentions its 1 review", del != null && onFx(del::getContentText).contains("atsiliepimai (1)"));
        answer(del, "Taip");
        check("reservation gone, its review gone, Anna's average back to 4.0",
                rows().size() == 5 && Repositories.reservations().findById(created.getId()).isEmpty()
                        && Repositories.reviews().findAll().size() == reviewsBefore - 1 && anna.getAverageRating() == 4.0);

        title("tab refresh");
        onFx(() -> {
            tabs.getSelectionModel().select(tabNamed("Gyvūnai"));
            return null;
        });
        new ReservationService().changeStatus(pending, ReservationStatus.CANCELLED); // changed while the tab is closed
        onFx(() -> {
            tabs.getSelectionModel().select(tabNamed("Rezervacijos"));
            return null;
        });
        check("re-opening the tab shows the new status 'Atšaukta'", "Atšaukta".equals(onFx(() -> cell(7, pending))));

        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    // ----------------------------------------------------------------- app actions

    private static Parent openForm(Reservation r, String title) throws Exception {
        if (r == null) {
            clickLater(tab, "Nauja");
        } else {
            select(r);
            clickLater(tab, "Redaguoti");
        }
        Stage form = awaitForm(title);
        check("form '" + title + "' opened", form != null);
        return onFx(() -> form.getScene().getRoot());
    }

    private static Parent openReview(Parent reservationForm) throws Exception {
        clickLater(reservationForm, "Pridėti atsiliepimą");
        Stage form = awaitForm("Naujas atsiliepimas");
        check("review form opened", form != null);
        return onFx(() -> form.getScene().getRoot());
    }

    private static void changeStatus(Reservation r, ReservationStatus target) throws Exception {
        select(r);
        clickLater(tab, "Keisti būseną");
        DialogPane d = awaitDialog();
        onFx(() -> {
            ((ComboBox<ReservationStatus>) d.lookup(".combo-box")).setValue(target);
            return null;
        });
        answer(d, "Gerai");
    }

    private static List<Object> choiceItems(DialogPane d) {
        return List.copyOf(((ComboBox<Object>) d.lookup(".combo-box")).getItems());
    }

    private static String save(Parent form) throws Exception {
        clickLater(form, "Išsaugoti");
        DialogPane dialog = awaitDialog();
        if (dialog == null) {
            return null;
        }
        String text = onFx(dialog::getContentText);
        answer(dialog, "OK");
        return text;
    }

    private static void select(Reservation r) throws Exception {
        onFx(() -> {
            table.getSelectionModel().select(r);
            return null;
        });
    }

    // ----------------------------------------------------------------- lookups

    private static Tab tabNamed(String text) {
        return tabs.getTabs().stream().filter(t -> t.getText().equals(text)).findFirst().orElseThrow();
    }

    private static User user(String username) {
        return Repositories.users().findByUsername(username).orElseThrow();
    }

    private static Pet pet(String name) {
        return Repositories.pets().findAll().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Service service(String title) {
        return Repositories.services().findAll().stream().filter(s -> s.getTitle().equals(title)).findFirst().orElseThrow();
    }

    private static Reservation byStatus(ReservationStatus status) {
        return Repositories.reservations().findAll().stream().filter(r -> r.getStatus() == status).findFirst().orElseThrow();
    }

    private static List<Reservation> rows() throws Exception {
        return onFx(() -> List.copyOf(table.getItems()));
    }

    private static String cell(int column, Reservation r) {
        int row = table.getItems().indexOf(r);
        Object value = table.getColumns().get(column).getCellData(row);
        return value == null ? null : value.toString();
    }

    private static <T> ComboBox<T> combo(Parent root, String id) {
        return (ComboBox<T>) root.lookup(id);
    }

    private static ListView<Review> reviewList(Parent form) {
        return (ListView<Review>) form.lookup("#reviewsList");
    }

    private static boolean shown(Parent root, String id) {
        Node node = root.lookup(id);
        return node.isVisible() && node.isManaged();
    }

    private static String label(Parent root, String id) {
        return ((Label) root.lookup(id)).getText();
    }

    private static boolean red(Parent root, String id) {
        return root.lookup(id).getStyleClass().contains("error");
    }

    private static Window window(Parent root) throws Exception {
        return onFx(() -> root.getScene().getWindow());
    }

    private static Button button(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("No button '" + text + "'");
    }

    private static Button buttonById(Parent root, String id) {
        return (Button) root.lookup(id);
    }

    // ----------------------------------------------------------------- JavaFX plumbing

    private static <T> T onFx(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    private static void clickLater(Parent root, String text) throws Exception {
        Button b = onFx(() -> button(root, text));
        Platform.runLater(b::fire);
    }

    private static void clickNow(Parent root, String text) throws Exception {
        onFx(() -> {
            button(root, text).fire();
            return null;
        });
    }

    private static Stage awaitWindow(Predicate<Stage> test) throws Exception {
        for (int i = 0; i < 50; i++) {
            Stage found = onFx(() -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof Stage s && s.isShowing() && s.getScene() != null && test.test(s)) {
                        return s;
                    }
                }
                return null;
            });
            if (found != null) {
                return found;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private static DialogPane awaitDialog() throws Exception {
        Stage s = awaitWindow(st -> st.getScene().getRoot() instanceof DialogPane);
        return s == null ? null : (DialogPane) s.getScene().getRoot();
    }

    private static Stage awaitForm(String title) throws Exception {
        return awaitWindow(st -> title.equals(st.getTitle()) && !(st.getScene().getRoot() instanceof DialogPane));
    }

    private static boolean waitClosed(Window window) throws Exception {
        if (window == null) {
            return false;
        }
        for (int i = 0; i < 50; i++) {
            if (!onFx(window::isShowing)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static void answer(DialogPane dialog, String text) throws Exception {
        if (dialog == null) {
            check("expected a dialog to answer '" + text + "'", false);
            return;
        }
        Window window = onFx(() -> dialog.getScene().getWindow());
        onFx(() -> {
            var type = dialog.getButtonTypes().stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
            ((Button) dialog.lookupButton(type)).fire();
            return null;
        });
        waitClosed(window);
    }

    private static void title(String text) {
        System.out.println();
        System.out.println("== " + text + " ==");
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }
}
