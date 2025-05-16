package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
public class Engine {
    @Column
    public String power;
    @OneToOne
    private Car car;
    public Fuel fuel;
}
