package fr.kalis.gestion.tarif;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pièce en cours de chiffrage, transmise de règle en règle.
 * Chaque règle modifie les lignes ou ajoute un ajustement, puis laisse une trace.
 */
public final class Chiffrage {

    public static final BigDecimal CENT = BigDecimal.valueOf(100);

    private final String instance;
    private final Map<String, BigDecimal> parametres;
    private final List<Ligne> lignes;
    private final List<Ajustement> ajustements = new ArrayList<>();
    private final List<String> mentions = new ArrayList<>();
    private final List<Trace> traces = new ArrayList<>();
    private boolean exonereTva;
    private RegleTarifaire regleCourante;

    public Chiffrage(String instance, Map<String, BigDecimal> parametres, List<Ligne> lignes) {
        this.instance = instance;
        this.parametres = Map.copyOf(parametres);
        this.lignes = List.copyOf(lignes);
    }

    void executer(RegleTarifaire regle) {
        regleCourante = regle;
        try {
            regle.appliquer(this);
        } finally {
            regleCourante = null;
        }
    }

    // --- Lecture des paramètres -------------------------------------------

    public BigDecimal parametre(String cle) {
        BigDecimal v = parametres.get(cle);
        if (v == null) {
            throw new IllegalStateException("Paramètre absent : " + cle);
        }
        return v;
    }

    public int parametreEntier(String cle) {
        return parametre(cle).intValueExact();
    }

    public boolean parametreActif(String cle) {
        return parametre(cle).signum() != 0;
    }

    // --- Actions des règles -------------------------------------------------

    public void ajouterAjustement(String libelle, BigDecimal montantHt, BigDecimal tauxTva) {
        ajustements.add(new Ajustement(libelle, montantHt.setScale(2, RoundingMode.HALF_UP), tauxTva, source()));
    }

    public void ajouterMention(String mention) {
        mentions.add(mention);
    }

    public void exonererTva() {
        this.exonereTva = true;
    }

    /** Trace d'une règle déclenchée, avec son effet sur le total HT. */
    public void tracer(BigDecimal effetHt, String detail) {
        traces.add(new Trace(regleCourante.ordre(), regleCourante.libelle(), regleCourante.nomClasse(), portee(), true,
                effetHt.setScale(2, RoundingMode.HALF_UP), detail));
    }

    /** Trace d'une règle évaluée mais sans effet. */
    public void tracerSansEffet(String detail) {
        traces.add(new Trace(regleCourante.ordre(), regleCourante.libelle(), regleCourante.nomClasse(), portee(), false,
                BigDecimal.ZERO.setScale(2), detail));
    }

    private String portee() {
        return regleCourante.instanceCiblee() == null ? "Standard" : "Spécifique";
    }

    private String source() {
        return regleCourante == null ? "Saisie" : regleCourante.libelle();
    }

    // --- Totaux ---------------------------------------------------------------

    public BigDecimal totalBrutHt() {
        return lignes.stream().map(Ligne::montantBrutHt).reduce(zero(), BigDecimal::add);
    }

    public BigDecimal totalRemises() {
        return lignes.stream().map(Ligne::remise).reduce(zero(), BigDecimal::add);
    }

    public BigDecimal totalMajorations() {
        return lignes.stream().map(Ligne::majoration).reduce(zero(), BigDecimal::add);
    }

    public BigDecimal totalLignesNetHt() {
        return lignes.stream().map(Ligne::montantNetHt).reduce(zero(), BigDecimal::add);
    }

    public BigDecimal totalHt() {
        return totalLignesNetHt().add(ajustements.stream().map(Ajustement::montantHt).reduce(zero(), BigDecimal::add));
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2);
    }

    public static BigDecimal pourcentage(BigDecimal base, BigDecimal taux) {
        return base.multiply(taux).divide(CENT, 2, RoundingMode.HALF_UP);
    }

    public String instance() { return instance; }
    public List<Ligne> lignes() { return lignes; }
    public List<Ajustement> ajustements() { return Collections.unmodifiableList(ajustements); }
    public List<String> mentions() { return Collections.unmodifiableList(mentions); }
    public List<Trace> traces() { return Collections.unmodifiableList(traces); }
    public boolean exonereTva() { return exonereTva; }
}
