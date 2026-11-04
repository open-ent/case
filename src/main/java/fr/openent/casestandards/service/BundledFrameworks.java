package fr.openent.casestandards.service;

import fr.openent.casestandards.CaseContext;
import fr.openent.casestandards.pkg.CasePackage;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Référentiels livrés avec le module, chargés au démarrage.
 *
 * <p>Les programmes officiels ne dépendent d'aucun établissement : ils sont la grille commune à
 * laquelle s'alignent les contenus et les badges de tous. Ils sont donc enregistrés au nom d'un
 * usager technique ({@code case.bundled-owner}, par défaut {@code open-ent-catalogue}, le même que
 * le catalogue d'exercices) et jamais au nom de la personne qui administre la plateforme.</p>
 *
 * <p>Un référentiel n'est réécrit que si son contenu a changé : l'empreinte du fichier est gardée
 * dans {@code source_uri}. Sa publication, elle, n'est fixée qu'au premier chargement — un
 * super administrateur qui l'a retiré ne le voit pas republié par une mise à jour du module.</p>
 */
public class BundledFrameworks {

	private static final Logger log = LoggerFactory.getLogger(BundledFrameworks.class);
	public static final String DEFAULT_OWNER = "open-ent-catalogue";
	private static final String[] DEFAULT_RESOURCES = {
			"referentiels/programmes-1d.json", "referentiels/programmes-college.json"};

	private final CaseContext ctx;
	private final FrameworkRepository repository;

	public BundledFrameworks(CaseContext ctx) {
		this.ctx = ctx;
		this.repository = ctx.frameworks;
	}

	/** Charge chaque référentiel livré ; l'échec de l'un ne bloque ni les autres ni le module. */
	public Future<Void> load() {
		final List<String> resources = new ArrayList<String>();
		final JsonArray configured = ctx.config.getJsonArray("bundled");
		if (configured != null) {
			for (Object value : configured) {
				resources.add(String.valueOf(value));
			}
		} else {
			for (String resource : DEFAULT_RESOURCES) {
				resources.add(resource);
			}
		}
		Future<Void> chain = Future.succeededFuture();
		for (String resource : resources) {
			chain = chain.compose(v -> loadOne(resource)
					.otherwise(error -> {
						log.error("[case] référentiel livré non chargé : " + resource, error);
						return null;
					}));
		}
		return chain;
	}

	private Future<Void> loadOne(String resource) {
		final String owner = ctx.config.getString("bundled-owner", DEFAULT_OWNER);
		return read(resource).compose(bytes -> {
			final String sourceUri = "bundled:" + resource + "#sha256=" + sha256(bytes);
			final CasePackage.Imported imported;
			try {
				imported = ctx.pkg.read(new JsonObject(new String(bytes, StandardCharsets.UTF_8)), sourceUri);
			} catch (Exception e) {
				return Future.failedFuture(e);
			}
			return repository.document(imported.documentId(), false).compose(existing -> {
				if (existing != null && sourceUri.equals(existing.getString("source_uri"))) {
					return Future.<Void>succeededFuture();
				}
				final boolean published = existing == null || existing.getBoolean("published", false);
				return repository.save(imported, published, owner)
						.compose(v -> repository.logImport(UUID.randomUUID().toString(), imported.documentId(),
								"bundled", sourceUri, "done", imported.report(), owner))
						.onSuccess(v -> log.info("[case] référentiel livré chargé : " + resource
								+ " (" + imported.items.size() + " énoncés)"))
						.mapEmpty();
			});
		});
	}

	private Future<byte[]> read(String resource) {
		final Promise<byte[]> promise = Promise.promise();
		ctx.vertx.executeBlocking(blocking -> {
			try (InputStream in = BundledFrameworks.class.getClassLoader().getResourceAsStream(resource)) {
				if (in == null) {
					blocking.fail("ressource absente du module : " + resource);
					return;
				}
				final ByteArrayOutputStream out = new ByteArrayOutputStream();
				final byte[] buffer = new byte[65536];
				int n;
				while ((n = in.read(buffer)) > 0) {
					out.write(buffer, 0, n);
				}
				blocking.complete(out.toByteArray());
			} catch (IOException e) {
				blocking.fail(e);
			}
		}, false, promise);
		return promise.future();
	}

	static String sha256(byte[] bytes) {
		try {
			final StringBuilder hex = new StringBuilder();
			for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
