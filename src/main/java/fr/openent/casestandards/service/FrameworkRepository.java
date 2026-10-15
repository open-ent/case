package fr.openent.casestandards.service;

import fr.openent.casestandards.pkg.CasePackage;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Lecture et écriture des référentiels dans le schéma {@code casestd}. */
public class FrameworkRepository {

	private static final String DOCUMENT_COLUMNS = "identifier, title, creator, publisher, description, "
			+ "official_source_url, language, version, adoption_status, status_start_date, status_end_date, "
			+ "license_identifier, notes, subjects, published, source, source_uri, last_change, created_by, created_at";

	private static final String ITEM_COLUMNS = "identifier, document_id, full_statement, abbreviated_statement, "
			+ "human_coding_scheme, list_enumeration, alternative_label, item_type_id, item_type, education_levels, "
			+ "concept_keywords, language, notes, status_start_date, status_end_date, last_change";

	private static final String ASSOCIATION_COLUMNS = "identifier, document_id, association_type, origin_id, "
			+ "origin_uri, origin_title, destination_id, destination_uri, destination_title, sequence_number, "
			+ "grouping_id, last_change";

	// ------------------------------------------------------------------ lecture

	/** @param publishedOnly vrai pour un appel non authentifié : seuls les référentiels publiés. */
	public Future<JsonArray> documents(boolean publishedOnly) {
		return Db.sql("SELECT " + DOCUMENT_COLUMNS + " FROM casestd.document"
				+ (publishedOnly ? " WHERE published" : "") + " ORDER BY title", new JsonArray());
	}

	public Future<JsonObject> document(String identifier, boolean publishedOnly) {
		return Db.sqlOne("SELECT " + DOCUMENT_COLUMNS + " FROM casestd.document WHERE identifier = ?"
				+ (publishedOnly ? " AND published" : ""), new JsonArray().add(identifier));
	}

	public Future<JsonArray> items(String documentId) {
		return Db.sql("SELECT " + ITEM_COLUMNS + " FROM casestd.item WHERE document_id = ? "
				+ "ORDER BY human_coding_scheme NULLS LAST, list_enumeration NULLS LAST, identifier",
				new JsonArray().add(documentId));
	}

	public Future<JsonArray> associations(String documentId) {
		return Db.sql("SELECT " + ASSOCIATION_COLUMNS + " FROM casestd.association WHERE document_id = ? "
				+ "ORDER BY sequence_number NULLS LAST, identifier", new JsonArray().add(documentId));
	}

	public Future<JsonArray> definitions(String documentId) {
		return Db.sql("SELECT identifier, kind, document_id, data, last_change FROM casestd.definition "
				+ "WHERE document_id = ? ORDER BY kind, identifier", new JsonArray().add(documentId));
	}

	/**
	 * Un énoncé, avec l'état de publication de son référentiel.
	 *
	 * <p>La jointure n'est pas une commodité : sans elle, un énoncé d'un référentiel non publié
	 * serait lisible par son URI directe alors que son référentiel ne l'est pas.</p>
	 */
	public Future<JsonObject> item(String identifier, boolean publishedOnly) {
		return Db.sqlOne("SELECT i.identifier, i.document_id, i.full_statement, i.abbreviated_statement, "
				+ "i.human_coding_scheme, i.list_enumeration, i.alternative_label, i.item_type_id, i.item_type, "
				+ "i.education_levels, i.concept_keywords, i.language, i.notes, i.status_start_date, "
				+ "i.status_end_date, i.last_change FROM casestd.item i "
				+ "JOIN casestd.document d ON d.identifier = i.document_id "
				+ "WHERE i.identifier = ?" + (publishedOnly ? " AND d.published" : ""),
				new JsonArray().add(identifier));
	}

	public Future<JsonObject> association(String identifier, boolean publishedOnly) {
		return Db.sqlOne("SELECT a.identifier, a.document_id, a.association_type, a.origin_id, a.origin_uri, "
				+ "a.origin_title, a.destination_id, a.destination_uri, a.destination_title, a.sequence_number, "
				+ "a.grouping_id, a.last_change FROM casestd.association a "
				+ "JOIN casestd.document d ON d.identifier = a.document_id "
				+ "WHERE a.identifier = ?" + (publishedOnly ? " AND d.published" : ""),
				new JsonArray().add(identifier));
	}

	/** Les liens qui partent d'un énoncé ou qui y arrivent. */
	public Future<JsonArray> itemAssociations(String itemId, boolean publishedOnly) {
		return Db.sql("SELECT a.identifier, a.document_id, a.association_type, a.origin_id, a.origin_uri, "
				+ "a.origin_title, a.destination_id, a.destination_uri, a.destination_title, a.sequence_number, "
				+ "a.grouping_id, a.last_change FROM casestd.association a "
				+ "JOIN casestd.document d ON d.identifier = a.document_id "
				+ "WHERE (a.origin_id = ? OR a.destination_id = ?)" + (publishedOnly ? " AND d.published" : "")
				+ " ORDER BY a.sequence_number NULLS LAST, a.identifier",
				new JsonArray().add(itemId).add(itemId));
	}

	public Future<JsonObject> definition(String kind, String identifier, boolean publishedOnly) {
		return Db.sqlOne("SELECT f.identifier, f.kind, f.document_id, f.data, f.last_change "
				+ "FROM casestd.definition f LEFT JOIN casestd.document d ON d.identifier = f.document_id "
				+ "WHERE f.kind = ? AND f.identifier = ?"
				+ (publishedOnly ? " AND (f.document_id IS NULL OR d.published)" : ""),
				new JsonArray().add(kind).add(identifier));
	}

	// ------------------------------------------------------------------ écriture

	/**
	 * Enregistre un référentiel importé, en une transaction.
	 *
	 * <p>Le référentiel est remplacé en entier : ses énoncés, ses liens et ses définitions sont
	 * effacés puis réécrits. Un import partiel laisserait un référentiel dont on ne saurait plus
	 * dire de quelle version il est — et la version, dans CASE, est ce qui fait foi.</p>
	 */
	public Future<JsonArray> save(CasePackage.Imported imported, boolean published, String userId) {
		final String documentId = imported.documentId();
		final JsonArray statements = new JsonArray();
		statements.add(Db.statement("DELETE FROM casestd.document WHERE identifier = ?",
				new JsonArray().add(documentId)));

		final JsonObject document = imported.document;
		statements.add(Db.statement("INSERT INTO casestd.document (identifier, title, creator, publisher, "
				+ "description, official_source_url, language, version, adoption_status, status_start_date, "
				+ "status_end_date, license_identifier, notes, subjects, published, source, source_uri, "
				+ "created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::date, ?::date, ?, ?, ?::jsonb, ?, "
				+ "'import', ?, ?)",
				new JsonArray()
						.add(documentId)
						.add(document.getString("title"))
						.add(document.getString("creator"))
						.add(document.getValue("publisher"))
						.add(document.getValue("description"))
						.add(document.getValue("official_source_url"))
						.add(document.getString("language", "fr"))
						.add(document.getValue("version"))
						.add(document.getValue("adoption_status"))
						.add(document.getValue("status_start_date"))
						.add(document.getValue("status_end_date"))
						.add(document.getValue("license_identifier"))
						.add(document.getValue("notes"))
						.add(Db.encode(document.getJsonArray("subjects", new JsonArray())))
						.add(published)
						.add(document.getValue("source_uri"))
						.add(userId)));

		for (JsonObject item : imported.items) {
			statements.add(Db.statement("INSERT INTO casestd.item (identifier, document_id, full_statement, "
					+ "abbreviated_statement, human_coding_scheme, list_enumeration, alternative_label, "
					+ "item_type_id, item_type, education_levels, concept_keywords, language, notes, "
					+ "status_start_date, status_end_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, "
					+ "?::jsonb, ?, ?, ?::date, ?::date)",
					new JsonArray()
							.add(item.getString("identifier"))
							.add(item.getString("document_id"))
							.add(item.getString("full_statement"))
							.add(item.getValue("abbreviated_statement"))
							.add(item.getValue("human_coding_scheme"))
							.add(item.getValue("list_enumeration"))
							.add(item.getValue("alternative_label"))
							.add(item.getValue("item_type_id"))
							.add(item.getValue("item_type"))
							.add(Db.encode(item.getJsonArray("education_levels", new JsonArray())))
							.add(Db.encode(item.getJsonArray("concept_keywords", new JsonArray())))
							.add(item.getValue("language"))
							.add(item.getValue("notes"))
							.add(item.getValue("status_start_date"))
							.add(item.getValue("status_end_date"))));
		}

		for (JsonObject association : imported.associations) {
			statements.add(Db.statement("INSERT INTO casestd.association (identifier, document_id, "
					+ "association_type, origin_id, origin_uri, origin_title, destination_id, destination_uri, "
					+ "destination_title, sequence_number, grouping_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
					new JsonArray()
							.add(association.getString("identifier"))
							.add(association.getString("document_id"))
							.add(association.getString("association_type"))
							.add(association.getString("origin_id"))
							.add(association.getValue("origin_uri"))
							.add(association.getValue("origin_title"))
							.add(association.getString("destination_id"))
							.add(association.getValue("destination_uri"))
							.add(association.getValue("destination_title"))
							.add(association.getValue("sequence_number"))
							.add(association.getValue("grouping_id"))));
		}

		for (JsonObject definition : imported.definitions) {
			statements.add(Db.statement("INSERT INTO casestd.definition (identifier, kind, document_id, data) "
					+ "VALUES (?, ?, ?, ?::jsonb)",
					new JsonArray()
							.add(definition.getString("identifier"))
							.add(definition.getString("kind"))
							.add(definition.getString("document_id"))
							.add(Db.encode(definition.getJsonObject("data", new JsonObject())))));
		}
		return Db.transaction(statements);
	}

	public Future<JsonArray> delete(String identifier) {
		return Db.sql("DELETE FROM casestd.document WHERE identifier = ? RETURNING identifier",
				new JsonArray().add(identifier));
	}

	public Future<JsonObject> setPublished(String identifier, boolean published) {
		return Db.sqlOne("UPDATE casestd.document SET published = ?, last_change = NOW() "
				+ "WHERE identifier = ? RETURNING " + DOCUMENT_COLUMNS,
				new JsonArray().add(published).add(identifier));
	}

	public Future<JsonArray> logImport(String id, String documentId, String source, String sourceUri,
									   String status, JsonObject report, String userId) {
		return Db.sql("INSERT INTO casestd.import (id, document_id, source, source_uri, status, report, "
				+ "created_by, finished_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
				new JsonArray().add(id).add(documentId).add(source).add(sourceUri).add(status)
						.add(Db.encode(report)).add(userId));
	}

	public Future<JsonArray> imports(int limit) {
		return Db.sql("SELECT id, document_id, source, source_uri, status, report, created_by, created_at, "
				+ "finished_at FROM casestd.import ORDER BY created_at DESC LIMIT ?",
				new JsonArray().add(limit));
	}

	public static List<JsonObject> rows(JsonArray array) {
		final List<JsonObject> list = new ArrayList<JsonObject>();
		for (int i = 0; i < array.size(); i++) {
			list.add(array.getJsonObject(i));
		}
		return list;
	}
}
