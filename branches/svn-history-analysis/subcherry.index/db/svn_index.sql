CREATE TABLE `revision` (
  `id` bigint(20) NOT NULL,
  `author` varchar(1024) DEFAULT NULL,
  `date` timestamp NOT NULL,
  `message` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `node` (
  `id` bigint(20) NOT NULL,
  `rev_min` bigint(20) NOT NULL,
  `rev_max` bigint(20) NOT NULL,
  `parent` bigint(20) NOT NULL,
  `predecessor` bigint(20) NOT NULL,
  `predecessor_type` tinyint(4) NOT NULL,
  `name` varchar(1024) NOT NULL,
  `path` varchar(8192) NOT NULL,
  `node_type` tinyint(4) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `parent_idx` (`parent`,`rev_min`),
  KEY `predecessor_idx` (`predecessor`,`predecessor_type`),
  KEY `path_idx` (`path`(255),`rev_min`),
  KEY `revision_idx` (`rev_min`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `content` (
  `node` bigint(20) NOT NULL,
  `rev_min` bigint(20) NOT NULL,
  `rev_max` bigint(20) NOT NULL,
  `data` bigint(20) NOT NULL,
  PRIMARY KEY (`node`, `rev_min`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `data` (
  `id` bigint(20) NOT NULL,
  `blob` blob NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;
