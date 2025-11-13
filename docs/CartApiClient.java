// CartApiClient.java - Android Java implementation for POS Cart API
package com.candykush.pos.api;

import okhttp3.*;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CartApiClient {
    private static final String BASE_URL = "https://pos-candy-kush.vercel.app/api";
    private final OkHttpClient client;
    private final Gson gson;

    public CartApiClient() {
        this.client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
        this.gson = new Gson();
    }

    // Get current cart contents
    public Cart getCart() throws IOException {
        Request request = new Request.Builder()
            .url(BASE_URL + "/cart")
            .get()
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String json = response.body().string();
                CartResponse cartResponse = gson.fromJson(json, CartResponse.class);
                if (cartResponse.isSuccess()) {
                    return cartResponse.getCart();
                }
            }
        }
        return null;
    }

    // Get payment status
    public PaymentStatus getPaymentStatus() throws IOException {
        Request request = new Request.Builder()
            .url(BASE_URL + "/cart/payment")
            .get()
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String json = response.body().string();
                PaymentResponse paymentResponse = gson.fromJson(json, PaymentResponse.class);
                if (paymentResponse.isSuccess()) {
                    return paymentResponse.getPaymentStatus();
                }
            }
        }
        return null;
    }

    // Data classes (same structure as Kotlin version)
    public static class CartItem {
        private String id;
        private String productId;
        private String name;
        private int quantity;
        private double price;
        private double total;
        private Double weight;
        private String unit;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        // ... add other getters/setters
    }

    public static class Customer {
        private String id;
        private String name;
        private String phone;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        // ... add other getters/setters
    }

    public static class Cart {
        private java.util.List<CartItem> items;
        private Discount discount;
        private Tax tax;
        private Customer customer;
        private String notes;
        private double total;
        private String lastUpdated;

        // Getters and setters
        public java.util.List<CartItem> getItems() { return items; }
        public void setItems(java.util.List<CartItem> items) { this.items = items; }
        public double getTotal() { return total; }
        public void setTotal(double total) { this.total = total; }
        public Customer getCustomer() { return customer; }
        public void setCustomer(Customer customer) { this.customer = customer; }
        // ... add other getters/setters
    }

    public static class PaymentStatus {
        private String status;
        private String timestamp;
        private double amount;
        private String method;
        private String transactionId;

        // Getters and setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        // ... add other getters/setters
    }

    // Response wrapper classes
    public static class CartResponse {
        private boolean success;
        private Cart cart;
        private String timestamp;

        public boolean isSuccess() { return success; }
        public Cart getCart() { return cart; }
    }

    public static class PaymentResponse {
        private boolean success;
        private PaymentStatus paymentStatus;

        public boolean isSuccess() { return success; }
        public PaymentStatus getPaymentStatus() { return paymentStatus; }
    }

    // Supporting classes
    public static class Discount {
        private String type;
        private double value;
        // getters/setters
    }

    public static class Tax {
        private double rate;
        private double amount;
        // getters/setters
    }
}