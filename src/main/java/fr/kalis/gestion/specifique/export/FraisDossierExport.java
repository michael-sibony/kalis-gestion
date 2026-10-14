package fr.kalis.gestion.specifique.export;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Distrimed Export : forfait de constitution du dossier d'expédition. */
@ApplicationScoped
@Specifique("EXPORT")
public class FraisDossierExport implements RegleTarifaire {

    static final String FRAIS = "export_frais_dossier";
    static final BigDecimal TVA_NORMALE = new BigDecimal("20");

    @Override
    public int ordre() {
        return 500;
    }

    @Override
    public String libelle() {
        return "Frais de dossier export";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(FRAIS, "Forfait dossier d'expédition", "€ HT", "25", "0", "150", "5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal frais = c.parametre(FRAIS);
        if (c.totalHt().signum() == 0 || frais.signum() == 0) {
            c.tracerSansEffet("Aucun forfait dossier.");
            return;
        }
        c.ajouterAjustement("Dossier d'expédition", frais, TVA_NORMALE);
        c.tracer(frais, "Forfait de " + Montants.euros(frais) + " HT par expédition.");
    }
}
