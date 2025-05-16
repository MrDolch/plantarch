package playground.erm.jpa;

import javax.persistence.*;
import java.util.List;

@Entity
public class Car implements Vehicle {
    @Column
    private String name;
    @OneToOne
    private Engine engine;
    @ManyToMany
    private List<Driver> drivers;
    @ManyToOne
    private Owner owner;
    @OneToMany(orphanRemoval = true)
    private List<Seat> seats;

    public Sex getSex() {
        return owner.sex;
    }

    public void setFuel(Fuel fuel) {
        engine.fuel = fuel;
    }
}
