import com.example.petregistry.MainApp;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Species;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.UserService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * M6 "Done when": create / edit / delete / filter work; empty or bad input shows errors;
 * deleting a pet with reservations shows the rule's message.
 * Drives the real Pets tab and form from code: types, clicks, double-clicks and answers dialogs.
 */
@SuppressWarnings("unchecked")
public class M6Check {

    private static int pass;
    private static int fail;

    private static Stage main;
    private static Parent tab;
    private static TableView<Pet> table;

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);

        main = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            return s;
        });
        tab = onFx(() -> {
            TabPane tabs = (TabPane) ((BorderPane) main.getScene().getRoot()).getCenter();
            Tab pets = tabs.getTabs().stream().filter(t -> t.getText().equals("Gyvūnai")).findFirst().orElseThrow();
            tabs.getSelectionModel().select(pets);
            return (Parent) pets.getContent();
        });
        table = onFx(() -> (TableView<Pet>) tab.lookup("#table"));
        Thread.sleep(300); // let JavaFX draw the table once

        PetOwner anna = owner("anna");
        PetOwner jonas = owner("jonas");
        PetOwner greta = owner("greta");
        Pet reksas = pet("Reksas");

        // ============================================================ table
        title("table");
        onFx(() -> {
            check("shows all 5 seed pets", table.getItems().size() == 5);
            check("columns: Vardas, Rūšis, Dydis, Gimimo data, Svoris (kg), Šeimininkas",
                    table.getColumns().stream().map(TableColumn::getText).toList()
                            .equals(List.of("Vardas", "Rūšis", "Dydis", "Gimimo data", "Svoris (kg)", "Šeimininkas")));
            int row = table.getItems().indexOf(reksas);
            check("Reksas: name cell 'Reksas'", "Reksas".equals(cell(0, row)));
            check("Reksas: species cell shows the Lithuanian label 'Šuo'", "Šuo".equals(cell(1, row)));
            check("Reksas: owner cell = Anna's full name (lambda cell factory)", anna.getFullName().equals(cell(5, row)));
            check("species filter has 5 species", combo(tab, "#speciesFilter").getItems().size() == 5);
            check("owner filter has the 3 owners", combo(tab, "#ownerFilter").getItems().equals(List.of(anna, jonas, greta)));
            check("at start the species box shows its prompt 'Visos'", "Visos".equals(shownText(combo(tab, "#speciesFilter"))));
            return null;
        });

        // ============================================================ filters
        title("filters");
        search("rek", null, null);
        check("name 'rek' → Reksas", names().equals(List.of("Reksas")));
        search("REK", null, null);
        check("name 'REK' (other case) → Reksas", names().equals(List.of("Reksas")));
        onFx(() -> {
            TextField name = (TextField) tab.lookup("#nameFilter");
            name.setText("mur");
            name.fireEvent(new ActionEvent()); // = pressing Enter in the field
            return null;
        });
        check("Enter in the name field searches: 'mur' → Murka", names().equals(List.of("Murka")));
        search("", Species.CAT, null);
        check("species Katė → Murka", names().equals(List.of("Murka")));
        check("the species box shows the chosen 'Katė'", "Katė".equals(onFx(() -> shownText(combo(tab, "#speciesFilter")))));
        search("", null, jonas);
        check("owner Jonas → Bimas, Žydrukas", names().equals(List.of("Bimas", "Žydrukas")));
        search("", Species.DOG, jonas);
        check("species Šuo + owner Jonas → Bimas", names().equals(List.of("Bimas")));
        search("zzz", null, null);
        check("no match → empty table", names().isEmpty());
        check("empty table shows 'Gyvūnų nerasta'",
                onFx(() -> "Gyvūnų nerasta".equals(((Label) table.getPlaceholder()).getText())));
        search("", Species.DOG, jonas);
        clickNow(tab, "Rodyti visus");
        Thread.sleep(200);
        onFx(() -> {
            check("Rodyti visus → all 5 pets", table.getItems().size() == 5);
            check("Rodyti visus clears the name", ((TextField) tab.lookup("#nameFilter")).getText().isEmpty());
            check("Rodyti visus clears species and owner",
                    combo(tab, "#speciesFilter").getValue() == null && combo(tab, "#ownerFilter").getValue() == null);
            check("cleared species box shows its prompt 'Visos'", "Visos".equals(shownText(combo(tab, "#speciesFilter"))));
            check("cleared owner box shows its prompt 'Visi'", "Visi".equals(shownText(combo(tab, "#ownerFilter"))));
            return null;
        });

        // ============================================================ nothing selected
        title("Redaguoti / Šalinti with nothing selected");
        onFx(() -> {
            table.getSelectionModel().clearSelection();
            return null;
        });
        for (String button : List.of("Redaguoti", "Šalinti")) {
            clickLater(tab, button);
            DialogPane info = awaitDialog();
            check(button + " → info 'Pirmiausia pasirinkite gyvūną lentelėje.'",
                    info != null && "Pirmiausia pasirinkite gyvūną lentelėje.".equals(onFx(info::getContentText)));
            answer(info, "OK");
        }

        // ============================================================ new pet
        title("Naujas: the form");
        clickLater(tab, "Naujas");
        Stage form = awaitForm("Naujas gyvūnas");
        check("form 'Naujas gyvūnas' opened", form != null);
        Parent f = onFx(() -> form.getScene().getRoot());
        onFx(() -> {
            check("form is modal and opens on top of the main window",
                    form.getModality() == Modality.WINDOW_MODAL && form.getOwner() == main);
            check("all fields start empty", text(f, "#nameField").isEmpty() && text(f, "#weightField").isEmpty()
                    && combo(f, "#speciesBox").getValue() == null && combo(f, "#ownerBox").getValue() == null
                    && date(f).getValue() == null);
            check("species box: 5, size box: 3, owner box: the 3 owners",
                    combo(f, "#speciesBox").getItems().size() == 5 && combo(f, "#sizeBox").getItems().size() == 3
                            && combo(f, "#ownerBox").getItems().equals(List.of(anna, jonas, greta)));
            check("Enter = Išsaugoti, Esc = Atšaukti",
                    ((Button) f.lookup("#saveButton")).isDefaultButton() && ((Button) f.lookup("#cancelButton")).isCancelButton());
            check("no photo yet", ((ImageView) f.lookup("#photoView")).getImage() == null
                    && "Nuotraukos nėra".equals(((Label) f.lookup("#photoLabel")).getText()));
            return null;
        });

        title("Naujas: save an empty form");
        String errors = save(f);
        check("one error alert with all 5 messages", errors != null && errors.split("\n").length == 5
                && errors.contains("Įveskite gyvūno vardą") && errors.contains("Pasirinkite rūšį")
                && errors.contains("Pasirinkite dydį") && errors.contains("Svoris turi būti daugiau nei 0")
                && errors.contains("Pasirinkite šeimininką"));
        check("red: name, species, size, weight, owner", onFx(() -> red(f, "#nameField") && red(f, "#speciesBox")
                && red(f, "#sizeBox") && red(f, "#weightField") && red(f, "#ownerBox")));
        check("not red: birth date (it is optional)", onFx(() -> !red(f, "#birthDatePicker")));
        check("nothing was saved", Repositories.pets().findAll().size() == 5);

        title("Naujas: bad weight text");
        onFx(() -> {
            setText(f, "#nameField", "Testukas");
            combo(f, "#speciesBox").setValue(Species.DOG);
            combo(f, "#sizeBox").setValue(PetSize.SMALL);
            combo(f, "#ownerBox").setValue(greta);
            setText(f, "#weightField", "abc");
            return null;
        });
        errors = save(f);
        check("'abc' → only 'Svoris turi būti skaičius, pvz. 4,5'", "Svoris turi būti skaičius, pvz. 4,5".equals(errors));
        check("only the weight is red now (old red marks are cleared)",
                onFx(() -> red(f, "#weightField") && !red(f, "#nameField") && !red(f, "#ownerBox")));
        onFx(() -> setText(f, "#weightField", "NaN"));
        check("'NaN' → not a number", "Svoris turi būti skaičius, pvz. 4,5".equals(save(f)));
        onFx(() -> setText(f, "#weightField", "500"));
        check("'500' → the service's range message", String.valueOf(save(f)).startsWith("Svoris turi būti daugiau nei 0"));
        onFx(() -> setText(f, "#weightField", "-1"));
        check("'-1' → the service's range message", String.valueOf(save(f)).startsWith("Svoris turi būti daugiau nei 0"));

        title("Naujas: birth date in the future");
        onFx(() -> {
            setText(f, "#weightField", "4,5");
            date(f).setValue(LocalDate.now().plusDays(1));
            return null;
        });
        check("tomorrow → 'Gimimo data negali būti ateityje'", "Gimimo data negali būti ateityje".equals(save(f)));
        check("the birth date is red", onFx(() -> red(f, "#birthDatePicker")));
        clickNow(f, "✕");
        check("✕ clears the date", onFx(() -> date(f).getValue() == null));

        title("Naujas: valid input is saved");
        onFx(() -> {
            date(f).setValue(LocalDate.of(2020, 5, 1));
            ((TextArea) f.lookup("#notesArea")).setText("  Labai draugiškas  ");
            return null;
        });
        clickLater(f, "Išsaugoti");
        check("the form closes", waitClosed(form));
        Pet testukas = pet("Testukas");
        check("6 pets stored", Repositories.pets().findAll().size() == 6);
        check("the table shows it, and it is selected",
                onFx(() -> table.getItems().contains(testukas) && table.getSelectionModel().getSelectedItem() == testukas));
        check("values: Šuo, Mažas, 2020-05-01, '4,5' → 4.5 kg",
                testukas.getSpecies() == Species.DOG && testukas.getSize() == PetSize.SMALL
                        && LocalDate.of(2020, 5, 1).equals(testukas.getBirthDate()) && testukas.getWeightKg() == 4.5);
        check("notes are trimmed", "Labai draugiškas".equals(testukas.getNotes()));
        check("linked both ways: owner Greta, and Greta's list has it",
                testukas.getOwner() == greta && greta.getPets().contains(testukas));

        // ============================================================ edit
        title("Redaguoti: a refused edit leaves the stored pet unchanged");
        select(testukas);
        clickLater(tab, "Redaguoti");
        Stage edit = awaitForm("Redaguoti gyvūną");
        check("form 'Redaguoti gyvūną' opened", edit != null);
        Parent e = onFx(() -> edit.getScene().getRoot());
        check("fields are filled: Testukas, Šuo, Mažas, 2020-05-01, '4.5', Greta, notes",
                onFx(() -> text(e, "#nameField").equals("Testukas") && combo(e, "#speciesBox").getValue() == Species.DOG
                        && combo(e, "#sizeBox").getValue() == PetSize.SMALL
                        && LocalDate.of(2020, 5, 1).equals(date(e).getValue()) && text(e, "#weightField").equals("4.5")
                        && combo(e, "#ownerBox").getValue() == greta
                        && "Labai draugiškas".equals(((TextArea) e.lookup("#notesArea")).getText())));
        onFx(() -> {
            setText(e, "#nameField", "   ");
            setText(e, "#weightField", "500");
            return null;
        });
        errors = save(e);
        check("2 errors: name and weight", errors != null && errors.split("\n").length == 2);
        clickNow(e, "Atšaukti");
        check("Atšaukti closes the form", waitClosed(edit));
        check("stored pet still 'Testukas' with 4.5 kg (the draft was checked, not the real pet)",
                "Testukas".equals(testukas.getName()) && testukas.getWeightKg() == 4.5);

        title("Redaguoti by double-click: change name and owner");
        doubleClick(testukas);
        Stage edit2 = awaitForm("Redaguoti gyvūną");
        check("double-click opened the edit form", edit2 != null);
        Parent e2 = onFx(() -> edit2.getScene().getRoot());
        onFx(() -> {
            setText(e2, "#nameField", "Testukas2");
            combo(e2, "#ownerBox").setValue(jonas);
            return null;
        });
        clickLater(e2, "Išsaugoti");
        check("the form closes", waitClosed(edit2));
        check("renamed to Testukas2", "Testukas2".equals(testukas.getName()));
        check("moved: owner Jonas, in Jonas's list, gone from Greta's",
                testukas.getOwner() == jonas && jonas.getPets().contains(testukas) && !greta.getPets().contains(testukas));
        check("the table row shows the new name and Jonas's full name", onFx(() -> {
            int row = table.getItems().indexOf(testukas);
            return "Testukas2".equals(cell(0, row)) && jonas.getFullName().equals(cell(5, row));
        }));

        title("Redaguoti: Atšaukti changes nothing");
        select(testukas);
        clickLater(tab, "Redaguoti");
        Stage edit3 = awaitForm("Redaguoti gyvūną");
        Parent e3 = onFx(() -> edit3.getScene().getRoot());
        onFx(() -> setText(e3, "#nameField", "XYZ"));
        clickNow(e3, "Atšaukti");
        waitClosed(edit3);
        check("still 'Testukas2'", "Testukas2".equals(testukas.getName()));

        // ============================================================ photo
        title("photo preview (FileChooser itself is a native window: test it by hand)");
        File png = File.createTempFile("m6-photo-", ".png");
        png.deleteOnExit();
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", png);
        testukas.setPhotoPath(png.getAbsolutePath()); // as if chosen earlier with the FileChooser
        select(testukas);
        clickLater(tab, "Redaguoti");
        Stage photoForm = awaitForm("Redaguoti gyvūną");
        Parent p = onFx(() -> photoForm.getScene().getRoot());
        check("the stored photo is shown, with its file name", onFx(() ->
                ((ImageView) p.lookup("#photoView")).getImage() != null
                        && png.getName().equals(((Label) p.lookup("#photoLabel")).getText())));
        clickNow(p, "Išvalyti");
        check("Išvalyti removes the preview", onFx(() ->
                ((ImageView) p.lookup("#photoView")).getImage() == null
                        && "Nuotraukos nėra".equals(((Label) p.lookup("#photoLabel")).getText())));
        check("…but nothing is stored before Išsaugoti", png.getAbsolutePath().equals(testukas.getPhotoPath()));
        clickLater(p, "Išsaugoti");
        waitClosed(photoForm);
        check("after Išsaugoti the photo path is cleared", testukas.getPhotoPath() == null);

        testukas.setPhotoPath(new File(png.getParentFile(), "no-such-photo.png").getAbsolutePath());
        select(testukas);
        clickLater(tab, "Redaguoti");
        Stage missing = awaitForm("Redaguoti gyvūną");
        Parent m = onFx(() -> missing.getScene().getRoot());
        check("a missing file shows 'Failas nerastas: …' and no crash", onFx(() ->
                ((ImageView) m.lookup("#photoView")).getImage() == null
                        && ((Label) m.lookup("#photoLabel")).getText().startsWith("Failas nerastas")));
        clickNow(m, "Atšaukti");
        waitClosed(missing);
        testukas.setPhotoPath(null);

        // ============================================================ delete
        title("Šalinti: answer 'Ne'");
        select(testukas);
        clickLater(tab, "Šalinti");
        DialogPane ask = awaitDialog();
        check("asks 'Ar tikrai pašalinti gyvūną „Testukas2“?'",
                ask != null && "Ar tikrai pašalinti gyvūną „Testukas2“?".equals(onFx(ask::getContentText)));
        answer(ask, "Ne");
        check("still there", Repositories.pets().findAll().contains(testukas));

        title("Šalinti: a pet without reservations");
        new UserService().create(newOwner(), "Slaptazodis1", "Slaptazodis1"); // to see that refresh() reloads owners
        select(testukas);
        clickLater(tab, "Šalinti");
        answer(awaitDialog(), "Taip");
        Thread.sleep(200);
        check("removed from storage", !Repositories.pets().findAll().contains(testukas));
        check("removed from Jonas's list", !jonas.getPets().contains(testukas));
        check("removed from the table", onFx(() -> table.getItems().size() == 5 && !table.getItems().contains(testukas)));
        check("refresh() also reloaded the owner filter (now 4 owners, with Petras)", onFx(() ->
                combo(tab, "#ownerFilter").getItems().size() == 4));

        title("Šalinti: a pet WITH reservations (the delete rule)");
        select(reksas);
        clickLater(tab, "Šalinti");
        answer(awaitDialog(), "Taip");
        DialogPane refused = awaitDialog();
        String message = refused == null ? "" : onFx(refused::getContentText);
        check("error alert shows the rule's message", message.startsWith("Gyvūno „Reksas“ pašalinti negalima")
                && message.contains("rezervacijų"));
        check("the alert's title is 'Pašalinti negalima'", refused != null
                && "Pašalinti negalima".equals(onFx(() -> ((Stage) refused.getScene().getWindow()).getTitle())));
        answer(refused, "OK");
        check("Reksas is still stored and shown",
                Repositories.pets().findAll().contains(reksas) && onFx(() -> table.getItems().contains(reksas)));

        title("Naujas: Atšaukti adds nothing");
        clickLater(tab, "Naujas");
        Stage cancelled = awaitForm("Naujas gyvūnas");
        Parent c = onFx(() -> cancelled.getScene().getRoot());
        onFx(() -> setText(c, "#nameField", "Niekas"));
        clickNow(c, "Atšaukti");
        waitClosed(cancelled);
        check("still 5 pets", Repositories.pets().findAll().size() == 5);

        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    // ----------------------------------------------------------------- app actions

    private static void search(String name, Species species, PetOwner owner) throws Exception {
        onFx(() -> {
            ((TextField) tab.lookup("#nameFilter")).setText(name);
            combo(tab, "#speciesFilter").setValue(species);
            combo(tab, "#ownerFilter").setValue(owner);
            button(tab, "Ieškoti").fire();
            return null;
        });
    }

    /** Clicks Išsaugoti and returns the error alert's text (after closing it), or null if no alert came. */
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

    private static void select(Pet pet) throws Exception {
        onFx(() -> {
            table.getSelectionModel().select(pet);
            return null;
        });
    }

    private static void doubleClick(Pet pet) throws Exception {
        Thread.sleep(200);
        TableRow<Pet> row = onFx(() -> {
            table.layout();
            for (Node node : table.lookupAll(".table-row-cell")) {
                TableRow<Pet> r = (TableRow<Pet>) node;
                if (r.getItem() == pet) {
                    return r;
                }
            }
            return null;
        });
        check("found the table row to double-click", row != null);
        if (row != null) {
            Platform.runLater(() -> row.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 2, false, false, false, false, true, false, false, true, false, false, null)));
        }
    }

    private static PetOwner newOwner() {
        PetOwner o = new PetOwner();
        o.setUsername("petras");
        o.setFirstName("Petras");
        o.setLastName("Petraitis");
        o.setEmail("petras@gmail.com");
        return o;
    }

    // ----------------------------------------------------------------- lookups

    private static PetOwner owner(String username) {
        return (PetOwner) Repositories.users().findByUsername(username).orElseThrow();
    }

    private static Pet pet(String name) {
        return Repositories.pets().findAll().stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }

    private static List<String> names() throws Exception {
        return onFx(() -> table.getItems().stream().map(Pet::getName).toList());
    }

    private static String cell(int column, int row) {
        Object value = table.getColumns().get(column).getCellData(row);
        return value == null ? null : value.toString();
    }

    private static <T> ComboBox<T> combo(Parent root, String id) {
        return (ComboBox<T>) root.lookup(id);
    }

    private static DatePicker date(Parent form) {
        return (DatePicker) form.lookup("#birthDatePicker");
    }

    private static String text(Parent root, String id) {
        String t = ((TextField) root.lookup(id)).getText();
        return t == null ? "" : t;
    }

    private static Void setText(Parent root, String id, String value) {
        ((TextField) root.lookup(id)).setText(value);
        return null;
    }

    private static boolean red(Parent root, String id) {
        return root.lookup(id).getStyleClass().contains("error");
    }

    /** The text a ComboBox shows right now: the text of its button cell (the visible one, not the popup's). */
    private static String shownText(ComboBox<?> box) {
        box.applyCss();
        box.layout();
        ListCell<?> cell = box.getButtonCell();
        return cell == null ? null : cell.getText();
    }

    private static Button button(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("No button '" + text + "'");
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

    /** Clicks and returns at once: used when the click opens a window that waits (showAndWait). */
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

    /** Presses a dialog button by its text ("OK", "Taip", "Ne") and waits until the dialog is gone. */
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
