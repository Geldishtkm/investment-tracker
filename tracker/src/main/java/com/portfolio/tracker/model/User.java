package com.portfolio.tracker.model;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;

@Entity
@Table(name = "users") // Avoid conflict with reserved "user" keyword
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(length = 20)
    private String role = "USER"; // Default role

    // One user can have many assets
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore // Prevent circular reference in JSON serialization
    private List<Asset> assets = new ArrayList<>();

    // Constructors
    public User() {
        this.assets = new ArrayList<>();
        this.role = "USER";
    }

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.role = "USER";
        this.assets = new ArrayList<>();
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role != null ? role : "USER";
        this.assets = new ArrayList<>();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role != null ? role : "USER";
    }

    public List<Asset> getAssets() {
        return assets;
    }

    public void setAssets(List<Asset> assets) {
        this.assets = assets != null ? assets : new ArrayList<>();
    }

    // Helper methods
    public void addAsset(Asset asset) {
        if (asset != null) {
            asset.setUser(this);
            this.assets.add(asset);
        }
    }

    public void removeAsset(Asset asset) {
        if (asset != null) {
            this.assets.remove(asset);
            asset.setUser(null);
        }
    }

    public boolean hasAssets() {
        return assets != null && !assets.isEmpty();
    }

    public int getAssetCount() {
        return assets != null ? assets.size() : 0;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", assetCount=" + getAssetCount() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) && Objects.equals(username, user.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, username);
    }
}
