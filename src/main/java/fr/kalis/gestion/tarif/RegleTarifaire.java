package fr.kalis.gestion.tarif;

import java.util.List;

/**
 * Une règle du moteur de chiffrage.
 *
 * Les règles du coeur s'appliquent à toutes les instances. Une règle annotée
 * {@link Specifique} ne s'applique qu'à l'instance client qu'elle désigne :
 * c'est par là que passe tout développement spécifique, sans toucher au coeur.
 */
public interface RegleTarifaire {

    /** Rang d'exécution, du plus petit au plus grand. */
    int ordre();

    /** Libellé affiché dans la trace et dans l'écran de paramétrage. */
    String libelle();

    /** Paramètres que l'intégrateur peut régler pour chaque client. */
    default List<DefinitionParametre> parametres() {
        return List.of();
    }

    void appliquer(Chiffrage chiffrage);

    /** Code de l'instance ciblée, ou null pour une règle du coeur. */
    default String instanceCiblee() {
        Specifique s = getClass().getAnnotation(Specifique.class);
        if (s == null && getClass().getSuperclass() != null) {
            // Les beans CDI sont parfois servis par une sous-classe proxy.
            s = getClass().getSuperclass().getAnnotation(Specifique.class);
        }
        return s == null ? null : s.value();
    }

    default String nomClasse() {
        Class<?> c = getClass();
        while (c.getName().contains("$")) {
            c = c.getSuperclass();
        }
        return c.getName();
    }
}
