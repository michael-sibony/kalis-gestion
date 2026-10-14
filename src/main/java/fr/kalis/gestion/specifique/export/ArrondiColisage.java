package fr.kalis.gestion.specifique.export;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Distrimed Export : les quantités sont portées au carton complet supérieur. */
@ApplicationScoped
@Specifique("EXPORT")
public class ArrondiColisage implements RegleTarifaire {

    static final String COLISAGE = "export_colisage";

    @Override
    public int ordre() {
        return 50;
    }

    @Override
    public String libelle() {
        return "Arrondi au carton complet";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(COLISAGE, "Unités par carton d'expédition", "unités", "10", "1", "100", "1"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        int colisage = c.parametreEntier(COLISAGE);
        BigDecimal avant = c.totalBrutHt();
        List<String> changements = new ArrayList<>();
        for (Ligne l : c.lignes()) {
            int q = l.quantite();
            if (q > 0 && q % colisage != 0) {
                int arrondi = ((q / colisage) + 1) * colisage;
                l.fixerQuantite(arrondi);
                changements.add(l.code() + " " + q + " → " + arrondi);
            }
        }
        if (changements.isEmpty()) {
            c.tracerSansEffet("Toutes les quantités tombent sur des cartons de " + colisage + ".");
            return;
        }
        c.tracer(c.totalBrutHt().subtract(avant), "Cartons de " + colisage + " : " + String.join(", ", changements) + ".");
    }
}
