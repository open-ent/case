package fr.openent.casestandards.api;

import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.core.Fields;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FilterTest {

	private static final Set<String> FIELDS = new HashSet<String>(Arrays.asList(
			"title", "subject", "sequenceNumber", "CFItemTypeURI", "adoptionStatus"));

	private final JsonObject entity = new JsonObject()
			.put("title", "Socle commun")
			.put("adoptionStatus", "Adopted")
			.put("sequenceNumber", 3)
			.put("subject", new JsonArray().add("Français").add("Histoire"))
			.put("CFItemTypeURI", new JsonObject().put("title", "Domaine"));

	@Test
	public void sans_filtre_tout_passe() throws Exception {
		assertTrue(Filter.parse(null, FIELDS).matches(entity));
		assertTrue(Filter.parse("   ", FIELDS).matches(entity));
	}

	@Test
	public void compare_une_valeur_simple() throws Exception {
		assertTrue(matches("title='Socle commun'"));
		assertFalse(matches("title='Programme'"));
		assertTrue(matches("title!='Programme'"));
	}

	@Test
	public void accepte_une_valeur_sans_guillemets() throws Exception {
		assertTrue(matches("adoptionStatus=Adopted"));
	}

	@Test
	public void compare_les_nombres_en_nombres() throws Exception {
		// En texte, « 3 » serait supérieur à « 10 » : la comparaison doit être numérique.
		assertTrue(matches("sequenceNumber>2"));
		assertTrue(matches("sequenceNumber<10"));
		assertFalse(matches("sequenceNumber>3"));
		assertTrue(matches("sequenceNumber>=3"));
	}

	@Test
	public void cherche_dans_un_texte() throws Exception {
		assertTrue(matches("title~'socle'"));
		assertFalse(matches("title~'programme'"));
	}

	@Test
	public void traverse_les_tableaux_et_les_objets() throws Exception {
		// Un filtre sur un champ multiple porte sur « au moins une des valeurs ».
		assertTrue(matches("subject='Histoire'"));
		assertFalse(matches("subject='Mathématiques'"));
		assertTrue(matches("CFItemTypeURI.title='Domaine'"));
	}

	@Test
	public void combine_par_et_et_ou() throws Exception {
		assertTrue(matches("title='Socle commun' AND subject='Français'"));
		assertFalse(matches("title='Socle commun' AND subject='Mathématiques'"));
		assertTrue(matches("title='Programme' OR subject='Français'"));
	}

	@Test
	public void le_et_lie_plus_fort_que_le_ou() throws Exception {
		// « faux ET faux OU vrai » vaut vrai : sans la priorité, l'expression vaudrait faux.
		assertTrue(matches("title='Programme' AND subject='Latin' OR adoptionStatus='Adopted'"));
		assertFalse(matches("(title='Programme' AND subject='Latin') AND adoptionStatus='Adopted'"));
	}

	@Test
	public void respecte_les_parentheses() throws Exception {
		assertFalse(matches("(title='Programme' OR subject='Latin') AND adoptionStatus='Adopted'"));
		assertTrue(matches("(title='Socle commun' OR subject='Latin') AND adoptionStatus='Adopted'"));
	}

	@Test
	public void un_champ_absent_ne_vaut_aucune_valeur_mais_differe_de_toutes() throws Exception {
		final JsonObject bare = new JsonObject().put("title", "Vide");
		assertFalse(Filter.parse("subject='Français'", FIELDS).matches(bare));
		assertTrue(Filter.parse("subject!='Français'", FIELDS).matches(bare));
	}

	@Test
	public void un_champ_inconnu_est_une_erreur_et_non_un_filtre_vide() {
		// Rendre une collection vide ferait croire au consommateur qu'il n'y a rien à lire.
		try {
			Filter.parse("inventedField='x'", FIELDS);
			fail("un champ inconnu doit être refusé");
		} catch (CaseException e) {
			assertEquals(CaseException.INVALID_FILTER_FIELD, e.getCodeMinor());
			assertEquals(400, e.getStatus());
		}
	}

	@Test
	public void refuse_une_expression_mal_formee() {
		for (String expression : new String[]{"title=", "title 'x'", "(title='x'", "title='x",
				"='x'", "title='x' AND"}) {
			try {
				Filter.parse(expression, FIELDS);
				fail("expression acceptée à tort : " + expression);
			} catch (CaseException expected) {
				assertEquals(400, expected.getStatus());
			}
		}
	}

	@Test
	public void les_champs_d_un_referentiel_sont_ceux_de_la_norme() throws Exception {
		// Garde-fou : un renommage de Fields casserait les filtres des consommateurs.
		assertTrue(Fields.DOCUMENT.contains("adoptionStatus"));
		assertTrue(Fields.ITEM.contains("fullStatement"));
		assertTrue(Fields.ASSOCIATION.contains("associationType"));
		Filter.parse("fullStatement~'langue'", Fields.ITEM);
	}

	private boolean matches(String expression) throws CaseException {
		return Filter.parse(expression, FIELDS).matches(entity);
	}
}
