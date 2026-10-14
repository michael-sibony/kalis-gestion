package fr.kalis.gestion.tarif;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatage français des montants et des taux pour les traces et les mentions. */
public final class Montants {

    private Montants() {
    }

    public static String euros(BigDecimal montant) {
        return format(montant, "#,##0.00") + " €";
    }

    public static String taux(BigDecimal taux) {
        return format(taux, "#,##0.##") + " %";
    }

    public static String nombre(BigDecimal valeur) {
        return format(valeur, "#,##0.##");
    }

    private static String format(BigDecimal valeur, String motif) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        s.setDecimalSeparator(',');
        DecimalFormat f = new DecimalFormat(motif, s);
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(valeur);
    }

    public static String pluriel(int n, String singulier, String pluriel) {
        return n + " " + (n > 1 ? pluriel : singulier);
    }
}
