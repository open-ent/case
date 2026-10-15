package fr.openent.casestandards.api;

import fr.openent.casestandards.core.CaseException;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Paramètres de lecture d'une collection CASE : {@code limit}, {@code offset}, {@code sort},
 * {@code orderBy}, {@code filter} et {@code fields}.
 *
 * <p>Tout s'applique en mémoire, sur les entités déjà converties au format CASE : les noms de
 * champs sont donc ceux de la norme et non ceux des colonnes. Un référentiel tient en quelques
 * milliers d'énoncés — le filtrer en base obligerait à traduire la grammaire du {@code filter}
 * en SQL, pour un gain qui ne se verrait pas.</p>
 */
public final class Query {

	public static final int DEFAULT_LIMIT = 100;

	private final int limit;
	private final int offset;
	private final String sort;
	private final boolean descending;
	private final String filterExpression;
	private final List<String> fields;

	public Query(int limit, int offset, String sort, boolean descending, String filterExpression,
				 List<String> fields) {
		this.limit = limit;
		this.offset = offset;
		this.sort = sort;
		this.descending = descending;
		this.filterExpression = filterExpression;
		this.fields = fields;
	}

	public static Query parse(MultiMap params, int maxLimit) throws CaseException {
		final int limit = integer(params.get("limit"), DEFAULT_LIMIT, 1);
		final int offset = integer(params.get("offset"), 0, 0);
		final String orderBy = params.get("orderBy");
		if (orderBy != null && !"asc".equalsIgnoreCase(orderBy) && !"desc".equalsIgnoreCase(orderBy)) {
			throw new CaseException("invalid.orderBy");
		}
		final List<String> fields = new ArrayList<String>();
		for (String value : params.getAll("fields")) {
			for (String field : value.split(",")) {
				if (!field.trim().isEmpty() && !fields.contains(field.trim())) {
					fields.add(field.trim());
				}
			}
		}
		return new Query(Math.min(limit, maxLimit), offset, blankToNull(params.get("sort")),
				"desc".equalsIgnoreCase(orderBy), params.get("filter"), fields);
	}

	/** Requête « tout » : l'export d'un paquet n'est pas paginé. */
	public static Query everything() {
		return new Query(Integer.MAX_VALUE, 0, null, false, null, Collections.<String>emptyList());
	}

	public int getLimit() {
		return limit;
	}

	public int getOffset() {
		return offset;
	}

	public Page apply(List<JsonObject> entities, Set<String> knownFields) throws CaseException {
		final Filter filter = Filter.parse(filterExpression, knownFields);
		final List<JsonObject> selected = new ArrayList<JsonObject>();
		for (JsonObject entity : entities) {
			if (filter.matches(entity)) {
				selected.add(entity);
			}
		}
		if (sort != null) {
			if (!knownFields.contains(sort.split("\\.")[0])) {
				throw new CaseException(400, CaseException.INVALID_SORT_FIELD, "invalid.sort.field:" + sort);
			}
			final Comparator<JsonObject> comparator = byField(sort);
			Collections.sort(selected, descending ? Collections.reverseOrder(comparator) : comparator);
		}
		final int total = selected.size();
		final int from = Math.min(offset, total);
		final int to = (int) Math.min((long) from + limit, total);
		final List<JsonObject> items = new ArrayList<JsonObject>(to - from);
		for (JsonObject entity : selected.subList(from, to)) {
			items.add(project(entity, knownFields));
		}
		return new Page(items, total, limit, offset);
	}

	/** Projection d'une entité seule (lecture par identifiant). */
	public JsonObject project(JsonObject entity, Set<String> knownFields) {
		if (fields.isEmpty()) {
			return entity;
		}
		final JsonObject projected = new JsonObject();
		for (String field : fields) {
			// Un champ inconnu dans « fields » est ignoré, là où il serait une erreur dans
			// « filter » : demander un champ de trop n'induit personne en erreur.
			if (knownFields.contains(field) && entity.containsKey(field)) {
				projected.put(field, entity.getValue(field));
			}
		}
		return projected;
	}

	private static Comparator<JsonObject> byField(final String path) {
		return new Comparator<JsonObject>() {
			@Override
			public int compare(JsonObject a, JsonObject b) {
				final List<Object> left = Filter.resolve(a, path);
				final List<Object> right = Filter.resolve(b, path);
				if (left.isEmpty() || right.isEmpty()) {
					// Les entités sans valeur passent en dernier, quel que soit le sens du tri.
					return Boolean.compare(left.isEmpty(), right.isEmpty());
				}
				return Filter.compare(left.get(0), String.valueOf(right.get(0)));
			}
		};
	}

	private static int integer(String value, int defaultValue, int min) throws CaseException {
		if (value == null || value.trim().isEmpty()) {
			return defaultValue;
		}
		try {
			final int parsed = Integer.parseInt(value.trim());
			if (parsed < min) {
				throw new CaseException("invalid.pagination");
			}
			return parsed;
		} catch (NumberFormatException e) {
			throw new CaseException("invalid.pagination");
		}
	}

	private static String blankToNull(String value) {
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	/** Page de résultats et nombre total d'entités correspondant au filtre. */
	public static final class Page {
		public final List<JsonObject> items;
		public final int total;
		public final int limit;
		public final int offset;

		Page(List<JsonObject> items, int total, int limit, int offset) {
			this.items = items;
			this.total = total;
			this.limit = limit;
			this.offset = offset;
		}
	}
}
