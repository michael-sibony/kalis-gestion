package fr.kalis.gestion.service;

import fr.kalis.gestion.tarif.MoteurTarification;
import fr.kalis.gestion.tarif.RegleTarifaire;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Rassemble toutes les règles découvertes par CDI, coeur et spécifiques confondus.
 * Ajouter un spécifique client revient à déposer une classe annotée : rien à câbler ici.
 */
@ApplicationScoped
public class CatalogueRegles {

    @Inject
    @Any
    Instance<RegleTarifaire> regles;

    private MoteurTarification moteur;

    @PostConstruct
    void charger() {
        List<RegleTarifaire> liste = new ArrayList<>();
        regles.forEach(liste::add);
        moteur = new MoteurTarification(liste);
    }

    public MoteurTarification moteur() {
        return moteur;
    }
}
