package com.catalogix.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

// All fields are optional except currentPassword, which is required when email or
// newPassword changes (see UserSvc.updateProfile).
public class UpdateProfileRequest {

    private String name;

    @Email(message = "email must be valid")
    private String email;

    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{6,}$",
            message = "newPassword must be at least 6 characters and include a letter and a number")
    private String newPassword;

    private String currentPassword;

    public UpdateProfileRequest() {
        /*
         * Required by Jackson to instantiate this DTO during JSON deserialization.
         * Fields are populated through the setters after construction.
         */
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String currentPassword) { this.currentPassword = currentPassword; }
}
