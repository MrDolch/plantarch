package playground.erm.jpa;

import javax.persistence.Column;

public class Person extends Somebody {
    @Column
    private String name;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
