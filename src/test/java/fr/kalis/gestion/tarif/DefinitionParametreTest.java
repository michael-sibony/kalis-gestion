package fr.kalis.gestion.tarif;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionParametreTest {

    private final DefinitionParametre taux = DefinitionParametre.of("t", "Remise", "%", "5", "0", "20", "0.5");

    @Test
    void accepteUneValeurDansLesBornesEtSurLePas() {
        assertDoesNotThrow(() -> taux.verifier(new BigDecimal("12.5")));
        assertDoesNotThrow(() -> taux.verifier(new BigDecimal("0")));
        assertDoesNotThrow(() -> taux.verifier(new BigDecimal("20")));
    }

    @Test
    void refuseHorsBornes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> taux.verifier(new BigDecimal("20.5")));
        assertTrue(e.getMessage().contains("entre 0 et 20"));
        assertThrows(IllegalArgumentException.class, () -> taux.verifier(new BigDecimal("-1")));
    }

    @Test
    void refuseHorsPas() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> taux.verifier(new BigDecimal("3.3")));
        assertTrue(e.getMessage().contains("pas de 0.5"));
    }

    @Test
    void unPasPartantDUneBorneNegative() {
        DefinitionParametre indice = DefinitionParametre.of("i", "Indice", "%", "0", "-10", "25", "0.5");
        assertDoesNotThrow(() -> indice.verifier(new BigDecimal("-4.5")));
        assertThrows(IllegalArgumentException.class, () -> indice.verifier(new BigDecimal("-4.2")));
    }

    @Test
    void ouiNonNeVautQueZeroOuUn() {
        DefinitionParametre b = DefinitionParametre.ouiNon("b", "Actif", true);
        assertDoesNotThrow(() -> b.verifier(BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> b.verifier(new BigDecimal("2")));
        assertThrows(IllegalArgumentException.class, () -> b.verifier(null));
    }
}
