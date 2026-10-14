package fr.kalis.gestion.specifique.vauban;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Ateliers Vauban : escompte proposé pour règlement sous 10 jours, porté en mention. */
@ApplicationScoped
@Specifique("VAUBAN")
public class EscompteReglementRapide implements RegleTarifaire {

    static final String TAUX = "vauban_escompte_pct";

    @Override
    public int ordre() {
        return 800;
    }

    @Override
    public String libelle() {
        return "Escompte règlement à 10 jours";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(TAUX, "Escompte pour règlement sous 10 jours", "%", "2", "0", "5", "0.5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal taux = c.parametre(TAUX);
        BigDecimal ht = c.totalHt();
        if (taux.signum() == 0 || ht.signum() == 0) {
            c.ajouterMention("Pas d'escompte pour règlement anticipé.");
            c.tracerSansEffet("Escompte désactivé.");
            return;
        }
        BigDecimal montant = Chiffrage.pourcentage(ht, taux);
        c.ajouterMention("Escompte de " + Montants.taux(taux) + " pour règlement sous 10 jours, soit "
                + Montants.euros(montant) + " HT.");
        c.tracer(BigDecimal.ZERO, "Escompte de " + Montants.euros(montant) + " HT proposé, sans effet sur le total facturé.");
    }
}
