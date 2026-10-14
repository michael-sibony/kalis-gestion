package fr.kalis.gestion.api;

import fr.kalis.gestion.service.CatalogueRegles;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ce qui tourne réellement : serveur, JVM, base, règles chargées par CDI. */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class PlateformeResource {

    @Context
    ServletContext servlet;

    @Resource(lookup = "java:comp/DefaultDataSource")
    DataSource source;

    @Inject
    CatalogueRegles catalogue;

    public record RegleVue(String classe, String libelle, String portee, String instance, int ordre, int parametres) {
    }

    @GET
    @Path("plateforme")
    public Map<String, Object> plateforme() {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("serveur", servlet.getServerInfo());
        p.put("servlet", servlet.getEffectiveMajorVersion() + "." + servlet.getEffectiveMinorVersion()
                + " (conteneur " + servlet.getMajorVersion() + "." + servlet.getMinorVersion() + ")");
        p.put("java", Runtime.version().toString());
        p.put("jvm", System.getProperty("java.vm.name") + ", " + System.getProperty("java.vendor"));
        p.put("systeme", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        p.put("processeurs", rt.availableProcessors());
        p.put("memoireUtiliseeMo", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        p.put("memoireMaxMo", rt.maxMemory() / (1024 * 1024));
        p.put("demarreDepuisSecondes", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        p.put("base", base());
        p.put("regles", catalogue.moteur().toutesLesRegles().stream()
                .map(r -> new RegleVue(r.nomClasse(), r.libelle(), r.instanceCiblee() == null ? "Standard" : "Spécifique",
                        r.instanceCiblee(), r.ordre(), r.parametres().size()))
                .toList());
        p.put("api", List.of(
                "GET  /api/instances",
                "GET  /api/instances/{code}/articles",
                "GET  /api/instances/{code}/parametres",
                "PUT  /api/instances/{code}/parametres/{cle}",
                "POST /api/instances/{code}/chiffrage",
                "POST /api/instances/{code}/reprise/analyse",
                "POST /api/instances/{code}/reprise/integration",
                "GET  /api/instances/{code}/journal",
                "POST /api/outils/migration-jakarta",
                "GET  /api/plateforme",
                "GET  /api/sante"));
        return p;
    }

    @GET
    @Path("sante")
    public Response sante() {
        Map<String, Object> s = new LinkedHashMap<>();
        boolean baseOk;
        try (Connection c = source.getConnection()) {
            baseOk = c.isValid(2);
        } catch (Exception e) {
            baseOk = false;
        }
        s.put("statut", baseOk ? "UP" : "DOWN");
        s.put("base", baseOk ? "joignable" : "injoignable");
        s.put("regles", catalogue.moteur().toutesLesRegles().size());
        return Response.status(baseOk ? 200 : 503).entity(s).build();
    }

    private Map<String, String> base() {
        Map<String, String> b = new LinkedHashMap<>();
        try (Connection c = source.getConnection()) {
            DatabaseMetaData m = c.getMetaData();
            b.put("produit", m.getDatabaseProductName() + " " + m.getDatabaseProductVersion());
            b.put("pilote", m.getDriverName() + " " + m.getDriverVersion());
            b.put("source", "java:comp/DefaultDataSource");
        } catch (Exception e) {
            b.put("produit", "indisponible");
        }
        return b;
    }
}
