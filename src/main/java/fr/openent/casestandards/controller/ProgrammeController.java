package fr.openent.casestandards.controller;

import fr.openent.casestandards.CaseContext;
import fr.openent.casestandards.api.Http;
import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.service.Db;
import fr.openent.casestandards.service.FrameworkRepository;
import fr.openent.casestandards.service.ProgrammeRepository;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Consultation des programmes dans l'ENT : l'arbre d'un référentiel filtré par niveau et matière.
 *
 * <p>Ce n'est pas une route CASE : les routes normatives ne filtrent pas, et un consommateur CASE
 * reprend le paquet entier. Ici, on sert l'écran qui affiche le programme d'une classe. Comme
 * l'administration, seul le super administrateur voit les référentiels non publiés — c'est ce qui
 * lui permet de relire un import avant de le publier.</p>
 */
public class ProgrammeController extends BaseController {

	/** Ordre scolaire des niveaux, pour les rendre dans l'ordre d'une scolarité. */
	private static final List<String> LEVELS = Arrays.asList(
			"PS", "MS", "GS", "CP", "CE1", "CE2", "CM1", "CM2", "6E", "5E", "4E", "3E", "2DE", "1RE", "TLE");
	/** Au-delà, une demande d'énoncés n'est plus celle d'un pack. */
	private static final int MAX_ITEMS = 500;

	private final ProgrammeRepository repository = new ProgrammeRepository();
	private final FrameworkRepository frameworks;

	public ProgrammeController(CaseContext ctx) {
		this.frameworks = ctx.frameworks;
		this.pathPrefix = "/case";
	}

	@Get("/programmes")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void programmes(HttpServerRequest request) {
		publishedOnly(request)
				.compose(repository::programmes)
				.onSuccess(rows -> {
					final JsonArray programmes = new JsonArray();
					for (int i = 0; i < rows.size(); i++) {
						final JsonObject row = rows.getJsonObject(i);
						final JsonArray subjects = Db.jsonArray(row, "subjects");
						for (int j = 0; j < subjects.size(); j++) {
							final JsonObject subject = subjects.getJsonObject(j);
							subject.put("levels", sortLevels(subject.getJsonArray("levels", new JsonArray())));
						}
						programmes.add(new JsonObject()
								.put("id", row.getString("id"))
								.put("title", row.getString("title"))
								.put("version", row.getString("version"))
								.put("published", row.getBoolean("published", false))
								.put("levels", sortLevels(Db.jsonArray(row, "levels")))
								.put("subjects", subjects));
					}
					Http.json(request, 200, programmes);
				})
				.onFailure(error -> Http.error(request, error));
	}

	/** {@code ?level=CE1&subject=MAT&q=fraction&current=true} */
	@Get("/programmes/:id/tree")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void tree(HttpServerRequest request) {
		final String documentId = request.params().get("id");
		publishedOnly(request)
				.compose(publishedOnly -> frameworks.document(documentId, publishedOnly))
				.compose(document -> document == null
						? Future.<JsonArray>failedFuture(CaseException.notFound("CFDocument"))
						: repository.tree(documentId, param(request, "level"), param(request, "subject"),
								param(request, "q"), !"false".equals(request.params().get("current"))))
				.onSuccess(rows -> {
					for (int i = 0; i < rows.size(); i++) {
						final JsonObject row = rows.getJsonObject(i);
						row.put("levels", Db.jsonArray(row, "levels"));
					}
					Http.json(request, 200, rows);
				})
				.onFailure(error -> Http.error(request, error));
	}

	/** {@code ?ids=a,b,c} : identifiants d'énoncés, ou leurs URI ({@code urn:uuid:…}, {@code …/CFItems/…}). */
	@Get("/programmes/items")
	@SecuredAction(value = "", type = ActionType.AUTHENTICATED)
	public void items(HttpServerRequest request) {
		final Set<String> ids = new LinkedHashSet<>();
		final String raw = request.params().get("ids");
		if (raw != null) {
			for (String id : raw.split(",")) {
				final String trimmed = id.trim();
				final String identifier = trimmed.substring(Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf(':')) + 1);
				if (!identifier.isEmpty()) {
					ids.add(identifier);
				}
			}
		}
		if (ids.size() > MAX_ITEMS) {
			Http.error(request, new CaseException("too.many.items"));
			return;
		}
		publishedOnly(request)
				.compose(publishedOnly -> repository.items(ids, publishedOnly))
				.onSuccess(rows -> {
					for (int i = 0; i < rows.size(); i++) {
						final JsonObject row = rows.getJsonObject(i);
						row.put("levels", Db.jsonArray(row, "levels"));
					}
					Http.json(request, 200, rows);
				})
				.onFailure(error -> Http.error(request, error));
	}

	private Future<Boolean> publishedOnly(HttpServerRequest request) {
		final Promise<Boolean> promise = Promise.promise();
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				promise.fail(new CaseException(401, "unauthorized"));
			} else {
				promise.complete(!isSuperAdmin(user));
			}
		});
		return promise.future();
	}

	private static boolean isSuperAdmin(UserInfos user) {
		return user.getFunctions() != null && user.getFunctions().containsKey(DefaultFunctions.SUPER_ADMIN);
	}

	private static String param(HttpServerRequest request, String name) {
		final String value = request.params().get(name);
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static JsonArray sortLevels(JsonArray levels) {
		final JsonArray sorted = levels.copy();
		sorted.getList().sort((a, b) -> Integer.compare(rank(String.valueOf(a)), rank(String.valueOf(b))));
		return sorted;
	}

	private static int rank(String level) {
		final int index = LEVELS.indexOf(level);
		return index < 0 ? LEVELS.size() : index;
	}
}
