package com.catalogix.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email must not be blank")
    private String email;

    @NotBlank(message = "Password must not be blank")
    private String password;

    public LoginRequest() {
        /* No-arg constructor required by frameworks (e.g. Jackson, Hibernate)
           for deserialization and reflective instantiation; intentionally left empty. */
    }

    public String getEmail() { return email;}

    public void setEmail(String email) { this.email = email;}

    public String getPassword() { return password;}

    public void setPassword(String password) { this.password = password; }
}
