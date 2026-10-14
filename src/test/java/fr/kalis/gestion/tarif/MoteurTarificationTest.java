package fr.kalis.gestion.tarif;

import fr.kalis.gestion.specifique.export.ArrondiColisage;
import fr.kalis.gestion.specifique.export.ExonerationIntraUe;
import fr.kalis.gestion.specifique.export.FraisDossierExport;
import fr.kalis.gestion.specifique.negoce.FrancoDePort;
import fr.kalis.gestion.specifique.negoce.RemiseParPaliers;
import fr.kalis.gestion.specifique.vauban.EscompteReglementRapide;
import fr.kalis.gestion.specifique.vauban.FraisPetiteCommande;
import fr.kalis.gestion.specifique.vauban.IndexationMatiere;
import fr.kalis.gestion.tarif.coeur.MentionsObligatoires;
import fr.kalis.gestion.tarif.coeur.PlafondRemise;
import fr.kalis.gestion.tarif.coeur.RemiseClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoteurTarificationTest {

    private final MoteurTarification moteur = new MoteurTarification(List.of(
            new MentionsObligatoires(), new PlafondRemise(), new RemiseClient(),
            new RemiseParPaliers(), new FrancoDePort(),
            new IndexationMatiere(), new FraisPetiteCommande(), new EscompteReglementRapide(),
            new ArrondiColisage(), new FraisDossierExport(), new ExonerationIntraUe()));

    private static Ligne ligne(String code, String famille, int q, String prix) {
        return new Ligne(code, code, famille, "u", q, new BigDecimal(prix), new BigDecimal("20"));
    }

    private static BigDecimal e(String montant) {
        return new BigDecimal(montant);
    }

    private static Map<String, BigDecimal> reglages(String... cleValeur) {
        java.util.Map<String, BigDecimal> m = new java.util.HashMap<>();
        for (int i = 0; i < cleValeur.length; i += 2) {
            m.put(cleValeur[i], new BigDecimal(cleValeur[i + 1]));
        }
        return m;
    }

    private static List<Ligne> panierNegoce() {
        return List.of(
                ligne("PLQ-BA13", "PLAQUE", 60, "8.90"),
                ligne("RAIL-M48", "OSSATURE", 40, "4.20"),
                ligne("MONT-M48", "OSSATURE", 80, "5.10"),
                ligne("VIS-TTPC25", "FIXATION", 4, "18.50"));
    }

    @Test
    void lesReglesSontExecuteesDansLOrdre() {
        List<Integer> ordres = moteur.reglesPour("NEGOCE", true).stream().map(RegleTarifaire::ordre).toList();
        assertEquals(List.of(100, 200, 500, 600, 990), ordres);
    }

    @Test
    void unSpecifiqueNeFuitPasVersUneAutreInstance() {
        assertTrue(moteur.reglesPour("VAUBAN", true).stream().noneMatch(r -> "NEGOCE".equals(r.instanceCiblee())));
        assertTrue(moteur.reglesPour("INCONNU", true).stream().allMatch(r -> r.instanceCiblee() == null));
    }

    @Test
    void negoceCumulePaliersRemiseClientEtFraisDePort() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages("remise_client_pct", "2"), panierNegoce(), true);
        assertEquals(e("1184.00"), r.totalBrutHt());
        // Paliers : 26,70 + 20,40. Remise client en cascade : 10,15 + 3,36 + 7,75 + 1,48.
        assertEquals(e("69.84"), r.totalRemises());
        assertEquals(1, r.ajustements().size());
        assertEquals("Frais de port", r.ajustements().get(0).libelle());
        assertEquals(e("1159.16"), r.totalHt());
        assertEquals(e("231.83"), r.totalTva());
        assertEquals(e("1390.99"), r.totalTtc());
    }

    @Test
    void leCoeurSeulIgnoreLesSpecifiques() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages("remise_client_pct", "2"), panierNegoce(), false);
        assertEquals(e("23.68"), r.totalRemises());
        assertTrue(r.ajustements().isEmpty());
        assertEquals(e("1160.32"), r.totalHt());
        assertEquals(e("1392.38"), r.totalTtc());
    }

    @Test
    void lePalierDeuxRemplaceLePalierUn() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages(), List.of(ligne("PLQ-BA13", "PLAQUE", 200, "8.90")), true);
        // 1 780,00 brut, 10 % au palier 2, franco atteint.
        assertEquals(e("178.00"), r.totalRemises());
        assertEquals(e("1602.00"), r.totalHt());
        assertTrue(r.ajustements().isEmpty());
    }

    @Test
    void leFrancoSuitLeReglageDuSeuil() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages("negoce_seuil_franco", "1000"), panierNegoce(), true);
        assertTrue(r.ajustements().isEmpty());
        Trace franco = trace(r, "Franco de port");
        assertFalse(franco.declenchee());
    }

    @Test
    void lePlafondRameneLesRemisesCumulees() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages("remise_client_pct", "20"),
                List.of(ligne("PLQ-BA13", "PLAQUE", 250, "8.90")), true);
        // 2 225,00 brut ; 222,50 de palier + 400,50 de remise client = 623,00, plafonnées à 15 % soit 333,75.
        assertEquals(e("333.75"), r.totalRemises());
        assertEquals(e("1891.25"), r.totalHt());
        assertEquals(e("289.25"), trace(r, "Plafond des remises cumulées").effetHt());
    }

    @Test
    void lePlafondTombeAuCentimePresSurPlusieursLignes() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages("remise_client_pct", "20", "remise_max_pct", "7"), List.of(
                ligne("A1", "X", 51, "3.33"), ligne("A2", "X", 77, "9.99"), ligne("A3", "X", 203, "1.07")), true);
        BigDecimal brut = r.totalBrutHt();
        assertEquals(Chiffrage.pourcentage(brut, e("7")), r.totalRemises());
        BigDecimal somme = r.lignes().stream().map(Resultat.LigneChiffree::remise).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(r.totalRemises(), somme);
    }

    @Test
    void vaubanIndexeLaMatiereEtAnnonceLEscompte() {
        Resultat r = moteur.chiffrer("VAUBAN", reglages(), List.of(
                ligne("CBL-CU16", "MATIERE", 25, "6.80"),
                ligne("USN-BRIDE", "USINAGE", 3, "64.00"),
                ligne("PRS-REGLAGE", "SERVICE", 1, "72.00")), true);
        assertEquals(e("5.95"), r.totalMajorations());
        assertEquals(e("439.95"), r.totalHt());
        assertTrue(r.ajustements().isEmpty());
        assertTrue(r.mentions().stream().anyMatch(m -> m.contains("soit 8,80 € HT")));
        assertEquals(e("527.94"), r.totalTtc());
    }

    @Test
    void vaubanFactureLeForfaitSousLeMinimum() {
        Resultat r = moteur.chiffrer("VAUBAN", reglages(), List.of(ligne("CBL-CU16", "MATIERE", 10, "6.80")), true);
        assertEquals(e("105.38"), r.totalHt());
        assertEquals("Forfait de lancement petite série", r.ajustements().get(0).libelle());
    }

    @Test
    void uneBaisseDuCuivreDevientUneDecote() {
        Resultat r = moteur.chiffrer("VAUBAN", reglages("vauban_indice_cuivre_pct", "-4"),
                List.of(ligne("BAR-CU20", "MATIERE", 20, "42.00")), true);
        assertEquals(e("-33.60"), r.totalMajorations());
        assertEquals(e("806.40"), r.totalHt());
    }

    @Test
    void exportArrondiAuCartonEtExonereLaTva() {
        Resultat r = moteur.chiffrer("EXPORT", reglages("remise_client_pct", "3", "remise_max_pct", "12"), List.of(
                ligne("GNT-NIT100", "PROTECTION", 37, "7.90"),
                ligne("MSQ-FFP2", "PROTECTION", 24, "12.40"),
                ligne("SER-5ML", "INJECTION", 10, "15.60")), true);
        assertEquals(40, r.lignes().get(0).quantite());
        assertEquals(37, r.lignes().get(0).quantiteDemandee());
        assertEquals(30, r.lignes().get(1).quantite());
        assertEquals(e("98.10"), trace(r, "Arrondi au carton complet").effetHt());
        assertEquals(e("843.68"), r.totalHt());
        assertTrue(r.exonereTva());
        assertEquals(e("0.00"), r.totalTva());
        assertEquals(r.totalHt(), r.totalTtc());
        assertTrue(r.mentions().stream().anyMatch(m -> m.contains("262 ter")));
    }

    @Test
    void exportEnFranceGardeLaTva() {
        Resultat r = moteur.chiffrer("EXPORT", reglages("export_livraison_ue", "0"),
                List.of(ligne("SER-5ML", "INJECTION", 10, "15.60")), true);
        assertFalse(r.exonereTva());
        // 156,00 + 25,00 de dossier = 181,00 HT, TVA 36,20.
        assertEquals(e("36.20"), r.totalTva());
    }

    @Test
    void uneTraceParRegleExecutee() {
        Resultat r = moteur.chiffrer("NEGOCE", reglages(), panierNegoce(), true);
        assertEquals(5, r.traces().size());
        assertEquals("Spécifique", trace(r, "Franco de port").portee());
        assertEquals("Standard", trace(r, "Plafond des remises cumulées").portee());
    }

    @Test
    void lesTauxDeTvaSontRegroupes() {
        Ligne reduite = new Ligne("LIV", "Livre", "DOC", "u", 2, e("10.00"), e("5.50"));
        Resultat r = moteur.chiffrer("AUTRE", reglages(), List.of(reduite, ligne("A", "X", 1, "100.00")), true);
        assertEquals(2, r.tva().size());
        assertEquals(e("5.5"), r.tva().get(0).taux());
        assertEquals(e("1.10"), r.tva().get(0).montant());
        assertEquals(e("20.00"), r.tva().get(1).montant());
    }

    @Test
    void lesParametresEffectifsPrennentLeReglageSinonLeDefaut() {
        Map<String, BigDecimal> p = moteur.parametresEffectifs("NEGOCE", reglages("negoce_frais_port", "60"));
        assertEquals(e("60"), p.get("negoce_frais_port"));
        assertEquals(e("1500"), p.get("negoce_seuil_franco"));
        assertFalse(p.containsKey("vauban_escompte_pct"));
    }

    private static Trace trace(Resultat r, String regle) {
        return r.traces().stream().filter(t -> t.regle().equals(regle)).findFirst().orElseThrow();
    }
}
