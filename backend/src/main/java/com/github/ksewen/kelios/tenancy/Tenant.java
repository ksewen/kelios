package com.github.ksewen.kelios.tenancy;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tenant")
public class Tenant {
    protected Tenant() {}
    @Id
    @Column(name = "id")
    UUID id;
    @Column(name = "name", columnDefinition = "text")
    String name;
    public UUID id() { return id; }
    public String name() { return name; }
}
