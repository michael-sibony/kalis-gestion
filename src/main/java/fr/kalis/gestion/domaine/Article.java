package fr.kalis.gestion.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Entity
@Table(name = "ARTICLE", uniqueConstraints = @UniqueConstraint(columnNames = {"instanceCode", "code"}))
@NamedQuery(name = Article.PAR_INSTANCE,
        query = "select a from Article a where a.instanceCode = :instance order by a.famille, a.code")
public class Article {

    public static final String PAR_INSTANCE = "Article.parInstance";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 16)
    private String instanceCode;

    @NotBlank
    @Size(max = 24)
    @Column(nullable = false, length = 24)
    private String code;

    @NotBlank
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String libelle;

    @NotBlank
    @Column(nullable = false, length = 24)
    private String famille;

    @NotBlank
    @Column(nullable = false, length = 16)
    private String unite;

    @DecimalMin(value = "0.01")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal prixHt;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tauxTva;

    @Column(nullable = false, length = 60)
    private String origine;

    protected Article() {
    }

    public Article(String instanceCode, String code, String libelle, String famille, String unite,
                   BigDecimal prixHt, BigDecimal tauxTva, String origine) {
        this.instanceCode = instanceCode;
        this.code = code;
        this.libelle = libelle;
        this.famille = famille;
        this.unite = unite;
        this.prixHt = prixHt;
        this.tauxTva = tauxTva;
        this.origine = origine;
    }

    public void reprendre(String libelle, String famille, String unite, BigDecimal prixHt, BigDecimal tauxTva,
                          String origine) {
        this.libelle = libelle;
        this.famille = famille;
        this.unite = unite;
        this.prixHt = prixHt;
        this.tauxTva = tauxTva;
        this.origine = origine;
    }

    public Long getId() { return id; }
    public String getInstanceCode() { return instanceCode; }
    public String getCode() { return code; }
    public String getLibelle() { return libelle; }
    public String getFamille() { return famille; }
    public String getUnite() { return unite; }
    public BigDecimal getPrixHt() { return prixHt; }
    public BigDecimal getTauxTva() { return tauxTva; }
    public String getOrigine() { return origine; }
}
