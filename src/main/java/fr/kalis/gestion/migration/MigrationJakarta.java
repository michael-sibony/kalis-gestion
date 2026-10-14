package fr.kalis.gestion.migration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passage d'un code Java EE 8 (javax) à Jakarta EE 10 (jakarta).
 *
 * Le piège classique : tout ce qui commence par javax ne bascule pas. Les paquets du JDK
 * (javax.sql, javax.naming, javax.transaction.xa, javax.crypto...) restent en javax.
 * Un rechercher-remplacer aveugle casse la compilation, c'est ce que cet outil évite.
 */
public final class MigrationJakarta {

    /** Paquets des spécifications Jakarta EE, renommés en jakarta à partir d'EE 9. */
    static final List<String> PAQUETS_JAKARTA = List.of(
            "javax.activation", "javax.annotation", "javax.batch", "javax.decorator", "javax.ejb",
            "javax.el", "javax.enterprise", "javax.faces", "javax.inject", "javax.interceptor",
            "javax.jms", "javax.json", "javax.jws", "javax.mail", "javax.persistence", "javax.resource",
            "javax.security.auth.message", "javax.security.enterprise", "javax.security.jacc",
            "javax.servlet", "javax.transaction", "javax.validation", "javax.websocket", "javax.ws.rs",
            "javax.xml.bind", "javax.xml.soap", "javax.xml.ws");

    /** Sous-paquets qui appartiennent au JDK et restent en javax. */
    static final List<String> EXCEPTIONS_JDK = List.of(
            "javax.annotation.processing", "javax.transaction.xa");

    private static final Pattern NOM_QUALIFIE = Pattern.compile("\\bjavax(\\.[A-Za-z_][A-Za-z0-9_]*)+");
    private static final Pattern VERSION_RACINE = Pattern.compile("version=\"[0-9.]+\"");

    private MigrationJakarta() {
    }

    public enum TypeFichier { JAVA, PERSISTENCE, BEANS, WEB, POM, AUTRE }

    public record Changement(int ligne, String avant, String apres, String motif) {
    }

    public record Alerte(int ligne, String niveau, String message) {
    }

    public record Resultat(TypeFichier type, String contenu, List<Changement> changements, List<Alerte> alertes,
                           int conservesEnJavax) {
    }

    public static TypeFichier detecter(String nomFichier, String contenu) {
        String nom = nomFichier == null ? "" : nomFichier.toLowerCase(Locale.ROOT).trim();
        String c = contenu == null ? "" : contenu;
        if (nom.endsWith(".java")) return TypeFichier.JAVA;
        if (nom.endsWith("persistence.xml")) return TypeFichier.PERSISTENCE;
        if (nom.endsWith("beans.xml")) return TypeFichier.BEANS;
        if (nom.endsWith("web.xml")) return TypeFichier.WEB;
        if (nom.endsWith("pom.xml")) return TypeFichier.POM;
        if (c.contains("<persistence")) return TypeFichier.PERSISTENCE;
        if (c.contains("<beans")) return TypeFichier.BEANS;
        if (c.contains("<web-app")) return TypeFichier.WEB;
        if (c.contains("<project")) return TypeFichier.POM;
        if (c.contains("import ") || c.contains("package ") || c.contains("class ")) return TypeFichier.JAVA;
        return TypeFichier.AUTRE;
    }

    public static Resultat convertir(String nomFichier, String contenu) {
        String source = contenu == null ? "" : contenu.replace("\r\n", "\n");
        TypeFichier type = detecter(nomFichier, source);
        String[] lignes = source.split("\n", -1);
        List<Changement> changements = new ArrayList<>();
        List<Alerte> alertes = new ArrayList<>();
        int conserves = 0;
        String racine = switch (type) {
            case PERSISTENCE -> "<persistence";
            case BEANS -> "<beans";
            case WEB -> "<web-app";
            default -> null;
        };
        boolean dansRacine = false;
        boolean racineVue = false;

        for (int i = 0; i < lignes.length; i++) {
            int numero = i + 1;
            String avant = lignes[i];
            String ligne = avant;
            List<String> motifs = new ArrayList<>();

            // 1. Paquets javax des spécifications : on bascule, sauf exceptions du JDK.
            Matcher m = NOM_QUALIFIE.matcher(ligne);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String nom = m.group();
                String paquet = paquetJakarta(nom);
                if (paquet != null) {
                    m.appendReplacement(sb, Matcher.quoteReplacement("jakarta" + nom.substring("javax".length())));
                    String motif = paquet + " → jakarta" + paquet.substring("javax".length());
                    if (!motifs.contains(motif)) motifs.add(motif);
                } else {
                    conserves++;
                    alertes.add(new Alerte(numero, "info", nom + " reste en javax : paquet du JDK, hors Jakarta EE."));
                }
            }
            m.appendTail(sb);
            ligne = sb.toString();

            // 2. Espaces de noms et versions des descripteurs XML. La balise racine peut tenir sur plusieurs lignes.
            if (racine != null && !racineVue && (ligne.contains(racine + " ") || ligne.contains(racine + ">")
                    || ligne.trim().equals(racine))) {
                dansRacine = true;
                racineVue = true;
            }
            ligne = descripteurs(type, ligne, motifs, dansRacine);
            if (dansRacine && ligne.contains(">")) {
                dansRacine = false;
            }

            // 3. Points d'attention qui ne se corrigent pas mécaniquement.
            alertes.addAll(alertes(type, numero, avant, lignes));

            if (!ligne.equals(avant)) {
                lignes[i] = ligne;
                changements.add(new Changement(numero, avant, ligne, String.join(" ; ", motifs)));
            }
        }

        if (type == TypeFichier.POM) {
            pom(lignes, changements, alertes);
        }
        if (type == TypeFichier.BEANS) {
            beans(lignes, changements, alertes);
        }
        return new Resultat(type, String.join("\n", lignes), changements, alertes, conserves);
    }

    /** Renvoie le paquet de spécification concerné, ou null si le nom reste en javax. */
    static String paquetJakarta(String nomQualifie) {
        for (String exception : EXCEPTIONS_JDK) {
            if (nomQualifie.equals(exception) || nomQualifie.startsWith(exception + ".")) {
                return null;
            }
        }
        String retenu = null;
        for (String p : PAQUETS_JAKARTA) {
            if ((nomQualifie.equals(p) || nomQualifie.startsWith(p + "."))
                    && (retenu == null || p.length() > retenu.length())) {
                retenu = p;
            }
        }
        return retenu;
    }

    private static String descripteurs(TypeFichier type, String ligne, List<String> motifs, boolean racine) {
        String l = ligne;
        switch (type) {
            case PERSISTENCE -> {
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd",
                        "https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd", "schéma Persistence 3.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/persistence/persistence_2_1.xsd",
                        "https://jakarta.ee/xml/ns/persistence/persistence_3_0.xsd", "schéma Persistence 3.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/persistence",
                        "https://jakarta.ee/xml/ns/persistence", "espace de noms Jakarta Persistence", motifs);
                l = remplacer(l, "org.hibernate.ejb.HibernatePersistence",
                        "org.hibernate.jpa.HibernatePersistenceProvider", "fournisseur Hibernate actuel", motifs);
                if (racine) {
                    l = version(l, "3.0", motifs);
                }
            }
            case BEANS -> {
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee/beans_2_0.xsd",
                        "https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd", "schéma CDI 4.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee/beans_1_1.xsd",
                        "https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd", "schéma CDI 4.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee",
                        "https://jakarta.ee/xml/ns/jakartaee", "espace de noms Jakarta EE", motifs);
                if (racine) {
                    l = version(l, "4.0", motifs);
                }
            }
            case WEB -> {
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee/web-app_4_0.xsd",
                        "https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd", "schéma Servlet 6.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd",
                        "https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd", "schéma Servlet 6.0", motifs);
                l = remplacer(l, "http://xmlns.jcp.org/xml/ns/javaee",
                        "https://jakarta.ee/xml/ns/jakartaee", "espace de noms Jakarta EE", motifs);
                if (racine) {
                    l = version(l, "6.0", motifs);
                }
            }
            default -> {
            }
        }
        return l;
    }

    private static String remplacer(String ligne, String cible, String remplacement, String motif, List<String> motifs) {
        if (!ligne.contains(cible)) {
            return ligne;
        }
        if (!motifs.contains(motif)) motifs.add(motif);
        return ligne.replace(cible, remplacement);
    }

    private static String version(String ligne, String version, List<String> motifs) {
        Matcher v = VERSION_RACINE.matcher(ligne);
        if (v.find() && !v.group().equals("version=\"" + version + "\"")) {
            motifs.add("version " + version);
            return v.replaceFirst("version=\"" + version + "\"");
        }
        return ligne;
    }

    private static List<Alerte> alertes(TypeFichier type, int numero, String ligne, String[] toutes) {
        List<Alerte> a = new ArrayList<>();
        if (ligne.contains("javax.faces.bean")) {
            a.add(new Alerte(numero, "à reprendre", "Les beans gérés JSF (javax.faces.bean) n'existent plus en Faces 4.0 : "
                    + "passer en CDI avec @Named et une portée CDI."));
        }
        if (ligne.contains("javax.xml.bind")) {
            a.add(new Alerte(numero, "à vérifier", "JAXB a quitté le JDK en Java 11 : il est fourni par le serveur Jakarta EE, "
                    + "mais un batch lancé hors serveur doit embarquer jakarta.xml.bind-api et son implémentation."));
        }
        if (ligne.contains("@TypeDef") || ligne.contains("org.hibernate.annotations.TypeDef")) {
            a.add(new Alerte(numero, "à reprendre", "@TypeDef a disparu en Hibernate 6 (embarqué par WildFly 27 et suivants) : "
                    + "passer par @JdbcTypeCode ou un AttributeConverter."));
        }
        if (ligne.contains("createCriteria(") || ligne.contains("org.hibernate.Criteria")) {
            a.add(new Alerte(numero, "à reprendre", "L'ancienne API Criteria d'Hibernate est supprimée en Hibernate 6 : "
                    + "réécrire avec CriteriaBuilder de Jakarta Persistence."));
        }
        if (type == TypeFichier.PERSISTENCE && ligne.matches(".*hibernate\\.dialect.*[0-9]+Dialect.*")) {
            a.add(new Alerte(numero, "à vérifier", "Dialecte versionné déprécié en Hibernate 6, qui détecte la base tout seul : "
                    + "retirer la propriété ou garder le dialecte générique."));
        }
        if (type == TypeFichier.POM && ligne.matches(".*<(maven\\.compiler\\.)?(source|target|release)>1\\.[0-8]<.*")) {
            a.add(new Alerte(numero, "à reprendre", "Jakarta EE 10 demande Java 11 au minimum : viser Java 17 ou 21."));
        }
        if (type == TypeFichier.POM && ligne.contains("<groupId>org.hibernate</groupId>")
                && numero < toutes.length && toutes[numero].contains("hibernate-core")) {
            a.add(new Alerte(numero, "à vérifier", "Hibernate 6 a changé de groupId (org.hibernate.orm) ; "
                    + "sur WildFly, le passer en scope provided."));
        }
        return a;
    }

    /** Remplace les API Java EE 8 par leurs équivalents Jakarta EE 10, sur les lignes du bloc de dépendance. */
    private static void pom(String[] lignes, List<Changement> changements, List<Alerte> alertes) {
        for (int i = 0; i < lignes.length; i++) {
            String api = null;
            String version = null;
            if (lignes[i].contains("<artifactId>javaee-api</artifactId>")) {
                api = "<artifactId>jakarta.jakartaee-api</artifactId>";
                version = "10.0.0";
            } else if (lignes[i].contains("<artifactId>javaee-web-api</artifactId>")) {
                api = "<artifactId>jakarta.jakartaee-web-api</artifactId>";
                version = "10.0.0";
            } else if (lignes[i].contains("<artifactId>jakarta.servlet-api</artifactId>")) {
                // Déjà renommée par l'étape javax : il reste la version, la 4.x n'existe pas en jakarta.
                version = "6.0.0";
            }
            if (version == null) continue;
            for (int j = Math.max(0, i - 2); j <= Math.min(lignes.length - 1, i + 2); j++) {
                String avant = lignes[j];
                String apres = avant
                        .replace("<groupId>javax</groupId>", "<groupId>jakarta.platform</groupId>")
                        .replaceAll("<version>[3-8]\\.[0-9.]+</version>", "<version>" + version + "</version>");
                if (api != null) {
                    apres = apres.replaceAll("<artifactId>javaee(-web)?-api</artifactId>", api);
                }
                if (!apres.equals(avant)) {
                    lignes[j] = apres;
                    enregistrer(changements, j + 1, avant, apres, "dépendance Jakarta EE 10");
                }
            }
        }
    }

    /**
     * CDI 4.0 change le sens d'un beans.xml sans mode de découverte : il vaut désormais
     * « annotated » au lieu de « all ». On écrit « all » pour garder le comportement d'avant.
     */
    private static void beans(String[] lignes, List<Changement> changements, List<Alerte> alertes) {
        for (int i = 0; i < lignes.length; i++) {
            String avant = lignes[i];
            if (!avant.contains("<beans")) {
                continue;
            }
            StringBuilder balise = new StringBuilder();
            for (int j = i; j < lignes.length; j++) {
                balise.append(lignes[j]);
                if (lignes[j].contains(">")) break;
            }
            if (balise.toString().contains("bean-discovery-mode")) {
                return;
            }
            {
                String apres = avant.replaceFirst("<beans", "<beans bean-discovery-mode=\"all\"");
                lignes[i] = apres;
                enregistrer(changements, i + 1, avant, apres, "découverte « all » rendue explicite");
                alertes.add(new Alerte(i + 1, "à vérifier", "En CDI 4.0, un beans.xml sans bean-discovery-mode vaut « annotated » "
                        + "et non plus « all » : les classes sans annotation de portée ne seraient plus des beans. "
                        + "Le mode « all » est écrit pour garder le comportement actuel."));
                return;
            }
        }
    }

    /** Ajoute un changement, ou le fusionne avec celui déjà relevé sur la même ligne. */
    private static void enregistrer(List<Changement> changements, int ligne, String avant, String apres, String motif) {
        for (int k = 0; k < changements.size(); k++) {
            Changement c = changements.get(k);
            if (c.ligne() == ligne) {
                String motifs = c.motif().isEmpty() ? motif : c.motif() + " ; " + motif;
                changements.set(k, new Changement(ligne, c.avant(), apres, motifs));
                return;
            }
        }
        changements.add(new Changement(ligne, avant, apres, motif));
        changements.sort((a, b) -> Integer.compare(a.ligne(), b.ligne()));
    }
}
