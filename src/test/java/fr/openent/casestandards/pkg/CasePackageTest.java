package fr.openent.casestandards.pkg;

import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.core.Uris;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CasePackageTest {

	private static final String BASE = "https://ent.example.org";
	private static final String DOC = "0f0e2a6c-1111-4b2a-9c3d-000000000001";
	private static final String ITEM_ROOT = "0f0e2a6c-1111-4b2a-9c3d-000000000010";
	private static final String ITEM_CHILD = "0f0e2a6c-1111-4b2a-9c3d-000000000011";
	private static final String ITEM_ORPHAN = "0f0e2a6c-1111-4b2a-9c3d-000000000012";
	private static final String EXTERNAL = "99999999-2222-4b2a-9c3d-000000000099";

	private final CasePackage pkg = new CasePackage(new Uris(BASE));

	// ------------------------------------------------------------------ lecture d'un paquet

	@Test
	public void lit_un_paquet_complet() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), "https://editeur.example.org/case");

		assertEquals(DOC, imported.documentId());
		assertEquals("Socle commun d'essai", imported.document.getString("title"));
		assertEquals("Ministère", imported.document.getString("creator"));
		assertEquals("fr", imported.document.getString("language"));
		assertEquals("https://editeur.example.org/case", imported.document.getString("source_uri"));
		assertEquals(3, imported.items.size());
		assertEquals(2, imported.associations.size());
		assertEquals(2, imported.definitions.size());
	}

	@Test
	public void conserve_les_identifiants_du_paquet() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		// Renuméroter couperait les correspondances que d'autres référentiels établissent vers
		// ces énoncés : c'est exactement ce que CASE sert à éviter.
		assertEquals(ITEM_ROOT, imported.items.get(0).getString("identifier"));
		assertEquals(ITEM_CHILD, imported.items.get(1).getString("identifier"));
	}

	@Test
	public void garde_l_uri_d_une_destination_hebergee_ailleurs() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		JsonObject external = null;
		for (JsonObject association : imported.associations) {
			if ("exactMatchOf".equals(association.getString("association_type"))) {
				external = association;
			}
		}
		assertNotNull("le lien exactMatchOf doit être repris", external);
		assertEquals(EXTERNAL, external.getString("destination_id"));
		// La destination vit sur un autre serveur : son URI est la seule trace qui permette d'y
		// revenir, et elle doit survivre à l'import.
		assertEquals("https://autre.example.org/ims/case/v1p0/CFItems/" + EXTERNAL,
				external.getString("destination_uri"));
		// L'origine, elle, est chez nous : pas d'URI à conserver.
		assertNull(external.getString("origin_uri"));
	}

	@Test
	public void ecarte_un_enonce_sans_fullStatement() throws Exception {
		JsonObject source = sourcePackage();
		source.getJsonArray("CFItems").add(new JsonObject()
				.put("identifier", "0f0e2a6c-1111-4b2a-9c3d-0000000000ff")
				.put("abbreviatedStatement", "sans énoncé"));

		CasePackage.Imported imported = pkg.read(source, null);
		assertEquals(3, imported.items.size());
		assertTrue(hasNotice(imported, "item.without.statement"));
	}

	@Test
	public void ecarte_un_lien_dont_le_type_n_est_pas_du_vocabulaire() throws Exception {
		JsonObject source = sourcePackage();
		source.getJsonArray("CFAssociations").add(association(
				"0f0e2a6c-1111-4b2a-9c3d-0000000000fe", "inventedType", ITEM_ROOT, ITEM_CHILD, null));

		CasePackage.Imported imported = pkg.read(source, null);
		assertEquals(2, imported.associations.size());
		assertTrue(hasNotice(imported, "association.unknown.type"));
	}

	@Test
	public void ecarte_un_lien_dont_l_origine_est_absente_du_paquet() throws Exception {
		JsonObject source = sourcePackage();
		source.getJsonArray("CFAssociations").add(association(
				"0f0e2a6c-1111-4b2a-9c3d-0000000000fd", "isChildOf", EXTERNAL, ITEM_ROOT, null));

		CasePackage.Imported imported = pkg.read(source, null);
		// On ne sert jamais l'origine : le lien serait invisible, et laisserait croire que le
		// référentiel décrit un énoncé qu'il n'héberge pas.
		assertEquals(2, imported.associations.size());
		assertTrue(hasNotice(imported, "association.origin.absent"));
	}

	@Test
	public void refuse_un_paquet_sans_referentiel() {
		try {
			pkg.read(new JsonObject().put("CFItems", new JsonArray()), null);
			fail("un paquet sans CFDocument n'a pas d'objet");
		} catch (CaseException expected) {
			assertEquals("package.without.document", expected.getMessage());
		}
	}

	@Test
	public void lit_un_identifiant_de_lien_qui_ne_porte_que_son_uri() {
		// Beaucoup de paquets réels n'écrivent que l'URI dans un LinkURI.
		assertEquals(ITEM_ROOT, CasePackage.linkIdentifier(new JsonObject()
				.put("uri", BASE + "/ims/case/v1p0/CFItems/" + ITEM_ROOT)));
		assertNull(CasePackage.linkIdentifier(null));
	}

	// ------------------------------------------------------------------ écriture

	@Test
	public void ecrit_un_paquet_relisible_a_l_identique() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		JsonObject rebuilt = pkg.build(stamp(imported.document), stampAll(imported.items),
				stampAll(imported.associations), definitionRows(imported));

		CasePackage.Imported again = pkg.read(rebuilt, null);
		assertEquals(imported.documentId(), again.documentId());
		assertEquals(imported.items.size(), again.items.size());
		assertEquals(imported.associations.size(), again.associations.size());
		assertEquals(imported.definitions.size(), again.definitions.size());
		assertEquals(imported.items.get(1).getString("full_statement"),
				again.items.get(1).getString("full_statement"));
		assertEquals(imported.items.get(1).getString("human_coding_scheme"),
				again.items.get(1).getString("human_coding_scheme"));
	}

	@Test
	public void un_enonce_porte_son_uri_et_celle_de_son_referentiel() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		JsonObject item = pkg.item(stamp(imported.items.get(0)));
		assertEquals(BASE + "/ims/case/v1p0/CFItems/" + ITEM_ROOT, item.getString("uri"));
		assertEquals(BASE + "/ims/case/v1p0/CFDocuments/" + DOC,
				item.getJsonObject("CFDocumentURI").getString("uri"));
		assertEquals("Maîtriser la langue", item.getString("fullStatement"));
	}

	@Test
	public void un_referentiel_designe_son_paquet() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		JsonObject document = pkg.document(stamp(imported.document));
		assertEquals(BASE + "/ims/case/v1p0/CFPackages/" + DOC,
				document.getJsonObject("CFPackageURI").getString("uri"));
		assertEquals(new JsonArray().add("Français"), document.getJsonArray("subject"));
	}

	@Test
	public void normalise_l_horodatage_rendu_par_postgresql() {
		// mod-postgresql rend « 2026-10-15 23:19:00.123 » là où la norme veut un T et pas de
		// fraction de seconde.
		assertEquals("2026-10-15T23:19:00", CasePackage.dateTime("2026-10-15 23:19:00.123"));
		assertEquals("2026-10-15T23:19:00", CasePackage.dateTime("2026-10-15T23:19:00"));
		assertNull(CasePackage.dateTime(null));
		assertEquals("2026-10-15", CasePackage.date("2026-10-15 00:00:00"));
	}

	@Test
	public void un_enonce_sans_champ_facultatif_ne_porte_pas_de_cle_vide() throws Exception {
		CasePackage.Imported imported = pkg.read(sourcePackage(), null);
		JsonObject orphan = null;
		for (JsonObject row : imported.items) {
			if (ITEM_ORPHAN.equals(row.getString("identifier"))) {
				orphan = row;
			}
		}
		assertNotNull(orphan);
		JsonObject item = pkg.item(stamp(orphan));
		// Ce que le paquet ne porte pas reste absent : une clé nulle ferait croire à un champ vidé.
		assertFalse(item.containsKey("humanCodingScheme"));
		assertFalse(item.containsKey("notes"));
		assertTrue(item.containsKey("fullStatement"));
	}

	// ------------------------------------------------------------------ fixture

	private static JsonObject sourcePackage() {
		final JsonObject document = new JsonObject()
				.put("identifier", DOC)
				.put("uri", "https://editeur.example.org/ims/case/v1p0/CFDocuments/" + DOC)
				.put("title", "Socle commun d'essai")
				.put("creator", "Ministère")
				.put("publisher", "Open ENT")
				.put("language", "fr")
				.put("version", "2026")
				.put("adoptionStatus", "Adopted")
				.put("subject", new JsonArray().add("Français"))
				.put("licenseURI", new JsonObject()
						.put("identifier", "0f0e2a6c-1111-4b2a-9c3d-000000000002")
						.put("title", "Licence ouverte"));

		final JsonArray items = new JsonArray()
				.add(item(ITEM_ROOT, "Maîtriser la langue", "D1", "Domaine"))
				.add(item(ITEM_CHILD, "Comprendre un texte", "D1.1", "Composante"))
				.add(new JsonObject()
						.put("identifier", ITEM_ORPHAN)
						.put("fullStatement", "Énoncé sans métadonnée"));

		final JsonArray associations = new JsonArray()
				.add(association("0f0e2a6c-1111-4b2a-9c3d-000000000020", "isChildOf",
						ITEM_CHILD, ITEM_ROOT, null))
				.add(association("0f0e2a6c-1111-4b2a-9c3d-000000000021", "exactMatchOf",
						ITEM_CHILD, EXTERNAL,
						"https://autre.example.org/ims/case/v1p0/CFItems/" + EXTERNAL));

		final JsonObject definitions = new JsonObject()
				.put("CFLicenses", new JsonArray().add(new JsonObject()
						.put("identifier", "0f0e2a6c-1111-4b2a-9c3d-000000000002")
						.put("title", "Licence ouverte")
						.put("description", "Étalab 2.0")))
				.put("CFItemTypes", new JsonArray().add(new JsonObject()
						.put("identifier", "0f0e2a6c-1111-4b2a-9c3d-000000000003")
						.put("title", "Domaine")
						.put("hierarchyCode", "1")));

		return new JsonObject()
				.put("CFDocument", document)
				.put("CFItems", items)
				.put("CFAssociations", associations)
				.put("CFDefinitions", definitions);
	}

	private static JsonObject item(String identifier, String statement, String code, String type) {
		return new JsonObject()
				.put("identifier", identifier)
				.put("fullStatement", statement)
				.put("humanCodingScheme", code)
				.put("CFItemType", type)
				.put("language", "fr")
				.put("educationLevel", new JsonArray().add("06").add("05"));
	}

	private static JsonObject association(String identifier, String type, String origin,
										  String destination, String destinationUri) {
		final JsonObject destinationLink = new JsonObject().put("identifier", destination);
		if (destinationUri != null) {
			destinationLink.put("uri", destinationUri);
		}
		return new JsonObject()
				.put("identifier", identifier)
				.put("associationType", type)
				.put("originNodeURI", new JsonObject().put("identifier", origin))
				.put("destinationNodeURI", destinationLink);
	}

	/** Une ligne telle que la base la rendrait : avec son horodatage. */
	private static JsonObject stamp(JsonObject row) {
		return row.copy().put("last_change", "2026-10-15 23:19:00.000");
	}

	private static List<JsonObject> stampAll(List<JsonObject> rows) {
		final List<JsonObject> stamped = new ArrayList<JsonObject>();
		for (JsonObject row : rows) {
			stamped.add(stamp(row));
		}
		return stamped;
	}

	private static List<JsonObject> definitionRows(CasePackage.Imported imported) {
		final List<JsonObject> rows = new ArrayList<JsonObject>();
		for (JsonObject definition : imported.definitions) {
			rows.add(definition.copy().put("last_change", "2026-10-15 23:19:00.000"));
		}
		return rows;
	}

	private static boolean hasNotice(CasePackage.Imported imported, String code) {
		for (int i = 0; i < imported.notices.size(); i++) {
			if (code.equals(imported.notices.getJsonObject(i).getString("code"))) {
				return true;
			}
		}
		return false;
	}
}
