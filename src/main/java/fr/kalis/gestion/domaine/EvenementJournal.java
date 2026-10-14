package fr.kalis.gestion.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;

import java.time.Instant;

/** Trace d'une opération de mise en oeuvre : paramétrage, reprise, réinitialisation. */
@Entity
@Table(name = "JOURNAL")
@NamedQuery(name = EvenementJournal.PAR_INSTANCE,
        query = "select e from EvenementJournal e where e.instanceCode = :instance order by e.horodatage desc, e.id desc")
public class EvenementJournal {

    public static final String PAR_INSTANCE = "EvenementJournal.parInstance";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String instanceCode;

    @Column(nullable = false)
    private Instant horodatage;

    @Column(nullable = false, length = 24)
    private String nature;

    @Column(nullable = false, length = 400)
    private String message;

    protected EvenementJournal() {
    }

    public EvenementJournal(String instanceCode, String nature, String message) {
        this.instanceCode = instanceCode;
        this.horodatage = Instant.now();
        this.nature = nature;
        this.message = message.length() > 400 ? message.substring(0, 397) + "..." : message;
    }

    public Long getId() { return id; }
    public String getInstanceCode() { return instanceCode; }
    public Instant getHorodatage() { return horodatage; }
    public String getNature() { return nature; }
    public String getMessage() { return message; }
}
