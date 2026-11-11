package com.example.petregistry;

import com.example.petregistry.controller.MainController;
import com.example.petregistry.db.DatabaseManager;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.ReviewService;
import com.example.petregistry.util.Dialogs;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // The app starts with data in memory; Prisijungti… switches to MySQL.
        // The averages are computed from the seed reviews once.
        Repositories.useMemory();
        new ReviewService().refreshAllAverages();

        // Safety net: an uncaught error becomes an error alert instead of a stack trace.
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) ->
                Dialogs.error(stage, "Netikėta klaida", messageOf(error)));

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/main.fxml"));
        Scene scene = new Scene(loader.load());
        MainController controller = loader.getController();

        // The window's X asks the same question as Failas → Išeiti.
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmExit()) {
                event.consume();
            }
        });

        stage.setTitle("Pet Registry");
        stage.setScene(scene);
        stage.show();
    }

    /** JavaFX calls this once when the app closes (Išeiti, the window's X). Never leave a connection open. */
    @Override
    public void stop() {
        DatabaseManager.disconnect();
    }

    /** The most useful text of an error: the first readable message in the cause chain wins, else the deepest. */
    private static String messageOf(Throwable error) {
        String message = null;
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getClass().getName().startsWith("com.example.petregistry") && t.getMessage() != null) {
                return t.getMessage();
            }
            if (t.getMessage() != null) {
                message = t.getMessage();
            }
        }
        return message != null ? message : error.getClass().getSimpleName();
    }
}
