package fr.kalis.gestion.tarif.coeur;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Garde-fou du coeur : quelles que soient les remises ajoutées par les spécifiques,
 * leur cumul ne dépasse pas un pourcentage du brut. Protège la marge.
 */
@ApplicationScoped
public class PlafondRemise implements RegleTarifaire {

    static final String CLE = "remise_max_pct";

    @Override
    public int ordre() {
        return 600;
    }

    @Override
    public String libelle() {
        return "Plafond des remises cumulées";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(CLE, "Plafond des remises cumulées", "% du brut", "15", "0", "40", "1"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal brut = c.totalBrutHt();
        BigDecimal remises = c.totalRemises();
        BigDecimal max = c.parametre(CLE);
        BigDecimal plafond = Chiffrage.pourcentage(brut, max);
        if (brut.signum() == 0 || remises.compareTo(plafond) <= 0) {
            String part = brut.signum() == 0 ? "0"
                    : Montants.nombre(remises.multiply(Chiffrage.CENT).divide(brut, 1, RoundingMode.HALF_UP));
            c.tracerSansEffet("Remises cumulées à " + part + " % du brut, sous le plafond de " + Montants.taux(max) + ".");
            return;
        }
        BigDecimal facteur = plafond.divide(remises, MathContext.DECIMAL64);
        BigDecimal reparti = BigDecimal.ZERO;
        for (Ligne l : c.lignes()) {
            if (l.remise().signum() > 0) {
                l.fixerRemise(l.remise().multiply(facteur).setScale(2, RoundingMode.DOWN));
                reparti = reparti.add(l.remise());
            }
        }
        // Les centimes perdus à l'arrondi vont sur la ligne la plus remisée : le plafond tombe juste.
        BigDecimal reste = plafond.subtract(reparti);
        if (reste.signum() != 0) {
            Ligne principale = c.lignes().stream().max(Comparator.comparing(Ligne::remise)).orElseThrow();
            principale.ajouterRemise(reste);
        }
        c.tracer(remises.subtract(plafond), "Remises ramenées de " + Montants.euros(remises) + " à "
                + Montants.euros(plafond) + " (" + Montants.taux(max) + " du brut).");
    }
}
