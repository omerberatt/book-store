package com.bookstore.app;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * The JAX-RS application (ADR-0009, ADR-0010): every module's {@code rest} resources live under
 * {@code /api/v1}. The server finds the resources and the {@code shared-web} providers (problem
 * mappers, JSON-B settings, HTTP Basic filter) in the WAR by themselves, so nothing is listed here;
 * the rest of the path stays free for the HTML pages of Phase 7.
 */
@ApplicationPath("api/v1")
public class BookstoreApplication extends Application {
}
