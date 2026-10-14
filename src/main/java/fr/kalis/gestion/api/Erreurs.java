package fr.kalis.gestion.api;

import fr.kalis.gestion.service.InstanceInconnue;
import jakarta.ejb.EJBException;
import jakarta.transaction.TransactionalException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Toute erreur part en JSON lisible : un message métier, jamais une pile Java. */
@Provider
public class Erreurs implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(Erreurs.class.getName());

    @Override
    public Response toResponse(Throwable e) {
        Throwable cause = racine(e);
        if (cause instanceof ConstraintViolationException v) {
            return reponse(400, v.getConstraintViolations().stream()
                    .map(c -> c.getPropertyPath() + " : " + c.getMessage())
                    .sorted().collect(Collectors.joining(" ; ")));
        }
        if (cause instanceof WebApplicationException w) {
            int statut = w.getResponse().getStatus();
            return reponse(statut, statut == 404 ? "Ressource introuvable." : "Requête refusée (" + statut + ").");
        }
        if (cause instanceof InstanceInconnue) {
            return reponse(404, cause.getMessage());
        }
        if (cause instanceof IllegalArgumentException) {
            return reponse(400, cause.getMessage());
        }
        LOG.log(Level.SEVERE, "Erreur non prévue", e);
        return reponse(500, "Erreur interne, consignée dans le journal du serveur.");
    }

    private static Throwable racine(Throwable e) {
        Throwable t = e;
        while ((t instanceof EJBException || t instanceof TransactionalException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static Response reponse(int statut, String message) {
        return Response.status(statut).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("erreur", message == null ? "Erreur" : message)).build();
    }
}
