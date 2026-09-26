package com.example.it.catalog;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import com.example.it.support.CatalogFixtures;
import com.example.it.support.Specs;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("catalog-service: stock adjustments")
class CatalogStockIT {

    @Test
    void reservesAndReleasesStock() {
        String id = CatalogFixtures.createBook("Stocked", "15.00", 5);

        given(Specs.catalog())
                .body(Map.of("delta", -3))
                .when()
                .patch("/api/books/{id}/stock", id)
                .then()
                .statusCode(200)
                .body("stock", equalTo(2));

        given(Specs.catalog())
                .body(Map.of("delta", 3))
                .when()
                .patch("/api/books/{id}/stock", id)
                .then()
                .statusCode(200)
                .body("stock", equalTo(5));
    }

    @Test
    void refusesToReserveMoreThanAvailable() {
        String id = CatalogFixtures.createBook("Scarce", "15.00", 1);

        given(Specs.catalog())
                .body(Map.of("delta", -2))
                .when()
                .patch("/api/books/{id}/stock", id)
                .then()
                .statusCode(409);

        given(Specs.catalog()).when().get("/api/books/{id}", id).then().body("stock", equalTo(1));
    }

    @Test
    void returns404WhenAdjustingUnknownBook() {
        given(Specs.catalog())
                .body(Map.of("delta", -1))
                .when()
                .patch("/api/books/{id}/stock", "does-not-exist")
                .then()
                .statusCode(404);
    }
}
