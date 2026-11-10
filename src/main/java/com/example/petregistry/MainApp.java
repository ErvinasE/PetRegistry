package com.example.petregistry;

import com.example.petregistry.controller.MainController;
import com.example.petregistry.repo.Repositories;
import com.example.petregistry.service.ReviewService;
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
}
