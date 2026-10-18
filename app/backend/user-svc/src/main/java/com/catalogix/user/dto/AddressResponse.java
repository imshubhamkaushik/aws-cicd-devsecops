package com.catalogix.user.dto;

import com.catalogix.user.model.Address;

public class AddressResponse {

    private Long id;
    private String label;
    private String line1;
    private String line2;
    private String city;
    private String state;
    private String pincode;
    private String phone;
    private boolean isDefault;

    public AddressResponse() {
        /*
        * Required by Jackson to instantiate this DTO during JSON deserialization.
        * Fields are populated through the setters after construction.
        */
    }

    public static AddressResponse from(Address a) {
        AddressResponse r = new AddressResponse();
        r.id = a.getId();
        r.label = a.getLabel();
        r.line1 = a.getLine1();
        r.line2 = a.getLine2();
        r.city = a.getCity();
        r.state = a.getState();
        r.pincode = a.getPincode();
        r.phone = a.getPhone();
        r.isDefault = a.isDefault();
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
}
