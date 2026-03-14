package com.example.banking.model;

/**
 * In-memory account representation. Not a @Model — this is internal data.
 */
public class Account {

    private String id;
    private String holderName;
    private String email;
    private String phone;
    private double balance;

    public Account() {}

    public Account(String id, String holderName, String email, String phone, double balance) {
        this.id = id;
        this.holderName = holderName;
        this.email = email;
        this.phone = phone;
        this.balance = balance;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
