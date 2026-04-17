package org.acme.security;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

@QuarkusTest
public class AdminResourceAuthzTest {

    @Test
    public void adminPing_unauthenticated_is401() {
        given().when().get("/api/admin/ping").then().statusCode(401);
    }

    @Test
    @TestSecurity(user = "alice", roles = {"user"})
    public void adminPing_userRole_is403() {
        given().when().get("/api/admin/ping").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "bob", roles = {"admin"})
    public void adminPing_adminRole_is200() {
        given().when().get("/api/admin/ping").then().statusCode(200).body("status", is("ok"));
    }
}

