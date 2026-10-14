package fr.kalis.gestion.tarif.coeur;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.RegleTarifaire;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

/** Mentions légales portées sur toute pièce de vente entre professionnels. */
@ApplicationScoped
public class MentionsObligatoires implements RegleTarifaire {

    @Override
    public int ordre() {
        return 990;
    }

    @Override
    public String libelle() {
        return "Mentions obligatoires";
    }

    @Override
    public void appliquer(Chiffrage c) {
        c.ajouterMention("Indemnité forfaitaire pour frais de recouvrement en cas de retard de paiement : 40 € "
                + "(article D441-5 du Code de commerce).");
        c.tracer(BigDecimal.ZERO, "Mentions de paiement ajoutées à la pièce.");
    }
}
