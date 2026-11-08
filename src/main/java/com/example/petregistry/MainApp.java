package com.example.petregistry;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 900, 600);
        stage.setTitle("Pet Registry");
        stage.setScene(scene);
        stage.show();
    }
}
