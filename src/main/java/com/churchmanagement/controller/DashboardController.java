package com.churchmanagement.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DashboardController {
    @FXML
    private Label dateLabel;

    @FXML
    private void initialize() {
        dateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy")));
    }
}
