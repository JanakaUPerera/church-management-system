package com.churchmanagement.controller;

import com.churchmanagement.reports.ReceiptPdfGenerator;
import com.churchmanagement.util.ButtonIconUtil;
import com.churchmanagement.util.DialogStyler;
import com.churchmanagement.util.ProcessingDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

public class ReportsController {
    private final ReceiptPdfGenerator receiptPdfGenerator = new ReceiptPdfGenerator();

    @FXML
    private Button unicodeFontTestButton;

    @FXML
    private Label unicodeFontPathLabel;

    @FXML
    private void initialize() {
        ButtonIconUtil.applyIcon(unicodeFontTestButton, "fas-file-pdf");
    }

    @FXML
    private void handleUnicodeFontTest() {
        ProcessingDialog.run("Unicode Font Test",
                "Generating test PDF...",
                receiptPdfGenerator::generateUnicodeFontTestPdf,
                path -> unicodeFontPathLabel.setText(path),
                this::showError);
    }

    private void showError(Throwable throwable) {
        String message = throwable.getMessage() == null ? "Unable to generate test PDF." : throwable.getMessage();
        Alert alert = DialogStyler.apply(new Alert(Alert.AlertType.ERROR));
        alert.setTitle("Reports");
        alert.setHeaderText("Unable to generate Unicode font test PDF");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
