package com.bookstore.domain.customer;

import com.bookstore.common.validation.Guard;

/**
 * A person's first and last name.
 *
 * @param firstName the first name, or names
 * @param lastName  the family name
 */
public record PersonName(String firstName, String lastName) {

    /** Longest allowed first or last name. */
    public static final int MAX_LENGTH = 100;

    /** Trims and checks both parts. */
    public PersonName {
        firstName = Guard.requireText(firstName, "firstName", MAX_LENGTH);
        lastName = Guard.requireText(lastName, "lastName", MAX_LENGTH);
    }

    /**
     * Returns the name as it is usually written.
     *
     * @return for example {@code "Ayşe Yılmaz"}
     */
    public String fullName() {
        return firstName + " " + lastName;
    }

    @Override
    public String toString() {
        return fullName();
    }
}
