package fr.kalis.gestion.tarif;

import java.math.BigDecimal;

/** Passage d'une règle dans le moteur : ce qu'elle a fait et combien ça a pesé sur le HT. */
public record Trace(int ordre, String regle, String classe, String portee, boolean declenchee,
                    BigDecimal effetHt, String detail) {
}
