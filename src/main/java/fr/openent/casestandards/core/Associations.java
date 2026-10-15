package fr.openent.casestandards.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Le vocabulaire des liens entre énoncés (CFAssociation).
 *
 * <p>Il tient en neuf valeurs, et c'est tout ce qu'un référentiel CASE sait dire d'une relation.
 * La hiérarchie elle-même en fait partie : <strong>un référentiel CASE n'a pas d'arbre</strong>,
 * il a des énoncés et des liens {@code isChildOf}. C'est la différence de fond avec le LSUN, où
 * le domaine contient ses composantes.</p>
 */
public final class Associations {

	/** Rattachement hiérarchique : c'est lui, et lui seul, qui donne l'arborescence. */
	public static final String IS_CHILD_OF = "isChildOf";
	public static final String IS_PEER_OF = "isPeerOf";
	public static final String IS_PART_OF = "isPartOf";
	public static final String EXACT_MATCH_OF = "exactMatchOf";
	public static final String PRECEDES = "precedes";
	public static final String IS_RELATED_TO = "isRelatedTo";
	public static final String REPLACED_BY = "replacedBy";
	public static final String HAS_SKILL_LEVEL = "hasSkillLevel";
	public static final String EXEMPLAR = "exemplar";

	public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
			IS_CHILD_OF, IS_PEER_OF, IS_PART_OF, EXACT_MATCH_OF, PRECEDES, IS_RELATED_TO,
			REPLACED_BY, HAS_SKILL_LEVEL, EXEMPLAR));

	private Associations() {
	}

	public static boolean isKnown(String type) {
		return type != null && ALL.contains(type);
	}
}
