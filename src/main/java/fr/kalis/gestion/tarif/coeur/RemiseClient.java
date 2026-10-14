package fr.kalis.gestion.tarif.coeur;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.RegleTarifaire;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Remise contractuelle du client, appliquée en cascade après les remises de ligne. */
@ApplicationScoped
public class RemiseClient implements RegleTarifaire {

    static final String CLE = "remise_client_pct";

    @Override
    public int ordre() {
        return 200;
    }

    @Override
    public String libelle() {
        return "Remise client négociée";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.of(CLE, "Remise client négociée", "%", "0", "0", "20", "0.5"));
    }

    @Override
    public void appliquer(Chiffrage c) {
        BigDecimal taux = c.parametre(CLE);
        if (taux.signum() == 0) {
            c.tracerSansEffet("Aucune remise contractuelle pour ce client.");
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (Ligne l : c.lignes()) {
            if (l.quantite() == 0) {
                continue;
            }
            BigDecimal r = Chiffrage.pourcentage(l.montantNetHt(), taux);
            l.ajouterRemise(r);
            total = total.add(r);
            n++;
        }
        if (n == 0) {
            c.tracerSansEffet("Pièce vide.");
            return;
        }
        c.tracer(total.negate(), "Remise de " + Montants.taux(taux) + " en cascade sur "
                + Montants.pluriel(n, "ligne", "lignes") + ".");
    }
}
