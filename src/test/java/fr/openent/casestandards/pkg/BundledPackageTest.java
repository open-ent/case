package fr.openent.casestandards.pkg;

import fr.openent.casestandards.core.Uris;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import static org.junit.Assert.assertEquals;
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

	private static JsonObject resource(String path) throws Exception {
		try (InputStream in = BundledPackageTest.class.getClassLoader().getResourceAsStream(path)) {
			assertNotNull(path, in);
			try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
				return new JsonObject(scanner.next());
			}
		}
	}
}
