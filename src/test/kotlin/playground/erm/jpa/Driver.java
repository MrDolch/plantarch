package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity
public class Driver {
  @Column private String name;

  public String getName() {
    return name;
  }
}
