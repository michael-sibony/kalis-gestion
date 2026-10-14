package fr.kalis.gestion.tarif;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une règle comme développement spécifique d'une instance client.
 * La valeur est le code de l'instance (NEGOCE, VAUBAN, EXPORT...).
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Specifique {
    String value();
}
