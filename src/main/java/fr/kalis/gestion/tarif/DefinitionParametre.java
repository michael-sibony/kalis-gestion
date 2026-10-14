package fr.kalis.gestion.tarif;

import java.math.BigDecimal;

/**
 * Paramètre réglable d'une règle, avec ses bornes.
 * Une unité "oui/non" se règle à 0 ou 1.
 */
public record DefinitionParametre(
        String cle,
        String libelle,
        String unite,
        BigDecimal defaut,
        BigDecimal min,
        BigDecimal max,
        BigDecimal pas) {

    public static DefinitionParametre of(String cle, String libelle, String unite,
                                         String defaut, String min, String max, String pas) {
        return new DefinitionParametre(cle, libelle, unite,
                new BigDecimal(defaut), new BigDecimal(min), new BigDecimal(max), new BigDecimal(pas));
    }

    public static DefinitionParametre ouiNon(String cle, String libelle, boolean defaut) {
        return of(cle, libelle, "oui/non", defaut ? "1" : "0", "0", "1", "1");
    }

    /** Vérifie qu'une valeur saisie respecte les bornes et le pas. */
    public void verifier(BigDecimal valeur) {
        if (valeur == null) {
            throw new IllegalArgumentException("Valeur manquante pour « " + libelle + " ».");
        }
        if (valeur.compareTo(min) < 0 || valeur.compareTo(max) > 0) {
            throw new IllegalArgumentException("« " + libelle + " » doit être compris entre "
                    + min.toPlainString() + " et " + max.toPlainString() + ".");
        }
        BigDecimal reste = valeur.subtract(min).remainder(pas);
        if (reste.signum() != 0) {
            throw new IllegalArgumentException("« " + libelle + " » se règle par pas de "
                    + pas.toPlainString() + ".");
        }
    }
}
