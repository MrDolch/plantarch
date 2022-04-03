package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

@Entity
public class Seat {
    @Column
    private String name;
    @ManyToOne
    @JoinColumn
    private Car car;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Car getCar() {
        return car;
    }

    public void setCar(final Car car) {
        this.car = car;
    }
}
