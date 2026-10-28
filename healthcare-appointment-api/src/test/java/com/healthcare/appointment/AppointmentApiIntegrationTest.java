package com.healthcare.appointment;

import com.healthcare.appointment.model.Role;
import com.healthcare.appointment.model.User;
import com.healthcare.appointment.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end API tests: a real server on a random port, real security filter chain and an
 * in-memory H2 database. Every test creates its own uniquely named users, so tests don't
 * depend on each other or on execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentApiIntegrationTest {

    private static final String PASSWORD = "Password1";

    @Value("${local.server.port}")
    private int port;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void configureRestAssured() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    // ================= registration & login =================

    @Test
    void register_patient_returns201_andToken() {
        given().contentType(ContentType.JSON)
                .body(registerBody("Asha", uniqueEmail(), "PATIENT"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("token", notNullValue())
                .body("role", equalTo("PATIENT"));
    }

    @Test
    void register_duplicateEmail_returns409() {
        String email = uniqueEmail();
        register("Asha", email, "PATIENT");

        given().contentType(ContentType.JSON)
                .body(registerBody("Asha Again", email, "PATIENT"))
                .when().post("/api/auth/register")
                .then().statusCode(409);
    }

    @Test
    void register_invalidEmailAndShortPassword_returns400_withFieldErrors() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Asha");
        body.put("email", "not-an-email");
        body.put("password", "123");
        body.put("role", "PATIENT");

        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/auth/register")
                .then().statusCode(400)
                .body("fieldErrors.email", notNullValue())
                .body("fieldErrors.password", notNullValue());
    }

    @Test
    void register_asAdmin_returns400() {
        given().contentType(ContentType.JSON)
                .body(registerBody("Eve", uniqueEmail(), "ADMIN"))
                .when().post("/api/auth/register")
                .then().statusCode(400);
    }

    @Test
    void login_withValidCredentials_returnsToken() {
        String email = uniqueEmail();
        register("Asha", email, "PATIENT");

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("token", notNullValue());
    }

    @Test
    void login_withWrongPassword_returns401() {
        String email = uniqueEmail();
        register("Asha", email, "PATIENT");

        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", "WrongPass9"))
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    // ================= authentication =================

    @Test
    void protectedEndpoint_withoutToken_returns401() {
        given().when().get("/api/doctors").then().statusCode(401);
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() {
        given().header("Authorization", "Bearer this.is.not-a-real-token")
                .when().get("/api/doctors")
                .then().statusCode(401);
    }

    @Test
    void listDoctors_withToken_includesRegisteredDoctor() {
        String doctorName = "Dr " + UUID.randomUUID();
        register(doctorName, uniqueEmail(), "DOCTOR");
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");

        given().header("Authorization", "Bearer " + patientToken)
                .when().get("/api/doctors")
                .then().statusCode(200)
                .body("name", hasItem(doctorName));
    }

    // ================= booking =================

    @Test
    void patient_booksAppointment_returns201_withBookedStatus() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");

        given().header("Authorization", "Bearer " + patientToken)
                .contentType(ContentType.JSON)
                .body(bookingBody(doctor.id, slot(10, 0), 30))
                .when().post("/api/appointments")
                .then().statusCode(201)
                .body("status", equalTo("BOOKED"))
                .body("doctorName", equalTo(doctor.name))
                .body("id", notNullValue());
    }

    @Test
    void doubleBooking_sameSlot_returns409() {
        Doctor doctor = newDoctor();
        String firstPatient = register("Pat1", uniqueEmail(), "PATIENT");
        String secondPatient = register("Pat2", uniqueEmail(), "PATIENT");
        book(firstPatient, doctor.id, slot(10, 0), 30).then().statusCode(201);

        book(secondPatient, doctor.id, slot(10, 0), 30).then().statusCode(409);
    }

    @Test
    void booking_partiallyOverlappingSlot_returns409() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        book(patientToken, doctor.id, slot(10, 0), 60).then().statusCode(201);

        book(patientToken, doctor.id, slot(10, 30), 30).then().statusCode(409);
    }

    @Test
    void booking_backToBackSlot_isAllowed() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        book(patientToken, doctor.id, slot(10, 0), 30).then().statusCode(201);

        // starts exactly when the first one ends
        book(patientToken, doctor.id, slot(10, 30), 30).then().statusCode(201);
    }

    @Test
    void doctor_cannotBook_returns403() {
        Doctor doctor = newDoctor();

        book(doctor.token, doctor.id, slot(10, 0), 30).then().statusCode(403);
    }

    @Test
    void booking_inThePast_returns400() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        String yesterday = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now().minusDays(1));

        book(patientToken, doctor.id, yesterday, 30).then().statusCode(400);
    }

    @Test
    void booking_unknownDoctor_returns404() {
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");

        book(patientToken, 999_999L, slot(10, 0), 30).then().statusCode(404);
    }

    @Test
    void booking_withTooShortDuration_returns400() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");

        book(patientToken, doctor.id, slot(10, 0), 5).then().statusCode(400);
    }

    // ================= cancel / complete =================

    @Test
    void owner_cancelsAppointment_returns200_andFreesTheSlot() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        long appointmentId = bookAndGetId(patientToken, doctor.id, slot(10, 0), 30);

        given().header("Authorization", "Bearer " + patientToken)
                .when().patch("/api/appointments/" + appointmentId + "/cancel")
                .then().statusCode(200)
                .body("status", equalTo("CANCELLED"));

        // the cancelled slot can be booked again
        book(patientToken, doctor.id, slot(10, 0), 30).then().statusCode(201);
    }

    @Test
    void otherPatient_cannotCancel_returns403() {
        Doctor doctor = newDoctor();
        String owner = register("Owner", uniqueEmail(), "PATIENT");
        String stranger = register("Stranger", uniqueEmail(), "PATIENT");
        long appointmentId = bookAndGetId(owner, doctor.id, slot(10, 0), 30);

        given().header("Authorization", "Bearer " + stranger)
                .when().patch("/api/appointments/" + appointmentId + "/cancel")
                .then().statusCode(403);
    }

    @Test
    void cancellingTwice_returns409() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        long appointmentId = bookAndGetId(patientToken, doctor.id, slot(10, 0), 30);
        given().header("Authorization", "Bearer " + patientToken)
                .when().patch("/api/appointments/" + appointmentId + "/cancel")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + patientToken)
                .when().patch("/api/appointments/" + appointmentId + "/cancel")
                .then().statusCode(409);
    }

    @Test
    void doctor_completesAppointment_returns200() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        long appointmentId = bookAndGetId(patientToken, doctor.id, slot(10, 0), 30);

        given().header("Authorization", "Bearer " + doctor.token)
                .when().patch("/api/appointments/" + appointmentId + "/complete")
                .then().statusCode(200)
                .body("status", equalTo("COMPLETED"));
    }

    @Test
    void patient_cannotComplete_returns403() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        long appointmentId = bookAndGetId(patientToken, doctor.id, slot(10, 0), 30);

        given().header("Authorization", "Bearer " + patientToken)
                .when().patch("/api/appointments/" + appointmentId + "/complete")
                .then().statusCode(403);
    }

    // ================= listing & admin =================

    @Test
    void patient_seesOnlyOwnAppointments() {
        Doctor doctor = newDoctor();
        String owner = register("Owner", uniqueEmail(), "PATIENT");
        String stranger = register("Stranger", uniqueEmail(), "PATIENT");
        book(owner, doctor.id, slot(10, 0), 30).then().statusCode(201);

        given().header("Authorization", "Bearer " + owner)
                .when().get("/api/appointments/me")
                .then().statusCode(200).body("$", hasSize(1));

        given().header("Authorization", "Bearer " + stranger)
                .when().get("/api/appointments/me")
                .then().statusCode(200).body("$", hasSize(0));
    }

    @Test
    void doctor_seesTheirAppointments() {
        Doctor doctor = newDoctor();
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");
        book(patientToken, doctor.id, slot(10, 0), 30).then().statusCode(201);

        given().header("Authorization", "Bearer " + doctor.token)
                .when().get("/api/appointments/me")
                .then().statusCode(200).body("$", hasSize(1));
    }

    @Test
    void admin_canListAllAppointments() {
        String adminToken = adminToken();

        given().header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/appointments")
                .then().statusCode(200);
    }

    @Test
    void patient_cannotListAllAppointments_returns403() {
        String patientToken = register("Pat", uniqueEmail(), "PATIENT");

        given().header("Authorization", "Bearer " + patientToken)
                .when().get("/api/admin/appointments")
                .then().statusCode(403);
    }

    // ================= helpers =================

    private record Doctor(long id, String name, String token) {
    }

    private Doctor newDoctor() {
        String name = "Dr " + UUID.randomUUID();
        String email = uniqueEmail();
        String token = register(name, email, "DOCTOR");
        long id = userRepository.findByEmail(email).orElseThrow().getId();
        return new Doctor(id, name, token);
    }

    private String adminToken() {
        String email = uniqueEmail();
        // ADMIN can't be registered through the API, so create one directly
        userRepository.save(new User("Admin", email, passwordEncoder.encode(PASSWORD), Role.ADMIN));
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", PASSWORD))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private String register(String name, String email, String role) {
        return given().contentType(ContentType.JSON)
                .body(registerBody(name, email, role))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .extract().path("token");
    }

    private io.restassured.response.Response book(String token, long doctorId, String startTime, int minutes) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(bookingBody(doctorId, startTime, minutes))
                .when().post("/api/appointments");
    }

    private long bookAndGetId(String token, long doctorId, String startTime, int minutes) {
        Number id = book(token, doctorId, startTime, minutes)
                .then().statusCode(201)
                .extract().path("id");
        return id.longValue();
    }

    private static Map<String, Object> registerBody(String name, String email, String role) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        return body;
    }

    private static Map<String, Object> bookingBody(long doctorId, String startTime, int minutes) {
        Map<String, Object> body = new HashMap<>();
        body.put("doctorId", doctorId);
        body.put("startTime", startTime);
        body.put("durationMinutes", minutes);
        body.put("reason", "Routine check-up");
        return body;
    }

    /** A time on a future day, so @Future validation always passes. */
    private static String slot(int hour, int minute) {
        LocalDateTime t = LocalDateTime.now().plusDays(7).withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(t);
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
