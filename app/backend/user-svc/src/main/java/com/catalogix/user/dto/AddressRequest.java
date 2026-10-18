package com.catalogix.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AddressRequest {

    @NotBlank(message = "label is required")
    @Size(max = 40, message = "label must be at most 40 characters")
    private String label;

    @NotBlank(message = "line1 is required")
    @Size(max = 200, message = "line1 must be at most 200 characters")
    private String line1;

    @Size(max = 200, message = "line2 must be at most 200 characters")
    private String line2;

    @NotBlank(message = "city is required")
    private String city;

    @NotBlank(message = "state is required")
    private String state;

    @NotBlank(message = "pincode is required")
    @Pattern(regexp = "\\d{4,10}", message = "pincode must be 4-10 digits")
    private String pincode;

    @NotBlank(message = "phone is required")
    @Pattern(regexp = "[0-9+][0-9 -]{6,19}", message = "phone must be a valid phone number")
    private String phone;

    // Optional; the first address a user adds always becomes the default (see AddressSvc.create).
    private boolean makeDefault = false;

    public AddressRequest() {
        /*
        * Required by Jackson to instantiate this DTO during JSON deserialization.
        * Fields are populated through the setters after construction.
        */
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getLine1() { return line1; }
    public void setLine1(String line1) { this.line1 = line1; }

    public String getLine2() { return line2; }
    public void setLine2(String line2) { this.line2 = line2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isMakeDefault() { return makeDefault; }
    public void setMakeDefault(boolean makeDefault) { this.makeDefault = makeDefault; }
}
