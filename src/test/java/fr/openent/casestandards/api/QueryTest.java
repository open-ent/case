package fr.openent.casestandards.api;

import fr.openent.casestandards.core.CaseException;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QueryTest {

	private static final Set<String> FIELDS = new HashSet<String>(Arrays.asList(
			"identifier", "title", "sequenceNumber"));

	private final List<JsonObject> entities = new ArrayList<JsonObject>(Arrays.asList(
			entity("c", "Charlie", 3),
			entity("a", "Alpha", 1),
			entity("b", "Bravo", 2)));

	@Test
	public void pagine_et_compte_le_total() throws Exception {
		Query.Page page = Query.parse(params("limit", "2", "offset", "1"), 1000)
				.apply(entities, FIELDS);
		assertEquals(3, page.total);
		assertEquals(2, page.items.size());
		assertEquals(2, page.limit);
		assertEquals(1, page.offset);
	}

	@Test
	public void un_offset_au_dela_du_total_rend_une_page_vide_et_non_une_erreur() throws Exception {
		Query.Page page = Query.parse(params("offset", "99"), 1000).apply(entities, FIELDS);
		assertEquals(3, page.total);
		assertTrue(page.items.isEmpty());
	}

	@Test
	public void plafonne_la_limite_demandee() throws Exception {
		// Sans plafond, un consommateur peut demander la base entière en une requête.
		Query.Page page = Query.parse(params("limit", "100000"), 50).apply(entities, FIELDS);
		assertEquals(50, page.limit);
	}

	@Test
	public void trie_dans_les_deux_sens() throws Exception {
		Query.Page ascending = Query.parse(params("sort", "title"), 1000).apply(entities, FIELDS);
		assertEquals("Alpha", ascending.items.get(0).getString("title"));

		Query.Page descending = Query.parse(params("sort", "title", "orderBy", "desc"), 1000)
				.apply(entities, FIELDS);
		assertEquals("Charlie", descending.items.get(0).getString("title"));
	}

	@Test
	public void les_entites_sans_valeur_de_tri_passent_en_dernier() throws Exception {
		entities.add(new JsonObject().put("identifier", "d"));
		Query.Page page = Query.parse(params("sort", "title"), 1000).apply(entities, FIELDS);
		assertEquals("d", page.items.get(page.items.size() - 1).getString("identifier"));
	}

	@Test
	public void projette_les_champs_demandes() throws Exception {
		Query.Page page = Query.parse(params("fields", "identifier,title"), 1000)
				.apply(entities, FIELDS);
		JsonObject first = page.items.get(0);
		assertTrue(first.containsKey("identifier"));
		assertTrue(first.containsKey("title"));
		assertFalse("un champ non demandé ne doit pas être rendu",
				first.containsKey("sequenceNumber"));
	}

	@Test
	public void un_champ_inconnu_dans_fields_est_ignore() throws Exception {
		// Contrairement à filter : demander un champ de trop n'induit personne en erreur.
		Query.Page page = Query.parse(params("fields", "title,inventedField"), 1000)
				.apply(entities, FIELDS);
		assertEquals(1, page.items.get(0).size());
	}

	@Test
	public void un_champ_inconnu_dans_sort_est_une_erreur() throws Exception {
		try {
			Query.parse(params("sort", "inventedField"), 1000).apply(entities, FIELDS);
			fail("un tri sur un champ inconnu doit être refusé");
		} catch (CaseException e) {
			assertEquals(CaseException.INVALID_SORT_FIELD, e.getCodeMinor());
		}
	}

	@Test
	public void refuse_une_pagination_ou_un_ordre_illisibles() {
		for (String[] bad : new String[][]{{"limit", "0"}, {"limit", "abc"}, {"offset", "-1"},
				{"orderBy", "ascending"}}) {
			try {
				Query.parse(params(bad[0], bad[1]), 1000);
				fail("paramètre accepté à tort : " + bad[0] + "=" + bad[1]);
			} catch (CaseException expected) {
				assertEquals(400, expected.getStatus());
			}
		}
	}

	@Test
	public void la_requete_tout_ne_pagine_pas() throws Exception {
		Query.Page page = Query.everything().apply(entities, FIELDS);
		assertEquals(3, page.items.size());
	}

	private static JsonObject entity(String id, String title, int sequence) {
		return new JsonObject().put("identifier", id).put("title", title)
				.put("sequenceNumber", sequence);
	}

	private static MultiMap params(String... pairs) {
		final MultiMap map = MultiMap.caseInsensitiveMultiMap();
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.add(pairs[i], pairs[i + 1]);
		}
		return map;
	}
}
