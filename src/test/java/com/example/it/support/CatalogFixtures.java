package com.example.it.support;

import static io.restassured.RestAssured.given;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Creates test data through catalog-service's public API. Every book gets a unique ISBN so tests
 * never depend on seed data or on each other.
 */
public final class CatalogFixtures {

    private CatalogFixtures() {
    }

    public static String uniqueIsbn() {
        return "978" + ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
    }

    public static Map<String, Object> bookPayload(String title, String price, int stock) {
        return Map.of(
                "isbn", uniqueIsbn(),
                "title", title,
                "author", "Integration Tester",
                "price", price,
                "stock", stock);
    }

    /** Creates a book and returns its id. */
    public static String createBook(String title, String price, int stock) {
        return given(Specs.catalog())
                .body(bookPayload(title, price, stock))
                .when()
                .post("/api/books")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    public static int stockOf(String bookId) {
        return given(Specs.catalog())
                .when()
                .get("/api/books/{id}", bookId)
                .then()
                .statusCode(200)
                .extract().path("stock");
    }
}
