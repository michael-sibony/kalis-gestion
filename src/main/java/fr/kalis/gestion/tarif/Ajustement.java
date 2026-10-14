package fr.kalis.gestion.tarif;

import java.math.BigDecimal;

/** Ligne ajoutée par une règle (frais de port, frais de dossier...). Montant HT signé. */
public record Ajustement(String libelle, BigDecimal montantHt, BigDecimal tauxTva, String source) {
}
