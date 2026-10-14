package fr.kalis.gestion.reprise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Contrôle d'un fichier d'articles avant reprise dans une instance client.
 *
 * Format attendu, tel que sorti des anciens logiciels des clients :
 * code;libellé;famille;unité;prix HT;taux TVA, séparateur point-virgule ou tabulation,
 * décimales à la virgule ou au point, ligne d'en-tête facultative.
 */
public final class RepriseArticles {

    public static final Set<BigDecimal> TAUX_TVA_FRANCE = Set.of(
            new BigDecimal("0"), new BigDecimal("2.1"), new BigDecimal("5.5"),
            new BigDecimal("10"), new BigDecimal("20"));
    public static final int COLONNES = 6;
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9._-]{1,23}");
    private static final BigDecimal PRIX_MAX = new BigDecimal("1000000");

    private RepriseArticles() {
    }

    /**
     * @param contenu        texte du fichier
     * @param codesExistants codes déjà présents dans l'instance : la ligne devient une mise à jour
     */
    public static Rapport analyser(String contenu, Set<String> codesExistants) {
        List<LigneReprise> lignes = new ArrayList<>();
        if (contenu == null || contenu.isBlank()) {
            return new Rapport(lignes);
        }
        String texte = contenu.startsWith("﻿") ? contenu.substring(1) : contenu;
        String[] brutes = texte.split("\\R", -1);
        Map<String, Integer> vus = new HashMap<>();
        for (int i = 0; i < brutes.length; i++) {
            int numero = i + 1;
            String brute = brutes[i];
            if (brute.isBlank()) {
                continue;
            }
            String[] champs = decouper(brute);
            if (i == 0 && estEntete(champs)) {
                continue;
            }
            lignes.add(controler(numero, champs, vus, codesExistants));
        }
        return new Rapport(lignes);
    }

    private static LigneReprise controler(int numero, String[] champs, Map<String, Integer> vus,
                                          Set<String> codesExistants) {
        List<String> erreurs = new ArrayList<>();
        if (champs.length != COLONNES) {
            erreurs.add(champs.length + " colonnes au lieu de " + COLONNES + ".");
            String code = champs.length > 0 ? champs[0].trim() : "";
            return new LigneReprise(numero, code, "", "", "", null, null, Statut.REJET, erreurs);
        }
        String code = champs[0].trim().toUpperCase(Locale.ROOT);
        String libelle = champs[1].trim();
        String famille = champs[2].trim().toUpperCase(Locale.ROOT);
        String unite = champs[3].trim();
        BigDecimal prix = decimal(champs[4]);
        BigDecimal tva = decimal(champs[5].replace("%", ""));

        if (!CODE.matcher(code).matches()) {
            erreurs.add("Code article « " + champs[0].trim() + " » invalide (2 à 24 caractères, lettres, chiffres, . _ -).");
        }
        if (libelle.isEmpty()) {
            erreurs.add("Libellé vide.");
        } else if (libelle.length() > 80) {
            erreurs.add("Libellé de " + libelle.length() + " caractères, 80 au maximum.");
        }
        if (famille.isEmpty()) {
            erreurs.add("Famille vide.");
        }
        if (unite.isEmpty()) {
            erreurs.add("Unité vide.");
        }
        if (prix == null) {
            erreurs.add("Prix HT « " + champs[4].trim() + " » illisible.");
        } else if (prix.signum() <= 0) {
            erreurs.add("Prix HT nul ou négatif.");
        } else if (prix.compareTo(PRIX_MAX) >= 0) {
            erreurs.add("Prix HT hors norme (" + prix.toPlainString() + ").");
        } else if (prix.scale() > 2 && prix.setScale(2, RoundingMode.HALF_UP).compareTo(prix) != 0) {
            erreurs.add("Prix HT avec plus de 2 décimales.");
        }
        if (tva == null) {
            erreurs.add("Taux de TVA « " + champs[5].trim() + " » illisible.");
        } else if (TAUX_TVA_FRANCE.stream().noneMatch(t -> t.compareTo(tva) == 0)) {
            erreurs.add("Taux de TVA " + tva.stripTrailingZeros().toPlainString() + " % inconnu en France.");
        }
        Integer premiere = vus.putIfAbsent(code, numero);
        if (premiere != null && CODE.matcher(code).matches()) {
            erreurs.add("Code en double avec la ligne " + premiere + ".");
        }

        Statut statut;
        if (!erreurs.isEmpty()) {
            statut = Statut.REJET;
        } else if (codesExistants.contains(code)) {
            statut = Statut.MISE_A_JOUR;
        } else {
            statut = Statut.CREATION;
        }
        return new LigneReprise(numero, code, libelle, famille, unite,
                prix == null ? null : prix.setScale(2, RoundingMode.HALF_UP), tva, statut, erreurs);
    }

    static String[] decouper(String ligne) {
        String sep = ligne.contains("\t") ? "\t" : ";";
        String[] champs = ligne.split(Pattern.quote(sep), -1);
        for (int i = 0; i < champs.length; i++) {
            String c = champs[i].trim();
            if (c.length() >= 2 && c.startsWith("\"") && c.endsWith("\"")) {
                c = c.substring(1, c.length() - 1).replace("\"\"", "\"");
            }
            champs[i] = c;
        }
        return champs;
    }

    private static boolean estEntete(String[] champs) {
        return champs.length > 0 && champs[0].trim().toLowerCase(Locale.ROOT).startsWith("code");
    }

    /** Lit « 1 234,50 », « 1234.5 », « 12,00 € ». Rend null si illisible. */
    static BigDecimal decimal(String brut) {
        String s = brut.trim().replace("€", "").replace(" ", "").replace(" ", "").replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        // Le dernier séparateur rencontré est celui des décimales : « 1.234,50 » comme « 1,234.50 ».
        if (s.lastIndexOf(',') > s.lastIndexOf('.')) {
            s = s.replace(".", "").replace(',', '.');
        } else {
            s = s.replace(",", "");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum Statut { CREATION, MISE_A_JOUR, REJET }

    public record LigneReprise(int numero, String code, String libelle, String famille, String unite,
                               BigDecimal prixHt, BigDecimal tauxTva, Statut statut, List<String> erreurs) {
        public boolean valide() {
            return statut != Statut.REJET;
        }
    }

    public record Rapport(List<LigneReprise> lignes) {
        public long creations() {
            return lignes.stream().filter(l -> l.statut() == Statut.CREATION).count();
        }

        public long misesAJour() {
            return lignes.stream().filter(l -> l.statut() == Statut.MISE_A_JOUR).count();
        }

        public long rejets() {
            return lignes.stream().filter(l -> l.statut() == Statut.REJET).count();
        }

        public List<LigneReprise> valides() {
            return lignes.stream().filter(LigneReprise::valide).toList();
        }
    }
}
