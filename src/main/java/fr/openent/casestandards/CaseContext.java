package fr.openent.casestandards;

import fr.openent.casestandards.core.Uris;
import fr.openent.casestandards.pkg.CasePackage;
import fr.openent.casestandards.service.FrameworkRepository;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;

/** Dépendances partagées par les contrôleurs du module. */
public class CaseContext {

	public final Vertx vertx;
	public final EventBus eb;
	public final JsonObject config;
	/** Origine publique de l'ENT, qui préfixe toutes les URI servies. */
	public final String publicBase;
	public final int maxLimit;
	public final Uris uris;
	public final CasePackage pkg;
	public final FrameworkRepository frameworks = new FrameworkRepository();

	public CaseContext(Vertx vertx, EventBus eb, JsonObject config) {
		final JsonObject settings = config.getJsonObject("case", new JsonObject());
		this.vertx = vertx;
		this.eb = eb;
		this.config = settings;
		this.publicBase = stripTrailingSlash(settings.getString("issuer",
				config.getString("host", "http://localhost:8090")));
		this.maxLimit = settings.getInteger("max-limit", 10000);
		this.uris = new Uris(publicBase);
		this.pkg = new CasePackage(uris);
	}

	private static String stripTrailingSlash(String value) {
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}
}
