package fr.kalis.gestion.specifique.export;

import fr.kalis.gestion.tarif.Chiffrage;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Specifique;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.util.List;

/** Distrimed Export : livraison de biens à un assujetti d'un autre État membre, exonérée de TVA. */
@ApplicationScoped
@Specifique("EXPORT")
public class ExonerationIntraUe implements RegleTarifaire {

    static final String LIVRAISON_UE = "export_livraison_ue";

    @Override
    public int ordre() {
        return 800;
    }

    @Override
    public String libelle() {
        return "Exonération livraison intracommunautaire";
    }

    @Override
    public List<DefinitionParametre> parametres() {
        return List.of(DefinitionParametre.ouiNon(LIVRAISON_UE, "Client assujetti dans un autre État membre", true));
    }

    @Override
    public void appliquer(Chiffrage c) {
        if (!c.parametreActif(LIVRAISON_UE)) {
            c.tracerSansEffet("Livraison en France : TVA française appliquée.");
            return;
        }
        c.exonererTva();
        c.ajouterMention("Exonération de TVA, article 262 ter, I du CGI (livraison intracommunautaire).");
        c.tracer(BigDecimal.ZERO, "TVA à 0 et mention d'exonération portée sur la pièce.");
    }
}
