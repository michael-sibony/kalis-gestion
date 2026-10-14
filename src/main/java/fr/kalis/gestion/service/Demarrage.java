package fr.kalis.gestion.service;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

import java.util.logging.Logger;

/** Charge l'environnement de recette au déploiement de l'application. */
@Singleton
@Startup
public class Demarrage {

    private static final Logger LOG = Logger.getLogger(Demarrage.class.getName());

    @Inject
    GestionInstances gestion;

    @Inject
    CatalogueRegles catalogue;

    @PostConstruct
    void demarrer() {
        gestion.initialiser();
        LOG.info(() -> "Kalis prêt : " + catalogue.moteur().toutesLesRegles().size() + " règles chargées.");
    }
}
