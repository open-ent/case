package fr.openent.casestandards.controller;

import fr.openent.casestandards.CaseContext;
import fr.openent.casestandards.api.Http;
import fr.openent.casestandards.core.Associations;
import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.core.Uris;
import fr.openent.casestandards.pkg.CasePackage;
import fr.openent.casestandards.service.FrameworkRepository;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.SuperAdminFilter;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Administration des référentiels : réservée au super administrateur.
 *
 * <p>C'est le pendant protégé de {@link CaseController}. Tout ce qui écrit passe ici, et tout ce
 * qui n'est pas publié ne se voit qu'ici.</p>
 */
public class AdminController extends BaseController {

	private final CaseContext ctx;
	private final FrameworkRepository repository;

	public AdminController(CaseContext ctx) {
		this.ctx = ctx;
		this.repository = ctx.frameworks;
		this.pathPrefix = "/case";
	}

	/** Les adresses à communiquer à un consommateur, et le vocabulaire servi. */
	@Get("/admin/configuration")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void configuration(HttpServerRequest request) {
		renderJson(request, new JsonObject()
				.put("service", ctx.publicBase + Uris.PREFIX)
				.put("documents", ctx.publicBase + Uris.PREFIX + "/CFDocuments")
				.put("association_types", new JsonArray(new ArrayList<Object>(Associations.ALL)))
				.put("public_read", true));
	}

	/** Tous les référentiels, publiés ou non. */
	@Get("/admin/documents")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void documents(HttpServerRequest request) {
		repository.documents(false)
				.onSuccess(rows -> {
					final JsonArray documents = new JsonArray();
					for (int i = 0; i < rows.size(); i++) {
						final JsonObject row = rows.getJsonObject(i);
						documents.add(ctx.pkg.document(row)
								.put("published", row.getBoolean("published", false))
								.put("source", row.getString("source"))
								.put("sourceUri", row.getString("source_uri")));
					}
					renderJson(request, new JsonObject().put("CFDocuments", documents));
				})
				.onFailure(error -> Http.error(request, error));
	}

	/**
	 * Dépose un paquet CASE.
	 *
	 * <p>Le référentiel est importé <strong>non publié</strong> : on le relit, puis on le publie.
	 * L'inverse — publier d'office ce qu'un tiers vient de déposer — ferait d'un import raté une
	 * donnée publique.</p>
	 */
	@Post("/admin/documents/import")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void importPackage(HttpServerRequest request) {
		user(request).compose(user -> Http.json(request).compose(body -> {
			final String sourceUri = body.getString("sourceUri", request.params().get("sourceUri"));
			final JsonObject pkg = body.containsKey("CFDocument") ? body : body.getJsonObject("package");
			final CasePackage.Imported imported;
			try {
				imported = ctx.pkg.read(pkg, sourceUri);
			} catch (CaseException e) {
				return Future.<JsonObject>failedFuture(e);
			}
			final String importId = UUID.randomUUID().toString();
			return repository.save(imported, false, user.getUserId())
					.compose(v -> repository.logImport(importId, imported.documentId(), "upload", sourceUri,
							"done", imported.report(), user.getUserId()))
					.map(v -> new JsonObject()
							.put("importId", importId)
							.put("CFDocument", ctx.pkg.document(imported.document
									.copy()
									.put("last_change", "")))
							.put("report", imported.report()));
		}))
				.onSuccess(result -> Http.json(request, 201, result))
				.onFailure(error -> Http.error(request, error));
	}

	/** Publie ou retire un référentiel. Un référentiel retiré cesse d'être servi aux tiers. */
	@Put("/admin/documents/:id/publication")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void publication(HttpServerRequest request) {
		Http.json(request)
				.compose(body -> repository.setPublished(request.params().get("id"),
						body.getBoolean("published", Boolean.TRUE)))
				.onSuccess(row -> {
					if (row == null) {
						Http.error(request, CaseException.notFound("CFDocument"));
						return;
					}
					Http.json(request, 200, ctx.pkg.document(row)
							.put("published", row.getBoolean("published", false)));
				})
				.onFailure(error -> Http.error(request, error));
	}

	@Delete("/admin/documents/:id")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void delete(HttpServerRequest request) {
		repository.delete(request.params().get("id"))
				.onSuccess(rows -> {
					if (rows.isEmpty()) {
						Http.error(request, CaseException.notFound("CFDocument"));
						return;
					}
					Http.noContent(request);
				})
				.onFailure(error -> Http.error(request, error));
	}

	@Get("/admin/imports")
	@ResourceFilter(SuperAdminFilter.class)
	@SecuredAction(value = "", type = ActionType.RESOURCE)
	public void imports(HttpServerRequest request) {
		repository.imports(50)
				.onSuccess(rows -> renderJson(request, new JsonObject().put("imports", rows)))
				.onFailure(error -> Http.error(request, error));
	}

	private Future<UserInfos> user(HttpServerRequest request) {
		final io.vertx.core.Promise<UserInfos> promise = io.vertx.core.Promise.promise();
		UserUtils.getUserInfos(eb, request, user -> {
			if (user == null) {
				promise.fail(new CaseException(401, "unauthorized"));
			} else {
				promise.complete(user);
			}
		});
		return promise.future();
	}
}
