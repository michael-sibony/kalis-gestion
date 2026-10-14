package fr.kalis.gestion.api;

import fr.kalis.gestion.migration.MigrationJakarta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/outils")
@Produces(MediaType.APPLICATION_JSON)
public class OutilsResource {

    public record Source(@Size(max = 120) String nomFichier, @NotNull @Size(max = 100_000) String contenu) {
    }

    @POST
    @Path("/migration-jakarta")
    @Consumes(MediaType.APPLICATION_JSON)
    public MigrationJakarta.Resultat migrer(@Valid @NotNull Source source) {
        return MigrationJakarta.convertir(source.nomFichier(), source.contenu());
    }
}
