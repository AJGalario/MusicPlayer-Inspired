import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.media.AudioEqualizer;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * HeadSet Player — Native JavaFX Desktop Music Player & Widget
 * Designed to match the HeadSet UI specification (deep violet/lavender theme).
 * Works across Java 17 to Java 25.
 */
public class HeadSetApp extends Application {

    // ── Design Theme Colors ───────────────────────────────────────────────────
    private static final String BG_ROOT   = "#0b0914";
    private static final String BG_SIDE   = "#0f0d1a";
    private static final String BG_CARD   = "#161327";
    private static final String BG_HOVER  = "#1e1a35";
    private static final String BG_ACTIVE = "#2b234d";
    private static final String BORDER_C  = "#262040";
    private static final String ACCENT    = "#8e75ff";
    private static final String ACCENT2   = "#b5a3ff";
    private static final String TEXT_PRI  = "#f0eeff";
    private static final String TEXT_SEC  = "#8a85a8";
    private static final String PLAYER_BG = "#0d0b18";

    // ── Track Model ───────────────────────────────────────────────────────────
    static class Track {
        String title;
        String artist;
        String path;
        String durationStr = "0:00";
        Image art;

        Track(File f) {
            this.path = f.getAbsolutePath();
            this.title = f.getName().replaceAll("\\.[^.]+$", "");
            this.artist = "Unknown Artist";
        }
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private MediaPlayer player;
    private final List<Track> library = new ArrayList<>();
    private int currentIdx = -1;
    private boolean shuffling = false;
    private boolean repeating = false;
    private boolean isDraggingSeek = false;

    // ── UI Components ─────────────────────────────────────────────────────────
    private Stage primaryStage;
    private StackPane contentPane;
    private Node viewHome;
    private Node viewLibrary;
    private Node viewNowPlaying;
    private Node viewEqualizer;

    // Nav buttons
    private Button btnNavHome;
    private Button btnNavNow;
    private Button btnNavLib;
    private Label lblCollectionCount;

    // Bottom Player Bar
    private Label lblBarTitle;
    private Label lblBarArtist;
    private Label lblBarTime;
    private Label lblBarDuration;
    private Slider sldBarSeek;
    private Slider sldBarVol;
    private Button btnBarPlay;
    private Button btnBarShuffle;
    private Button btnBarRepeat;
    private ImageView ivBarArt;
    private StackPane artThumbStack;

    // Now Playing Elements
    private ImageView ivNowArt;
    private Label lblNowTitle;
    private Label lblNowArtist;

    // Equalizer Controls
    private final Slider[] eqSliders = new Slider[10];
    private Slider sldTempo;

    // Container for Home track rows
    private VBox homeTracksBox;
    private VBox libTracksBox;
    private Label lblHomeSongCount;

    // Window dragging
    private double dragOffsetX, dragOffsetY;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // Build main views
        viewHome = buildHomeView();
        viewLibrary = buildLibraryView();
        viewNowPlaying = buildNowPlayingView();
        viewEqualizer = buildEqualizerView();

        contentPane = new StackPane(viewHome);
        contentPane.setStyle("-fx-background-color: " + BG_ROOT + ";");
        HBox.setHgrow(contentPane, Priority.ALWAYS);

        VBox sidebar = buildSidebar();
        HBox mainBody = new HBox(sidebar, contentPane);
        VBox.setVgrow(mainBody, Priority.ALWAYS);

        VBox root = new VBox(buildTitleBar(stage), mainBody, buildBottomPlayerBar());
        root.setStyle("-fx-background-color: " + BG_ROOT + "; -fx-border-color: " + BORDER_C + "; -fx-border-width: 1;");

        Scene scene = new Scene(root, 1180, 750);
        scene.setFill(Color.TRANSPARENT);

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setTitle("HeadSet");
        stage.setScene(scene);
        stage.setMinWidth(920);
        stage.setMinHeight(600);
        stage.show();
        stage.centerOnScreen();

        // Scan local music folder if present
        Path localMusic = Path.of(System.getProperty("user.dir")).resolve("music");
        if (Files.isDirectory(localMusic)) {
            scanDirectory(localMusic.toFile());
        }

        installSystemTray(stage);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. CUSTOM TITLE BAR (Frameless window draggable)
    // ═════════════════════════════════════════════════════════════════════════
    private HBox buildTitleBar(Stage stage) {
        Label logoIcon = new Label("🎧");
        logoIcon.setStyle("-fx-font-size: 16px; -fx-text-fill: " + ACCENT + ";");

        Label logoText = new Label("HeadSet");
        logoText.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnWidgetMode = new Button("📱 Widget Mode");
        btnWidgetMode.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + ACCENT2 + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;");
        btnWidgetMode.setOnMouseEntered(e -> btnWidgetMode.setStyle("-fx-background-color: " + BG_HOVER + "; -fx-text-fill: " + TEXT_PRI + "; -fx-border-color: " + ACCENT + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;"));
        btnWidgetMode.setOnMouseExited(e -> btnWidgetMode.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + ACCENT2 + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 3 10; -fx-font-size: 11px; -fx-cursor: hand;"));
        btnWidgetMode.setOnAction(e -> launchWidgetMode(stage));

        Button btnClose = createWinButton("✕", e -> stage.hide());
        btnClose.setStyle("-fx-background-color: transparent; -fx-text-fill: #ff6e85; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 10;");

        HBox bar = new HBox(8, logoIcon, logoText, spacer, btnWidgetMode, btnClose);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 16));
        bar.setStyle("-fx-background-color: " + BG_SIDE + "; -fx-border-color: " + BORDER_C + "; -fx-border-width: 0 0 1 0;");
        bar.setPrefHeight(38);

        bar.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - stage.getX();
            dragOffsetY = e.getScreenY() - stage.getY();
        });
        bar.setOnMouseDragged(e -> {
            if (!stage.isMaximized()) {
                stage.setX(e.getScreenX() - dragOffsetX);
                stage.setY(e.getScreenY() - dragOffsetY);
            }
        });

        return bar;
    }

    private Button createWinButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 4;");
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: " + BG_HOVER + "; -fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 4;"));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 4;"));
        b.setOnAction(action);
        return b;
    }

    private void launchWidgetMode(Stage stage) {
        stage.setIconified(true);
        new Thread(() -> {
            try {
                Path root = Path.of(System.getProperty("user.dir"));
                Path widgetBat = root.resolve("START_WIDGET_WINDOWS.bat");
                if (Files.exists(widgetBat)) {
                    new ProcessBuilder("cmd.exe", "/c", widgetBat.toString()).directory(root.toFile()).start();
                } else {
                    Path nested = root.resolve("HeadSet-Widget-App").resolve("START_WIDGET_WINDOWS.bat");
                    if (Files.exists(nested)) {
                        new ProcessBuilder("cmd.exe", "/c", nested.toString()).directory(nested.getParent().toFile()).start();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }).start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. SIDEBAR
    // ═════════════════════════════════════════════════════════════════════════
    private VBox buildSidebar() {
        // App brand with icon
        StackPane brandIconBg = new StackPane();
        Rectangle iconRect = new Rectangle(36, 36, Color.web(ACCENT));
        iconRect.setArcWidth(10);
        iconRect.setArcHeight(10);
        Label iconGlyph = new Label("♫");
        iconGlyph.setStyle("-fx-font-size: 18px; -fx-text-fill: white; -fx-font-weight: bold;");
        brandIconBg.getChildren().addAll(iconRect, iconGlyph);

        Label brandTitle = new Label("HeadSet");
        brandTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        Label brandSub = new Label("Your personal sound");
        brandSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SEC + ";");
        VBox brandText = new VBox(1, brandTitle, brandSub);

        HBox brandBox = new HBox(12, brandIconBg, brandText);
        brandBox.setAlignment(Pos.CENTER_LEFT);
        brandBox.setPadding(new Insets(14, 16, 20, 16));

        // Category Header
        Label secLabel = new Label("YOUR SPACE");
        secLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SEC + ";");
        secLabel.setPadding(new Insets(8, 16, 6, 16));

        // Nav items
        btnNavHome = createNavButton("🏠", "Home", () -> switchView(viewHome, btnNavHome));
        btnNavNow = createNavButton("🎵", "Now playing", () -> switchView(viewNowPlaying, btnNavNow));
        btnNavLib = createNavButton("📁", "Your library", () -> switchView(viewLibrary, btnNavLib));
        Button btnNavEq = createNavButton("🎚", "Equalizer", () -> switchView(viewEqualizer, null));

        setNavButtonActive(btnNavHome, true);

        // Bottom Collection Card
        StackPane collCard = new StackPane();
        Rectangle collBg = new Rectangle(195, 62, Color.web(BG_CARD));
        collBg.setArcWidth(14);
        collBg.setArcHeight(14);
        collBg.setStroke(Color.web(BORDER_C));

        StackPane collIconBg = new StackPane();
        Rectangle cIconRect = new Rectangle(34, 34, Color.web(ACCENT, 0.25));
        cIconRect.setArcWidth(8);
        cIconRect.setArcHeight(8);
        Label cIcon = new Label("♫");
        cIcon.setStyle("-fx-text-fill: " + ACCENT2 + "; -fx-font-size: 16px;");
        collIconBg.getChildren().addAll(cIconRect, cIcon);

        Label lblCollTitle = new Label("Your collection");
        lblCollTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        lblCollectionCount = new Label("0 songs in your library");
        lblCollectionCount.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_SEC + ";");
        VBox collText = new VBox(2, lblCollTitle, lblCollectionCount);

        Region cSpacer = new Region();
        HBox.setHgrow(cSpacer, Priority.ALWAYS);

        Button btnAddPlus = new Button("+");
        btnAddPlus.setStyle("-fx-background-color: " + BG_HOVER + "; -fx-text-fill: " + TEXT_PRI + "; -fx-background-radius: 50%; -fx-min-width: 26px; -fx-min-height: 26px; -fx-cursor: hand; -fx-font-size: 14px;");
        btnAddPlus.setOnAction(e -> addFiles());

        HBox collContent = new HBox(10, collIconBg, collText, cSpacer, btnAddPlus);
        collContent.setAlignment(Pos.CENTER_LEFT);
        collContent.setPadding(new Insets(10));
        collCard.getChildren().addAll(collBg, collContent);
        collCard.setOnMouseClicked(e -> switchView(viewLibrary, btnNavLib));

        Region fillSpacer = new Region();
        VBox.setVgrow(fillSpacer, Priority.ALWAYS);

        VBox sidebar = new VBox(4, brandBox, secLabel, btnNavHome, btnNavNow, btnNavLib, btnNavEq, fillSpacer, collCard);
        sidebar.setPadding(new Insets(8, 12, 16, 12));
        sidebar.setPrefWidth(225);
        sidebar.setMinWidth(225);
        sidebar.setMaxWidth(225);
        sidebar.setStyle("-fx-background-color: " + BG_SIDE + "; -fx-border-color: " + BORDER_C + "; -fx-border-width: 0 1 0 0;");

        return sidebar;
    }

    private Button createNavButton(String icon, String label, Runnable action) {
        Button b = new Button(icon + "   " + label);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setStyle(getNavButtonStyle(false));
        b.setOnAction(e -> {
            clearNavActiveStyles();
            setNavButtonActive(b, true);
            action.run();
        });
        b.setOnMouseEntered(e -> {
            if (!b.getStyle().contains(BG_ACTIVE)) {
                b.setStyle(getNavButtonHoverStyle());
            }
        });
        b.setOnMouseExited(e -> {
            if (!b.getStyle().contains(BG_ACTIVE)) {
                b.setStyle(getNavButtonStyle(false));
            }
        });
        return b;
    }

    private void clearNavActiveStyles() {
        if (btnNavHome != null) btnNavHome.setStyle(getNavButtonStyle(false));
        if (btnNavNow != null) btnNavNow.setStyle(getNavButtonStyle(false));
        if (btnNavLib != null) btnNavLib.setStyle(getNavButtonStyle(false));
    }

    private void setNavButtonActive(Button b, boolean active) {
        if (b != null) b.setStyle(getNavButtonStyle(active));
    }

    private String getNavButtonStyle(boolean active) {
        if (active) {
            return "-fx-background-color: " + BG_ACTIVE + "; -fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 9 14; -fx-background-radius: 10; -fx-cursor: hand; -fx-alignment: center-left;";
        } else {
            return "-fx-background-color: transparent; -fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 13px; -fx-padding: 9 14; -fx-background-radius: 10; -fx-cursor: hand; -fx-alignment: center-left;";
        }
    }

    private String getNavButtonHoverStyle() {
        return "-fx-background-color: " + BG_HOVER + "; -fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 13px; -fx-padding: 9 14; -fx-background-radius: 10; -fx-cursor: hand; -fx-alignment: center-left;";
    }

    private void switchView(Node targetView, Button correspondingNav) {
        contentPane.getChildren().setAll(targetView);
        if (correspondingNav != null) {
            clearNavActiveStyles();
            setNavButtonActive(correspondingNav, true);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. HOME VIEW (Exact recreation of user's uploaded mockup)
    // ═════════════════════════════════════════════════════════════════════════
    private Node buildHomeView() {
        // Top Bar: "WELCOME TO HEADSET / Home" + Search + "+ Add music"
        Label lblWelcome = new Label("WELCOME TO HEADSET");
        lblWelcome.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SEC + ";");
        Label lblHomeTitle = new Label("Home");
        lblHomeTitle.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        VBox titleBox = new VBox(1, lblWelcome, lblHomeTitle);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        TextField tfSearch = new TextField();
        tfSearch.setPromptText("🔍  Search your songs");
        tfSearch.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRI + "; -fx-prompt-text-fill: " + TEXT_SEC + "; -fx-background-radius: 20; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 20; -fx-padding: 7 16; -fx-font-size: 12px;");
        tfSearch.setPrefWidth(220);
        tfSearch.textProperty().addListener((obs, o, query) -> filterLibrary(query));

        Button btnAddMusicTop = new Button("+ Add music");
        btnAddMusicTop.setStyle("-fx-background-color: " + BG_ACTIVE + "; -fx-text-fill: " + TEXT_PRI + "; -fx-background-radius: 20; -fx-border-color: " + ACCENT + "; -fx-border-radius: 20; -fx-padding: 7 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAddMusicTop.setOnMouseEntered(e -> btnAddMusicTop.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 20; -fx-border-color: " + ACCENT + "; -fx-border-radius: 20; -fx-padding: 7 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;"));
        btnAddMusicTop.setOnMouseExited(e -> btnAddMusicTop.setStyle("-fx-background-color: " + BG_ACTIVE + "; -fx-text-fill: " + TEXT_PRI + "; -fx-background-radius: 20; -fx-border-color: " + ACCENT + "; -fx-border-radius: 20; -fx-padding: 7 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;"));
        btnAddMusicTop.setOnAction(e -> addFiles());

        HBox topBar = new HBox(12, titleBox, headerSpacer, tfSearch, btnAddMusicTop);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(20, 28, 16, 28));

        // ── HERO GRADIENT BANNER ──
        StackPane heroBanner = buildHeroBanner();

        // ── "MAKE IT YOURS / Jump right in" Section with 3 Cards ──
        Label lblMake = new Label("MAKE IT YOURS");
        lblMake.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SEC + ";");
        Label lblJump = new Label("Jump right in");
        lblJump.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        VBox jumpHeader = new VBox(2, lblMake, lblJump);
        jumpHeader.setPadding(new Insets(20, 28, 12, 28));

        HBox cardsBox = new HBox(16);
        cardsBox.setPadding(new Insets(0, 28, 0, 28));

        Node cardListen = createActionCard("▶", ACCENT, "Listen now", "Pick up where you left off", () -> {
            if (!library.isEmpty()) playTrack(currentIdx >= 0 ? currentIdx : 0);
        });
        Node cardLibrary = createActionCard("📁", "#528bff", "Your library", "Browse every track", () -> {
            switchView(viewLibrary, btnNavLib);
        });
        Node cardEqualizer = createActionCard("🎚", "#e5944d", "Equalizer", "Find your perfect sound", () -> {
            switchView(viewEqualizer, null);
        });
        cardsBox.getChildren().addAll(cardListen, cardLibrary, cardEqualizer);

        // ── "THE SOUNDTRACK TO YOUR DAY / Your songs" Section ──
        Label lblSoundtrack = new Label("THE SOUNDTRACK TO YOUR DAY");
        lblSoundtrack.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_SEC + ";");
        Label lblYourSongs = new Label("Your songs");
        lblYourSongs.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        lblHomeSongCount = new Label("0 tracks");
        lblHomeSongCount.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 10px; -fx-padding: 2 8; -fx-background-radius: 10;");

        HBox songTitleRow = new HBox(8, lblYourSongs, lblHomeSongCount);
        songTitleRow.setAlignment(Pos.CENTER_LEFT);
        VBox songHeader = new VBox(2, lblSoundtrack, songTitleRow);

        Region songSpacer = new Region();
        HBox.setHgrow(songSpacer, Priority.ALWAYS);

        Button btnViewAll = new Button("Full library >");
        btnViewAll.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px; -fx-cursor: hand;");
        btnViewAll.setOnAction(e -> switchView(viewLibrary, btnNavLib));

        HBox songsBar = new HBox(12, songHeader, songSpacer, btnViewAll);
        songsBar.setAlignment(Pos.CENTER_LEFT);
        songsBar.setPadding(new Insets(24, 28, 8, 28));

        homeTracksBox = new VBox(4);
        homeTracksBox.setPadding(new Insets(0, 28, 20, 28));

        VBox contentList = new VBox(topBar, heroBanner, jumpHeader, cardsBox, songsBar, homeTracksBox);
        ScrollPane sp = new ScrollPane(contentList);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: " + BG_ROOT + "; -fx-border-color: transparent;");

        refreshHomeTracks();
        return sp;
    }

    private StackPane buildHeroBanner() {
        StackPane banner = new StackPane();
        banner.setPadding(new Insets(0, 28, 0, 28));

        Rectangle bg = new Rectangle();
        bg.setArcWidth(24);
        bg.setArcHeight(24);
        bg.widthProperty().bind(banner.widthProperty().subtract(56));
        bg.setHeight(210);

        LinearGradient gradient = new LinearGradient(
                0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#311f62")),
                new Stop(0.5, Color.web("#4b2a8a")),
                new Stop(1, Color.web("#6842b0"))
        );
        bg.setFill(gradient);

        // Left Content
        Label tag = new Label("✦  YOUR MUSIC, YOUR WAY");
        tag.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: " + ACCENT2 + ";");

        Label headline = new Label("Press play on\nyour world.");
        headline.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white; -fx-line-spacing: -4px;");

        Label subtitle = new Label("All your favorite songs in one beautiful place. Set the mood,\nturn up the volume, and make it yours.");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #d3caff;");

        Button btnListenNow = new Button("▶  Listen now");
        btnListenNow.setStyle("-fx-background-color: white; -fx-text-fill: #3b1e70; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 8 18; -fx-background-radius: 20; -fx-cursor: hand;");
        btnListenNow.setOnAction(e -> {
            if (!library.isEmpty()) playTrack(currentIdx >= 0 ? currentIdx : 0);
        });

        Button btnExplore = new Button("Explore library  ↗");
        btnExplore.setStyle("-fx-background-color: rgba(255,255,255,0.15); -fx-text-fill: white; -fx-font-size: 12px; -fx-padding: 8 18; -fx-background-radius: 20; -fx-cursor: hand;");
        btnExplore.setOnAction(e -> switchView(viewLibrary, btnNavLib));

        HBox btnRow = new HBox(12, btnListenNow, btnExplore);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox leftBox = new VBox(8, tag, headline, subtitle, btnRow);
        leftBox.setAlignment(Pos.CENTER_LEFT);
        leftBox.setPadding(new Insets(20, 24, 20, 32));

        // Right Content: Vinyl Record Graphic & Waveform
        StackPane vinylGraphic = buildVinylGraphic();
        Label waveText = new Label("GOOD MUSIC • GOOD ENERGY   |||||!|||");
        waveText.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: rgba(255,255,255,0.6); -fx-letter-spacing: 1px;");

        VBox rightBox = new VBox(6, vinylGraphic, waveText);
        rightBox.setAlignment(Pos.CENTER);
        rightBox.setPadding(new Insets(10, 32, 10, 0));

        Region bannerSpacer = new Region();
        HBox.setHgrow(bannerSpacer, Priority.ALWAYS);

        HBox bannerContent = new HBox(leftBox, bannerSpacer, rightBox);
        bannerContent.setAlignment(Pos.CENTER_LEFT);

        banner.getChildren().addAll(bg, bannerContent);
        return banner;
    }

    private StackPane buildVinylGraphic() {
        StackPane vinyl = new StackPane();
        Circle outer = new Circle(62, Color.web("#171424"));
        outer.setStroke(Color.web("#382f55"));
        outer.setStrokeWidth(3);

        Circle ring1 = new Circle(50, Color.TRANSPARENT);
        ring1.setStroke(Color.web("#262040"));
        ring1.setStrokeWidth(2);

        Circle ring2 = new Circle(38, Color.TRANSPARENT);
        ring2.setStroke(Color.web("#332b55"));
        ring2.setStrokeWidth(1.5);

        Circle labelCircle = new Circle(22, Color.web(ACCENT));
        Label labelText = new Label("HEAD\nSET");
        labelText.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: white; -fx-alignment: center;");

        Circle centerHole = new Circle(5, Color.web("#171424"));

        vinyl.getChildren().addAll(outer, ring1, ring2, labelCircle, labelText, centerHole);
        return vinyl;
    }

    private Node createActionCard(String icon, String colorHex, String title, String subtitle, Runnable action) {
        StackPane card = new StackPane();
        HBox.setHgrow(card, Priority.ALWAYS);

        Rectangle bg = new Rectangle();
        bg.setArcWidth(16);
        bg.setArcHeight(16);
        bg.setFill(Color.web(BG_CARD));
        bg.setStroke(Color.web(BORDER_C));
        bg.widthProperty().bind(card.widthProperty());
        bg.setHeight(84);

        StackPane iconBg = new StackPane();
        Rectangle ir = new Rectangle(34, 34, Color.web(colorHex, 0.2));
        ir.setArcWidth(8);
        ir.setArcHeight(8);
        Label iLbl = new Label(icon);
        iLbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-font-size: 14px;");
        iconBg.getChildren().addAll(ir, iLbl);

        Label lblT = new Label(title);
        lblT.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        Label lblSub = new Label(subtitle);
        lblSub.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SEC + ";");
        VBox textBox = new VBox(2, lblT, lblSub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label arrow = new Label("↗");
        arrow.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_SEC + ";");

        HBox content = new HBox(12, iconBg, textBox, sp, arrow);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(16));

        card.getChildren().addAll(bg, content);
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseClicked(e -> action.run());

        card.setOnMouseEntered(e -> {
            bg.setFill(Color.web(BG_HOVER));
            arrow.setStyle("-fx-font-size: 14px; -fx-text-fill: " + ACCENT2 + ";");
        });
        card.setOnMouseExited(e -> {
            bg.setFill(Color.web(BG_CARD));
            arrow.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_SEC + ";");
        });

        return card;
    }

    private void refreshHomeTracks() {
        if (homeTracksBox == null) return;
        homeTracksBox.getChildren().clear();

        int count = library.size();
        if (lblHomeSongCount != null) lblHomeSongCount.setText(count + (count == 1 ? " track" : " tracks"));
        if (lblCollectionCount != null) lblCollectionCount.setText(count + (count == 1 ? " song in your library" : " songs in your library"));

        if (library.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(30));
            Label msg = new Label("No tracks added yet. Click '+ Add music' above or add a folder to get started.");
            msg.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px;");
            empty.getChildren().add(msg);
            homeTracksBox.getChildren().add(empty);
            return;
        }

        // Show top 5 tracks on Home
        int limit = Math.min(5, library.size());
        for (int i = 0; i < limit; i++) {
            homeTracksBox.getChildren().add(createTrackRow(i, library.get(i)));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. LIBRARY VIEW
    // ═════════════════════════════════════════════════════════════════════════
    private Node buildLibraryView() {
        Label title = new Label("Your Library");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnAddFolder = new Button("+ Add Folder");
        btnAddFolder.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAddFolder.setOnAction(e -> addFolder());

        Button btnAddFiles = new Button("+ Add Files");
        btnAddFiles.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRI + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 16; -fx-font-size: 12px; -fx-cursor: hand;");
        btnAddFiles.setOnAction(e -> addFiles());

        HBox top = new HBox(12, title, spacer, btnAddFolder, btnAddFiles);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(24, 28, 16, 28));

        // Column Header
        HBox cols = new HBox();
        cols.setPadding(new Insets(8, 28, 8, 28));
        cols.setStyle("-fx-border-color: " + BORDER_C + "; -fx-border-width: 0 0 1 0;");

        Label cNum = new Label("#");
        cNum.setMinWidth(40);
        cNum.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label cTitle = new Label("TITLE");
        HBox.setHgrow(cTitle, Priority.ALWAYS);
        cTitle.setMaxWidth(Double.MAX_VALUE);
        cTitle.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label cArtist = new Label("ARTIST");
        cArtist.setMinWidth(200);
        cArtist.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label cTime = new Label("TIME");
        cTime.setMinWidth(70);
        cTime.setAlignment(Pos.CENTER_RIGHT);
        cTime.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px; -fx-font-weight: bold;");

        cols.getChildren().addAll(cNum, cTitle, cArtist, cTime);

        libTracksBox = new VBox(2);
        libTracksBox.setPadding(new Insets(6, 28, 20, 28));

        ScrollPane sp = new ScrollPane(new VBox(top, cols, libTracksBox));
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: " + BG_ROOT + "; -fx-border-color: transparent;");

        refreshLibraryView();
        return sp;
    }

    private void refreshLibraryView() {
        if (libTracksBox == null) return;
        libTracksBox.getChildren().clear();

        if (library.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(80));

            Label icon = new Label("♫");
            icon.setStyle("-fx-font-size: 48px; -fx-text-fill: " + ACCENT + ";");

            Label msg1 = new Label("Your library is empty");
            msg1.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");

            Label msg2 = new Label("Add audio files or an entire folder to start listening.");
            msg2.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SEC + ";");

            Button btnAdd = new Button("+ Add Folder");
            btnAdd.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 20; -fx-font-size: 13px; -fx-cursor: hand;");
            btnAdd.setOnAction(e -> addFolder());

            empty.getChildren().addAll(icon, msg1, msg2, btnAdd);
            libTracksBox.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < library.size(); i++) {
            libTracksBox.getChildren().add(createTrackRow(i, library.get(i)));
        }
    }

    private HBox createTrackRow(int index, Track t) {
        int realIndex = library.indexOf(t);
        boolean isPlayingThis = (realIndex == currentIdx);

        Label num = new Label(String.valueOf(index + 1));
        num.setMinWidth(40);
        num.setStyle("-fx-text-fill: " + (isPlayingThis ? ACCENT2 : TEXT_SEC) + "; -fx-font-size: 12px;");

        StackPane artBox = new StackPane();
        Rectangle artRect = new Rectangle(36, 36, Color.web(BG_CARD));
        artRect.setArcWidth(8);
        artRect.setArcHeight(8);
        Label artGlyph = new Label(isPlayingThis ? "▶" : "♫");
        artGlyph.setStyle("-fx-text-fill: " + (isPlayingThis ? ACCENT : TEXT_SEC) + "; -fx-font-size: 14px;");
        ImageView rowIv = new ImageView();
        rowIv.setFitWidth(36);
        rowIv.setFitHeight(36);
        if (t.art != null) {
            rowIv.setImage(t.art);
            artGlyph.setVisible(false);
        }
        artBox.getChildren().addAll(artRect, artGlyph, rowIv);

        Label title = new Label(t.title);
        title.setStyle("-fx-text-fill: " + (isPlayingThis ? ACCENT2 : TEXT_PRI) + "; -fx-font-size: 13px; -fx-font-weight: " + (isPlayingThis ? "bold" : "normal") + ";");
        Label artist = new Label(t.artist);
        artist.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 11px;");
        VBox info = new VBox(2, title, artist);
        HBox.setHgrow(info, Priority.ALWAYS);

        Label dur = new Label(t.durationStr);
        dur.setMinWidth(70);
        dur.setAlignment(Pos.CENTER_RIGHT);
        dur.setStyle("-fx-text-fill: " + TEXT_SEC + "; -fx-font-size: 12px;");

        HBox row = new HBox(12, num, artBox, info, dur);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 14, 8, 14));
        row.setStyle("-fx-background-color: " + (isPlayingThis ? BG_ACTIVE : "transparent") + "; -fx-background-radius: 8; -fx-cursor: hand;");

        row.setOnMouseEntered(e -> {
            if (library.indexOf(t) != currentIdx) {
                row.setStyle("-fx-background-color: " + BG_HOVER + "; -fx-background-radius: 8; -fx-cursor: hand;");
            }
        });
        row.setOnMouseExited(e -> {
            boolean activeNow = (library.indexOf(t) == currentIdx);
            row.setStyle("-fx-background-color: " + (activeNow ? BG_ACTIVE : "transparent") + "; -fx-background-radius: 8; -fx-cursor: hand;");
        });
        row.setOnMouseClicked(e -> {
            if (realIndex >= 0) playTrack(realIndex);
        });

        return row;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. NOW PLAYING VIEW
    // ═════════════════════════════════════════════════════════════════════════
    private Node buildNowPlayingView() {
        // Big Vinyl Artwork
        StackPane artStack = new StackPane();
        Circle glow = new Circle(140, Color.web(ACCENT, 0.15));
        Rectangle artBg = new Rectangle(240, 240, Color.web(BG_CARD));
        artBg.setArcWidth(20);
        artBg.setArcHeight(20);
        artBg.setStroke(Color.web(BORDER_C));

        Label bigIcon = new Label("♫");
        bigIcon.setStyle("-fx-font-size: 72px; -fx-text-fill: " + ACCENT + ";");

        ivNowArt = new ImageView();
        ivNowArt.setFitWidth(240);
        ivNowArt.setFitHeight(240);

        artStack.getChildren().addAll(glow, artBg, bigIcon, ivNowArt);

        lblNowTitle = new Label("No track playing");
        lblNowTitle.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        lblNowArtist = new Label("Select a song to start");
        lblNowArtist.setStyle("-fx-font-size: 14px; -fx-text-fill: " + TEXT_SEC + ";");

        VBox titleBox = new VBox(6, lblNowTitle, lblNowArtist);
        titleBox.setAlignment(Pos.CENTER);

        // Quick big controls
        Button btnBigPrev = new Button("⏮");
        btnBigPrev.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 22px; -fx-cursor: hand;");
        btnBigPrev.setOnAction(e -> playPrev());

        Button btnBigPlay = new Button("▶");
        btnBigPlay.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 56px; -fx-min-height: 56px; -fx-font-size: 22px; -fx-cursor: hand;");
        btnBigPlay.setOnAction(e -> togglePlay());

        Button btnBigNext = new Button("⏭");
        btnBigNext.setStyle("-fx-background-color: transparent; -fx-text-fill: " + TEXT_PRI + "; -fx-font-size: 22px; -fx-cursor: hand;");
        btnBigNext.setOnAction(e -> playNext());

        HBox bigCtrls = new HBox(20, btnBigPrev, btnBigPlay, btnBigNext);
        bigCtrls.setAlignment(Pos.CENTER);

        VBox center = new VBox(24, artStack, titleBox, bigCtrls);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(40));

        ScrollPane sp = new ScrollPane(center);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: " + BG_ROOT + "; -fx-border-color: transparent;");
        return sp;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. EQUALIZER VIEW (Bass, Presets, 10 bands, Tempo/Speed)
    // ═════════════════════════════════════════════════════════════════════════
    private Node buildEqualizerView() {
        Label title = new Label("Equalizer & Sound Tuning");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");

        Label subtitle = new Label("Customize frequencies, boost bass, or adjust tempo to match your ears.");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SEC + ";");

        VBox header = new VBox(4, title, subtitle);
        header.setPadding(new Insets(24, 28, 16, 28));

        // Presets chips
        HBox presetBar = new HBox(10);
        presetBar.setPadding(new Insets(0, 28, 16, 28));
        String[] presets = {"Flat", "Bass Boost", "Rock", "Pop", "Jazz", "Vocal", "Classical"};
        for (String p : presets) {
            Button b = new Button(p);
            b.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRI + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 16; -fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11px; -fx-cursor: hand;");
            b.setOnAction(e -> applyPreset(p));
            b.setOnMouseEntered(ev -> b.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-border-color: " + ACCENT + "; -fx-border-radius: 16; -fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11px; -fx-cursor: hand;"));
            b.setOnMouseExited(ev -> b.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRI + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 16; -fx-background-radius: 16; -fx-padding: 6 14; -fx-font-size: 11px; -fx-cursor: hand;"));
            presetBar.getChildren().add(b);
        }

        // 10-Band Equalizer Sliders
        HBox bandsBox = new HBox(22);
        bandsBox.setAlignment(Pos.BOTTOM_CENTER);
        bandsBox.setPadding(new Insets(16));

        String[] freqs = {"32Hz", "63Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"};
        for (int i = 0; i < 10; i++) {
            Slider s = new Slider(-12, 12, 0);
            s.setOrientation(Orientation.VERTICAL);
            s.setPrefHeight(180);
            s.setStyle("-fx-base: " + ACCENT + "; -fx-accent: " + ACCENT + ";");
            eqSliders[i] = s;

            final int bandIndex = i;
            Label lblGain = new Label("0 dB");
            lblGain.setStyle("-fx-font-size: 10px; -fx-text-fill: " + ACCENT2 + ";");
            s.valueProperty().addListener((obs, o, nv) -> {
                lblGain.setText(String.format("%+.0f dB", nv.doubleValue()));
                if (player != null) {
                    AudioEqualizer eq = player.getAudioEqualizer();
                    if (eq != null && eq.getBands().size() > bandIndex) {
                        eq.getBands().get(bandIndex).setGain(nv.doubleValue());
                    }
                }
            });

            Label lblF = new Label(freqs[i]);
            lblF.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_SEC + ";");

            VBox bandCol = new VBox(6, lblGain, s, lblF);
            bandCol.setAlignment(Pos.CENTER);
            bandsBox.getChildren().add(bandCol);
        }

        StackPane eqCard = new StackPane();
        Rectangle eqBg = new Rectangle();
        eqBg.setArcWidth(16);
        eqBg.setArcHeight(16);
        eqBg.setFill(Color.web(BG_CARD));
        eqBg.setStroke(Color.web(BORDER_C));
        eqBg.widthProperty().bind(eqCard.widthProperty());
        eqBg.heightProperty().bind(bandsBox.heightProperty().add(30));
        eqCard.getChildren().addAll(eqBg, bandsBox);
        eqCard.setPadding(new Insets(0, 28, 20, 28));

        // Tempo / Speed Slider
        Label lblTempoTitle = new Label("Playback Speed & Tempo");
        lblTempoTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");

        sldTempo = new Slider(0.5, 2.0, 1.0);
        sldTempo.setPrefWidth(300);
        sldTempo.setStyle("-fx-base: " + ACCENT + "; -fx-accent: " + ACCENT + ";");

        Label lblTempoVal = new Label("1.00x");
        lblTempoVal.setStyle("-fx-font-size: 13px; -fx-text-fill: " + ACCENT2 + "; -fx-font-weight: bold;");
        sldTempo.valueProperty().addListener((obs, o, nv) -> {
            lblTempoVal.setText(String.format("%.2fx", nv.doubleValue()));
            if (player != null) {
                player.setRate(nv.doubleValue());
            }
        });

        HBox tempoRow = new HBox(16, sldTempo, lblTempoVal);
        tempoRow.setAlignment(Pos.CENTER_LEFT);

        VBox tempoBox = new VBox(8, lblTempoTitle, tempoRow);
        tempoBox.setStyle("-fx-background-color: " + BG_CARD + "; -fx-background-radius: 16; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 16; -fx-padding: 18;");
        HBox tempoWrap = new HBox(tempoBox);
        tempoWrap.setPadding(new Insets(0, 28, 24, 28));

        VBox eqContent = new VBox(header, presetBar, eqCard, tempoWrap);
        ScrollPane sp = new ScrollPane(eqContent);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background: transparent; -fx-background-color: " + BG_ROOT + "; -fx-border-color: transparent;");
        return sp;
    }

    private void applyPreset(String name) {
        double[] gains = switch (name) {
            case "Bass Boost" -> new double[]{9, 8, 6, 2, 0, 0, 0, 0, 0, 0};
            case "Rock" -> new double[]{5, 3, 2, 0, -1, 0, 2, 4, 5, 5};
            case "Pop" -> new double[]{-1, 0, 2, 4, 5, 4, 2, 0, -1, -1};
            case "Jazz" -> new double[]{4, 3, 1, 2, 0, 0, 1, 2, 3, 3};
            case "Vocal" -> new double[]{-2, -2, 0, 3, 5, 5, 3, 1, 0, -2};
            case "Classical" -> new double[]{5, 4, 3, 1, 0, 0, 1, 3, 4, 4};
            default -> new double[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        };

        for (int i = 0; i < 10; i++) {
            if (eqSliders[i] != null) {
                eqSliders[i].setValue(gains[i]);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 7. BOTTOM PLAYER BAR (Matching footer in uploaded image)
    // ═════════════════════════════════════════════════════════════════════════
    private HBox buildBottomPlayerBar() {
        // Track Artwork Thumb
        artThumbStack = new StackPane();
        Rectangle thumbBg = new Rectangle(46, 46, Color.web(BG_CARD));
        thumbBg.setArcWidth(10);
        thumbBg.setArcHeight(10);
        Label thumbGlyph = new Label("♫");
        thumbGlyph.setStyle("-fx-font-size: 18px; -fx-text-fill: " + ACCENT + ";");

        ivBarArt = new ImageView();
        ivBarArt.setFitWidth(46);
        ivBarArt.setFitHeight(46);

        artThumbStack.getChildren().addAll(thumbBg, thumbGlyph, ivBarArt);

        lblBarTitle = new Label("Want You (feat. Miranda Glory)");
        lblBarTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRI + ";");
        lblBarTitle.setMaxWidth(200);

        lblBarArtist = new Label("flynx");
        lblBarArtist.setStyle("-fx-font-size: 11px; -fx-text-fill: " + TEXT_SEC + ";");

        VBox metaBox = new VBox(2, lblBarTitle, lblBarArtist);
        metaBox.setAlignment(Pos.CENTER_LEFT);

        HBox left = new HBox(12, artThumbStack, metaBox);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMinWidth(260);

        // Center Controls & Progress
        btnBarShuffle = createIconCtrlButton("⇄", 14, e -> {
            shuffling = !shuffling;
            btnBarShuffle.setStyle(getCtrlBtnStyle(shuffling));
        });
        Button btnBarPrev = createIconCtrlButton("⏮", 16, e -> playPrev());

        btnBarPlay = new Button("▶");
        btnBarPlay.setStyle("-fx-background-color: " + ACCENT + "; -fx-text-fill: white; -fx-background-radius: 50%; -fx-min-width: 38px; -fx-min-height: 38px; -fx-font-size: 14px; -fx-cursor: hand;");
        btnBarPlay.setOnAction(e -> togglePlay());

        Button btnBarNext = createIconCtrlButton("⏭", 16, e -> playNext());
        btnBarRepeat = createIconCtrlButton("↻", 14, e -> {
            repeating = !repeating;
            btnBarRepeat.setStyle(getCtrlBtnStyle(repeating));
        });

        HBox ctrlButtons = new HBox(12, btnBarShuffle, btnBarPrev, btnBarPlay, btnBarNext, btnBarRepeat);
        ctrlButtons.setAlignment(Pos.CENTER);

        // Progress row
        lblBarTime = new Label("0:00");
        lblBarTime.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_SEC + ";");

        sldBarSeek = new Slider(0, 1, 0);
        sldBarSeek.setStyle("-fx-base: " + ACCENT + "; -fx-accent: " + ACCENT + ";");
        HBox.setHgrow(sldBarSeek, Priority.ALWAYS);
        sldBarSeek.setMaxWidth(460);

        sldBarSeek.setOnMousePressed(e -> isDraggingSeek = true);
        sldBarSeek.setOnMouseReleased(e -> {
            isDraggingSeek = false;
            if (player != null) {
                Duration dur = player.getTotalDuration();
                if (dur != null && !dur.isUnknown()) {
                    player.seek(dur.multiply(sldBarSeek.getValue()));
                }
            }
        });

        lblBarDuration = new Label("0:00");
        lblBarDuration.setStyle("-fx-font-size: 10px; -fx-text-fill: " + TEXT_SEC + ";");

        HBox progressRow = new HBox(8, lblBarTime, sldBarSeek, lblBarDuration);
        progressRow.setAlignment(Pos.CENTER);

        VBox center = new VBox(4, ctrlButtons, progressRow);
        center.setAlignment(Pos.CENTER);
        HBox.setHgrow(center, Priority.ALWAYS);

        // Right side: Volume and "Open player" button (as shown in image)
        Label volIcon = new Label("🔊");
        volIcon.setStyle("-fx-font-size: 12px; -fx-text-fill: " + TEXT_SEC + ";");

        sldBarVol = new Slider(0, 1, 0.8);
        sldBarVol.setPrefWidth(85);
        sldBarVol.setStyle("-fx-base: " + ACCENT + "; -fx-accent: " + ACCENT + ";");
        sldBarVol.valueProperty().addListener((obs, o, nv) -> {
            if (player != null) player.setVolume(nv.doubleValue());
        });

        Button btnOpenPlayer = new Button("Open player  ⤢");
        btnOpenPlayer.setStyle("-fx-background-color: " + BG_CARD + "; -fx-text-fill: " + TEXT_PRI + "; -fx-border-color: " + BORDER_C + "; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 12; -fx-font-size: 11px; -fx-cursor: hand;");
        btnOpenPlayer.setOnAction(e -> switchView(viewNowPlaying, btnNavNow));

        HBox right = new HBox(12, volIcon, sldBarVol, btnOpenPlayer);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(220);

        HBox bar = new HBox(16, left, center, right);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 24, 10, 24));
        bar.setStyle("-fx-background-color: " + PLAYER_BG + "; -fx-border-color: " + BORDER_C + "; -fx-border-width: 1 0 0 0;");
        bar.setPrefHeight(74);

        return bar;
    }

    private Button createIconCtrlButton(String text, double fontSize, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button(text);
        b.setStyle(getCtrlBtnStyle(false) + "; -fx-font-size: " + fontSize + "px;");
        b.setOnAction(action);
        return b;
    }

    private String getCtrlBtnStyle(boolean active) {
        if (active) {
            return "-fx-background-color: transparent; -fx-text-fill: " + ACCENT2 + "; -fx-cursor: hand;";
        } else {
            return "-fx-background-color: transparent; -fx-text-fill: " + TEXT_SEC + "; -fx-cursor: hand;";
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 8. AUDIO PLAYBACK ENGINE (javafx.media)
    // ═════════════════════════════════════════════════════════════════════════
    private void playTrack(int idx) {
        if (idx < 0 || idx >= library.size()) return;
        currentIdx = idx;

        if (player != null) {
            player.stop();
            player.dispose();
            player = null;
        }

        Track track = library.get(idx);
        try {
            Media media = new Media(new File(track.path).toURI().toString());
            player = new MediaPlayer(media);
            player.setVolume(sldBarVol.getValue());
            if (sldTempo != null) player.setRate(sldTempo.getValue());

            // Initialize Audio Equalizer when ready
            player.setOnReady(() -> {
                Platform.runLater(() -> {
                    AudioEqualizer eq = player.getAudioEqualizer();
                    if (eq != null) {
                        eq.setEnabled(true);
                        int bandCount = Math.min(10, eq.getBands().size());
                        for (int i = 0; i < bandCount; i++) {
                            if (eqSliders[i] != null) {
                                eq.getBands().get(i).setGain(eqSliders[i].getValue());
                            }
                        }
                    }
                    Duration total = player.getTotalDuration();
                    track.durationStr = formatDuration(total);
                    lblBarDuration.setText(track.durationStr);
                    refreshHomeTracks();
                    refreshLibraryView();
                });
            });

            // Metadata extraction (Title, Artist, Album Art)
            media.getMetadata().addListener((MapChangeListener<String, Object>) change -> {
                if (!change.wasAdded()) return;
                Platform.runLater(() -> {
                    String key = String.valueOf(change.getKey());
                    switch (key) {
                        case "title" -> {
                            track.title = change.getValueAdded().toString();
                            updateTrackUI(track);
                        }
                        case "artist" -> {
                            track.artist = change.getValueAdded().toString();
                            updateTrackUI(track);
                        }
                        case "image" -> {
                            if (change.getValueAdded() instanceof Image img) {
                                track.art = img;
                                ivBarArt.setImage(img);
                                ivNowArt.setImage(img);
                            }
                        }
                    }
                });
            });

            // Position updater
            player.currentTimeProperty().addListener((obs, oldV, curTime) -> {
                Platform.runLater(() -> {
                    if (!isDraggingSeek) {
                        Duration total = player.getTotalDuration();
                        if (total != null && !total.isUnknown() && total.toMillis() > 0) {
                            sldBarSeek.setValue(curTime.toMillis() / total.toMillis());
                        }
                        lblBarTime.setText(formatDuration(curTime));
                    }
                });
            });

            player.setOnEndOfMedia(() -> {
                Platform.runLater(() -> {
                    if (repeating) {
                        player.seek(Duration.ZERO);
                        player.play();
                    } else {
                        playNext();
                    }
                });
            });

            player.play();
            updateTrackUI(track);
            btnBarPlay.setText("⏸");
            refreshHomeTracks();
            refreshLibraryView();

        } catch (Exception ex) {
            System.err.println("Error playing audio: " + ex.getMessage());
        }
    }

    private void togglePlay() {
        if (player == null) {
            if (!library.isEmpty()) playTrack(0);
            return;
        }
        if (player.getStatus() == MediaPlayer.Status.PLAYING) {
            player.pause();
            btnBarPlay.setText("▶");
        } else {
            player.play();
            btnBarPlay.setText("⏸");
        }
    }

    private void playNext() {
        if (library.isEmpty()) return;
        int next;
        if (shuffling) {
            next = new Random().nextInt(library.size());
        } else {
            next = (currentIdx + 1) % library.size();
        }
        playTrack(next);
    }

    private void playPrev() {
        if (library.isEmpty()) return;
        if (player != null && player.getCurrentTime().greaterThan(Duration.seconds(3))) {
            player.seek(Duration.ZERO);
            return;
        }
        int prev = (currentIdx - 1 + library.size()) % library.size();
        playTrack(prev);
    }

    private void updateTrackUI(Track t) {
        if (lblBarTitle != null) lblBarTitle.setText(t.title);
        if (lblBarArtist != null) lblBarArtist.setText(t.artist);
        if (lblNowTitle != null) lblNowTitle.setText(t.title);
        if (lblNowArtist != null) lblNowArtist.setText(t.artist);
        if (t.art != null) {
            if (ivBarArt != null) ivBarArt.setImage(t.art);
            if (ivNowArt != null) ivNowArt.setImage(t.art);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 9. FILE IMPORT & SCANNING
    // ═════════════════════════════════════════════════════════════════════════
    private void addFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add Music Files");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Audio Files (*.mp3, *.m4a, *.wav, *.aac, *.flac, *.ogg)",
                "*.mp3", "*.m4a", "*.wav", "*.aac", "*.flac", "*.ogg", "*.opus"
        ));
        List<File> files = fc.showOpenMultipleDialog(primaryStage);
        if (files == null) return;

        for (File f : files) {
            if (library.stream().noneMatch(t -> t.path.equals(f.getAbsolutePath()))) {
                library.add(new Track(f));
            }
        }
        refreshHomeTracks();
        refreshLibraryView();
    }

    private void addFolder() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Add Music Folder");
        File folder = dc.showDialog(primaryStage);
        if (folder != null) {
            scanDirectory(folder);
        }
    }

    private void scanDirectory(File dir) {
        new Thread(() -> {
            try (Stream<Path> stream = Files.walk(dir.toPath())) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().matches("(?i).*\\.(mp3|m4a|wav|aac|flac|ogg|opus)$"))
                        .filter(p -> library.stream().noneMatch(t -> t.path.equals(p.toString())))
                        .forEach(p -> library.add(new Track(p.toFile())));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            Platform.runLater(() -> {
                refreshHomeTracks();
                refreshLibraryView();
            });
        }).start();
    }

    private void filterLibrary(String query) {
        if (query == null || query.isBlank()) {
            refreshLibraryView();
            return;
        }
        String q = query.toLowerCase();
        if (libTracksBox != null) {
            libTracksBox.getChildren().clear();
            List<Track> matched = library.stream()
                    .filter(t -> t.title.toLowerCase().contains(q) || t.artist.toLowerCase().contains(q))
                    .toList();
            for (int i = 0; i < matched.size(); i++) {
                libTracksBox.getChildren().add(createTrackRow(i, matched.get(i)));
            }
        }
    }

    private static String formatDuration(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) return "0:00";
        int secs = (int) d.toSeconds();
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 10. SYSTEM TRAY (Runs as background widget / mini icon)
    // ═════════════════════════════════════════════════════════════════════════
    private void installSystemTray(Stage stage) {
        if (!java.awt.SystemTray.isSupported()) return;
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(32, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(142, 117, 255));
            g.fillOval(0, 0, 31, 31);
            g.setColor(java.awt.Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g.drawArc(7, 5, 18, 14, 0, 180);
            g.fillRoundRect(4, 15, 6, 9, 3, 3);
            g.fillRoundRect(22, 15, 6, 9, 3, 3);
            g.dispose();

            java.awt.TrayIcon tray = new java.awt.TrayIcon(img, "HeadSet");
            tray.setImageAutoSize(true);

            java.awt.PopupMenu menu = new java.awt.PopupMenu();
            java.awt.MenuItem itemShow = new java.awt.MenuItem("Show HeadSet");
            java.awt.MenuItem itemPlay = new java.awt.MenuItem("Play / Pause");
            java.awt.MenuItem itemExit = new java.awt.MenuItem("Exit");

            itemShow.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));
            itemPlay.addActionListener(e -> Platform.runLater(this::togglePlay));
            itemExit.addActionListener(e -> {
                Platform.exit();
                System.exit(0);
            });

            menu.add(itemShow);
            menu.add(itemPlay);
            menu.addSeparator();
            menu.add(itemExit);

            tray.setPopupMenu(menu);
            tray.addActionListener(e -> Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            }));

            java.awt.SystemTray.getSystemTray().add(tray);
        } catch (Exception ex) {
            System.err.println("SystemTray init: " + ex.getMessage());
        }
    }

    @Override
    public void stop() {
        if (player != null) player.dispose();
        System.exit(0);
    }
}
