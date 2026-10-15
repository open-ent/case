package fr.openent.casestandards.pkg;

import fr.openent.casestandards.core.Associations;
import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.core.Uris;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conversion entre les lignes du schéma {@code casestd} et les objets de la norme CASE 1.1, dans
 * les deux sens.
 *
 * <p>Classe <strong>pure</strong> : aucune base, aucun Vert.x, aucun réseau. C'est ce qui permet
 * de l'éprouver sur un paquet réel sans rien déployer, et c'est là que se joue toute la fidélité
 * de l'échange.</p>
 *
 * <p>Deux propriétés de la norme commandent le reste du code.</p>
 *
 * <p><strong>Un référentiel CASE n'a pas d'arbre.</strong> Il a des énoncés et des liens
 * {@code isChildOf}. La hiérarchie n'est donc jamais stockée dans l'énoncé : elle se reconstruit
 * en parcourant les associations, et un énoncé peut parfaitement avoir deux parents.</p>
 *
 * <p><strong>Les identifiants sont conservés à l'import.</strong> Un {@code CFItem} est désigné
 * par son UUID partout où il est cité, y compris depuis un autre référentiel et depuis un autre
 * serveur ({@code exactMatchOf}). Les renuméroter à l'import couperait tous ces liens — c'est
 * exactement ce que CASE sert à éviter.</p>
 */
public final class CasePackage {

	private final Uris uris;

	public CasePackage(Uris uris) {
		this.uris = uris;
	}

	// ------------------------------------------------------------------ vers CASE

	public JsonObject document(JsonObject row) {
		final String identifier = row.getString("identifier");
		final JsonObject document = new JsonObject()
				.put("identifier", identifier)
				.put("uri", uris.document(identifier))
				.put("title", row.getString("title"))
				.put("creator", row.getString("creator"))
				.put("lastChangeDateTime", dateTime(row.getString("last_change")))
				.put("CFPackageURI", Uris.link(row.getString("title"), identifier, uris.pkg(identifier)));
		putIfPresent(document, "publisher", row.getString("publisher"));
		putIfPresent(document, "description", row.getString("description"));
		putIfPresent(document, "officialSourceURL", row.getString("official_source_url"));
		putIfPresent(document, "language", row.getString("language"));
		putIfPresent(document, "version", row.getString("version"));
		putIfPresent(document, "adoptionStatus", row.getString("adoption_status"));
		putIfPresent(document, "statusStartDate", date(row.getString("status_start_date")));
		putIfPresent(document, "statusEndDate", date(row.getString("status_end_date")));
		putIfPresent(document, "notes", row.getString("notes"));
		final JsonArray subjects = array(row, "subjects");
		if (!subjects.isEmpty()) {
			document.put("subject", subjects);
		}
		final String license = row.getString("license_identifier");
		if (license != null) {
			document.put("licenseURI", Uris.link(null, license, uris.definition("CFLicenses", license)));
		}
		return document;
	}

	public JsonObject item(JsonObject row) {
		final String identifier = row.getString("identifier");
		final String documentId = row.getString("document_id");
		final JsonObject item = new JsonObject()
				.put("identifier", identifier)
				.put("uri", uris.item(identifier))
				.put("lastChangeDateTime", dateTime(row.getString("last_change")))
				.put("CFDocumentURI", Uris.link(null, documentId, uris.document(documentId)))
				.put("fullStatement", row.getString("full_statement"));
		putIfPresent(item, "abbreviatedStatement", row.getString("abbreviated_statement"));
		putIfPresent(item, "humanCodingScheme", row.getString("human_coding_scheme"));
		putIfPresent(item, "listEnumeration", row.getString("list_enumeration"));
		putIfPresent(item, "alternativeLabel", row.getString("alternative_label"));
		putIfPresent(item, "CFItemType", row.getString("item_type"));
		putIfPresent(item, "language", row.getString("language"));
		putIfPresent(item, "notes", row.getString("notes"));
		putIfPresent(item, "statusStartDate", date(row.getString("status_start_date")));
		putIfPresent(item, "statusEndDate", date(row.getString("status_end_date")));
		final String itemType = row.getString("item_type_id");
		if (itemType != null) {
			item.put("CFItemTypeURI", Uris.link(row.getString("item_type"), itemType,
					uris.definition("CFItemTypes", itemType)));
		}
		final JsonArray levels = array(row, "education_levels");
		if (!levels.isEmpty()) {
			item.put("educationLevel", levels);
		}
		final JsonArray keywords = array(row, "concept_keywords");
		if (!keywords.isEmpty()) {
			item.put("conceptKeywords", keywords);
		}
		return item;
	}

	public JsonObject association(JsonObject row) {
		final String identifier = row.getString("identifier");
		final String documentId = row.getString("document_id");
		final JsonObject association = new JsonObject()
				.put("identifier", identifier)
				.put("uri", uris.association(identifier))
				.put("lastChangeDateTime", dateTime(row.getString("last_change")))
				.put("CFDocumentURI", Uris.link(null, documentId, uris.document(documentId)))
				.put("associationType", row.getString("association_type"))
				.put("originNodeURI", node(row, "origin"))
				.put("destinationNodeURI", node(row, "destination"));
		if (row.getValue("sequence_number") != null) {
			association.put("sequenceNumber", row.getInteger("sequence_number"));
		}
		final String grouping = row.getString("grouping_id");
		if (grouping != null) {
			association.put("CFAssociationGroupingURI", Uris.link(null, grouping,
					uris.definition("CFAssociationGroupings", grouping)));
		}
		return association;
	}

	/**
	 * Un bout de lien : l'URI stockée quand l'objet vit ailleurs, celle d'ici sinon.
	 *
	 * <p>C'est le seul endroit où se décide si un lien sort du serveur. Une association
	 * {@code exactMatchOf} vers le référentiel d'un éditeur n'a pas d'autre trace que cette
	 * URI.</p>
	 */
	private JsonObject node(JsonObject row, String side) {
		final String identifier = row.getString(side + "_id");
		final String uri = row.getString(side + "_uri");
		return Uris.link(row.getString(side + "_title"), identifier,
				uri != null ? uri : uris.item(identifier));
	}

	public JsonObject definition(JsonObject row) {
		final String identifier = row.getString("identifier");
		final String kind = row.getString("kind");
		final JsonObject data = row.getValue("data") instanceof JsonObject
				? row.getJsonObject("data")
				: new JsonObject(row.getString("data", "{}"));
		return data.copy()
				.put("identifier", identifier)
				.put("uri", uris.definition(kind, identifier))
				.put("lastChangeDateTime", dateTime(row.getString("last_change")));
	}

	/** Le paquet complet : le référentiel, ses définitions, ses énoncés et ses liens. */
	public JsonObject build(JsonObject documentRow, List<JsonObject> itemRows,
							List<JsonObject> associationRows, List<JsonObject> definitionRows) {
		final JsonArray items = new JsonArray();
		for (JsonObject row : itemRows) {
			items.add(item(row));
		}
		final JsonArray associations = new JsonArray();
		for (JsonObject row : associationRows) {
			associations.add(association(row));
		}
		final JsonObject definitions = new JsonObject();
		for (JsonObject row : definitionRows) {
			final String kind = row.getString("kind");
			definitions.put(kind, definitions.getJsonArray(kind, new JsonArray()).add(definition(row)));
		}
		return new JsonObject()
				.put("CFDocument", document(documentRow))
				.put("CFDefinitions", definitions)
				.put("CFItems", items)
				.put("CFAssociations", associations);
	}

	// ------------------------------------------------------------------ depuis CASE

	/** Ce qu'un paquet déposé donne une fois vérifié : des lignes prêtes à être insérées. */
	public static final class Imported {
		public final JsonObject document = new JsonObject();
		public final List<JsonObject> items = new ArrayList<JsonObject>();
		public final List<JsonObject> associations = new ArrayList<JsonObject>();
		public final List<JsonObject> definitions = new ArrayList<JsonObject>();
		public final JsonArray notices = new JsonArray();

		public String documentId() {
			return document.getString("identifier");
		}

		public JsonObject report() {
			return new JsonObject()
					.put("items", items.size())
					.put("associations", associations.size())
					.put("definitions", definitions.size())
					.put("notices", notices);
		}

		void notice(String code, String message) {
			notices.add(new JsonObject().put("code", code).put("message", message));
		}
	}

	/**
	 * Lit un paquet CASE déposé par un tiers.
	 *
	 * <p>Rien n'est inventé : ce que le paquet ne porte pas reste vide. Ce qu'il porte mais que la
	 * norme n'admet pas — un type d'association inconnu, un lien vers un énoncé absent — laisse
	 * une notice et n'est pas repris, plutôt que d'entrer en base et d'y produire un référentiel
	 * incohérent.</p>
	 *
	 * @param sourceUri adresse d'où le paquet a été repris, pour la traçabilité.
	 */
	public Imported read(JsonObject pkg, String sourceUri) throws CaseException {
		if (pkg == null) {
			throw new CaseException("package.empty");
		}
		final JsonObject source = pkg.getJsonObject("CFDocument");
		if (source == null) {
			throw new CaseException("package.without.document");
		}
		final String title = source.getString("title");
		if (title == null || title.trim().isEmpty()) {
			throw new CaseException("document.without.title");
		}

		final Imported imported = new Imported();
		final String documentId = identifier(source.getString("identifier"));
		imported.document
				.put("identifier", documentId)
				.put("title", title.trim())
				.put("creator", source.getString("creator", "inconnu"))
				.put("publisher", source.getString("publisher"))
				.put("description", source.getString("description"))
				.put("official_source_url", source.getString("officialSourceURL"))
				.put("language", source.getString("language", "fr"))
				.put("version", source.getString("version"))
				.put("adoption_status", source.getString("adoptionStatus"))
				.put("status_start_date", source.getString("statusStartDate"))
				.put("status_end_date", source.getString("statusEndDate"))
				.put("notes", source.getString("notes"))
				.put("subjects", subjects(source))
				.put("license_identifier", linkIdentifier(source.getJsonObject("licenseURI")))
				.put("source_uri", sourceUri);

		readDefinitions(pkg.getJsonObject("CFDefinitions"), documentId, imported);
		final Set<String> known = readItems(pkg.getJsonArray("CFItems"), documentId, imported);
		readAssociations(pkg.getJsonArray("CFAssociations"), documentId, known, imported);
		return imported;
	}

	private Set<String> readItems(JsonArray items, String documentId, Imported imported) {
		final Set<String> known = new LinkedHashSet<String>();
		if (items == null) {
			imported.notice("items.none", "Le paquet ne porte aucun énoncé.");
			return known;
		}
		final Map<String, JsonObject> byIdentifier = new LinkedHashMap<String, JsonObject>();
		for (int i = 0; i < items.size(); i++) {
			final JsonObject source = items.getJsonObject(i);
			if (source == null) {
				continue;
			}
			final String statement = source.getString("fullStatement");
			if (statement == null || statement.trim().isEmpty()) {
				// fullStatement est le seul champ obligatoire d'un CFItem : sans lui, l'énoncé ne
				// dit rien et n'a rien à faire dans un référentiel.
				imported.notice("item.without.statement",
						"Un énoncé sans fullStatement (" + source.getString("identifier")
								+ ") n'est pas repris.");
				continue;
			}
			final String identifier = identifier(source.getString("identifier"));
			if (byIdentifier.containsKey(identifier)) {
				imported.notice("item.duplicate",
						"L'énoncé " + identifier + " figure plusieurs fois : seul le premier est repris.");
				continue;
			}
			final JsonObject itemType = source.getJsonObject("CFItemTypeURI");
			final JsonObject row = new JsonObject()
					.put("identifier", identifier)
					.put("document_id", documentId)
					.put("full_statement", statement.trim())
					.put("abbreviated_statement", source.getString("abbreviatedStatement"))
					.put("human_coding_scheme", source.getString("humanCodingScheme"))
					.put("list_enumeration", source.getString("listEnumeration"))
					.put("alternative_label", source.getString("alternativeLabel"))
					.put("item_type_id", linkIdentifier(itemType))
					.put("item_type", source.getString("CFItemType",
							itemType == null ? null : itemType.getString("title")))
					.put("education_levels", source.getJsonArray("educationLevel", new JsonArray()))
					.put("concept_keywords", source.getJsonArray("conceptKeywords", new JsonArray()))
					.put("language", source.getString("language"))
					.put("notes", source.getString("notes"))
					.put("status_start_date", source.getString("statusStartDate"))
					.put("status_end_date", source.getString("statusEndDate"));
			byIdentifier.put(identifier, row);
			known.add(identifier);
		}
		imported.items.addAll(byIdentifier.values());
		return known;
	}

	private void readAssociations(JsonArray associations, String documentId, Set<String> knownItems,
								  Imported imported) {
		if (associations == null) {
			return;
		}
		final Set<String> seen = new LinkedHashSet<String>();
		for (int i = 0; i < associations.size(); i++) {
			final JsonObject source = associations.getJsonObject(i);
			if (source == null) {
				continue;
			}
			final String type = source.getString("associationType");
			if (!Associations.isKnown(type)) {
				imported.notice("association.unknown.type",
						"Le lien de type « " + type + " » n'appartient pas au vocabulaire de la "
								+ "norme : il n'est pas repris.");
				continue;
			}
			final JsonObject origin = source.getJsonObject("originNodeURI");
			final JsonObject destination = source.getJsonObject("destinationNodeURI");
			final String originId = linkIdentifier(origin);
			final String destinationId = linkIdentifier(destination);
			if (originId == null || destinationId == null) {
				imported.notice("association.incomplete",
						"Un lien sans origine ou sans destination n'est pas repris.");
				continue;
			}
			// Une destination hors du paquet est licite — c'est tout l'intérêt d'exactMatchOf —
			// mais une ORIGINE hors du paquet désigne un énoncé qu'on n'héberge pas : le lien
			// serait invisible, puisqu'on ne sert jamais son origine.
			if (!knownItems.contains(originId)) {
				imported.notice("association.origin.absent",
						"Le lien part de l'énoncé " + originId + ", absent du paquet : il n'est "
								+ "pas repris.");
				continue;
			}
			final String identifier = identifier(source.getString("identifier"));
			if (!seen.add(identifier)) {
				continue;
			}
			imported.associations.add(new JsonObject()
					.put("identifier", identifier)
					.put("document_id", documentId)
					.put("association_type", type)
					.put("origin_id", originId)
					.put("origin_uri", external(origin, knownItems))
					.put("origin_title", origin.getString("title"))
					.put("destination_id", destinationId)
					.put("destination_uri", external(destination, knownItems))
					.put("destination_title", destination.getString("title"))
					.put("sequence_number", source.getValue("sequenceNumber"))
					.put("grouping_id", linkIdentifier(source.getJsonObject("CFAssociationGroupingURI"))));
		}
	}

	/** @return l'URI à conserver, ou {@code null} si l'objet est servi par nous. */
	private String external(JsonObject link, Set<String> knownItems) {
		final String identifier = linkIdentifier(link);
		if (identifier != null && knownItems.contains(identifier)) {
			return null;
		}
		return link == null ? null : link.getString("uri");
	}

	private void readDefinitions(JsonObject definitions, String documentId, Imported imported) {
		if (definitions == null) {
			return;
		}
		for (String kind : definitions.fieldNames()) {
			final Object value = definitions.getValue(kind);
			if (!(value instanceof JsonArray)) {
				continue;
			}
			final JsonArray entries = (JsonArray) value;
			for (int i = 0; i < entries.size(); i++) {
				final JsonObject entry = entries.getJsonObject(i);
				if (entry == null) {
					continue;
				}
				final JsonObject data = entry.copy();
				data.remove("identifier");
				data.remove("uri");
				data.remove("lastChangeDateTime");
				imported.definitions.add(new JsonObject()
						.put("identifier", identifier(entry.getString("identifier")))
						.put("kind", kind)
						.put("document_id", documentId)
						.put("data", data));
			}
		}
	}

	// ------------------------------------------------------------------ utilitaires

	/**
	 * Identifiant repris tel quel, ou tiré au sort s'il manque.
	 *
	 * <p>Conserver celui du paquet est la règle : c'est lui que citent les autres référentiels, y
	 * compris sur d'autres serveurs. Le renuméroter couperait les correspondances que CASE sert
	 * précisément à établir.</p>
	 */
	public static String identifier(String declared) {
		if (declared == null || declared.trim().isEmpty()) {
			return UUID.randomUUID().toString();
		}
		return declared.trim();
	}

	public static String linkIdentifier(JsonObject link) {
		if (link == null) {
			return null;
		}
		final String identifier = link.getString("identifier");
		if (identifier != null && !identifier.trim().isEmpty()) {
			return identifier.trim();
		}
		// Un LinkURI peut ne porter que l'URI : l'identifiant est alors son dernier segment.
		final String uri = link.getString("uri");
		if (uri == null) {
			return null;
		}
		final int slash = uri.lastIndexOf('/');
		final String last = slash < 0 ? uri : uri.substring(slash + 1);
		return last.isEmpty() ? null : last;
	}

	private static JsonArray subjects(JsonObject document) {
		final Object value = document.getValue("subject");
		if (value instanceof JsonArray) {
			return (JsonArray) value;
		}
		return value instanceof String ? new JsonArray().add(value) : new JsonArray();
	}

	private static JsonArray array(JsonObject row, String key) {
		final Object value = row.getValue(key);
		if (value instanceof JsonArray) {
			return (JsonArray) value;
		}
		return value instanceof String ? new JsonArray((String) value) : new JsonArray();
	}

	private static void putIfPresent(JsonObject target, String key, String value) {
		if (value != null && !value.isEmpty()) {
			target.put(key, value);
		}
	}

	/**
	 * Horodatage ISO-8601.
	 *
	 * <p>mod-postgresql rend un {@code TIMESTAMP} sous la forme {@code 2026-10-15 23:19:00.000} ;
	 * la norme veut un {@code T} et pas de fraction de seconde.</p>
	 */
	static String dateTime(String timestamp) {
		if (timestamp == null || timestamp.isEmpty()) {
			return null;
		}
		String value = timestamp.trim().replace(' ', 'T');
		final int dot = value.indexOf('.');
		if (dot > 0) {
			value = value.substring(0, dot);
		}
		return value;
	}

	/** Date seule, sans la partie horaire qu'une colonne DATE peut traîner. */
	static String date(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed;
	}
}
