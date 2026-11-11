import com.example.petregistry.MainApp;
import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Reservation;
import com.example.petregistry.model.ReservationStatus;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.Service;
import com.example.petregistry.model.ServiceLocation;
import com.example.petregistry.model.ServiceStatus;
import com.example.petregistry.model.ServiceType;
import com.example.petregistry.model.Species;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * M11 + M13 "Done when", through the real GUI:
 *  phase "first":  Prisijungti… (wrong password, then right) → every tab does create / edit / delete / filter
 *                  against pet_registry_test → Atsijungti goes back to memory.
 *  phase "second": a NEW JVM (= the app restarted) connects again: the data is still there.
 */
@SuppressWarnings("unchecked")
public class M13Check {

    static final String URL = "jdbc:mysql://localhost:3306/pet_registry_test";
    static int pass;
    static int fail;
    static final AtomicInteger uncaught = new AtomicInteger();
    static Stage main;
    static TabPane tabs;
    static Label status;

    public static void main(String[] args) {
        try {
            boot();
            if (args.length > 0 && args[0].equals("second")) {
                second();
            } else {
                first();
            }
            check("no uncaught errors", uncaught.get() == 0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.out.println("CRASHED: " + t + "  (PASSED " + pass + ", FAILED " + fail + " so far)");
            System.exit(2);
        }
        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    static void boot() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);
        main = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
                uncaught.incrementAndGet();
                System.out.println("  UNCAUGHT: " + e);
                e.printStackTrace(System.out);
            });
            return s;
        });
        tabs = onFx(() -> (TabPane) ((BorderPane) main.getScene().getRoot()).getCenter());
        status = onFx(() -> (Label) main.getScene().getRoot().lookup("#statusLabel"));
        Thread.sleep(300);
    }

    // ================================================================= phase 1

    static void first() throws Exception {
        title("M11: start in memory, menu state");
        check("status 'Duomenys atmintyje'", "Duomenys atmintyje".equals(onFx(status::getText)));
        check("Prisijungti… enabled, Atsijungti disabled", onFx(() -> !menuItem("Prisijungti…").isDisable()
                && menuItem("Atsijungti").isDisable()));

        title("M11: Prisijungti… with a wrong password");
        Platform.runLater(() -> menuItem("Prisijungti…").fire());
        DialogPane login = awaitDialog();
        check("dialog has XAMPP defaults (URL, root)", login != null && onFx(() ->
                ((TextField) login.lookup("#urlField")).getText().equals("jdbc:mysql://localhost:3306/pet_registry")
                        && ((TextField) login.lookup("#userField")).getText().equals("root")));
        onFx(() -> {
            ((TextField) login.lookup("#urlField")).setText(URL);
            ((PasswordField) login.lookup("#passwordField")).setText("neteisingas");
            return null;
        });
        clickDialogLater(login, "Prisijungti");
        DialogPane error = awaitWindowDialog(d -> d != login);
        check("wrong password → error alert 'Nepavyko prisijungti…'", error != null
                && onFx(error::getContentText).startsWith("Nepavyko prisijungti"));
        answer(error, "OK");
        check("…and the login dialog stays open, still in memory", onFx(() -> login.getScene().getWindow().isShowing())
                && !DatabaseManager.isConnected() && "Duomenys atmintyje".equals(onFx(status::getText)));
        onFx(() -> {
            ((PasswordField) login.lookup("#passwordField")).setText("");
            return null;
        });
        answer(login, "Prisijungti");
        check("right password → connected, status 'Prisijungta prie DB: " + URL + "'", DatabaseManager.isConnected()
                && Repositories.isDatabase() && onFx(status::getText).equals("Prisijungta prie DB: " + URL));
        check("menu switched: Prisijungti… disabled, Atsijungti enabled", onFx(() -> menuItem("Prisijungti…").isDisable()
                && !menuItem("Atsijungti").isDisable()));
        Parent users = tab("Vartotojai");
        check("Vartotojai tab now shows the DB (only admin)", onFx(() -> rows(users).size()) == 1);
        check("Rezervacijos tab reloaded too (empty DB)", onFx(() -> rows(tabNoSelect("Rezervacijos")).size()) == 0);

        // ------------------------------------------------------------ Companies + services (inside the company)
        title("M13 Įmonės: create, add a service inside, edit, filter");
        Parent comps = tab("Įmonės");
        clickLater(comps, "Nauja");
        Stage cForm = awaitForm("Nauja įmonė");
        Parent cf = root(cForm);
        onFx(() -> {
            text(cf, "#nameField", "Letenėlė");
            text(cf, "#codeField", "123456789");
            text(cf, "#cityField", "Vilnius");
            return null;
        });
        check("save new company: no error, window stays open", save(cf) == null && onFx(cForm::isShowing));
        Company letenele = Repositories.companies().findByCode("123456789").orElseThrow();
        check("company row is in the DB", "Letenėlė".equals(letenele.getName()));
        clickLater(cf, "Pridėti paslaugą");
        Stage sForm = awaitForm("Nauja paslauga");
        Parent sf = root(sForm);
        onFx(() -> {
            check("service form: company fixed", ((ComboBox<Company>) sf.lookup("#companyBox")).isDisabled());
            text(sf, "#titleField", "Šunų vedžiojimas");
            ((ComboBox<ServiceType>) sf.lookup("#typeBox")).setValue(ServiceType.DOG_WALKING);
            text(sf, "#priceField", "15,50");
            ((ComboBox<ServiceLocation>) sf.lookup("#locationBox")).setValue(ServiceLocation.OUTDOORS);
            checkBox(sf, "Šuo").setSelected(true);
            checkBox(sf, "Vidutinis").setSelected(true);
            checkBox(sf, "Didelis").setSelected(true);
            return null;
        });
        check("save service: closes", save(sf) == null && waitClosed(sForm));
        Thread.sleep(300); // the company form reloads its list right after showAndWait() returns
        check("service list in the company form shows it", onFx(() -> ((ListView<?>) cf.lookup("#servicesList")).getItems().size()) == 1);
        Service walk = Repositories.services().findAll().get(0);
        check("service in DB with price 15.50, species DOG, sizes MEDIUM+LARGE, company Letenėlė",
                walk.getPrice().compareTo(new BigDecimal("15.50")) == 0 && walk.getAllowedSpecies().equals(java.util.Set.of(Species.DOG))
                        && walk.getAllowedSizes().equals(java.util.Set.of(PetSize.MEDIUM, PetSize.LARGE)) && walk.getCompany().equals(letenele));
        clickNow(cf, "Uždaryti");
        waitClosed(cForm);
        check("Įmonės table: 1 row, 'Paslaugų' = 1", onFx(() -> rows(comps).size() == 1
                && "1".equals(cell(comps, 5, 0))));
        // a second company, to filter
        clickLater(comps, "Nauja");
        Stage c2Form = awaitForm("Nauja įmonė");
        Parent c2 = root(c2Form);
        onFx(() -> {
            text(c2, "#nameField", "Uodegėlė");
            text(c2, "#codeField", "987654321");
            text(c2, "#cityField", "Kaunas");
            return null;
        });
        save(c2);
        clickNow(c2, "Uždaryti");
        waitClosed(c2Form);
        onFx(() -> {
            text(comps, "#cityField".replace("Field", "Filter"), "kaun");
            button(comps, "Ieškoti").fire();
            return null;
        });
        check("filter city 'kaun' (SQL LIKE) → Uodegėlė only", onFx(() -> rows(comps).size() == 1
                && "Uodegėlė".equals(cell(comps, 0, 0))));
        clickNow(comps, "Rodyti visus");
        check("Rodyti visus → 2", onFx(() -> rows(comps).size()) == 2);
        // edit by the table
        selectRow(comps, 1);
        clickLater(comps, "Redaguoti");
        Stage eForm = awaitForm("Redaguoti įmonę");
        Parent ef = root(eForm);
        onFx(() -> {
            text(ef, "#addressField", "Laisvės al. 10");
            return null;
        });
        check("edit company → closes", save(ef) == null && waitClosed(eForm));
        check("address saved in DB", "Laisvės al. 10".equals(Repositories.companies().findByCode("987654321").orElseThrow().getAddress()));

        // ------------------------------------------------------------ Users
        title("M13 Vartotojai: owner, caretaker, manager; edit; block; filter");
        newUser(users, Role.PET_OWNER, "anna", "Anna", "Kazlauskienė", f -> text(f, "#addressField", "Vilnius"));
        newUser(users, Role.CARETAKER, "tomas", "Tomas", "Petraitis", f -> text(f, "#experienceField", "4"));
        newUser(users, Role.COMPANY_MANAGER, "rasa", "Rasa", "Jonaitė",
                f -> ((ComboBox<Company>) f.lookup("#companyBox")).setValue(((ComboBox<Company>) f.lookup("#companyBox"))
                        .getItems().stream().filter(c -> c.getName().equals("Uodegėlė")).findFirst().orElseThrow()));
        check("4 users in the table and the DB", onFx(() -> rows(users).size()) == 4 && Repositories.users().findAll().size() == 4);
        User rasa = Repositories.users().findByUsername("rasa").orElseThrow();
        check("manager stored with company Uodegėlė", rasa instanceof com.example.petregistry.model.CompanyManager m
                && "Uodegėlė".equals(m.getCompany().getName()));
        onFx(() -> {
            text(users, "#textFilter", "kazl");
            button(users, "Ieškoti").fire();
            return null;
        });
        check("filter 'kazl' → Anna", onFx(() -> rows(users).size() == 1));
        selectRow(users, 0);
        clickNow(users, "Blokuoti");
        check("Blokuoti → saved in DB, row still selected after refresh",
                Repositories.users().findByUsername("anna").orElseThrow().isBlocked()
                        && onFx(() -> ((TableView<?>) users.lookup("#table")).getSelectionModel().getSelectedIndex()) == 0);
        clickNow(users, "Atblokuoti");
        clickNow(users, "Rodyti visus");

        title("M13 hire the caretaker inside the company form");
        Parent comps2 = tab("Įmonės");
        selectRow(comps2, 0);
        clickLater(comps2, "Redaguoti");
        Stage hForm = awaitForm("Redaguoti įmonę");
        Parent hf = root(hForm);
        onFx(() -> {
            ComboBox<Caretaker> free = (ComboBox<Caretaker>) hf.lookup("#freeCaretakerBox");
            free.setValue(free.getItems().get(0));
            button(hf, "Priskirti").fire();
            return null;
        });
        check("caretaker assigned list = Tomas", onFx(() -> ((ListView<?>) hf.lookup("#caretakersList")).getItems().size()) == 1);
        check("users.company_id saved", !((Caretaker) Repositories.users().findByUsername("tomas").orElseThrow()).isIndependent());
        clickNow(hf, "Uždaryti");
        waitClosed(hForm);

        // ------------------------------------------------------------ Pets
        title("M13 Gyvūnai: create, edit, filter");
        Parent petsTab = tab("Gyvūnai");
        newPet(petsTab, "Reksas", Species.DOG, PetSize.LARGE, "32,5");
        newPet(petsTab, "Murka", Species.CAT, PetSize.SMALL, "4,2");
        check("2 pets in table and DB", onFx(() -> rows(petsTab).size()) == 2 && Repositories.pets().findAll().size() == 2);
        check("owner column = Anna", onFx(() -> cell(petsTab, 5, 0)).equals("Anna Kazlauskienė"));
        onFx(() -> {
            ((ComboBox<Species>) petsTab.lookup("#speciesFilter")).setValue(Species.CAT);
            button(petsTab, "Ieškoti").fire();
            return null;
        });
        check("filter species Katė → Murka", onFx(() -> rows(petsTab).size() == 1 && "Murka".equals(cell(petsTab, 0, 0))));
        clickNow(petsTab, "Rodyti visus");
        selectRow(petsTab, 1);
        clickLater(petsTab, "Redaguoti");
        Stage pForm = awaitForm("Redaguoti gyvūną");
        Parent pf = root(pForm);
        onFx(() -> {
            text(pf, "#weightField", "4,8");
            return null;
        });
        check("edit Murka's weight → closes", save(pf) == null && waitClosed(pForm));
        check("weight 4.8 in DB", Repositories.pets().findAll().stream().anyMatch(p -> p.getName().equals("Murka") && p.getWeightKg() == 4.8));
        check("owner's pet list (Users → edit Anna) has 2 pets from the DB",
                ((PetOwner) Repositories.users().findByUsername("anna").orElseThrow()).getPets().size() == 2);

        title("M13 owner's pet list inside the Users form (fixed owner, DB copies)");
        Parent users2 = tab("Vartotojai");
        int annaIdx = onFx(() -> indexOf(users2, "anna"));
        selectRow(users2, annaIdx);
        clickLater(users2, "Redaguoti");
        Stage uForm = awaitForm("Redaguoti vartotoją");
        Parent uf = root(uForm);
        int listBefore = onFx(() -> ((ListView<?>) uf.lookup("#petsList")).getItems().size());
        clickLater(uf, "Pridėti gyvūną");
        Stage npForm = awaitForm("Naujas gyvūnas");
        Parent np = root(npForm);
        onFx(() -> {
            text(np, "#nameField", "Pūkas");
            ((ComboBox<Species>) np.lookup("#speciesBox")).setValue(Species.RODENT);
            ((ComboBox<PetSize>) np.lookup("#sizeBox")).setValue(PetSize.SMALL);
            text(np, "#weightField", "0,3");
            return null;
        });
        check("Pridėti gyvūną → saved", save(np) == null && waitClosed(npForm));
        Thread.sleep(300);
        check("the owner's pet list shows the new pet (" + (listBefore + 1) + ")",
                onFx(() -> ((ListView<?>) uf.lookup("#petsList")).getItems().size()) == listBefore + 1);
        onFx(() -> {
            ListView<Pet> list = (ListView<Pet>) uf.lookup("#petsList");
            list.getSelectionModel().select(list.getItems().stream().filter(p -> p.getName().equals("Pūkas")).findFirst().orElseThrow());
            return null;
        });
        clickLater(uf, "Pašalinti gyvūną");
        answer(awaitDialog(), "Taip");
        Thread.sleep(300);
        check("Pašalinti gyvūną → gone from the list and the DB", onFx(() -> ((ListView<?>) uf.lookup("#petsList")).getItems().size()) == listBefore
                && Repositories.pets().findAll().stream().noneMatch(p -> p.getName().equals("Pūkas")));
        clickNow(uf, "Atšaukti");
        waitClosed(uForm);

        // ------------------------------------------------------------ Reservations
        title("M13 Rezervacijos: create, status, review, filter");
        Parent resTab = tab("Rezervacijos");
        clickLater(resTab, "Nauja");
        Stage rForm = awaitForm("Nauja rezervacija");
        Parent rf = root(rForm);
        onFx(() -> {
            ComboBox<PetOwner> owner = (ComboBox<PetOwner>) rf.lookup("#ownerBox");
            owner.setValue(owner.getItems().get(0));
            ComboBox<Pet> pet = (ComboBox<Pet>) rf.lookup("#petBox");
            pet.setValue(pet.getItems().stream().filter(p -> p.getName().equals("Reksas")).findFirst().orElseThrow());
            ComboBox<Service> service = (ComboBox<Service>) rf.lookup("#serviceBox");
            service.setValue(service.getItems().get(0));
            ComboBox<Caretaker> caretaker = (ComboBox<Caretaker>) rf.lookup("#caretakerBox");
            check("caretaker list (from the DB) = Tomas", caretaker.getItems().size() == 1);
            caretaker.setValue(caretaker.getItems().get(0));
            ((DatePicker) rf.lookup("#datePicker")).setValue(LocalDate.now().plusDays(2));
            ((ComboBox<LocalTime>) rf.lookup("#timeBox")).setValue(LocalTime.of(10, 0));
            check("price label 15.50 €", "15.50 €".equals(((Label) rf.lookup("#priceLabel")).getText()));
            return null;
        });
        check("save reservation → closes", save(rf) == null && waitClosed(rForm));
        Reservation res = Repositories.reservations().findAll().get(0);
        check("reservation in DB: Anna/Reksas/walk/Tomas, end = +60, price 15.50", res.getOwner().getUsername().equals("anna")
                && res.getPet().getName().equals("Reksas") && res.getCaretaker().getUsername().equals("tomas")
                && res.getEnd().equals(res.getStart().plusMinutes(60)) && res.getPrice().compareTo(new BigDecimal("15.50")) == 0);
        for (ReservationStatus s : List.of(ReservationStatus.CONFIRMED, ReservationStatus.IN_PROGRESS, ReservationStatus.COMPLETED)) {
            selectRow(resTab, 0);
            clickLater(resTab, "Keisti būseną");
            DialogPane d = awaitDialog();
            onFx(() -> {
                ((ComboBox<ReservationStatus>) d.lookup(".combo-box")).setValue(s);
                return null;
            });
            answer(d, "Gerai");
        }
        check("status COMPLETED saved in DB", Repositories.reservations().findAll().get(0).getStatus() == ReservationStatus.COMPLETED);
        selectRow(resTab, 0);
        clickLater(resTab, "Redaguoti");
        Stage vForm = awaitForm("Redaguoti rezervaciją");
        Parent vf = root(vForm);
        clickLater(vf, "Pridėti atsiliepimą");
        Stage revForm = awaitForm("Naujas atsiliepimas");
        Parent rv = root(revForm);
        onFx(() -> {
            ComboBox<User> author = (ComboBox<User>) rv.lookup("#authorBox");
            author.setValue(author.getItems().get(0)); // the owner
            ComboBox<Object> target = (ComboBox<Object>) rv.lookup("#targetBox");
            target.setValue(target.getItems().get(0)); // the caretaker
            ((ComboBox<Integer>) rv.lookup("#ratingBox")).setValue(4);
            return null;
        });
        check("review saved → closes", save(rv) == null && waitClosed(revForm));
        check("review list shows it (1)", onFx(() -> ((ListView<?>) vf.lookup("#reviewsList")).getItems().size()) == 1);
        clickLater(vf, "Pridėti atsiliepimą");
        Stage rev2 = awaitForm("Naujas atsiliepimas");
        Parent rv2 = root(rev2);
        onFx(() -> {
            ComboBox<User> author = (ComboBox<User>) rv2.lookup("#authorBox");
            author.setValue(author.getItems().get(0));
            ComboBox<Object> target = (ComboBox<Object>) rv2.lookup("#targetBox");
            target.setValue(target.getItems().get(1)); // the company
            ((ComboBox<Integer>) rv2.lookup("#ratingBox")).setValue(2);
            return null;
        });
        check("second review by the owner refused (DB data)", String.valueOf(save(rv2)).contains("jau paliko"));
        clickNow(rv2, "Atšaukti");
        waitClosed(rev2);
        clickNow(vf, "Uždaryti");
        waitClosed(vForm);
        check("Tomas's average (SQL AVG) = 4.0", ((Caretaker) Repositories.users().findByUsername("tomas").orElseThrow()).getAverageRating() == 4.0);
        onFx(() -> {
            ((ComboBox<ReservationStatus>) resTab.lookup("#statusFilter")).setValue(ReservationStatus.PENDING);
            button(resTab, "Ieškoti").fire();
            return null;
        });
        check("filter Laukiama → nothing", onFx(() -> rows(resTab).isEmpty()));
        clickNow(resTab, "Rodyti visus");

        // ------------------------------------------------------------ delete rules in the GUI
        title("M13 delete rules against the DB");
        Parent svcTab = tab("Paslaugos");
        selectRow(svcTab, 0);
        clickLater(svcTab, "Šalinti");
        answer(awaitDialog(), "Taip");
        DialogPane info = awaitDialog();
        check("booked service → info 'pažymėta kaip neaktyvi'", info != null && onFx(info::getContentText).contains("neaktyvi"));
        answer(info, "OK");
        check("status INACTIVE in DB", Repositories.services().findAll().get(0).getStatus() == ServiceStatus.INACTIVE);
        Parent pets2 = tab("Gyvūnai");
        selectRow(pets2, 0); // Reksas has a reservation
        clickLater(pets2, "Šalinti");
        answer(awaitDialog(), "Taip");
        DialogPane refused = awaitDialog();
        check("pet with a reservation → 'Pašalinti negalima'", refused != null && onFx(refused::getContentText).contains("rezervacijų"));
        answer(refused, "OK");
        selectRow(pets2, 1); // Murka: no reservations
        clickLater(pets2, "Šalinti");
        answer(awaitDialog(), "Taip");
        Thread.sleep(300);
        check("Murka deleted from DB and table", Repositories.pets().findAll().size() == 1 && onFx(() -> rows(pets2).size()) == 1);

        title("M11: Atsijungti → back to memory");
        Platform.runLater(() -> menuItem("Atsijungti").fire());
        Thread.sleep(800);
        check("disconnected, status 'Duomenys atmintyje', menu switched back", !DatabaseManager.isConnected()
                && !Repositories.isDatabase() && "Duomenys atmintyje".equals(onFx(status::getText))
                && onFx(() -> !menuItem("Prisijungti…").isDisable() && menuItem("Atsijungti").isDisable()));
        check("tabs show the in-memory seed data again (5 reservations)", onFx(() -> rows(tabNoSelect("Rezervacijos")).size()) == 5);
    }

    // ================================================================= phase 2 (new JVM)

    static void second() throws Exception {
        title("after an app restart: connect and find the data");
        Platform.runLater(() -> menuItem("Prisijungti…").fire());
        DialogPane login = awaitDialog();
        onFx(() -> {
            ((TextField) login.lookup("#urlField")).setText(URL);
            return null;
        });
        answer(login, "Prisijungti");
        check("connected", DatabaseManager.isConnected());
        Parent uT = tab("Vartotojai");
        check("Vartotojai: admin, anna, tomas, rasa", onFx(() -> rows(uT).size()) == 4);
        Parent cT = tab("Įmonės");
        check("Įmonės: 2, Letenėlė has its caretaker", onFx(() -> rows(cT).size() == 2 && "1".equals(cell(cT, 6, 0))));
        Parent sT = tab("Paslaugos");
        check("Paslaugos: 1, Neaktyvi", onFx(() -> rows(sT).size() == 1 && "Neaktyvi".equals(cell(sT, 6, 0))));
        Parent pT = tab("Gyvūnai");
        check("Gyvūnai: Reksas", onFx(() -> rows(pT).size() == 1 && "Reksas".equals(cell(pT, 0, 0))));
        Parent rT = tab("Rezervacijos");
        check("Rezervacijos: 1, Įvykdyta", onFx(() -> rows(rT).size() == 1 && "Įvykdyta".equals(cell(rT, 7, 0))));
        check("its review is still there", Repositories.reservations().findAll().get(0).getReviews().size() == 1);

        title("delete a reservation with its review (transaction) through the GUI");
        Parent resTab = tab("Rezervacijos");
        selectRow(resTab, 0);
        clickLater(resTab, "Šalinti");
        DialogPane q = awaitDialog();
        check("question mentions 1 review", q != null && onFx(q::getContentText).contains("atsiliepimai (1)"));
        answer(q, "Taip");
        Thread.sleep(300);
        check("reservation and review gone from the DB", Repositories.reservations().findAll().isEmpty()
                && Repositories.reviews().findAll().isEmpty());
        check("Tomas's average back to 0", ((Caretaker) Repositories.users().findByUsername("tomas").orElseThrow()).getAverageRating() == 0.0);

        title("delete the owner (with her pet) and a company (with its service) through the GUI");
        Parent users = tab("Vartotojai");
        int annaRow = onFx(() -> indexOf(users, "anna"));
        selectRow(users, annaRow);
        clickLater(users, "Šalinti");
        DialogPane qa = awaitDialog();
        check("question mentions her 1 pet", qa != null && onFx(qa::getContentText).contains("gyvūnai (1)"));
        answer(qa, "Taip");
        Thread.sleep(300);
        check("Anna and Reksas gone", Repositories.users().findByUsername("anna").isEmpty() && Repositories.pets().findAll().isEmpty());
        Parent comps = tab("Įmonės");
        selectRow(comps, 0); // Letenėlė: no manager, service not booked any more
        clickLater(comps, "Šalinti");
        answer(awaitDialog(), "Taip");
        Thread.sleep(300);
        check("Letenėlė and its service gone; Tomas stays, independent",
                Repositories.companies().findByCode("123456789").isEmpty() && Repositories.services().findAll().isEmpty()
                        && ((Caretaker) Repositories.users().findByUsername("tomas").orElseThrow()).isIndependent());
        Platform.runLater(() -> menuItem("Atsijungti").fire());
        Thread.sleep(500);
    }

    // ================================================================= helpers

    interface FormFill {
        void fill(Parent form);
    }

    static void newUser(Parent users, Role role, String username, String first, String last, FormFill extra) throws Exception {
        clickLater(users, "Naujas");
        Stage form = awaitForm("Naujas vartotojas");
        Parent f = root(form);
        onFx(() -> {
            ((ComboBox<Role>) f.lookup("#roleBox")).setValue(role);
            text(f, "#usernameField", username);
            text(f, "#emailField", username + "@test.lt");
            text(f, "#firstNameField", first);
            text(f, "#lastNameField", last);
            text(f, "#passwordField", "Slaptazodis1");
            text(f, "#passwordRepeatField", "Slaptazodis1");
            extra.fill(f);
            return null;
        });
        check("new " + role + " '" + username + "' saved", save(f) == null && waitClosed(form));
    }

    static void newPet(Parent petsTab, String name, Species species, PetSize size, String weight) throws Exception {
        clickLater(petsTab, "Naujas");
        Stage form = awaitForm("Naujas gyvūnas");
        Parent f = root(form);
        onFx(() -> {
            text(f, "#nameField", name);
            ((ComboBox<Species>) f.lookup("#speciesBox")).setValue(species);
            ((ComboBox<PetSize>) f.lookup("#sizeBox")).setValue(size);
            text(f, "#weightField", weight);
            ComboBox<PetOwner> owner = (ComboBox<PetOwner>) f.lookup("#ownerBox");
            owner.setValue(owner.getItems().get(0));
            return null;
        });
        check("new pet '" + name + "' saved", save(f) == null && waitClosed(form));
    }

    static Parent tab(String name) throws Exception {
        return onFx(() -> {
            Tab t = tabs.getTabs().stream().filter(x -> x.getText().equals(name)).findFirst().orElseThrow();
            tabs.getSelectionModel().select(t);
            return (Parent) t.getContent();
        });
    }

    static Parent tabNoSelect(String name) {
        return (Parent) tabs.getTabs().stream().filter(x -> x.getText().equals(name)).findFirst().orElseThrow().getContent();
    }

    static List<?> rows(Parent tab) {
        return List.copyOf(((TableView<?>) tab.lookup("#table")).getItems());
    }

    static String cell(Parent tab, int column, int row) {
        TableView<?> t = (TableView<?>) tab.lookup("#table");
        Object v = t.getColumns().get(column).getCellData(row);
        return v == null ? null : v.toString();
    }

    static int indexOf(Parent tab, String username) {
        List<?> items = rows(tab);
        for (int i = 0; i < items.size(); i++) {
            if (((User) items.get(i)).getUsername().equals(username)) {
                return i;
            }
        }
        return -1;
    }

    static void selectRow(Parent tab, int row) throws Exception {
        onFx(() -> {
            ((TableView<?>) tab.lookup("#table")).getSelectionModel().select(row);
            return null;
        });
    }

    static void text(Parent root, String id, String value) {
        ((javafx.scene.control.TextInputControl) root.lookup(id)).setText(value);
    }

    static CheckBox checkBox(Parent root, String text) {
        for (Node n : root.lookupAll(".check-box")) {
            if (n instanceof CheckBox c && text.equals(c.getText())) {
                return c;
            }
        }
        throw new IllegalStateException("No checkbox " + text);
    }

    static Parent root(Stage s) throws Exception {
        return onFx(() -> s.getScene().getRoot());
    }

    static MenuItem menuItem(String text) {
        MenuBar bar = (MenuBar) ((BorderPane) main.getScene().getRoot()).getTop();
        for (Menu m : bar.getMenus()) {
            for (MenuItem i : m.getItems()) {
                if (text.equals(i.getText())) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("No menu item " + text);
    }

    static String save(Parent form) throws Exception {
        clickLater(form, "Išsaugoti");
        DialogPane dialog = awaitDialog();
        if (dialog == null) {
            return null;
        }
        String text = onFx(dialog::getContentText);
        System.out.println("        (alert: " + text.replace("\n", " | ") + ")");
        answer(dialog, "OK");
        return text;
    }

    static Button button(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("No button '" + text + "'");
    }

    static void clickLater(Parent root, String text) throws Exception {
        Button b = onFx(() -> button(root, text));
        Platform.runLater(b::fire);
    }

    static void clickNow(Parent root, String text) throws Exception {
        onFx(() -> {
            button(root, text).fire();
            return null;
        });
    }

    static void clickDialogLater(DialogPane d, String text) throws Exception {
        Button b = onFx(() -> (Button) d.lookupButton(d.getButtonTypes().stream()
                .filter(t -> t.getText().equals(text)).findFirst().orElseThrow()));
        Platform.runLater(b::fire);
    }

    static <T> T onFx(Callable<T> task) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(task.call());
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        return result.get(20, TimeUnit.SECONDS);
    }

    static Stage awaitWindow(Predicate<Stage> test) throws Exception {
        for (int i = 0; i < 60; i++) {
            Stage found = onFx(() -> {
                Stage last = null;
                for (Window w : Window.getWindows()) {
                    if (w instanceof Stage s && s.isShowing() && s.getScene() != null && test.test(s)) {
                        last = s;
                    }
                }
                return last;
            });
            if (found != null) {
                return found;
            }
            Thread.sleep(100);
        }
        return null;
    }

    static DialogPane awaitDialog() throws Exception {
        Stage s = awaitWindow(st -> st.getScene().getRoot() instanceof DialogPane);
        return s == null ? null : (DialogPane) s.getScene().getRoot();
    }

    static DialogPane awaitWindowDialog(Predicate<DialogPane> test) throws Exception {
        Stage s = awaitWindow(st -> st.getScene().getRoot() instanceof DialogPane d && test.test(d));
        return s == null ? null : (DialogPane) s.getScene().getRoot();
    }

    static Stage awaitForm(String title) throws Exception {
        Stage s = awaitWindow(st -> title.equals(st.getTitle()) && !(st.getScene().getRoot() instanceof DialogPane));
        if (s == null) {
            throw new IllegalStateException("Form '" + title + "' did not open");
        }
        return s;
    }

    static boolean waitClosed(Window window) throws Exception {
        if (window == null) {
            return false;
        }
        for (int i = 0; i < 60; i++) {
            if (!onFx(window::isShowing)) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    static void answer(DialogPane dialog, String text) throws Exception {
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

    static void title(String text) {
        System.out.println();
        System.out.println("== " + text + " ==");
    }

    static void check(String name, boolean ok) {
        if (ok) {
            pass++;
            System.out.println("  PASS  " + name);
        } else {
            fail++;
            System.out.println("  FAIL  " + name);
        }
    }
}
