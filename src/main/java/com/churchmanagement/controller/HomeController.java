package com.churchmanagement.controller;

import com.churchmanagement.navigation.HomeNavigator;
import com.churchmanagement.navigation.NavItem;
import com.churchmanagement.navigation.NavModule;
import com.churchmanagement.navigation.NavigationAccess;
import com.churchmanagement.security.AuthenticatedUser;
import com.churchmanagement.service.HomeBadgeService;
import com.churchmanagement.service.SystemConfigurationCache;
import com.churchmanagement.util.ThemeService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/** Home: one tile per module the user can open, with an (i) popover listing its screens. */
public class HomeController {
    private static final String STYLESHEET = "/com/churchmanagement/view/app.css";

    @FXML private Label greetingLabel;
    @FXML private Label dateLabel;
    @FXML private TilePane tilePane;

    private final Map<String, Label> badgeLabels = new HashMap<>();
    private NavigationAccess access;
    private HomeNavigator navigator;
    private Popup openPopover;

    public void configure(NavigationAccess access, HomeNavigator navigator, AuthenticatedUser user) {
        this.access = access;
        this.navigator = navigator;
        greetingLabel.setText(greeting() + ", " + user.getFullName());
        dateLabel.setText(LocalDate.now().format(dateFormatter()));
        tilePane.getChildren().setAll(access.visibleModules().stream().map(this::tile).toList());
        loadBadges();
    }

    private Node tile(NavModule module) {
        FontIcon icon = new FontIcon(module.icon());
        icon.getStyleClass().add("report-card-icon");

        Button infoButton = new Button();
        infoButton.setGraphic(new FontIcon("fas-info-circle"));
        infoButton.getStyleClass().add("nav-tile-info-button");
        infoButton.setAccessibleText("What's in " + module.title());
        infoButton.setTooltip(new Tooltip("What's in " + module.title()));
        infoButton.setOnAction(event -> {
            event.consume();
            showPopover(module, infoButton);
        });

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox top = new HBox(icon, topSpacer, infoButton);
        top.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(module.title());
        title.getStyleClass().add("report-card-title");
        Label description = new Label(module.description());
        description.setWrapText(true);
        description.getStyleClass().add("report-card-action");

        Label badge = new Label();
        badge.getStyleClass().add("nav-tile-badge");
        badge.setVisible(false);
        badge.setManaged(false);
        badgeLabels.put(module.id(), badge);

        Region bottomSpacer = new Region();
        VBox.setVgrow(bottomSpacer, Priority.ALWAYS);
        VBox content = new VBox(6, top, title, description, bottomSpacer, badge);

        StackPane tile = new StackPane(content);
        tile.getStyleClass().addAll("report-card", "nav-tile");
        tile.setFocusTraversable(true);
        tile.setAccessibleText(module.title() + ". " + module.description());
        Tooltip.install(tile, new Tooltip(module.title() + " (Ctrl+" + module.shortcutNumber() + ")"));
        // Clicks and keys on the (i) button bubble up to the tile; only the tile itself opens the module.
        tile.setOnMouseClicked(event -> {
            if (!(event.getTarget() instanceof Node target) || !isInside(target, infoButton)) {
                navigator.openModule(module);
            }
        });
        tile.setOnKeyPressed(event -> {
            if (event.getTarget() == tile && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                event.consume();
                navigator.openModule(module);
            }
        });
        return tile;
    }

    private void showPopover(NavModule module, Node anchor) {
        if (openPopover != null) {
            openPopover.hide();
        }

        FontIcon icon = new FontIcon(module.icon());
        icon.getStyleClass().add("report-card-icon");
        Label title = new Label(module.title());
        title.getStyleClass().add("report-card-title");
        HBox header = new HBox(8, icon, title);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10, header);
        boolean sections = access.useSections(module);
        String lastSection = null;
        for (NavItem item : access.visibleItems(module)) {
            if (sections && item.section() != null && !item.section().equals(lastSection)) {
                Label sectionLabel = new Label(item.section());
                sectionLabel.getStyleClass().add("nav-popover-section");
                box.getChildren().add(sectionLabel);
                lastSection = item.section();
            }
            FontIcon itemIcon = new FontIcon(item.icon());
            itemIcon.getStyleClass().add("report-card-icon");
            Label itemTitle = new Label(item.title());
            itemTitle.getStyleClass().add("nav-popover-item-title");
            Label itemDescription = new Label(item.description());
            itemDescription.setWrapText(true);
            itemDescription.getStyleClass().add("report-card-action");
            HBox row = new HBox(10, itemIcon, new VBox(2, itemTitle, itemDescription));
            row.setAlignment(Pos.TOP_LEFT);
            box.getChildren().add(row);
        }

        Popup popover = new Popup();
        Button openButton = new Button("Open module");
        openButton.getStyleClass().add("primary-button");
        openButton.setOnAction(event -> {
            popover.hide();
            navigator.openModule(module);
        });
        HBox actions = new HBox(openButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        box.getChildren().add(actions);

        box.getStyleClass().addAll("report-card", "nav-popover");
        box.setPrefWidth(330);
        box.getStylesheets().add(getClass().getResource(STYLESHEET).toExternalForm());
        new ThemeService().applyConfiguredTheme(box);

        popover.getContent().add(box);
        popover.setAutoHide(true);
        popover.setHideOnEscape(true);
        popover.setOnHidden(event -> anchor.requestFocus());
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popover.show(anchor, bounds.getMinX(), bounds.getMaxY() + 4);
        openPopover = popover;
        Platform.runLater(openButton::requestFocus);
    }

    private void loadBadges() {
        Thread thread = new Thread(() -> {
            Map<String, String> badges = new HomeBadgeService().badgeTexts();
            Platform.runLater(() -> badges.forEach((moduleId, text) -> {
                Label badge = badgeLabels.get(moduleId);
                if (badge != null) {
                    badge.setText(text);
                    badge.setVisible(true);
                    badge.setManaged(true);
                }
            }));
        }, "home-badges");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean isInside(Node node, Node ancestor) {
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static String greeting() {
        int hour = LocalTime.now().getHour();
        return hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
    }

    private static DateTimeFormatter dateFormatter() {
        String pattern = SystemConfigurationCache.getInstance().getString("system.date.format");
        try {
            return DateTimeFormatter.ofPattern(pattern == null || pattern.isBlank() ? "yyyy-MMM-dd" : pattern);
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("yyyy-MMM-dd");
        }
    }
}
