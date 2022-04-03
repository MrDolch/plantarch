package playground.erm.jpa;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import java.util.List;

@Entity
public class Driver extends Person {
    @ManyToMany
    private List<Car> cars;
}
