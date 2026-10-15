package fr.openent.casestandards.controller;

import fr.openent.casestandards.CaseContext;
import fr.openent.casestandards.api.Http;
import fr.openent.casestandards.api.Query;
import fr.openent.casestandards.core.CaseException;
import fr.openent.casestandards.core.Fields;
import fr.openent.casestandards.core.Uris;
import fr.openent.casestandards.service.FrameworkRepository;
import fr.wseduc.rs.Get;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Le service CASE 1.1 proprement dit, sous {@code /ims/case/v1p0}.
 *
 * <p><strong>Ces routes sont publiques, et c'est délibéré.</strong> Elles ne portent pas
 * d'{@code @SecuredAction} — sans cette annotation, entcore ne les soumet à aucun filtre — parce
 * qu'un référentiel de compétences est une donnée publique et qu'un consommateur CASE est
 * précisément un tiers qui n'a pas de compte ici. C'est aussi ce que font les serveurs CASE
 * publics.</p>
 *
 * <p>La contrepartie est stricte : <strong>seuls les référentiels publiés sont servis</strong>.
 * Un référentiel en cours de rédaction n'existe pas pour ces routes, et il ne se voit que par
 * l'administration, elle protégée. La condition est portée par les requêtes elles-mêmes, jointure
 * comprise pour les énoncés et les liens : lire un énoncé par son URI directe ne doit pas
 * contourner l'état de publication de son référentiel.</p>
 */
public class CaseController extends BaseController {

	/** Les cinq genres de définition que la norme range sous leur propre route. */
	private static final String[] DEFINITION_KINDS = {
			"CFItemTypes", "CFLicenses", "CFSubjects", "CFConcepts", "CFAssociationGroupings"};

	private final CaseContext ctx;
	private final FrameworkRepository repository;

	public CaseController(CaseContext ctx) {
		this.ctx = ctx;
		this.repository = ctx.frameworks;
		this.pathPrefix = Uris.PREFIX;
	}

	// ---------------------------------------------------------------- référentiels

	@Get("/CFDocuments")
	public void documents(HttpServerRequest request) {
		list(request, "CFDocuments", Fields.DOCUMENT,
				repository.documents(true).map(rows -> convert(rows, ctx.pkg::document)));
	}

	@Get("/CFDocuments/:id")
	public void document(HttpServerRequest request) {
		one(request, Fields.DOCUMENT, "CFDocument",
				repository.document(id(request), true).map(row -> row == null ? null : ctx.pkg.document(row)));
	}

	/**
	 * Le paquet complet d'un référentiel : c'est par là qu'un consommateur le reprend en entier.
	 *
	 * <p>Jamais paginé, volontairement. Un paquet qui arriverait par tranches ne serait plus un
	 * paquet : son importateur n'aurait aucun moyen de savoir qu'il lui manque des liens.</p>
	 */
	@Get("/CFPackages/:id")
	public void pkg(HttpServerRequest request) {
		final String identifier = id(request);
		repository.document(identifier, true)
				.compose(document -> {
					if (document == null) {
						return Future.failedFuture(CaseException.notFound("CFDocument"));
					}
					return CompositeFuture.all(
							repository.items(identifier),
							repository.associations(identifier),
							repository.definitions(identifier))
							.map(results -> ctx.pkg.build(document,
									FrameworkRepository.rows(results.resultAt(0)),
									FrameworkRepository.rows(results.resultAt(1)),
									FrameworkRepository.rows(results.resultAt(2))));
				})
				.onSuccess(body -> Http.json(request, 200, body))
				.onFailure(error -> Http.error(request, error));
	}

	// ---------------------------------------------------------------- énoncés et liens

	@Get("/CFItems/:id")
	public void item(HttpServerRequest request) {
		one(request, Fields.ITEM, "CFItem",
				repository.item(id(request), true).map(row -> row == null ? null : ctx.pkg.item(row)));
	}

	@Get("/CFAssociations/:id")
	public void association(HttpServerRequest request) {
		one(request, Fields.ASSOCIATION, "CFAssociation",
				repository.association(id(request), true)
						.map(row -> row == null ? null : ctx.pkg.association(row)));
	}

	/**
	 * Les liens d'un énoncé, dans les deux sens.
	 *
	 * <p>Un lien n'est pas orienté du point de vue de la navigation : on veut aussi bien les
	 * enfants d'un domaine que le domaine d'une composante. Ne rendre que les liens sortants
	 * obligerait le consommateur à charger tout le référentiel pour remonter d'un cran.</p>
	 */
	@Get("/CFItemAssociations/:id")
	public void itemAssociations(HttpServerRequest request) {
		final String identifier = id(request);
		repository.item(identifier, true)
				.compose(item -> {
					if (item == null) {
						return Future.failedFuture(CaseException.notFound("CFItem"));
					}
					return repository.itemAssociations(identifier, true);
				})
				.map(rows -> convert(rows, ctx.pkg::association))
				.onSuccess(entities -> page(request, "CFAssociations", Fields.ASSOCIATION, entities))
				.onFailure(error -> Http.error(request, error));
	}

	// ---------------------------------------------------------------- définitions

	@Get("/CFItemTypes/:id")
	public void itemType(HttpServerRequest request) {
		definition(request, "CFItemTypes", "CFItemType");
	}

	@Get("/CFLicenses/:id")
	public void license(HttpServerRequest request) {
		definition(request, "CFLicenses", "CFLicense");
	}

	@Get("/CFSubjects/:id")
	public void subject(HttpServerRequest request) {
		definition(request, "CFSubjects", "CFSubject");
	}

	@Get("/CFConcepts/:id")
	public void concept(HttpServerRequest request) {
		definition(request, "CFConcepts", "CFConcept");
	}

	@Get("/CFAssociationGroupings/:id")
	public void associationGrouping(HttpServerRequest request) {
		definition(request, "CFAssociationGroupings", "CFAssociationGrouping");
	}

	private void definition(HttpServerRequest request, String kind, String what) {
		one(request, Fields.DEFINITION, what,
				repository.definition(kind, id(request), true)
						.map(row -> row == null ? null : ctx.pkg.definition(row)));
	}

	// ---------------------------------------------------------------- mécanique

	private void list(HttpServerRequest request, String key, Set<String> fields,
					  Future<List<JsonObject>> entities) {
		entities.onSuccess(items -> page(request, key, fields, items))
				.onFailure(error -> Http.error(request, error));
	}

	private void page(HttpServerRequest request, String key, Set<String> fields,
					  List<JsonObject> entities) {
		try {
			final Query query = Query.parse(request.params(), ctx.maxLimit);
			Http.page(request, ctx.publicBase, key, query.apply(entities, fields));
		} catch (CaseException e) {
			Http.error(request, e);
		}
	}

	private void one(HttpServerRequest request, Set<String> fields, String what,
					 Future<JsonObject> entity) {
		entity.onSuccess(found -> {
			if (found == null) {
				Http.error(request, CaseException.notFound(what));
				return;
			}
			try {
				Http.json(request, 200, Query.parse(request.params(), ctx.maxLimit).project(found, fields));
			} catch (CaseException e) {
				Http.error(request, e);
			}
		}).onFailure(error -> Http.error(request, error));
	}

	private static List<JsonObject> convert(JsonArray rows, java.util.function.Function<JsonObject, JsonObject> f) {
		final List<JsonObject> entities = new ArrayList<JsonObject>(rows.size());
		for (int i = 0; i < rows.size(); i++) {
			entities.add(f.apply(rows.getJsonObject(i)));
		}
		return entities;
	}

	private static String id(HttpServerRequest request) {
		return request.params().get("id");
	}
}
