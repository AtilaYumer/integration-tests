package com.example.it.order;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import com.example.it.support.CatalogFixtures;
import com.example.it.support.Specs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("order-service: orders")
class OrdersIT {

    @Test
    void placesAnOrderWithCatalogPrices() {
        String bookId = CatalogFixtures.createBook("Java Concurrency in Practice", "42.50", 10);

        String orderId = given(Specs.order())
                .body(Map.of("customerName", "Ada", "items", List.of(Map.of("bookId", bookId, "quantity", 2))))
                .when()
                .post("/api/orders")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("CREATED"))
                .body("customerName", equalTo("Ada"))
                .body("lines", hasSize(1))
                .body("lines[0].title", equalTo("Java Concurrency in Practice"))
                .body("lines[0].unitPrice", equalTo(42.5f))
                .body("total", equalTo(85.0f))
                .extract().path("id");

        given(Specs.order()).when().get("/api/orders/{id}", orderId).then().statusCode(200)
                .body("id", equalTo(orderId));
        given(Specs.order()).when().get("/api/orders").then().statusCode(200)
                .body("id", hasItem(orderId));
    }

    @Test
    void returns404ForUnknownOrder() {
        given(Specs.order())
                .when()
                .get("/api/orders/{id}", "does-not-exist")
                .then()
                .statusCode(404)
                .body("detail", equalTo("Order does-not-exist not found"));
    }

    @Test
    void rejectsOrderWithoutItems() {
        given(Specs.order())
                .body(Map.of("customerName", "Ada", "items", List.of()))
                .when()
                .post("/api/orders")
                .then()
                .statusCode(400);
    }

    @Test
    void rejectsOrderForBookMissingFromCatalog() {
        given(Specs.order())
                .body(Map.of("customerName", "Ada", "items", List.of(Map.of("bookId", "does-not-exist", "quantity", 1))))
                .when()
                .post("/api/orders")
                .then()
                .statusCode(422);
    }

    @Test
    void cannotCancelTwice() {
        String bookId = CatalogFixtures.createBook("Once", "9.99", 3);
        String orderId = given(Specs.order())
                .body(Map.of("customerName", "Ada", "items", List.of(Map.of("bookId", bookId, "quantity", 1))))
                .when().post("/api/orders")
                .then().statusCode(201).extract().path("id");

        given(Specs.order()).when().post("/api/orders/{id}/cancel", orderId).then().statusCode(200)
                .body("status", equalTo("CANCELLED"));
        given(Specs.order()).when().post("/api/orders/{id}/cancel", orderId).then().statusCode(409);
    }
}
