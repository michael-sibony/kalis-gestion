package fr.kalis.gestion.specifique.vauban;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Ateliers Vauban : forfait de lancement sous un minimum de commande. */
@ApplicationScoped
@Specifique("VAUBAN")
public class FraisPetiteCommande implements RegleTarifaire {

    static final String MINIMUM = "vauban_minimum_commande";
    static final String FRAIS = "vauban_frais_petite_commande";
    static final BigDecimal TVA_NORMALE = new BigDecimal("20");

    @Override
    public int ordre() {
        return 500;
    }

    @Override
    public String libelle() {
        return "Frais de petite commande";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(
                DefinitionParametre.of(MINIMUM, "Minimum de commande", "€ HT", "400", "0", "3000", "50"),
                DefinitionParametre.of(FRAIS, "Forfait de lancement sous le minimum", "€ HT", "35", "0", "200", "5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal ht = c.totalHt();
        BigDecimal minimum = c.parametre(MINIMUM);
        BigDecimal frais = c.parametre(FRAIS);
        if (ht.signum() == 0) {
            c.tracerSansEffet("Pièce vide.");
        } else if (ht.compareTo(minimum) >= 0) {
            c.tracerSansEffet("Minimum atteint : " + Montants.euros(ht) + " HT pour " + Montants.euros(minimum) + ".");
        } else if (frais.signum() == 0) {
            c.tracerSansEffet("Sous le minimum, mais forfait réglé à 0.");
        } else {
            c.ajouterAjustement("Forfait de lancement petite série", frais, TVA_NORMALE);
            c.tracer(frais, "Commande à " + Montants.euros(ht) + " HT, sous le minimum de " + Montants.euros(minimum) + ".");
        }
    }
}
