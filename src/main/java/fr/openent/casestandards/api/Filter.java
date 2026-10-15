package fr.openent.casestandards.api;

import fr.openent.casestandards.core.CaseException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Le paramètre {@code filter} des services 1EdTech.
 *
 * <p>Grammaire : des prédicats {@code champ opérateur 'valeur'} combinés par {@code AND} et
 * {@code OR}, éventuellement entre parenthèses. {@code AND} lie plus fort que {@code OR}.</p>
 *
 * <p>Un champ inconnu est une <strong>erreur</strong>, jamais un filtre qui ne retient rien : un
 * consommateur qui se trompe de nom de champ doit l'apprendre, pas recevoir une collection vide
 * qu'il prendra pour une réponse.</p>
 */
public final class Filter {

	/** Filtre qui laisse tout passer : c'est l'absence de {@code filter}. */
	private static final Filter ALL = new Filter(null);

	private final Node root;

	private Filter(Node root) {
		this.root = root;
	}

	public static Filter parse(String expression, Set<String> knownFields) throws CaseException {
		if (expression == null || expression.trim().isEmpty()) {
			return ALL;
		}
		final Parser parser = new Parser(expression, knownFields);
		final Node node = parser.parseOr();
		parser.expectEnd();
		return new Filter(node);
	}

	public boolean matches(JsonObject entity) {
		return root == null || root.matches(entity);
	}

	// ------------------------------------------------------------------ arbre

	private interface Node {
		boolean matches(JsonObject entity);
	}

	private static final class And implements Node {
		private final Node left;
		private final Node right;

		And(Node left, Node right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean matches(JsonObject entity) {
			return left.matches(entity) && right.matches(entity);
		}
	}

	private static final class Or implements Node {
		private final Node left;
		private final Node right;

		Or(Node left, Node right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public boolean matches(JsonObject entity) {
			return left.matches(entity) || right.matches(entity);
		}
	}

	private static final class Predicate implements Node {
		private final String path;
		private final String operator;
		private final String value;

		Predicate(String path, String operator, String value) {
			this.path = path;
			this.operator = operator;
			this.value = value;
		}

		@Override
		public boolean matches(JsonObject entity) {
			final List<Object> values = resolve(entity, path);
			if (values.isEmpty()) {
				// Un champ absent ne vaut pas la valeur cherchée — sauf si on cherchait à ce
				// qu'il en diffère, ce qui est vrai.
				return "!=".equals(operator);
			}
			for (Object candidate : values) {
				if (test(candidate)) {
					return true;
				}
			}
			return false;
		}

		private boolean test(Object candidate) {
			final String text = String.valueOf(candidate);
			if ("~".equals(operator)) {
				return text.toLowerCase().contains(value.toLowerCase());
			}
			final int comparison = compare(candidate, value);
			if ("=".equals(operator)) {
				return comparison == 0;
			}
			if ("!=".equals(operator)) {
				return comparison != 0;
			}
			if (">".equals(operator)) {
				return comparison > 0;
			}
			if (">=".equals(operator)) {
				return comparison >= 0;
			}
			if ("<".equals(operator)) {
				return comparison < 0;
			}
			return comparison <= 0;
		}
	}

	// ------------------------------------------------------------------ analyse

	private static final class Parser {

		private final String input;
		private final Set<String> knownFields;
		private int index;

		Parser(String input, Set<String> knownFields) {
			this.input = input;
			this.knownFields = knownFields;
		}

		Node parseOr() throws CaseException {
			Node node = parseAnd();
			while (keyword("OR")) {
				node = new Or(node, parseAnd());
			}
			return node;
		}

		Node parseAnd() throws CaseException {
			Node node = parseTerm();
			while (keyword("AND")) {
				node = new And(node, parseTerm());
			}
			return node;
		}

		Node parseTerm() throws CaseException {
			skipSpaces();
			if (peek() == '(') {
				index++;
				final Node node = parseOr();
				skipSpaces();
				if (peek() != ')') {
					throw new CaseException("filter.unbalanced.parenthesis");
				}
				index++;
				return node;
			}
			return parsePredicate();
		}

		Node parsePredicate() throws CaseException {
			skipSpaces();
			final int start = index;
			while (index < input.length() && isFieldChar(input.charAt(index))) {
				index++;
			}
			final String field = input.substring(start, index).trim();
			if (field.isEmpty()) {
				throw new CaseException("filter.missing.field");
			}
			if (!knownFields.contains(field.split("\\.")[0])) {
				throw new CaseException(400, CaseException.INVALID_FILTER_FIELD,
						"invalid.filter.field:" + field);
			}
			final String operator = operator();
			final String value = value();
			return new Predicate(field, operator, value);
		}

		private String operator() throws CaseException {
			skipSpaces();
			for (String candidate : new String[]{">=", "<=", "!=", "=", ">", "<", "~"}) {
				if (input.startsWith(candidate, index)) {
					index += candidate.length();
					return candidate;
				}
			}
			throw new CaseException("filter.missing.operator");
		}

		private String value() throws CaseException {
			skipSpaces();
			final char quote = peek();
			if (quote == '\'' || quote == '"') {
				index++;
				final int start = index;
				while (index < input.length() && input.charAt(index) != quote) {
					index++;
				}
				if (index >= input.length()) {
					throw new CaseException("filter.unterminated.value");
				}
				final String value = input.substring(start, index);
				index++;
				return value;
			}
			final int start = index;
			while (index < input.length() && !Character.isWhitespace(input.charAt(index))
					&& input.charAt(index) != ')') {
				index++;
			}
			if (start == index) {
				throw new CaseException("filter.missing.value");
			}
			return input.substring(start, index);
		}

		/** Consomme le mot-clé s'il est là, sans rien consommer sinon. */
		private boolean keyword(String word) {
			final int saved = index;
			skipSpaces();
			if (input.regionMatches(true, index, word, 0, word.length())) {
				final int after = index + word.length();
				if (after >= input.length() || !isFieldChar(input.charAt(after))) {
					index = after;
					return true;
				}
			}
			index = saved;
			return false;
		}

		void expectEnd() throws CaseException {
			skipSpaces();
			if (index < input.length()) {
				throw new CaseException("filter.unexpected:" + input.substring(index));
			}
		}

		private void skipSpaces() {
			while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
				index++;
			}
		}

		private char peek() {
			return index < input.length() ? input.charAt(index) : '\0';
		}

		private static boolean isFieldChar(char c) {
			return Character.isLetterOrDigit(c) || c == '.' || c == '_';
		}
	}

	// ------------------------------------------------------------------ valeurs

	/**
	 * Résout un chemin pointé dans une entité, en traversant les tableaux.
	 *
	 * <p>{@code subject} rend chaque sujet d'un référentiel, {@code CFItemType.title} le titre du
	 * type. Un filtre porte donc sur « au moins une des valeurs ».</p>
	 */
	public static List<Object> resolve(JsonObject entity, String path) {
		List<Object> current = new ArrayList<Object>();
		current.add(entity);
		for (String segment : path.split("\\.")) {
			final List<Object> next = new ArrayList<Object>();
			for (Object value : current) {
				if (value instanceof JsonObject) {
					final Object child = ((JsonObject) value).getValue(segment);
					if (child instanceof JsonArray) {
						next.addAll(((JsonArray) child).getList());
					} else if (child != null) {
						next.add(child);
					}
				}
			}
			current = next;
		}
		final List<Object> leaves = new ArrayList<Object>();
		for (Object value : current) {
			if (!(value instanceof JsonObject) && !(value instanceof JsonArray)) {
				leaves.add(value);
			}
		}
		return leaves;
	}

	/** Compare en nombres quand les deux côtés en sont, en texte sinon. */
	public static int compare(Object candidate, String value) {
		if (candidate instanceof Number) {
			try {
				return Double.compare(((Number) candidate).doubleValue(), Double.parseDouble(value));
			} catch (NumberFormatException ignored) {
				return String.valueOf(candidate).compareTo(value);
			}
		}
		if (candidate instanceof Boolean) {
			return Boolean.compare((Boolean) candidate, Boolean.parseBoolean(value));
		}
		return String.valueOf(candidate).compareTo(value);
	}
}
