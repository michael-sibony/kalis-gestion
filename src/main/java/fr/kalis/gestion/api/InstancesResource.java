package fr.kalis.gestion.api;

import fr.kalis.gestion.domaine.Article;
import fr.kalis.gestion.domaine.EvenementJournal;
import fr.kalis.gestion.domaine.InstanceClient;
import fr.kalis.gestion.reprise.RepriseArticles;
import fr.kalis.gestion.service.GestionInstances;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Path("/instances")
@Produces(MediaType.APPLICATION_JSON)
public class InstancesResource {

    @Inject
    GestionInstances gestion;

    public record InstanceVue(String code, String raisonSociale, String ville, String secteur, String miseEnService,
                              int articles, int specifiques, Map<String, Integer> panierType) {
    }

    public record ArticleVue(String code, String libelle, String famille, String unite, BigDecimal prixHt,
                             BigDecimal tauxTva, String origine) {
    }

    public record Reglage(@NotNull BigDecimal valeur) {
    }

    public record Demande(@NotNull @Size(max = 200) Map<String, Integer> quantites) {
    }

    public record RapportVue(long creations, long misesAJour, long rejets, List<RepriseArticles.LigneReprise> lignes,
                             boolean integre) {
        static RapportVue de(RepriseArticles.Rapport r, boolean integre) {
            return new RapportVue(r.creations(), r.misesAJour(), r.rejets(), r.lignes(), integre);
        }
    }

    public record EvenementVue(Instant horodatage, String nature, String message) {
    }

    @GET
    public List<InstanceVue> instances() {
        return gestion.instances().stream().map(this::vue).toList();
    }

    @GET
    @Path("/{code}")
    public InstanceVue instance(@PathParam("code") String code) {
        return vue(gestion.instance(code));
    }

    private InstanceVue vue(InstanceClient i) {
        return new InstanceVue(i.getCode(), i.getRaisonSociale(), i.getVille(), i.getSecteur(), i.getMiseEnService(),
                gestion.articles(i.getCode()).size(), gestion.nombreSpecifiques(i.getCode()), gestion.panierType(i.getCode()));
    }

    @GET
    @Path("/{code}/articles")
    public List<ArticleVue> articles(@PathParam("code") String code) {
        return gestion.articles(code).stream().map(InstancesResource::vue).toList();
    }

    private static ArticleVue vue(Article a) {
        return new ArticleVue(a.getCode(), a.getLibelle(), a.getFamille(), a.getUnite(), a.getPrixHt(), a.getTauxTva(),
                a.getOrigine());
    }

    @GET
    @Path("/{code}/parametres")
    public List<GestionInstances.GroupeParametres> parametres(@PathParam("code") String code) {
        return gestion.parametres(code);
    }

    @PUT
    @Path("/{code}/parametres/{cle}")
    @Consumes(MediaType.APPLICATION_JSON)
    public GestionInstances.ParametreRegle regler(@PathParam("code") String code, @PathParam("cle") String cle,
                                                  @Valid @NotNull Reglage reglage) {
        return gestion.regler(code, cle, reglage.valeur());
    }

    @POST
    @Path("/{code}/chiffrage")
    @Consumes(MediaType.APPLICATION_JSON)
    public GestionInstances.Comparaison chiffrer(@PathParam("code") String code, @Valid @NotNull Demande demande) {
        return gestion.chiffrer(code, demande.quantites());
    }

    @POST
    @Path("/{code}/reprise/analyse")
    @Consumes(MediaType.TEXT_PLAIN)
    public RapportVue analyser(@PathParam("code") String code, String contenu) {
        verifierTaille(contenu);
        return RapportVue.de(gestion.analyser(code, contenu), false);
    }

    @POST
    @Path("/{code}/reprise/integration")
    @Consumes(MediaType.TEXT_PLAIN)
    public RapportVue integrer(@PathParam("code") String code, String contenu) {
        verifierTaille(contenu);
        return RapportVue.de(gestion.integrer(code, contenu), true);
    }

    private static void verifierTaille(String contenu) {
        if (contenu != null && contenu.length() > 200_000) {
            throw new IllegalArgumentException("Fichier trop volumineux pour une reprise en ligne : découper par lots de 2 000 articles.");
        }
    }

    @GET
    @Path("/{code}/journal")
    public List<EvenementVue> journal(@PathParam("code") String code) {
        return gestion.journal(code).stream().map(e -> new EvenementVue(e.getHorodatage(), e.getNature(), e.getMessage())).toList();
    }

    @POST
    @Path("/{code}/reinitialisation")
    public InstanceVue reinitialiser(@PathParam("code") String code) {
        gestion.reinitialiser(code);
        return vue(gestion.instance(code));
    }
}
