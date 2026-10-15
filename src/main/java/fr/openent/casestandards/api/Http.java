package fr.openent.casestandards.api;

import fr.openent.casestandards.core.CaseException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Réponses HTTP de l'API CASE : enveloppes JSON, en-têtes de pagination, erreurs 1EdTech. */
public final class Http {

	private static final Logger log = LoggerFactory.getLogger(Http.class);
	/** Un paquet CASE complet se compte en mégaoctets ; au-delà, c'est un dépôt fautif. */
	private static final int MAX_BODY = 32 * 1024 * 1024;

	private Http() {
	}

	public static Future<JsonObject> json(HttpServerRequest request) {
		final Promise<JsonObject> promise = Promise.promise();
		request.exceptionHandler(promise::tryFail);
		request.bodyHandler(buffer -> {
			if (buffer.length() > MAX_BODY) {
				promise.tryFail(new CaseException(413, "body.too.large"));
				return;
			}
			try {
				promise.tryComplete(new JsonObject(buffer.toString("UTF-8")));
			} catch (RuntimeException e) {
				promise.tryFail(new CaseException("invalid.json"));
			}
		});
		resume(request);
		return promise.future();
	}

	public static void json(HttpServerRequest request, int status, Object body) {
		final String payload = body instanceof JsonArray
				? ((JsonArray) body).encode() : ((JsonObject) body).encode();
		request.response()
				.setStatusCode(status)
				.putHeader("Content-Type", "application/json; charset=utf-8")
				.putHeader("Cache-Control", "no-store")
				.end(payload);
	}

	public static void noContent(HttpServerRequest request) {
		request.response().setStatusCode(204).end();
	}

	/** Collection : {@code {"<clé>": [...]}}, avec X-Total-Count et Link. */
	public static void page(HttpServerRequest request, String publicBase, String key, Query.Page page) {
		final JsonArray items = new JsonArray();
		for (JsonObject item : page.items) {
			items.add(item);
		}
		request.response().putHeader("X-Total-Count", String.valueOf(page.total));
		final String links = links(publicBase + request.path(), request.params(), page);
		if (!links.isEmpty()) {
			request.response().putHeader("Link", links);
		}
		json(request, 200, new JsonObject().put(key, items));
	}

	static String links(String url, MultiMap params, Query.Page page) {
		if (page.total == 0 || page.limit <= 0) {
			return "";
		}
		final List<String> links = new ArrayList<String>();
		final int lastOffset = ((page.total - 1) / page.limit) * page.limit;
		links.add(link(url, params, page.limit, 0, "first"));
		if (page.offset > 0) {
			links.add(link(url, params, page.limit, Math.max(0, page.offset - page.limit), "prev"));
		}
		if ((long) page.offset + page.limit < page.total) {
			links.add(link(url, params, page.limit, page.offset + page.limit, "next"));
		}
		links.add(link(url, params, page.limit, lastOffset, "last"));
		return String.join(",", links);
	}

	private static String link(String url, MultiMap params, int limit, int offset, String rel) {
		final StringBuilder sb = new StringBuilder("<").append(url)
				.append("?limit=").append(limit).append("&offset=").append(offset);
		for (Map.Entry<String, String> entry : params) {
			if (!"limit".equals(entry.getKey()) && !"offset".equals(entry.getKey())) {
				sb.append('&').append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
			}
		}
		return sb.append(">; rel=\"").append(rel).append('"').toString();
	}

	private static String encode(String value) {
		try {
			return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Erreur au format imsx_StatusInfo, comme les autres services 1EdTech de l'ENT. */
	public static void error(HttpServerRequest request, Throwable error) {
		final CaseException e;
		if (error instanceof CaseException) {
			e = (CaseException) error;
		} else {
			log.error("[CASE] Unexpected error", error);
			e = new CaseException(500, "server_error");
		}
		if (e.getStatus() == 401) {
			request.response().putHeader("WWW-Authenticate", "Bearer");
		}
		json(request, e.getStatus(), statusInfo(e));
	}

	public static JsonObject statusInfo(CaseException e) {
		return new JsonObject()
				.put("imsx_codeMajor", "failure")
				.put("imsx_severity", "error")
				.put("imsx_description", e.getMessage())
				.put("imsx_CodeMinor", new JsonObject().put("imsx_codeMinorField",
						new JsonArray().add(new JsonObject()
								.put("imsx_codeMinorFieldName", "TargetEndSystem")
								.put("imsx_codeMinorFieldValue", e.getCodeMinor()))));
	}

	private static void resume(HttpServerRequest request) {
		try {
			request.resume();
		} catch (Exception ignored) {
			// déjà consommé
		}
	}
}
