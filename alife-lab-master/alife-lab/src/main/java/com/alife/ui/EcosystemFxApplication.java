package com.alife.ui;

import com.alife.AlifeApplication;
import com.alife.config.SimulationProperties;
import com.alife.environment.Environment;
import com.alife.model.Agent;
import com.alife.model.AgentType;
import com.alife.simulation.SimulationEngine;
import com.alife.simulation.SimulationStats;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** JavaFX interface for configuring, stepping and observing the ecosystem. */
public class EcosystemFxApplication extends Application {

    private static final int CANVAS_SIZE = 720;

    // ---- СВЕТЛАЯ ПАЛИТРА ----
    private static final String APP_BG      = "#f4f1ec"; // тёплый светлый фон
    private static final String SIDEBAR_BG  = "#2f3a4a"; // тёмно-синий сайдбар
    private static final String SIDEBAR_BG2 = "#3a4759";
    private static final String SETTINGS_BG = "#ffffff";
    private static final String SETTINGS_BG2= "#f0ece5";
    private static final String BORDER      = "#e0dcd4";
    private static final String TEXT        = "#2a2d31";
    private static final String TEXT_DIM    = "#7a7d82";
    private static final String TEXT_LIGHT  = "#eef2f7";
    private static final String ACCENT      = "#e07b39"; // тёплый оранжевый акцент
    private static final String ACCENT_HOV  = "#f09050";
    private static final String DANGER      = "#c0392b";
    private static final String CANVAS_BG   = "#fbfaf7";
    private static final String CANVAS_GRID = "#e7e3da";
    private static final String WALL        = "#8a8478";

    private static final Color PLANT_COLOR     = Color.web("#3f9142");
    private static final Color HERBIVORE_COLOR = Color.web("#c08a3e");
    private static final Color PREDATOR_COLOR  = Color.web("#c0392b");

    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private SimulationEngine engine;
    private SimulationProperties properties;
    private Canvas canvas;
    private Label statsLabel;
    private Label statusLabel;
    private Timeline timer;
    private boolean initialized;

    private int iteration = 0;
    private int stepIntervalMs = 180;

    @Override
    public void start(Stage stage) {
        engine = AlifeApplication.getBean(SimulationEngine.class);
        properties = AlifeApplication.getBean(SimulationProperties.class);

        canvas = new Canvas(CANVAS_SIZE, CANVAS_SIZE);
        canvas.setOnMouseClicked(event -> stepOnce());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + APP_BG + ";");
        root.setLeft(createSidebar());
        root.setCenter(createWorldPane());
        root.setRight(createSettingsPane());

        stage.setTitle("Искусственная жизнь — экосистема");
        stage.setScene(new Scene(root, 1280, 880));
        stage.setMinWidth(1100);
        stage.setMinHeight(760);
        stage.setOnCloseRequest(event -> {
            stopTimer();
            AlifeApplication.closeContext();
            Platform.exit();
        });
        stage.show();
        resetSimulation();
    }

    // ==================== ЛЕВЫЙ САЙДБАР: КНОПКИ ====================

    private VBox createSidebar() {
        Label title = new Label("ALife");
        title.setStyle(
                "-fx-text-fill: " + TEXT_LIGHT + ";" +
                        "-fx-font-size: 20px;" +
                        "-fx-font-weight: bold;"
        );
        Label subtitle = new Label("симуляция экосистемы");
        subtitle.setStyle("-fx-text-fill: #a9b6c7; -fx-font-size: 11px;");

        VBox header = new VBox(2, title, subtitle);
        header.setPadding(new Insets(20, 18, 24, 18));

        Button reset = sidebarButton("Применить / Сбросить");
        reset.setOnAction(e -> resetSimulation());

        Button step = sidebarButton("Один шаг");
        step.setOnAction(e -> stepOnce());

        Button applySpeed = sidebarButton("Применить скорость");
        applySpeed.setOnAction(e -> applySpeedOnTheFly());

        Button automatic = sidebarPrimaryButton("Запустить");
        automatic.setOnAction(e -> {
            if (timer != null && timer.getStatus() == Timeline.Status.RUNNING) {
                stopTimer();
                automatic.setText("Запустить");
                automatic.setStyle(sidebarPrimaryStyle());
            } else {
                if (!initialized) {
                    resetSimulation();
                }
                timer = new Timeline(new KeyFrame(Duration.millis(stepIntervalMs), tick -> stepOnce()));
                timer.setCycleCount(Timeline.INDEFINITE);
                timer.play();
                automatic.setText("Остановить");
                automatic.setStyle(sidebarDangerStyle());
            }
        });

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + SIDEBAR_BG2 + ";");
        VBox.setMargin(sep, new Insets(10, 14, 10, 14));

        VBox buttons = new VBox(8, reset, step, applySpeed, sep, automatic);
        buttons.setPadding(new Insets(0, 14, 0, 14));

        VBox sidebar = new VBox(header, buttons);
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(200);
        sidebar.setStyle("-fx-background-color: " + SIDEBAR_BG + ";");
        return sidebar;
    }

    private Button sidebarButton(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(sidebarButtonStyle());
        b.setOnMouseEntered(e -> b.setStyle(sidebarButtonHoverStyle()));
        b.setOnMouseExited(e -> b.setStyle(sidebarButtonStyle()));
        return b;
    }

    private Button sidebarPrimaryButton(String text) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(sidebarPrimaryStyle());
        b.setOnMouseEntered(e -> b.setStyle(sidebarPrimaryHoverStyle()));
        b.setOnMouseExited(e -> b.setStyle(sidebarPrimaryStyle()));
        return b;
    }

    private String sidebarButtonStyle() {
        return "-fx-background-color: " + SIDEBAR_BG2 + ";" +
                "-fx-text-fill: " + TEXT_LIGHT + ";" +
                "-fx-font-size: 13px;" +
                "-fx-padding: 10 14 10 14;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
    }

    private String sidebarButtonHoverStyle() {
        return "-fx-background-color: #47556a;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-padding: 10 14 10 14;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
    }

    private String sidebarPrimaryStyle() {
        return "-fx-background-color: " + ACCENT + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 12 14 12 14;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
    }

    private String sidebarPrimaryHoverStyle() {
        return "-fx-background-color: " + ACCENT_HOV + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 12 14 12 14;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
    }

    private String sidebarDangerStyle() {
        return "-fx-background-color: " + DANGER + ";" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 12 14 12 14;" +
                "-fx-background-radius: 8;" +
                "-fx-cursor: hand;";
    }

    // ==================== ПРАВАЯ ПАНЕЛЬ: НАСТРОЙКИ ====================

    private ScrollPane createSettingsPane() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(20));

        addSection(grid, "Симуляция");
        addIntField(grid, "Скорость (мс/шаг)", () -> stepIntervalMs, value -> stepIntervalMs = value);

        addSection(grid, "Карта и популяция");
        addInt(grid, "Ширина", properties::getGridWidth, properties::setGridWidth);
        addInt(grid, "Высота", properties::getGridHeight, properties::setGridHeight);
        addInt(grid, "Растения", properties::getInitialPlants, properties::setInitialPlants);
        addInt(grid, "Зайцы", properties::getInitialHerbivores, properties::setInitialHerbivores);
        addInt(grid, "Волки", properties::getInitialPredators, properties::setInitialPredators);

        addSection(grid, "Растения");
        addDouble(grid, "Начальная энергия", properties::getPlantInitialEnergy, properties::setPlantInitialEnergy);
        addDouble(grid, "Прирост за шаг", properties::getPlantEnergyPerStep, properties::setPlantEnergyPerStep);
        addDouble(grid, "Потеря за шаг", properties::getPlantEnergyDecayPerStep, properties::setPlantEnergyDecayPerStep);
        addDouble(grid, "Максимум энергии", properties::getPlantMaxEnergy, properties::setPlantMaxEnergy);
        addDouble(grid, "Порог размножения", properties::getPlantReproductionThreshold, properties::setPlantReproductionThreshold);
        addDouble(grid, "Цена размножения", properties::getPlantReproductionCost, properties::setPlantReproductionCost);

        addSection(grid, "Зайцы");
        addDouble(grid, "Начальная энергия", properties::getHerbivoreInitialEnergy, properties::setHerbivoreInitialEnergy);
        addDouble(grid, "Расход за шаг", properties::getHerbivoreEnergyLossPerStep, properties::setHerbivoreEnergyLossPerStep);
        addInt(grid, "Радиус зрения", properties::getHerbivoreVisionRadius, properties::setHerbivoreVisionRadius);
        addDouble(grid, "Порог размножения", properties::getHerbivoreReproductionThreshold, properties::setHerbivoreReproductionThreshold);
        addDouble(grid, "Цена размножения", properties::getHerbivoreReproductionCost, properties::setHerbivoreReproductionCost);
        addInt(grid, "Пауза размножения", properties::getHerbivoreReproductionCooldownSteps, properties::setHerbivoreReproductionCooldownSteps);

        addSection(grid, "Волки");
        addDouble(grid, "Начальная энергия", properties::getPredatorInitialEnergy, properties::setPredatorInitialEnergy);
        addDouble(grid, "Расход за шаг", properties::getPredatorEnergyLossPerStep, properties::setPredatorEnergyLossPerStep);
        addInt(grid, "Радиус зрения", properties::getPredatorVisionRadius, properties::setPredatorVisionRadius);
        addDouble(grid, "Порог размножения", properties::getPredatorReproductionThreshold, properties::setPredatorReproductionThreshold);
        addDouble(grid, "Цена размножения", properties::getPredatorReproductionCost, properties::setPredatorReproductionCost);
        addInt(grid, "Пауза размножения", properties::getPredatorReproductionCooldownSteps, properties::setPredatorReproductionCooldownSteps);

        ScrollPane pane = new ScrollPane(grid);
        pane.setFitToWidth(true);
        pane.setPrefWidth(340);
        pane.setMinWidth(300);
        pane.setStyle(
                "-fx-background: " + SETTINGS_BG + ";" +
                        "-fx-background-color: " + SETTINGS_BG + ";" +
                        "-fx-border-color: " + BORDER + ";" +
                        "-fx-border-width: 0 0 0 1;"
        );
        return pane;
    }

    // ==================== ЦЕНТР ====================

    private VBox createWorldPane() {
        statsLabel = new Label("—");
        statsLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT + ";");

        statusLabel = new Label("Настройте параметры и запустите симуляцию.");
        statusLabel.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 13px;");

        VBox statsBar = new VBox(4, statsLabel, statusLabel);
        statsBar.setPadding(new Insets(20, 24, 12, 24));

        VBox canvasBox = new VBox(canvas);
        canvasBox.setAlignment(Pos.CENTER);
        canvasBox.setPadding(new Insets(0, 24, 0, 24));

        VBox legend = createLegend();

        VBox box = new VBox(10, statsBar, canvasBox, legend);
        box.setAlignment(Pos.TOP_CENTER);
        box.setStyle("-fx-background-color: " + APP_BG + ";");
        return box;
    }

    private VBox createLegend() {
        HBox legend = new HBox(22,
                legendItem(PLANT_COLOR, "растение"),
                legendItem(HERBIVORE_COLOR, "травоядное"),
                legendItem(PREDATOR_COLOR, "хищник")
        );
        legend.setAlignment(Pos.CENTER);

        Label note = new Label("• карта замкнута барьерами: агенты не выходят за пределы поля");
        note.setStyle("-fx-text-fill: " + TEXT_DIM + "; -fx-font-size: 12px;");

        VBox wrapper = new VBox(8, legend, note);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPadding(new Insets(10, 0, 20, 0));
        return wrapper;
    }

    private HBox legendItem(Color color, String text) {
        Circle dot = new Circle(7, color);
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 12px;");
        HBox item = new HBox(6, dot, label);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    // ==================== ЛОГИКА ====================

    private void applySpeedOnTheFly() {
        try {
            TextField field = fields.get("int:Скорость (мс/шаг)");
            if (field == null) return;
            int value = Integer.parseInt(field.getText().trim());
            if (value < 10) {
                value = 10;
                field.setText("10");
            }
            stepIntervalMs = value;

            if (timer != null && timer.getStatus() == Timeline.Status.RUNNING) {
                stopTimer();
                timer = new Timeline(new KeyFrame(Duration.millis(stepIntervalMs), tick -> stepOnce()));
                timer.setCycleCount(Timeline.INDEFINITE);
                timer.play();
            }
            statusLabel.setText("Скорость обновлена: " + stepIntervalMs + " мс/шаг");
        } catch (NumberFormatException ex) {
            statusLabel.setText("Некорректное значение скорости");
        }
    }

    private void resetSimulation() {
        stopTimer();
        try {
            applySettings();
            engine.initialize();
            initialized = true;
            iteration = 0;
            statusLabel.setText("Новая экосистема создана. Можно запускать или делать шаги вручную.");
            redraw();
        } catch (IllegalArgumentException exception) {
            initialized = false;
            statusLabel.setText("Ошибка настройки: " + exception.getMessage());
        }
    }

    private void stepOnce() {
        if (!initialized) {
            resetSimulation();
        }
        if (!initialized) return;
        engine.step();
        iteration++;
        redraw();
        if (engine.isEcosystemCollapsed()) {
            stopTimer();
            statusLabel.setText("Экосистема вымерла. Измените настройки и создайте новую.");
        }
    }

    // ==================== ОТРИСОВКА ====================

    private void redraw() {
        Environment environment = engine.getEnvironment();
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.web(CANVAS_BG));
        g.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

        double cell = Math.min(CANVAS_SIZE / (double) environment.getWidth(),
                CANVAS_SIZE / (double) environment.getHeight());
        double usedWidth = cell * environment.getWidth();
        double usedHeight = cell * environment.getHeight();
        double offsetX = (CANVAS_SIZE - usedWidth) / 2;
        double offsetY = (CANVAS_SIZE - usedHeight) / 2;

        g.setFill(Color.web("#ffffff"));
        g.fillRect(offsetX, offsetY, usedWidth, usedHeight);

        if (cell >= 8) {
            g.setStroke(Color.web(CANVAS_GRID));
            g.setLineWidth(1);
            for (int x = 0; x <= environment.getWidth(); x++) {
                double px = offsetX + x * cell;
                g.strokeLine(px, offsetY, px, offsetY + usedHeight);
            }
            for (int y = 0; y <= environment.getHeight(); y++) {
                double py = offsetY + y * cell;
                g.strokeLine(offsetX, py, offsetX + usedWidth, py);
            }
        }

        double radius = Math.max(1.2, cell * 0.4);
        for (int y = 0; y < environment.getHeight(); y++) {
            for (int x = 0; x < environment.getWidth(); x++) {
                Agent agent = environment.getAgent(x, y);
                if (agent != null) {
                    double cx = offsetX + (x + 0.5) * cell;
                    double cy = offsetY + (y + 0.5) * cell;
                    g.setFill(colorFor(agent.getType()));
                    g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
                }
            }
        }

        g.setStroke(Color.web(WALL));
        g.setLineWidth(4);
        g.strokeRect(offsetX + 2, offsetY + 2, usedWidth - 4, usedHeight - 4);

        SimulationStats stats = engine.collectStats();
        statsLabel.setText(String.format(
                "Итерация: %d     Занято: %d     Растения: %d     Травоядные: %d     Хищники: %d",
                iteration,
                stats.getOccupiedCells(),
                stats.getPlantCount(),
                stats.getHerbivoreCount(),
                stats.getPredatorCount()
        ));
    }

    private Color colorFor(AgentType type) {
        return switch (type) {
            case PLANT -> PLANT_COLOR;
            case HERBIVORE -> HERBIVORE_COLOR;
            case PREDATOR -> PREDATOR_COLOR;
        };
    }

    // ==================== НАСТРОЙКИ ====================

    private void applySettings() {
        fields.forEach((name, field) -> {
            try {
                if (name.startsWith("int:")) {
                    int value = Integer.parseInt(field.getText().trim());
                    if (value < 0) throw new IllegalArgumentException("значение «" + name.substring(4) + "» не может быть отрицательным");
                    ((Consumer<Integer>) field.getUserData()).accept(value);
                } else {
                    double value = Double.parseDouble(field.getText().trim());
                    if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("некорректное значение «" + name.substring(7) + "»");
                    ((Consumer<Double>) field.getUserData()).accept(value);
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("введите число для «" + name.substring(name.indexOf(':') + 1) + "»");
            }
        });
        if (properties.getGridWidth() == 0 || properties.getGridHeight() == 0) {
            throw new IllegalArgumentException("ширина и высота должны быть больше нуля");
        }
    }

    private void addSection(GridPane grid, String title) {
        int row = grid.getRowCount();
        Label label = new Label(title);
        label.setStyle(
                "-fx-font-weight: bold;" +
                        "-fx-font-size: 13px;" +
                        "-fx-text-fill: " + ACCENT + ";" +
                        "-fx-padding: 14 0 4 0;"
        );
        grid.add(label, 0, row, 2, 1);
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + BORDER + ";");
        grid.add(sep, 0, row + 1, 2, 1);
    }

    private void addInt(GridPane grid, String label, Supplier<Integer> getter, Consumer<Integer> setter) {
        addField(grid, label, Integer.toString(getter.get()), "int:" + label, setter);
    }

    private void addDouble(GridPane grid, String label, Supplier<Double> getter, Consumer<Double> setter) {
        addField(grid, label, Double.toString(getter.get()), "double:" + label, setter);
    }

    private void addIntField(GridPane grid, String label, Supplier<Integer> getter, Consumer<Integer> setter) {
        addField(grid, label, Integer.toString(getter.get()), "int:" + label, setter);
    }

    private void addField(GridPane grid, String label, String value, String key, Object setter) {
        int row = grid.getRowCount();
        TextField field = new TextField(value);
        field.setPrefColumnCount(8);
        field.setUserData(setter);
        field.setStyle(
                "-fx-background-color: " + SETTINGS_BG2 + ";" +
                        "-fx-text-fill: " + TEXT + ";" +
                        "-fx-prompt-text-fill: " + TEXT_DIM + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-border-radius: 6;" +
                        "-fx-border-color: " + BORDER + ";" +
                        "-fx-padding: 5 8 5 8;"
        );
        fields.put(key, field);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 12px;");
        grid.add(lbl, 0, row);
        grid.add(field, 1, row);
        GridPane.setHgrow(field, Priority.ALWAYS);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
        }
    }
}