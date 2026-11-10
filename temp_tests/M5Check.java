import com.example.petregistry.MainApp;
import com.example.petregistry.controller.MainController;
import com.example.petregistry.model.Rateable;
import com.example.petregistry.repo.Repositories;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * M5 "Done when": all 5 tabs load, and both alerts work.
 * Opens the real main window, then clicks the menu items and the dialog buttons from code.
 */
public class M5Check {

    private static int pass;
    private static int fail;

    public static void main(String[] args) throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Platform.startup(started::countDown);
        started.await();
        Platform.setImplicitExit(false);
        Thread fxThread = onFx(Thread::currentThread);

        // ------------------------------------------------------------ start the real app
        Stage stage = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            return s;
        });

        title("startup");
        onFx(() -> {
            check("window is showing", stage.isShowing());
            check("title is 'Pet Registry'", "Pet Registry".equals(stage.getTitle()));
            check("memory storage chosen (5 pets)", Repositories.pets().findAll().size() == 5);
            Rateable egle = (Rateable) Repositories.users().findByUsername("egle").orElseThrow();
            check("averages refreshed (Eglė = 5.0)", egle.getAverageRating() == 5.0);
            return null;
        });

        // ------------------------------------------------------------ layout
        title("menu bar");
        onFx(() -> {
            BorderPane root = (BorderPane) stage.getScene().getRoot();
            MenuBar bar = (MenuBar) root.getTop();
            check("menus: Failas, Duomenų bazė, Pagalba",
                    bar.getMenus().stream().map(Menu::getText).toList()
                            .equals(List.of("Failas", "Duomenų bazė", "Pagalba")));
            check("Failas: Išeiti", texts(bar.getMenus().get(0)).equals(List.of("Išeiti")));
            check("Duomenų bazė: Prisijungti…, Atsijungti",
                    texts(bar.getMenus().get(1)).equals(List.of("Prisijungti…", "Atsijungti")));
            check("both database items are disabled",
                    bar.getMenus().get(1).getItems().stream().allMatch(MenuItem::isDisable));
            check("Pagalba: Apie", texts(bar.getMenus().get(2)).equals(List.of("Apie")));
            return null;
        });

        title("tabs");
        onFx(() -> {
            TabPane tabs = (TabPane) ((BorderPane) stage.getScene().getRoot()).getCenter();
            check("5 tabs in order",
                    tabs.getTabs().stream().map(Tab::getText).toList()
                            .equals(List.of("Vartotojai", "Įmonės", "Paslaugos", "Gyvūnai", "Rezervacijos")));
            check("tabs can't be closed", tabs.getTabClosingPolicy() == TabPane.TabClosingPolicy.UNAVAILABLE);
            for (Tab tab : tabs.getTabs()) {
                tabs.getSelectionModel().select(tab);
                boolean loaded = tab.getContent() instanceof BorderPane pane && pane.getCenter() instanceof Label;
                check("tab '" + tab.getText() + "' loaded its FXML and can be selected",
                        loaded && tabs.getSelectionModel().getSelectedItem() == tab);
            }
            Label status = (Label) stage.getScene().lookup("#statusLabel");
            check("status bar: 'Duomenys atmintyje'", status != null && "Duomenys atmintyje".equals(status.getText()));
            return null;
        });

        title("tab controllers injected into MainController (fx:id + \"Controller\")");
        onFx(() -> {
            FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/main.fxml"));
            loader.load();
            Object controller = loader.getController();
            check("controller is MainController", controller instanceof MainController);
            String[][] fields = {
                    {"usersTabController", "UsersTabController"},
                    {"companiesTabController", "CompaniesTabController"},
                    {"servicesTabController", "ServicesTabController"},
                    {"petsTabController", "PetsTabController"},
                    {"reservationsTabController", "ReservationsTabController"},
            };
            for (String[] f : fields) {
                Field field = MainController.class.getDeclaredField(f[0]);
                field.setAccessible(true);
                Object value = field.get(controller);
                check(f[0] + " is a " + f[1], value != null && value.getClass().getSimpleName().equals(f[1]));
            }
            return null;
        });

        // ------------------------------------------------------------ alerts
        title("Pagalba → Apie (information alert)");
        click(stage, "Apie");
        Stage about = waitForDialog();
        check("a dialog opened", about != null);
        if (about != null) {
            onFx(() -> {
                DialogPane pane = (DialogPane) about.getScene().getRoot();
                check("title 'Apie'", "Apie".equals(about.getTitle()));
                check("header 'Pet Registry'", "Pet Registry".equals(pane.getHeaderText()));
                check("text mentions VILNIUS TECH", pane.getContentText().contains("VILNIUS TECH"));
                check("only an OK button", pane.getButtonTypes().equals(List.of(ButtonType.OK)));
                check("opens on top of the main window", about.getOwner() == stage);
                ((Button) pane.lookupButton(ButtonType.OK)).fire();
                return null;
            });
            check("OK closes it", waitForNoDialog());
        }

        title("Failas → Išeiti, answer 'Ne'");
        click(stage, "Išeiti");
        Stage exit = waitForDialog();
        check("a dialog opened", exit != null);
        if (exit != null) {
            onFx(() -> {
                DialogPane pane = (DialogPane) exit.getScene().getRoot();
                check("title 'Išeiti'", "Išeiti".equals(exit.getTitle()));
                check("asks 'Ar tikrai norite išeiti?'", "Ar tikrai norite išeiti?".equals(pane.getContentText()));
                check("buttons 'Taip', 'Ne'",
                        pane.getButtonTypes().stream().map(ButtonType::getText).toList().equals(List.of("Taip", "Ne")));
                check("opens on top of the main window", exit.getOwner() == stage);
                pressButton(pane, "Ne");
                return null;
            });
            check("'Ne' closes the dialog", waitForNoDialog());
            check("the app is still open", onFx(stage::isShowing));
        }

        title("Failas → Išeiti, close the dialog with its X");
        click(stage, "Išeiti");
        Stage exitX = waitForDialog();
        check("a dialog opened", exitX != null);
        if (exitX != null) {
            onFx(() -> {
                exitX.fireEvent(new WindowEvent(exitX, WindowEvent.WINDOW_CLOSE_REQUEST));
                return null;
            });
            check("X closes the dialog", waitForNoDialog());
            check("the app is still open", onFx(stage::isShowing));
        }

        title("main window X, answer 'Ne'");
        Platform.runLater(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        Stage closeNo = waitForDialog();
        check("the same confirmation opened", closeNo != null
                && onFx(() -> "Ar tikrai norite išeiti?".equals(((DialogPane) closeNo.getScene().getRoot()).getContentText())));
        if (closeNo != null) {
            onFx(() -> {
                pressButton((DialogPane) closeNo.getScene().getRoot(), "Ne");
                return null;
            });
            check("'Ne' closes the dialog", waitForNoDialog());
            check("the main window is still open", onFx(stage::isShowing));
        }

        title("main window X, answer 'Taip'");
        Platform.runLater(() -> stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST)));
        Stage closeYes = waitForDialog();
        check("a dialog opened", closeYes != null);
        if (closeYes != null) {
            onFx(() -> {
                pressButton((DialogPane) closeYes.getScene().getRoot(), "Taip");
                return null;
            });
            check("the main window closes", waitForNoDialog() && !onFx(stage::isShowing));
        }

        title("Failas → Išeiti, answer 'Taip' (on a freshly opened window)");
        Stage stage2 = onFx(() -> {
            Stage s = new Stage();
            new MainApp().start(s);
            return s;
        });
        click(stage2, "Išeiti");
        Stage exitYes = waitForDialog();
        check("a dialog opened", exitYes != null);
        if (exitYes != null) {
            onFx(() -> {
                pressButton((DialogPane) exitYes.getScene().getRoot(), "Taip");
                return null;
            });
            fxThread.join(5000);
            check("'Taip' shuts JavaFX down (Platform.exit)", !fxThread.isAlive());
        }

        System.out.println();
        System.out.println("PASSED " + pass + ", FAILED " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    // ----------------------------------------------------------------- helpers

    /** Runs code on the JavaFX thread and waits for its result. */
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

    /** Fires a menu item WITHOUT waiting: its handler blocks in showAndWait() until the dialog closes. */
    private static void click(Stage stage, String text) throws Exception {
        MenuItem item = onFx(() -> {
            MenuBar bar = (MenuBar) ((BorderPane) stage.getScene().getRoot()).getTop();
            return bar.getMenus().stream().flatMap(m -> m.getItems().stream())
                    .filter(i -> text.equals(i.getText())).findFirst().orElseThrow();
        });
        Platform.runLater(item::fire);
    }

    private static Stage findDialog() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage s && s.getScene() != null && s.getScene().getRoot() instanceof DialogPane) {
                return s;
            }
        }
        return null;
    }

    private static Stage waitForDialog() throws Exception {
        for (int i = 0; i < 50; i++) {
            Stage dialog = onFx(M5Check::findDialog);
            if (dialog != null) {
                return dialog;
            }
            Thread.sleep(100);
        }
        return null;
    }

    private static boolean waitForNoDialog() throws Exception {
        for (int i = 0; i < 50; i++) {
            if (onFx(M5Check::findDialog) == null) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static void pressButton(DialogPane pane, String text) {
        ButtonType type = pane.getButtonTypes().stream()
                .filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
        ((Button) pane.lookupButton(type)).fire();
    }

    private static List<String> texts(Menu menu) {
        return menu.getItems().stream().map(MenuItem::getText).toList();
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
