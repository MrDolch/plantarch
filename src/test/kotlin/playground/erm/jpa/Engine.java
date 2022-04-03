package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToOne;

@Entity
public class Engine {
    @Column
    private String power;
    @OneToOne
    private Car car;

}
