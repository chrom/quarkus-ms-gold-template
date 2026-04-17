package org.acme.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

/** Example of a Panache entity (active record). For custom ID type — {@link PanacheEntity} → PanacheEntityBase. */
@Entity
public class MyEntity extends PanacheEntity {
    public String field;
}
