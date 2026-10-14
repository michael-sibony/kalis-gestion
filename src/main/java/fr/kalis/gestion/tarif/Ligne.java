package fr.kalis.gestion.tarif;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ligne de pièce en cours de chiffrage. Les montants sont en euros HT. */
public final class Ligne {

    private final String code;
    private final String libelle;
    private final String famille;
    private final String unite;
    private final int quantiteDemandee;
    private final BigDecimal prixUnitaireHt;
    private final BigDecimal tauxTva;
    private int quantite;
    private BigDecimal remise = BigDecimal.ZERO.setScale(2);
    private BigDecimal majoration = BigDecimal.ZERO.setScale(2);

    public Ligne(String code, String libelle, String famille, String unite,
                 int quantite, BigDecimal prixUnitaireHt, BigDecimal tauxTva) {
        if (quantite < 0) {
            throw new IllegalArgumentException("Quantité négative sur " + code + ".");
        }
        this.code = code;
        this.libelle = libelle;
        this.famille = famille;
        this.unite = unite;
        this.quantiteDemandee = quantite;
        this.quantite = quantite;
        this.prixUnitaireHt = prixUnitaireHt.setScale(2, RoundingMode.HALF_UP);
        this.tauxTva = tauxTva;
    }

    public BigDecimal montantBrutHt() {
        return prixUnitaireHt.multiply(BigDecimal.valueOf(quantite)).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal montantNetHt() {
        return montantBrutHt().subtract(remise).add(majoration);
    }

    public void ajouterRemise(BigDecimal montant) {
        remise = remise.add(montant).setScale(2, RoundingMode.HALF_UP);
    }

    public void fixerRemise(BigDecimal montant) {
        remise = montant.setScale(2, RoundingMode.HALF_UP);
    }

    public void ajouterMajoration(BigDecimal montant) {
        majoration = majoration.add(montant).setScale(2, RoundingMode.HALF_UP);
    }

    public void fixerQuantite(int quantite) {
        if (quantite < 0) {
            throw new IllegalArgumentException("Quantité négative sur " + code + ".");
        }
        this.quantite = quantite;
    }

    public String code() { return code; }
    public String libelle() { return libelle; }
    public String famille() { return famille; }
    public String unite() { return unite; }
    public int quantite() { return quantite; }
    public int quantiteDemandee() { return quantiteDemandee; }
    public BigDecimal prixUnitaireHt() { return prixUnitaireHt; }
    public BigDecimal tauxTva() { return tauxTva; }
    public BigDecimal remise() { return remise; }
    public BigDecimal majoration() { return majoration; }
}
