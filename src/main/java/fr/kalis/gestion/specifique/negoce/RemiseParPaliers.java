package fr.kalis.gestion.specifique.negoce;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Négoce Atlantique : remise par paliers de quantité, ligne par ligne. */
@ApplicationScoped
@Specifique("NEGOCE")
public class RemiseParPaliers implements RegleTarifaire {

    static final String SEUIL_1 = "negoce_palier1_qte";
    static final String TAUX_1 = "negoce_palier1_pct";
    static final String SEUIL_2 = "negoce_palier2_qte";
    static final String TAUX_2 = "negoce_palier2_pct";

    @Override
    public int ordre() {
        return 100;
    }

    @Override
    public String libelle() {
        return "Remise par paliers de quantité";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(
                DefinitionParametre.of(SEUIL_1, "Seuil du palier 1", "unités", "50", "1", "1000", "1"),
                DefinitionParametre.of(TAUX_1, "Remise au palier 1", "%", "5", "0", "20", "0.5"),
                DefinitionParametre.of(SEUIL_2, "Seuil du palier 2", "unités", "200", "1", "2000", "1"),
                DefinitionParametre.of(TAUX_2, "Remise au palier 2", "%", "10", "0", "30", "0.5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        int seuil1 = c.parametreEntier(SEUIL_1);
        int seuil2 = c.parametreEntier(SEUIL_2);
        BigDecimal taux1 = c.parametre(TAUX_1);
        BigDecimal taux2 = c.parametre(TAUX_2);
        BigDecimal total = BigDecimal.ZERO;
        int auPalier1 = 0;
        int auPalier2 = 0;
        for (Ligne l : c.lignes()) {
            int q = l.quantite();
            BigDecimal taux;
            if (q >= seuil2 && seuil2 >= seuil1) {
                taux = taux2;
                auPalier2++;
            } else if (q >= seuil1) {
                taux = taux1;
                auPalier1++;
            } else {
                continue;
            }
            BigDecimal r = Chiffrage.pourcentage(l.montantBrutHt(), taux);
            l.ajouterRemise(r);
            total = total.add(r);
        }
        if (auPalier1 + auPalier2 == 0) {
            c.tracerSansEffet("Aucune ligne n'atteint " + seuil1 + " unités.");
            return;
        }
        List<String> parts = new ArrayList<>();
        if (auPalier1 > 0) {
            parts.add(Montants.pluriel(auPalier1, "ligne", "lignes") + " au palier 1 (" + Montants.taux(taux1) + ")");
        }
        if (auPalier2 > 0) {
            parts.add(Montants.pluriel(auPalier2, "ligne", "lignes") + " au palier 2 (" + Montants.taux(taux2) + ")");
        }
        c.tracer(total.negate(), String.join(", ", parts) + ".");
    }
}
