package org.acme.rest.error;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

/**
 * End-to-end contract test for the RFC 7807 {@link ProblemDetail} body (ADR 0012).
 *
 * <p>Every assertion here reflects a guarantee the template must not silently break:
 * media type, status/title/detail correlation, {@code requestId} correlation with logs,
 * per-field errors for validation. If any of these are re-touched, the test rewrites the
 * baseline — the intent is to catch regressions in mappers, not to exercise the happy-path
 * routes (covered by {@link org.acme.ProductResourceTest}).
 */
@QuarkusTest
class ProblemDetailContractTest {

    @Test
    void notFoundEmitsProblemDetail() {
        given()
                .accept(ContentType.ANY)
                .when()
                .get("/products/999999")
                .then()
                .statusCode(404)
                .contentType(startsWith(ProblemDetail.MEDIA_TYPE))
                .body("status", is(404))
                .body("title", is("Not Found"))
                .body("type", is("about:blank"))
                .body("detail", notNullValue())
                .body("instance", is("/products/999999"))
                .body("timestamp", notNullValue());
    }

    @Test
    void badRequestValidationEmitsFieldErrors() {
        String invalidJson = """
                { "name": "", "price": -50.0 }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(invalidJson)
                .when()
                .post("/products")
                .then()
                .statusCode(400)
                .contentType(startsWith(ProblemDetail.MEDIA_TYPE))
                .body("status", is(400))
                .body("title", is("Bad Request"))
                .body("detail", is("Validation failed"))
                .body("errors", notNullValue())
                .body("errors.size()", not(is(0)))
                // Hamcrest: every entry should have a field and message
                .body("errors[0]", hasKey("field"))
                .body("errors[0]", hasKey("message"));
    }

    @Test
    void requestIdHeaderIsEchoedIntoBody() {
        String suppliedId = "test-correlation-0001";

        given()
                .header("X-Request-Id", suppliedId)
                .when()
                .get("/products/999999")
                .then()
                .statusCode(404)
                .header("X-Request-Id", equalTo(suppliedId))
                .body("requestId", is(suppliedId));
    }

    @Test
    void legacyErrorShapeIsGone() {
        // The old GlobalExceptionMapper returned an ErrorResponse record with fields
        // {status,error,message,path,timestamp}. Make sure nothing in the pipeline still
        // produces that shape by asserting the *new* field names are present (and the
        // old `error` field — reserved by RFC 7807 for the `title` member — is not).
        given()
                .when()
                .get("/products/999999")
                .then()
                .statusCode(404)
                .body("title", notNullValue())
                .body("error", org.hamcrest.Matchers.nullValue());
    }
}
