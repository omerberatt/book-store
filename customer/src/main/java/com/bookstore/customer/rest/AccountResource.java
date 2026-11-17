package com.bookstore.customer.rest;

import com.bookstore.customer.api.AccountFacade;
import com.bookstore.customer.api.CustomerView;
import com.bookstore.customer.rest.AccountRequests.AddressRequest;
import com.bookstore.customer.rest.AccountRequests.PasswordChangeRequest;
import com.bookstore.customer.rest.AccountRequests.ProfileRequest;
import com.bookstore.customer.rest.AccountRequests.RegistrationRequest;
import com.bookstore.shared.kernel.identity.CustomerId;
import com.bookstore.shared.web.Routes;
import com.bookstore.shared.web.WebRequest;
import com.bookstore.shared.web.WebResponse;

import java.util.Comparator;
import java.util.Objects;

/**
 * The account endpoints (ACC-1 – ACC-5). Registration is open to everyone; everything under
 * {@code /api/v1/me} is about the signed-in customer, who is never named in the path, so nobody
 * can reach another customer's account.
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><td>{@code POST /api/v1/customers}</td><td>register</td></tr>
 *   <tr><td>{@code GET|PUT /api/v1/me}</td><td>the profile</td></tr>
 *   <tr><td>{@code PUT /api/v1/me/password}</td><td>change the password</td></tr>
 *   <tr><td>{@code POST /api/v1/me/addresses}</td><td>save an address</td></tr>
 *   <tr><td>{@code GET|PUT|DELETE /api/v1/me/addresses/{id}}</td><td>one saved address</td></tr>
 *   <tr><td>{@code POST /api/v1/me/addresses/{id}/default}</td><td>make it the default</td></tr>
 * </table>
 */
public final class AccountResource implements Routes.Resource {

    private final AccountFacade accounts;

    /**
     * Creates the resource.
     *
     * @param accounts the account use cases
     */
    public AccountResource(AccountFacade accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    @Override
    public void register(Routes routes) {
        routes.post("/api/v1/customers", this::registerCustomer)
                .get("/api/v1/me", request -> WebResponse.ok(accounts.profile(me(request))))
                .put("/api/v1/me", this::updateProfile)
                .put("/api/v1/me/password", this::changePassword)
                .post("/api/v1/me/addresses", this::addAddress)
                .get("/api/v1/me/addresses/{id}",
                        request -> WebResponse.ok(accounts.address(me(request), request.pathId("id"))))
                .put("/api/v1/me/addresses/{id}", this::changeAddress)
                .delete("/api/v1/me/addresses/{id}", this::removeAddress)
                .post("/api/v1/me/addresses/{id}/default", request -> WebResponse.ok(
                        accounts.makeDefaultAddress(me(request), request.pathId("id"))));
    }

    private WebResponse registerCustomer(WebRequest request) {
        CustomerView created = accounts.register(request.body(RegistrationRequest.class).toCommand());
        return WebResponse.created("/api/v1/me", created);
    }

    private WebResponse updateProfile(WebRequest request) {
        ProfileRequest profile = request.body(ProfileRequest.class);
        return WebResponse.ok(accounts.updateProfile(me(request), profile.firstName(), profile.lastName(),
                profile.email()));
    }

    private WebResponse changePassword(WebRequest request) {
        PasswordChangeRequest change = request.body(PasswordChangeRequest.class);
        accounts.changePassword(me(request), change.currentPassword(), change.newPassword());
        return WebResponse.noContent();
    }

    private WebResponse addAddress(WebRequest request) {
        AddressRequest address = request.body(AddressRequest.class);
        CustomerView profile = accounts.addAddress(me(request), address.label(), address.toAddress(),
                address.makeDefault());
        long newest = profile.addresses().stream()
                .max(Comparator.comparingLong(CustomerView.AddressView::id))
                .orElseThrow()
                .id();
        return WebResponse.created("/api/v1/me/addresses/" + newest, profile);
    }

    private WebResponse changeAddress(WebRequest request) {
        AddressRequest address = request.body(AddressRequest.class);
        return WebResponse.ok(accounts.changeAddress(me(request), request.pathId("id"), address.label(),
                address.toAddress()));
    }

    private WebResponse removeAddress(WebRequest request) {
        accounts.removeAddress(me(request), request.pathId("id"));
        return WebResponse.noContent();
    }

    private static CustomerId me(WebRequest request) {
        return request.customer().customerId();
    }
}
