package com.example.it.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

import com.example.it.support.CatalogFixtures;
import com.example.it.support.Specs;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("catalog-service: books")
class CatalogBooksIT {

    @Test
    void createsAndFetchesABook() {
        Map<String, Object> payload = CatalogFixtures.bookPayload("Refactoring", "47.25", 4);

        String id = given(Specs.catalog())
                .body(payload)
                .when()
                .post("/api/books")
                .then()
                .statusCode(201)
                .header("Location", startsWith("/api/books/"))
                .body("id", notNullValue())
                .body("isbn", equalTo(payload.get("isbn")))
                .extract().path("id");

        given(Specs.catalog())
                .when()
                .get("/api/books/{id}", id)
                .then()
                .statusCode(200)
                .body("title", equalTo("Refactoring"))
                .body("author", equalTo("Integration Tester"))
                .body("price", equalTo(47.25f))
                .body("stock", equalTo(4));
    }

    @Test
    void listsBooksAndFiltersByAuthor() {
        String id = CatalogFixtures.createBook("Domain-Driven Design", "59.00", 2);

        given(Specs.catalog())
                .queryParam("author", "integration tester")
                .when()
                .get("/api/books")
                .then()
                .statusCode(200)
                .body("id", hasItem(id))
                .body("author", everyItem(equalTo("Integration Tester")));
    }

    @Test
    void updatesABook() {
        String id = CatalogFixtures.createBook("Old Title", "10.00", 1);
        Map<String, Object> update = new HashMap<>(CatalogFixtures.bookPayload("New Title", "12.50", 7));

        given(Specs.catalog())
                .body(update)
                .when()
                .put("/api/books/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("title", equalTo("New Title"))
                .body("stock", equalTo(7));
    }

    @Test
    void deletesABook() {
        String id = CatalogFixtures.createBook("Short-lived", "5.00", 1);

        given(Specs.catalog()).when().delete("/api/books/{id}", id).then().statusCode(204);
        given(Specs.catalog()).when().get("/api/books/{id}", id).then().statusCode(404);
    }

    @Test
    void returns404ProblemForUnknownBook() {
        given(Specs.catalog())
                .when()
                .get("/api/books/{id}", "does-not-exist")
                .then()
                .statusCode(404)
                .body("status", equalTo(404))
                .body("detail", equalTo("Book does-not-exist not found"));
    }

    @Test
    void rejectsDuplicateIsbn() {
        Map<String, Object> payload = CatalogFixtures.bookPayload("Original", "20.00", 1);
        given(Specs.catalog()).body(payload).when().post("/api/books").then().statusCode(201);

        given(Specs.catalog())
                .body(payload)
                .when()
                .post("/api/books")
                .then()
                .statusCode(409);
    }

    @Test
    void rejectsInvalidBook() {
        given(Specs.catalog())
                .body(Map.of("isbn", "", "title", "", "author", "x", "price", "-1", "stock", -3))
                .when()
                .post("/api/books")
                .then()
                .statusCode(400);
    }
}
