package fr.kalis.gestion.domaine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un client de l'éditeur, avec son paramétrage et ses spécifiques. */
@Entity
@Table(name = "INSTANCE_CLIENT")
public class InstanceClient {

    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false, length = 80)
    private String raisonSociale;

    @Column(nullable = false, length = 60)
    private String ville;

    @Column(nullable = false, length = 80)
    private String secteur;

    @Column(nullable = false, length = 40)
    private String miseEnService;

    protected InstanceClient() {
    }

    public InstanceClient(String code, String raisonSociale, String ville, String secteur, String miseEnService) {
        this.code = code;
        this.raisonSociale = raisonSociale;
        this.ville = ville;
        this.secteur = secteur;
        this.miseEnService = miseEnService;
    }

    public String getCode() { return code; }
    public String getRaisonSociale() { return raisonSociale; }
    public String getVille() { return ville; }
    public String getSecteur() { return secteur; }
    public String getMiseEnService() { return miseEnService; }
}
