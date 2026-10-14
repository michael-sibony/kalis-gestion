package fr.kalis.gestion.service;

import fr.kalis.gestion.domaine.Article;
import fr.kalis.gestion.domaine.InstanceClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Données de l'environnement de recette : trois instances clients et leur catalogue. */
final class JeuDeRecette {

    private JeuDeRecette() {
    }

    static final String ORIGINE = "Catalogue initial";

    static List<InstanceClient> instances() {
        return List.of(
                new InstanceClient("NEGOCE", "Négoce Atlantique", "Nantes", "Négoce de matériaux", "Lot 3 en recette"),
                new InstanceClient("VAUBAN", "Ateliers Vauban", "Lille", "Mécanique de précision", "Lot 2 en recette"),
                new InstanceClient("EXPORT", "Distrimed Export", "Lyon", "Distribution médicale vers l'UE", "Lot 1 en recette"));
    }

    static List<Article> articles(String instance) {
        return switch (instance) {
            case "NEGOCE" -> List.of(
                    a(instance, "PLQ-BA13", "Plaque de plâtre BA13 2,50 m", "PLAQUE", "u", "8.90"),
                    a(instance, "RAIL-M48", "Rail métallique M48, 3 m", "OSSATURE", "u", "4.20"),
                    a(instance, "MONT-M48", "Montant métallique M48, 3 m", "OSSATURE", "u", "5.10"),
                    a(instance, "VIS-TTPC25", "Vis TTPC 25 mm, boîte de 1000", "FIXATION", "boîte", "18.50"),
                    a(instance, "ISO-LDV100", "Laine de verre 100 mm, rouleau", "ISOLANT", "rouleau", "32.00"),
                    a(instance, "ENDUIT-25", "Enduit de jointoiement, sac de 25 kg", "FINITION", "sac", "21.40"));
            case "VAUBAN" -> List.of(
                    a(instance, "CBL-CU16", "Câble cuivre 16 mm²", "MATIERE", "m", "6.80"),
                    a(instance, "BAR-CU20", "Barre cuivre Ø 20 mm", "MATIERE", "m", "42.00"),
                    a(instance, "USN-BRIDE", "Bride usinée inox 316L", "USINAGE", "u", "64.00"),
                    a(instance, "USN-AXE30", "Axe rectifié Ø 30 mm", "USINAGE", "u", "38.50"),
                    a(instance, "PRS-REGLAGE", "Réglage machine, heure", "SERVICE", "h", "72.00"));
            case "EXPORT" -> List.of(
                    a(instance, "GNT-NIT100", "Gants nitrile, boîte de 100", "PROTECTION", "boîte", "7.90"),
                    a(instance, "MSQ-FFP2", "Masques FFP2, boîte de 20", "PROTECTION", "boîte", "12.40"),
                    a(instance, "SER-5ML", "Seringues 5 ml, carton de 100", "INJECTION", "carton", "15.60"),
                    a(instance, "CMP-STE10", "Compresses stériles 10 x 10, boîte de 50", "SOIN", "boîte", "6.30"));
            default -> List.of();
        };
    }

    /** Réglages propres à chaque client, au-delà des valeurs par défaut des règles. */
    static Map<String, BigDecimal> reglages(String instance) {
        return switch (instance) {
            case "NEGOCE" -> Map.of("remise_client_pct", new BigDecimal("2"));
            case "VAUBAN" -> Map.of("remise_client_pct", new BigDecimal("0"), "remise_max_pct", new BigDecimal("10"));
            case "EXPORT" -> Map.of("remise_client_pct", new BigDecimal("3"), "remise_max_pct", new BigDecimal("12"));
            default -> Map.of();
        };
    }

    /** Panier proposé à l'ouverture de l'écran de chiffrage. */
    static Map<String, Integer> panierType(String instance) {
        return switch (instance) {
            case "NEGOCE" -> Map.of("PLQ-BA13", 120, "MONT-M48", 220, "RAIL-M48", 60, "VIS-TTPC25", 6);
            case "VAUBAN" -> Map.of("CBL-CU16", 120, "BAR-CU20", 15, "USN-BRIDE", 4, "PRS-REGLAGE", 2);
            case "EXPORT" -> Map.of("GNT-NIT100", 37, "MSQ-FFP2", 24, "SER-5ML", 10);
            default -> Map.of();
        };
    }

    private static Article a(String instance, String code, String libelle, String famille, String unite, String prix) {
        return new Article(instance, code, libelle, famille, unite, new BigDecimal(prix), new BigDecimal("20"), ORIGINE);
    }
}
