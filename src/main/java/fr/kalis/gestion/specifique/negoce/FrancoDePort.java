package fr.kalis.gestion.specifique.negoce;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Négoce Atlantique : frais de port facturés sous le seuil de franco. */
@ApplicationScoped
@Specifique("NEGOCE")
public class FrancoDePort implements RegleTarifaire {

    static final String SEUIL = "negoce_seuil_franco";
    static final String FRAIS = "negoce_frais_port";
    static final BigDecimal TVA_NORMALE = new BigDecimal("20");

    @Override
    public int ordre() {
        return 500;
    }

    @Override
    public String libelle() {
        return "Franco de port";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(
                DefinitionParametre.of(SEUIL, "Seuil de franco", "€ HT", "1500", "0", "10000", "50"),
                DefinitionParametre.of(FRAIS, "Frais de port sous le seuil", "€ HT", "45", "0", "300", "5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal ht = c.totalHt();
        BigDecimal seuil = c.parametre(SEUIL);
        BigDecimal frais = c.parametre(FRAIS);
        if (ht.signum() == 0) {
            c.tracerSansEffet("Pièce vide.");
        } else if (ht.compareTo(seuil) >= 0) {
            c.tracerSansEffet("Franco atteint : " + Montants.euros(ht) + " HT pour un seuil de " + Montants.euros(seuil) + ".");
        } else if (frais.signum() == 0) {
            c.tracerSansEffet("Sous le franco, mais frais de port réglés à 0.");
        } else {
            c.ajouterAjustement("Frais de port", frais, TVA_NORMALE);
            c.tracer(frais, "Commande à " + Montants.euros(ht) + " HT, sous le franco de " + Montants.euros(seuil) + ".");
        }
    }
}
