package com.bookstore.app;

import com.bookstore.catalog.rest.CatalogResource;
import com.bookstore.customer.rest.AccountAuthenticator;
import com.bookstore.customer.rest.AccountResource;
import com.bookstore.review.rest.ReviewResource;
import com.bookstore.sales.rest.CartResource;
import com.bookstore.sales.rest.OrderAdminResource;
import com.bookstore.sales.rest.OrderResource;
import com.bookstore.shared.web.HttpApi;
import com.bookstore.shared.web.Routes;

/**
 * Puts every module's endpoints on one {@link HttpApi} (ADR-0019): each module's {@code rest}
 * resource gets that module's façades from the {@link BookstoreModule}, and HTTP Basic sign-in goes
 * through the customer module's accounts.
 */
public final class BookstoreApi {

    private BookstoreApi() {
    }

    /**
     * Creates the API; call {@link HttpApi#start(int)} to serve it.
     *
     * @param bookstore the wired application
     * @return the API
     */
    public static HttpApi of(BookstoreModule bookstore) {
        Routes routes = new Routes()
                .add(new CatalogResource(bookstore.catalog()))
                .add(new AccountResource(bookstore.accounts()))
                .add(new CartResource(bookstore.cart()))
                .add(new OrderResource(bookstore.checkout(), bookstore.orders()))
                .add(new OrderAdminResource(bookstore.orderAdmin()))
                .add(new ReviewResource(bookstore.reviews()));
        return new HttpApi(routes, new AccountAuthenticator(bookstore.accounts()));
    }
}
