module com.example.petregistry {
    requires javafx.controls;
    requires javafx.fxml;
    requires jbcrypt;
    requires java.sql;

    opens com.example.petregistry to javafx.fxml;
    opens com.example.petregistry.model to javafx.base;
    exports com.example.petregistry;
}
