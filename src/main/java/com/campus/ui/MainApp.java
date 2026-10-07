package com.campus.ui;

import com.campus.dao.DatabaseConnection;
import com.campus.dao.EventDAO;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MainApp extends Application {

    private Stage primaryStage;

    // Database memory stores (synced with SQLite via DAO)
    private static final Map<String, String> registeredClubs = new HashMap<>();
    private static final Map<String, String> clubCredentials = new HashMap<>();
    private static final Map<String, String> clubAvatarPaths = new HashMap<>();

    private static final Map<String, StudentSession> registeredStudents = new HashMap<>();
    private static final Map<String, String> studentCredentials = new HashMap<>();
    private static final Map<String, String> studentAvatarPaths = new HashMap<>();

    private static final List<CampusEventCardData> eventRepository = new ArrayList<>();
    private static final List<PastCampusEvent> pastCampusArchive = new ArrayList<>();
    private static final Set<String> registrationDuplicatesStore = new HashSet<>();

    private String currentClubEmail = "ieee@gmail.com";
    private String currentClubName = "IEEE Student Branch";
    private StudentSession currentStudent = null;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Initialize SQLite & load data via DAO
        DatabaseConnection.initialize();
        loadAllDataFromDatabase();

        primaryStage.setTitle("Campus Event Hub - Unified Portal");
        showRoleSelectionView();
        primaryStage.show();
    }

    // ==========================================
    // DATABASE SYNC VIA DAO LAYER
    // ==========================================
    private static void loadAllDataFromDatabase() {
        EventDAO.loadClubs(registeredClubs, clubCredentials, clubAvatarPaths);

        Map<String, Object> tempStudents = new HashMap<>();
        EventDAO.loadStudents(tempStudents, studentCredentials, studentAvatarPaths,
                (roll, name, email, dept) -> new StudentSession(roll, name, email, dept));
        tempStudents.forEach((k, v) -> registeredStudents.put(k, (StudentSession) v));

        eventRepository.clear();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM events ORDER BY id DESC")) {

            while (rs.next()) {
                int evId = rs.getInt("id");
                CampusEventCardData ev = new CampusEventCardData(
                        evId,
                        rs.getString("name"),
                        rs.getString("club_name"),
                        rs.getString("type"),
                        rs.getInt("credits"),
                        rs.getString("status"),
                        rs.getString("student_coords"),
                        rs.getString("faculty_coords"),
                        rs.getInt("booked_seats"),
                        rs.getInt("total_capacity"),
                        rs.getString("color_theme"),
                        rs.getString("poster_logo"),
                        rs.getString("time_left"),
                        rs.getString("reg_amount"),
                        rs.getString("whatsapp_link"),
                        rs.getString("payment_qr_path"),
                        rs.getInt("cert_provided") == 1,
                        rs.getString("cert_timeline")
                );

                try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM registrations WHERE event_id = ?")) {
                    ps.setInt(1, evId);
                    try (ResultSet rsa = ps.executeQuery()) {
                        while (rsa.next()) {
                            ev.attendees.add(new AttendeeRecord(
                                    rsa.getString("student_name"),
                                    rsa.getString("student_roll"),
                                    rsa.getString("department"),
                                    rsa.getString("utr"),
                                    rsa.getString("student_email"),
                                    rsa.getString("timestamp")
                            ));
                            registrationDuplicatesStore.add(rsa.getString("student_roll") + "_" + evId);
                        }
                    }
                }
                eventRepository.add(ev);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        if (pastCampusArchive.isEmpty()) {
            pastCampusArchive.add(new PastCampusEvent("Winter Code Sprint 2025", "Coding Ninjas Club", "CGPA (2 Credits)", "Dec 15, 2025", "250+ Attendees", "Certificates Distributed", "Winner: Team CodeCrafters"));
            pastCampusArchive.add(new PastCampusEvent("Annual Drone Racing Cup", "IEEE Student Branch", "Non-CGPA", "Nov 02, 2025", "180 Attendees", "Certificates Distributed", "Fastest Lap: 42.1s"));
        }
    }

    // ==========================================
    // 1. LANDING SELECTION VIEW
    // ==========================================
    private void showRoleSelectionView() {
        VBox root = new VBox(25);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #0b0f19; -fx-padding: 40;");

        Label badge = new Label("ENTERPRISE CAMPUS ECOSYSTEM");
        badge.setStyle("-fx-text-fill: #38bdf8; -fx-background-color: rgba(56, 189, 248, 0.1); -fx-padding: 6 14; -fx-background-radius: 20; -fx-font-size: 11px; -fx-font-weight: bold; -fx-border-color: rgba(56, 189, 248, 0.3); -fx-border-radius: 20;");

        Label title = new Label("Choose Your Portal");
        title.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 32px; -fx-font-weight: 800;");

        Label subtitle = new Label("Modern portal for organizing clubs and university students with Excel export, certificate tracking, and permanent database storage.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");

        HBox cardsBox = new HBox(30);
        cardsBox.setAlignment(Pos.CENTER);

        VBox clubCard = createPortalCard("Club & Chapter Lead", "Propose events, configure 24hr deadlines, export attendee rosters to Excel, and store to database.", "#6366f1", this::showClubWebAuthView);
        VBox studentCard = createPortalCard("Student & Attendee", "Browse verified campus events, view detailed specs, scan UPI QR, and check certificate issuance.", "#0ea5e9", this::showStudentWebAuthView);

        cardsBox.getChildren().addAll(clubCard, studentCard);
        root.getChildren().addAll(badge, title, subtitle, cardsBox);

        Scene scene = new Scene(root, 1140, 780);
        primaryStage.setScene(scene);
    }

    private VBox createPortalCard(String title, String desc, String brandColor, Runnable action) {
        VBox card = new VBox(16);
        card.setPrefSize(320, 260);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: #111827; -fx-background-radius: 16px; -fx-border-color: #1f2937; -fx-border-radius: 16px; -fx-border-width: 1.5; -fx-padding: 28; -fx-cursor: hand;");

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000", 0.5));
        shadow.setRadius(20);
        card.setEffect(shadow);

        Circle pill = new Circle(6, Color.web(brandColor));
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #f9fafb; -fx-font-size: 19px; -fx-font-weight: 700;");

        Label lblDesc = new Label(desc);
        lblDesc.setWrapText(true);
        lblDesc.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px; -fx-line-spacing: 2px;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button btn = new Button("Open Portal →");
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: " + brandColor + "; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 0; -fx-cursor: hand;");
        btn.setOnAction(e -> action.run());

        card.getChildren().addAll(pill, lblTitle, lblDesc, spacer, btn);
        card.setOnMouseClicked(e -> action.run());
        return card;
    }

    // ==========================================
    // 2. UNIVERSAL WEB AUTHENTICATION WITH REAL FORGOT PASSWORD
    // ==========================================
    private void showClubWebAuthView() {
        showUniversalAuthView(true);
    }

    private void showStudentWebAuthView() {
        showUniversalAuthView(false);
    }

    private void showUniversalAuthView(boolean isClub) {
        HBox webContainer = new HBox();
        webContainer.setStyle("-fx-background-color: #0b0f19;");

        VBox leftBanner = new VBox(25);
        leftBanner.setPrefWidth(420);
        leftBanner.setAlignment(Pos.CENTER_LEFT);
        leftBanner.setPadding(new Insets(50));
        leftBanner.setStyle(isClub
                ? "-fx-background-color: linear-gradient(to bottom right, #1e1b4b, #0f172a 75%); -fx-border-color: #1e293b; -fx-border-width: 0 1 0 0;"
                : "-fx-background-color: linear-gradient(to bottom right, #0c4a6e, #0f172a 75%); -fx-border-color: #1e293b; -fx-border-width: 0 1 0 0;");

        HBox logoBrand = new HBox(12);
        logoBrand.setAlignment(Pos.CENTER_LEFT);
        StackPane iconBox = new StackPane();
        Circle bgCircle = new Circle(18, Color.web(isClub ? "#4f46e5" : "#0284c7"));
        Label iconChar = new Label(isClub ? "⚡" : "🎓");
        iconChar.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");
        iconBox.getChildren().addAll(bgCircle, iconChar);

        Label brandText = new Label(isClub ? "ClubDesk Enterprise" : "StudentPortal");
        brandText.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: 800;");
        logoBrand.getChildren().addAll(iconBox, brandText);

        VBox heroTextGroup = new VBox(10);
        Label heroMain = new Label(isClub ? "Manage campus events with SQLite persistence." : "Verified event registrations & certificate delivery.");
        heroMain.setWrapText(true);
        heroMain.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 24px; -fx-font-weight: 800;");

        Label heroSub = new Label("Supports any universal email address with permanent SQL database storage.");
        heroSub.setWrapText(true);
        heroSub.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        heroTextGroup.getChildren().addAll(heroMain, heroSub);

        Region bannerSpacer = new Region();
        VBox.setVgrow(bannerSpacer, Priority.ALWAYS);
        leftBanner.getChildren().addAll(logoBrand, heroTextGroup, bannerSpacer);

        ScrollPane rightScroll = new ScrollPane();
        rightScroll.setFitToWidth(true);
        HBox.setHgrow(rightScroll, Priority.ALWAYS);
        rightScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox rightContent = new VBox(18);
        rightContent.setAlignment(Pos.CENTER);
        rightContent.setPadding(new Insets(40, 60, 40, 60));
        rightContent.setMaxWidth(480);

        HBox topNavRow = new HBox();
        Button btnBackHome = new Button("← Portals");
        btnBackHome.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748b; -fx-font-size: 12px; -fx-cursor: hand;");
        btnBackHome.setOnAction(e -> showRoleSelectionView());
        topNavRow.getChildren().add(btnBackHome);

        VBox formHeader = new VBox(4);
        Label headerTitle = new Label(isClub ? "Club Organizer Sign In" : "Student Attendee Sign In");
        headerTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 24px; -fx-font-weight: 800;");
        Label headerDesc = new Label("Works with any personal or institutional email address.");
        headerDesc.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        formHeader.getChildren().addAll(headerTitle, headerDesc);

        final boolean[] isLogin = {true};
        VBox dynamicFields = new VBox(12);
        Label feedback = new Label();
        feedback.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        TextField txtEmail = createWebInput("Email Address", isClub ? "ieee@gmail.com" : "alex@gmail.com");
        PasswordField txtPassword = createWebPassword("Password", "password123");
        PasswordField txtConfirm = createWebPassword("Confirm Password", "password123");

        TextField txtNameOrClub = createWebInput(isClub ? "Club Official Name" : "Student Full Name", isClub ? "e.g. Robotics Guild" : "e.g. Alex Rivers");
        TextField txtRollNumber = createWebInput("Registration / Roll Number", "e.g. 22CS101");
        TextField txtDepartment = createWebInput("Department", "e.g. Computer Science");

        Hyperlink forgotPasswordLink = new Hyperlink("Forgot password?");
        forgotPasswordLink.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 12px; -fx-border-color: transparent;");
        forgotPasswordLink.setOnAction(e -> openForgotPasswordDialog(isClub));

        Button btnSubmit = new Button("Sign In →");
        btnSubmit.setMaxWidth(Double.MAX_VALUE);
        btnSubmit.setStyle("-fx-background-color: " + (isClub ? "linear-gradient(to right, #4f46e5, #6366f1)" : "linear-gradient(to right, #0284c7, #0ea5e9)") + "; -fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12; -fx-background-radius: 8px; -fx-cursor: hand;");

        HBox switchBox = new HBox(6);
        switchBox.setAlignment(Pos.CENTER);
        Label lblPrompt = new Label("Need an account?");
        lblPrompt.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        Hyperlink linkSwitch = new Hyperlink("Create one here");
        linkSwitch.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: transparent;");
        switchBox.getChildren().addAll(lblPrompt, linkSwitch);

        Runnable renderFields = () -> {
            feedback.setText("");
            dynamicFields.getChildren().clear();
            if (isLogin[0]) {
                headerTitle.setText(isClub ? "Club Organizer Sign In" : "Student Sign In");
                btnSubmit.setText("Sign In →");
                lblPrompt.setText("Need an account?");
                linkSwitch.setText("Create one here");
                dynamicFields.getChildren().addAll(
                        createFieldContainer("Email Address", txtEmail),
                        createFieldContainer("Password", txtPassword),
                        forgotPasswordLink
                );
            } else {
                headerTitle.setText(isClub ? "Register New Club" : "Student Registration");
                btnSubmit.setText("Register Account →");
                lblPrompt.setText("Already registered?");
                linkSwitch.setText("Sign in instead");

                if (isClub) {
                    dynamicFields.getChildren().addAll(
                            createFieldContainer("Official Club Name", txtNameOrClub),
                            createFieldContainer("Official Email Address", txtEmail),
                            createFieldContainer("Password", txtPassword),
                            createFieldContainer("Confirm Password", txtConfirm)
                    );
                } else {
                    dynamicFields.getChildren().addAll(
                            createFieldContainer("Registration / Roll Number", txtRollNumber),
                            createFieldContainer("Full Name", txtNameOrClub),
                            createFieldContainer("Department", txtDepartment),
                            createFieldContainer("Email Address", txtEmail),
                            createFieldContainer("Password", txtPassword),
                            createFieldContainer("Confirm Password", txtConfirm)
                    );
                }
            }
        };

        linkSwitch.setOnAction(e -> {
            isLogin[0] = !isLogin[0];
            renderFields.run();
        });

        btnSubmit.setOnAction(e -> {
            String email = txtEmail.getText().trim().toLowerCase();
            String pwd = txtPassword.getText();

            if (isLogin[0]) {
                if (isClub) {
                    if (!clubCredentials.containsKey(email) || !clubCredentials.get(email).equals(pwd)) {
                        feedback.setText("Invalid club email or password.");
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }
                    this.currentClubEmail = email;
                    this.currentClubName = registeredClubs.getOrDefault(email, "Campus Club");
                    showClubEventCreationView();
                } else {
                    if (!studentCredentials.containsKey(email) || !studentCredentials.get(email).equals(pwd)) {
                        feedback.setText("Invalid student email or password.");
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }
                    this.currentStudent = registeredStudents.get(email);
                    showStudentEventBrowserView();
                }
            } else {
                String confirm = txtConfirm.getText();
                if (email.isEmpty() || pwd.isEmpty() || !pwd.equals(confirm)) {
                    feedback.setText("Please verify all fields and ensure passwords match.");
                    feedback.setTextFill(Color.web("#f87171"));
                    return;
                }

                if (isClub) {
                    String cName = txtNameOrClub.getText().trim();
                    if (cName.isEmpty()) {
                        feedback.setText("Club name is required.");
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }

                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement("INSERT INTO clubs(email, name, password) VALUES (?, ?, ?)")) {
                        ps.setString(1, email);
                        ps.setString(2, cName);
                        ps.setString(3, pwd);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        feedback.setText("Email already registered: " + ex.getMessage());
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }

                    registeredClubs.put(email, cName);
                    clubCredentials.put(email, pwd);
                    this.currentClubEmail = email;
                    this.currentClubName = cName;
                    showClubEventCreationView();
                } else {
                    String roll = txtRollNumber.getText().trim();
                    String name = txtNameOrClub.getText().trim();
                    String dept = txtDepartment.getText().trim();
                    if (roll.isEmpty() || name.isEmpty() || dept.isEmpty()) {
                        feedback.setText("All student credentials are required.");
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }

                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement ps = conn.prepareStatement("INSERT INTO students(email, roll_number, name, department, password) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setString(1, email);
                        ps.setString(2, roll);
                        ps.setString(3, name);
                        ps.setString(4, dept);
                        ps.setString(5, pwd);
                        ps.executeUpdate();
                    } catch (SQLException ex) {
                        feedback.setText("Error registering student: " + ex.getMessage());
                        feedback.setTextFill(Color.web("#f87171"));
                        return;
                    }

                    StudentSession s = new StudentSession(roll, name, email, dept);
                    registeredStudents.put(email, s);
                    studentCredentials.put(email, pwd);
                    this.currentStudent = s;
                    showStudentEventBrowserView();
                }
            }
        });

        renderFields.run();

        rightContent.getChildren().addAll(topNavRow, formHeader, dynamicFields, feedback, btnSubmit, switchBox);
        StackPane centerWrapper = new StackPane(rightContent);
        centerWrapper.setAlignment(Pos.CENTER);
        rightScroll.setContent(centerWrapper);

        webContainer.getChildren().addAll(leftBanner, rightScroll);
        Scene scene = new Scene(webContainer, 1140, 780);
        primaryStage.setScene(scene);
    }

    // ==========================================
    // FORGOT PASSWORD DIALOG
    // ==========================================
    private void openForgotPasswordDialog(boolean isClub) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Reset Password");

        VBox box = new VBox(14);
        box.setStyle("-fx-background-color: #0f172a; -fx-padding: 24;");
        box.setPrefWidth(400);

        Label lbl = new Label("Account Password Recovery");
        lbl.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 16px; -fx-font-weight: bold;");

        TextField txtEmail = createWebInput("Enter your registered email", "");
        PasswordField txtNewPwd = createWebPassword("Enter New Password", "");
        PasswordField txtCfmPwd = createWebPassword("Confirm New Password", "");
        Label feedback = new Label();
        feedback.setStyle("-fx-font-size: 11px;");

        Button btnReset = new Button("Reset Password");
        btnReset.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 16; -fx-background-radius: 6;");

        btnReset.setOnAction(e -> {
            String email = txtEmail.getText().trim().toLowerCase();
            String p1 = txtNewPwd.getText();
            String p2 = txtCfmPwd.getText();

            Map<String, String> creds = isClub ? clubCredentials : studentCredentials;
            if (!creds.containsKey(email)) {
                feedback.setText("No registered account found with this email.");
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }
            if (p1.isEmpty() || !p1.equals(p2)) {
                feedback.setText("Passwords do not match or are empty.");
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            try {
                EventDAO.updatePassword(isClub ? "clubs" : "students", email, p1);
            } catch (SQLException ex) {
                feedback.setText("Error: " + ex.getMessage());
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            creds.put(email, p1);
            feedback.setText("Password reset successful! You can now sign in.");
            feedback.setTextFill(Color.web("#34d399"));
            btnReset.setDisable(true);
        });

        box.getChildren().addAll(lbl, new Label("Enter your email and choose a new password:"), txtEmail, txtNewPwd, txtCfmPwd, feedback, btnReset);
        stage.setScene(new Scene(box, 420, 320));
        stage.showAndWait();
    }

    // ==========================================
    // 3. CLUB EVENT CREATION
    // ==========================================
    private void showClubEventCreationView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #0b0f19;");

        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 28, 14, 28));
        topBar.setStyle("-fx-background-color: #111827; -fx-border-color: #1f2937; -fx-border-width: 0 0 1 0;");

        HBox clubProfileClickable = new HBox(12);
        clubProfileClickable.setAlignment(Pos.CENTER_LEFT);
        clubProfileClickable.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 8px; -fx-border-color: rgba(99, 102, 241, 0.3); -fx-border-radius: 8px;");

        StackPane avatarPane = createAvatarCircle(clubAvatarPaths.get(currentClubEmail), currentClubName, 22, "#4f46e5");

        VBox titleBox = new VBox(2);
        Label topTitle = new Label(currentClubName);
        topTitle.setStyle("-fx-text-fill: #f9fafb; -fx-font-size: 15px; -fx-font-weight: 800;");
        Label topSub = new Label("Click to edit profile / View past conducted events →");
        topSub.setStyle("-fx-text-fill: #818cf8; -fx-font-size: 11px;");
        titleBox.getChildren().addAll(topTitle, topSub);

        clubProfileClickable.getChildren().addAll(avatarPane, titleBox);
        clubProfileClickable.setOnMouseClicked(e -> showClubProfileAndHistoryView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnBack = new Button("Sign out");
        btnBack.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #9ca3af; -fx-border-color: #374151; -fx-border-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        btnBack.setOnAction(e -> showClubWebAuthView());

        topBar.getChildren().addAll(clubProfileClickable, spacer, btnBack);
        layout.setTop(topBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox formContainer = new VBox(22);
        formContainer.setPadding(new Insets(30, 45, 45, 45));
        formContainer.setMaxWidth(880);
        formContainer.setAlignment(Pos.TOP_CENTER);

        VBox basicSection = createFormSection("1. Basic Event Identity & Event Logo");
        GridPane basicGrid = new GridPane();
        basicGrid.setHgap(15);
        basicGrid.setVgap(14);

        TextField txtEvent = createStyledTextField("e.g. National Hackathon 2026");

        final String[] eventLogoPath = {""};
        Label lblLogoChosen = new Label("Default Emblem Selected");
        lblLogoChosen.setStyle("-fx-text-fill: #94a3b8;");
        Button btnPickLogo = new Button("Upload Event Logo (PNG/JPG)...");
        btnPickLogo.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-border-color: #38bdf8; -fx-border-radius: 6;");
        btnPickLogo.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f != null) {
                eventLogoPath[0] = f.getAbsolutePath();
                lblLogoChosen.setText("Custom Logo: " + f.getName());
                lblLogoChosen.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
            }
        });
        HBox logoRow = new HBox(10, btnPickLogo, lblLogoChosen);
        logoRow.setAlignment(Pos.CENTER_LEFT);

        basicGrid.addRow(0, createFieldLabel("Event Name:"), txtEvent);
        basicGrid.addRow(1, createFieldLabel("Event Official Logo:"), logoRow);
        basicSection.getChildren().add(basicGrid);

        VBox capacitySection = createFormSection("2. Seats & 24-Hour Registration Deadline");
        GridPane capacityGrid = new GridPane();
        capacityGrid.setHgap(15);
        capacityGrid.setVgap(14);

        ComboBox<String> cmbType = new ComboBox<>();
        cmbType.getItems().addAll("Non-CGPA", "CGPA");
        cmbType.setValue("Non-CGPA");
        cmbType.setStyle("-fx-background-color: #1f2937; -fx-text-fill: white;");

        Spinner<Integer> spinSeats = new Spinner<>(5, 1000, 50, 5);
        spinSeats.setEditable(true);

        DatePicker datePicker = new DatePicker(LocalDate.now().plusDays(3));
        Spinner<Integer> spinHour = new Spinner<>(0, 23, 23);
        Spinner<Integer> spinMinute = new Spinner<>(0, 59, 59);
        spinHour.setPrefWidth(70);
        spinMinute.setPrefWidth(70);
        HBox time24Box = new HBox(8, datePicker, new Label("Time:"), spinHour, new Label(":"), spinMinute, new Label("HRS (24h)"));
        time24Box.setAlignment(Pos.CENTER_LEFT);

        capacityGrid.addRow(0, createFieldLabel("Event Type:"), cmbType);
        capacityGrid.addRow(1, createFieldLabel("Total Seats Available:"), spinSeats);
        capacityGrid.addRow(2, createFieldLabel("Registration Deadline:"), time24Box);
        capacitySection.getChildren().add(capacityGrid);

        VBox certSection = createFormSection("3. Certificate Provision & Timeline");
        GridPane certGrid = new GridPane();
        certGrid.setHgap(15);
        certGrid.setVgap(14);

        CheckBox chkCertProvided = new CheckBox("Certificates will be provided for this event");
        chkCertProvided.setSelected(true);
        chkCertProvided.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold;");

        Label lblCertWhen = createFieldLabel("When will certificates be provided?");
        TextField txtCertTimeline = createStyledTextField("e.g. Certificates provided within 3 days after event completion");
        txtCertTimeline.setText("Certificates provided within 3 days after event completion");

        chkCertProvided.setOnAction(e -> {
            boolean prov = chkCertProvided.isSelected();
            lblCertWhen.setVisible(prov);
            txtCertTimeline.setVisible(prov);
        });

        certGrid.addRow(0, createFieldLabel("Certification:"), chkCertProvided);
        certGrid.addRow(1, lblCertWhen, txtCertTimeline);
        certSection.getChildren().add(certGrid);

        VBox coordsSection = createFormSection("4. Faculty & Student Coordinators (Modern Layout)");

        ObservableList<CoordinatorRow> studentCoordList = FXCollections.observableArrayList(
                new CoordinatorRow("1", "Rohan Mehta", "9876543210"),
                new CoordinatorRow("2", "Kavya Sharma", "9123456780")
        );
        ObservableList<CoordinatorRow> facultyCoordList = FXCollections.observableArrayList(
                new CoordinatorRow("1", "Dr. A. Sharma", "CSE (asharma@gmail.com)"),
                new CoordinatorRow("2", "Prof. M. Verma", "ECE (mverma@gmail.com)")
        );

        VBox studentCardsBox = new VBox(8);
        VBox facultyCardsBox = new VBox(8);

        Runnable refreshCoordCards = () -> {
            studentCardsBox.getChildren().clear();
            facultyCardsBox.getChildren().clear();

            for (int i = 0; i < studentCoordList.size(); i++) {
                int index = i;
                CoordinatorRow r = studentCoordList.get(i);
                HBox card = new HBox(12);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setStyle("-fx-background-color: #1e293b; -fx-padding: 8 12; -fx-background-radius: 8px; -fx-border-color: #334155; -fx-border-radius: 8px;");

                Label tag = new Label("Student #" + (index + 1));
                tag.setStyle("-fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-min-width: 80;");
                TextField tfName = createStyledTextField("Student Name");
                tfName.setText(r.getName());
                tfName.textProperty().addListener((o, ov, nv) -> r.setName(nv));

                TextField tfPhone = createStyledTextField("Contact Number");
                tfPhone.setText(r.getContact());
                tfPhone.textProperty().addListener((o, ov, nv) -> r.setContact(nv));

                Button btnDel = new Button("✕");
                btnDel.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-cursor: hand;");
                btnDel.setOnAction(e -> {
                    if (studentCoordList.size() > 2) {
                        studentCoordList.remove(index);
                    } else {
                        new Alert(Alert.AlertType.WARNING, "Minimum 2 student coordinators required.").show();
                    }
                });

                card.getChildren().addAll(tag, tfName, tfPhone, btnDel);
                studentCardsBox.getChildren().add(card);
            }

            for (int i = 0; i < facultyCoordList.size(); i++) {
                CoordinatorRow r = facultyCoordList.get(i);
                HBox card = new HBox(12);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setStyle("-fx-background-color: #1e293b; -fx-padding: 8 12; -fx-background-radius: 8px; -fx-border-color: #334155; -fx-border-radius: 8px;");

                Label tag = new Label("Faculty #" + (i + 1));
                tag.setStyle("-fx-text-fill: #a78bfa; -fx-font-weight: bold; -fx-min-width: 80;");
                TextField tfName = createStyledTextField("Faculty Name");
                tfName.setText(r.getName());
                tfName.textProperty().addListener((o, ov, nv) -> r.setName(nv));

                TextField tfDept = createStyledTextField("Dept / Contact");
                tfDept.setText(r.getContact());
                tfDept.textProperty().addListener((o, ov, nv) -> r.setContact(nv));

                card.getChildren().addAll(tag, tfName, tfDept);
                facultyCardsBox.getChildren().add(card);
            }
        };

        studentCoordList.addListener((javafx.collections.ListChangeListener<CoordinatorRow>) c -> refreshCoordCards.run());
        facultyCoordList.addListener((javafx.collections.ListChangeListener<CoordinatorRow>) c -> refreshCoordCards.run());
        refreshCoordCards.run();

        Button btnAddStudentCoord = new Button("+ Add Student Coordinator (Max 4)");
        btnAddStudentCoord.setStyle("-fx-background-color: #334155; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 6;");
        btnAddStudentCoord.setOnAction(e -> {
            if (studentCoordList.size() < 4) {
                studentCoordList.add(new CoordinatorRow(String.valueOf(studentCoordList.size() + 1), "", ""));
            } else {
                new Alert(Alert.AlertType.WARNING, "Maximum 4 student coordinators allowed.").show();
            }
        });

        coordsSection.getChildren().addAll(
                new Label("Student Coordinators (2 to 4 Required):"),
                studentCardsBox,
                btnAddStudentCoord,
                new Label("Faculty Mentors (2 Required):"),
                facultyCardsBox
        );

        VBox logisticsSection = createFormSection("5. Registration Fee, UPI QR & WhatsApp Community");
        GridPane logGrid = new GridPane();
        logGrid.setHgap(15);
        logGrid.setVgap(14);

        TextField txtFee = createStyledTextField("FREE or ₹150");
        txtFee.setText("FREE");

        final String[] qrPath = {""};
        Label lblQrChosen = new Label("No QR attached");
        lblQrChosen.setStyle("-fx-text-fill: #94a3b8;");
        Button btnPickQr = new Button("Upload Club UPI QR (GPay/PhonePe)...");
        btnPickQr.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-border-color: #0284c7; -fx-border-radius: 6;");
        btnPickQr.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f != null) {
                qrPath[0] = f.getAbsolutePath();
                lblQrChosen.setText("Uploaded: " + f.getName());
                lblQrChosen.setStyle("-fx-text-fill: #34d399; -fx-font-weight: bold;");
            }
        });
        HBox qrRow = new HBox(10, btnPickQr, lblQrChosen);
        qrRow.setAlignment(Pos.CENTER_LEFT);

        TextField txtWhatsApp = createStyledTextField("https://chat.whatsapp.com/event-community");

        logGrid.addRow(0, createFieldLabel("Registration Amount:"), txtFee);
        logGrid.addRow(1, createFieldLabel("Payment UPI QR Code:"), qrRow);
        logGrid.addRow(2, createFieldLabel("WhatsApp Group Link:"), txtWhatsApp);
        logisticsSection.getChildren().add(logGrid);

        Label feedback = new Label();
        feedback.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnPublish = new Button("Publish Event Immediately →");
        btnPublish.setPrefWidth(280);
        btnPublish.setStyle("-fx-background-color: linear-gradient(to right, #059669, #10b981); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px; -fx-padding: 12 24; -fx-background-radius: 8px; -fx-cursor: hand;");

        btnPublish.setOnAction(e -> {
            String eventName = txtEvent.getText().trim();
            if (eventName.isEmpty()) {
                feedback.setText("Please enter an event name.");
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            String deadline24h = datePicker.getValue().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                    + " at " + String.format("%02d:%02d HRS", spinHour.getValue(), spinMinute.getValue());

            StringBuilder sCoords = new StringBuilder();
            for (CoordinatorRow r : studentCoordList) {
                if (!r.getName().isEmpty()) {
                    if (sCoords.length() > 0) sCoords.append(", ");
                    sCoords.append(r.getName()).append(" (").append(r.getContact()).append(")");
                }
            }

            StringBuilder fCoords = new StringBuilder();
            for (CoordinatorRow r : facultyCoordList) {
                if (!r.getName().isEmpty()) {
                    if (fCoords.length() > 0) fCoords.append(", ");
                    fCoords.append(r.getName()).append(" (").append(r.getContact()).append(")");
                }
            }

            int generatedId = -1;
            try {
                generatedId = EventDAO.insertEvent(
                        eventName, currentClubName, cmbType.getValue(),
                        "CGPA".equals(cmbType.getValue()) ? 2 : 0,
                        "UPCOMING", sCoords.toString(), fCoords.toString(),
                        spinSeats.getValue(), "#8b5cf6",
                        eventLogoPath[0].isEmpty() ? "🎯" : eventLogoPath[0],
                        deadline24h, txtFee.getText().trim(), txtWhatsApp.getText().trim(),
                        qrPath[0], chkCertProvided.isSelected(), txtCertTimeline.getText().trim()
                );
            } catch (SQLException ex) {
                feedback.setText("Database save error: " + ex.getMessage());
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            CampusEventCardData newEvent = new CampusEventCardData(
                    generatedId, eventName, currentClubName, cmbType.getValue(),
                    "CGPA".equals(cmbType.getValue()) ? 2 : 0, "UPCOMING",
                    sCoords.toString(), fCoords.toString(), 0, spinSeats.getValue(),
                    "#8b5cf6", eventLogoPath[0].isEmpty() ? "🎯" : eventLogoPath[0],
                    deadline24h, txtFee.getText().trim(), txtWhatsApp.getText().trim(),
                    qrPath[0], chkCertProvided.isSelected(), txtCertTimeline.getText().trim()
            );

            eventRepository.add(0, newEvent);

            feedback.setText("Event '" + eventName + "' permanently published via DAO into SQLite!");
            feedback.setTextFill(Color.web("#34d399"));
        });

        formContainer.getChildren().addAll(
                basicSection, capacitySection, certSection, coordsSection, logisticsSection, feedback, btnPublish
        );

        scrollPane.setContent(formContainer);
        layout.setCenter(scrollPane);

        Scene scene = new Scene(layout, 1140, 780);
        primaryStage.setScene(scene);
    }

    // ==========================================
    // 4. CLUB PROFILE, PAST CONDUCTED & EXCEL EXPORT
    // ==========================================
    private void showClubProfileAndHistoryView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #0b0f19;");

        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 30, 14, 30));
        topBar.setStyle("-fx-background-color: #111827; -fx-border-color: #1f2937; -fx-border-width: 0 0 1 0;");

        Button btnBack = new Button("← Back to Event Creation Workspace");
        btnBack.setStyle("-fx-background-color: transparent; -fx-border-color: #6366f1; -fx-text-fill: #818cf8; -fx-border-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        btnBack.setOnAction(e -> showClubEventCreationView());

        Label title = new Label("Club Profile & Operations Workspace");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

        topBar.getChildren().addAll(btnBack, title);
        layout.setTop(topBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(24);
        content.setPadding(new Insets(30, 45, 45, 45));

        HBox clubHero = new HBox(20);
        clubHero.setAlignment(Pos.CENTER_LEFT);
        clubHero.setStyle("-fx-background-color: #111827; -fx-padding: 24; -fx-background-radius: 14px; -fx-border-color: #1f2937; -fx-border-radius: 14px;");

        StackPane avatarPane = createAvatarCircle(clubAvatarPaths.get(currentClubEmail), currentClubName, 36, "#4f46e5");

        VBox clubMeta = new VBox(5);
        Label cTitle = new Label(currentClubName);
        cTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label cEmail = new Label("Contact Email: " + currentClubEmail);
        cEmail.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

        Button btnChangeAvatar = new Button("Change Club Profile Picture");
        btnChangeAvatar.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-border-color: #38bdf8; -fx-border-radius: 6; -fx-cursor: hand;");
        btnChangeAvatar.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f != null) {
                clubAvatarPaths.put(currentClubEmail, f.getAbsolutePath());
                EventDAO.updateAvatar("clubs", currentClubEmail, f.getAbsolutePath());
                showClubProfileAndHistoryView();
            }
        });

        clubMeta.getChildren().addAll(cTitle, cEmail, btnChangeAvatar);
        clubHero.getChildren().addAll(avatarPane, clubMeta);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        List<CampusEventCardData> clubActiveEvents = eventRepository.stream()
                .filter(ev -> ev.clubName.equalsIgnoreCase(currentClubName))
                .toList();

        Tab tabActive = new Tab("Active Published Events & Attendee Sheets (" + clubActiveEvents.size() + ")");
        VBox activeBox = new VBox(14);
        activeBox.setPadding(new Insets(20, 0, 10, 0));

        if (clubActiveEvents.isEmpty()) {
            activeBox.getChildren().add(new Label("No published events yet by this club."));
        } else {
            for (CampusEventCardData ev : clubActiveEvents) {
                VBox activeCard = new VBox(10);
                activeCard.setStyle("-fx-background-color: #111827; -fx-padding: 16; -fx-background-radius: 10px; -fx-border-color: #334155; -fx-border-radius: 10px;");

                HBox top = new HBox(10);
                top.setAlignment(Pos.CENTER_LEFT);
                Label evTitle = new Label(ev.eventName);
                evTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 16px; -fx-font-weight: bold;");

                Region r = new Region();
                HBox.setHgrow(r, Priority.ALWAYS);

                Button btnExportExcel = new Button("📥 Export Attendees to Excel (.CSV)");
                btnExportExcel.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px; -fx-background-radius: 6; -fx-cursor: hand;");
                btnExportExcel.setOnAction(e -> exportAttendeesToExcel(ev));

                top.getChildren().addAll(evTitle, r, btnExportExcel);

                Label details = new Label("Registered Seats: " + ev.bookedSeats + "/" + ev.totalCapacity + " | Certificate Timeline: " + ev.certTimeline);
                details.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

                activeCard.getChildren().addAll(top, details);
                activeBox.getChildren().add(activeCard);
            }
        }
        tabActive.setContent(activeBox);

        List<PastCampusEvent> clubPastEvents = pastCampusArchive.stream()
                .filter(pe -> pe.clubName.equalsIgnoreCase(currentClubName))
                .toList();

        Tab tabPast = new Tab("Events Conducted in Past (" + clubPastEvents.size() + ")");
        VBox pastBox = new VBox(14);
        pastBox.setPadding(new Insets(20, 0, 10, 0));

        if (clubPastEvents.isEmpty()) {
            pastBox.getChildren().add(new Label("No past events recorded for this club yet."));
        } else {
            for (PastCampusEvent pe : clubPastEvents) {
                pastBox.getChildren().add(createPastEventCard(pe));
            }
        }
        tabPast.setContent(pastBox);

        tabPane.getTabs().addAll(tabActive, tabPast);
        content.getChildren().addAll(clubHero, tabPane);
        scrollPane.setContent(content);
        layout.setCenter(scrollPane);

        Scene scene = new Scene(layout, 1140, 780);
        primaryStage.setScene(scene);
    }

    private void exportAttendeesToExcel(CampusEventCardData event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Attendee Excel Sheet (.csv)");
        chooser.setInitialFileName(event.eventName.replaceAll("\\s+", "_") + "_Attendees.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV / Excel", "*.csv"));
        File file = chooser.showSaveDialog(primaryStage);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write("Attendee Name,Registration/Roll Number,Department,Event Name,Organizing Club,Registration Timestamp,Payment UTR/Transaction ID\n");
                for (AttendeeRecord a : event.attendees) {
                    writer.write(String.format("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            a.name, a.rollNumber, a.department, event.eventName, event.clubName, a.timestamp, a.utr));
                }
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Excel Export Success");
                alert.setHeaderText("Spreadsheet Generated Successfully");
                alert.setContentText("Exported " + event.attendees.size() + " attendee records to:\n" + file.getAbsolutePath());
                alert.showAndWait();
            } catch (IOException ex) {
                new Alert(Alert.AlertType.ERROR, "Failed to save Excel sheet: " + ex.getMessage()).show();
            }
        }
    }

    // ==========================================
    // 5. STUDENT PORTAL (DETAILS BUTTON & CARD VIEW)
    // ==========================================
    private void showStudentEventBrowserView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #0b0f19;");

        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 30, 14, 30));
        topBar.setStyle("-fx-background-color: #111827; -fx-border-color: #1f2937; -fx-border-width: 0 0 1 0;");

        HBox profileClickable = new HBox(12);
        profileClickable.setAlignment(Pos.CENTER_LEFT);
        profileClickable.setStyle("-fx-cursor: hand; -fx-padding: 4 10; -fx-background-radius: 8px; -fx-border-color: rgba(56, 189, 248, 0.2); -fx-border-radius: 8px;");

        StackPane avatar = createAvatarCircle(studentAvatarPaths.get(currentStudent.email), currentStudent.name, 20, "#0284c7");

        VBox studentInfo = new VBox(1);
        Label lblName = new Label(currentStudent.name);
        lblName.setStyle("-fx-text-fill: #f9fafb; -fx-font-size: 14px; -fx-font-weight: 800;");
        Label lblRoll = new Label(currentStudent.rollNumber + " • Profile & Registrations →");
        lblRoll.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 11px;");
        studentInfo.getChildren().addAll(lblName, lblRoll);

        profileClickable.getChildren().addAll(avatar, studentInfo);
        profileClickable.setOnMouseClicked(e -> showStudentProfileAndHistoryView());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnSignOut = new Button("Sign out");
        btnSignOut.setStyle("-fx-background-color: #1f2937; -fx-text-fill: #9ca3af; -fx-border-color: #374151; -fx-border-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        btnSignOut.setOnAction(e -> showStudentWebAuthView());

        topBar.getChildren().addAll(profileClickable, spacer, btnSignOut);
        layout.setTop(topBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox contentBox = new VBox(22);
        contentBox.setPadding(new Insets(26, 40, 40, 40));

        VBox headerArea = new VBox(6);
        Label title = new Label("Campus Events & Workshops");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 24px; -fx-font-weight: 800;");
        Label subtitle = new Label("Click 'Details' on any card to view full event specifications, certificate commitments, and coordinator contacts.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        headerArea.getChildren().addAll(title, subtitle);

        FlowPane cardsGrid = new FlowPane();
        cardsGrid.setHgap(20);
        cardsGrid.setVgap(20);

        if (eventRepository.isEmpty()) {
            cardsGrid.getChildren().add(new Label("No live events posted yet. Sign into a Club account to publish!"));
        } else {
            for (CampusEventCardData ev : eventRepository) {
                cardsGrid.getChildren().add(createRectangularEventCard(ev));
            }
        }

        contentBox.getChildren().addAll(headerArea, cardsGrid);
        scrollPane.setContent(contentBox);
        layout.setCenter(scrollPane);

        Scene scene = new Scene(layout, 1140, 780);
        primaryStage.setScene(scene);
    }

    private VBox createRectangularEventCard(CampusEventCardData event) {
        VBox card = new VBox(14);
        card.setPrefWidth(480);
        card.setStyle("-fx-background-color: #111827; -fx-background-radius: 14px; -fx-border-color: #1f2937; -fx-border-radius: 14px; -fx-border-width: 1.5;");

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000", 0.45));
        shadow.setRadius(16);
        card.setEffect(shadow);

        StackPane posterBanner = new StackPane();
        posterBanner.setPrefHeight(120);
        posterBanner.setStyle("-fx-background-color: linear-gradient(to right, " + event.colorTheme + ", #0f172a); -fx-background-radius: 14px 14px 0 0;");

        HBox posterLogoBox = new HBox(12);
        posterLogoBox.setAlignment(Pos.CENTER_LEFT);
        posterLogoBox.setPadding(new Insets(15));

        StackPane logoCircle = createAvatarCircle(event.posterLogo, event.eventName, 28, "#0b0f19");

        VBox bannerText = new VBox(2);
        Label bannerClub = new Label(event.clubName.toUpperCase());
        bannerClub.setStyle("-fx-text-fill: #93c5fd; -fx-font-size: 10px; -fx-font-weight: 800;");
        Label bannerTitle = new Label(event.eventName);
        bannerTitle.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16px; -fx-font-weight: 800;");
        bannerText.getChildren().addAll(bannerClub, bannerTitle);

        posterLogoBox.getChildren().addAll(logoCircle, bannerText);

        HBox topBadges = new HBox(8);
        topBadges.setPadding(new Insets(10));
        topBadges.setAlignment(Pos.TOP_RIGHT);
        Label lblTime = new Label("⏳ " + event.timeLeft);
        lblTime.setStyle("-fx-background-color: rgba(15, 23, 42, 0.85); -fx-text-fill: #fbbf24; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");
        Label lblFee = new Label("🏷️ " + event.registrationAmount);
        lblFee.setStyle("-fx-background-color: rgba(16, 185, 129, 0.85); -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");
        topBadges.getChildren().addAll(lblTime, lblFee);
        posterBanner.getChildren().addAll(posterLogoBox, topBadges);

        VBox cardBody = new VBox(12);
        cardBody.setPadding(new Insets(14, 20, 18, 20));

        Label certTag = new Label(event.isCertificateProvided ? "📜 Certificate Provided" : "❌ No Certificate");
        certTag.setStyle(event.isCertificateProvided
                ? "-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #34d399; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;"
                : "-fx-background-color: rgba(239, 68, 68, 0.2); -fx-text-fill: #f87171; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");

        HBox actionRow = new HBox(12);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        Button btnDetails = new Button("ℹ️ Details");
        btnDetails.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-font-weight: bold; -fx-font-size: 12px; -fx-border-color: #38bdf8; -fx-border-radius: 6px; -fx-padding: 8 16; -fx-cursor: hand;");
        btnDetails.setOnAction(e -> openEventDetailsModal(event));

        Button btnRegister = new Button("Register Now →");
        btnRegister.setStyle("-fx-background-color: linear-gradient(to right, #0284c7, #0ea5e9); -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-background-radius: 6px; -fx-padding: 8 16; -fx-cursor: hand;");

        String duplicateKey = (currentStudent != null ? currentStudent.rollNumber : "GUEST") + "_" + event.eventId;
        if (registrationDuplicatesStore.contains(duplicateKey)) {
            btnRegister.setText("✓ Registered");
            btnRegister.setDisable(true);
            btnRegister.setStyle("-fx-background-color: #065f46; -fx-text-fill: #34d399; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 16;");
        }

        btnRegister.setOnAction(e -> openRegistrationModal(event, btnRegister));

        actionRow.getChildren().addAll(btnDetails, btnRegister);
        cardBody.getChildren().addAll(certTag, actionRow);
        card.getChildren().addAll(posterBanner, cardBody);
        return card;
    }

    private void openEventDetailsModal(CampusEventCardData event) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Event Details - " + event.eventName);

        VBox box = new VBox(14);
        box.setStyle("-fx-background-color: #0f172a; -fx-padding: 26;");
        box.setPrefWidth(500);

        Label title = new Label(event.eventName);
        title.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 18px; -fx-font-weight: bold;");

        VBox specs = new VBox(8);
        specs.setStyle("-fx-background-color: #111827; -fx-padding: 16; -fx-background-radius: 10px; -fx-border-color: #1f2937; -fx-border-radius: 10px;");

        specs.getChildren().addAll(
                createDetailRow("Organizing Club:", event.clubName),
                createDetailRow("Event Category:", event.type + (event.credits > 0 ? " (" + event.credits + " Credits)" : "")),
                createDetailRow("Registration Fee:", event.registrationAmount),
                createDetailRow("Registration Deadline (24h):", event.timeLeft),
                createDetailRow("Seats Remaining:", (event.totalCapacity - event.bookedSeats) + " seats left of " + event.totalCapacity),
                createDetailRow("Student Coordinators:", event.studentCoordinators),
                createDetailRow("Faculty Mentors:", event.facultyCoordinators),
                createDetailRow("Certificate Policy:", event.isCertificateProvided ? "YES - " + event.certTimeline : "No certificate provided for this event")
        );

        box.getChildren().addAll(title, specs);
        modal.setScene(new Scene(box, 520, 420));
        modal.showAndWait();
    }

    private HBox createDetailRow(String label, String value) {
        HBox r = new HBox(10);
        Label l = new Label(label);
        l.setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold; -fx-min-width: 170;");
        Label v = new Label(value);
        v.setWrapText(true);
        v.setStyle("-fx-text-fill: #f8fafc;");
        r.getChildren().addAll(l, v);
        return r;
    }

    // ==========================================
    // 6. STUDENT PROFILE & 2-TAB REGISTRATION VIEW
    // ==========================================
    private void showStudentProfileAndHistoryView() {
        BorderPane layout = new BorderPane();
        layout.setStyle("-fx-background-color: #0b0f19;");

        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 30, 14, 30));
        topBar.setStyle("-fx-background-color: #111827; -fx-border-color: #1f2937; -fx-border-width: 0 0 1 0;");

        Button btnBack = new Button("← Back to Live Events");
        btnBack.setStyle("-fx-background-color: transparent; -fx-border-color: #38bdf8; -fx-text-fill: #38bdf8; -fx-border-radius: 6px; -fx-padding: 6 14; -fx-cursor: hand;");
        btnBack.setOnAction(e -> showStudentEventBrowserView());

        Label title = new Label("Student Activity & Registrations Portal");
        title.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");

        topBar.getChildren().addAll(btnBack, title);
        layout.setTop(topBar);

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        VBox content = new VBox(24);
        content.setPadding(new Insets(30, 45, 45, 45));

        // Profile Identity Card
        HBox idCard = new HBox(20);
        idCard.setAlignment(Pos.CENTER_LEFT);
        idCard.setStyle("-fx-background-color: #111827; -fx-padding: 22; -fx-background-radius: 14px; -fx-border-color: #1f2937; -fx-border-radius: 14px;");

        StackPane bigAvatar = createAvatarCircle(studentAvatarPaths.get(currentStudent.email), currentStudent.name, 34, "#0284c7");

        VBox meta = new VBox(4);
        Label pName = new Label(currentStudent.name);
        pName.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label pRoll = new Label("Roll No: " + currentStudent.rollNumber + " | Department: " + currentStudent.department + " | Email: " + currentStudent.email);
        pRoll.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");

        Button btnChangeAvatar = new Button("Change Profile Picture");
        btnChangeAvatar.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #38bdf8; -fx-font-size: 11px; -fx-border-color: #38bdf8; -fx-border-radius: 6; -fx-cursor: hand;");
        btnChangeAvatar.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
            File f = chooser.showOpenDialog(primaryStage);
            if (f != null) {
                studentAvatarPaths.put(currentStudent.email, f.getAbsolutePath());
                EventDAO.updateAvatar("students", currentStudent.email, f.getAbsolutePath());
                showStudentProfileAndHistoryView();
            }
        });

        meta.getChildren().addAll(pName, pRoll, btnChangeAvatar);
        idCard.getChildren().addAll(bigAvatar, meta);

        // TabPane: Tab 1 (All College Events) & Tab 2 (My Registrations)
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // TAB 1: All Events Happened in College
        Tab tabCollegeHistory = new Tab("1. All Events Happened in the College (" + pastCampusArchive.size() + ")");
        VBox collegeBox = new VBox(14);
        collegeBox.setPadding(new Insets(20, 0, 10, 0));
        for (PastCampusEvent pe : pastCampusArchive) {
            collegeBox.getChildren().add(createPastEventCard(pe));
        }
        tabCollegeHistory.setContent(collegeBox);

        // Fetch student's real registered events from repository
        List<CampusEventCardData> myUpcomingEvents = new ArrayList<>();
        List<CampusEventCardData> myPastEvents = new ArrayList<>();

        for (CampusEventCardData ev : eventRepository) {
            boolean isRegistered = ev.attendees.stream()
                    .anyMatch(a -> a.rollNumber.equalsIgnoreCase(currentStudent.rollNumber));

            if (isRegistered) {
                if ("COMPLETED".equalsIgnoreCase(ev.status)) {
                    myPastEvents.add(ev);
                } else {
                    myUpcomingEvents.add(ev);
                }
            }
        }

        // TAB 2: My Registrations (Split into Past & Upcoming)
        Tab tabMyReg = new Tab("2. My Registrations (" + (myUpcomingEvents.size() + myPastEvents.size() + currentStudent.pastParticipated.size()) + ")");
        VBox myRegBox = new VBox(20);
        myRegBox.setPadding(new Insets(20, 0, 10, 0));

        // Sub-section I: Past Events (Certificate Provided Status)
        VBox pastSubBox = new VBox(10);
        Label lblPastSub = new Label("i. Past Events (Certificate Provided Status):");
        lblPastSub.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 14px; -fx-font-weight: bold;");
        pastSubBox.getChildren().add(lblPastSub);

        for (RegisteredRecord r : currentStudent.pastParticipated) {
            HBox row = createRegistrationRow(r.eventName, r.clubName, "Status: " + r.certificateStatus, "#34d399");
            pastSubBox.getChildren().add(row);
        }

        for (CampusEventCardData ev : myPastEvents) {
            String certText = ev.isCertificateProvided
                    ? "✓ " + ev.certTimeline
                    : "No Certificate Provided";
            HBox row = createRegistrationRow(ev.eventName, ev.clubName, certText, ev.isCertificateProvided ? "#34d399" : "#f87171");
            pastSubBox.getChildren().add(row);
        }

        if (currentStudent.pastParticipated.isEmpty() && myPastEvents.isEmpty()) {
            Label emptyPast = new Label("No completed past events found.");
            emptyPast.setStyle("-fx-text-fill: #94a3b8;");
            pastSubBox.getChildren().add(emptyPast);
        }

        // Sub-section II: Upcoming Registered Events
        VBox upcomingSubBox = new VBox(10);
        Label lblUpcomingSub = new Label("ii. Upcoming Registered Events:");
        lblUpcomingSub.setStyle("-fx-text-fill: #fbbf24; -fx-font-size: 14px; -fx-font-weight: bold;");
        upcomingSubBox.getChildren().add(lblUpcomingSub);

        if (myUpcomingEvents.isEmpty()) {
            Label emptyUpcoming = new Label("No upcoming events registered yet. Browse live events to join!");
            emptyUpcoming.setStyle("-fx-text-fill: #94a3b8;");
            upcomingSubBox.getChildren().add(emptyUpcoming);
        } else {
            for (CampusEventCardData ev : myUpcomingEvents) {
                HBox card = new HBox(14);
                card.setAlignment(Pos.CENTER_LEFT);
                card.setStyle("-fx-background-color: #111827; -fx-padding: 14; -fx-background-radius: 10px; -fx-border-color: #1f2937; -fx-border-radius: 10px;");

                VBox details = new VBox(3);
                Label evTitle = new Label(ev.eventName + " (" + ev.clubName + ")");
                evTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold; -fx-font-size: 14px;");

                Label evMeta = new Label("Deadline: " + ev.timeLeft + " | Category: " + ev.type + (ev.credits > 0 ? " (" + ev.credits + " Credits)" : ""));
                evMeta.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");

                Label evCert = new Label("Certificate: " + (ev.isCertificateProvided ? "Will be provided (" + ev.certTimeline + ")" : "Not provided"));
                evCert.setStyle("-fx-text-fill: " + (ev.isCertificateProvided ? "#38bdf8" : "#94a3b8") + "; -fx-font-size: 11px;");

                details.getChildren().addAll(evTitle, evMeta, evCert);

                Region sp = new Region();
                HBox.setHgrow(sp, Priority.ALWAYS);

                Label badgeConfirmed = new Label("✓ Confirmed Seat");
                badgeConfirmed.setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-text-fill: #34d399; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 6;");

                card.getChildren().addAll(details, sp, badgeConfirmed);
                upcomingSubBox.getChildren().add(card);
            }
        }

        myRegBox.getChildren().addAll(pastSubBox, new Separator(), upcomingSubBox);
        tabMyReg.setContent(myRegBox);

        tabPane.getTabs().addAll(tabCollegeHistory, tabMyReg);
        content.getChildren().addAll(idCard, tabPane);
        scrollPane.setContent(content);
        layout.setCenter(scrollPane);

        Scene scene = new Scene(layout, 1140, 780);
        primaryStage.setScene(scene);
    }

    private HBox createRegistrationRow(String eventName, String clubName, String statusText, String statusColor) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: #111827; -fx-padding: 12; -fx-background-radius: 8px; -fx-border-color: #1f2937; -fx-border-radius: 8px;");

        Label t = new Label(eventName + " (" + clubName + ")");
        t.setStyle("-fx-text-fill: #f8fafc; -fx-font-weight: bold;");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label certStatus = new Label(statusText);
        certStatus.setStyle("-fx-text-fill: " + statusColor + "; -fx-font-weight: bold; -fx-font-size: 12px;");

        row.getChildren().addAll(t, sp, certStatus);
        return row;
    }

    // ==========================================
    // REGISTRATION WORKFLOW MODAL
    // ==========================================
    private void openRegistrationModal(CampusEventCardData event, Button btnTrigger) {
        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Registration for " + event.eventName);

        VBox root = new VBox(14);
        root.setStyle("-fx-background-color: #0f172a; -fx-padding: 24;");
        root.setPrefWidth(480);

        Label header = new Label("Reserve Seat for " + event.eventName);
        header.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 16px; -fx-font-weight: bold;");

        TextField txtName = createWebInput("Full Name", currentStudent.name);
        TextField txtDept = createWebInput("Department", currentStudent.department);
        TextField txtRoll = createWebInput("Registration / Roll Number", currentStudent.rollNumber);
        TextField txtUtr = createWebInput("12-Digit UPI Transaction ID / UTR", "e.g. 987654321012");

        CheckBox chkAgree = new CheckBox("I confirm these details will appear on my event certificate.");
        chkAgree.setStyle("-fx-text-fill: #cbd5e1;");

        Label feedback = new Label();
        Button btnConfirm = new Button("Confirm Registration (" + event.registrationAmount + ")");
        btnConfirm.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10; -fx-background-radius: 6;");

        btnConfirm.setOnAction(e -> {
            if (!chkAgree.isSelected()) {
                feedback.setText("Please accept the certificate details agreement.");
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            String roll = txtRoll.getText().trim().toUpperCase();
            String key = roll + "_" + event.eventId;
            if (registrationDuplicatesStore.contains(key)) {
                feedback.setText("Duplicate registration detected for this roll number.");
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            try {
                EventDAO.registerStudentForEvent(
                        event.eventId, roll, txtName.getText().trim(),
                        txtDept.getText().trim(), txtUtr.getText().trim(),
                        currentStudent.email, LocalDate.now().toString()
                );
            } catch (SQLException ex) {
                feedback.setText("Registration error: " + ex.getMessage());
                feedback.setTextFill(Color.web("#f87171"));
                return;
            }

            registrationDuplicatesStore.add(key);
            event.bookedSeats++;
            event.attendees.add(new AttendeeRecord(txtName.getText().trim(), roll, txtDept.getText().trim(), txtUtr.getText().trim(), currentStudent.email, LocalDate.now().toString()));

            btnTrigger.setText("✓ Registered");
            btnTrigger.setDisable(true);
            btnTrigger.setStyle("-fx-background-color: #065f46; -fx-text-fill: #34d399; -fx-font-weight: bold; -fx-background-radius: 6px; -fx-padding: 8 16;");

            modal.close();
            new Alert(Alert.AlertType.INFORMATION, "Registration Confirmed! Stored permanently in campus_events.db for Excel export.").showAndWait();
        });

        root.getChildren().addAll(header,
                createFieldContainer("Name for Certificate:", txtName),
                createFieldContainer("Department:", txtDept),
                createFieldContainer("Roll Number:", txtRoll)
        );

        if (!event.registrationAmount.equalsIgnoreCase("FREE")) {
            root.getChildren().add(createFieldContainer("Payment UPI UTR / Trans ID:", txtUtr));
        }

        root.getChildren().addAll(chkAgree, feedback, btnConfirm);
        modal.setScene(new Scene(root, 500, 480));
        modal.showAndWait();
    }

    // ==========================================
    // HELPER UI BUILDERS & MODELS
    // ==========================================
    private StackPane createAvatarCircle(String imagePath, String fallbackName, double radius, String bgColor) {
        StackPane pane = new StackPane();
        Circle circ = new Circle(radius);
        circ.setFill(Color.web(bgColor));
        circ.setStroke(Color.web("#ffffff", 0.3));
        circ.setStrokeWidth(1.5);

        if (imagePath != null && !imagePath.isEmpty() && new File(imagePath).exists()) {
            Image img = new Image(new File(imagePath).toURI().toString());
            circ.setFill(new ImagePattern(img));
            pane.getChildren().add(circ);
        } else {
            String initial = fallbackName.isEmpty() ? "C" : fallbackName.substring(0, 1).toUpperCase();
            Label lbl = new Label(initial);
            lbl.setStyle("-fx-text-fill: white; -fx-font-weight: 800; -fx-font-size: " + (radius * 0.8) + "px;");
            pane.getChildren().addAll(circ, lbl);
        }
        return pane;
    }

    private VBox createPastEventCard(PastCampusEvent ev) {
        VBox card = new VBox(8);
        card.setStyle("-fx-background-color: #111827; -fx-padding: 14 18; -fx-background-radius: 10px; -fx-border-color: #1f2937; -fx-border-radius: 10px;");
        Label t = new Label(ev.eventName + " (" + ev.clubName + ")");
        t.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label d = new Label("Date: " + ev.dateHappened + " | Turnout: " + ev.attendanceCount + " | Certificate Status: " + ev.videoArchiveStatus);
        d.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        card.getChildren().addAll(t, d);
        return card;
    }

    private VBox createFormSection(String title) {
        VBox section = new VBox(12);
        section.setStyle("-fx-background-color: #111827; -fx-background-radius: 12px; -fx-border-color: #1f2937; -fx-border-radius: 12px; -fx-padding: 20;");
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #38bdf8; -fx-font-size: 14px; -fx-font-weight: bold;");
        section.getChildren().add(lbl);
        return section;
    }

    private Label createFieldLabel(String text) {
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: #9ca3af; -fx-font-size: 13px;");
        lbl.setMinWidth(170);
        return lbl;
    }

    private TextField createStyledTextField(String prompt) {
        TextField tf = new TextField();
        tf.setPromptText(prompt);
        tf.setPrefWidth(350);
        tf.setStyle("-fx-background-color: #0b0f19; -fx-text-fill: #f9fafb; -fx-prompt-text-fill: #4b5563; -fx-border-color: #1f2937; -fx-border-radius: 6px; -fx-padding: 8 12;");
        return tf;
    }

    private VBox createFieldContainer(String labelText, Control input) {
        VBox container = new VBox(6);
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #d1d5db; -fx-font-size: 12px; -fx-font-weight: bold;");
        container.getChildren().addAll(lbl, input);
        return container;
    }

    private TextField createWebInput(String prompt, String example) {
        TextField tf = new TextField();
        tf.setPromptText(example);
        tf.setMaxWidth(Double.MAX_VALUE);
        tf.setStyle("-fx-background-color: #111827; -fx-text-fill: #f9fafb; -fx-prompt-text-fill: #4b5563; -fx-border-color: #374151; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10 14; -fx-font-size: 13px;");
        return tf;
    }

    private PasswordField createWebPassword(String prompt, String example) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(example);
        pf.setMaxWidth(Double.MAX_VALUE);
        pf.setStyle("-fx-background-color: #111827; -fx-text-fill: #f9fafb; -fx-prompt-text-fill: #4b5563; -fx-border-color: #374151; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 10 14; -fx-font-size: 13px;");
        return pf;
    }

    public static class AttendeeRecord {
        public final String name, rollNumber, department, utr, email, timestamp;
        public AttendeeRecord(String name, String rollNumber, String department, String utr, String email, String timestamp) {
            this.name = name;
            this.rollNumber = rollNumber;
            this.department = department;
            this.utr = utr;
            this.email = email;
            this.timestamp = timestamp;
        }
    }

    public static class RegisteredRecord {
        public final String eventName, clubName, certificateStatus;
        public RegisteredRecord(String eventName, String clubName, String certificateStatus) {
            this.eventName = eventName;
            this.clubName = clubName;
            this.certificateStatus = certificateStatus;
        }
    }

    public static class StudentSession {
        public final String rollNumber, name, email, department;
        public final List<RegisteredRecord> pastParticipated = new ArrayList<>();

        public StudentSession(String rollNumber, String name, String email, String department) {
            this.rollNumber = rollNumber;
            this.name = name;
            this.email = email;
            this.department = department;
        }
    }

    public static class PastCampusEvent {
        public final String eventName, clubName, typeCredits, dateHappened, attendanceCount, videoArchiveStatus, specialNote;
        public PastCampusEvent(String eventName, String clubName, String typeCredits, String dateHappened, String attendanceCount, String videoArchiveStatus, String specialNote) {
            this.eventName = eventName;
            this.clubName = clubName;
            this.typeCredits = typeCredits;
            this.dateHappened = dateHappened;
            this.attendanceCount = attendanceCount;
            this.videoArchiveStatus = videoArchiveStatus;
            this.specialNote = specialNote;
        }
    }

    public static class CampusEventCardData {
        public final int eventId;
        public final String eventName, clubName, type;
        public final int credits;
        public final String status, studentCoordinators, facultyCoordinators;
        public int bookedSeats;
        public final int totalCapacity;
        public final String colorTheme, posterLogo, timeLeft, registrationAmount, whatsappLink, paymentQrPath;
        public final boolean isCertificateProvided;
        public final String certTimeline;
        public final List<AttendeeRecord> attendees = new ArrayList<>();

        public CampusEventCardData(int eventId, String eventName, String clubName, String type, int credits, String status, String studentCoordinators, String facultyCoordinators, int bookedSeats, int totalCapacity, String colorTheme, String posterLogo, String timeLeft, String registrationAmount, String whatsappLink, String paymentQrPath, boolean isCertificateProvided, String certTimeline) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.clubName = clubName;
            this.type = type;
            this.credits = credits;
            this.status = status;
            this.studentCoordinators = studentCoordinators;
            this.facultyCoordinators = facultyCoordinators;
            this.bookedSeats = bookedSeats;
            this.totalCapacity = totalCapacity;
            this.colorTheme = colorTheme;
            this.posterLogo = posterLogo;
            this.timeLeft = timeLeft;
            this.registrationAmount = registrationAmount;
            this.whatsappLink = whatsappLink;
            this.paymentQrPath = paymentQrPath;
            this.isCertificateProvided = isCertificateProvided;
            this.certTimeline = certTimeline;
        }
    }

    public static class CoordinatorRow {
        private String index, name, contact;
        public CoordinatorRow(String index, String name, String contact) {
            this.index = index;
            this.name = name;
            this.contact = contact;
        }
        public String getName() { return name; }
        public String getContact() { return contact; }
        public void setName(String name) { this.name = name; }
        public void setContact(String contact) { this.contact = contact; }
    }

    public static void main(String[] args) {
        launch(args);
    }
}