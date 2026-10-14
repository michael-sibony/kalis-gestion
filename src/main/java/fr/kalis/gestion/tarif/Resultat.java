package fr.kalis.gestion.tarif;

import java.math.BigDecimal;
import java.util.List;

/** Photo figée d'une pièce chiffrée, prête à être sérialisée en JSON. */
public record Resultat(
        String instance,
        List<LigneChiffree> lignes,
        List<Ajustement> ajustements,
        List<Trace> traces,
        List<String> mentions,
        BigDecimal totalBrutHt,
        BigDecimal totalRemises,
        BigDecimal totalMajorations,
        BigDecimal totalHt,
        List<Tva> tva,
        BigDecimal totalTva,
        BigDecimal totalTtc,
        boolean exonereTva) {

    public record LigneChiffree(String code, String libelle, String unite, int quantiteDemandee, int quantite,
                                BigDecimal prixUnitaireHt, BigDecimal montantBrutHt, BigDecimal remise,
                                BigDecimal majoration, BigDecimal montantNetHt, BigDecimal tauxTva) {
    }

    public record Tva(BigDecimal taux, BigDecimal base, BigDecimal montant) {
    }
}
