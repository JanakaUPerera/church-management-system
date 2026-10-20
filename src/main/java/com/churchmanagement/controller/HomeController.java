package com.churchmanagement.controller;

import com.churchmanagement.navigation.HomeNavigator;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.security.AuthenticatedUser;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.TilePane;

public class HomeController {
    @FXML private TilePane tilePane;

    public void configure(NavigationAccess access, HomeNavigator navigator, AuthenticatedUser user) {
        tilePane.getChildren().setAll(access.visibleModules().stream().map(module -> {
            Button button = new Button(module.title());
            button.setOnAction(event -> navigator.openModule(module));
            return button;
        }).toList());
    }
}
