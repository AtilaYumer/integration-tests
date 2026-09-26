package com.example.it.support;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/** Shared Rest Assured request specifications, one per service. */
public final class Specs {

    private Specs() {
    }

    public static RequestSpecification catalog() {
        return base(ServiceEndpoints.catalog());
    }

    public static RequestSpecification order() {
        return base(ServiceEndpoints.order());
    }

    private static RequestSpecification base(String baseUri) {
        return new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .log(LogDetail.URI)
                .build();
    }
}
