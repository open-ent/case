package fr.openent.casestandards.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;

/** Accès PostgreSQL (mod-postgresql) sous forme de Future. */
public final class Db {

	private Db() {
	}

	public static Future<JsonArray> sql(String query, JsonArray params) {
		final Promise<JsonArray> promise = Promise.promise();
		Sql.getInstance().prepared(query, params, SqlResult.validResultHandler(either -> complete(promise, either)));
		return promise.future();
	}

	/** Première ligne, ou {@code null}. */
	public static Future<JsonObject> sqlOne(String query, JsonArray params) {
		return sql(query, params).map(rows -> rows.isEmpty() ? null : rows.getJsonObject(0));
	}

	/** Transaction : liste d'instructions préparées {statement, values}. */
	public static Future<JsonArray> transaction(JsonArray statements) {
		final Promise<JsonArray> promise = Promise.promise();
		Sql.getInstance().transaction(statements, message -> {
			final JsonObject body = message.body();
			if ("ok".equals(body.getString("status"))) {
				promise.complete(body.getJsonArray("results", new JsonArray()));
			} else {
				promise.fail(body.getString("message", "sql.transaction.error"));
			}
		});
		return promise.future();
	}

	public static JsonObject statement(String query, JsonArray values) {
		return new JsonObject().put("statement", query).put("values", values).put("action", "prepared");
	}

	private static void complete(Promise<JsonArray> promise, Either<String, JsonArray> either) {
		if (either.isRight()) {
			promise.complete(either.right().getValue());
		} else {
			promise.fail(either.left().getValue());
		}
	}

	/** Les colonnes JSONB peuvent être rendues sous forme de chaîne par mod-postgresql. */
	public static JsonArray jsonArray(JsonObject row, String key) {
		final Object value = row.getValue(key);
		if (value instanceof JsonArray) {
			return (JsonArray) value;
		}
		return value instanceof String ? new JsonArray((String) value) : new JsonArray();
	}

	public static String encode(Object json) {
		if (json instanceof JsonObject) {
			return ((JsonObject) json).encode();
		}
		if (json instanceof JsonArray) {
			return ((JsonArray) json).encode();
		}
		return null;
	}
}
