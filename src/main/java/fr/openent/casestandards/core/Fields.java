package fr.openent.casestandards.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Les champs que chaque entité CASE expose.
 *
 * <p>Ils servent à vérifier {@code filter}, {@code sort} et {@code fields} : un champ inconnu
 * dans un filtre ou un tri est une erreur — sinon le consommateur croirait filtrer alors qu'il
 * ne filtre rien — tandis qu'un champ inconnu dans une projection est simplement ignoré.</p>
 */
public final class Fields {

	public static final Set<String> DOCUMENT = immutable(
			"identifier", "uri", "creator", "title", "lastChangeDateTime", "officialSourceURL",
			"publisher", "description", "subject", "subjectURI", "language", "version",
			"adoptionStatus", "statusStartDate", "statusEndDate", "licenseURI", "notes",
			"CFPackageURI");

	public static final Set<String> ITEM = immutable(
			"identifier", "uri", "lastChangeDateTime", "CFDocumentURI", "humanCodingScheme",
			"listEnumeration", "abbreviatedStatement", "fullStatement", "alternativeLabel",
			"CFItemTypeURI", "CFItemType", "notes", "language", "educationLevel", "conceptKeywords",
			"licenseURI", "statusStartDate", "statusEndDate");

	public static final Set<String> ASSOCIATION = immutable(
			"identifier", "uri", "lastChangeDateTime", "CFDocumentURI", "originNodeURI",
			"associationType", "destinationNodeURI", "sequenceNumber", "CFAssociationGroupingURI");

	public static final Set<String> DEFINITION = immutable(
			"identifier", "uri", "lastChangeDateTime", "title", "description", "hierarchyCode",
			"typeCode", "code", "url");

	private Fields() {
	}

	private static Set<String> immutable(String... names) {
		return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(names)));
	}
}
