package com.example.petregistry;

import com.example.petregistry.controller.MainController;
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
        // LAB1: data lives in memory. The averages are computed from the seed reviews once.
        Repositories.useMemory();
        new ReviewService().refreshAllAverages();

        // Safety net: an error that no controller caught (e.g. the database connection was lost)
        // becomes an error alert, instead of a stack trace in the console and a button that "does nothing".
        // start() runs on the JavaFX thread, so this handler covers every button click.
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) ->
                Dialogs.error(stage, "Netikėta klaida", messageOf(error)));

        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource("view/main.fxml"));
        Scene scene = new Scene(loader.load());
        MainController controller = loader.getController();

        // The window's X asks the same question as Failas → Išeiti.
        // consume() = "cancel this close", so the window stays open on "Ne".
        stage.setOnCloseRequest(event -> {
            if (!controller.confirmExit()) {
                event.consume();
            }
        });

        stage.setTitle("Pet Registry");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * The most useful text of an error. JavaFX wraps errors from button handlers in other exceptions,
     * so we walk down the "cause" chain. Our own exceptions (e.g. DataAccessException) already have a
     * readable Lithuanian message, so the first one wins. Otherwise we keep the deepest message.
     */
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
