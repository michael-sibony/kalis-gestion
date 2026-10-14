package fr.kalis.gestion.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/** Valeur réglée par l'intégrateur pour un paramètre de règle, sur une instance. */
@Entity
@Table(name = "VALEUR_PARAMETRE", uniqueConstraints = @UniqueConstraint(columnNames = {"instanceCode", "cle"}))
@NamedQuery(name = ValeurParametre.PAR_INSTANCE,
        query = "select v from ValeurParametre v where v.instanceCode = :instance")
public class ValeurParametre {

    public static final String PAR_INSTANCE = "ValeurParametre.parInstance";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String instanceCode;

    @Column(nullable = false, length = 48)
    private String cle;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal valeur;

    @Column(nullable = false)
    private Instant modifieLe;

    @Version
    private int version;

    protected ValeurParametre() {
    }

    public ValeurParametre(String instanceCode, String cle, BigDecimal valeur) {
        this.instanceCode = instanceCode;
        this.cle = cle;
        this.valeur = valeur;
        this.modifieLe = Instant.now();
    }

    public void modifier(BigDecimal valeur) {
        this.valeur = valeur;
        this.modifieLe = Instant.now();
    }

    public String getInstanceCode() { return instanceCode; }
    public String getCle() { return cle; }
    public BigDecimal getValeur() { return valeur; }
    public Instant getModifieLe() { return modifieLe; }
}
