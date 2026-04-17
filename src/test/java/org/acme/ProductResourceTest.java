package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductResourceTest {

    // Store the ID of the created product between scenarios
    private static Long createdProductId;

    // --- POSITIVE SCENARIOS ---

    @Test
    @Order(1)
    public void testPostPositiveScenario() {
        String json = """
            { "name": "Test Laptop", "price": 1000.0 }
        """;

        // Create a product and extract its ID for subsequent tests
        Number extractedId = given()
            .contentType(ContentType.JSON)
            .body(json)
        .when()
            .post("/products")
        .then()
            .statusCode(201)
            .body("name", is("Test Laptop"))
            .extract().path("id");
            
        createdProductId = extractedId.longValue();
            
        // Checking that id is not null
        assertThat(createdProductId, is(notNullValue()));
    }

    @Test
    @Order(2)
    public void testGetPositiveScenario() {
        given()
        .when()
            .get("/products/" + createdProductId)
        .then()
            .statusCode(200)
            .body("name", is("Test Laptop"));
    }

    @Test
    @Order(3)
    public void testGetPaginationPositive() {
        // Create two more products to check pagination
        given().contentType(ContentType.JSON).body("{ \"name\": \"Mouse\", \"price\": 20.0 }").post("/products");
        given().contentType(ContentType.JSON).body("{ \"name\": \"Keyboard\", \"price\": 50.0 }").post("/products");

        // Requesting saved products, but limiting to size=2
        given()
        .when()
            .get("/products?page=0&size=2")
        .then()
            .statusCode(200)
            .body("data.size()", is(2)) // Only 2 elements returned
            .body("totalElements", greaterThanOrEqualTo(3)); // But total is at least 3
    }

    @Test
    @Order(4)
    public void testPutPositiveScenario() {
        String updatedJson = """
            { "name": "Updated Laptop", "price": 1500.0 }
        """;

        given()
            .contentType(ContentType.JSON)
            .body(updatedJson)
        .when()
            .put("/products/" + createdProductId)
        .then()
            .statusCode(200) // OK
            .body("name", is("Updated Laptop"))
            .body("price", is((float) 1500.0)); // RestAssured parses floating point numbers as float
    }

    @Test
    @Order(5)
    public void testDeletePositiveScenario() {
        // Deletion should return status 204 No Content
        given()
        .when()
            .delete("/products/" + createdProductId)
        .then()
            .statusCode(204);

        // Checking that a GET request after deletion returns 404
        given()
        .when()
            .get("/products/" + createdProductId)
        .then()
            .statusCode(404);
    }

    // --- NEGATIVE SCENARIOS ---

    @Test
    @Order(6)
    public void testPostNegativeValidation() {
        // Empty name, negative price (both violate our annotations)
        String invalidJson = """
            { "name": "", "price": -50.0 }
        """;

        given()
            .contentType(ContentType.JSON)
            .body(invalidJson)
        .when()
            .post("/products")
        .then()
            .statusCode(400); // 400 Bad Request from Hibernate Validator
    }

    @Test
    @Order(7)
    public void testNegativeNotFound() {
        // Checking that passing a non-existent ID triggers the correct error (404)
        long fakeId = 999999L;

        // Not found during GET
        given().when().get("/products/" + fakeId).then().statusCode(404);
        
        // Not found during PUT
        given()
            .contentType(ContentType.JSON)
            .body("{ \"name\": \"Fake\", \"price\": 100.0 }")
            .when().put("/products/" + fakeId)
            .then().statusCode(404);
            
        // Not found during DELETE
        given().when().delete("/products/" + fakeId).then().statusCode(404);
    }
}
