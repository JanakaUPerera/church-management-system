package com.churchmanagement.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AtCommandResult {
    private boolean modemDetected;
    private String message;
    private String response;
    private List<String> commands = new ArrayList<>();

    public AtCommandResult() {
    }

    public AtCommandResult(boolean modemDetected, String message, String response, List<String> commands) {
        this.modemDetected = modemDetected;
        this.message = message;
        this.response = response;
        setCommands(commands);
    }

    public boolean isModemDetected() {
        return modemDetected;
    }

    public void setModemDetected(boolean modemDetected) {
        this.modemDetected = modemDetected;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public List<String> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    public void setCommands(List<String> commands) {
        this.commands = commands == null ? new ArrayList<>() : new ArrayList<>(commands);
    }
}
