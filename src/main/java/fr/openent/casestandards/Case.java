package fr.openent.casestandards;

import fr.openent.casestandards.controller.AdminController;
import fr.openent.casestandards.controller.CaseController;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import org.entcore.common.http.BaseServer;

/**
 * Module CASE 1.1 (1EdTech) : Open ENT serveur de référentiels de compétences, et consommateur
 * de paquets CASE publiés par des tiers.
 */
public class Case extends BaseServer {

	@Override
	public void start(final Promise<Void> startPromise) throws Exception {
		final Promise<Void> promise = Promise.promise();
		super.start(promise);
		promise.future()
				.compose(v -> initCase(new CaseContext(vertx, vertx.eventBus(), config)))
				.onComplete(startPromise);
	}

	private Future<Void> initCase(CaseContext context) {
		return addController(new CaseController(context))
				.compose(v -> addController(new AdminController(context)))
				.mapEmpty();
	}
}
