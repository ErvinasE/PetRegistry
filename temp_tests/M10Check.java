import com.example.petregistry.MainApp;
import com.example.petregistry.db.DatabaseManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * M10 "Done when": press every button with nothing selected and save every form empty:
 * no stack traces, only alerts. Run with argument "db" to do the same while connected to pet_registry_test.
 */
public class M10Check {

    static int pass;
    static int fail;
    static final AtomicInteger uncaught = new AtomicInteger();
    static Stage main;
    static TabPane tabs;

    /** Buttons inside edit forms that need a selection in a list. */
    static final Set<String> INNER = Set.of("Pašalinti paslaugą", "Atsieti", "Priskirti", "Pašalinti gyvūną",
            "Pašalinti atsiliepimą");

    public static void main(String[] args) {
        boolean db = args.length > 0 && args[0].equals("db");
        try {
            run(db);
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

    static void run(boolean db) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);
        main = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            // Count every error nobody caught (instead of the app's alert), so the test can see them.
            Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
                uncaught.incrementAndGet();
                System.out.println("  UNCAUGHT: " + e);
                e.printStackTrace(System.out);
            });
            return s;
        });
        tabs = onFx(() -> (TabPane) ((BorderPane) main.getScene().getRoot()).getCenter());
        Thread.sleep(300);

        if (db) {
            title("connect to pet_registry_test through the menu");
            Platform.runLater(() -> menuItem("Prisijungti…").fire());
            DialogPane d = awaitDialog();
            onFx(() -> {
                ((TextField) d.lookup("#urlField")).setText("jdbc:mysql://localhost:3306/pet_registry_test");
                ((PasswordField) d.lookup("#passwordField")).setText("");
                return null;
            });
            answer(d, "Prisijungti");
            check("connected", DatabaseManager.isConnected());
        }

        for (Tab tab : onFx(() -> List.copyOf(tabs.getTabs()))) {
            String name = onFx(tab::getText);
            title("tab " + name + (db ? " (DB)" : ""));
            onFx(() -> {
                tabs.getSelectionModel().select(tab);
                return null;
            });
            Parent root = onFx(() -> (Parent) tab.getContent());
            TableView<?> table = onFx(() -> (TableView<?>) root.lookup("#table"));
            List<Button> buttons = onFx(() -> {
                List<Button> list = new ArrayList<>();
                for (Node n : ((HBox) ((BorderPane) root).getBottom()).getChildren()) {
                    if (n instanceof Button b) {
                        list.add(b);
                    }
                }
                return list;
            });
            for (Button b : buttons) {
                String text = onFx(b::getText);
                onFx(() -> {
                    table.getSelectionModel().clearSelection();
                    return null;
                });
                int before = uncaught.get();
                Platform.runLater(b::fire);
                Object shown = awaitDialogOrForm();
                if (shown instanceof DialogPane dialog) {
                    String msg = onFx(dialog::getContentText);
                    check(text + " (nothing selected) → alert: " + msg, uncaught.get() == before);
                    answer(dialog, firstButton(dialog));
                } else if (shown instanceof Stage form) {
                    emptySave(form, text);
                } else {
                    check(text + " → expected an alert or a form", false);
                }
                check(text + " → no uncaught error", uncaught.get() == before);
            }

            // Edit the first row (if any) and press its inner buttons with nothing selected.
            int rows = onFx(() -> table.getItems().size());
            if (rows > 0) {
                onFx(() -> {
                    table.getSelectionModel().select(0);
                    return null;
                });
                Button edit = buttons.stream().filter(b -> b.getText().equals("Redaguoti")).findFirst().orElseThrow();
                Platform.runLater(edit::fire);
                Object shown = awaitDialogOrForm();
                if (shown instanceof Stage form) {
                    Parent f = onFx(() -> form.getScene().getRoot());
                    List<Button> inner = onFx(() -> f.lookupAll(".button").stream()
                            .filter(n -> n instanceof Button b && INNER.contains(b.getText()) && !b.isDisabled())
                            .map(n -> (Button) n).toList());
                    for (Button b : inner) {
                        int before = uncaught.get();
                        Platform.runLater(b::fire);
                        DialogPane dialog = awaitDialog();
                        check("edit form: " + onFx(b::getText) + " (nothing selected) → alert",
                                dialog != null && uncaught.get() == before);
                        answer(dialog, firstButton(dialog));
                    }
                    closeForm(form);
                } else if (shown instanceof DialogPane dialog) {
                    answer(dialog, firstButton(dialog));
                }
            }
        }

        title("nested forms opened from inside forms, saved empty");
        // Company form → Paslaugos → Pridėti paslaugą (service form with the company fixed)
        Tab companies = onFx(() -> tabs.getTabs().stream().filter(t -> t.getText().equals("Įmonės")).findFirst().orElseThrow());
        Parent cRoot = onFx(() -> {
            tabs.getSelectionModel().select(companies);
            return (Parent) companies.getContent();
        });
        TableView<?> cTable = onFx(() -> (TableView<?>) cRoot.lookup("#table"));
        if (onFx(() -> cTable.getItems().size()) > 0) {
            onFx(() -> {
                cTable.getSelectionModel().select(0);
                return null;
            });
            Platform.runLater(() -> button(cRoot, "Redaguoti").fire());
            Stage cForm = (Stage) awaitDialogOrForm();
            Parent cf = onFx(() -> cForm.getScene().getRoot());
            Platform.runLater(() -> button(cf, "Pridėti paslaugą").fire());
            Stage sForm = awaitWindow(s -> "Nauja paslauga".equals(s.getTitle()));
            emptySave(sForm, "company form → Pridėti paslaugą");
            closeForm(cForm);
        }
        check("no uncaught errors in the whole run", uncaught.get() == 0);

        if (db) {
            Platform.runLater(() -> menuItem("Atsijungti").fire());
            Thread.sleep(500);
            check("disconnected", !DatabaseManager.isConnected());
        }
    }

    /** Išsaugoti on an empty form must show an error alert and keep the form open. */
    static void emptySave(Stage form, String what) throws Exception {
        String title = onFx(form::getTitle);
        Parent f = onFx(() -> form.getScene().getRoot());
        int before = uncaught.get();
        Platform.runLater(() -> button(f, "Išsaugoti").fire());
        DialogPane dialog = awaitDialog();
        check(what + " → form '" + title + "', empty Išsaugoti → error alert", dialog != null
                && onFx(() -> dialog.getScene().getWindow() != null) && uncaught.get() == before);
        if (dialog != null) {
            System.out.println("        " + onFx(dialog::getContentText).replace("\n", " | "));
            answer(dialog, "OK");
        }
        check(what + " → form still open after the error", onFx(form::isShowing));
        closeForm(form);
    }

    static void closeForm(Stage form) throws Exception {
        Parent f = onFx(() -> form.getScene().getRoot());
        onFx(() -> {
            for (Node n : f.lookupAll(".button")) {
                if (n instanceof Button b && (b.getText().equals("Atšaukti") || b.getText().equals("Uždaryti"))) {
                    b.fire();
                    return null;
                }
            }
            form.close();
            return null;
        });
        waitClosed(form);
    }

    static String firstButton(DialogPane dialog) throws Exception {
        if (dialog == null) {
            return "OK";
        }
        return onFx(() -> {
            List<ButtonType> types = dialog.getButtonTypes();
            // Prefer the safe answer: OK for info, "Ne"/"Atšaukti" for questions.
            for (ButtonType t : types) {
                if (t.getText().equals("Ne") || t.getText().equals("Atšaukti")) {
                    return t.getText();
                }
            }
            return types.get(0).getText();
        });
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

    // ----------------------------------------------------------------- plumbing

    static Object awaitDialogOrForm() throws Exception {
        Stage s = awaitWindow(st -> st != main);
        if (s == null) {
            return null;
        }
        return onFx(() -> s.getScene().getRoot() instanceof DialogPane d ? d : s);
    }

    static Button button(Parent root, String text) {
        for (Node node : root.lookupAll(".button")) {
            if (node instanceof Button b && text.equals(b.getText())) {
                return b;
            }
        }
        throw new IllegalStateException("No button '" + text + "'");
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
        return result.get(15, TimeUnit.SECONDS);
    }

    static Stage awaitWindow(Predicate<Stage> test) throws Exception {
        for (int i = 0; i < 50; i++) {
            Stage found = onFx(() -> {
                Stage last = null;
                for (Window w : Window.getWindows()) {
                    if (w instanceof Stage s && s.isShowing() && s.getScene() != null && test.test(s)) {
                        last = s; // the newest matching window
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

    static boolean waitClosed(Window window) throws Exception {
        for (int i = 0; i < 50; i++) {
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
