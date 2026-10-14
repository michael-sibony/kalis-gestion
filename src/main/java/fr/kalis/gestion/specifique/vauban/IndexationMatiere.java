package fr.kalis.gestion.specifique.vauban;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Ateliers Vauban : prix des articles matière indexés sur le cours du cuivre. */
@ApplicationScoped
@Specifique("VAUBAN")
public class IndexationMatiere implements RegleTarifaire {

    static final String INDICE = "vauban_indice_cuivre_pct";
    static final String FAMILLE = "MATIERE";

    @Override
    public int ordre() {
        return 100;
    }

    @Override
    public String libelle() {
        return "Indexation matière cuivre";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(INDICE, "Variation de l'indice cuivre", "%", "3.5", "-10", "25", "0.5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal indice = c.parametre(INDICE);
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (Ligne l : c.lignes()) {
            if (FAMILLE.equals(l.famille()) && l.quantite() > 0) {
                BigDecimal m = Chiffrage.pourcentage(l.montantBrutHt(), indice);
                l.ajouterMajoration(m);
                total = total.add(m);
                n++;
            }
        }
        if (n == 0) {
            c.tracerSansEffet("Aucun article matière dans la pièce.");
        } else if (indice.signum() == 0) {
            c.tracerSansEffet("Indice cuivre stable : aucune variation appliquée.");
        } else {
            String signe = indice.signum() > 0 ? "+" : "";
            c.tracer(total, "Indice cuivre " + signe + Montants.taux(indice) + " sur "
                    + Montants.pluriel(n, "ligne matière", "lignes matière") + ".");
        }
    }
}
