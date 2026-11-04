package fr.openent.casestandards.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;

import java.util.Collection;
import java.util.Collections;

/**
 * Lectures de consultation des programmes : un arbre filtré par niveau et matière, sans rapatrier
 * le paquet complet (5 Mo pour les programmes du 1D).
 *
 * <p>La matière d'un énoncé est le deuxième segment de son code ({@code C2.MAT.1.2.5} → MAT) ; son
 * libellé est celui de l'énoncé de type « Enseignement » qui porte ce segment. L'arbre est porté
 * par les liens {@code isChildOf} : l'enfant en origine, le parent en destination ; les énoncés de
 * premier niveau ont le référentiel lui-même pour parent.</p>
 */
public class ProgrammeRepository {

	private static final String SUBJECT = "split_part(i.human_coding_scheme, '.', 2)";

	/** Les référentiels, avec leurs niveaux et leurs matières. */
	public Future<JsonArray> programmes(boolean publishedOnly) {
		return Db.sql("SELECT d.identifier AS id, d.title, d.version, d.published, "
				+ "(SELECT COALESCE(json_agg(DISTINCT e.code), '[]'::json) FROM casestd.item AS i "
				+ "CROSS JOIN LATERAL jsonb_array_elements_text(i.education_levels) AS e(code) "
				+ "WHERE i.document_id = d.identifier) AS levels, "
				+ "(SELECT COALESCE(json_agg(json_build_object('code', s.code, 'label', s.label, 'levels', s.levels) "
				+ "ORDER BY s.label), '[]'::json) FROM (SELECT " + SUBJECT + " AS code, min(i.full_statement) AS label, "
				+ "COALESCE(json_agg(DISTINCT e.code) FILTER (WHERE e.code IS NOT NULL), '[]'::json) AS levels "
				+ "FROM casestd.item AS i "
				+ "LEFT JOIN LATERAL jsonb_array_elements_text(i.education_levels) AS e(code) ON true "
				+ "WHERE i.document_id = d.identifier AND i.item_type = 'Enseignement' AND " + SUBJECT + " <> '' "
				+ "GROUP BY 1) AS s) AS subjects "
				+ "FROM casestd.document AS d" + (publishedOnly ? " WHERE d.published" : "")
				+ " ORDER BY d.title", new JsonArray());
	}

	/**
	 * L'arbre d'un référentiel, à plat : {@code id, parent, seq, code, label, type, levels, notes,
	 * start, end}. Les filtres portent sur les énoncés ; leurs ancêtres sont toujours rendus, pour
	 * que l'arbre reste lisible de la racine à la feuille.
	 *
	 * @param level   niveau ({@code CE1}, {@code 4E}…), ou {@code null}
	 * @param subject segment de matière ({@code MAT}…), ou {@code null}
	 * @param search  texte cherché dans le libellé ou le code, ou {@code null}
	 * @param current vrai pour écarter les énoncés hors de leur période de validité
	 */
	public Future<JsonArray> tree(String documentId, String level, String subject, String search, boolean current) {
		final JsonArray params = new JsonArray().add(documentId);
		final StringBuilder filters = new StringBuilder("true");
		if (level != null) {
			filters.append(" AND n.levels @> jsonb_build_array(?::text)");
			params.add(level);
		}
		if (subject != null) {
			filters.append(" AND split_part(n.code, '.', 2) = ?");
			params.add(subject);
		}
		if (search != null) {
			final String pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
			filters.append(" AND (n.label ILIKE ? OR n.code ILIKE ?)");
			params.add(pattern).add(pattern);
		}
		if (current) {
			filters.append(" AND (n.start IS NULL OR n.start <= CURRENT_DATE)"
					+ " AND (n.\"end\" IS NULL OR n.\"end\" >= CURRENT_DATE)");
		}
		return Db.sql("WITH RECURSIVE nodes AS (SELECT DISTINCT ON (i.identifier) i.identifier AS id, "
				+ "NULLIF(a.destination_id, i.document_id) AS parent, a.sequence_number AS seq, "
				+ "i.human_coding_scheme AS code, i.full_statement AS label, i.item_type AS type, "
				+ "i.education_levels AS levels, i.notes, i.status_start_date AS start, i.status_end_date AS \"end\" "
				+ "FROM casestd.item AS i LEFT JOIN casestd.association AS a ON a.origin_id = i.identifier "
				+ "AND a.document_id = i.document_id AND a.association_type = 'isChildOf' "
				+ "WHERE i.document_id = ? ORDER BY i.identifier, a.sequence_number NULLS LAST), "
				+ "up AS (SELECT n.id, n.parent FROM nodes AS n WHERE " + filters + " "
				+ "UNION SELECT n.id, n.parent FROM up JOIN nodes AS n ON n.id = up.parent) "
				// Des dates, pas des instants : mod-postgresql rendrait un DATE en horodatage avec fuseau.
				+ "SELECT n.id, n.parent, n.seq, n.code, n.label, n.type, n.levels, n.notes, "
				+ "to_char(n.start, 'YYYY-MM-DD') AS start, to_char(n.\"end\", 'YYYY-MM-DD') AS \"end\" "
				+ "FROM nodes AS n WHERE n.id IN (SELECT id FROM up) "
				+ "ORDER BY n.seq NULLS LAST, n.code NULLS LAST", params);
	}

	/** Des énoncés désignés un à un (les rattachements d'un pack), avec leur référentiel. */
	public Future<JsonArray> items(Collection<String> ids, boolean publishedOnly) {
		if (ids.isEmpty()) {
			return Future.succeededFuture(new JsonArray());
		}
		final JsonArray params = new JsonArray();
		ids.forEach(params::add);
		return Db.sql("SELECT i.identifier AS id, i.document_id AS document, d.title AS \"documentTitle\", "
				+ "i.human_coding_scheme AS code, i.full_statement AS label, i.item_type AS type, "
				+ "i.education_levels AS levels FROM casestd.item AS i "
				+ "JOIN casestd.document AS d ON d.identifier = i.document_id "
				+ "WHERE i.identifier IN (" + String.join(", ", Collections.nCopies(ids.size(), "?")) + ")"
				+ (publishedOnly ? " AND d.published" : ""), params);
	}
}
