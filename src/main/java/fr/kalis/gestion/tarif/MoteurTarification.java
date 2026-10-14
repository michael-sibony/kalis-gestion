package fr.kalis.gestion.tarif;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Exécute les règles applicables à une instance client, dans l'ordre.
 * Classe sans dépendance au conteneur : elle se teste en JUnit pur.
 */
public final class MoteurTarification {

    private final List<RegleTarifaire> regles;

    public MoteurTarification(List<? extends RegleTarifaire> regles) {
        List<RegleTarifaire> triees = new ArrayList<>(regles);
        triees.sort(Comparator.comparingInt(RegleTarifaire::ordre).thenComparing(RegleTarifaire::nomClasse));
        this.regles = List.copyOf(triees);
    }

    /** Règles qui s'exécutent pour cette instance : le coeur, plus ses spécifiques si demandé. */
    public List<RegleTarifaire> reglesPour(String instance, boolean avecSpecifiques) {
        return regles.stream()
                .filter(r -> r.instanceCiblee() == null
                        || (avecSpecifiques && r.instanceCiblee().equals(instance)))
                .toList();
    }

    public List<RegleTarifaire> toutesLesRegles() {
        return regles;
    }

    /** Valeurs effectives : la valeur réglée pour l'instance, sinon la valeur par défaut de la règle. */
    public Map<String, BigDecimal> parametresEffectifs(String instance, Map<String, BigDecimal> reglages) {
        Map<String, BigDecimal> effectifs = new LinkedHashMap<>();
        for (RegleTarifaire r : reglesPour(instance, true)) {
            for (DefinitionParametre d : r.parametres()) {
                effectifs.put(d.cle(), reglages.getOrDefault(d.cle(), d.defaut()));
            }
        }
        return effectifs;
    }

    public Resultat chiffrer(String instance, Map<String, BigDecimal> reglages,
                             List<Ligne> lignes, boolean avecSpecifiques) {
        Chiffrage c = new Chiffrage(instance, parametresEffectifs(instance, reglages), lignes);
        for (RegleTarifaire regle : reglesPour(instance, avecSpecifiques)) {
            c.executer(regle);
        }
        return resultat(c);
    }

    /** 20.00 devient 20 et 5.50 devient 5.5, pour regrouper les bases par taux. */
    static BigDecimal taux(BigDecimal t) {
        BigDecimal n = t.stripTrailingZeros();
        return n.scale() < 0 ? n.setScale(0) : n;
    }

    private static Resultat resultat(Chiffrage c) {
        Map<BigDecimal, BigDecimal> bases = new TreeMap<>();
        for (Ligne l : c.lignes()) {
            bases.merge(taux(l.tauxTva()), l.montantNetHt(), BigDecimal::add);
        }
        for (Ajustement a : c.ajustements()) {
            bases.merge(taux(a.tauxTva()), a.montantHt(), BigDecimal::add);
        }
        List<Resultat.Tva> tva = new ArrayList<>();
        BigDecimal totalTva = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<BigDecimal, BigDecimal> e : bases.entrySet()) {
            BigDecimal montant = c.exonereTva()
                    ? BigDecimal.ZERO.setScale(2)
                    : e.getValue().multiply(e.getKey()).divide(Chiffrage.CENT, 2, RoundingMode.HALF_UP);
            tva.add(new Resultat.Tva(e.getKey(), e.getValue(), montant));
            totalTva = totalTva.add(montant);
        }
        List<Resultat.LigneChiffree> lignes = c.lignes().stream()
                .map(l -> new Resultat.LigneChiffree(l.code(), l.libelle(), l.unite(), l.quantiteDemandee(),
                        l.quantite(), l.prixUnitaireHt(), l.montantBrutHt(), l.remise(), l.majoration(),
                        l.montantNetHt(), l.tauxTva()))
                .toList();
        BigDecimal totalHt = c.totalHt();
        return new Resultat(c.instance(), lignes, c.ajustements(), c.traces(), c.mentions(),
                c.totalBrutHt(), c.totalRemises(), c.totalMajorations(), totalHt, tva, totalTva,
                totalHt.add(totalTva), c.exonereTva());
    }
}
