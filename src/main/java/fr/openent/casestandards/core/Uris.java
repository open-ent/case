package fr.openent.casestandards.core;

import io.vertx.core.json.JsonObject;

/**
 * Les URI d'un serveur CASE.
 *
 * <p>Chaque objet CASE porte une {@code uri} absolue, et les liens entre objets se font par
 * {@code LinkURI} — un petit objet {@code {title, identifier, uri}} plutôt qu'un identifiant nu.
 * C'est ce qui permet à un référentiel de citer l'énoncé d'un <em>autre</em> serveur : l'URI
 * suffit à aller le chercher, là où un identifiant seul n'aurait aucun sens hors de son hôte.</p>
 */
public final class Uris {

	public static final String PREFIX = "/ims/case/v1p0";

	private final String base;

	public Uris(String publicBase) {
		this.base = publicBase.endsWith("/") ? publicBase.substring(0, publicBase.length() - 1) : publicBase;
	}

	public String document(String identifier) {
		return base + PREFIX + "/CFDocuments/" + identifier;
	}

	public String pkg(String identifier) {
		return base + PREFIX + "/CFPackages/" + identifier;
	}

	public String item(String identifier) {
		return base + PREFIX + "/CFItems/" + identifier;
	}

	public String association(String identifier) {
		return base + PREFIX + "/CFAssociations/" + identifier;
	}

	public String definition(String kind, String identifier) {
		return base + PREFIX + "/" + kind + "/" + identifier;
	}

	/**
	 * Un {@code LinkURI}.
	 *
	 * @param uri URI absolue déjà connue — celle d'un objet d'un autre serveur — ou {@code null}
	 *            pour désigner un objet d'ici.
	 */
	public static JsonObject link(String title, String identifier, String uri) {
		if (identifier == null && uri == null) {
			return null;
		}
		final JsonObject link = new JsonObject();
		if (title != null) {
			link.put("title", title);
		}
		if (identifier != null) {
			link.put("identifier", identifier);
		}
		if (uri != null) {
			link.put("uri", uri);
		}
		return link;
	}
}
