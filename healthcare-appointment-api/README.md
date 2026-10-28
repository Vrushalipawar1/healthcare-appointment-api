# Healthcare Appointment Booking API

A REST backend where patients book appointments with doctors. Built with Java 17, Spring Boot 3, Spring Security (JWT), Spring Data JPA, and tested with JUnit 5, Mockito and REST Assured.

![CI](../../actions/workflows/ci.yml/badge.svg)

## Features

- Register / login with JWT (stateless, BCrypt-hashed passwords)
- Role-based access: `PATIENT`, `DOCTOR`, `ADMIN`
- Book an appointment with a doctor, with **overlap detection** (back-to-back slots are allowed)
- Cancel (patient, treating doctor or admin) and complete (treating doctor only)
- Request validation with clear field-level error messages
- Swagger UI at `/swagger-ui.html`

## Run it

```bash
mvn spring-boot:run            # H2 in-memory database, no setup needed
```

Open http://localhost:8080/swagger-ui.html

With MySQL via Docker:

```bash
docker compose up --build      # starts MySQL + the API on :8080
```

Demo accounts created on startup (local only, `app.seed.enabled=true`):

| Role   | Email                    | Password     |
|--------|--------------------------|--------------|
| ADMIN  | admin@healthcare.local   | Admin@1234   |
| DOCTOR | doctor@healthcare.local  | Doctor@1234  |

## Run the tests

```bash
mvn test
```

| Layer | What it covers |
|-------|----------------|
| `JwtServiceTest` | token creation, tampered / expired / garbage tokens |
| `AuthServiceTest` (Mockito) | hashing, email normalisation, duplicate email, admin self-registration blocked, login failures |
| `AppointmentServiceTest` (Mockito) | booking rules, overlap window, who may cancel / complete, status transitions |
| `AppointmentApiIntegrationTest` (REST Assured) | full HTTP flow through the real security filter chain: 401 vs 403, validation errors, double booking, cancel frees the slot, data isolation between patients |

## API

| Method | Path | Who | Notes |
|--------|------|-----|-------|
| POST | `/api/auth/register` | public | `role` must be `PATIENT` or `DOCTOR` |
| POST | `/api/auth/login` | public | returns a JWT |
| GET | `/api/doctors` | any logged-in user | list doctors and their ids |
| POST | `/api/appointments` | PATIENT | `409` if the doctor is already booked |
| GET | `/api/appointments/me` | PATIENT, DOCTOR | your own appointments only |
| PATCH | `/api/appointments/{id}/cancel` | PATIENT, DOCTOR, ADMIN | participants or admin |
| PATCH | `/api/appointments/{id}/complete` | DOCTOR | treating doctor only |
| GET | `/api/admin/appointments` | ADMIN | all appointments |

Send the token as `Authorization: Bearer <token>`.

Example:

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Asha","email":"asha@example.com","password":"Password1","role":"PATIENT"}'

curl -X POST localhost:8080/api/appointments \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"doctorId":2,"startTime":"2030-01-15T10:00:00","durationMinutes":30,"reason":"Check-up"}'
```

## Design decisions (good interview material)

- **Overlap check** is one query: two slots clash when `existing.start < new.end AND existing.end > new.start`. That is why a slot starting exactly when another ends is allowed.
- **401 vs 403**: a missing or invalid token returns 401 (custom entry point); a valid token without the right role returns 403.
- **Admin accounts can't be self-registered**; otherwise anyone could grant themselves full access.
- **Login errors are identical** for "unknown email" and "wrong password" so accounts can't be enumerated.
- **Known limitation:** the overlap check and the insert are not atomic, so two simultaneous requests for the same slot could both succeed. A production fix is a pessimistic lock on the doctor row or a database constraint. Mentioning this in an interview shows you understand concurrency.

## Project layout

```
controller/   HTTP layer, role rules via @PreAuthorize
service/      business rules (booking, cancel, complete, auth)
repository/   Spring Data JPA
security/     JWT service, request filter, security config
exception/    error types and one global handler
dto/          request / response records
```
