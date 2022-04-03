package playground.erm.jpa;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import java.util.List;

@Entity
public class Car {
  @Column private String name;
  @Column private Driver driver;
  @JoinColumn private List<Seat> seats;

  public String getName() {
    return name;
  }

  public Driver getDriver() {
    return driver;
  }

  public void setDriver(final Driver driver) {
    this.driver = driver;
  }
}
