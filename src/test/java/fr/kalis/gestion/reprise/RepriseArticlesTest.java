package fr.kalis.gestion.reprise;

import fr.kalis.gestion.reprise.RepriseArticles.LigneReprise;
import fr.kalis.gestion.reprise.RepriseArticles.Rapport;
import fr.kalis.gestion.reprise.RepriseArticles.Statut;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepriseArticlesTest {

    private static Rapport analyser(String csv) {
        return RepriseArticles.analyser(csv, Set.of("PLQ-BA13"));
    }

    @Test
    void ignoreLEnTeteEtLesLignesVides() {
        Rapport r = analyser("code;libellé;famille;unité;prix;tva\n\nRAIL-M70;Rail M70;OSSATURE;u;5,40;20\n");
        assertEquals(1, r.lignes().size());
        assertEquals(3, r.lignes().get(0).numero());
        assertEquals(Statut.CREATION, r.lignes().get(0).statut());
    }

    @Test
    void unCodeExistantDevientUneMiseAJour() {
        Rapport r = analyser("PLQ-BA13;Plaque BA13;PLAQUE;u;9,10;20");
        assertEquals(Statut.MISE_A_JOUR, r.lignes().get(0).statut());
        assertEquals(1, r.misesAJour());
    }

    @Test
    void litLesMontantsALaFrancaise() {
        LigneReprise l = analyser("ISO-200;Isolant 200;ISOLANT;u;1 234,50 €;5,5").lignes().get(0);
        assertEquals(new BigDecimal("1234.50"), l.prixHt());
        assertEquals(Statut.CREATION, l.statut());
    }

    @Test
    void litLesDeuxConventionsDeSeparateurs() {
        assertEquals(new BigDecimal("1234.5"), RepriseArticles.decimal("1.234,5"));
        assertEquals(new BigDecimal("1234.5"), RepriseArticles.decimal("1,234.5"));
        assertEquals(new BigDecimal("12.5"), RepriseArticles.decimal("12,5"));
        assertNull(RepriseArticles.decimal("douze"));
    }

    @Test
    void rejetteUnTauxDeTvaInconnu() {
        LigneReprise l = analyser("A-1;Article;X;u;10;19,6").lignes().get(0);
        assertEquals(Statut.REJET, l.statut());
        assertTrue(l.erreurs().get(0).contains("19.6"));
    }

    @Test
    void rejetteUnPrixIllisibleOuNul() {
        assertEquals(Statut.REJET, analyser("A-1;Article;X;u;sur devis;20").lignes().get(0).statut());
        assertEquals(Statut.REJET, analyser("A-1;Article;X;u;0;20").lignes().get(0).statut());
        assertEquals(Statut.REJET, analyser("A-1;Article;X;u;1,234;20").lignes().get(0).statut());
    }

    @Test
    void rejetteLeNombreDeColonnes() {
        LigneReprise l = analyser("A-1;Article;X;u;10").lignes().get(0);
        assertEquals(Statut.REJET, l.statut());
        assertTrue(l.erreurs().get(0).startsWith("5 colonnes"));
    }

    @Test
    void signaleLesDoublonsAvecLaPremiereLigne() {
        Rapport r = analyser("A-1;Un;X;u;10;20\nB-2;Deux;X;u;10;20\na-1;Un bis;X;u;11;20");
        LigneReprise doublon = r.lignes().get(2);
        assertEquals(Statut.REJET, doublon.statut());
        assertTrue(doublon.erreurs().contains("Code en double avec la ligne 1."));
        assertEquals(2, r.creations());
    }

    @Test
    void cumuleLesErreursDUneMemeLigne() {
        LigneReprise l = analyser("x;;;;-3;7").lignes().get(0);
        assertTrue(l.erreurs().size() >= 5, l.erreurs().toString());
    }

    @Test
    void accepteTabulationGuillemetsEtBom() {
        Rapport r = analyser("﻿code\tlibelle\tfamille\tunite\tprix\ttva\n\"VIS-6\"\t\"Vis \"\"inox\"\" 6 mm\"\tFIXATION\tboîte\t12.00\t20 %");
        LigneReprise l = r.lignes().get(0);
        assertEquals(Statut.CREATION, l.statut(), l.erreurs().toString());
        assertEquals("Vis \"inox\" 6 mm", l.libelle());
    }

    @Test
    void unFichierVideNeProduitRien() {
        assertTrue(analyser("").lignes().isEmpty());
        assertTrue(analyser(null).lignes().isEmpty());
    }
}
