package playground.erm.jpa;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.List;

@Entity
public class Owner extends Person {
    @OneToMany
    private List<Car> cars;
}
