package fr.openent.casestandards.pkg;

import fr.openent.casestandards.core.Uris;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Le référentiel livré avec le module doit passer le convertisseur sans rien perdre : un énoncé
 * ou un lien écarté au chargement le serait en silence, au démarrage de chaque instance.
 */
public class BundledPackageTest {

	private final CasePackage pkg = new CasePackage(new Uris("https://ent.example.org"));

	@Test
	public void les_programmes_du_primaire_se_chargent_sans_perte() throws Exception {
		final JsonObject source = resource("referentiels/programmes-1d.json");
		final CasePackage.Imported imported = pkg.read(source, "bundled:test");

		assertEquals("2b931521-4371-534c-8201-b5c0ab29f0d8", imported.documentId());
		assertEquals(source.getJsonArray("CFItems").size(), imported.items.size());
		assertEquals(source.getJsonArray("CFAssociations").size(), imported.associations.size());
		assertEquals("Aucun énoncé ni lien écarté : " + imported.notices.encode(), 0, imported.notices.size());

		// Les identifiants sont repris tels quels : c'est eux que citent badges et contenus.
		final Set<String> identifiers = new HashSet<String>();
		for (JsonObject item : imported.items) {
			assertTrue(identifiers.add(item.getString("identifier")));
			assertTrue("niveau scolaire", item.getJsonArray("education_levels").size() > 0);
		}
	}

	@Test
	public void les_programmes_du_college_se_chargent_sans_perte() throws Exception {
		final JsonObject source = resource("referentiels/programmes-college.json");
		final CasePackage.Imported imported = pkg.read(source, "bundled:test");

		assertEquals(source.getJsonArray("CFItems").size(), imported.items.size());
		assertEquals(source.getJsonArray("CFAssociations").size(), imported.associations.size());
		assertEquals("Aucun énoncé ni lien écarté : " + imported.notices.encode(), 0, imported.notices.size());
		for (JsonObject item : imported.items) {
			for (Object level : item.getJsonArray("education_levels")) {
				assertTrue("niveau du collège : " + level, Arrays.asList("6E", "5E", "4E", "3E").contains(level));
			}
		}
	}

	/**
	 * Les énoncés de tous les référentiels partagent une table, indexée par leur identifiant : le
	 * « Cycle 3 » du collège ne doit pas reprendre celui du primaire (espaces de noms distincts).
	 */
	@Test
	public void les_referentiels_livres_ne_partagent_aucun_identifiant() throws Exception {
		final Set<String> primaire = new HashSet<String>();
		for (Object item : resource("referentiels/programmes-1d.json").getJsonArray("CFItems")) {
			primaire.add(((JsonObject) item).getString("identifier"));
		}
		for (Object item : resource("referentiels/programmes-college.json").getJsonArray("CFItems")) {
			assertFalse(primaire.contains(((JsonObject) item).getString("identifier")));
		}
	}

	private static JsonObject resource(String path) throws Exception {
		try (InputStream in = BundledPackageTest.class.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(path, in);
			try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
				return new JsonObject(scanner.next());
			}
		}
	}
}
