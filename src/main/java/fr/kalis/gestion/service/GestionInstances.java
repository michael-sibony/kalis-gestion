package fr.kalis.gestion.service;

import fr.kalis.gestion.domaine.Article;
import fr.kalis.gestion.domaine.EvenementJournal;
import fr.kalis.gestion.domaine.InstanceClient;
import fr.kalis.gestion.domaine.ValeurParametre;
import fr.kalis.gestion.reprise.RepriseArticles;
import fr.kalis.gestion.tarif.DefinitionParametre;
import fr.kalis.gestion.tarif.Ligne;
import fr.kalis.gestion.tarif.Montants;
import fr.kalis.gestion.tarif.MoteurTarification;
import fr.kalis.gestion.tarif.RegleTarifaire;
import fr.kalis.gestion.tarif.Resultat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Opérations de mise en oeuvre sur une instance client. Transactionnel de bout en bout. */
@ApplicationScoped
@Transactional
public class GestionInstances {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @PersistenceContext
    EntityManager em;

    @Inject
    CatalogueRegles catalogue;

    // --- Jeu de recette -------------------------------------------------------

    public void initialiser() {
        for (InstanceClient i : JeuDeRecette.instances()) {
            if (em.find(InstanceClient.class, i.getCode()) == null) {
                em.persist(i);
                charger(i.getCode());
                journaliser(i.getCode(), "Installation", "Instance créée : catalogue initial et paramétrage de recette chargés.");
            }
        }
    }

    public void reinitialiser(String code) {
        instance(code);
        em.createQuery("delete from Article a where a.instanceCode = :i").setParameter("i", code).executeUpdate();
        em.createQuery("delete from ValeurParametre v where v.instanceCode = :i").setParameter("i", code).executeUpdate();
        em.flush();
        charger(code);
        journaliser(code, "Réinitialisation", "Catalogue et paramétrage remis à l'état de recette.");
    }

    private void charger(String code) {
        JeuDeRecette.articles(code).forEach(em::persist);
        JeuDeRecette.reglages(code).forEach((cle, valeur) -> em.persist(new ValeurParametre(code, cle, valeur)));
    }

    // --- Lecture ----------------------------------------------------------------

    public List<InstanceClient> instances() {
        return em.createQuery("select i from InstanceClient i order by i.raisonSociale", InstanceClient.class).getResultList();
    }

    public InstanceClient instance(String code) {
        InstanceClient i = em.find(InstanceClient.class, code);
        if (i == null) {
            throw new InstanceInconnue(code);
        }
        return i;
    }

    public List<Article> articles(String code) {
        instance(code);
        return em.createNamedQuery(Article.PAR_INSTANCE, Article.class).setParameter("instance", code).getResultList();
    }

    public Map<String, Integer> panierType(String code) {
        return JeuDeRecette.panierType(code);
    }

    public List<EvenementJournal> journal(String code) {
        instance(code);
        return em.createNamedQuery(EvenementJournal.PAR_INSTANCE, EvenementJournal.class)
                .setParameter("instance", code).setMaxResults(30).getResultList();
    }

    public int nombreSpecifiques(String code) {
        return (int) catalogue.moteur().toutesLesRegles().stream().filter(r -> code.equals(r.instanceCiblee())).count();
    }

    // --- Paramétrage ------------------------------------------------------------

    public record ParametreRegle(DefinitionParametre definition, BigDecimal valeur, boolean personnalise) {
    }

    public record GroupeParametres(String regle, String classe, String portee, int ordre, List<ParametreRegle> parametres) {
    }

    public List<GroupeParametres> parametres(String code) {
        instance(code);
        Map<String, BigDecimal> reglages = reglages(code);
        List<GroupeParametres> groupes = new ArrayList<>();
        for (RegleTarifaire r : catalogue.moteur().reglesPour(code, true)) {
            List<ParametreRegle> ps = r.parametres().stream()
                    .map(d -> new ParametreRegle(d, reglages.getOrDefault(d.cle(), d.defaut()), reglages.containsKey(d.cle())))
                    .toList();
            groupes.add(new GroupeParametres(r.libelle(), r.nomClasse(),
                    r.instanceCiblee() == null ? "Standard" : "Spécifique", r.ordre(), ps));
        }
        return groupes;
    }

    public ParametreRegle regler(String code, String cle, BigDecimal valeur) {
        instance(code);
        DefinitionParametre def = catalogue.moteur().reglesPour(code, true).stream()
                .flatMap(r -> r.parametres().stream())
                .filter(d -> d.cle().equals(cle))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Le paramètre " + cle + " n'existe pas pour cette instance."));
        def.verifier(valeur);
        List<ValeurParametre> existants = em.createQuery(
                        "select v from ValeurParametre v where v.instanceCode = :i and v.cle = :c", ValeurParametre.class)
                .setParameter("i", code).setParameter("c", cle).getResultList();
        BigDecimal ancienne = existants.isEmpty() ? def.defaut() : existants.get(0).getValeur();
        if (existants.isEmpty()) {
            em.persist(new ValeurParametre(code, cle, valeur));
        } else {
            existants.get(0).modifier(valeur);
        }
        if (ancienne.compareTo(valeur) != 0) {
            journaliser(code, "Paramétrage", def.libelle() + " : " + Montants.nombre(ancienne) + " → "
                    + Montants.nombre(valeur) + " " + def.unite() + ".");
        }
        return new ParametreRegle(def, valeur, true);
    }

    Map<String, BigDecimal> reglages(String code) {
        return em.createNamedQuery(ValeurParametre.PAR_INSTANCE, ValeurParametre.class)
                .setParameter("instance", code).getResultList().stream()
                .collect(Collectors.toMap(ValeurParametre::getCle, ValeurParametre::getValeur, (a, b) -> b, HashMap::new));
    }

    // --- Chiffrage --------------------------------------------------------------

    public record Comparaison(Resultat avecSpecifiques, Resultat coeurSeul, BigDecimal ecartHt, BigDecimal ecartTtc) {
    }

    public Comparaison chiffrer(String code, Map<String, Integer> quantites) {
        Map<String, Article> parCode = articles(code).stream()
                .collect(Collectors.toMap(Article::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (String c : quantites.keySet()) {
            if (!parCode.containsKey(c)) {
                throw new IllegalArgumentException("Article inconnu dans cette instance : " + c);
            }
        }
        Map<String, BigDecimal> reglages = reglages(code);
        MoteurTarification moteur = catalogue.moteur();
        Resultat avec = moteur.chiffrer(code, reglages, lignes(parCode, quantites), true);
        Resultat sans = moteur.chiffrer(code, reglages, lignes(parCode, quantites), false);
        return new Comparaison(avec, sans, avec.totalHt().subtract(sans.totalHt()), avec.totalTtc().subtract(sans.totalTtc()));
    }

    private static List<Ligne> lignes(Map<String, Article> parCode, Map<String, Integer> quantites) {
        List<Ligne> lignes = new ArrayList<>();
        for (Article a : parCode.values()) {
            Integer q = quantites.get(a.getCode());
            if (q == null || q <= 0) {
                continue;
            }
            if (q > 100_000) {
                throw new IllegalArgumentException("Quantité hors norme sur " + a.getCode() + ".");
            }
            lignes.add(new Ligne(a.getCode(), a.getLibelle(), a.getFamille(), a.getUnite(), q, a.getPrixHt(), a.getTauxTva()));
        }
        return lignes;
    }

    // --- Reprise de données -----------------------------------------------------------

    public RepriseArticles.Rapport analyser(String code, String contenu) {
        Set<String> existants = articles(code).stream().map(Article::getCode).collect(Collectors.toSet());
        return RepriseArticles.analyser(contenu, existants);
    }

    public RepriseArticles.Rapport integrer(String code, String contenu) {
        RepriseArticles.Rapport rapport = analyser(code, contenu);
        Map<String, Article> parCode = articles(code).stream()
                .collect(Collectors.toMap(Article::getCode, Function.identity()));
        String origine = "Reprise du " + ZonedDateTime.now(PARIS).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        for (RepriseArticles.LigneReprise l : rapport.valides()) {
            Article existant = parCode.get(l.code());
            if (existant != null) {
                existant.reprendre(l.libelle(), l.famille(), l.unite(), l.prixHt(), l.tauxTva(), origine);
            } else {
                em.persist(new Article(code, l.code(), l.libelle(), l.famille(), l.unite(), l.prixHt(), l.tauxTva(), origine));
            }
        }
        journaliser(code, "Reprise", rapport.creations() + " création(s), " + rapport.misesAJour() + " mise(s) à jour, "
                + rapport.rejets() + " ligne(s) rejetée(s) et laissée(s) de côté.");
        return rapport;
    }

    // --- Journal --------------------------------------------------------------------

    private void journaliser(String code, String nature, String message) {
        em.persist(new EvenementJournal(code, nature, message));
    }
}
