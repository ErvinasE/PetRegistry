import com.example.petregistry.MainApp;
import com.example.petregistry.model.Administrator;
import com.example.petregistry.model.Caretaker;
import com.example.petregistry.model.Company;
import com.example.petregistry.model.CompanyManager;
import com.example.petregistry.model.Pet;
import com.example.petregistry.model.PetOwner;
import com.example.petregistry.model.PetSize;
import com.example.petregistry.model.Role;
import com.example.petregistry.model.Species;
import com.example.petregistry.model.User;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.PetService;
import com.example.petregistry.service.UserService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * M7 "Done when": one user of each role can be created; a manager without a company is refused.
 * Also: role boxes, passwords, the owner's pet list, Blokuoti / Atblokuoti, delete rules, refresh on tab switch.
 * Drives the real Users tab and form from code.
 */
@SuppressWarnings("unchecked")
public class M7Check {

    private static int pass;
    private static int fail;

    private static Stage main;
    private static TabPane tabs;
    private static Parent tab;
    private static TableView<User> table;

    /** A crash in the test must end the program (the JavaFX thread would otherwise keep it alive). */
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
        tab = onFx(() -> (Parent) tabNamed("Vartotojai").getContent());
        table = onFx(() -> (TableView<User>) tab.lookup("#table"));
        Thread.sleep(300);

        PetOwner anna = (PetOwner) user("anna");
        PetOwner jonas = (PetOwner) user("jonas");
        Caretaker tomas = (Caretaker) user("tomas");
        Caretaker lukas = (Caretaker) user("lukas");
        CompanyManager rasa = (CompanyManager) user("rasa");
        Company letenele = Repositories.companies().findByCode("123456789").orElseThrow();
        int seedUsers = Repositories.users().findAll().size();

        // ============================================================ table
        title("table");
        onFx(() -> {
            check("Vartotojai is the first tab and is open", tabs.getSelectionModel().getSelectedItem() == tabNamed("Vartotojai"));
            check("shows all " + seedUsers + " seed users", table.getItems().size() == seedUsers);
            check("columns: Prisijungimo vardas, Vardas, pavardė, El. paštas, Telefonas, Rolė, Įmonė, Užblokuotas",
                    table.getColumns().stream().map(TableColumn::getText).toList().equals(List.of(
                            "Prisijungimo vardas", "Vardas, pavardė", "El. paštas", "Telefonas", "Rolė", "Įmonė", "Užblokuotas")));
            check("Anna: full name cell (property 'fullName' → getFullName())", anna.getFullName().equals(cell(1, anna)));
            check("Anna: role shows the label 'Augintinio šeimininkas'", "Augintinio šeimininkas".equals(cell(4, anna)));
            check("Anna: no company", "".equals(cell(5, anna)));
            check("Tomas (caretaker): company 'Letenėlė'", "Letenėlė".equals(cell(5, tomas)));
            check("Lukas (caretaker): 'Nepriklausomas'", "Nepriklausomas".equals(cell(5, lukas)));
            check("Rasa (manager): company 'Letenėlė'", "Letenėlė".equals(cell(5, rasa)));
            check("nobody is blocked", table.getItems().stream().allMatch(u -> "".equals(cell(6, u))));
            return null;
        });

        // ============================================================ filters
        title("filters");
        search("anna", null);
        check("text 'anna' → Anna", rows().equals(List.of(anna)));
        search("", Role.CARETAKER);
        check("role Prižiūrėtojas → the 3 caretakers",
                rows().size() == 3 && rows().stream().allMatch(u -> u instanceof Caretaker));
        search("tomas", Role.PET_OWNER);
        check("'tomas' + Augintinio šeimininkas → nothing", rows().isEmpty());
        clickNow(tab, "Rodyti visus");
        check("Rodyti visus → all users again", rows().size() == seedUsers);
        check("role box shows its prompt 'Visos' again", "Visos".equals(onFx(() -> shownText(combo(tab, "#roleFilter")))));

        // ============================================================ nothing selected
        title("buttons with nothing selected");
        onFx(() -> {
            table.getSelectionModel().clearSelection();
            return null;
        });
        for (String button : List.of("Redaguoti", "Šalinti", "Blokuoti")) {
            clickLater(tab, button);
            DialogPane info = awaitDialog();
            check(button + " → 'Pirmiausia pasirinkite vartotoją lentelėje.'",
                    info != null && "Pirmiausia pasirinkite vartotoją lentelėje.".equals(onFx(info::getContentText)));
            answer(info, "OK");
        }

        // ============================================================ new user: form basics
        title("Naujas: the form");
        Parent f = openNew();
        onFx(() -> {
            check("role box is enabled and empty", !combo(f, "#roleBox").isDisabled() && combo(f, "#roleBox").getValue() == null);
            check("role box offers the 4 roles", combo(f, "#roleBox").getItems().equals(List.of(Role.values())));
            check("no role box is shown yet", !shown(f, "#ownerFields") && !shown(f, "#caretakerFields") && !shown(f, "#managerFields"));
            check("password hint 'Bent 8 simboliai.'", "Bent 8 simboliai.".equals(label(f, "#passwordHint")));
            return null;
        });

        title("Naujas: save an empty form (all errors at once, even without a role)");
        String errors = save(f);
        check("6 messages: role, username, email, first name, last name, password", errors != null
                && errors.split("\n").length == 6 && errors.contains("Pasirinkite rolę")
                && errors.contains("Įveskite prisijungimo vardą") && errors.contains("Įveskite el. paštą")
                && errors.contains("Įveskite vardą") && errors.contains("Įveskite pavardę")
                && errors.contains("Slaptažodis turi būti bent 8 simbolių"));
        check("those 6 fields are red", onFx(() -> red(f, "#roleBox") && red(f, "#usernameField") && red(f, "#emailField")
                && red(f, "#firstNameField") && red(f, "#lastNameField") && red(f, "#passwordField")));

        title("Naujas: choosing a role shows only that role's fields (setVisible + setManaged)");
        onFx(() -> {
            combo(f, "#roleBox").setValue(Role.PET_OWNER);
            check("Augintinio šeimininkas → owner box only",
                    shown(f, "#ownerFields") && !shown(f, "#caretakerFields") && !shown(f, "#managerFields"));
            check("…but no pet list while creating (the owner isn't saved yet)", !shown(f, "#petsSection"));
            combo(f, "#roleBox").setValue(Role.CARETAKER);
            check("Prižiūrėtojas → caretaker box only",
                    !shown(f, "#ownerFields") && shown(f, "#caretakerFields") && !shown(f, "#managerFields"));
            check("a new caretaker is independent", label(f, "#caretakerCompanyLabel").startsWith("Nepriklausomas"));
            combo(f, "#roleBox").setValue(Role.COMPANY_MANAGER);
            check("Įmonės vadovas → manager box only, with both companies",
                    !shown(f, "#ownerFields") && !shown(f, "#caretakerFields") && shown(f, "#managerFields")
                            && combo(f, "#companyBox").getItems().size() == 2);
            combo(f, "#roleBox").setValue(Role.ADMIN);
            check("Administratorius → no extra box",
                    !shown(f, "#ownerFields") && !shown(f, "#caretakerFields") && !shown(f, "#managerFields"));
            check("hidden boxes take no space (managed = false)", !f.lookup("#ownerFields").isManaged());
            return null;
        });

        title("Naujas: field rules");
        fillCommon(f, Role.ADMIN, "anna", "nauja@pets.lt", "Slaptazodis1", "Slaptazodis1");
        check("username 'anna' → 'Toks prisijungimo vardas jau užimtas'", "Toks prisijungimo vardas jau užimtas".equals(save(f)));
        fillCommon(f, Role.ADMIN, "a b", "nauja@pets.lt", "Slaptazodis1", "Slaptazodis1");
        check("username 'a b' → the 3–30 letters/digits/_ rule", String.valueOf(save(f)).startsWith("Prisijungimo vardas: 3–30"));
        fillCommon(f, Role.ADMIN, "naujas", anna.getEmail(), "Slaptazodis1", "Slaptazodis1");
        check("Anna's email → 'Toks el. paštas jau naudojamas'", "Toks el. paštas jau naudojamas".equals(save(f)));
        fillCommon(f, Role.ADMIN, "naujas", "abc", "Slaptazodis1", "Slaptazodis1");
        check("email 'abc' → 'Neteisingas el. pašto formatas'", "Neteisingas el. pašto formatas".equals(save(f)));
        fillCommon(f, Role.ADMIN, "naujas", "nauja@pets.lt", "Slaptazodis1", "Kitoks12345");
        check("different passwords → 'Slaptažodžiai nesutampa'", "Slaptažodžiai nesutampa".equals(save(f)));
        fillCommon(f, Role.ADMIN, "naujas", "nauja@pets.lt", "abc", "abc");
        check("password 'abc' → 'bent 8 simbolių'", "Slaptažodis turi būti bent 8 simbolių".equals(save(f)));
        fillCommon(f, Role.ADMIN, "naujas", "nauja@pets.lt", "Slaptazodis1", "Slaptazodis1");
        onFx(() -> setText(f, "#phoneField", "861234567"));
        check("phone '861234567' → '+370 ir 8 skaitmenys'", "Telefonas turi būti +370 ir 8 skaitmenys".equals(save(f)));
        check("nothing was saved so far", Repositories.users().findAll().size() == seedUsers);
        clickNow(f, "Atšaukti");
        waitClosed(window(f));

        // ============================================================ one user of each role
        title("create: Administratorius");
        Parent a = openNew();
        fillCommon(a, Role.ADMIN, "admin1", "admin1@pets.lt", "Slaptazodis1", "Slaptazodis1");
        onFx(() -> setText(a, "#phoneField", " +37061234567 "));
        clickLater(a, "Išsaugoti");
        check("the form closes", waitClosed(window(a)));
        User admin = user("admin1");
        check("stored as an Administrator", admin instanceof Administrator);
        check("phone trimmed", "+37061234567".equals(admin.getPhone()));
        check("only a BCrypt hash is stored, and it checks", admin.getPasswordHash() != null
                && !admin.getPasswordHash().contains("Slaptazodis1") && admin.checkPassword("Slaptazodis1"));
        check("shown and selected in the table",
                onFx(() -> table.getItems().contains(admin) && table.getSelectionModel().getSelectedItem() == admin));

        title("create: Įmonės vadovas WITHOUT a company is refused (Done when)");
        Parent m = openNew();
        fillCommon(m, Role.COMPANY_MANAGER, "vadovas1", "vadovas1@pets.lt", "Slaptazodis1", "Slaptazodis1");
        check("→ only 'Vadybininkui būtina nurodyti įmonę'", "Vadybininkui būtina nurodyti įmonę".equals(save(m)));
        check("the company box is red", onFx(() -> red(m, "#companyBox")));
        check("not stored", Repositories.users().findByUsername("vadovas1").isEmpty());
        onFx(() -> {
            combo(m, "#companyBox").setValue(letenele);
            return null;
        });
        clickLater(m, "Išsaugoti");
        check("with a company it saves", waitClosed(window(m)));
        User manager = user("vadovas1");
        check("stored as a CompanyManager of Letenėlė",
                manager instanceof CompanyManager cm && cm.getCompany() == letenele);
        check("Letenėlė now has 2 managers", Repositories.users().countManagersOf(letenele.getId()) == 2);

        title("create: Prižiūrėtojas");
        Parent c = openNew();
        fillCommon(c, Role.CARETAKER, "prizi1", "prizi1@pets.lt", "Slaptazodis1", "Slaptazodis1");
        onFx(() -> setText(c, "#experienceField", "abc"));
        check("experience 'abc' → 'Patirtis turi būti sveikasis skaičius, pvz. 3'",
                "Patirtis turi būti sveikasis skaičius, pvz. 3".equals(save(c)));
        check("the experience field is red", onFx(() -> red(c, "#experienceField")));
        onFx(() -> setText(c, "#experienceField", "-2"));
        check("experience '-2' → 'Patirtis negali būti neigiama' (service rule)", "Patirtis negali būti neigiama".equals(save(c)));
        onFx(() -> {
            setText(c, "#experienceField", "5");
            ((TextArea) c.lookup("#bioArea")).setText("Myliu šunis");
            return null;
        });
        clickLater(c, "Išsaugoti");
        check("the form closes", waitClosed(window(c)));
        User caretaker = user("prizi1");
        check("stored as an independent Caretaker, 5 years, with bio", caretaker instanceof Caretaker ct
                && ct.getExperienceYears() == 5 && ct.isIndependent() && "Myliu šunis".equals(ct.getBio()));

        title("create: Augintinio šeimininkas");
        Parent o = openNew();
        fillCommon(o, Role.PET_OWNER, "savininkas1", "savininkas1@pets.lt", "Slaptazodis1", "Slaptazodis1");
        onFx(() -> setText(o, "#addressField", "Gedimino pr. 1, Vilnius"));
        clickLater(o, "Išsaugoti");
        check("the form closes", waitClosed(window(o)));
        User newOwner = user("savininkas1");
        check("stored as a PetOwner with the address, no pets", newOwner instanceof PetOwner po
                && "Gedimino pr. 1, Vilnius".equals(po.getAddress()) && po.getPets().isEmpty());
        check("Done when: one user of each of the 4 roles was created",
                admin instanceof Administrator && manager instanceof CompanyManager
                        && caretaker instanceof Caretaker && newOwner instanceof PetOwner);

        // ============================================================ edit
        title("Redaguoti: Anna (pet owner)");
        Parent e = openEdit(anna, false);
        onFx(() -> {
            check("role box shows her role and is disabled (an object can't change class)",
                    combo(e, "#roleBox").getValue() == Role.PET_OWNER && combo(e, "#roleBox").isDisabled());
            check("fields are filled", text(e, "#usernameField").equals("anna") && text(e, "#emailField").equals(anna.getEmail())
                    && text(e, "#firstNameField").equals(anna.getFirstName()));
            check("password fields are empty, label 'Naujas slaptažodis', hint about keeping it",
                    text(e, "#passwordField").isEmpty() && "Naujas slaptažodis".equals(label(e, "#passwordLabel"))
                            && label(e, "#passwordHint").startsWith("Palikite tuščius"));
            check("owner box AND the pet list are shown", shown(e, "#ownerFields") && shown(e, "#petsSection"));
            check("pet list = her pets", petList(e).getItems().equals(anna.getPets()));
            setText(e, "#phoneField", "123");
            return null;
        });
        check("bad phone → error", String.valueOf(save(e)).startsWith("Telefonas"));
        clickNow(e, "Atšaukti");
        waitClosed(window(e));
        check("the refused edit left the stored phone unchanged", anna.getPhone() == null || !anna.getPhone().equals("123"));

        title("Redaguoti: blank passwords keep the old one; her own email isn't 'taken'");
        String oldHash = anna.getPasswordHash();
        Parent e2 = openEdit(anna, false);
        onFx(() -> setText(e2, "#firstNameField", "Ana"));
        clickLater(e2, "Išsaugoti");
        check("saved", waitClosed(window(e2)));
        check("first name changed to 'Ana'", "Ana".equals(anna.getFirstName()));
        check("password hash unchanged", oldHash.equals(anna.getPasswordHash()) && anna.checkPassword("Slaptazodis1"));

        title("Redaguoti: change the password");
        Parent e3 = openEdit(anna, false);
        onFx(() -> setText(e3, "#passwordField", "Naujas123"));
        check("only one password field typed → 'Slaptažodžiai nesutampa'", "Slaptažodžiai nesutampa".equals(save(e3)));
        onFx(() -> setText(e3, "#passwordRepeatField", "Naujas123"));
        clickLater(e3, "Išsaugoti");
        check("saved", waitClosed(window(e3)));
        check("the new password works, the old one doesn't",
                anna.checkPassword("Naujas123") && !anna.checkPassword("Slaptazodis1"));

        title("owner's pet list: Pridėti gyvūną opens the pet form with the owner fixed");
        Parent e4 = openEdit(anna, false);
        clickLater(e4, "Pridėti gyvūną");
        Stage petForm = awaitForm("Naujas gyvūnas");
        check("pet form opened", petForm != null);
        Parent pf = onFx(() -> petForm.getScene().getRoot());
        check("owner is Anna and locked", onFx(() ->
                combo(pf, "#ownerBox").getValue() == anna && combo(pf, "#ownerBox").isDisabled()));
        Window userFormWindow = window(e4); // NOT inside onFx: window() already runs on the FX thread itself
        check("the pet form opens on top of the user form", onFx(() -> petForm.getOwner() == userFormWindow));
        onFx(() -> {
            setText(pf, "#nameField", "Pifas");
            combo(pf, "#speciesBox").setValue(Species.DOG);
            combo(pf, "#sizeBox").setValue(PetSize.SMALL);
            setText(pf, "#weightField", "3");
            return null;
        });
        clickLater(pf, "Išsaugoti");
        check("pet form closes", waitClosed(petForm));
        Pet pifas = Repositories.pets().findAll().stream().filter(p -> "Pifas".equals(p.getName())).findFirst().orElse(null);
        check("Pifas is stored and belongs to Anna", pifas != null && pifas.getOwner() == anna && anna.getPets().contains(pifas));
        check("the list shows it right away", onFx(() -> petList(e4).getItems().contains(pifas)));

        title("owner's pet list: Pašalinti gyvūną");
        clickLater(e4, "Pašalinti gyvūną");
        DialogPane none = awaitDialog();
        check("nothing selected → 'Pirmiausia pasirinkite gyvūną sąraše.'",
                none != null && "Pirmiausia pasirinkite gyvūną sąraše.".equals(onFx(none::getContentText)));
        answer(none, "OK");
        onFx(() -> {
            petList(e4).getSelectionModel().select(pifas);
            return null;
        });
        clickLater(e4, "Pašalinti gyvūną");
        answer(awaitDialog(), "Taip");
        Thread.sleep(200);
        check("Pifas is deleted (a pet can't exist without an owner)", !Repositories.pets().findAll().contains(pifas)
                && !anna.getPets().contains(pifas) && onFx(() -> !petList(e4).getItems().contains(pifas)));
        Pet reksas = anna.getPets().stream().filter(p -> "Reksas".equals(p.getName())).findFirst().orElseThrow();
        onFx(() -> {
            petList(e4).getSelectionModel().select(reksas);
            return null;
        });
        clickLater(e4, "Pašalinti gyvūną");
        answer(awaitDialog(), "Taip");
        DialogPane refused = awaitDialog();
        check("Reksas (has reservations) → the delete rule's message",
                refused != null && onFx(refused::getContentText).startsWith("Gyvūno „Reksas“ pašalinti negalima"));
        answer(refused, "OK");
        check("Reksas stays", anna.getPets().contains(reksas) && onFx(() -> petList(e4).getItems().contains(reksas)));
        clickNow(e4, "Atšaukti");
        waitClosed(window(e4));

        title("Redaguoti: caretaker and manager show their company");
        Parent t = openEdit(tomas, true);
        check("Tomas: caretaker box, company 'Letenėlė', experience filled", onFx(() -> shown(t, "#caretakerFields")
                && "Letenėlė".equals(label(t, "#caretakerCompanyLabel"))
                && text(t, "#experienceField").equals(String.valueOf(tomas.getExperienceYears()))));
        clickNow(t, "Atšaukti");
        waitClosed(window(t));
        Parent l = openEdit(lukas, false);
        check("Lukas: 'Nepriklausomas'", onFx(() -> "Nepriklausomas".equals(label(l, "#caretakerCompanyLabel"))));
        clickNow(l, "Atšaukti");
        waitClosed(window(l));
        Parent r = openEdit(rasa, false);
        check("Rasa: manager box with Letenėlė chosen", onFx(() -> shown(r, "#managerFields")
                && combo(r, "#companyBox").getValue() == letenele && !shown(r, "#ownerFields")));
        clickNow(r, "Atšaukti");
        waitClosed(window(r));

        // ============================================================ block
        title("Blokuoti / Atblokuoti");
        select(jonas);
        check("Jonas selected → button says 'Blokuoti'", "Blokuoti".equals(onFx(() -> buttonById(tab, "#blockButton").getText())));
        clickNow(tab, "Blokuoti");
        check("Jonas is blocked and can't log in", jonas.isBlocked() && !jonas.canLogin());
        check("the table shows 'Taip', he stays selected, button says 'Atblokuoti'", onFx(() ->
                "Taip".equals(cell(6, jonas)) && table.getSelectionModel().getSelectedItem() == jonas
                        && "Atblokuoti".equals(buttonById(tab, "#blockButton").getText())));
        clickNow(tab, "Atblokuoti");
        check("unblocked again, button back to 'Blokuoti'", !jonas.isBlocked()
                && "Blokuoti".equals(onFx(() -> buttonById(tab, "#blockButton").getText())));

        // ============================================================ delete
        title("Šalinti: Anna has reservations");
        select(anna);
        clickLater(tab, "Šalinti");
        DialogPane ask = awaitDialog();
        check("the question warns about her pets (2)", ask != null && onFx(ask::getContentText)
                .contains("Kartu bus pašalinti jo gyvūnai (2)"));
        answer(ask, "Taip");
        DialogPane no = awaitDialog();
        check("→ 'Šeimininko „Ana …“ pašalinti negalima'", no != null
                && onFx(no::getContentText).startsWith("Šeimininko „" + anna.getFullName() + "“ pašalinti negalima"));
        answer(no, "OK");
        check("Anna stays", Repositories.users().findAll().contains(anna));

        title("Šalinti: an owner without reservations takes their pets along");
        Pet kakis = new Pet();
        kakis.setName("Kakis");
        kakis.setSpecies(Species.CAT);
        kakis.setSize(PetSize.SMALL);
        kakis.setWeightKg(3);
        new PetService().create(kakis, (PetOwner) newOwner);
        select(newOwner);
        clickLater(tab, "Šalinti");
        DialogPane ask2 = awaitDialog();
        check("the question says 1 pet goes too", ask2 != null && onFx(ask2::getContentText)
                .contains("Kartu bus pašalinti jo gyvūnai (1)"));
        answer(ask2, "Taip");
        Thread.sleep(200);
        check("owner and Kakis deleted", !Repositories.users().findAll().contains(newOwner)
                && !Repositories.pets().findAll().contains(kakis));
        check("gone from the table", onFx(() -> !table.getItems().contains(newOwner)));

        title("Šalinti: 'Ne' keeps; 'Taip' deletes (administrator: no rule)");
        select(admin);
        clickLater(tab, "Šalinti");
        answer(awaitDialog(), "Ne");
        check("'Ne' → still there", Repositories.users().findAll().contains(admin));
        select(admin);
        clickLater(tab, "Šalinti");
        answer(awaitDialog(), "Taip");
        Thread.sleep(200);
        check("'Taip' → deleted", !Repositories.users().findAll().contains(admin));

        // ============================================================ refresh on tab switch
        title("a tab reloads when it is opened");
        new UserService().create(owner("petras", "petras@pets.lt"), "Slaptazodis1", "Slaptazodis1");
        onFx(() -> {
            tabs.getSelectionModel().select(tabNamed("Gyvūnai"));
            return null;
        });
        Parent petsTab = onFx(() -> (Parent) tabNamed("Gyvūnai").getContent());
        check("Gyvūnai: the owner filter now lists Petras (created elsewhere)", onFx(() ->
                combo(petsTab, "#ownerFilter").getItems().stream().anyMatch(u -> "petras".equals(((User) u).getUsername()))));
        check("Gyvūnai: the table matches storage (Kakis gone with its owner)", onFx(() ->
                ((TableView<Pet>) petsTab.lookup("#table")).getItems().size() == Repositories.pets().findAll().size()));
        new UserService().setBlocked(jonas, true); // a change made while the Users tab is not open
        onFx(() -> {
            tabs.getSelectionModel().select(tabNamed("Vartotojai"));
            return null;
        });
        check("Vartotojai: shows Petras and Jonas's new 'blocked' state",
                onFx(() -> table.getItems().stream().anyMatch(u -> "petras".equals(u.getUsername()))
                        && "Taip".equals(cell(6, jonas))));

        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        Platform.exit();
        System.exit(fail == 0 ? 0 : 1);
    }

    // ----------------------------------------------------------------- app actions

    private static Parent openNew() throws Exception {
        clickLater(tab, "Naujas");
        Stage form = awaitForm("Naujas vartotojas");
        check("form 'Naujas vartotojas' opened (modal, on top of the main window)", form != null
                && onFx(() -> form.getModality() == Modality.WINDOW_MODAL && form.getOwner() == main));
        return onFx(() -> form.getScene().getRoot());
    }

    /** Opens the edit form with Redaguoti, or with a double-click on the row. */
    private static Parent openEdit(User user, boolean doubleClick) throws Exception {
        select(user);
        if (doubleClick) {
            doubleClick(user);
        } else {
            clickLater(tab, "Redaguoti");
        }
        Stage form = awaitForm("Redaguoti vartotoją");
        check("edit form opened for " + user.getUsername() + (doubleClick ? " (by double-click)" : ""), form != null);
        return onFx(() -> form.getScene().getRoot());
    }

    private static void fillCommon(Parent f, Role role, String username, String email, String pw, String pw2) throws Exception {
        onFx(() -> {
            combo(f, "#roleBox").setValue(role);
            setText(f, "#usernameField", username);
            setText(f, "#emailField", email);
            setText(f, "#firstNameField", "Testas");
            setText(f, "#lastNameField", "Testaitis");
            setText(f, "#phoneField", "");
            setText(f, "#passwordField", pw);
            setText(f, "#passwordRepeatField", pw2);
            return null;
        });
    }

    private static void search(String text, Role role) throws Exception {
        onFx(() -> {
            setText(tab, "#textFilter", text);
            combo(tab, "#roleFilter").setValue(role);
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

    private static void select(User user) throws Exception {
        onFx(() -> {
            table.getSelectionModel().select(user);
            return null;
        });
    }

    private static void doubleClick(User user) throws Exception {
        Thread.sleep(200);
        TableRow<User> row = onFx(() -> {
            table.scrollTo(user);
            table.layout();
            for (Node node : table.lookupAll(".table-row-cell")) {
                TableRow<User> candidate = (TableRow<User>) node;
                if (candidate.getItem() == user) {
                    return candidate;
                }
            }
            return null;
        });
        if (row != null) {
            Platform.runLater(() -> row.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                    MouseButton.PRIMARY, 2, false, false, false, false, true, false, false, true, false, false, null)));
        }
    }

    private static PetOwner owner(String username, String email) {
        PetOwner o = new PetOwner();
        o.setUsername(username);
        o.setFirstName("Petras");
        o.setLastName("Petraitis");
        o.setEmail(email);
        return o;
    }

    // ----------------------------------------------------------------- lookups

    private static Tab tabNamed(String text) {
        return tabs.getTabs().stream().filter(t -> t.getText().equals(text)).findFirst().orElseThrow();
    }

    private static User user(String username) {
        return Repositories.users().findByUsername(username).orElseThrow();
    }

    private static List<User> rows() throws Exception {
        return onFx(() -> List.copyOf(table.getItems()));
    }

    private static String cell(int column, User user) {
        int row = table.getItems().indexOf(user);
        Object value = table.getColumns().get(column).getCellData(row);
        return value == null ? null : value.toString();
    }

    private static <T> ComboBox<T> combo(Parent root, String id) {
        return (ComboBox<T>) root.lookup(id);
    }

    private static ListView<Pet> petList(Parent form) {
        return (ListView<Pet>) form.lookup("#petsList");
    }

    private static boolean shown(Parent root, String id) {
        Node node = root.lookup(id);
        return node.isVisible() && node.isManaged();
    }

    private static String text(Parent root, String id) {
        String t = ((TextField) root.lookup(id)).getText();
        return t == null ? "" : t;
    }

    private static String label(Parent root, String id) {
        return ((Label) root.lookup(id)).getText();
    }

    private static Void setText(Parent root, String id, String value) {
        ((TextField) root.lookup(id)).setText(value);
        return null;
    }

    private static boolean red(Parent root, String id) {
        return root.lookup(id).getStyleClass().contains("error");
    }

    private static String shownText(ComboBox<?> box) {
        box.applyCss();
        box.layout();
        ListCell<?> cell = box.getButtonCell();
        return cell == null ? null : cell.getText();
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
