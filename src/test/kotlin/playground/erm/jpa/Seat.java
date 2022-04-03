package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;

@Entity
public class Seat {
  @Column private String name;
}
