/**
 * The entry point that puts the business modules together: the {@link
 * com.bookstore.app.BookstoreModule} composition root (ADR-0006), the HTTP API of every module
 * ({@link com.bookstore.app.BookstoreApi}), the server that runs it
 * ({@link com.bookstore.app.BookstoreServer}) and the adapters it needs until real ones exist. In
 * Phase 6 it becomes the WAR that Payara Micro runs (ADR-0010).
 */
package com.bookstore.app;
