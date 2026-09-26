package com.example.it.flows;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.it.support.CatalogFixtures;
import com.example.it.support.Specs;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cross-service behaviour: order-service must keep catalog-service's stock consistent. */
@DisplayName("order-service + catalog-service: stock consistency")
class OrderStockFlowIT {

    @Test
    void placingAnOrderReservesStockAndCancellingReleasesIt() {
        String bookId = CatalogFixtures.createBook("The Pragmatic Programmer", "39.95", 5);

        String orderId = given(Specs.order())
                .body(Map.of("customerName", "Grace", "items", List.of(Map.of("bookId", bookId, "quantity", 3))))
                .when().post("/api/orders")
                .then().statusCode(201).extract().path("id");
        assertThat(CatalogFixtures.stockOf(bookId)).isEqualTo(2);

        given(Specs.order()).when().post("/api/orders/{id}/cancel", orderId).then().statusCode(200);
        assertThat(CatalogFixtures.stockOf(bookId)).isEqualTo(5);
    }

    @Test
    void insufficientStockRejectsOrderWithoutReservingAnything() {
        String plentiful = CatalogFixtures.createBook("Plentiful", "10.00", 10);
        String scarce = CatalogFixtures.createBook("Scarce", "10.00", 1);

        given(Specs.order())
                .body(Map.of("customerName", "Grace", "items", List.of(
                        Map.of("bookId", plentiful, "quantity", 4),
                        Map.of("bookId", scarce, "quantity", 2))))
                .when().post("/api/orders")
                .then().statusCode(409);

        assertThat(CatalogFixtures.stockOf(plentiful))
                .as("the earlier line's reservation is rolled back")
                .isEqualTo(10);
        assertThat(CatalogFixtures.stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void orderUsesPriceFromCatalogAtOrderTime() {
        String bookId = CatalogFixtures.createBook("Repriced", "20.00", 5);
        Map<String, Object> repriced = new HashMap<>(CatalogFixtures.bookPayload("Repriced", "25.00", 5));
        given(Specs.catalog()).body(repriced).when().put("/api/books/{id}", bookId).then().statusCode(200);

        float total = given(Specs.order())
                .body(Map.of("customerName", "Grace", "items", List.of(Map.of("bookId", bookId, "quantity", 2))))
                .when().post("/api/orders")
                .then().statusCode(201).extract().path("total");

        assertThat(total).isEqualTo(50.0f);
    }
}
