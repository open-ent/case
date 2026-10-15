-- Schéma « casestd » et non « case » : CASE est un mot réservé de SQL, et un schéma qui porte ce
-- nom doit être cité entre guillemets dans chaque requête — une erreur attend à chaque oubli.
CREATE SCHEMA IF NOT EXISTS casestd;

CREATE TABLE casestd.scripts (
	filename VARCHAR(255) NOT NULL PRIMARY KEY,
	passed TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Un référentiel (CFDocument) : le socle commun, un programme, un référentiel d'éditeur.
CREATE TABLE casestd.document (
	identifier VARCHAR(36) NOT NULL PRIMARY KEY,
	title TEXT NOT NULL,
	creator TEXT NOT NULL,
	publisher TEXT,
	description TEXT,
	official_source_url TEXT,
	language VARCHAR(16) NOT NULL DEFAULT 'fr',
	version VARCHAR(64),
	adoption_status VARCHAR(32),
	status_start_date DATE,
	status_end_date DATE,
	license_identifier VARCHAR(36),
	notes TEXT,
	subjects JSONB NOT NULL DEFAULT '[]',
	-- Un référentiel publié est lisible sans authentification sur les routes normatives : c'est
	-- ce qu'attend un consommateur CASE, et un référentiel de programmes est une donnée publique.
	published BOOLEAN NOT NULL DEFAULT FALSE,
	-- « local » : saisi dans l'ENT. « import » : repris d'un paquet CASE d'un tiers.
	source VARCHAR(16) NOT NULL DEFAULT 'local',
	source_uri TEXT,
	last_change TIMESTAMP NOT NULL DEFAULT NOW(),
	created_by VARCHAR(36),
	created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX document_published ON casestd.document (published, last_change DESC);

-- Un énoncé de compétence (CFItem).
CREATE TABLE casestd.item (
	identifier VARCHAR(36) NOT NULL PRIMARY KEY,
	document_id VARCHAR(36) NOT NULL REFERENCES casestd.document(identifier) ON DELETE CASCADE,
	full_statement TEXT NOT NULL,
	abbreviated_statement TEXT,
	human_coding_scheme TEXT,
	list_enumeration TEXT,
	alternative_label TEXT,
	item_type_id VARCHAR(36),
	item_type TEXT,
	education_levels JSONB NOT NULL DEFAULT '[]',
	concept_keywords JSONB NOT NULL DEFAULT '[]',
	language VARCHAR(16),
	notes TEXT,
	status_start_date DATE,
	status_end_date DATE,
	last_change TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX item_document ON casestd.item (document_id);
CREATE INDEX item_coding ON casestd.item (document_id, human_coding_scheme);

-- Un lien entre deux énoncés (CFAssociation). L'origine ou la destination peuvent désigner un
-- énoncé d'un AUTRE référentiel, y compris hébergé ailleurs : d'où l'URI et le titre conservés
-- à côté de l'identifiant, sans contrainte de clé étrangère.
CREATE TABLE casestd.association (
	identifier VARCHAR(36) NOT NULL PRIMARY KEY,
	document_id VARCHAR(36) NOT NULL REFERENCES casestd.document(identifier) ON DELETE CASCADE,
	association_type VARCHAR(32) NOT NULL,
	origin_id VARCHAR(36) NOT NULL,
	origin_uri TEXT,
	origin_title TEXT,
	destination_id VARCHAR(36) NOT NULL,
	destination_uri TEXT,
	destination_title TEXT,
	sequence_number INTEGER,
	grouping_id VARCHAR(36),
	last_change TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX association_document ON casestd.association (document_id);
CREATE INDEX association_origin ON casestd.association (origin_id);
CREATE INDEX association_destination ON casestd.association (destination_id);

-- Les définitions partagées d'un référentiel : CFItemType, CFLicense, CFSubject, CFConcept,
-- CFAssociationGrouping. Elles ont toutes la même forme — un identifiant, un titre, une
-- description — et ne diffèrent que par ce qu'elles qualifient, d'où une table unique.
CREATE TABLE casestd.definition (
	identifier VARCHAR(36) NOT NULL PRIMARY KEY,
	kind VARCHAR(32) NOT NULL,
	document_id VARCHAR(36) REFERENCES casestd.document(identifier) ON DELETE CASCADE,
	data JSONB NOT NULL,
	last_change TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX definition_kind ON casestd.definition (kind);
CREATE INDEX definition_document ON casestd.definition (document_id);

-- Journal des imports de paquets CASE.
CREATE TABLE casestd.import (
	id VARCHAR(36) NOT NULL PRIMARY KEY,
	document_id VARCHAR(36),
	source VARCHAR(16) NOT NULL,
	source_uri TEXT,
	status VARCHAR(16) NOT NULL,
	report JSONB NOT NULL DEFAULT '{}',
	created_by VARCHAR(36),
	created_at TIMESTAMP NOT NULL DEFAULT NOW(),
	finished_at TIMESTAMP
);
CREATE INDEX import_created ON casestd.import (created_at DESC);
